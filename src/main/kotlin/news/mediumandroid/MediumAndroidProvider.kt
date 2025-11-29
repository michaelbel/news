package news.mediumandroid

import news.MEDIUM_ANDROID_DEVELOPERS_URL
import news.NewsProvider
import news.Timestamp
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

object MediumAndroidProvider: NewsProvider<MediumAndroidItem> {

    private const val FEED_URL = MEDIUM_ANDROID_DEVELOPERS_URL

    override fun fetchItems(lastCheck: Instant): List<MediumAndroidItem> {
        val client = HttpClient.newHttpClient()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }

        val result = mutableListOf<MediumAndroidItem>()

        System.err.println("==== Medium Android: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            System.err.println(
                "Medium Android HTTP status=${resp.statusCode()}"
            )
            if (resp.statusCode() !in 200..299) {
                System.err.println(
                    "Medium Android: non-2xx, body snippet=${resp.body().take(200)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            System.err.println(
                "Medium Android: failed to fetch $FEED_URL: ${e.message}"
            )
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            System.err.println(
                "Medium Android: failed to parse XML: ${e.message}"
            )
            return emptyList()
        }

        val items = doc.getElementsByTagName("item")

        var total = 0
        var parsed = 0
        var afterFilter = 0

        for (i in 0 until items.length) {
            total += 1
            val item = items.item(i)
            val children = item.childNodes

            var pubDateStr: String? = null
            var updatedStr: String? = null
            var title: String? = null
            var linkHref: String? = null

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "pubdate" -> pubDateStr = node.textContent
                    "updated" -> updatedStr = node.textContent
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                }
            }

            val dateRaw = pubDateStr ?: updatedStr ?: continue
            val published = parseDate(dateRaw) ?: continue
            parsed += 1
            if (published <= lastCheck) continue

            afterFilter += 1

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += MediumAndroidItem(
                published = published,
                title = safeTitle,
                url = url
            )
        }

        System.err.println(
            "Medium Android items: total=$total, parsed=$parsed, afterFilter=$afterFilter, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }

    private fun parseDate(raw: String): Instant? {
        val iso = Timestamp.parseIso(raw)
        if (iso != null) return iso

        return try {
            ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (e: Exception) {
            System.err.println("Medium Android: cannot parse date '$raw': ${e.message}")
            null
        }
    }
}