package news.youtube

import news.NewsItem
import news.Timestamp
import news.YOUTUBE_CHANNELS
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

data class YoutubeItem(
    override val published: Instant,
    override val title: String,
    override val url: String
) : NewsItem

object YoutubeProvider {

    fun fetchItems(lastCheck: Instant): List<YoutubeItem> {
        val feeds = YOUTUBE_CHANNELS
        if (feeds.isEmpty()) return emptyList()

        val client = HttpClient.newHttpClient()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<YoutubeItem>()

        for (feed in feeds) {
            System.err.println("==== YouTube: fetching $feed")

            val request = HttpRequest.newBuilder()
                .uri(URI(feed))
                .GET()
                .build()

            val xml = try {
                val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
                System.err.println("YouTube HTTP status=${resp.statusCode()}")
                if (resp.statusCode() !in 200..299) {
                    System.err.println(
                        "YouTube: non-2xx " +
                                "body snippet=${resp.body().take(200)}"
                    )
                    continue
                }
                resp.body()
            } catch (e: Exception) {
                System.err.println("YouTube: failed to fetch $feed: ${e.message}")
                continue
            }

            val doc = try {
                val builder = factory.newDocumentBuilder()
                builder
                    .parse(xml.byteInputStream())
                    .apply { documentElement.normalize() }
            } catch (e: Exception) {
                System.err.println("YouTube: failed to parse XML: ${e.message}")
                continue
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
                var videoId: String? = null
                var linkHref: String? = null

                for (j in 0 until children.length) {
                    val node = children.item(j)
                    when (node.nodeName) {
                        "published" -> publishedStr = node.textContent
                        "title" -> title = node.textContent
                        "yt:videoId" -> videoId = node.textContent
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

                val url = when {
                    !videoId.isNullOrBlank() -> "https://www.youtube.com/watch?v=$videoId"
                    !linkHref.isNullOrBlank() -> linkHref
                    else -> ""
                }

                result += YoutubeItem(
                    published = published,
                    title = safeTitle,
                    url = url
                )
            }

            System.err.println(
                "YouTube feed: total=$total, parsed=$parsed, " +
                        "afterFilter=$afterFilter"
            )
        }

        System.err.println("YouTube items collected total: ${result.size}")

        return result.sortedBy { it.published }
    }
}