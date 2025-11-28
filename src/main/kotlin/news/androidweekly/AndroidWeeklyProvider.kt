package news.androidweekly

import news.ANDROID_WEEKLY_URL
import news.NewsItem
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

data class AndroidWeeklyItem(
    override val published: Instant,
    override val title: String,
    override val url: String
) : NewsItem

object AndroidWeeklyProvider {

    private const val FEED_URL = ANDROID_WEEKLY_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME

    fun fetchItems(lastCheck: Instant): List<AndroidWeeklyItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<AndroidWeeklyItem>()

        logInfo("AndroidWeekly: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("AndroidWeekly: HTTP status for $FEED_URL = ${resp.statusCode()}")
            if (resp.statusCode() != 200) {
                logWarn(
                    "AndroidWeekly: non-200 status for $FEED_URL, " +
                            "body snippet=${resp.body().take(300)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("AndroidWeekly: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("AndroidWeekly: failed to parse XML, error=${e.message}")
            logWarn("AndroidWeekly raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0

        // RSS2: <item> внутри <channel>
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i)
            val children = item.childNodes

            totalItems++

            var title: String? = null
            var linkHref: String? = null
            var pubDateStr: String? = null

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                    "pubdate" -> pubDateStr = node.textContent
                }
            }

            if (pubDateStr == null) continue

            val published: Instant = try {
                ZonedDateTime.parse(pubDateStr.trim(), rfc1123Formatter).toInstant()
            } catch (e: Exception) {
                logWarn("AndroidWeekly: cannot parse pubDate '$pubDateStr': ${e.message}")
                continue
            }

            parsedItems++

            if (published <= lastCheck) {
                continue
            }

            afterFilterItems++

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += AndroidWeeklyItem(
                published = published,
                title = safeTitle,
                url = url
            )
        }

        logInfo(
            "AndroidWeekly: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}