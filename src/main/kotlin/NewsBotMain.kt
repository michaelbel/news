package news

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

data class YoutubeItem(
    val published: Instant,
    val title: String,
    val url: String
)

fun main() {
    val lastCheckStr = System.getenv("LAST_CHECK")?.trim().orEmpty()
    val lastCheck = if (lastCheckStr.isBlank()) {
        val fallback = Instant.now().minusSeconds(24 * 60 * 60)
        System.err.println("LAST_CHECK is empty, fallback to $fallback")
        fallback
    } else {
        parseIso(lastCheckStr) ?: run {
            System.err.println("Cannot parse LAST_CHECK=$lastCheckStr")
            return
        }
    }

    val items = fetchYoutubeItems(lastCheck)
    val text = buildMessage(items)
    sendTelegram(text)
}

fun parseIso(value: String): Instant? {
    if (value.isBlank()) return null
    return try {
        Instant.parse(value)
    } catch (_: Exception) {
        try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: Exception) {
            null
        }
    }
}

fun fetchYoutubeItems(lastCheck: Instant): List<YoutubeItem> {
    val feeds = readFeeds()
    if (feeds.isEmpty()) return emptyList()

    val client = HttpClient.newHttpClient()
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true

    val result = mutableListOf<YoutubeItem>()

    for (feed in feeds) {
        System.err.println("Fetching $feed")
        val request = HttpRequest.newBuilder()
            .uri(URI(feed))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            resp.body()
        } catch (e: Exception) {
            System.err.println("Failed to fetch $feed: ${e.message}")
            continue
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder.parse(xml.byteInputStream()).apply { documentElement.normalize() }
        } catch (e: Exception) {
            System.err.println("Failed to parse XML for $feed: ${e.message}")
            continue
        }

        val entries = doc.getElementsByTagName("entry")
        for (i in 0 until entries.length) {
            val entry = entries.item(i)
            val children = entry.childNodes

            var publishedStr: String? = null
            var title: String? = null
            var videoId: String? = null
            var linkHref: String? = null

            for (j in 0 until children.length) {
                val node = children.item(j)
                val name = node.nodeName
                when (name) {
                    "published" -> publishedStr = node.textContent
                    "title" -> title = node.textContent
                    "yt:videoId" -> videoId = node.textContent
                    "link" -> {
                        val rel = node.attributes?.getNamedItem("rel")?.nodeValue
                        if (rel == "alternate") {
                            linkHref = node.attributes?.getNamedItem("href")?.nodeValue
                        }
                    }
                }
            }

            if (publishedStr == null) continue
            val published = parseIso(publishedStr) ?: continue
            if (published <= lastCheck) continue

            val safeTitle = title?.trim().takeUnless { it.isNullOrEmpty() } ?: "(no title)"
            val url = when {
                !videoId.isNullOrBlank() -> "https://www.youtube.com/watch?v=$videoId"
                !linkHref.isNullOrBlank() -> linkHref!!
                else -> ""
            }

            result += YoutubeItem(published, safeTitle, url)
        }
    }

    return result.sortedBy { it.published }
}

fun readFeeds(): List<String> {
    val file = File("youtube.txt")
    if (!file.exists()) {
        System.err.println("youtube.txt not found, skip")
        return emptyList()
    }

    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line -> line.substringBefore("#").trim() }
        .filter { it.isNotEmpty() }
}

fun buildMessage(items: List<YoutubeItem>): String {
    if (items.isEmpty()) {
        return "*Новые YouTube-видео*\n\nНовых видео нет."
    }

    val zone = ZoneId.of("Europe/Berlin")
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    val lines = mutableListOf<String>()
    lines += "*Новые YouTube-видео*"
    lines += ""

    for (item in items) {
        val local = item.published.atZone(zone)
        val dateStr = local.format(dateFormatter)
        val line = "$dateStr – [${escapeMarkdown(item.title)}](${item.url})"
        lines += line
        lines += ""
    }

    return lines.joinToString("\n").trimEnd()
}

fun escapeMarkdown(text: String): String {
    val specials = listOf("\\", "_", "*", "[", "]", "(", ")", "~", "`", ">", "#", "+", "-", "=", "|", "{", "}", ".", "!")
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

    val sb = StringBuilder()
    sb.append("{")
    sb.append("\"chat_id\":\"").append(chatId).append("\",")
    if (threadId.isNotBlank()) {
        sb.append("\"message_thread_id\":\"").append(threadId).append("\",")
    }
    sb.append("\"text\":\"").append(text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append("\",")
    sb.append("\"disable_web_page_preview\":true,")
    sb.append("\"parse_mode\":\"Markdown\"")
    sb.append("}")

    val payload = sb.toString()

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