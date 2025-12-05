package news.ninetofivegoogle

import news.NINE_TO_FIVE_GOOGLE_URL
import news.NewsProvider
import news.cleanAndTruncate
import news.cleanText
import news.logInfo
import news.logWarn
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

object NineToFiveGoogleProvider : NewsProvider<NineToFiveGoogleItem> {

    private const val FEED_URL = NINE_TO_FIVE_GOOGLE_URL

    private val client: HttpClient = HttpClient.newHttpClient()
    private val rfc1123Formatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<NineToFiveGoogleItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<NineToFiveGoogleItem>()

        logInfo("9to5Google: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("9to5Google: HTTP status=${response.statusCode()}")
            if (response.statusCode() !in 200..299) {
                logWarn("9to5Google: non-2xx status, body snippet=${response.body().take(300)}")
                return emptyList()
            }
            response.body()
        } catch (e: Exception) {
            logWarn("9to5Google: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("9to5Google: failed to parse XML: ${e.message}")
            logWarn("9to5Google raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0

        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            totalItems++
            val item = items.item(i)
            val children = item.childNodes

            var title: String? = null
            var linkHref: String? = null
            var pubDateStr: String? = null
            var author: String? = null
            var description: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                    "pubdate" -> pubDateStr = node.textContent
                    "dc:creator", "author" -> author = node.textContent
                    "description", "content:encoded" -> description = node.textContent
                    "category" -> cleanText(node.textContent)?.let { categories += it }
                }
            }

            val published = parseDate(pubDateStr) ?: continue
            parsedItems++

            if (published <= lastCheck) continue

            afterFilterItems++

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += NineToFiveGoogleItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                summary = cleanAndTruncate(description),
                categories = categories
            )
        }

        logInfo(
            "9to5Google: items total=$totalItems, parsed=$parsedItems, " +
                "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }

    private fun parseDate(raw: String?): Instant? {
        raw ?: return null
        return try {
            ZonedDateTime.parse(raw.trim(), rfc1123Formatter).toInstant()
        } catch (e: Exception) {
            logWarn("9to5Google: cannot parse pubDate '$raw': ${e.message}")
            null
        }
    }
}
