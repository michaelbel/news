package news

import news.androidblog.AndroidBlogItem
import news.androidblog.AndroidBlogProvider
import news.androidweekly.AndroidWeeklyItem
import news.androidweekly.AndroidWeeklyProvider
import news.kotlinblog.KotlinBlogItem
import news.kotlinblog.KotlinBlogProvider
import news.youtube.YoutubeItem
import news.youtube.YoutubeProvider
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TELEGRAM_MAX_LEN = 3500 // запас до 4096

fun main() {
    logSection("Timestamp & last check")
    val lastCheck = Timestamp.readLastCheck()
    logInfo("Last check instant: $lastCheck")
    endSection()

    logSection("Collect sources")

    val youtubeItems: List<YoutubeItem> =
        if (NewsFeatures.YOUTUBE_ENABLED) {
            val items = YoutubeProvider.fetchItems(lastCheck)
            logInfo("YouTube items collected (after filter): ${items.size}")
            items
        } else {
            logInfo("YouTube parsing disabled by feature flag")
            emptyList()
        }

    val androidBlogItems: List<AndroidBlogItem> =
        if (NewsFeatures.ANDROID_BLOG_ENABLED) {
            val items = AndroidBlogProvider.fetchItems(lastCheck)
            logInfo("Android Developers Blog items collected (after filter): ${items.size}")
            items
        } else {
            logInfo("Android Developers Blog parsing disabled by feature flag")
            emptyList()
        }

    val kotlinBlogItems: List<KotlinBlogItem> =
        if (NewsFeatures.KOTLIN_BLOG_ENABLED) {
            val items = KotlinBlogProvider.fetchItems(lastCheck)
            logInfo("Kotlin Blog items collected (after filter): ${items.size}")
            items
        } else {
            logInfo("Kotlin Blog parsing disabled by feature flag")
            emptyList()
        }

    val androidWeeklyItems: List<AndroidWeeklyItem> =
        if (NewsFeatures.ANDROID_WEEKLY_ENABLED) {
            val items = AndroidWeeklyProvider.fetchItems(lastCheck)
            logInfo("Android Weekly items collected (after filter): ${items.size}")
            items
        } else {
            logInfo("Android Weekly parsing disabled by feature flag")
            emptyList()
        }

    endSection()

    logSection("Build messages")
    val messages = buildMessages(
        youtubeItems = youtubeItems,
        androidBlogItems = androidBlogItems,
        kotlinBlogItems = kotlinBlogItems,
        androidWeeklyItems = androidWeeklyItems,
        youtubeEnabled = NewsFeatures.YOUTUBE_ENABLED,
        androidBlogEnabled = NewsFeatures.ANDROID_BLOG_ENABLED,
        kotlinBlogEnabled = NewsFeatures.KOTLIN_BLOG_ENABLED,
        androidWeeklyEnabled = NewsFeatures.ANDROID_WEEKLY_ENABLED
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
    androidWeeklyItems: List<AndroidWeeklyItem>,
    youtubeEnabled: Boolean,
    androidBlogEnabled: Boolean,
    kotlinBlogEnabled: Boolean,
    androidWeeklyEnabled: Boolean
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
                sb = StringBuilder() // следующие чанки без заголовка
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

    appendYoutubeChunks()
    appendAndroidBlogChunks()
    appendKotlinBlogChunks()
    appendAndroidWeeklyChunks()

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