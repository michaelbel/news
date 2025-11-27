package news

import news.Timestamp
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

fun main() {
    val lastCheck = Timestamp.readLastCheck()

    val youtubeItems = YoutubeProvider.fetchItems(lastCheck)
    val blogItems = AndroidBlogProvider.fetchItems(lastCheck)

    val text = buildMessage(
        youtubeItems = youtubeItems,
        blogItems = blogItems
    )

    sendTelegram(text)
}

fun buildMessage(
    youtubeItems: List<YoutubeItem>,
    blogItems: List<AndroidBlogItem>
): String {
    val zone = ZoneId.of("Europe/Berlin")
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    val sb = StringBuilder()

    // блок YouTube
    sb.append("<b>Новые YouTube-видео</b>\n\n")
    if (youtubeItems.isEmpty()) {
        sb.append("Новых видео нет.\n\n")
    } else {
        for (item in youtubeItems) {
            val local = item.published.atZone(zone)
            val dateStr = local.format(dateFormatter)
            sb.append(dateStr)
                .append(" – ")
                .append("<a href=\"")
                .append(escapeHtml(item.url))
                .append("\">")
                .append(escapeHtml(item.title))
                .append("</a>")
                .append("\n\n")
        }
    }

    // блок Android Developers Blog
    sb.append("\n<b>Новые статьи Android Developers Blog</b>\n\n")
    if (blogItems.isEmpty()) {
        sb.append("Новых статей нет.\n")
    } else {
        for (item in blogItems) {
            val local = item.published.atZone(zone)
            val dateStr = local.format(dateFormatter)
            sb.append(dateStr)
                .append(" – ")
                .append("<a href=\"")
                .append(escapeHtml(item.url))
                .append("\">")
                .append(escapeHtml(item.title))
                .append("</a>")
                .append("\n\n")
        }
    }

    return sb.toString().trim()
}

fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

fun sendTelegram(text: String) {
    val token = System.getenv("TELEGRAM_TOKEN").orEmpty()
    val chatId = System.getenv("CHAT_ID").orEmpty()
    val threadId = System.getenv("THREAD_ID").orEmpty()

    if (token.isBlank() || chatId.isBlank()) {
        System.err.println("TELEGRAM_TOKEN or CHAT_ID not set, skip send")
        return
    }

    System.err.println("Preparing to send Telegram message")
    System.err.println("Text length = ${text.length}")

    val jsonText = text
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

    System.err.println("Payload for Telegram: $payload")

    val url = "https://api.telegram.org/bot$token/sendMessage"
    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder()
        .uri(URI(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    System.err.println("Telegram response: ${response.statusCode()}")
    System.err.println("Telegram body: ${response.body()}")

    if (response.statusCode() != 200) {
        error("Telegram send failed with status ${response.statusCode()}")
    }
}
