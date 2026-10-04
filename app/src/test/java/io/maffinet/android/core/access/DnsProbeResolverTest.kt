package io.maffinet.android.core.access

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class DnsProbeResolverTest {
    @Test fun diagnosticRetainsRejectedAnswersWithoutCallingThemNoData() {
        LocalDnsServer({ query -> response(query, listOf(record(pointer(), 1, ipv4("10.0.0.1")))) }).use { server ->
            val result = resolver(server).diagnose(HOST, RESOLVER)
            assertEquals(DnsProbeResolver.Outcome.ANSWER, result.outcome)
            assertTrue(result.addresses.isEmpty())
            assertEquals(listOf("10.0.0.1"), result.rejectedAddresses)
            assertEquals(DnsProbeResolver.Transport.UDP, result.transport)
        }
    }

    @Test fun diagnosticDistinguishesNxDomainNoDataAndResolverErrors() {
        listOf(0 to DnsProbeResolver.Outcome.NODATA, 3 to DnsProbeResolver.Outcome.NXDOMAIN,
            2 to DnsProbeResolver.Outcome.RCODE_ERROR, 5 to DnsProbeResolver.Outcome.RCODE_ERROR).forEach { (rcode, outcome) ->
            LocalDnsServer({ query -> response(query, emptyList(), flags = 0x8180 or rcode) }).use { server ->
                val result = resolver(server).diagnose(HOST, RESOLVER)
                assertEquals(outcome, result.outcome)
                assertEquals(rcode, result.rcode)
                assertTrue(result.addresses.isEmpty())
            }
        }
    }

    @Test fun diagnosticErrorReplyCannotSupplyRoutesAndMixedAnswersRetainEvidence() {
        LocalDnsServer({ query -> response(query, listOf(record(pointer(), 1, ipv4("1.1.1.1"))), flags = 0x8182) }).use { server ->
            val result = resolver(server).diagnose(HOST, RESOLVER)
            assertEquals(DnsProbeResolver.Outcome.RCODE_ERROR, result.outcome)
            assertTrue(result.addresses.isEmpty())
        }
        LocalDnsServer({ query -> response(query, listOf("1.1.1.1", "10.0.0.1", "10.0.0.1").map {
            record(pointer(), 1, ipv4(it))
        }) }).use { server ->
            val result = resolver(server).diagnose(HOST, RESOLVER)
            assertEquals(DnsProbeResolver.Outcome.ANSWER, result.outcome)
            assertEquals(listOf("1.1.1.1"), result.addresses)
            assertEquals(listOf("10.0.0.1"), result.rejectedAddresses)
        }
    }

    @Test fun diagnosticDistinguishesMalformedTimeoutAndTransportFailure() {
        LocalDnsServer({ query -> response(query, emptyList()).copyOf(10) }).use { server ->
            assertEquals(DnsProbeResolver.Outcome.MALFORMED, resolver(server).diagnose(HOST, RESOLVER).outcome)
        }
        LocalDnsServer({ null }).use { server ->
            assertEquals(DnsProbeResolver.Outcome.TIMEOUT, resolver(server).diagnose(HOST, RESOLVER, 100).outcome)
        }
        LocalDnsServer({ null }).use { server ->
            val client = DnsProbeResolver(server.address, bindUdp = { throw IOException("Network unavailable") })
            assertEquals(DnsProbeResolver.Outcome.TRANSPORT_ERROR, client.diagnose(HOST, RESOLVER).outcome)
        }
    }

    @Test fun diagnosticPreservesTcpOutcomeAndCancellation() {
        LocalDnsServer({ query -> response(query, emptyList(), flags = 0x8380) }, { query, socket ->
            writeTcp(socket, response(query, emptyList(), flags = 0x8183))
        }).use { server ->
            val result = resolver(server).diagnose(HOST, RESOLVER)
            assertEquals(DnsProbeResolver.Outcome.NXDOMAIN, result.outcome)
            assertEquals(DnsProbeResolver.Transport.TCP, result.transport)
        }
        val checks = AtomicInteger()
        LocalDnsServer({ null }).use { server ->
            assertThrows(CancellationException::class.java) {
                resolver(server).diagnose(HOST, RESOLVER, checkCancelled = {
                    if (checks.incrementAndGet() > 8) throw CancellationException("Cancelled")
                })
            }
        }
    }

    @Test fun compressedAnswersFilterPrivateAddressesAndDuplicates() {
        LocalDnsServer({ query -> response(query, listOf("1.1.1.1", "10.0.0.1", "127.0.0.1", "224.0.0.1", "1.1.1.1")
            .map { record(pointer(), 1, ipv4(it)) }) }).use { server ->
            assertEquals(listOf("1.1.1.1"), resolver(server).lookup(HOST, RESOLVER))
        }
    }

    @Test fun onlyTerminalAnswerInTheQuestionCnameChainIsReturned() {
        LocalDnsServer({ query -> response(query, listOf(
            record(name("unrelated.org"), 1, ipv4("8.8.8.8")),
            record(pointer(), 5, name("edge.linkedin.com")),
            record(name("edge.linkedin.com"), 5, name("last.linkedin.com")),
            record(name("last.linkedin.com"), 1, ipv4("1.1.1.1")),
        )) }).use { server -> assertEquals(listOf("1.1.1.1"), resolver(server).lookup(HOST, RESOLVER)) }
    }

    @Test fun additionalRecordsCannotSupplyAnUnansweredQuestion() {
        LocalDnsServer({ query -> response(query, emptyList(), additional = listOf(record(pointer(), 1, ipv4("1.1.1.1")))) }).use { server ->
            assertEquals(emptyList<String>(), resolver(server).lookup(HOST, RESOLVER))
        }
    }

    @Test fun wrongTransactionQuestionClassOrResponseFlagsAreRejected() {
        val mutations: List<(ByteArray) -> Unit> = listOf(
            { it[0] = (it[0].toInt() xor 1).toByte() },
            { it[13] = 'x'.code.toByte() },
            { it[2] = (it[2].toInt() and 0x7f).toByte() },
            { it[2] = (it[2].toInt() or 0x08).toByte() },
            { it[3] = (it[3].toInt() or 3).toByte() },
            { it[3] = (it[3].toInt() or 0x40).toByte() },
            { it[5] = 2 },
        )
        mutations.forEach { mutate ->
            LocalDnsServer({ query -> response(query, listOf(record(pointer(), 1, ipv4("1.1.1.1")))).also(mutate) }).use { server ->
                assertThrows(IOException::class.java) { resolver(server).lookup(HOST, RESOLVER) }
            }
        }
        LocalDnsServer({ query -> response(query, emptyList()).also { it[it.lastIndex] = 2 } }).use { server ->
            assertThrows(IOException::class.java) { resolver(server).lookup(HOST, RESOLVER) }
        }
    }

    @Test fun incompleteRecordLengthsAndTrailingBytesAreRejected() {
        val mutations: List<(ByteArray) -> ByteArray> = listOf(
            { it.copyOf(it.size - 1) },
            { it + byteArrayOf(0) },
            { it.also { packet -> packet[packet.size - 6] = 0x7f } },
        )
        mutations.forEach { mutate ->
            LocalDnsServer({ query -> mutate(response(query, listOf(record(pointer(), 1, ipv4("1.1.1.1"))))) }).use { server ->
                assertThrows(IOException::class.java) { resolver(server).lookup(HOST, RESOLVER) }
            }
        }
    }

    @Test fun compressionCyclesOutOfBoundsReservedLabelsAndLongNamesAreRejected() {
        val answers: List<(ByteArray) -> ByteArray> = listOf(
            { query -> record(pointer(query.size), 1, ipv4("1.1.1.1")) },
            { _ -> record(pointer(0x3fff), 1, ipv4("1.1.1.1")) },
            { _ -> record(byteArrayOf(0x40, 0), 1, ipv4("1.1.1.1")) },
            { _ -> record(name(List(4) { "a".repeat(63) }.joinToString(".")), 1, ipv4("1.1.1.1")) },
            { query -> record(pointer(), 5, pointer(query.size + 12)) },
        )
        answers.forEach { answer ->
            LocalDnsServer({ query -> response(query, listOf(answer(query))) }).use { server ->
                assertThrows(IOException::class.java) { resolver(server).lookup(HOST, RESOLVER) }
            }
        }
    }

    @Test fun cnameCyclesConflictingAliasesAndMixedCnameAOwnersAreRejected() {
        val answerSets = listOf(
            listOf(record(pointer(), 5, name("edge.linkedin.com")), record(name("edge.linkedin.com"), 5, pointer())),
            listOf(record(pointer(), 5, name("one.linkedin.com")), record(pointer(), 5, name("two.linkedin.com"))),
            listOf(record(pointer(), 5, name("edge.linkedin.com")), record(pointer(), 1, ipv4("1.1.1.1"))),
        )
        answerSets.forEach { answers ->
            LocalDnsServer({ query -> response(query, answers) }).use { server ->
                assertThrows(IOException::class.java) { resolver(server).lookup(HOST, RESOLVER) }
            }
        }
    }

    @Test fun packetsFromAnotherSourceCannotReplaceTheConnectedResolverAnswer() {
        LocalDnsServer({ query -> response(query, listOf(record(pointer(), 1, ipv4("1.1.1.1")))) }, spoofFirst = true).use { server ->
            assertEquals(listOf("1.1.1.1"), resolver(server).lookup(HOST, RESOLVER))
        }
    }

    @Test fun truncatedUdpFallsBackToTcpAndBindsBothTransports() {
        val udpBindings = AtomicInteger()
        val tcpBindings = AtomicInteger()
        LocalDnsServer({ query -> response(query, emptyList(), flags = 0x8380) }, { query, socket ->
            writeTcp(socket, response(query, listOf(record(pointer(), 1, ipv4("1.1.1.1")))))
        }).use { server ->
            val client = DnsProbeResolver(server.address, { udpBindings.incrementAndGet() }, { tcpBindings.incrementAndGet() })
            assertEquals(listOf("1.1.1.1"), client.lookup(HOST, RESOLVER))
            assertEquals(1, udpBindings.get())
            assertEquals(1, tcpBindings.get())
        }
    }

    @Test fun truncatedUdpStillHasToMatchTheQuestionBeforeTcpIsUsed() {
        val tcpBindings = AtomicInteger()
        LocalDnsServer({ query -> response(query, emptyList(), flags = 0x8380).also { it[13] = 'x'.code.toByte() } }).use { server ->
            assertThrows(IOException::class.java) {
                DnsProbeResolver(server.address, bindTcp = { tcpBindings.incrementAndGet() }).lookup(HOST, RESOLVER)
            }
            assertEquals(0, tcpBindings.get())
        }
    }

    @Test fun malformedTcpLengthShortBodyAndRepeatedTruncationAreRejected() {
        val tcpAnswers: List<(ByteArray, Socket) -> Unit> = listOf(
            { _, socket -> socket.getOutputStream().write(byteArrayOf(0, 5, 1, 2, 3, 4, 5)) },
            { _, socket -> socket.getOutputStream().write(byteArrayOf(0, 100, 1, 2, 3)) },
            { query, socket -> writeTcp(socket, response(query, emptyList(), flags = 0x8380)) },
        )
        tcpAnswers.forEach { tcpAnswer ->
            LocalDnsServer({ query -> response(query, emptyList(), flags = 0x8380) }, tcpAnswer).use { server ->
                assertThrows(IOException::class.java) { resolver(server).lookup(HOST, RESOLVER) }
            }
        }
    }

    @Test fun udpAndPartialTcpReadsShareOneDeadline() {
        LocalDnsServer({ query -> Thread.sleep(100); response(query, emptyList(), flags = 0x8380) }, { _, socket ->
            socket.getOutputStream().write(byteArrayOf(0, 40, 1))
            while (socket.getInputStream().read() >= 0) { /* Wait until the deadline closes the client. */ }
        }).use { server ->
            val start = System.nanoTime()
            assertThrows(SocketTimeoutException::class.java) { resolver(server).lookup(HOST, RESOLVER, timeoutMs = 300) }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertTrue("Combined lookup took $elapsedMs ms", elapsedMs in 240..600)
        }
    }

    @Test fun requestedLongTimeoutIsClampedToTwoSeconds() {
        LocalDnsServer({ null }).use { server ->
            val start = System.nanoTime()
            assertThrows(SocketTimeoutException::class.java) { resolver(server).lookup(HOST, RESOLVER, timeoutMs = 10_000) }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertTrue("Hard maximum lookup took $elapsedMs ms", elapsedMs in 1_800..2_500)
        }
    }

    @Test fun cancellationInterruptsPollingWithoutReturningAnAddress() {
        val checks = AtomicInteger()
        LocalDnsServer({ null }).use { server ->
            val start = System.nanoTime()
            assertThrows(CancellationException::class.java) {
                resolver(server).lookup(HOST, RESOLVER, checkCancelled = {
                    if (checks.incrementAndGet() > 8) throw CancellationException("Cancelled")
                })
            }
            assertTrue((System.nanoTime() - start) / 1_000_000 < 500)
        }
    }

    @Test fun lanResolversAreAcceptedButPrivateAnswersStillCannotReceiveRoutes() {
        listOf("192.168.1.1", "10.0.0.1", "172.16.0.1").forEach { address ->
            LocalDnsServer({ query -> response(query, listOf(record(pointer(), 1, ipv4("192.168.1.2")),
                record(pointer(), 1, ipv4("1.1.1.1")))) }).use { server ->
                assertEquals(listOf("1.1.1.1"), resolver(server).lookup(HOST, address))
            }
        }
    }

    @Test fun resolverMustBeNumericUnicastEvenWithAnInjectedLocalDestination() {
        LocalDnsServer({ null }).use { server ->
            listOf("localhost", "resolver.org", "127.0.0.1", "0.0.0.0", "169.254.1.1", "01.1.1.1", "224.0.0.1").forEach { address ->
                assertThrows(IllegalArgumentException::class.java) { resolver(server).lookup(HOST, address) }
            }
            listOf("", "a..org", "https://linkedin.com", "a".repeat(64) + ".org").forEach { host ->
                assertThrows(IllegalArgumentException::class.java) { resolver(server).lookup(host, RESOLVER) }
            }
        }
    }

    private fun resolver(server: LocalDnsServer) = DnsProbeResolver(server.address)

    private class LocalDnsServer(
        private val udpAnswer: (ByteArray) -> ByteArray?,
        private val tcpAnswer: ((ByteArray, Socket) -> Unit)? = null,
        private val spoofFirst: Boolean = false,
    ) : Closeable {
        private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        private val tcp = tcpAnswer?.let { ServerSocket(0, 1, loopback) }
        private val udp = DatagramSocket(InetSocketAddress(loopback, tcp?.localPort ?: 0))
        val address = InetSocketAddress(loopback, udp.localPort)
        private val failure = AtomicReference<Throwable?>()
        private val threads = mutableListOf<Thread>()

        init {
            // Client initialization may seed SecureRandom before the lookup deadline starts.
            // close() unblocks this receive even when a validation test sends no query.
            threads += worker {
                val packet = DatagramPacket(ByteArray(512), 512)
                udp.receive(packet)
                val query = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                if (spoofFirst) DatagramSocket(InetSocketAddress(loopback, 0)).use { spoof ->
                    val wrong = response(query, listOf(record(pointer(), 1, ipv4("8.8.8.8"))))
                    spoof.send(DatagramPacket(wrong, wrong.size, packet.socketAddress))
                }
                udpAnswer(query)?.let { answer -> udp.send(DatagramPacket(answer, answer.size, packet.socketAddress)) }
            }
            if (tcp != null) threads += worker {
                tcp.accept().use { socket ->
                    socket.soTimeout = 2_500
                    val input = java.io.DataInputStream(socket.getInputStream())
                    val length = input.readUnsignedShort()
                    val query = ByteArray(length).also(input::readFully)
                    checkNotNull(tcpAnswer)(query, socket)
                }
            }
        }

        private fun worker(block: () -> Unit): Thread = thread(isDaemon = true, name = "FakeDnsServer") {
            try { block() }
            catch (error: Throwable) {
                if (error !is SocketException && error !is SocketTimeoutException) failure.compareAndSet(null, error)
            }
        }

        override fun close() {
            udp.close()
            tcp?.close()
            threads.forEach { it.join(2_000); assertFalse("DNS fixture worker leaked", it.isAlive) }
            failure.get()?.let { throw AssertionError("DNS fixture failed", it) }
        }
    }

    companion object {
        private const val HOST = "www.linkedin.com"
        private const val RESOLVER = "1.1.1.1"
        private fun pointer(offset: Int = 12) = byteArrayOf((0xc0 or (offset ushr 8)).toByte(), offset.toByte())
        private fun ipv4(address: String) = address.split('.').map { it.toInt().toByte() }.toByteArray()
        private fun name(value: String): ByteArray = ByteArrayOutputStream().also { output ->
            value.split('.').forEach { label -> output.write(label.length); output.write(label.toByteArray(Charsets.US_ASCII)) }
            output.write(0)
        }.toByteArray()

        private fun record(owner: ByteArray, type: Int, data: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).apply {
                write(owner); writeShort(type); writeShort(1); writeInt(60); writeShort(data.size); write(data)
            }
        }.toByteArray()

        private fun response(query: ByteArray, answers: List<ByteArray>, flags: Int = 0x8180,
            additional: List<ByteArray> = emptyList()): ByteArray = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).apply {
                write(query, 0, 2); writeShort(flags); writeShort(1); writeShort(answers.size); writeShort(0); writeShort(additional.size)
                write(query, 12, query.size - 12)
                answers.forEach { write(it) }; additional.forEach { write(it) }
            }
        }.toByteArray()

        private fun writeTcp(socket: Socket, response: ByteArray) {
            DataOutputStream(socket.getOutputStream()).apply { writeShort(response.size); write(response); flush() }
        }
    }
}
