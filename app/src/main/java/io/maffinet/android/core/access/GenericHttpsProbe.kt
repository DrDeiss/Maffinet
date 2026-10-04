package io.maffinet.android.core.access

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** A public, credential-free HTTP/1.1 health probe, preserving the original TLS identity. */
class GenericHttpsProbe internal constructor(private val tlsFactory: SSLSocketFactory) {
    constructor() : this(SSLSocketFactory.getDefault() as SSLSocketFactory)

    /** bodyComplete means a complete small body or a validated 64 KiB prefix. */
    data class Result(val statusCode: Int?, val bodyComplete: Boolean, val error: String? = null)

    fun probe(
        host: String,
        port: Int,
        candidateIp: String,
        localSocksPort: Int = 1080,
        timeoutMs: Long = MAX_DURATION_MS,
        checkCancelled: () -> Unit = {},
        localSocksIp: String = "127.0.0.1",
    ): Result {
        val originalHost = HostAccessPolicy.normalizePublicHost(host)
            ?: return Result(null, false, "Invalid public HTTPS host")
        if (!HostAccessPolicy.isPublicOriginalIp(candidateIp) || port !in 1..65535 || localSocksPort !in 1..65535 ||
            localSocksIp !in setOf("127.0.0.1", "::1") || timeoutMs <= 0) {
            return Result(null, false, "Invalid probe destination")
        }
        checkCancelled()
        val duration = minOf(timeoutMs, MAX_DURATION_MS)
        val deadline = System.nanoTime() + duration * 1_000_000
        fun remainingMs(): Int {
            checkCancelled()
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw SocketTimeoutException("HTTPS probe deadline expired")
            return ((remaining + 999_999) / 1_000_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        val raw = Socket()
        // Also bound blocking TLS writes; SO_TIMEOUT alone bounds only reads.
        val expiration = deadlines.schedule({ runCatching { raw.close() } }, duration, TimeUnit.MILLISECONDS)
        try {
            raw.connect(InetSocketAddress(InetAddress.getByName(localSocksIp), localSocksPort), remainingMs())
            val input = raw.getInputStream()
            val output = raw.getOutputStream()
            output.write(byteArrayOf(5, 2, 0, INTERNAL_PROBE_METHOD.toByte()))
            output.flush()
            fun readByte(): Int {
                raw.soTimeout = remainingMs()
                return input.read().also { if (it < 0) throw EOFException("Incomplete SOCKS reply") }
            }
            if (readByte() != 5 || readByte() != 0) throw IOException("SOCKS authentication rejected")
            // Policy validation above guarantees a numeric address, never a DNS lookup.
            val address = InetAddress.getByName(candidateIp).address
            val addressType = if (address.size == 4) 1 else 4
            output.write(byteArrayOf(5, 1, 0, addressType.toByte()) + address + byteArrayOf((port ushr 8).toByte(), port.toByte()))
            output.flush()
            if (readByte() != 5 || readByte() != 0 || readByte() != 0) throw IOException("SOCKS CONNECT rejected")
            val replyAddressBytes = when (readByte()) {
                1 -> 4
                4 -> 16
                3 -> readByte().also { if (it == 0) throw IOException("Invalid SOCKS bound address") }
                else -> throw IOException("Invalid SOCKS reply address type")
            }
            repeat(replyAddressBytes + 2) { readByte() }

            val tls = tlsFactory.createSocket(raw, originalHost, port, true) as SSLSocket
            tls.use {
                it.sslParameters = it.sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                    serverNames = listOf(SNIHostName(originalHost))
                }
                it.soTimeout = remainingMs()
                it.startHandshake()
                remainingMs()
                val authority = if (port == 443) originalHost else "$originalHost:$port"
                val request = "GET / HTTP/1.1\r\nHost: $authority\r\nAccept: */*\r\n" +
                    "Accept-Encoding: identity\r\nConnection: close\r\n\r\n"
                it.getOutputStream().apply { write(request.toByteArray(StandardCharsets.US_ASCII)); flush() }
                val bodyInput = object : InputStream() {
                    private val source = it.getInputStream()
                    override fun read(): Int {
                        it.soTimeout = remainingMs()
                        return source.read().also { remainingMs() }
                    }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        it.soTimeout = remainingMs()
                        return source.read(buffer, offset, length).also { remainingMs() }
                    }
                }
                return readResponse(bodyInput, checkCancelled)
            }
        } catch (error: Exception) {
            checkCancelled() // Cancellation must not become ordinary failed evidence.
            return Result(null, false, describe(error))
        } finally {
            expiration.cancel(false)
            runCatching { raw.close() }
        }
    }

    /** Shared production parser; stream tests exercise incomplete bodies without a live site. */
    internal fun readResponse(input: InputStream, checkCancelled: () -> Unit = {}): Result {
        var status: Int? = null
        try {
            var metadataBytes = 0
            fun line(): String {
                val bytes = ArrayList<Byte>()
                while (true) {
                    checkCancelled()
                    val value = input.read()
                    if (value < 0) throw EOFException("HTTP headers ended early")
                    metadataBytes++
                    if (metadataBytes > MAX_METADATA_BYTES || bytes.size > MAX_LINE_BYTES) throw IOException("HTTP metadata is too large")
                    if (value == 13) {
                        if (input.read() != 10) throw IOException("Invalid HTTP line ending")
                        metadataBytes++
                        return bytes.toByteArray().toString(StandardCharsets.ISO_8859_1)
                    }
                    if (value == 10 || value == 0) throw IOException("Invalid HTTP line")
                    bytes.add(value.toByte())
                }
            }
            var headers: Map<String, List<String>>
            var informational = 0
            do {
                val start = line()
                val match = Regex("HTTP/1\\.[01] ([1-5][0-9]{2})(?: .*)?").matchEntire(start)
                    ?: throw IOException("Invalid HTTP status line")
                status = match.groupValues[1].toInt()
                val fields = linkedMapOf<String, MutableList<String>>()
                while (true) {
                    val field = line()
                    if (field.isEmpty()) break
                    val colon = field.indexOf(':')
                    if (colon <= 0 || field.take(colon).any { !it.isLetterOrDigit() && it !in "!#$%&'*+-.^_`|~" }) {
                        throw IOException("Invalid HTTP header")
                    }
                    fields.getOrPut(field.take(colon).lowercase()) { mutableListOf() }.add(field.substring(colon + 1).trim())
                }
                headers = fields
                if (status in 100..199 && (++informational > 4 || status == 101)) throw IOException("Unexpected HTTP upgrade/informational response")
            } while (status in 100..199)

            if (status == 204 || status == 205 || status == 304) return Result(status, true)
            val encodings = headers["content-encoding"].orEmpty().flatMap { it.split(',') }.map { it.trim().lowercase() }
            if (encodings.any { it != "identity" }) throw IOException("Server ignored identity content encoding")
            val lengths = headers["content-length"].orEmpty().flatMap { it.split(',') }.map { value ->
                value.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()
                    ?: throw IOException("Invalid HTTP content length")
            }
            if (lengths.distinct().size > 1) throw IOException("Conflicting HTTP content lengths")
            val transfer = headers["transfer-encoding"].orEmpty().flatMap { it.split(',') }.map { it.trim().lowercase() }
            if (transfer.isNotEmpty() && (transfer != listOf("chunked") || lengths.isNotEmpty())) {
                throw IOException("Unsupported or ambiguous HTTP framing")
            }
            var received = 0
            val buffer = ByteArray(8 * 1024)
            fun readBody(amount: Long) {
                var left = minOf(amount, (SAMPLE_BYTES - received).toLong())
                while (left > 0) {
                    checkCancelled()
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (count < 0) throw EOFException("HTTP body ended early after $received bytes")
                    if (count == 0) continue
                    received += count
                    left -= count
                }
            }
            if (transfer.isNotEmpty()) {
                while (received < SAMPLE_BYTES) {
                    val size = line().substringBefore(';').trim()
                    val chunk = size.takeIf { it.isNotEmpty() && it.length <= 16 && it.all { c -> c in "0123456789abcdefABCDEF" } }
                        ?.toLongOrNull(16) ?: throw IOException("Invalid HTTP chunk size")
                    if (chunk == 0L) {
                        while (line().isNotEmpty()) { /* bounded trailers */ }
                        break
                    }
                    readBody(chunk)
                    if (received == SAMPLE_BYTES) break
                    if (input.read() != 13 || input.read() != 10) throw IOException("Invalid HTTP chunk ending")
                }
            } else if (lengths.isNotEmpty()) {
                readBody(lengths.first())
            } else {
                while (received < SAMPLE_BYTES) {
                    checkCancelled()
                    val count = input.read(buffer, 0, minOf(buffer.size, SAMPLE_BYTES - received))
                    if (count < 0) break
                    received += count
                }
            }
            checkCancelled()
            return Result(status, true)
        } catch (error: Exception) {
            checkCancelled()
            return Result(status, false, describe(error))
        }
    }

    companion object {
        const val MAX_DURATION_MS = 5_000L
        const val SAMPLE_BYTES = 64 * 1024
        private const val MAX_METADATA_BYTES = 128 * 1024
        private const val MAX_LINE_BYTES = 8 * 1024
        private const val INTERNAL_PROBE_METHOD = 0x80
        private val deadlines = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "Maffinet-HTTPS-deadline").apply { isDaemon = true }
        }
        private fun describe(error: Exception): String =
            (error.message ?: error.javaClass.simpleName).take(240)
    }
}
