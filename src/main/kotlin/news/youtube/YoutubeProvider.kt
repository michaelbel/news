package news.youtube

data class YoutubeItem(
    val published: java.time.Instant,
    val title: String,
    val url: String
)

private data class YoutubeFeed(
    val url: String,
    val label: String
)

private val feeds = listOf(
    YoutubeFeed(
        url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCVHFbqXqoYvEWM1Ddxl0QDg",
        label = "AndroidDevelopers"
    ),
    YoutubeFeed(
        url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCKNTZMRHPLXfqlbdOI7mCkg",
        label = "PhilippLackner"
    ),
    YoutubeFeed(
        url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCKsqMPIIhev3qbMxCL8Emvw",
        label = "AndroidBroadcast"
    ),
    YoutubeFeed(
        url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCQFhFs4Ff1BP__DWMD5gC5g",
        label = "gulyaev_it"
    ),
    YoutubeFeed(
        url = "https://www.youtube.com/feeds/videos.xml?channel_id=UC71omjio31Esx7LytaZ2ytA",
        label = "JovMit"
    ),
    YoutubeFeed(
        url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCofyDdGnCssPNwABNkxLFKg",
        label = "randrushchenko"
    ),
    YoutubeFeed(
        url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCP7uiEZIqci43m22KDl0sNw",
        label = "Kotlin"
    ),
    YoutubeFeed(
        url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCIjEgHA1vatSR2K4rfcdNRg",
        label = "AristiDevs"
    ),
    YoutubeFeed(
        url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCSmp6mEF9bml3sinNuKcZZg",
        label = "offer_factory"
    ),
    YoutubeFeed(
        url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCy9dX2OpiYIDRF-wduZSXPQ",
        label = "ievetrov"
    ),
    YoutubeFeed(
        url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCYLAirIEMMXtWOECuZAtjqQ",
        label = "StevdzaSan"
    ),
    YoutubeFeed(
        url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCFaLqiT8yyi4UYeA7NMPRIA",
        label = "typealias"
    ),
    YoutubeFeed(
        url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCTjQSpx2waqXTC37AgM8qyA",
        label = "NativeMobileBits"
    )
)

object YoutubeProvider {

    fun fetchItems(lastCheck: java.time.Instant): List<YoutubeItem> {
        if (feeds.isEmpty()) return emptyList()

        val client = java.net.http.HttpClient.newHttpClient()
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<YoutubeItem>()

        for (feed in feeds) {
            System.err.println("Fetching ${feed.url}")

            val request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI(feed.url))
                .GET()
                .build()

            val xml = try {
                val resp = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                resp.body()
            } catch (e: Exception) {
                System.err.println("Failed to fetch ${feed.url}: ${e.message}")
                continue
            }

            val doc = try {
                val builder = factory.newDocumentBuilder()
                builder
                    .parse(xml.byteInputStream())
                    .apply { documentElement.normalize() }
            } catch (e: Exception) {
                System.err.println("Failed to parse XML for ${feed.url}: ${e.message}")
                continue
            }

            val entries = doc.getElementsByTagName("entry")
            for (i in 0 until entries.length) {
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
                val published = news.Timestamp.parseIso(publishedStr) ?: continue
                if (published <= lastCheck) continue

                val safeTitle = title
                    ?.trim()
                    .takeUnless { it.isNullOrEmpty() }
                    ?: "(no title)"

                val url = when {
                    !videoId.isNullOrBlank() -> "https://www.youtube.com/watch?v=$videoId"
                    !linkHref.isNullOrBlank() -> linkHref!!
                    else -> ""
                }

                result += YoutubeItem(
                    published = published,
                    title = safeTitle,
                    url = url
                )
            }
        }

        return result.sortedBy { it.published }
    }
}