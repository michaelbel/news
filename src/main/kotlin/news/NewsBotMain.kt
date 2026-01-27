package news

import news.androidauthority.AndroidAuthorityItem
import news.androidauthority.AndroidAuthorityProvider
import news.androidblog.AndroidBlogItem
import news.androidblog.AndroidBlogProvider
import news.androidstudioblog.AndroidStudioBlogItem
import news.androidstudioblog.AndroidStudioBlogProvider
import news.androidweekly.AndroidWeeklyItem
import news.androidweekly.AndroidWeeklyProvider
import news.devto.DevToItem
import news.devto.DevToProvider
import news.firebaseblog.FirebaseBlogItem
import news.firebaseblog.FirebaseBlogProvider
import news.github.GithubReleaseItem
import news.github.GithubReleasesProvider
import news.githubblog.GithubBlogItem
import news.githubblog.GithubBlogProvider
import news.githubtrending.GithubTrendingKotlinItem
import news.githubtrending.GithubTrendingKotlinProvider
import news.habr.HabrAiItem
import news.habr.HabrAiProvider
import news.habr.HabrAndroidItem
import news.habr.HabrAndroidProvider
import news.habr.HabrCareerItem
import news.habr.HabrCareerProvider
import news.habr.HabrProgrammingItem
import news.habr.HabrProgrammingProvider
import news.jetbrainsblog.JetBrainsBlogItem
import news.jetbrainsblog.JetBrainsBlogProvider
import news.kotlinblog.KotlinBlogItem
import news.kotlinblog.KotlinBlogProvider
import news.mediumandroid.MediumAndroidItem
import news.mediumandroid.MediumAndroidProvider
import news.mediumgoogle.MediumGoogleItem
import news.mediumgoogle.MediumGoogleProvider
import news.ninetofivegoogle.NineToFiveGoogleItem
import news.ninetofivegoogle.NineToFiveGoogleProvider
import news.proandroiddev.ProAndroidDevItem
import news.proandroiddev.ProAndroidDevProvider
import news.techradar.TechRadarItem
import news.techradar.TechRadarProvider
import news.youtube.YoutubeItem
import news.youtube.YoutubeProvider
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TELEGRAM_MAX_LEN = 3500

fun main() {
    logSection("Timestamp & last check")
    val lastCheck = Timestamp.readLastCheck()
    logInfo("Last check instant: $lastCheck")
    endSection()

    logSection("Collect sources")
    val youtubeItems = collectItems(
        enabled = YOUTUBE_ENABLED,
        name = "YouTube",
        lastCheck = lastCheck,
        provider = YoutubeProvider
    )

    val androidBlogItems = collectItems(
        enabled = ANDROID_BLOG_ENABLED,
        name = "Android Developers Blog",
        lastCheck = lastCheck,
        provider = AndroidBlogProvider
    )

    val androidStudioBlogItems = collectItems(
        enabled = ANDROID_STUDIO_BLOG_ENABLED,
        name = "Android Studio Blog",
        lastCheck = lastCheck,
        provider = AndroidStudioBlogProvider
    )

    val androidAuthorityItems = collectItems(
        enabled = ANDROID_AUTHORITY_ENABLED,
        name = "Android Authority",
        lastCheck = lastCheck,
        provider = AndroidAuthorityProvider
    )

    val firebaseBlogItems = collectItems(
        enabled = FIREBASE_BLOG_ENABLED,
        name = "Firebase Blog",
        lastCheck = lastCheck,
        provider = FirebaseBlogProvider
    )

    val kotlinBlogItems = collectItems(
        enabled = KOTLIN_BLOG_ENABLED,
        name = "Kotlin Blog",
        lastCheck = lastCheck,
        provider = KotlinBlogProvider
    )

    val githubBlogItems = collectItems(
        enabled = GITHUB_BLOG_ENABLED,
        name = "GitHub Blog",
        lastCheck = lastCheck,
        provider = GithubBlogProvider
    )

    val jetBrainsBlogItems = collectItems(
        enabled = JETBRAINS_BLOG_ENABLED,
        name = "JetBrains Blog",
        lastCheck = lastCheck,
        provider = JetBrainsBlogProvider
    )

    val mediumGoogleItems = collectItems(
        enabled = MEDIUM_GOOGLE_ENABLED,
        name = "Medium Google",
        lastCheck = lastCheck,
        provider = MediumGoogleProvider
    )

    val mediumAndroidItems = collectItems(
        enabled = MEDIUM_ANDROID_ENABLED,
        name = "Medium Android",
        lastCheck = lastCheck,
        provider = MediumAndroidProvider
    )

    val devToAndroidItems = collectItems(
        enabled = DEV_TO_ANDROID_ENABLED,
        name = "Dev.to Android",
        lastCheck = lastCheck,
        provider = DevToProvider
    )

    val androidWeeklyItems = collectItems(
        enabled = ANDROID_WEEKLY_ENABLED,
        name = "Android Weekly",
        lastCheck = lastCheck,
        provider = AndroidWeeklyProvider
    )

    val nineToFiveGoogleItems = collectItems(
        enabled = NINE_TO_FIVE_GOOGLE_ENABLED,
        name = "9to5Google",
        lastCheck = lastCheck,
        provider = NineToFiveGoogleProvider
    )

    val proAndroidDevItems = collectItems(
        enabled = PRO_ANDROID_DEV_ENABLED,
        name = "ProAndroidDev",
        lastCheck = lastCheck,
        provider = ProAndroidDevProvider
    )

    val techRadarItems = collectItems(
        enabled = TECHRADAR_ANDROID_ENABLED,
        name = "TechRadar Android",
        lastCheck = lastCheck,
        provider = TechRadarProvider
    )

    val habrAndroidItems = collectItems(
        enabled = HABR_ANDROID_ENABLED,
        name = "HABR ANDROID",
        lastCheck = lastCheck,
        provider = HabrAndroidProvider
    )

    val habrAiItems = collectItems(
        enabled = HABR_AI_ENABLED,
        name = "HABR AI",
        lastCheck = lastCheck,
        provider = HabrAiProvider
    )

    val habrProgrammingItems = collectItems(
        enabled = HABR_PROGRAMMING_ENABLED,
        name = "HABR PROGRAMMING",
        lastCheck = lastCheck,
        provider = HabrProgrammingProvider
    )

    val habrCareerItems = collectItems(
        enabled = HABR_CAREER_ENABLED,
        name = "HABR CAREER",
        lastCheck = lastCheck,
        provider = HabrCareerProvider
    )

    val githubReleaseItems = collectItems(
        enabled = GITHUB_RELEASES_ENABLED,
        name = "GitHub releases",
        lastCheck = lastCheck,
        provider = GithubReleasesProvider
    )

    val githubTrendingKotlinItems = collectItems(
        enabled = GITHUB_TRENDING_KOTLIN_ENABLED,
        name = "GitHub trending Kotlin",
        lastCheck = lastCheck,
        provider = GithubTrendingKotlinProvider
    )

    endSection()

    logSection("Build messages")
    val messages = buildMessages(
        youtubeItems = youtubeItems,
        androidBlogItems = androidBlogItems,
        androidStudioBlogItems = androidStudioBlogItems,
        androidAuthorityItems = androidAuthorityItems,
        firebaseBlogItems = firebaseBlogItems,
        kotlinBlogItems = kotlinBlogItems,
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
        githubReleaseItems = githubReleaseItems,
        githubTrendingKotlinItems = githubTrendingKotlinItems,
        youtubeEnabled = YOUTUBE_ENABLED,
        androidBlogEnabled = ANDROID_BLOG_ENABLED,
        androidStudioBlogEnabled = ANDROID_STUDIO_BLOG_ENABLED,
        androidAuthorityEnabled = ANDROID_AUTHORITY_ENABLED,
        firebaseBlogEnabled = FIREBASE_BLOG_ENABLED,
        kotlinBlogEnabled = KOTLIN_BLOG_ENABLED,
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
        githubReleasesEnabled = GITHUB_RELEASES_ENABLED,
        githubTrendingKotlinEnabled = GITHUB_TRENDING_KOTLIN_ENABLED
    )
    logInfo("Built messages count: ${messages.size}")
    endSection()

    if (messages.isEmpty()) {
        logInfo("Новостей нет, ничего не отправляем в Telegram")
        return
    }

    logSection("Send to Telegram")
    sendTelegram(messages)
    endSection()
}

private fun buildMessages(
    youtubeItems: List<YoutubeItem>,
    androidBlogItems: List<AndroidBlogItem>,
    androidStudioBlogItems: List<AndroidStudioBlogItem>,
    androidAuthorityItems: List<AndroidAuthorityItem>,
    firebaseBlogItems: List<FirebaseBlogItem>,
    kotlinBlogItems: List<KotlinBlogItem>,
    githubBlogItems: List<GithubBlogItem>,
    jetBrainsBlogItems: List<JetBrainsBlogItem>,
    mediumGoogleItems: List<MediumGoogleItem>,
    mediumAndroidItems: List<MediumAndroidItem>,
    devToAndroidItems: List<DevToItem>,
    androidWeeklyItems: List<AndroidWeeklyItem>,
    nineToFiveGoogleItems: List<NineToFiveGoogleItem>,
    proAndroidDevItems: List<ProAndroidDevItem>,
    techRadarItems: List<TechRadarItem>,
    habrAndroidItems: List<HabrAndroidItem>,
    habrAiItems: List<HabrAiItem>,
    habrProgrammingItems: List<HabrProgrammingItem>,
    habrCareerItems: List<HabrCareerItem>,
    githubReleaseItems: List<GithubReleaseItem>,
    githubTrendingKotlinItems: List<GithubTrendingKotlinItem>,
    youtubeEnabled: Boolean,
    androidBlogEnabled: Boolean,
    androidStudioBlogEnabled: Boolean,
    androidAuthorityEnabled: Boolean,
    firebaseBlogEnabled: Boolean,
    kotlinBlogEnabled: Boolean,
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
    githubReleasesEnabled: Boolean,
    githubTrendingKotlinEnabled: Boolean
): List<String> {
    val zone = ZoneId.of("Europe/Moscow")
    val dateFormatter = DateTimeFormatter.ofPattern("d LLL 'в' HH:mm", Locale.of("ru"))

    val sections = listOf(
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5321283442577527159\">▶️</tg-emoji> <b>YOUTUBE</b>")
                append("\n\n")
            },
            enabled = youtubeEnabled,
            items = youtubeItems,
            formatLine = ::defaultLine
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
                append("<tg-emoji emoji-id=\"5208912452275701983\">▶️</tg-emoji> <b>MEDIUM GOOGLE DEVELOPER EXPERTS</b>")
                append("\n\n")
            },
            enabled = mediumGoogleEnabled,
            items = mediumGoogleItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<tg-emoji emoji-id=\"5368646963233312962\">▶️</tg-emoji> <b>MEDIUM ANDROID DEVELOPERS</b>")
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
            formatLine = ::defaultLine
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
                append("<tg-emoji emoji-id=\"5321190645514130346\">▶️</tg-emoji> <b>GITHUB TRENDING</b>")
                append("\n\n")
            },
            enabled = githubTrendingKotlinEnabled,
            items = githubTrendingKotlinItems,
            formatLine = ::formatGithubTrendingLine
        )
    )

    return sections.flatMap { section -> buildSectionMessages(section, zone, dateFormatter) }
}

private data class MessageSection<T: NewsItem>(
    val header: String,
    val enabled: Boolean,
    val items: List<T>,
    val formatLine: (T, ZoneId, DateTimeFormatter) -> String
)

private fun defaultLine(
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
        item.author?.let { author ->
            append("\nАвтор: ")
            append(escapeHtml(author))
        }
        if (item.categories.isNotEmpty()) {
            append("\nТеги: ")
            append(escapeHtml(item.categories.joinToString(", ")))
        }
        append("\n\n")
    }
}

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

private fun formatGithubTrendingLine(
    item: GithubTrendingKotlinItem,
    zone: ZoneId,
    dateFormatter: DateTimeFormatter
): String {
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
): List<String> {
    if (!section.enabled) return emptyList()
    if (section.items.isEmpty()) return emptyList()

    val builder = StringBuilder()
    val result = mutableListOf<String>()
    var isFirstChunk = true

    fun flushChunk() {
        val text = builder.toString().trim()
        if (text.isNotEmpty()) {
            result += text
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
    provider: NewsProvider<T>
): List<T> {
    if (!enabled) {
        logInfo("$name parsing disabled by feature flag")
        return emptyList()
    }

    val items = provider.fetchItems(lastCheck)
    logInfo("$name items collected (after filter): ${items.size}")
    return items
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

private fun sendTelegram(messages: List<String>) {
    val token = System.getenv("TELEGRAM_TOKEN").orEmpty()
    val chatId = System.getenv("CHAT_ID").orEmpty()
    val threadId = System.getenv("THREAD_ID").orEmpty()

    if (token.isBlank() || chatId.isBlank()) {
        logWarn("TELEGRAM_TOKEN or CHAT_ID not set, skip send")
        return
    }

    val url = "https://api.telegram.org/bot$token/sendMessage"
    val client = HttpClient.newHttpClient()

    messages.forEachIndexed { index, rawText ->
        logInfo("Preparing to send Telegram message #${index + 1}")
        logInfo("Text length = ${rawText.length}")

        val jsonText = rawText
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

        val payload = buildString {
            append("{")
            append("\"chat_id\":\"").append(chatId).append("\",")
            if (threadId.isNotBlank()) {
                append("\"message_thread_id\":\"").append(threadId).append("\",")
            }
            append("\"text\":\"").append(jsonText).append("\",")
            append("\"disable_web_page_preview\":true,")
            append("\"parse_mode\":\"HTML\"")
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
}
