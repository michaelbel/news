package news.androidx

import news.ANDROIDX_RELEASE_NOTES_URL
import news.NewsProvider
import news.Timestamp
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

object AndroidxReleaseNotesProvider: NewsProvider<AndroidxReleaseNotesItem> {

    private const val FEED_URL = ANDROIDX_RELEASE_NOTES_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<AndroidxReleaseNotesItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<AndroidxReleaseNotesItem>()

        logInfo("AndroidXReleaseNotes: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("AndroidXReleaseNotes: HTTP status for $FEED_URL = ${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                logWarn(
                    "AndroidXReleaseNotes: non-2xx status for $FEED_URL, " +
                            "body snippet=${resp.body().take(300)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("AndroidXReleaseNotes: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("AndroidXReleaseNotes: failed to parse XML, error=${e.message}")
            logWarn("AndroidXReleaseNotes raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        val entries = doc.getElementsByTagName("entry")
        val items = doc.getElementsByTagName("item")

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0

        fun addItem(
            title: String?,
            linkHref: String?,
            published: Instant?,
            author: String?,
            categories: List<String>
        ) {
            if (published == null) return

            parsedItems++
            if (published <= lastCheck) {
                return
            }
            afterFilterItems++

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: return

            result += AndroidxReleaseNotesItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                categories = categories
            )
        }

        if (entries.length > 0) {
            for (i in 0 until entries.length) {
                totalItems++

                val entry = entries.item(i)
                val children = entry.childNodes

                var publishedStr: String? = null
                var updatedStr: String? = null
                var title: String? = null
                var linkHref: String? = null
                var author: String? = null
                val categories = mutableListOf<String>()

                for (j in 0 until children.length) {
                    val node = children.item(j)
                    when (node.nodeName.lowercase()) {
                        "published" -> publishedStr = node.textContent
                        "updated" -> updatedStr = node.textContent
                        "title" -> title = node.textContent
                        "link" -> {
                            val rel = node.attributes?.getNamedItem("rel")?.nodeValue
                            val href = node.attributes?.getNamedItem("href")?.nodeValue
                            if (linkHref == null && (rel == null || rel == "alternate")) {
                                linkHref = href
                            }
                        }
                        "author" -> {
                            val nameNode = node.childNodes
                                .let { authorChildren ->
                                    (0 until authorChildren.length)
                                        .map(authorChildren::item)
                                        .firstOrNull { it.nodeName == "name" }
                                }
                            author = nameNode?.textContent ?: node.textContent
                        }
                        "category" -> {
                            val term = node.attributes?.getNamedItem("term")?.nodeValue
                            val label = term ?: node.textContent
                            cleanText(label)?.let { categories += it }
                        }
                    }
                }

                val published = Timestamp.parseIso(publishedStr ?: updatedStr ?: "")
                addItem(title, linkHref, published, author, categories)
            }
        } else if (items.length > 0) {
            for (i in 0 until items.length) {
                totalItems++

                val item = items.item(i)
                val children = item.childNodes

                var title: String? = null
                var linkHref: String? = null
                var pubDateStr: String? = null
                var author: String? = null
                val categories = mutableListOf<String>()

                for (j in 0 until children.length) {
                    val node = children.item(j)
                    when (node.nodeName.lowercase()) {
                        "title" -> title = node.textContent
                        "link" -> linkHref = node.textContent
                        "pubdate" -> pubDateStr = node.textContent
                        "dc:creator", "author" -> author = node.textContent
                        "category" -> cleanText(node.textContent)?.let { categories += it }
                    }
                }

                val published = pubDateStr?.let {
                    try {
                        ZonedDateTime.parse(it.trim(), rfc1123Formatter).toInstant()
                    } catch (e: Exception) {
                        logWarn("AndroidXReleaseNotes: cannot parse pubDate '$it': ${e.message}")
                        null
                    }
                }
                addItem(title, linkHref, published, author, categories)
            }
        }

        logInfo(
            "AndroidXReleaseNotes: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}
