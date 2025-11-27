package news.androidblog

data class AndroidBlogItem(
    val published: java.time.Instant,
    val title: String,
    val url: String
)

object AndroidBlogProvider {

    private const val FEED_URL: String =
        "https://android-developers.blogspot.com/atom.xml"

    fun fetchItems(lastCheck: java.time.Instant): List<AndroidBlogItem> {
        val client = java.net.http.HttpClient.newHttpClient()
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<AndroidBlogItem>()

        System.err.println("Fetching $FEED_URL")

        val request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
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

        val entries = doc.getElementsByTagName("entry")
        for (i in 0 until entries.length) {
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
            val published = news.Timestamp.parseIso(publishedStr) ?: continue
            if (published <= lastCheck) continue

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

        return result.sortedBy { it.published }
    }
}