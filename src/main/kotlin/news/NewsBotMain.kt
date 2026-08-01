package news

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TELEGRAM_MAX_LEN = 3500

fun main() {
    logSection("Timestamp & last check")
    val lastCheck = Timestamp.readLastCheck()
    logInfo("Last check instant: $lastCheck")
    val githubTrendingLastSentDate = Timestamp.readGithubTrendingLastDate()
    logInfo("GitHub Trending last sent date: $githubTrendingLastSentDate")
    val shouldSendGithubTrending = Timestamp.shouldSendGithubTrending(
        lastSentDate = githubTrendingLastSentDate
    )
    endSection()

    logSection("Collect sources")
    val seenTitles = mutableSetOf<String>()

    val youtubeItems = collectItems(
        enabled = YOUTUBE_ENABLED,
        name = "YouTube",
        lastCheck = lastCheck,
        provider = YoutubeProvider,
        seenTitles = seenTitles
    )

    val androidBlogItems = collectItems(
        enabled = ANDROID_BLOG_ENABLED,
        name = "Android Developers Blog",
        lastCheck = lastCheck,
        provider = AndroidBlogProvider,
        seenTitles = seenTitles
    )

    val androidxReleaseNotesItems = collectItems(
        enabled = ANDROIDX_RELEASE_NOTES_ENABLED,
        name = "AndroidX Release Notes",
        lastCheck = lastCheck,
        provider = AndroidxReleaseNotesProvider,
        seenTitles = seenTitles
    )

    val androidStudioBlogItems = collectItems(
        enabled = ANDROID_STUDIO_BLOG_ENABLED,
        name = "Android Studio Blog",
        lastCheck = lastCheck,
        provider = AndroidStudioBlogProvider,
        seenTitles = seenTitles
    )

    val androidAuthorityItems = collectItems(
        enabled = ANDROID_AUTHORITY_ENABLED,
        name = "Android Authority",
        lastCheck = lastCheck,
        provider = AndroidAuthorityProvider,
        seenTitles = seenTitles
    )

    val androidPoliceItems = collectItems(
        enabled = ANDROID_POLICE_ENABLED,
        name = "Android Police",
        lastCheck = lastCheck,
        provider = AndroidPoliceProvider,
        seenTitles = seenTitles
    )

    val firebaseBlogItems = collectItems(
        enabled = FIREBASE_BLOG_ENABLED,
        name = "Firebase Blog",
        lastCheck = lastCheck,
        provider = FirebaseBlogProvider,
        seenTitles = seenTitles
    )

    val kotlinBlogItems = collectItems(
        enabled = KOTLIN_BLOG_ENABLED,
        name = "Kotlin Blog",
        lastCheck = lastCheck,
        provider = KotlinBlogProvider,
        seenTitles = seenTitles
    )

    val kotlinDiscussionsItems = collectItems(
        enabled = KOTLIN_DISCUSSIONS_ENABLED,
        name = "Kotlin Discussions",
        lastCheck = lastCheck,
        provider = KotlinDiscussionsProvider,
        seenTitles = seenTitles
    )

    val gradleBlogItems = collectItems(
        enabled = GRADLE_BLOG_ENABLED,
        name = "Gradle Blog",
        lastCheck = lastCheck,
        provider = GradleBlogProvider,
        seenTitles = seenTitles
    )

    val githubBlogItems = collectItems(
        enabled = GITHUB_BLOG_ENABLED,
        name = "GitHub Blog",
        lastCheck = lastCheck,
        provider = GithubBlogProvider,
        seenTitles = seenTitles
    )

    val jetBrainsBlogItems = collectItems(
        enabled = JETBRAINS_BLOG_ENABLED,
        name = "JetBrains Blog",
        lastCheck = lastCheck,
        provider = JetBrainsBlogProvider,
        seenTitles = seenTitles
    )

    val mediumGoogleItems = collectItems(
        enabled = MEDIUM_GOOGLE_ENABLED,
        name = "Medium Google",
        lastCheck = lastCheck,
        provider = MediumGoogleProvider,
        seenTitles = seenTitles
    )

    val mediumAndroidItems = collectItems(
        enabled = MEDIUM_ANDROID_ENABLED,
        name = "Medium Android",
        lastCheck = lastCheck,
        provider = MediumAndroidProvider,
        seenTitles = seenTitles
    )

    val devToAndroidItems = collectItems(
        enabled = DEV_TO_ANDROID_ENABLED,
        name = "Dev.to Android",
        lastCheck = lastCheck,
        provider = DevToProvider,
        seenTitles = seenTitles
    )

    val androidWeeklyItems = collectItems(
        enabled = ANDROID_WEEKLY_ENABLED,
        name = "Android Weekly",
        lastCheck = lastCheck,
        provider = AndroidWeeklyProvider,
        seenTitles = seenTitles
    )

    val nineToFiveGoogleItems = collectItems(
        enabled = NINE_TO_FIVE_GOOGLE_ENABLED,
        name = "9to5Google",
        lastCheck = lastCheck,
        provider = NineToFiveGoogleProvider,
        seenTitles = seenTitles
    )

    val proAndroidDevItems = collectItems(
        enabled = PRO_ANDROID_DEV_ENABLED,
        name = "ProAndroidDev",
        lastCheck = lastCheck,
        provider = ProAndroidDevProvider,
        seenTitles = seenTitles
    )

    val techRadarItems = collectItems(
        enabled = TECHRADAR_ANDROID_ENABLED,
        name = "TechRadar Android",
        lastCheck = lastCheck,
        provider = TechRadarProvider,
        seenTitles = seenTitles
    )

    val habrAndroidItems = collectItems(
        enabled = HABR_ANDROID_ENABLED,
        name = "HABR ANDROID",
        lastCheck = lastCheck,
        provider = HabrAndroidProvider,
        seenTitles = seenTitles
    )

    val habrAiItems = collectItems(
        enabled = HABR_AI_ENABLED,
        name = "HABR AI",
        lastCheck = lastCheck,
        provider = HabrAiProvider,
        seenTitles = seenTitles
    )

    val habrProgrammingItems = collectItems(
        enabled = HABR_PROGRAMMING_ENABLED,
        name = "HABR PROGRAMMING",
        lastCheck = lastCheck,
        provider = HabrProgrammingProvider,
        seenTitles = seenTitles
    )

    val habrCareerItems = collectItems(
        enabled = HABR_CAREER_ENABLED,
        name = "HABR CAREER",
        lastCheck = lastCheck,
        provider = HabrCareerProvider,
        seenTitles = seenTitles
    )

    val vcRuCareerItems = collectItems(
        enabled = VC_RU_CAREER_ENABLED,
        name = "VC.RU CAREER",
        lastCheck = lastCheck,
        provider = VcRuCareerProvider,
        seenTitles = seenTitles
    )

    val vcRuDevelopmentItems = collectItems(
        enabled = VC_RU_DEVELOPMENT_ENABLED,
        name = "VC.RU DEVELOPMENT",
        lastCheck = lastCheck,
        provider = VcRuDevelopmentProvider,
        seenTitles = seenTitles
    )

    val dtfSoftwareItems = collectItems(
        enabled = DTF_SOFTWARE_ENABLED,
        name = "DTF SOFTWARE",
        lastCheck = lastCheck,
        provider = DtfSoftwareProvider,
        seenTitles = seenTitles
    )

    val dtfMobileItems = collectItems(
        enabled = DTF_MOBILE_ENABLED,
        name = "DTF MOBILE",
        lastCheck = lastCheck,
        provider = DtfMobileProvider,
        seenTitles = seenTitles
    )

    val githubReleaseItems = collectItems(
        enabled = GITHUB_RELEASES_ENABLED,
        name = "GitHub releases",
        lastCheck = lastCheck,
        provider = GithubReleasesProvider,
        seenTitles = seenTitles
    )

    val githubTrendingKotlinItems = collectItems(
        enabled = GITHUB_TRENDING_KOTLIN_ENABLED && shouldSendGithubTrending,
        name = "GitHub Trending: Kotlin",
        lastCheck = lastCheck,
        provider = GithubTrendingKotlinProvider,
        seenTitles = seenTitles
    )

    val githubTrendingAllItems = collectItems(
        enabled = GITHUB_TRENDING_ALL_ENABLED && shouldSendGithubTrending,
        name = "GitHub Trending: All",
        lastCheck = lastCheck,
        provider = GithubTrendingAllProvider,
        seenTitles = seenTitles
    )

    val redditAndroidDevItems = collectItems(
        enabled = REDDIT_ANDROIDDEV_ENABLED,
        name = "Reddit: androiddev",
        lastCheck = lastCheck,
        provider = RedditAndroidDevProvider,
        seenTitles = seenTitles
    )

    val redditKotlinItems = collectItems(
        enabled = REDDIT_KOTLIN_ENABLED,
        name = "Reddit: Kotlin",
        lastCheck = lastCheck,
        provider = RedditKotlinProvider,
        seenTitles = seenTitles
    )

    val redditMobileDevItems = collectItems(
        enabled = REDDIT_MOBILEDEV_ENABLED,
        name = "Reddit: mobiledev",
        lastCheck = lastCheck,
        provider = RedditMobileDevProvider,
        seenTitles = seenTitles
    )

    endSection()

    logSection("Build messages")
    val messages = buildMessages(
        youtubeItems = youtubeItems,
        androidBlogItems = androidBlogItems,
        androidxReleaseNotesItems = androidxReleaseNotesItems,
        androidStudioBlogItems = androidStudioBlogItems,
        androidAuthorityItems = androidAuthorityItems,
        androidPoliceItems = androidPoliceItems,
        firebaseBlogItems = firebaseBlogItems,
        kotlinBlogItems = kotlinBlogItems,
        kotlinDiscussionsItems = kotlinDiscussionsItems,
        gradleBlogItems = gradleBlogItems,
        githubBlogItems = githubBlogItems,
        jetBrainsBlogItems = jetBrainsBlogItems,
        mediumGoogleItems = mediumGoogleItems,
        mediumAndroidItems = mediumAndroidItems,
        devToAndroidItems = devToAndroidItems,
        androidWeeklyItems = androidWeeklyItems,
        nineToFiveGoogleItems = nineToFiveGoogleItems,
        proAndroidDevItems = proAndroidDevItems,
        techRadarItems = techRadarItems,
        habrAndroidItems = habrAndroidItems,
        habrAiItems = habrAiItems,
        habrProgrammingItems = habrProgrammingItems,
        habrCareerItems = habrCareerItems,
        vcRuCareerItems = vcRuCareerItems,
        vcRuDevelopmentItems = vcRuDevelopmentItems,
        dtfSoftwareItems = dtfSoftwareItems,
        dtfMobileItems = dtfMobileItems,
        githubReleaseItems = githubReleaseItems,
        githubTrendingKotlinItems = githubTrendingKotlinItems,
        githubTrendingAllItems = githubTrendingAllItems,
        redditAndroidDevItems = redditAndroidDevItems,
        redditKotlinItems = redditKotlinItems,
        redditMobileDevItems = redditMobileDevItems,
        youtubeEnabled = YOUTUBE_ENABLED,
        androidBlogEnabled = ANDROID_BLOG_ENABLED,
        androidxReleaseNotesEnabled = ANDROIDX_RELEASE_NOTES_ENABLED,
        androidStudioBlogEnabled = ANDROID_STUDIO_BLOG_ENABLED,
        androidAuthorityEnabled = ANDROID_AUTHORITY_ENABLED,
        androidPoliceEnabled = ANDROID_POLICE_ENABLED,
        firebaseBlogEnabled = FIREBASE_BLOG_ENABLED,
        kotlinBlogEnabled = KOTLIN_BLOG_ENABLED,
        kotlinDiscussionsEnabled = KOTLIN_DISCUSSIONS_ENABLED,
        gradleBlogEnabled = GRADLE_BLOG_ENABLED,
        githubBlogEnabled = GITHUB_BLOG_ENABLED,
        jetBrainsBlogEnabled = JETBRAINS_BLOG_ENABLED,
        mediumGoogleEnabled = MEDIUM_GOOGLE_ENABLED,
        mediumAndroidEnabled = MEDIUM_ANDROID_ENABLED,
        devToAndroidEnabled = DEV_TO_ANDROID_ENABLED,
        androidWeeklyEnabled = ANDROID_WEEKLY_ENABLED,
        nineToFiveGoogleEnabled = NINE_TO_FIVE_GOOGLE_ENABLED,
        proAndroidDevEnabled = PRO_ANDROID_DEV_ENABLED,
        techRadarEnabled = TECHRADAR_ANDROID_ENABLED,
        habrAndroidEnabled = HABR_ANDROID_ENABLED,
        habrAiEnabled = HABR_AI_ENABLED,
        habrProgrammingEnabled = HABR_PROGRAMMING_ENABLED,
        habrCareerEnabled = HABR_CAREER_ENABLED,
        vcRuCareerEnabled = VC_RU_CAREER_ENABLED,
        vcRuDevelopmentEnabled = VC_RU_DEVELOPMENT_ENABLED,
        dtfSoftwareEnabled = DTF_SOFTWARE_ENABLED,
        dtfMobileEnabled = DTF_MOBILE_ENABLED,
        githubReleasesEnabled = GITHUB_RELEASES_ENABLED,
        githubTrendingKotlinEnabled = GITHUB_TRENDING_KOTLIN_ENABLED && shouldSendGithubTrending,
        githubTrendingAllEnabled = GITHUB_TRENDING_ALL_ENABLED && shouldSendGithubTrending,
        redditAndroidDevEnabled = REDDIT_ANDROIDDEV_ENABLED,
        redditKotlinEnabled = REDDIT_KOTLIN_ENABLED,
        redditMobileDevEnabled = REDDIT_MOBILEDEV_ENABLED
    )
    logInfo("Built messages count: ${messages.size}")
    endSection()

    if (messages.isEmpty()) {
        logInfo("Новостей нет, ничего не отправляем в Telegram")
        return
    }

    logSection("Send to Telegram")
    val sent = sendTelegram(messages)
    endSection()

    val githubTrendingItemsSent = sent && (
        githubTrendingKotlinItems.isNotEmpty() ||
            githubTrendingAllItems.isNotEmpty()
    )
    if (githubTrendingItemsSent) {
        Timestamp.writeGithubTrendingLastDate()
    }
}

private fun buildMessages(
    youtubeItems: List<SimpleNewsItem>,
    androidBlogItems: List<SimpleNewsItem>,
    androidxReleaseNotesItems: List<SimpleNewsItem>,
    androidStudioBlogItems: List<SimpleNewsItem>,
    androidAuthorityItems: List<SimpleNewsItem>,
    androidPoliceItems: List<SimpleNewsItem>,
    firebaseBlogItems: List<SimpleNewsItem>,
    kotlinBlogItems: List<SimpleNewsItem>,
    kotlinDiscussionsItems: List<SimpleNewsItem>,
    gradleBlogItems: List<SimpleNewsItem>,
    githubBlogItems: List<SimpleNewsItem>,
    jetBrainsBlogItems: List<SimpleNewsItem>,
    mediumGoogleItems: List<SimpleNewsItem>,
    mediumAndroidItems: List<SimpleNewsItem>,
    devToAndroidItems: List<SimpleNewsItem>,
    androidWeeklyItems: List<SimpleNewsItem>,
    nineToFiveGoogleItems: List<SimpleNewsItem>,
    proAndroidDevItems: List<SimpleNewsItem>,
    techRadarItems: List<SimpleNewsItem>,
    habrAndroidItems: List<SimpleNewsItem>,
    habrAiItems: List<SimpleNewsItem>,
    habrProgrammingItems: List<SimpleNewsItem>,
    habrCareerItems: List<SimpleNewsItem>,
    vcRuCareerItems: List<SimpleNewsItem>,
    vcRuDevelopmentItems: List<SimpleNewsItem>,
    dtfSoftwareItems: List<SimpleNewsItem>,
    dtfMobileItems: List<SimpleNewsItem>,
    githubReleaseItems: List<GithubReleaseItem>,
    githubTrendingKotlinItems: List<GithubTrendingItem>,
    githubTrendingAllItems: List<GithubTrendingItem>,
    redditAndroidDevItems: List<SimpleNewsItem>,
    redditKotlinItems: List<SimpleNewsItem>,
    redditMobileDevItems: List<SimpleNewsItem>,
    youtubeEnabled: Boolean,
    androidBlogEnabled: Boolean,
    androidxReleaseNotesEnabled: Boolean,
    androidStudioBlogEnabled: Boolean,
    androidAuthorityEnabled: Boolean,
    androidPoliceEnabled: Boolean,
    firebaseBlogEnabled: Boolean,
    kotlinBlogEnabled: Boolean,
    kotlinDiscussionsEnabled: Boolean,
    gradleBlogEnabled: Boolean,
    githubBlogEnabled: Boolean,
    jetBrainsBlogEnabled: Boolean,
    mediumGoogleEnabled: Boolean,
    mediumAndroidEnabled: Boolean,
    devToAndroidEnabled: Boolean,
    androidWeeklyEnabled: Boolean,
    nineToFiveGoogleEnabled: Boolean,
    proAndroidDevEnabled: Boolean,
    techRadarEnabled: Boolean,
    habrAndroidEnabled: Boolean,
    habrAiEnabled: Boolean,
    habrProgrammingEnabled: Boolean,
    habrCareerEnabled: Boolean,
    vcRuCareerEnabled: Boolean,
    vcRuDevelopmentEnabled: Boolean,
    dtfSoftwareEnabled: Boolean,
    dtfMobileEnabled: Boolean,
    githubReleasesEnabled: Boolean,
    githubTrendingKotlinEnabled: Boolean,
    githubTrendingAllEnabled: Boolean,
    redditAndroidDevEnabled: Boolean,
    redditKotlinEnabled: Boolean,
    redditMobileDevEnabled: Boolean
): List<TelegramMessage> {
    val zone = ZoneId.of("Europe/Moscow")
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM 'в' HH:mm")

    val sections = listOf(
        MessageSection(
            header = "▶️ *YOUTUBE*\n\n",
            enabled = youtubeEnabled,
            items = youtubeItems,
            rich = true,
            formatLine = ::richLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321471557850130882\">▶️</tg-emoji> <b>ANDROID DEVELOPERS BLOG</b>")
                append("\n\n")
            },
            enabled = androidBlogEnabled,
            items = androidBlogItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321471557850130882\">▶️</tg-emoji> <b>ANDROIDX RELEASE NOTES</b>")
                append("\n\n")
            },
            enabled = androidxReleaseNotesEnabled,
            items = androidxReleaseNotesItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5323664267503756290\">▶️</tg-emoji> <b>ANDROID STUDIO BLOG</b>")
                append("\n\n")
            },
            enabled = androidStudioBlogEnabled,
            items = androidStudioBlogItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321471557850130882\">▶️</tg-emoji> <b>ANDROID AUTHORITY</b>")
                append("\n\n")
            },
            enabled = androidAuthorityEnabled,
            items = androidAuthorityItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5190702632754253267\">▶️</tg-emoji> <b>ANDROID POLICE</b>")
                append("\n\n")
            },
            enabled = androidPoliceEnabled,
            items = androidPoliceItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321379748629205271\">▶️</tg-emoji> <b>FIREBASE BLOG</b>")
                append("\n\n")
            },
            enabled = firebaseBlogEnabled,
            items = firebaseBlogItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5208461085572632559\">▶️</tg-emoji> <b>KOTLIN BLOG</b>")
                append("\n\n")
            },
            enabled = kotlinBlogEnabled,
            items = kotlinBlogItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5208461085572632559\">▶️</tg-emoji> <b>KOTLIN DISCUSSIONS</b>")
                append("\n\n")
            },
            enabled = kotlinDiscussionsEnabled,
            items = kotlinDiscussionsItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321469715309152734\">▶️</tg-emoji> <b>GRADLE BLOG</b>")
                append("\n\n")
            },
            enabled = gradleBlogEnabled,
            items = gradleBlogItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321190645514130346\">▶️</tg-emoji> <b>GITHUB BLOG</b>")
                append("\n\n")
            },
            enabled = githubBlogEnabled,
            items = githubBlogItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5332725171029560146\">▶️</tg-emoji> <b>JETBRAINS BLOG</b>")
                append("\n\n")
            },
            enabled = jetBrainsBlogEnabled,
            items = jetBrainsBlogItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5208912452275701983\">▶️</tg-emoji> <b>GOOGLE DEVELOPER EXPERTS MEDIUM</b>")
                append("\n\n")
            },
            enabled = mediumGoogleEnabled,
            items = mediumGoogleItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5368646963233312962\">▶️</tg-emoji> <b>ANDROID DEVELOPERS MEDIUM</b>")
                append("\n\n")
            },
            enabled = mediumAndroidEnabled,
            items = mediumAndroidItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321105600866716102\">▶️</tg-emoji> <b>DEVTO</b>")
                append("\n\n")
            },
            enabled = devToAndroidEnabled,
            items = devToAndroidItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321413970928621177\">▶️</tg-emoji> <b>9TO5GOOGLE</b>")
                append("\n\n")
            },
            enabled = nineToFiveGoogleEnabled,
            items = nineToFiveGoogleItems,
            formatLine = ::defaultLineWithoutTags
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321471557850130882\">▶️</tg-emoji> <b>ANDROID WEEKLY</b>")
                append("\n\n")
            },
            enabled = androidWeeklyEnabled,
            items = androidWeeklyItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5368646963233312962\">▶️</tg-emoji> <b>PROANDROIDDEV</b>")
                append("\n\n")
            },
            enabled = proAndroidDevEnabled,
            items = proAndroidDevItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321189082146051942\">▶️</tg-emoji> <b>TECHRADAR</b>")
                append("\n\n")
            },
            enabled = techRadarEnabled,
            items = techRadarItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321324279126577604\">▶️</tg-emoji> <b>HABR ANDROID</b>")
                append("\n\n")
            },
            enabled = habrAndroidEnabled,
            items = habrAndroidItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321324279126577604\">▶️</tg-emoji> <b>HABR AI</b>")
                append("\n\n")
            },
            enabled = habrAiEnabled,
            items = habrAiItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321324279126577604\">▶️</tg-emoji> <b>HABR PROGRAMMING</b>")
                append("\n\n")
            },
            enabled = habrProgrammingEnabled,
            items = habrProgrammingItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321324279126577604\">▶️</tg-emoji> <b>HABR CAREER</b>")
                append("\n\n")
            },
            enabled = habrCareerEnabled,
            items = habrCareerItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = "▶️ *VCRU КАРЬЕРА*\n\n",
            enabled = vcRuCareerEnabled,
            items = vcRuCareerItems,
            rich = true,
            formatLine = ::richLine
        ),
        MessageSection(
            header = "▶️ *VCRU РАЗРАБОТКА*\n\n",
            enabled = vcRuDevelopmentEnabled,
            items = vcRuDevelopmentItems,
            rich = true,
            formatLine = ::richLine
        ),
        MessageSection(
            header = "▶️ *DTF СОФТ*\n\n",
            enabled = dtfSoftwareEnabled,
            items = dtfSoftwareItems,
            rich = true,
            formatLine = ::richLine
        ),
        MessageSection(
            header = "▶️ *DTF МОБАЙЛ*\n\n",
            enabled = dtfMobileEnabled,
            items = dtfMobileItems,
            rich = true,
            formatLine = ::richLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321190645514130346\">▶️</tg-emoji> <b>GITHUB RELEASES</b>")
                append("\n\n")
            },
            enabled = githubReleasesEnabled,
            items = githubReleaseItems,
            formatLine = ::formatGithubLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321190645514130346\">▶️</tg-emoji> <b>GITHUB TRENDING: KOTLIN</b>")
                append("\n\n")
            },
            enabled = githubTrendingKotlinEnabled,
            items = githubTrendingKotlinItems,
            formatLine = { item, _, _ -> formatGithubTrendingLine(item) }
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321190645514130346\">▶️</tg-emoji> <b>GITHUB TRENDING: ALL</b>")
                append("\n\n")
            },
            enabled = githubTrendingAllEnabled,
            items = githubTrendingAllItems,
            formatLine = { item, _, _ -> formatGithubTrendingLine(item) }
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5323326266462457730\">▶️</tg-emoji> <b>REDDIT: ANDROIDDEV</b>")
                append("\n\n")
            },
            enabled = redditAndroidDevEnabled,
            items = redditAndroidDevItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5323326266462457730\">▶️</tg-emoji> <b>REDDIT: KOTLIN</b>")
                append("\n\n")
            },
            enabled = redditKotlinEnabled,
            items = redditKotlinItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5323326266462457730\">▶️</tg-emoji> <b>REDDIT: MOBILEDEV</b>")
                append("\n\n")
            },
            enabled = redditMobileDevEnabled,
            items = redditMobileDevItems,
            formatLine = ::defaultLine
        )
    )

    return sections.flatMap { section -> buildSectionMessages(section, zone, dateFormatter) }
}

private data class TelegramMessage(
    val text: String,
    val rich: Boolean
)

private data class MessageSection<T: NewsItem>(
    val header: String,
    val enabled: Boolean,
    val items: List<T>,
    val rich: Boolean = false,
    val formatLine: (T, ZoneId, DateTimeFormatter) -> String
)

private fun defaultLine(
    item: NewsItem,
    zone: ZoneId,
    dateFormatter: DateTimeFormatter
): String = buildDefaultLine(item, zone, dateFormatter)

private fun defaultLineWithoutTags(
    item: NewsItem,
    zone: ZoneId,
    dateFormatter: DateTimeFormatter
): String = buildDefaultLine(item, zone, dateFormatter)

private fun buildDefaultLine(
    item: NewsItem,
    zone: ZoneId,
    dateFormatter: DateTimeFormatter
): String {
    val local = item.published.atZone(zone)
    val dateStr = local.format(dateFormatter)
    return buildString {
        append("<a href=\"")
        append(escapeHtml(item.url))
        append("\">")
        append(escapeHtml(item.title))
        append("</a>")
        append("\n")
        append(dateStr)
        append("\n\n")
    }
}

private fun richLine(
    item: NewsItem,
    zone: ZoneId,
    dateFormatter: DateTimeFormatter
): String = buildRichLine(item, zone, dateFormatter)

private fun buildRichLine(
    item: NewsItem,
    zone: ZoneId,
    dateFormatter: DateTimeFormatter
): String {
    val local = item.published.atZone(zone)
    val dateStr = local.format(dateFormatter)
    return buildString {
        append("[")
        append(escapeMarkdown(item.title))
        append("](")
        append(safeMarkdownUrl(item.url))
        append(")")
        append("\n")
        append(dateStr)
        append("\n")
        val imageUrl = item.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            append("\n![](")
            append(safeMarkdownUrl(imageUrl))
            append(")\n")
        }
        append("\n")
    }
}

private fun escapeMarkdown(text: String): String {
    val specialChars = "\\`*_{}[]()#+-.!>~|"
    return buildString {
        for (c in text) {
            if (c in specialChars) append('\\')
            append(c)
        }
    }
}

private fun safeMarkdownUrl(url: String): String =
    url.replace(")", "%29").replace(" ", "%20")

private fun formatGithubLine(
    item: GithubReleaseItem,
    zone: ZoneId,
    dateFormatter: DateTimeFormatter
): String {
    val local = item.published.atZone(zone)
    val dateStr = local.format(dateFormatter)
    return buildString {
        append("<a href=\"")
        append(escapeHtml(item.url))
        append("\">")
        append(escapeHtml("${item.repo}: ${item.title}"))
        append("</a>")
        append("\n")
        append(dateStr)
        append("\n\n")
    }
}

private fun formatGithubTrendingLine(item: GithubTrendingItem): String {
    return buildString {
        append("<a href=\"")
        append(escapeHtml(item.url))
        append("\">")
        append(escapeHtml(item.title))
        append("</a>")
        append("\n")
        if (!item.description.isNullOrBlank()) {
            append(escapeHtml(item.description))
            append("\n")
        }
        append("⭐️${item.stars}")
        append(" ")
        append("👤${item.forks}")
        append("\n\n")
    }
}

private fun <T: NewsItem> buildSectionMessages(
    section: MessageSection<T>,
    zone: ZoneId,
    dateFormatter: DateTimeFormatter
): List<TelegramMessage> {
    if (!section.enabled) return emptyList()
    if (section.items.isEmpty()) return emptyList()

    val builder = StringBuilder()
    val result = mutableListOf<TelegramMessage>()
    var isFirstChunk = true

    fun flushChunk() {
        val text = builder.toString().trim()
        if (text.isNotEmpty()) {
            result += TelegramMessage(text, section.rich)
        }
        builder.setLength(0)
    }

    for (item in section.items) {
        val line = section.formatLine(item, zone, dateFormatter)
        if (builder.isNotEmpty() && builder.length + line.length > TELEGRAM_MAX_LEN) {
            flushChunk()
            isFirstChunk = false
        }
        if (builder.isEmpty() && isFirstChunk) {
            builder.append(section.header)
        }
        builder.append(line)
    }

    flushChunk()

    return result
}

private fun <T: NewsItem> collectItems(
    enabled: Boolean,
    name: String,
    lastCheck: Instant,
    provider: NewsProvider<T>,
    seenTitles: MutableSet<String>
): List<T> {
    if (!enabled) {
        logInfo("$name parsing disabled by feature flag")
        return emptyList()
    }

    val items = provider.fetchItems(lastCheck)
    val deduped = items.filter { item ->
        val key = normalizeForDedup(item.title)
        key.isEmpty() || seenTitles.add(key)
    }
    val duplicatesRemoved = items.size - deduped.size
    if (duplicatesRemoved > 0) {
        logInfo("$name duplicates removed (already seen in earlier source): $duplicatesRemoved")
    }
    logInfo("$name items collected (after filter): ${deduped.size}")
    return deduped
}

private fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

private fun extractRetryAfterSeconds(body: String): Long? {
    val match = Regex("\"retry_after\"\\s*:\\s*(\\d+)").find(body) ?: return null
    val value = match.groupValues.getOrNull(1) ?: return null
    return value.toLongOrNull()
}

private fun jsonEscape(text: String): String {
    return text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
}

private fun sendTelegram(messages: List<TelegramMessage>): Boolean {
    val token = System.getenv("TELEGRAM_TOKEN").orEmpty()
    val chatId = System.getenv("CHAT_ID").orEmpty()
    val threadId = System.getenv("THREAD_ID").orEmpty()

    if (token.isBlank() || chatId.isBlank()) {
        logWarn("TELEGRAM_TOKEN or CHAT_ID not set, skip send")
        return false
    }

    val client = HttpClient.newHttpClient()

    messages.forEachIndexed { index, message ->
        logInfo("Preparing to send Telegram message #${index + 1}")
        logInfo("Text length = ${message.text.length}, rich = ${message.rich}")

        val method = if (message.rich) "sendRichMessage" else "sendMessage"
        val url = "https://api.telegram.org/bot$token/$method"

        val payload = buildString {
            append("{")
            append("\"chat_id\":\"").append(chatId).append("\",")
            if (threadId.isNotBlank()) {
                append("\"message_thread_id\":\"").append(threadId).append("\",")
            }
            if (message.rich) {
                append("\"rich_message\":{\"markdown\":\"")
                append(jsonEscape(message.text))
                append("\"}")
            } else {
                append("\"text\":\"").append(jsonEscape(message.text)).append("\",")
                append("\"disable_web_page_preview\":true,")
                append("\"parse_mode\":\"HTML\"")
            }
            append("}")
        }

        logInfo("Payload for Telegram #${index + 1}: $payload")

        var attempt = 0
        val maxRetries = 3

        while (true) {
            attempt++

            val request = HttpRequest.newBuilder()
                .uri(URI(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            val status = response.statusCode()
            val body = response.body()

            logInfo("Telegram response #${index + 1}: $status")
            logInfo("Telegram body #${index + 1}: $body")

            if (status == 200) break

            if (status == 429 && attempt <= maxRetries) {
                val retryAfter = extractRetryAfterSeconds(body) ?: 1L
                logWarn("Telegram 429 for message #${index + 1}, retry after $retryAfter seconds (attempt $attempt of $maxRetries)")
                Thread.sleep(retryAfter * 1000L)
                continue
            }

            logError("Telegram send failed for message #${index + 1} with status $status")
            error("Telegram send failed for message #${index + 1} with status $status")
        }
    }

    return true
}
