package news.androidauthority

import news.ANDROID_AUTHORITY_URL
import news.NewsProvider
import news.cleanAndTruncate
import news.cleanText
import news.logInfo
import news.logWarn
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpClient.Redirect
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

object AndroidAuthorityProvider : NewsProvider<AndroidAuthorityItem> {

    private const val FEED_URL = ANDROID_AUTHORITY_URL

    private val client = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<AndroidAuthorityItem> {
        logInfo("AndroidAuthority: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/124.0.0.0 Safari/537.36"
            )
            .header(
                "Accept",
                "application/rss+xml,application/xml;q=0.9,text/xml;q=0.8,*/*;q=0.7"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("AndroidAuthority: HTTP status=${resp.statusCode()}")
            if (resp.statusCode() != 200) {
                logWarn("AndroidAuthority: non-200 response, body snippet=${resp.body().take(300)}")
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("AndroidAuthority: failed to fetch feed: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("AndroidAuthority: failed to parse XML: ${e.message}")
            logWarn("AndroidAuthority raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        val items = doc.getElementsByTagName("item")

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0

        val result = mutableListOf<AndroidAuthorityItem>()

        for (i in 0 until items.length) {
            totalItems += 1

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

            val published = pubDateStr
                ?.let { parseDate(it) }
                ?: continue

            parsedItems += 1

            if (published <= lastCheck) {
                continue
            }

            afterFilterItems += 1

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += AndroidAuthorityItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                summary = cleanAndTruncate(description),
                categories = categories
            )
        }

        logInfo(
            "AndroidAuthority: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }

    private fun parseDate(raw: String): Instant? {
        return try {
            ZonedDateTime.parse(raw.trim(), dateFormatter).toInstant()
        } catch (e: Exception) {
            logWarn("AndroidAuthority: cannot parse pubDate '$raw': ${e.message}")
            null
        }
    }
}
