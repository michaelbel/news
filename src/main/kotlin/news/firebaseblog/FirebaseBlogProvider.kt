package news.firebaseblog

import news.FIREBASE_BLOG_URL
import news.NewsProvider
import news.Timestamp
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

object FirebaseBlogProvider: NewsProvider<FirebaseBlogItem> {

    private const val FEED_URL = FIREBASE_BLOG_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    override fun fetchItems(lastCheck: Instant): List<FirebaseBlogItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }

        logInfo("FirebaseBlog: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("FirebaseBlog: HTTP status for $FEED_URL = ${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                logWarn(
                    "FirebaseBlog: non-2xx status for $FEED_URL, " +
                            "body snippet=${resp.body().take(300)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("FirebaseBlog: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("FirebaseBlog: failed to parse XML, error=${e.message}")
            logWarn("FirebaseBlog raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        val items = doc.getElementsByTagName("item")

        val result = mutableListOf<FirebaseBlogItem>()
        var total = 0
        var parsed = 0
        var afterFilter = 0

        for (i in 0 until items.length) {
            total++
            val item = items.item(i)
            val children = item.childNodes

            var title: String? = null
            var linkHref: String? = null
            var publishedRaw: String? = null
            var author: String? = null
            var description: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                    "pubdate", "published", "updated" -> publishedRaw = node.textContent
                    "author", "dc:creator" -> author = node.textContent
                    "description" -> description = node.textContent
                    "category" -> cleanText(node.textContent)?.let { categories += it }
                }
            }

            val published = publishedRaw?.let(::parseDate) ?: continue
            parsed++

            if (published <= lastCheck) {
                continue
            }

            afterFilter++

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += FirebaseBlogItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                summary = cleanAndTruncate(description),
                categories = categories
            )
        }

        logInfo(
            "FirebaseBlog: items total=$total, parsed=$parsed, afterFilter=$afterFilter, " +
                    "result=${result.size}"
        )

        return result.sortedBy { it.published }
    }

    private fun parseDate(raw: String): Instant? {
        val iso = Timestamp.parseIso(raw)
        if (iso != null) return iso

        return try {
            ZonedDateTime.parse(raw.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (e: Exception) {
            logWarn("FirebaseBlog: cannot parse date '$raw': ${e.message}")
            null
        }
    }
}