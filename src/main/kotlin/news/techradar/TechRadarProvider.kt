package news.techradar

import news.NewsProvider
import news.TECHRADAR_ANDROID_URL
import news.cleanText
import news.logInfo
import news.logWarn
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.OffsetDateTime

object TechRadarProvider: NewsProvider<TechRadarItem> {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val articleBlockRegex = Regex(
        pattern = """(?is)<article[^>]*class="[^"]*(?:listing|article)[^"]*"[^>]*>.*?</article>"""
    )

    private val listingBlockRegex = Regex(
        pattern = """(?is)<div[^>]*class="[^"]*listingResult[^"]*"[^>]*>.*?</div>"""
    )

    private val urlRegex = Regex(
        pattern = """(?is)<a[^>]+href="([^"]+)"[^>]*class="[^"]*(?:article-link|listingResult|news-link|link)[^"]*"""
    )

    private val fallbackUrlRegex = Regex(
        pattern = """(?is)<a[^>]+href="(https://www\.techradar\.com/[^"]+)"""
    )

    private val titleRegex = Regex(
        pattern = """(?is)<h3[^>]*class="[^"]*(?:article-name|news-article-title|card__title)[^"]*"[^>]*>(.*?)</h3>"""
    )

    private val spanTitleRegex = Regex(
        pattern = """(?is)<span[^>]*class="[^"]*(?:article-name|news-article-title|card__title)[^"]*"[^>]*>(.*?)</span>"""
    )

    private val timeRegex = Regex(
        pattern = """(?is)<time[^>]+datetime="([^"]+)"""
    )

    override fun fetchItems(lastCheck: Instant): List<TechRadarItem> {
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        logInfo("TechRadar: fetching $TECHRADAR_ANDROID_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(TECHRADAR_ANDROID_URL))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .GET()
            .build()

        val html = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("TechRadar: HTTP status=${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                logWarn("TechRadar: non-2xx, body snippet=${resp.body().take(200)}")
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("TechRadar: failed to fetch page: ${e.message}")
            return emptyList()
        }

        val blocks = mutableListOf<String>().apply {
            addAll(articleBlockRegex.findAll(html).map { it.value })
            if (isEmpty()) {
                addAll(listingBlockRegex.findAll(html).map { it.value })
            }
        }

        if (blocks.isEmpty()) {
            logWarn("TechRadar: no article blocks found")
            return emptyList()
        }

        val seenUrls = mutableSetOf<String>()
        val result = mutableListOf<TechRadarItem>()
        var totalItems = 0
        var parsedItems = 0

        for (block in blocks) {
            totalItems += 1
            val urlRaw = extractUrl(block) ?: continue
            val normalizedUrl = normalizeUrl(urlRaw)
            if (!seenUrls.add(normalizedUrl)) continue

            val titleRaw = extractTitle(block) ?: continue
            val published = extractPublished(block)

            parsedItems += 1
            if (published <= lastCheck) continue

            result += TechRadarItem(
                published = published,
                title = cleanupHtml(titleRaw),
                url = normalizedUrl
            )

            if (result.size >= 20) break
        }

        logInfo("TechRadar: items total=$totalItems, parsed=$parsedItems, result=${result.size}")

        return result.sortedBy { it.published }
    }

    private fun extractUrl(block: String): String? {
        return urlRegex
            .find(block)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: fallbackUrlRegex
                .find(block)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
    }

    private fun extractTitle(block: String): String? {
        return titleRegex
            .find(block)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: spanTitleRegex
                .find(block)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
    }

    private fun extractPublished(block: String): Instant {
        val raw = timeRegex
            .find(block)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return Instant.now()

        return parseDate(raw)
    }

    private fun parseDate(raw: String): Instant {
        return try {
            Instant.parse(raw)
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(raw).toInstant()
            } catch (_: Exception) {
                Instant.now()
            }
        }
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http")) trimmed else "https://www.techradar.com$trimmed"
    }

    private fun cleanupHtml(raw: String): String {
        val withoutTags = raw
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

        return cleanText(withoutTags) ?: ""
    }
}
