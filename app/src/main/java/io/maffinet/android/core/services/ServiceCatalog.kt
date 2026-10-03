package io.maffinet.android.core.services

import io.maffinet.android.core.domains.DomainList
import io.maffinet.android.core.domains.DomainParser

/** Add a service here; routing, lists, UI and strategy targets consume the same records. */
object ServiceCatalog {
    val profiles = listOf(
        ServiceProfile(
            id = "youtube", name = "YouTube", provider = "Google",
            packages = setOf(
                "org.smarttube.stable", "org.smarttube.beta", "com.google.android.youtube",
                "com.google.android.youtube.tv", "com.liskovsoft.videomanager.v2",
                "com.liskovsoft.smarttubetv.beta", "com.teamsmart.videomanager.tv",
                "app.revanced.android.youtube", "com.google.android.apps.youtube.music",
                "com.google.android.apps.youtube.kids", "org.schabi.newpipe",
                "org.schabi.newpipe.legacy", "com.kapp.youtube", "com.bg.vanced",
                "com.libretube", "com.liskovsoft.smarttubetv",
            ),
            domains = linkedSetOf("youtube.com", "googlevideo.com", "ytimg.com", "ggpht.com"),
            testUrls = listOf("https://www.youtube.com"), enabledByDefault = true,
        ),
        ServiceProfile(
            id = "instagram", name = "Instagram", provider = "Meta",
            packages = setOf("com.instagram.android"),
            domains = linkedSetOf("instagram.com", "cdninstagram.com"),
            testUrls = listOf("https://www.instagram.com"),
        ),
        ServiceProfile(
            id = "linkedin", name = "LinkedIn", provider = "LinkedIn",
            // Verified against https://play.google.com/store/apps/details?id=com.linkedin.android
            packages = setOf("com.linkedin.android"),
            domains = linkedSetOf("linkedin.com", "licdn.com"),
            testUrls = listOf("https://www.linkedin.com"),
        ),
    )

    val enabledByDefault: Set<String> get() = profiles.filter { it.enabledByDefault }.map { it.id }.toSet()
    fun get(id: String): ServiceProfile? = profiles.firstOrNull { it.id == id }
    fun enabledProfiles(ids: Set<String>): List<ServiceProfile> = profiles.filter { it.id in ids }

    fun domainLists(enabledIds: Set<String>, userDomains: List<String>, userEnabled: Boolean): List<DomainList> =
        domainLists(profiles, enabledIds, userDomains, userEnabled)

    fun domainLists(
        profiles: List<ServiceProfile>, enabledIds: Set<String>, userDomains: List<String>, userEnabled: Boolean,
    ): List<DomainList> {
        val services = profiles.map {
            DomainList(it.id, it.name, it.domains.toList(), isActive = it.id in enabledIds, isBuiltIn = true)
        }
        val user = DomainList("user", "User", userDomains, isActive = userEnabled)
        val general = DomainList("general", "General", DomainParser.merge(services + user), isBuiltIn = true)
        return listOf(general, user) + services
    }
}
