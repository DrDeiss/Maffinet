package io.maffinet.android.core.strategy

import java.io.EOFException
import java.io.InputStream
import java.net.SocketTimeoutException

/** Headers alone are insufficient when DPI interrupts the response after its first packets. */
object HttpProbeBodyValidator {
    const val SAMPLE_BYTES = 64 * 1024
    const val MAX_DURATION_MS = 2_500L

    /**
     * Read a bounded prefix, or the entire body when it is smaller. The caller must also set
     * a socket read timeout: the elapsed-time check bounds slow trickles between blocking reads.
     * Redirects and responses that legitimately have no body keep their HTTP status semantics.
     */
    fun validate(
        statusCode: Int,
        contentLength: Long,
        openBody: () -> InputStream,
        checkCancelled: () -> Unit = {},
        maxDurationMs: Long = MAX_DURATION_MS,
        nanoTime: () -> Long = System::nanoTime,
    ) {
        checkCancelled()
        if (statusCode !in 200..299 || statusCode == 204 || statusCode == 205 || contentLength == 0L) return
        require(maxDurationMs > 0)
        val started = nanoTime()
        val requiredBytes = if (contentLength >= 0) minOf(contentLength, SAMPLE_BYTES.toLong()) else SAMPLE_BYTES.toLong()
        var received = 0L
        val buffer = ByteArray(8 * 1024)
        fun checkDeadline() {
            if ((nanoTime() - started) / 1_000_000 >= maxDurationMs) {
                throw SocketTimeoutException("Ответ HTTP не загрузился за $maxDurationMs мс")
            }
        }
        openBody().use { body ->
            while (received < requiredBytes) {
                checkCancelled()
                checkDeadline()
                val count = body.read(buffer, 0, minOf(buffer.size.toLong(), requiredBytes - received).toInt())
                checkCancelled()
                checkDeadline()
                if (count < 0) {
                    if (contentLength >= 0 && received < requiredBytes) {
                        throw EOFException("Ответ HTTP оборвался: получено $received из $contentLength байт")
                    }
                    return
                }
                received += count
            }
        }
    }
}
