package news.androidblog

import news.Timestamp
import java.net.URI
import java.net.http.HttpClient
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

    private const val FEED_URL: String =
        "https://android-developers.blogspot.com/atom.xml"

    fun fetchItems(lastCheck: Instant): List<AndroidBlogItem> {
        val client = HttpClient.newHttpClient()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<AndroidBlogItem>()

        System.err.println("Fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            resp.body()
        } catch (e: Exception) {
            System.err.println("Failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            System.err.println("Failed to parse XML for $FEED_URL: ${e.message}")
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
                System.err.println("AndroidBlog: cannot parse date '$publishedStr'")
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

        System.err.println(
            "AndroidBlog: entries total=$totalEntries, parsed=$parsedEntries, afterFilter=$afterFilterEntries, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}