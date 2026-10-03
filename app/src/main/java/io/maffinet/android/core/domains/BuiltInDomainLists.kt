package io.maffinet.android.core.domains

/** Initial data catalog; service profile ownership is introduced in the next phase. */
object BuiltInDomainLists {
    val services = listOf(
        DomainList("youtube", "YouTube", listOf("youtube.com", "googlevideo.com", "ytimg.com", "ggpht.com"), isBuiltIn = true),
        DomainList("instagram", "Instagram", listOf("instagram.com", "cdninstagram.com"), isBuiltIn = true),
        DomainList("linkedin", "LinkedIn", listOf("linkedin.com", "licdn.com"), isBuiltIn = true),
    )
    val enabledByDefault = setOf("youtube")
}
