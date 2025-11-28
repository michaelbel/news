package news

import news.Timestamp
import news.NewsFeatures
import news.androidblog.AndroidBlogItem
import news.androidblog.AndroidBlogProvider
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
    val lastCheck = Timestamp.readLastCheck()
    System.err.println("Last check instant: $lastCheck")

    val youtubeItems: List<YoutubeItem> =
        if (NewsFeatures.YOUTUBE_ENABLED) {
            val items = YoutubeProvider.fetchItems(lastCheck)
            System.err.println("YouTube items collected: ${items.size}")
            items
        } else {
            System.err.println("YouTube parsing disabled by feature flag")
            emptyList()
        }

    val blogItems: List<AndroidBlogItem> =
        if (NewsFeatures.ANDROID_BLOG_ENABLED) {
            val items = AndroidBlogProvider.fetchItems(lastCheck)
            System.err.println("Android Developers Blog items collected: ${items.size}")
            items
        } else {
            System.err.println("Android Developers Blog parsing disabled by feature flag")
            emptyList()
        }

    val messages = buildMessages(
        youtubeItems = youtubeItems,
        blogItems = blogItems,
        youtubeEnabled = NewsFeatures.YOUTUBE_ENABLED,
        blogEnabled = NewsFeatures.ANDROID_BLOG_ENABLED
    )

    if (messages.isEmpty()) {
        System.err.println("Новостей нет, ничего не отправляем в Telegram")
        return
    }

    sendTelegram(messages)
}

fun buildMessages(
    youtubeItems: List<YoutubeItem>,
    blogItems: List<AndroidBlogItem>,
    youtubeEnabled: Boolean,
    blogEnabled: Boolean
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
        if (youtubeItems.isEmpty()) return   // источник включён, но новостей нет – секцию не добавляем

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

    fun appendBlogChunks() {
        if (!blogEnabled) return
        if (blogItems.isEmpty()) return   // нет новых статей – секцию не добавляем

        val header = "<b>Новые статьи Android Developers Blog</b>\n\n"
        var sb = StringBuilder(header)

        for (item in blogItems) {
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

    appendYoutubeChunks()
    appendBlogChunks()

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
        System.err.println("TELEGRAM_TOKEN or CHAT_ID not set, skip send")
        return
    }

    val url = "https://api.telegram.org/bot$token/sendMessage"
    val client = HttpClient.newHttpClient()

    messages.forEachIndexed { index, rawText ->
        System.err.println("Preparing to send Telegram message #${index + 1}")
        System.err.println("Text length = ${rawText.length}")

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

        System.err.println("Payload for Telegram #${index + 1}: $payload")

        val request = HttpRequest.newBuilder()
            .uri(URI(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        System.err.println("Telegram response #${index + 1}: ${response.statusCode()}")
        System.err.println("Telegram body #${index + 1}: ${response.body()}")

        if (response.statusCode() != 200) {
            error("Telegram send failed for message #${index + 1} with status ${response.statusCode()}")
        }
    }
}