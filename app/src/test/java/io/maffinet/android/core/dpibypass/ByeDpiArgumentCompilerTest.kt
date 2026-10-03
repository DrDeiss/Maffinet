package io.maffinet.android.core.dpibypass

import io.maffinet.android.core.domains.DomainList
import io.maffinet.android.core.domains.LegacyStrategyAliases
import org.junit.Assert.*
import org.junit.Test

class ByeDpiArgumentCompilerTest {
    private val lists = listOf(
        DomainList("general", "General", listOf("example.com", "example.net")),
        DomainList("user", "User", listOf("custom.org", "another.org")),
    )
    private val selective = ByeDpiFilterConfiguration(lists, lists[0].domains)
    private val advanced = selective.copy(hostFilterOverride = true)

    private fun values(arguments: Array<String>, option: String): List<String> = arguments.indices
        .filter { arguments[it] == option }.map { arguments[it + 1] }

    @Test fun filtersEveryDesyncGroupAndAddsUnmodifiedFallback() {
        val args = ByeDpiArgumentCompiler.compile("-o1 -a1 -At,r,s -d3 -As -s1", selective)
        assertEquals(listOf(":example.com example.net", ":example.com example.net", ":example.com example.net"), values(args, "-H"))
        assertEquals(listOf("t,h", "t,h", "t,h"), values(args, "-K"))
        assertEquals(listOf("t,r,s", "s", "n"), values(args, "-A"))
        assertEquals(listOf("-A", "n"), args.takeLast(2))
        assertEquals(listOf("127.0.0.1"), values(args, "-i"))
    }

    @Test fun keepsNarrowTcpProtocolAndPreventsUdpFromIgnoringHosts() {
        val args = ByeDpiArgumentCompiler.compile("-Kh -d1 -An -Ku -a2", selective)
        assertEquals(listOf("h", "t,h", "u"), values(args, "-K"))
        assertEquals(2, values(args, "-H").size)
        assertEquals(listOf("-A", "n"), args.takeLast(2))
    }

    @Test fun expandsListsInsideOneHostArgumentByNameOrId() {
        val args = ByeDpiArgumentCompiler.compile("--hosts :{list:USER} --disorder 3 --auto none", advanced)
        assertEquals(listOf(":custom.org another.org"), values(args, "-H"))
        val active = ByeDpiArgumentCompiler.compile("-H {domains} -d1", advanced)
        assertEquals(listOf(":example.com example.net"), values(active, "-H"))
    }

    @Test fun preservesLegacyFakeSniButNewFakeListsUseRepeatedNativeOptions() {
        val legacy = ByeDpiArgumentCompiler.compile("-n {sni} -f1", selective)
        assertEquals(listOf(LegacyStrategyAliases.SNI), values(legacy, "-n"))
        val generic = ByeDpiArgumentCompiler.compile("-n {list:user} -f1", selective)
        assertEquals(listOf("custom.org", "another.org"), values(generic, "-n"))
    }

    @Test fun missingEmptyAndUnknownListsFailInsteadOfBecomingUnrestricted() {
        assertThrows(IllegalArgumentException::class.java) { ByeDpiArgumentCompiler.compile("-d1", selective.copy(activeDomains = emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { ByeDpiArgumentCompiler.compile("-H {list:missing}", advanced) }
        assertThrows(IllegalArgumentException::class.java) { ByeDpiArgumentCompiler.compile("-H {list:empty}", advanced.copy(lists = listOf(DomainList("empty", "Empty", emptyList())))) }
        assertThrows(IllegalArgumentException::class.java) { ByeDpiArgumentCompiler.compile("-H :", advanced) }
    }

    @Test fun explicitAdvancedOverridePreservesWhitelistBlacklistAndDisabledFiltering() {
        assertThrows(IllegalArgumentException::class.java) { ByeDpiArgumentCompiler.compile("-H :custom.org -d1", selective) }
        val disabled = ByeDpiArgumentCompiler.compile("-d1", advanced.copy(activeDomains = emptyList()))
        assertTrue(values(disabled, "-H").isEmpty())
        val blacklisted = ByeDpiArgumentCompiler.compile("-H :custom.org -An -d1", advanced)
        assertEquals(listOf(":custom.org"), values(blacklisted, "-H"))
        assertEquals(listOf("n"), values(blacklisted, "-A"))
        assertFalse(blacklisted.takeLast(2) == listOf("-A", "n"))
    }

    @Test fun candidateListenerOverridesEmbeddedListenerAndUnsafeGroupingIsRejected() {
        val args = ByeDpiArgumentCompiler.compile("--ip=0.0.0.0 -p9999 -d1", selective, "127.0.0.1", "1082", true)
        assertEquals(listOf("127.0.0.1"), values(args, "-i"))
        assertEquals(listOf("1082"), values(args, "-p"))
        assertThrows(IllegalArgumentException::class.java) { ByeDpiArgumentCompiler.compile("--aut=n -d1", selective) }
        assertThrows(IllegalArgumentException::class.java) { ByeDpiArgumentCompiler.compile("-SAn -d1", selective) }
        assertThrows(IllegalArgumentException::class.java) { ByeDpiArgumentCompiler.compile("-B1 -d1", selective) }
    }

    @Test fun splitsQuotesBeforeListExpansionAndRejectsUnclosedQuote() {
        assertEquals(listOf("-H", ":example.com example.net", "-d1"), CommandLineTokenizer.split("-H ':example.com example.net' -d1"))
        assertThrows(IllegalArgumentException::class.java) { CommandLineTokenizer.split("-H 'example.com") }
    }
}
