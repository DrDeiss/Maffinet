package io.maffinet.android.core.access

import io.maffinet.android.core.dns.DnsCatalog
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.IDN
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** A bounded A lookup on sockets owned by the selected physical network. Never uses system DNS. */
class DnsProbeResolver private constructor(
    private val bindUdp: (DatagramSocket) -> Unit,
    private val bindTcp: (Socket) -> Unit,
    private val testResolverAddress: InetSocketAddress?,
) {
    constructor(bindUdp: (DatagramSocket) -> Unit = {}, bindTcp: (Socket) -> Unit = {}) :
        this(bindUdp, bindTcp, null)

    /** A real local DNS server can replace the destination; answer filtering is never bypassed. */
    internal constructor(
        resolverSocketAddress: InetSocketAddress,
        bindUdp: (DatagramSocket) -> Unit = {},
        bindTcp: (Socket) -> Unit = {},
    ) : this(bindUdp, bindTcp, resolverSocketAddress) {
        require(!resolverSocketAddress.isUnresolved)
    }

    fun lookup(
        host: String,
        resolverIpv4: String,
        timeoutMs: Long = 2_000L,
        checkCancelled: () -> Unit = {},
    ): List<String> {
        require(timeoutMs > 0) { "DNS timeout must be positive" }
        val deadline = Deadline(timeoutMs.coerceAtMost(2_000L), checkCancelled)
        deadline.check()
        require(DnsCatalog.isUnicastIpv4(resolverIpv4)) { "DNS resolver must be a numeric unicast IPv4 address" }
        val name = normalizeHost(host)
        val destination = testResolverAddress ?: InetSocketAddress(
            InetAddress.getByAddress(resolverIpv4.split('.').map { it.toInt().toByte() }.toByteArray()), 53,
        )
        val id = random.nextInt(65_536)
        val query = query(name, id)
        val udp = guarded(DatagramSocket(null), deadline) { socket ->
            bindUdp(socket)
            deadline.check()
            socket.connect(destination)
            socket.send(DatagramPacket(query, query.size))
            val packet = DatagramPacket(ByteArray(65_535), 65_535)
            while (true) {
                socket.soTimeout = deadline.readTimeout()
                packet.length = packet.data.size
                try {
                    socket.receive(packet)
                    deadline.check()
                    if (packet.socketAddress != destination) continue
                    return@guarded parse(packet.data.copyOfRange(packet.offset, packet.offset + packet.length), name, id, deadline)
                } catch (_: SocketTimeoutException) {
                    deadline.check()
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("Unreachable DNS receive loop")
        }
        if (!udp.truncated) return udp.addresses
        return guarded(Socket(), deadline) { socket ->
            bindTcp(socket)
            deadline.check()
            socket.connect(destination, deadline.remainingMillis())
            socket.soTimeout = deadline.readTimeout()
            val output = socket.getOutputStream()
            output.write(byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()))
            output.write(query)
            output.flush()
            deadline.check()
            val input = socket.getInputStream()
            val prefix = readFully(input, socket, 2, deadline)
            val length = u16(prefix, 0)
            if (length < 12) throw IOException("Invalid DNS TCP message length")
            val response = parse(readFully(input, socket, length, deadline), name, id, deadline)
            if (response.truncated) throw IOException("Truncated DNS TCP response")
            response.addresses
        }
    }

    private fun normalizeHost(host: String): String {
        val ascii = IDN.toASCII(host.removeSuffix("."), IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        require(ascii.isNotEmpty() && ascii.length <= 253 && ascii.split('.').all {
            it.length in 1..63 && it.first() != '-' && it.last() != '-' && it.all { char -> char in 'a'..'z' || char in '0'..'9' || char == '-' }
        }) { "Invalid DNS host" }
        return ascii
    }

    private fun query(host: String, id: Int): ByteArray {
        val bytes = ArrayList<Byte>()
        fun word(value: Int) { bytes.add((value ushr 8).toByte()); bytes.add(value.toByte()) }
        word(id); word(0x0100); word(1); word(0); word(0); word(0)
        host.split('.').forEach { label -> bytes.add(label.length.toByte()); bytes.addAll(label.toByteArray(Charsets.US_ASCII).toList()) }
        bytes.add(0); word(1); word(1)
        return bytes.toByteArray()
    }

    private data class Response(val truncated: Boolean, val addresses: List<String> = emptyList())
    private data class Name(val value: String, val end: Int)

    private fun parse(bytes: ByteArray, host: String, id: Int, deadline: Deadline): Response {
        fun invalid(): Nothing = throw IOException("Malformed or mismatched DNS response")
        fun need(offset: Int, length: Int) { if (offset < 0 || length < 0 || offset > bytes.size - length) invalid() }
        fun word(offset: Int): Int { need(offset, 2); return u16(bytes, offset) }
        fun name(start: Int, encodedLimit: Int = bytes.size): Name {
            var offset = start
            var end: Int? = null
            var expandedSize = 1
            val labels = ArrayList<String>()
            val visited = HashSet<Int>()
            while (true) {
                deadline.check()
                if (!visited.add(offset) || visited.size > 128) invalid()
                need(offset, 1)
                if (end == null && offset >= encodedLimit) invalid()
                val length = bytes[offset].toInt() and 255
                if (length and 0xc0 == 0xc0) {
                    need(offset, 2)
                    if (end == null && offset + 2 > encodedLimit) invalid()
                    if (end == null) end = offset + 2
                    offset = ((length and 0x3f) shl 8) or (bytes[offset + 1].toInt() and 255)
                    continue
                }
                if (length and 0xc0 != 0) invalid()
                offset++
                if (length == 0) return Name(labels.joinToString("."), end ?: offset)
                need(offset, length)
                if (end == null && offset + length > encodedLimit) invalid()
                expandedSize += length + 1
                if (expandedSize > 255) invalid()
                // Do not let a dot or a binary label alias a different hostname when joined.
                val label = bytes.copyOfRange(offset, offset + length)
                if (label.any { byte ->
                        val char = (byte.toInt() and 255).toChar()
                        char !in 'a'..'z' && char !in 'A'..'Z' && char !in '0'..'9' && char != '-' && char != '_'
                    }) invalid()
                labels.add(String(label, Charsets.US_ASCII).lowercase(Locale.ROOT))
                offset += length
            }
        }

        need(0, 12)
        val flags = word(2)
        if (word(0) != id || flags and 0x8000 == 0 || flags and 0x7800 != 0 || flags and 0x004f != 0 || word(4) != 1) invalid()
        val question = name(12)
        if (question.value != host || word(question.end) != 1 || word(question.end + 2) != 1) invalid()
        var offset = question.end + 4
        if (flags and 0x0200 != 0) return Response(truncated = true)
        val answerCount = word(6)
        val recordCount = answerCount + word(8) + word(10)
        if (recordCount > (bytes.size - offset) / 11) invalid()
        val aliases = LinkedHashMap<String, String>()
        val addresses = LinkedHashMap<String, MutableList<String>>()
        repeat(recordCount) { index ->
            deadline.check()
            val owner = name(offset)
            offset = owner.end
            need(offset, 10)
            val type = word(offset)
            val recordClass = word(offset + 2)
            val length = word(offset + 8)
            offset += 10
            need(offset, length)
            if (index < answerCount && recordClass == 1) {
                when (type) {
                    1 -> {
                        if (length != 4) invalid()
                        val address = (offset until offset + 4).joinToString(".") { (bytes[it].toInt() and 255).toString() }
                        addresses.getOrPut(owner.value) { ArrayList() }.add(address)
                    }
                    5 -> {
                        val target = name(offset, offset + length)
                        if (target.end != offset + length || target.value.isEmpty()) invalid()
                        val previous = aliases.put(owner.value, target.value)
                        if (previous != null && previous != target.value) invalid()
                    }
                }
            }
            offset += length
        }
        if (offset != bytes.size) invalid()
        var terminal = host
        val chain = HashSet<String>()
        while (true) {
            deadline.check()
            if (!chain.add(terminal) || chain.size > 64) invalid()
            val target = aliases[terminal] ?: break
            if (addresses.containsKey(terminal)) invalid()
            terminal = target
        }
        return Response(false, addresses[terminal].orEmpty().filter(HostAccessPolicy::isPublicIpv4)
            .distinct().take(HostAccessPolicy.MAX_CANDIDATE_IPS))
    }

    private fun readFully(input: InputStream, socket: Socket, length: Int, deadline: Deadline): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            socket.soTimeout = deadline.readTimeout()
            try {
                val read = input.read(bytes, offset, length - offset)
                if (read < 0) throw EOFException("Incomplete DNS TCP response")
                offset += read
            } catch (_: SocketTimeoutException) {
                deadline.check()
            }
            deadline.check()
        }
        return bytes
    }

    private class Deadline(timeoutMs: Long, private val checkCancelled: () -> Unit) {
        private val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        val expired = AtomicBoolean(false)
        fun check() {
            checkCancelled()
            if (expired.get() || System.nanoTime() >= end) throw SocketTimeoutException("DNS lookup deadline exceeded")
        }
        fun remainingNanos(): Long { check(); return (end - System.nanoTime()).coerceAtLeast(1L) }
        fun remainingMillis(): Int = ((remainingNanos() + 999_999L) / 1_000_000L).toInt().coerceAtLeast(1)
        fun readTimeout(): Int = remainingMillis().coerceAtMost(50)
    }

    private fun <S : Closeable, T> guarded(socket: S, deadline: Deadline, block: (S) -> T): T {
        socket.use {
            val timer = closer.schedule({
                deadline.expired.set(true)
                runCatching { socket.close() }
            }, deadline.remainingNanos(), TimeUnit.NANOSECONDS)
            try {
                val result = block(socket)
                deadline.check()
                return result
            } catch (error: IOException) {
                deadline.check()
                throw error
            } finally {
                timer.cancel(false)
            }
        }
    }

    companion object {
        // Seed before lookup starts its network deadline, avoiding first-use entropy initialization.
        private val random = SecureRandom().apply { nextInt() }
        private val closer = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "MaffinetDnsDeadline").apply { isDaemon = true }
        }
        private fun u16(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 255) shl 8) or (bytes[offset + 1].toInt() and 255)
    }
}
