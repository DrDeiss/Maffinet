package io.maffinet.android.core.domains

/** Preserve imported NetFix commands exactly; this fake-SNI value is not a host filter. */
object LegacyStrategyAliases {
    const val SNI = "youtube.com,googlevideo.com,ytimg.com,ggpht.com,google.com"
    fun expand(command: String): String = command.replace("{sni}", SNI)
}
