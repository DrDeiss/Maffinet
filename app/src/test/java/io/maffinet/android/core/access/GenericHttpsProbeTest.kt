package io.maffinet.android.core.access

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class GenericHttpsProbeTest {
    private val probe get() = GenericHttpsProbe(clientContext.socketFactory)
    private fun parse(text: String) = probe.readResponse(ByteArrayInputStream(text.toByteArray(Charsets.ISO_8859_1)))

    @Test fun stalled13781ByteBodyIsNotSuccessfulHeadersOnlyEvidence() {
        val prefix = "HTTP/1.1 200 OK\r\nContent-Length: 63714\r\n\r\n" + "x".repeat(13781)
        val bytes = ByteArrayInputStream(prefix.toByteArray())
        val stalled = object : InputStream() {
            override fun read(): Int = bytes.read().also { if (it < 0) throw SocketTimeoutException("body stalled") }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                bytes.read(buffer, offset, length).also { if (it < 0) throw SocketTimeoutException("body stalled") }
        }
        val result = probe.readResponse(stalled)
        assertEquals(200, result.statusCode)
        assertFalse(result.bodyComplete)
        assertNotNull(result.error)
    }

    @Test fun fullBodyAndBoundedLargePrefixComplete() {
        val short = parse("HTTP/1.1 200 OK\r\nContent-Length: 13781\r\n\r\n" + "x".repeat(13781))
        assertTrue(short.bodyComplete)
        val prefix = parse("HTTP/1.1 200 OK\r\nContent-Length: 999999\r\n\r\n" + "x".repeat(65536))
        assertTrue(prefix.bodyComplete)
    }

    @Test fun errorStatusBodyIsValidatedAndRemainsSeparateFromAvailability() {
        for (code in listOf(401, 403, 404, 451, 500)) {
            val result = parse("HTTP/1.1 $code Status\r\nContent-Length: 4\r\n\r\nfull")
            assertEquals(code, result.statusCode)
            assertTrue(result.bodyComplete)
            val partial = parse("HTTP/1.1 $code Status\r\nContent-Length: 4\r\n\r\nno")
            assertEquals(code, partial.statusCode)
            assertFalse(partial.bodyComplete)
        }
    }

    @Test fun redirectsAreReportedWithoutFollowingAndTheirBodiesStillMatter() {
        val redirect = parse("HTTP/1.1 302 Found\r\nLocation: https://accounts.google.com/\r\nContent-Length: 3\r\n\r\nend")
        assertEquals(302, redirect.statusCode)
        assertTrue(redirect.bodyComplete)
        assertFalse(parse("HTTP/1.1 302 Found\r\nContent-Length: 3\r\n\r\nx").bodyComplete)
    }

    @Test fun chunkedAndEofFramingRequireActualCompletion() {
        assertTrue(parse("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n0\r\n\r\n").bodyComplete)
        assertFalse(parse("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nab").bodyComplete)
        assertTrue(parse("HTTP/1.0 200 OK\r\n\r\nclosed body").bodyComplete)
        assertTrue(parse("HTTP/1.1 204 No Content\r\n\r\n").bodyComplete)
        assertTrue(parse("HTTP/1.1 304 Not Modified\r\n\r\n").bodyComplete)
    }

    @Test fun ambiguousFramingAndOversizedMetadataAreRejected() {
        assertFalse(parse("HTTP/1.1 200 OK\r\nContent-Length: 1\r\nContent-Length: 2\r\n\r\nx").bodyComplete)
        assertFalse(parse("HTTP/1.1 200 OK\r\nContent-Length: 1\r\nTransfer-Encoding: chunked\r\n\r\nx").bodyComplete)
        assertFalse(parse("HTTP/1.1 200 OK\r\nX-Large: " + "x".repeat(9000) + "\r\n\r\n").bodyComplete)
        assertTrue(parse("HTTP/1.1 200 OK\r\nContent-Length: 1\r\nContent-Length: 1\r\n\r\nx").bodyComplete)
    }

    @Test fun cancellationIsNotConvertedIntoFailedHttpEvidence() {
        assertThrows(CancellationException::class.java) {
            probe.readResponse(ByteArrayInputStream(ByteArray(0))) { throw CancellationException("cancelled") }
        }
    }

    @Test fun socksMarkerNumericDestinationOriginalSniAndNoCredentialsArePreserved() {
        withSocksServer { port, server ->
            val result = probe.probe(HOST, 443, "8.8.8.8", port, timeoutMs = 2500)
            assertEquals(200, result.statusCode)
            assertTrue(result.bodyComplete)
            val request = server.get(3, TimeUnit.SECONDS)
            assertTrue(request.startsWith("GET / HTTP/1.1\r\nHost: $HOST\r\n"))
            assertTrue(request.contains("Accept-Encoding: identity\r\n"))
            assertFalse(request.contains("Cookie:", ignoreCase = true))
            assertFalse(request.contains("Authorization:", ignoreCase = true))
        }
    }

    @Test fun hostnameAndTrustValidationRemainMandatory() {
        withSocksServer(expectTlsFailure = true) { port, server ->
            assertFalse(probe.probe("wrong.wikipedia.org", 443, "8.8.8.8", port).bodyComplete)
            server.get(3, TimeUnit.SECONDS)
        }
        withSocksServer(expectTlsFailure = true) { port, server ->
            assertFalse(GenericHttpsProbe().probe(HOST, 443, "8.8.8.8", port).bodyComplete)
            server.get(3, TimeUnit.SECONDS)
        }
    }

    @Test fun publicIpv6DirectProbeUsesNumericSocksAddressAndOriginalTlsIdentity() {
        val ipv6 = "2606:4700:4700::1111"
        withSocksServer(candidateIp = ipv6) { port, server ->
            val result = probe.probe(HOST, 443, ipv6, port)
            assertEquals(200, result.statusCode)
            assertTrue(result.bodyComplete)
            server.get(3, TimeUnit.SECONDS)
        }
    }

    @Test fun absoluteDeadlineInterruptsBodyStallAndPrivateDestinationNeverConnects() {
        withSocksServer(stallBody = true) { port, server ->
            val start = System.nanoTime()
            val result = probe.probe(HOST, 443, "8.8.8.8", port, timeoutMs = 1500)
            assertEquals(200, result.statusCode)
            assertFalse(result.bodyComplete)
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000)
            server.get(3, TimeUnit.SECONDS)
        }
        assertFalse(probe.probe(HOST, 443, "127.0.0.1", 1).bodyComplete)
        assertFalse(probe.probe(HOST, 443, "8.8.8.8", 1, localSocksIp = "10.0.0.1").bodyComplete)
    }

    private fun withSocksServer(
        expectTlsFailure: Boolean = false,
        stallBody: Boolean = false,
        candidateIp: String = "8.8.8.8",
        test: (Int, java.util.concurrent.Future<String>) -> Unit,
    ) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
            val worker = Executors.newSingleThreadExecutor()
            val result = worker.submit<String> {
                listener.accept().use { raw ->
                    raw.soTimeout = 3000
                    val input = raw.getInputStream()
                    fun readExact(amount: Int): ByteArray = ByteArray(amount).also { buffer ->
                        var offset = 0
                        while (offset < amount) {
                            val count = input.read(buffer, offset, amount - offset)
                            check(count > 0)
                            offset += count
                        }
                    }
                    assertArrayEquals(byteArrayOf(5, 2, 0, 0x80.toByte()), readExact(4))
                    raw.getOutputStream().apply { write(byteArrayOf(5, 0)); flush() }
                    val address = InetAddress.getByName(candidateIp).address
                    val kind = if (address.size == 4) 1 else 4
                    assertArrayEquals(byteArrayOf(5, 1, 0, kind.toByte()) + address + byteArrayOf(1, 0xbb.toByte()), readExact(6 + address.size))
                    raw.getOutputStream().apply { write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0)); flush() }
                    try {
                        (serverContext.socketFactory.createSocket(raw, "127.0.0.1", raw.port, true) as SSLSocket).use { tls ->
                            tls.useClientMode = false
                            tls.soTimeout = 3000
                            tls.startHandshake()
                            val names = (tls.session as ExtendedSSLSession).requestedServerNames
                            assertEquals(HOST, (names.single() as SNIHostName).asciiName)
                            val request = StringBuilder()
                            while (!request.endsWith("\r\n\r\n")) {
                                val next = tls.getInputStream().read()
                                check(next >= 0 && request.length < 4096)
                                request.append(next.toChar())
                            }
                            val length = if (stallBody) 63714 else 13781
                            tls.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Length: $length\r\n\r\n".toByteArray())
                                write(ByteArray(13781) { 'x'.code.toByte() })
                                flush()
                            }
                            if (stallBody) Thread.sleep(1700)
                            request.toString()
                        }
                    } catch (error: java.io.IOException) {
                        if (!expectTlsFailure) throw error
                        "expected TLS rejection"
                    }
                }
            }
            try { test(listener.localPort, result) }
            finally {
                result.cancel(true)
                listener.close()
                worker.shutdownNow()
            }
        }
    }

    companion object {
        private const val HOST = "probe.wikipedia.org"
        private lateinit var directory: Path
        private lateinit var serverContext: SSLContext
        private lateinit var clientContext: SSLContext

        @JvmStatic @BeforeClass fun createTlsFixture() {
            directory = Files.createTempDirectory("maffinet-https-probe-")
            val storeFile = directory.resolve("fixture.p12")
            val password = "local-test-fixture"
            val keytool = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "keytool.exe" else "keytool")
            val process = ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "fixture", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-dname", "CN=$HOST", "-ext", "SAN=dns:$HOST",
                "-storetype", "PKCS12", "-keystore", storeFile.toString(), "-storepass", password, "-keypass", password)
                .redirectErrorStream(true).start()
            assertTrue("Local TLS fixture keytool timed out", process.waitFor(15, TimeUnit.SECONDS))
            assertEquals(process.inputStream.bufferedReader().readText(), 0, process.exitValue())
            val store = KeyStore.getInstance("PKCS12")
            Files.newInputStream(storeFile).use { store.load(it, password.toCharArray()) }
            val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, password.toCharArray()) }
            val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            serverContext = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
            clientContext = SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }
        }

        @JvmStatic @AfterClass fun removeTlsFixture() {
            if (::directory.isInitialized) {
                Files.deleteIfExists(directory.resolve("fixture.p12"))
                Files.deleteIfExists(directory)
            }
        }
    }
}
