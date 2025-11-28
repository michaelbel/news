package news

import news.androidblog.AndroidBlogItem
import news.androidblog.AndroidBlogProvider
import news.androidweekly.AndroidWeeklyItem
import news.androidweekly.AndroidWeeklyProvider
import news.github.GithubReleaseItem
import news.github.GithubReleasesProvider
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TELEGRAM_MAX_LEN = 3500

fun main() {
    logSection("Timestamp & last check")
    val lastCheck = Timestamp.readLastCheck()
    logInfo("Last check instant: $lastCheck")
    endSection()

    logSection("Collect sources")

    val youtubeItems: List<YoutubeItem> =
        if (YOUTUBE_ENABLED) {
            val items = YoutubeProvider.fetchItems(lastCheck)
            logInfo("YouTube items collected (after filter): ${items.size}")
            items
        } else {
            logInfo("YouTube parsing disabled by feature flag")
            emptyList()
        }

    val androidBlogItems: List<AndroidBlogItem> =
        if (ANDROID_BLOG_ENABLED) {
            val items = AndroidBlogProvider.fetchItems(lastCheck)
            logInfo("Android Developers Blog items collected (after filter): ${items.size}")
            items
        } else {
            logInfo("Android Developers Blog parsing disabled by feature flag")
            emptyList()
        }

    val kotlinBlogItems: List<KotlinBlogItem> =
        if (KOTLIN_BLOG_ENABLED) {
            val items = KotlinBlogProvider.fetchItems(lastCheck)
            logInfo("Kotlin Blog items collected (after filter): ${items.size}")
            items
        } else {
            logInfo("Kotlin Blog parsing disabled by feature flag")
            emptyList()
        }

    val mediumGoogleItems: List<MediumGoogleItem> =
        if (MEDIUM_GOOGLE_ENABLED) {
            val items = MediumGoogleProvider.fetchItems(lastCheck)
            logInfo("Medium Google items collected (after filter): ${items.size}")
            items
        } else {
            logInfo("Medium Google parsing disabled by feature flag")
            emptyList()
        }

    val mediumAndroidItems: List<MediumAndroidItem> =
        if (MEDIUM_ANDROID_ENABLED) {
            val items = MediumAndroidProvider.fetchItems(lastCheck)
            logInfo("Medium Android items collected (after filter): ${items.size}")
            items
        } else {
            logInfo("Medium Android parsing disabled by feature flag")
            emptyList()
        }

    val androidWeeklyItems: List<AndroidWeeklyItem> =
        if (ANDROID_WEEKLY_ENABLED) {
            val items = AndroidWeeklyProvider.fetchItems(lastCheck)
            logInfo("Android Weekly items collected (after filter): ${items.size}")
            items
        } else {
            logInfo("Android Weekly parsing disabled by feature flag")
            emptyList()
        }

    val proAndroidDevItems: List<ProAndroidDevItem> =
        if (PRO_ANDROID_DEV_ENABLED) {
            val items = ProAndroidDevProvider.fetchItems(lastCheck)
            logInfo("ProAndroidDev items collected (after filter): ${items.size}")
            items
        } else {
            logInfo("ProAndroidDev parsing disabled by feature flag")
            emptyList()
        }

    val githubReleaseItems: List<GithubReleaseItem> =
        if (GITHUB_RELEASES_ENABLED) {
            val items = GithubReleasesProvider.fetchItems(lastCheck)
            logInfo("GitHub releases collected (after filter): ${items.size}")
            items
        } else {
            logInfo("GitHub releases parsing disabled by feature flag")
            emptyList()
        }

    endSection()

    logSection("Build messages")
    val messages = buildMessages(
        youtubeItems = youtubeItems,
        androidBlogItems = androidBlogItems,
        kotlinBlogItems = kotlinBlogItems,
        mediumGoogleItems = mediumGoogleItems,
        mediumAndroidItems = mediumAndroidItems,
        androidWeeklyItems = androidWeeklyItems,
        proAndroidDevItems = proAndroidDevItems,
        githubReleaseItems = githubReleaseItems,
        youtubeEnabled = YOUTUBE_ENABLED,
        androidBlogEnabled = ANDROID_BLOG_ENABLED,
        kotlinBlogEnabled = KOTLIN_BLOG_ENABLED,
        mediumGoogleEnabled = MEDIUM_GOOGLE_ENABLED,
        mediumAndroidEnabled = MEDIUM_ANDROID_ENABLED,
        androidWeeklyEnabled = ANDROID_WEEKLY_ENABLED,
        proAndroidDevEnabled = PRO_ANDROID_DEV_ENABLED,
        githubReleasesEnabled = GITHUB_RELEASES_ENABLED
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

fun buildMessages(
    youtubeItems: List<YoutubeItem>,
    androidBlogItems: List<AndroidBlogItem>,
    kotlinBlogItems: List<KotlinBlogItem>,
    mediumGoogleItems: List<MediumGoogleItem>,
    mediumAndroidItems: List<MediumAndroidItem>,
    androidWeeklyItems: List<AndroidWeeklyItem>,
    proAndroidDevItems: List<ProAndroidDevItem>,
    githubReleaseItems: List<GithubReleaseItem>,
    youtubeEnabled: Boolean,
    androidBlogEnabled: Boolean,
    kotlinBlogEnabled: Boolean,
    mediumGoogleEnabled: Boolean,
    mediumAndroidEnabled: Boolean,
    androidWeeklyEnabled: Boolean,
    proAndroidDevEnabled: Boolean,
    githubReleasesEnabled: Boolean
): List<String> {
    val zone = ZoneId.of("Europe/Berlin")
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    val result = mutableListOf<String>()

    fun flushChunk(builder: StringBuilder) {
        val text = builder.toString().trim()
        if (text.isNotEmpty()) {
            result += text
        }
    }

    fun appendYoutubeChunks() {
        if (!youtubeEnabled) return
        if (youtubeItems.isEmpty()) return

        val header = "<b>Новые YouTube-видео</b>\n\n"
        var sb = StringBuilder(header)

        for (item in youtubeItems) {
            val local = item.published.atZone(zone)
            val dateStr = local.format(dateFormatter)
            val line = buildString {
                append(dateStr)
                append(" – ")
                append("<a href=\"")
                append(escapeHtml(item.url))
                append("\">")
                append(escapeHtml(item.title))
                append("</a>\n\n")
            }

            if (sb.length + line.length > TELEGRAM_MAX_LEN) {
                flushChunk(sb)
                sb = StringBuilder()
            }

            sb.append(line)
        }

        flushChunk(sb)
    }

    fun appendAndroidBlogChunks() {
        if (!androidBlogEnabled) return
        if (androidBlogItems.isEmpty()) return

        val header = "<b>Новые статьи Android Developers Blog</b>\n\n"
        var sb = StringBuilder(header)

        for (item in androidBlogItems) {
            val local = item.published.atZone(zone)
            val dateStr = local.format(dateFormatter)
            val line = buildString {
                append(dateStr)
                append(" – ")
                append("<a href=\"")
                append(escapeHtml(item.url))
                append("\">")
                append(escapeHtml(item.title))
                append("</a>\n\n")
            }

            if (sb.length + line.length > TELEGRAM_MAX_LEN) {
                flushChunk(sb)
                sb = StringBuilder()
            }

            sb.append(line)
        }

        flushChunk(sb)
    }

    fun appendKotlinBlogChunks() {
        if (!kotlinBlogEnabled) return
        if (kotlinBlogItems.isEmpty()) return

        val header = "<b>Новые статьи Kotlin Blog</b>\n\n"
        var sb = StringBuilder(header)

        for (item in kotlinBlogItems) {
            val local = item.published.atZone(zone)
            val dateStr = local.format(dateFormatter)
            val line = buildString {
                append(dateStr)
                append(" – ")
                append("<a href=\"")
                append(escapeHtml(item.url))
                append("\">")
                append(escapeHtml(item.title))
                append("</a>\n\n")
            }

            if (sb.length + line.length > TELEGRAM_MAX_LEN) {
                flushChunk(sb)
                sb = StringBuilder()
            }

            sb.append(line)
        }

        flushChunk(sb)
    }

    fun appendMediumGoogleChunks() {
        if (!mediumGoogleEnabled) return
        if (mediumGoogleItems.isEmpty()) return

        val header = "<b>Новые статьи Google Developer Experts (Medium)</b>\n\n"
        var sb = StringBuilder(header)

        for (item in mediumGoogleItems) {
            val local = item.published.atZone(zone)
            val dateStr = local.format(dateFormatter)
            val line = buildString {
                append(dateStr)
                append(" – ")
                append("<a href=\"")
                append(escapeHtml(item.url))
                append("\">")
                append(escapeHtml(item.title))
                append("</a>\n\n")
            }

            if (sb.length + line.length > TELEGRAM_MAX_LEN) {
                flushChunk(sb)
                sb = StringBuilder()
            }

            sb.append(line)
        }

        flushChunk(sb)
    }

    fun appendMediumAndroidChunks() {
        if (!mediumAndroidEnabled) return
        if (mediumAndroidItems.isEmpty()) return

        val header = "<b>Новые статьи Android Developers (Medium)</b>\n\n"
        var sb = StringBuilder(header)

        for (item in mediumAndroidItems) {
            val local = item.published.atZone(zone)
            val dateStr = local.format(dateFormatter)
            val line = buildString {
                append(dateStr)
                append(" – ")
                append("<a href=\"")
                append(escapeHtml(item.url))
                append("\">")
                append(escapeHtml(item.title))
                append("</a>\n\n")
            }

            if (sb.length + line.length > TELEGRAM_MAX_LEN) {
                flushChunk(sb)
                sb = StringBuilder()
            }

            sb.append(line)
        }

        flushChunk(sb)
    }

    fun appendAndroidWeeklyChunks() {
        if (!androidWeeklyEnabled) return
        if (androidWeeklyItems.isEmpty()) return

        val header = "<b>Новые выпуски Android Weekly</b>\n\n"
        var sb = StringBuilder(header)

        for (item in androidWeeklyItems) {
            val local = item.published.atZone(zone)
            val dateStr = local.format(dateFormatter)
            val line = buildString {
                append(dateStr)
                append(" – ")
                append("<a href=\"")
                append(escapeHtml(item.url))
                append("\">")
                append(escapeHtml(item.title))
                append("</a>\n\n")
            }

            if (sb.length + line.length > TELEGRAM_MAX_LEN) {
                flushChunk(sb)
                sb = StringBuilder()
            }

            sb.append(line)
        }

        flushChunk(sb)
    }

    fun appendProAndroidDevChunks() {
        if (!proAndroidDevEnabled) return
        if (proAndroidDevItems.isEmpty()) return

        val header = "<b>Новые статьи ProAndroidDev</b>\n\n"
        var sb = StringBuilder(header)

        for (item in proAndroidDevItems) {
            val local = item.published.atZone(zone)
            val dateStr = local.format(dateFormatter)
            val line = buildString {
                append(dateStr)
                append(" – ")
                append("<a href=\"")
                append(escapeHtml(item.url))
                append("\">")
                append(escapeHtml(item.title))
                append("</a>\n\n")
            }

            if (sb.length + line.length > TELEGRAM_MAX_LEN) {
                flushChunk(sb)
                sb = StringBuilder()
            }

            sb.append(line)
        }

        flushChunk(sb)
    }

    fun appendGithubReleasesChunks() {
        if (!githubReleasesEnabled) return
        if (githubReleaseItems.isEmpty()) return

        val header = "<b>Новые релизы на GitHub</b>\n\n"
        var sb = StringBuilder(header)

        for (item in githubReleaseItems) {
            val local = item.published.atZone(zone)
            val dateStr = local.format(dateFormatter)
            val line = buildString {
                append(dateStr)
                append(" – ")
                append("<a href=\"")
                append(escapeHtml(item.url))
                append("\">")
                append(escapeHtml("${item.repo}: ${item.title}"))
                append("</a>\n\n")
            }

            if (sb.length + line.length > TELEGRAM_MAX_LEN) {
                flushChunk(sb)
                sb = StringBuilder()
            }

            sb.append(line)
        }

        flushChunk(sb)
    }

    appendYoutubeChunks()
    appendAndroidBlogChunks()
    appendKotlinBlogChunks()
    appendMediumGoogleChunks()
    appendMediumAndroidChunks()
    appendAndroidWeeklyChunks()
    appendProAndroidDevChunks()
    appendGithubReleasesChunks()

    return result
}

fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

fun sendTelegram(messages: List<String>) {
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

        val request = HttpRequest.newBuilder()
            .uri(URI(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        logInfo("Telegram response #${index + 1}: ${response.statusCode()}")
        logInfo("Telegram body #${index + 1}: ${response.body()}")

        if (response.statusCode() != 200) {
            logError("Telegram send failed for message #${index + 1} with status ${response.statusCode()}")
            error("Telegram send failed for message #${index + 1} with status ${response.statusCode()}")
        }
    }
}