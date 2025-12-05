package news

import news.androidblog.AndroidBlogItem
import news.androidblog.AndroidBlogProvider
import news.androidstudioblog.AndroidStudioBlogItem
import news.androidstudioblog.AndroidStudioBlogProvider
import news.androidweekly.AndroidWeeklyItem
import news.androidweekly.AndroidWeeklyProvider
import news.androidauthority.AndroidAuthorityItem
import news.androidauthority.AndroidAuthorityProvider
import news.devto.DevToItem
import news.devto.DevToProvider
import news.firebaseblog.FirebaseBlogItem
import news.firebaseblog.FirebaseBlogProvider
import news.github.GithubReleaseItem
import news.github.GithubReleasesProvider
import news.githubtrending.GithubTrendingKotlinItem
import news.githubtrending.GithubTrendingKotlinProvider
import news.habr.HabrAndroidItem
import news.habr.HabrAndroidProvider
import news.jetbrainsblog.JetBrainsBlogItem
import news.jetbrainsblog.JetBrainsBlogProvider
import news.kotlinblog.KotlinBlogItem
import news.kotlinblog.KotlinBlogProvider
import news.mediumandroid.MediumAndroidItem
import news.mediumandroid.MediumAndroidProvider
import news.mediumgoogle.MediumGoogleItem
import news.mediumgoogle.MediumGoogleProvider
import news.proandroiddev.ProAndroidDevItem
import news.proandroiddev.ProAndroidDevProvider
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

    val proAndroidDevItems = collectItems(
        enabled = PRO_ANDROID_DEV_ENABLED,
        name = "ProAndroidDev",
        lastCheck = lastCheck,
        provider = ProAndroidDevProvider
    )

    val habrAndroidItems = collectItems(
        enabled = HABR_ANDROID_ENABLED,
        name = "Habr Android",
        lastCheck = lastCheck,
        provider = HabrAndroidProvider
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
        jetBrainsBlogItems = jetBrainsBlogItems,
        mediumGoogleItems = mediumGoogleItems,
        mediumAndroidItems = mediumAndroidItems,
        devToAndroidItems = devToAndroidItems,
        androidWeeklyItems = androidWeeklyItems,
        proAndroidDevItems = proAndroidDevItems,
        habrAndroidItems = habrAndroidItems,
        githubReleaseItems = githubReleaseItems,
        githubTrendingKotlinItems = githubTrendingKotlinItems,
        youtubeEnabled = YOUTUBE_ENABLED,
        androidBlogEnabled = ANDROID_BLOG_ENABLED,
        androidStudioBlogEnabled = ANDROID_STUDIO_BLOG_ENABLED,
        androidAuthorityEnabled = ANDROID_AUTHORITY_ENABLED,
        firebaseBlogEnabled = FIREBASE_BLOG_ENABLED,
        kotlinBlogEnabled = KOTLIN_BLOG_ENABLED,
        jetBrainsBlogEnabled = JETBRAINS_BLOG_ENABLED,
        mediumGoogleEnabled = MEDIUM_GOOGLE_ENABLED,
        mediumAndroidEnabled = MEDIUM_ANDROID_ENABLED,
        devToAndroidEnabled = DEV_TO_ANDROID_ENABLED,
        androidWeeklyEnabled = ANDROID_WEEKLY_ENABLED,
        proAndroidDevEnabled = PRO_ANDROID_DEV_ENABLED,
        habrAndroidEnabled = HABR_ANDROID_ENABLED,
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
    jetBrainsBlogItems: List<JetBrainsBlogItem>,
    mediumGoogleItems: List<MediumGoogleItem>,
    mediumAndroidItems: List<MediumAndroidItem>,
    devToAndroidItems: List<DevToItem>,
    androidWeeklyItems: List<AndroidWeeklyItem>,
    proAndroidDevItems: List<ProAndroidDevItem>,
    habrAndroidItems: List<HabrAndroidItem>,
    githubReleaseItems: List<GithubReleaseItem>,
    githubTrendingKotlinItems: List<GithubTrendingKotlinItem>,
    youtubeEnabled: Boolean,
    androidBlogEnabled: Boolean,
    androidStudioBlogEnabled: Boolean,
    androidAuthorityEnabled: Boolean,
    firebaseBlogEnabled: Boolean,
    kotlinBlogEnabled: Boolean,
    jetBrainsBlogEnabled: Boolean,
    mediumGoogleEnabled: Boolean,
    mediumAndroidEnabled: Boolean,
    devToAndroidEnabled: Boolean,
    androidWeeklyEnabled: Boolean,
    proAndroidDevEnabled: Boolean,
    habrAndroidEnabled: Boolean,
    githubReleasesEnabled: Boolean,
    githubTrendingKotlinEnabled: Boolean
): List<String> {
    val zone = ZoneId.of("Europe/Moscow")
    val dateFormatter = DateTimeFormatter.ofPattern("d LLL 'в' HH:mm", Locale.of("ru"))

    val sections = listOf(
        MessageSection(
            header = buildString {
                append("<b>НОВЫЕ YOUTUBE-ВИДЕО</b>")
                append("\n\n")
                append("Свежие ролики с ${YOUTUBE_CHANNELS.size} отслеживаемых каналов про Android.")
                append("\n\n")
            },
            enabled = youtubeEnabled,
            items = youtubeItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<b>НОВЫЕ ПОСТЫ ANDROID DEVELOPERS BLOG</b>")
                append("\n\n")
                append("Последние материалы с официального блога Android команды Google.")
                append("\n\n")
            },
            enabled = androidBlogEnabled,
            items = androidBlogItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<b>НОВЫЕ ПОСТЫ ANDROID STUDIO BLOG</b>")
                append("\n\n")
                append("Новости и советы по Android Studio и инструментам разработки.")
                append("\n\n")
            },
            enabled = androidStudioBlogEnabled,
            items = androidStudioBlogItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<b>НОВЫЕ ПОСТЫ ANDROID AUTHORITY</b>")
                append("\n\n")
                append("Свежие материалы Android Authority о платформе и индустрии.")
                append("\n\n")
            },
            enabled = androidAuthorityEnabled,
            items = androidAuthorityItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<b>НОВЫЕ ПОСТЫ FIREBASE BLOG</b>")
                append("\n\n")
                append("Обновления и гайды по сервисам Firebase для мобильной разработки.")
                append("\n\n")
            },
            enabled = firebaseBlogEnabled,
            items = firebaseBlogItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<b>НОВЫЕ ПОСТЫ KOTLIN BLOG</b>")
                append("\n\n")
                append("Свежие записи о языке Kotlin и экосистеме от JetBrains/Google.")
                append("\n\n")
            },
            enabled = kotlinBlogEnabled,
            items = kotlinBlogItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<b>НОВЫЕ ПОСТЫ JETBRAINS BLOG</b>")
                append("\n\n")
                append("Анонсы продуктов, статьи и новости из основного блога JetBrains.")
                append("\n\n")
            },
            enabled = jetBrainsBlogEnabled,
            items = jetBrainsBlogItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<b>НОВЫЕ ПОСТЫ GOOGLE DEVELOPER EXPERTS</b>")
                append("\n\n")
                append("Подборка публикаций от экспертов GDE по Android/Kotlin.")
                append("\n\n")
            },
            enabled = mediumGoogleEnabled,
            items = mediumGoogleItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<b>НОВЫЕ ПОСТЫ ANDROID DEVELOPERS</b>")
                append("\n\n")
                append("Материалы команды Android в Medium о практиках и новинках платформы.")
                append("\n\n")
            },
            enabled = mediumAndroidEnabled,
            items = mediumAndroidItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<b>НОВЫЕ ПОСТЫ DEV.TO</b>")
                append("\n\n")
                append("Публикации с тегом Android от сообщества разработчиков.")
                append("\n\n")
            },
            enabled = devToAndroidEnabled,
            items = devToAndroidItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<b>НОВЫЕ ВЫПУСКИ ANDROID WEEKLY</b>")
                append("\n\n")
                append("Последний номер рассылки Android Weekly с подборкой статей и инструментов.")
                append("\n\n")
            },
            enabled = androidWeeklyEnabled,
            items = androidWeeklyItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<b>НОВЫЕ ПОСТЫ PROANDROIDDEV</b>")
                append("\n\n")
                append("Заметки и разборы от авторов ProAndroidDev про Android-разработку.")
                append("\n\n")
            },
            enabled = proAndroidDevEnabled,
            items = proAndroidDevItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<b>НОВЫЕ ПОСТЫ C ХАБРА</b>")
                append("\n\n")
                append("Новые публикации с Хабра по Android/Kotlin из отслеживаемых блогов.")
                append("\n\n")
            },
            enabled = habrAndroidEnabled,
            items = habrAndroidItems,
            formatLine = ::defaultLine
        ),
        MessageSection(
            header = buildString {
                append("<b>НОВЫЕ РЕЛИЗЫ НА GITHUB</b>")
                append("\n\n")
                append("Свежие релизные версии ${GITHUB_REPOS.size} отслеживаемых open-source проектов Android/Kotlin.")
                append("\n\n")
            },
            enabled = githubReleasesEnabled,
            items = githubReleaseItems,
            formatLine = ::formatGithubLine
        ),
        MessageSection(
            header = buildString {
                append("<b>GITHUB TRENDING</b>")
                append("\n\n")
                append("Cписок топовых Kotlin-репозиториев из GitHub Trending за ${Instant.now().atZone(zone).format(DateTimeFormatter.ofPattern("d MMMM", Locale.of("ru")))}.")
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
        item.summary?.let { summary ->
            append("\n")
            append(escapeHtml(summary))
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
        append("⭐️ ")
        append(item.stars)
        append(" • ")
        append("👤 ")
        append(item.forks)
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