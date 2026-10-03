package io.maffinet.android.core.domains

import io.maffinet.android.data.domains.UserDomainRepository
import io.maffinet.android.data.domains.UserDomainStore
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException

class DomainSourceDownloaderTest {
    @Test fun followsRelativeHttpsRedirectAndAppliesTimeoutsWithoutAutomaticRedirects() {
        val first = FakeConnection(URL("https://source.example.org/start"), status = 302, location = "/hosts")
        val second = FakeConnection(URL("https://source.example.org/hosts"), body = "176.99.11.77 example.org")
        val opened = mutableListOf<String>()
        val connections = listOf(first, second).iterator()
        val downloader = DomainSourceDownloader { url -> opened += url.toString(); connections.next() }
        assertEquals("176.99.11.77 example.org", downloader.download(first.url.toString()))
        assertEquals(listOf(first.url.toString(), second.url.toString()), opened)
        listOf(first, second).forEach {
            assertFalse(it.instanceFollowRedirects)
            assertEquals(DomainSourceDownloader.TIMEOUT_MILLIS, it.connectTimeout)
            assertEquals(DomainSourceDownloader.TIMEOUT_MILLIS, it.readTimeout)
            assertTrue(it.disconnected)
        }
    }

    @Test fun refusesHttpAtTheStartOrAfterARedirectBeforeOpeningIt() {
        var opened = 0
        val connection = FakeConnection(URL("https://source.example.org/start"), status = 302, location = "http://insecure.example.org/hosts")
        val downloader = DomainSourceDownloader { opened++; connection }
        assertThrows(IllegalArgumentException::class.java) { downloader.download("http://source.example.org/hosts") }
        assertEquals(0, opened)
        assertThrows(IllegalArgumentException::class.java) { downloader.download(connection.url.toString()) }
        assertEquals(1, opened)
        assertTrue(connection.disconnected)
    }

    @Test fun rejectsRedirectLoopsHttpErrorsHtmlAndOversizedContentLength() {
        var opened = 0
        val loop = DomainSourceDownloader { url -> opened++; FakeConnection(url, status = 307, location = "/same") }
        assertThrows(IllegalArgumentException::class.java) { loop.download("https://source.example.org/same") }
        assertEquals(DomainSourceDownloader.MAX_REDIRECTS + 1, opened)
        listOf(
            FakeConnection(URL("https://source.example.org/hosts"), status = 404),
            FakeConnection(URL("https://source.example.org/hosts"), contentType = "text/html; charset=UTF-8"),
            FakeConnection(URL("https://source.example.org/hosts"), length = DomainSourceDownloader.MAX_BYTES.toLong() + 1),
        ).forEach { connection ->
            assertThrows(IllegalArgumentException::class.java) { DomainSourceDownloader { connection }.download(connection.url.toString()) }
            assertFalse(connection.bodyOpened)
            assertTrue(connection.disconnected)
        }
    }

    @Test fun byteLimitAppliesWithoutContentLengthAndUtf8IsValidated() {
        val limit = DomainSourceDownloader.MAX_BYTES
        assertEquals(limit, DomainSourceDownloader.readUtf8(ByteArrayInputStream(ByteArray(limit) { 'a'.code.toByte() })).length)
        val oversized = FakeConnection(URL("https://source.example.org/hosts"), bytes = ByteArray(limit + 1) { 'a'.code.toByte() })
        assertThrows(IllegalArgumentException::class.java) { DomainSourceDownloader { oversized }.download(oversized.url.toString()) }
        assertTrue(oversized.disconnected)
        assertThrows(IllegalArgumentException::class.java) { DomainSourceDownloader.readUtf8(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28))) }
    }

    @Test fun failedDownloadsAndInvalidBodiesPreservePreviouslySavedDomains() {
        val store = object : UserDomainStore {
            var saved = listOf("saved.org")
            override fun read() = saved
            override fun write(domains: List<String>) { saved = domains }
        }
        val repository = UserDomainRepository(store)
        val url = URL("https://source.example.org/hosts")
        val timeout = FakeConnection(url, failure = SocketTimeoutException("Timed out"))
        assertThrows(SocketTimeoutException::class.java) {
            repository.importUserDomains(DomainSourceDownloader { timeout }.download(url.toString()))
        }
        assertTrue(timeout.disconnected)
        assertEquals(listOf("saved.org"), store.saved)
        val html = FakeConnection(url, body = "<html>failure</html>")
        assertFalse(repository.importUserDomains(DomainSourceDownloader { html }.download(url.toString())).isValid)
        assertEquals(listOf("saved.org"), store.saved)
    }

    private class FakeConnection(
        url: URL,
        private val status: Int = 200,
        private val body: String = "example.org",
        private val bytes: ByteArray = body.toByteArray(Charsets.UTF_8),
        private val contentType: String = "text/plain",
        private val location: String? = null,
        private val length: Long = -1,
        private val failure: Exception? = null,
    ) : HttpURLConnection(url) {
        var disconnected = false
        var bodyOpened = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode(): Int { failure?.let { throw it }; return status }
        override fun getContentType() = contentType
        override fun getContentLengthLong() = length
        override fun getHeaderField(name: String?) = if (name.equals("Location", true)) location else null
        override fun getInputStream(): ByteArrayInputStream { bodyOpened = true; return ByteArrayInputStream(bytes) }
    }
}
