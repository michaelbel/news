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

    val lines = mutableListOf<String>()

    // блок YouTube
    lines += "*Новые YouTube-видео*"
    lines += ""
    if (youtubeItems.isEmpty()) {
        lines += "Новых видео нет."
    } else {
        for (item in youtubeItems) {
            val local = item.published.atZone(zone)
            val dateStr = local.format(dateFormatter)
            val line = "$dateStr – [${escapeMarkdown(item.title)}](${item.url})"
            lines += line
            lines += ""
        }
    }

    // пустая строка между блоками
    lines += ""

    // блок Android Developers Blog
    lines += "*Новые статьи Android Developers Blog*"
    lines += ""
    if (blogItems.isEmpty()) {
        lines += "Новых статей нет."
    } else {
        for (item in blogItems) {
            val local = item.published.atZone(zone)
            val dateStr = local.format(dateFormatter)
            val line = "$dateStr – [${escapeMarkdown(item.title)}](${item.url})"
            lines += line
            lines += ""
        }
    }

    return lines.joinToString("\n").trimEnd()
}

fun escapeMarkdown(text: String): String {
    val specials = listOf(
        "\\", "_", "*", "[", "]", "(", ")", "~",
        "`", ">", "#", "+", "-", "=", "|", "{",
        "}", ".", "!"
    )
    var result = text
    for (ch in specials) {
        result = result.replace(ch, "\\$ch")
    }
    return result
}

fun sendTelegram(text: String) {
    val token = System.getenv("TELEGRAM_TOKEN").orEmpty()
    val chatId = System.getenv("CHAT_ID").orEmpty()
    val threadId = System.getenv("THREAD_ID").orEmpty()

    if (token.isBlank() || chatId.isBlank()) {
        System.err.println("TELEGRAM_TOKEN or CHAT_ID not set, skip send")
        return
    }

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
        append("\"parse_mode\":\"Markdown\"")
        append("}")
    }

    val url = "https://api.telegram.org/bot$token/sendMessage"
    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder()
        .uri(URI(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    System.err.println("Telegram response: ${response.statusCode()}")
}