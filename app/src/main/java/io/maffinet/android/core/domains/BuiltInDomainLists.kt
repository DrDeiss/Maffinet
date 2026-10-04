package io.maffinet.android.core.domains

/** Curated TCP host filters plus a user extension, independent of Android app routing. */
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

    // Sources and update policy: docs/HOSTS_AND_DNS.md. These are domain filters,
    // not desktop hosts IP mappings. Parent names also match their subdomains.
    val categories = listOf(
        category("video", "Видео и музыка", """
            youtube.com googlevideo.com ytimg.com ggpht.com youtu.be youtube-nocookie.com
            youtubei.googleapis.com youtube.googleapis.com youtubeembeddedplayer.googleapis.com
            youtubekids.com jnn-pa.googleapis.com wide-youtube.l.google.com youtube-ui.l.google.com
            yt-video-upload.l.google.com ytimg.l.google.com yt3.googleusercontent.com
            twitch.tv ttvnw.net live-video.net frankerfacez.com ffzap.com betterttv.net 7tv.app 7tv.io
            spotify.com scdn.co tidal.com deezer.com
        """),
        category("social", "Социальные сети", """
            instagram.com cdninstagram.com linkedin.com licdn.com facebook.com fbcdn.net
            twitter.com x.com twimg.com tiktok.com imgur.com patreon.com
        """),
        category("discord", "Discord", """
            dis.gd discord.com discord.gg discord.app discord.co discord.design discord.dev
            discord.gift discord.gifts discord.media discord.new discord.store discord.status
            discord-activities.com discordactivities.com discordapp.com discordapp.net discordcdn.com
            discordmerch.com discordpartygames.com discordsays.com discordsez.com discordstatus.com
            discord-attachments-uploads-prd.storage.googleapis.com
        """),
        category("telegram", "Telegram", """
            telegram.org telegram.me t.me tdesktop.com telegra.ph telesco.pe graph.org telegram-cdn.org
        """),
        category("ai", "Нейросети и перевод", """
            openai.com chatgpt.com oaistatic.com oaiusercontent.com cdn.openaimerge.com
            claude.ai claude.com anthropic.com gemini.google.com aistudio.google.com
            generativelanguage.googleapis.com notebooklm.google.com jules.google.com
            labs.google aitestkitchen.withgoogle.com aisandbox-pa.googleapis.com
            alkalimakersuite-pa.clients6.google.com webchannel-alkalimakersuite-pa.clients6.google.com
            copilot.microsoft.com sydney.bing.com edgeservices.bing.com grok.com x.ai
            elevenlabs.io elevenreader.io deepl.com manus.im
        """),
        category("development", "Разработка и работа", """
            github.com githubusercontent.com githubassets.com githubcopilot.com
            jetbrains.com jetbrains.ai notion.so notion.com linear.app
            windsurf.com codeium.com framer.com canva.com
        """),
        category("gaming", "Игры и Xbox", """
            xbox.com xboxlive.com supercell.com clashroyaleapp.com clashofclans.com
            brawlstarsgame.com squadbustersgame.com mocogame.com
        """),
        category("dns", "DNS и сетевые сервисы", """
            dns.google dns.quad9.net dns.nextdns.io doh.opendns.com doh.cleanbrowsing.org
            freedns.controld.com wikimedia-dns.org cloudflare-dns.com cloudflare-ech.com encryptedsni.com
        """),
    )

    // Keep the original eight domains first, preserving existing user-list merge order.
    val general = DomainList("general", "General",
        (legacyAliases + categories).flatMap { it.domains }.distinct(), isBuiltIn = true)

    private fun category(id: String, name: String, text: String) = DomainList(
        id, name, text.trim().split(Regex("\\s+")).map(DomainParser::normalize).distinct(),
        isActive = false, isBuiltIn = true,
    )

    /** General remains the active aggregate for existing {domains}/{list:general} callers. */
    fun configuration(userDomains: List<String>, userEnabled: Boolean): List<DomainList> {
        val user = DomainList("user", "User", userDomains, isActive = userEnabled)
        return listOf(general.copy(domains = DomainParser.merge(listOf(general, user))), user) + legacyAliases + categories
    }
}
