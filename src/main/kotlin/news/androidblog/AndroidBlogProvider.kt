package news.androidblog

import news.Timestamp
import news.logInfo
import news.logWarn
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpClient.Redirect
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

data class AndroidBlogItem(
    val published: Instant,
    val title: String,
    val url: String
)

object AndroidBlogProvider {

    // Финальный фид Android Developers Blog
    private const val FEED_URL_PRIMARY: String =
        "https://android-developers.googleblog.com/atom.xml"

    // Фолбэк на случай странных редиректов
    private const val FEED_URL_FALLBACK: String =
        "http://android-developers.googleblog.com/atom.xml"

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    fun fetchItems(lastCheck: Instant): List<AndroidBlogItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<AndroidBlogItem>()

        fun fetchXml(url: String): String? {
            logInfo("AndroidBlog: fetching $url")

            val request = HttpRequest.newBuilder()
                .uri(URI(url))
                .GET()
                .build()

            return try {
                val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
                logInfo("AndroidBlog: HTTP status for $url = ${resp.statusCode()}")
                if (resp.statusCode() != 200) {
                    logWarn(
                        "AndroidBlog: non-200 status for $url, " +
                                "body snippet=${resp.body().take(300)}"
                    )
                    null
                } else {
                    resp.body()
                }
            } catch (e: Exception) {
                logWarn("AndroidBlog: failed to fetch $url: ${e.message}")
                null
            }
        }

        val xml = fetchXml(FEED_URL_PRIMARY)
            ?: fetchXml(FEED_URL_FALLBACK)
            ?: return emptyList()

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("AndroidBlog: failed to parse XML, error=${e.message}")
            logWarn("AndroidBlog raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        var totalEntries = 0
        var parsedEntries = 0
        var afterFilterEntries = 0

        val entries = doc.getElementsByTagName("entry")
        for (i in 0 until entries.length) {
            val entry = entries.item(i)
            val children = entry.childNodes

            totalEntries++

            var publishedStr: String? = null
            var title: String? = null
            var linkHref: String? = null

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName) {
                    "published" -> publishedStr = node.textContent
                    "title" -> title = node.textContent
                    "link" -> {
                        val rel = node.attributes?.getNamedItem("rel")?.nodeValue
                        if (rel == "alternate") {
                            linkHref = node.attributes
                                ?.getNamedItem("href")
                                ?.nodeValue
                        }
                    }
                }
            }

            if (publishedStr == null) continue

            val published = Timestamp.parseIso(publishedStr)
            if (published == null) {
                logWarn("AndroidBlog: cannot parse date '$publishedStr'")
                continue
            }

            parsedEntries++

            if (published <= lastCheck) {
                continue
            }

            afterFilterEntries++

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref ?: continue

            result += AndroidBlogItem(
                published = published,
                title = safeTitle,
                url = url
            )
        }

        logInfo(
            "AndroidBlog: entries total=$totalEntries, parsed=$parsedEntries, " +
                    "afterFilter=$afterFilterEntries, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}