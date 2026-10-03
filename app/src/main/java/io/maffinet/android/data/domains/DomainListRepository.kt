package io.maffinet.android.data.domains

import android.content.Context
import io.maffinet.android.core.domains.BuiltInDomainLists
import io.maffinet.android.core.domains.DomainList
import io.maffinet.android.core.domains.DomainParseResult
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import java.io.File

class DomainListRepository(
    private val userStore: UserDomainStore,
    private val userEnabled: () -> Boolean = { true },
    private val changeUserEnabled: (Boolean) -> Unit = {},
) {
    private val users = UserDomainRepository(userStore)
    constructor(context: Context) : this(
        FileUserDomainStore(File(context.filesDir, "maffinet-domains/user-domains.txt")),
        { MaffinetSettingsRepository(context).userDomainsEnabled() },
        { MaffinetSettingsRepository(context).setUserDomainsEnabled(it) },
    )

    fun getLists(): List<DomainList> {
        return BuiltInDomainLists.configuration(userDomains(), userEnabled())
    }

    fun activeDomains(): List<String> = getLists().first { it.id == "general" }.domains
    fun builtInDomains(): List<String> = BuiltInDomainLists.general.domains
    fun userDomains(): List<String> = users.userDomains()
    fun setUserDomainsEnabled(enabled: Boolean) = changeUserEnabled(enabled)

    /** An invalid edit never replaces the previous saved list. Empty text clears it. */
    @Synchronized fun saveUserDomains(text: String): DomainParseResult {
        return users.saveUserDomains(text)
    }

    /** Imports merge with existing domains; the editor is the replace/edit/remove path. */
    @Synchronized fun importUserDomains(text: String): DomainParseResult {
        return users.importUserDomains(text)
    }

    fun exportUserDomains(): String = users.exportUserDomains()
}
