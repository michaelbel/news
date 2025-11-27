package news

fun main() {
    val lastCheck = Timestamp.readLastCheck()

    val items = news.youtube.YoutubeProvider.fetchItems(lastCheck)
    val text = buildMessage(items)
    sendTelegram(text)
}

fun buildMessage(items: List<news.youtube.YoutubeItem>): String {
    if (items.isEmpty()) {
        return "*Новые YouTube-видео*\n\nНовых видео нет."
    }

    val zone = java.time.ZoneId.of("Europe/Berlin")
    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

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
    val client = java.net.http.HttpClient.newHttpClient()
    val request = java.net.http.HttpRequest.newBuilder()
        .uri(java.net.URI(url))
        .header("Content-Type", "application/json")
        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
        .build()

    val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
    System.err.println("Telegram response: ${response.statusCode()}")
}