package io.maffinet.verification

import io.maffinet.android.core.dpibypass.ByeDpiArgumentCompiler
import io.maffinet.android.core.dpibypass.ByeDpiFilterConfiguration
import io.maffinet.android.core.strategy.DefaultStrategyCatalog
import io.maffinet.android.core.access.AutomaticAccessArguments
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Production Kotlin arguments interpreted by the actual pinned native C code. */
class NativeParserContractTest {
    private lateinit var fixture: File
    private val selection = ByeDpiFilterConfiguration(
        emptyList(), listOf("youtube.com", "googlevideo.com", "custom.example"),
    )

    @Before fun requiresPreparedLinuxFixture() {
        fixture = File(System.getProperty("maffinet.nativeFixture", ""))
        assumeTrue("Linux native fixture was not prepared for this JVM-only run", fixture.isFile && fixture.canExecute())
    }

    private fun selected(command: String, mode: String, host: String): List<Int> {
        val arguments = ByeDpiArgumentCompiler.compile(command, selection)
        return selectedArguments(arguments, mode, host)
    }

    private fun selectedArguments(arguments: Array<String>, mode: String, host: String): List<Int> {
        val process = ProcessBuilder(listOf(fixture.absolutePath, mode, host) + arguments.toList())
            .redirectErrorStream(true).start()
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        check(finished) { "Native contract process timed out" }
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.exitValue())
        return output.lineSequence().single { it.startsWith("contract ") }
            .removePrefix("contract ").split(' ').map(String::toInt)
    }

    @Test fun selectedRootsAndSubdomainsReachDesyncForHttpAndTls() {
        for (mode in listOf("http", "tls")) {
            for (host in listOf("youtube.com", "www.youtube.com", "r1.googlevideo.com", "custom.example")) {
                assertEquals(1, selected("-s1", mode, host)[1])
            }
        }
    }

    @Test fun unrelatedAndBoundaryLookalikeHostsUseNoDesyncFallback() {
        for (mode in listOf("http", "tls")) {
            for (host in listOf("example.org", "evilyoutube.com", "youtube.com.example.org")) {
                assertEquals(0, selected("-s1", mode, host)[1])
            }
        }
    }

    @Test fun retryGroupsStillEnforceTheSelectedHosts() {
        assertEquals(1, selected("-s1 -At -d2", "retry-tls", "www.youtube.com")[1])
        assertEquals(0, selected("-s1 -At -d2", "retry-tls", "example.org")[1])
    }

    @Test fun tlsOnlyProtocolRemainsNarrowAndOpaqueTrafficUsesFallback() {
        assertEquals(1, selected("-Kt -s1", "tls", "youtube.com")[1])
        assertEquals(0, selected("-Kt -s1", "http", "youtube.com")[1])
        assertEquals(0, selected("-s1", "opaque", "youtube.com")[1])
    }

    @Test fun udpIsForwardedUnmodifiedWithoutApplyingAnyFakes() {
        for (command in listOf("-a3", "-Ku -a3", "-s1 -An -a3")) {
            val result = selected(command, "udp", "youtube.com")
            assertEquals(0, result[1])
            assertEquals(0, result[2])
        }
    }

    @Test fun allInheritedPresetsAreAcceptedByTheProductionNativeParser() {
        for (command in DefaultStrategyCatalog.commands) {
            assertTrue(command, selected(command, "parse", "youtube.com")[3] > 0)
        }
    }

    @Test fun automaticProductionChainHandlesUnknownTlsHostsAndKeepsUdpUnmodified() {
        val arguments = AutomaticAccessArguments.create("127.0.0.1", 1080)
        assertTrue(selectedArguments(arguments, "parse", "uncatalogued-service.net")[3] > 0)
        assertEquals(1, selectedArguments(arguments, "tls", "uncatalogued-service.net")[1])
        val udp = selectedArguments(arguments, "udp", "uncatalogued-service.net")
        assertEquals(0, udp[1])
        assertEquals(0, udp[2])
    }
}
