package news.kotlinblog

import news.KOTLIN_BLOG_URL
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

object KotlinBlogProvider: NewsProvider<KotlinBlogItem> {

    private const val FEED_URL = KOTLIN_BLOG_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<KotlinBlogItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<KotlinBlogItem>()

        logInfo("KotlinBlog: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("KotlinBlog: HTTP status for $FEED_URL = ${resp.statusCode()}")
            if (resp.statusCode() != 200) {
                logWarn(
                    "KotlinBlog: non-200 status for $FEED_URL, " +
                            "body snippet=${resp.body().take(300)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("KotlinBlog: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("KotlinBlog: failed to parse XML, error=${e.message}")
            logWarn("KotlinBlog raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0

        // RSS2 формат: элементы в теге <item>
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i)
            val children = item.childNodes

            totalItems++

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

            if (pubDateStr == null) continue

            val published: Instant = try {
                ZonedDateTime.parse(pubDateStr.trim(), rfc1123Formatter).toInstant()
            } catch (e: Exception) {
                logWarn("KotlinBlog: cannot parse pubDate '$pubDateStr': ${e.message}")
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

            result += KotlinBlogItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                summary = cleanAndTruncate(description),
                categories = categories
            )
        }

        logInfo(
            "KotlinBlog: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}