package news.androidblog

import news.ANDROID_BLOG_URL
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

    fun fetchItems(lastCheck: Instant): List<AndroidBlogItem> {
        val feedUrl = ANDROID_BLOG_URL

        val client = HttpClient.newHttpClient()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<AndroidBlogItem>()

        System.err.println("==== AndroidBlog: fetching $feedUrl")

        val request = HttpRequest.newBuilder()
            .uri(URI(feedUrl))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            System.err.println("AndroidBlog HTTP status=${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                System.err.println(
                    "AndroidBlog: non-2xx, body snippet=${resp.body().take(200)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            System.err.println("AndroidBlog: failed to fetch $feedUrl: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            System.err.println("AndroidBlog: failed to parse XML: ${e.message}")
            return emptyList()
        }

        val entries = doc.getElementsByTagName("entry")

        var total = 0
        var parsed = 0
        var afterFilter = 0

        for (i in 0 until entries.length) {
            total += 1
            val entry = entries.item(i)
            val children = entry.childNodes

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
            val published = Timestamp.parseIso(publishedStr) ?: continue
            parsed += 1
            if (published <= lastCheck) continue

            afterFilter += 1

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
            "AndroidBlog: total=$total, parsed=$parsed, afterFilter=$afterFilter, " +
                    "result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}