package io.maffinet.android.core.strategy

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HttpProbeBodyValidatorTest {
    @Test fun largeResponseRequires64KiBBeforeSuccessAndDoesNotReadTheWholeDownload() {
        val body = ByteArrayInputStream(ByteArray(140_059))
        HttpProbeBodyValidator.validate(200, 140_059, { body })
        assertEquals(140_059 - 65_536, body.available())
    }

    @Test fun completeSmallResponseAndUnknownLengthEofAreSuccessful() {
        HttpProbeBodyValidator.validate(200, 12, { ByteArrayInputStream(ByteArray(12)) })
        HttpProbeBodyValidator.validate(200, -1, { ByteArrayInputStream(ByteArray(12)) })
        HttpProbeBodyValidator.validate(200, -1, { ByteArrayInputStream(ByteArray(0)) })
    }

    @Test fun truncatedKnownLengthIsRejectedEvenAfterHttp200() {
        val body = CloseRecordingStream(ByteArray(13_781))
        val error = assertThrows(EOFException::class.java) {
            HttpProbeBodyValidator.validate(200, 140_059, { body })
        }
        assertTrue(error.message.orEmpty().contains("13781"))
        assertTrue(body.closed)
    }

    @Test fun knownSmallBodyMustAlsoReachItsDeclaredLength() {
        assertThrows(EOFException::class.java) {
            HttpProbeBodyValidator.validate(200, 12, { ByteArrayInputStream(ByteArray(11)) })
        }
    }

    @Test fun bodylessAndRedirectStatusesDoNotRequireAResponseBody() {
        listOf(204, 205, 301, 302, 304, 307, 308).forEach { status ->
            HttpProbeBodyValidator.validate(status, -1, { error("Must not open body for HTTP $status") })
        }
        HttpProbeBodyValidator.validate(200, 0, { error("Declared empty body") })
    }

    @Test fun slowTrickleIsBoundedEvenWhenEveryReadReturnsData() {
        var clock = 0L
        var reads = 0
        val body = object : InputStream() {
            override fun read(): Int = error("Bulk reads only")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                reads++
                clock += 100_000_000L
                buffer[offset] = 1
                return 1
            }
        }
        assertThrows(SocketTimeoutException::class.java) {
            HttpProbeBodyValidator.validate(200, -1, { body }, maxDurationMs = 250, nanoTime = { clock })
        }
        assertEquals(3, reads)
    }

    @Test fun cancellationDuringReadingIsPropagatedAndClosesTheBody() {
        val body = CloseRecordingStream(ByteArray(140_059))
        var checks = 0
        assertThrows(CancellationException::class.java) {
            HttpProbeBodyValidator.validate(200, 140_059, { body }, checkCancelled = {
                if (++checks == 3) throw CancellationException("Cancelled")
            })
        }
        assertEquals(140_059 - 8_192, body.available())
        assertTrue(body.closed)
    }

    @Test fun alreadyCancelledProbeDoesNotOpenABody() {
        assertThrows(CancellationException::class.java) {
            HttpProbeBodyValidator.validate(200, -1, { error("Cancelled before opening") },
                checkCancelled = { throw CancellationException("Cancelled") })
        }
    }

    @Test fun serverThatSends200ThenStallsAfterFirstPacketsIsRejectedByReadTimeout() {
        val releaseServer = CountDownLatch(1)
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val worker = Thread {
                server.accept().use { socket ->
                    val headers = "HTTP/1.1 200 OK\r\nContent-Length: 140059\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().apply {
                        write(headers.toByteArray(Charsets.US_ASCII))
                        write(ByteArray(13_781))
                        flush()
                    }
                    releaseServer.await(10, TimeUnit.SECONDS)
                }
            }.apply { isDaemon = true; start() }
            val connection = URL("http://127.0.0.1:${server.localPort}/").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 1_000
                connection.readTimeout = 150
                assertEquals(200, connection.responseCode)
                val started = System.nanoTime()
                assertThrows(SocketTimeoutException::class.java) {
                    HttpProbeBodyValidator.validate(connection.responseCode, connection.contentLengthLong,
                        { connection.inputStream })
                }
                assertTrue("Read timeout must bound the stalled body", (System.nanoTime() - started) / 1_000_000 < 2_000)
            } finally {
                connection.disconnect()
                releaseServer.countDown()
                worker.join(1_000)
            }
        }
    }

    private class CloseRecordingStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }
}
