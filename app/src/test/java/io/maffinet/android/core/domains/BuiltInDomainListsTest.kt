package io.maffinet.android.core.domains

import io.maffinet.android.core.dpibypass.ByeDpiArgumentCompiler
import io.maffinet.android.core.dpibypass.ByeDpiFilterConfiguration
import org.junit.Assert.*
import org.junit.Test

class BuiltInDomainListsTest {
    @Test fun baseIsValidAndUserExtensionHasStableDeduplicatedOrder() {
        val base = BuiltInDomainLists.general.domains
        assertTrue(base.isNotEmpty())
        assertEquals(base, DomainParser.parse(base.joinToString("\n")).domains)
        val lists = BuiltInDomainLists.configuration(listOf("YOUTUBE.COM", "custom.org"), true)
        assertEquals(base + "custom.org", lists.first { it.id == "general" }.domains)
        assertTrue(lists.first { it.id == "user" }.isActive)
    }

    @Test fun disablingUserRetainsSavedExtensionAndNeverDisablesBase() {
        val disabled = BuiltInDomainLists.configuration(listOf("custom.org"), false)
        assertEquals(BuiltInDomainLists.general.domains, disabled.first { it.id == "general" }.domains)
        assertFalse(disabled.first { it.id == "user" }.isActive)
        assertEquals(listOf("custom.org"), disabled.first { it.id == "user" }.domains)
        val restored = BuiltInDomainLists.configuration(disabled.first { it.id == "user" }.domains, true)
        assertEquals(BuiltInDomainLists.general.domains + "custom.org", restored.first { it.id == "general" }.domains)
    }

    @Test fun activeHostsReachEveryNativeGroupWhileOldAliasesRemainAvailable() {
        val lists = BuiltInDomainLists.configuration(listOf("custom.org"), true)
        val active = lists.first { it.id == "general" }.domains
        val args = ByeDpiArgumentCompiler.compile("-n {list:youtube} -d1 -As -s1", ByeDpiFilterConfiguration(lists, active))
        val filters = args.indices.filter { args[it] == "-H" }.map { args[it + 1] }
        assertEquals(listOf(":" + active.joinToString(" "), ":" + active.joinToString(" ")), filters)
        assertEquals(listOf("youtube.com", "googlevideo.com", "ytimg.com", "ggpht.com"),
            args.indices.filter { args[it] == "-n" }.map { args[it + 1] })
        assertTrue(BuiltInDomainLists.legacyAliases.all { !it.isActive })
    }
}
