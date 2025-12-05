package news.androidweekly

import news.ANDROID_WEEKLY_FALLBACK_URL
import news.ANDROID_WEEKLY_URL
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

object AndroidWeeklyProvider: NewsProvider<AndroidWeeklyItem> {

    private val feedUrls = listOf(
        ANDROID_WEEKLY_URL,
        ANDROID_WEEKLY_FALLBACK_URL
    )

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<AndroidWeeklyItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<AndroidWeeklyItem>()

        var chosenFeed: String? = null
        var xml: String? = null

        for (feedUrl in feedUrls) {
            logInfo("AndroidWeekly: fetching $feedUrl")

            val request = HttpRequest.newBuilder()
                .uri(URI(feedUrl))
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

            val body = try {
                val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
                logInfo("AndroidWeekly: HTTP status for $feedUrl = ${resp.statusCode()}")
                if (resp.statusCode() != 200) {
                    logWarn(
                        "AndroidWeekly: non-200 status for $feedUrl, " +
                                "body snippet=${resp.body().take(300)}"
                    )
                    null
                } else {
                    resp.body()
                }
            } catch (e: Exception) {
                logWarn("AndroidWeekly: failed to fetch $feedUrl: ${e.message}")
                null
            }

            if (body != null) {
                xml = body
                chosenFeed = feedUrl
                break
            }
        }

        if (xml == null || chosenFeed == null) {
            return emptyList()
        }

        logInfo("AndroidWeekly: using feed $chosenFeed")

        val feedXml = xml ?: return emptyList()

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(feedXml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("AndroidWeekly: failed to parse XML, error=${e.message}")
            logWarn("AndroidWeekly raw snippet: ${feedXml.take(300)}")
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
            var description: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                    "pubdate" -> pubDateStr = node.textContent
                    "description" -> description = node.textContent
                    "category" -> cleanText(node.textContent)?.let { categories += it }
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
                url = url,
                summary = cleanAndTruncate(description),
                categories = categories
            )
        }

        logInfo("AndroidWeekly: items total=$totalItems, parsed=$parsedItems, " + "afterFilter=$afterFilterItems, result=${result.size}")
        return result.sortedBy { it.published }
    }
}