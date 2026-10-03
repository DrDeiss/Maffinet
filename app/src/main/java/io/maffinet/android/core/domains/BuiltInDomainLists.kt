package io.maffinet.android.core.domains

/** Hosts are a fixed base plus a user extension, independent of Android app routing. */
object BuiltInDomainLists {
    // Preserve names used by old {list:youtube/instagram/linkedin} strategies. These
    // aliases are never switches and never choose the active hosts configuration.
    val legacyAliases = listOf(
        DomainList("youtube", "YouTube", listOf("youtube.com", "googlevideo.com", "ytimg.com", "ggpht.com"),
            isActive = false, isBuiltIn = true),
        DomainList("instagram", "Instagram", listOf("instagram.com", "cdninstagram.com"),
            isActive = false, isBuiltIn = true),
        DomainList("linkedin", "LinkedIn", listOf("linkedin.com", "licdn.com"),
            isActive = false, isBuiltIn = true),
    )

    val general = DomainList("general", "General", legacyAliases.flatMap { it.domains }.distinct(), isBuiltIn = true)

    /** General remains the active aggregate for existing {domains}/{list:general} callers. */
    fun configuration(userDomains: List<String>, userEnabled: Boolean): List<DomainList> {
        val user = DomainList("user", "User", userDomains, isActive = userEnabled)
        return listOf(general.copy(domains = DomainParser.merge(listOf(general, user))), user) + legacyAliases
    }
}
