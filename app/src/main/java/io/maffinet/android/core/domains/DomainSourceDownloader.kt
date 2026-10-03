package io.maffinet.android.core.domains

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Explicit, bounded downloads. Every redirect must keep HTTPS; importing is a separate operation. */
class DomainSourceDownloader(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    fun download(urlText: String): String {
        var url = httpsUrl(urlText.trim())
        repeat(MAX_REDIRECTS + 1) { attempt ->
            val connection = openConnection(url)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.setRequestProperty("Accept", "text/plain, application/octet-stream;q=0.9")
                connection.setRequestProperty("Accept-Encoding", "identity")
                val status = connection.responseCode
                if (status in REDIRECT_STATUSES) {
                    require(attempt < MAX_REDIRECTS) { "Слишком много перенаправлений источника" }
                    val location = connection.getHeaderField("Location")
                    require(!location.isNullOrBlank()) { "Источник вернул перенаправление без адреса" }
                    url = httpsUrl(url.toURI().resolve(location.trim()).toString())
                } else {
                    require(status in 200..299) { "Источник вернул HTTP $status" }
                    val contentType = connection.contentType.orEmpty().substringBefore(';').trim()
                    require(!contentType.equals("text/html", true) && !contentType.equals("application/xhtml+xml", true)) {
                        "Источник вернул HTML-страницу. Нужна прямая ссылка на текстовый список или hosts"
                    }
                    require(connection.contentLengthLong <= MAX_BYTES) { "Список превышает 2 МБ" }
                    return connection.inputStream.use(::readUtf8)
                }
            } finally {
                connection.disconnect()
            }
        }
        error("Слишком много перенаправлений источника")
    }

    private fun httpsUrl(text: String): URL {
        val uri = try { URI(text) } catch (_: Exception) {
            throw IllegalArgumentException("Укажите корректную HTTPS-ссылку на текстовый список")
        }
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "Разрешены только HTTPS-ссылки без логина и пароля"
        }
        return uri.toURL()
    }

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        const val TIMEOUT_MILLIS = 10_000
        const val MAX_REDIRECTS = 5
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)

        /** Also used for document imports, so the same byte limit applies to files and URLs. */
        fun readUtf8(input: InputStream): String {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
                if (read < 0) break
                output.write(buffer, 0, read)
                require(output.size() <= MAX_BYTES) { "Список превышает 2 МБ" }
            }
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return try { decoder.decode(ByteBuffer.wrap(output.toByteArray())).toString() } catch (_: Exception) {
                throw IllegalArgumentException("Список должен быть текстом в кодировке UTF-8")
            }
        }
    }
}
