package news

import news.github.GithubReleaseItem
import news.githubtrending.GithubTrendingKotlinItem
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpClient.Redirect
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

object AndroidAuthorityProvider : NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = ANDROID_AUTHORITY_URL

    private val client = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        val secureFeatures = listOf(
            XMLConstants.FEATURE_SECURE_PROCESSING to true,
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false
        )
        secureFeatures.forEach { (feature, enabled) ->
            runCatching { setFeature(feature, enabled) }
                .onFailure { logWarn("AndroidAuthority: cannot set XML feature $feature: ${it.message}") }
        }
    }

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        logInfo("AndroidAuthority: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/124.0.0.0 Safari/537.36"
            )
            .header(
                "Accept",
                "application/rss+xml,application/xml;q=0.9,text/xml;q=0.8,*/*;q=0.7"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("AndroidAuthority: HTTP status=${resp.statusCode()}")
            if (resp.statusCode() != 200) {
                logWarn("AndroidAuthority: non-200 response, body snippet=${resp.body().take(300)}")
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("AndroidAuthority: failed to fetch feed: ${e.message}")
            return emptyList()
        }

        val sanitizedXml = sanitizeXml(xml)

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(sanitizedXml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("AndroidAuthority: failed to parse XML: ${e.message}")
            logWarn("AndroidAuthority raw snippet: ${sanitizedXml.take(300)}")
            return emptyList()
        }

        val items = doc.getElementsByTagName("item")

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0
        var translatedTitles = 0
        var translationFallbacks = 0

        val result = mutableListOf<SimpleNewsItem>()

        for (i in 0 until items.length) {
            totalItems += 1

            val item = items.item(i)
            val children = item.childNodes

            var title: String? = null
            var linkHref: String? = null
            var pubDateStr: String? = null
            var author: String? = null
            var description: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                    "pubdate" -> pubDateStr = node.textContent
                    "dc:creator", "author" -> author = node.textContent
                    "description", "content:encoded" -> description = node.textContent
                    "category" -> cleanText(node.textContent)?.let { categories += it }
                }
            }

            val published = pubDateStr
                ?.let { parseDate(it) }
                ?: continue

            parsedItems += 1

            if (published <= lastCheck) {
                continue
            }

            afterFilterItems += 1

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val translatedTitle = TranslationClient.translateToTarget(
                text = safeTitle,
                sourceLang = "en",
                context = "AndroidAuthority title"
            )

            val finalTitle = translatedTitle?.also { translatedTitles += 1 } ?: run {
                translationFallbacks += 1
                logWarn("AndroidAuthority: translation failed, keeping original title='${safeTitle.take(80)}'")
                safeTitle
            }

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += SimpleNewsItem(
                published = published,
                title = finalTitle,
                url = url,
                author = cleanText(author),
                summary = cleanAndTruncate(description),
                categories = categories
            )
        }

        logInfo(
            "AndroidAuthority: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}, " +
                    "translated=$translatedTitles, translationFallbacks=$translationFallbacks"
        )

        return result.sortedBy { it.published }
    }

    private fun parseDate(raw: String): Instant? {
        return try {
            ZonedDateTime.parse(raw.trim(), dateFormatter).toInstant()
        } catch (e: Exception) {
            logWarn("AndroidAuthority: cannot parse pubDate '$raw': ${e.message}")
            null
        }
    }

    private fun sanitizeXml(xml: String): String {
        val invalidCharsRegex = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uFFFE\\uFFFF]")
        if (!invalidCharsRegex.containsMatchIn(xml)) {
            return xml
        }

        logWarn("AndroidAuthority: removed invalid XML control characters from feed")
        return invalidCharsRegex.replace(xml, "")
    }
}

object AndroidBlogProvider: NewsProvider<SimpleNewsItem> {

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val feedUrl = ANDROID_BLOG_URL

        val client = HttpClient.newHttpClient()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

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
            var author: String? = null
            var summary: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName) {
                    "published" -> publishedStr = node.textContent
                    "title" -> title = node.textContent
                    "summary" -> summary = node.textContent
                    "link" -> {
                        val rel = node.attributes?.getNamedItem("rel")?.nodeValue
                        if (rel == "alternate") {
                            linkHref = node.attributes
                                ?.getNamedItem("href")
                                ?.nodeValue
                        }
                    }
                    "author" -> {
                        val nameNode = node.childNodes
                            .let { children ->
                                (0 until children.length)
                                    .map(children::item)
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

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                summary = cleanAndTruncate(summary),
                categories = categories
            )
        }

        System.err.println(
            "AndroidBlog: total=$total, parsed=$parsed, afterFilter=$afterFilter, " +
                    "result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}

object AndroidPoliceProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = ANDROID_POLICE_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

        logInfo("AndroidPolice: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("AndroidPolice: HTTP status for $FEED_URL = ${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                logWarn(
                    "AndroidPolice: non-2xx status for $FEED_URL, " +
                            "body snippet=${resp.body().take(300)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("AndroidPolice: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("AndroidPolice: failed to parse XML, error=${e.message}")
            logWarn("AndroidPolice raw snippet: ${xml.take(300)}")
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

            result += SimpleNewsItem(
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
                        logWarn("AndroidPolice: cannot parse pubDate '$it': ${e.message}")
                        null
                    }
                }
                addItem(title, linkHref, published, author, categories)
            }
        }

        logInfo(
            "AndroidPolice: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}

object AndroidStudioBlogProvider: NewsProvider<SimpleNewsItem> {

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val feedUrl = ANDROID_STUDIO_BLOG_URL

        val client = HttpClient.newHttpClient()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

        System.err.println("==== AndroidStudioBlog: fetching $feedUrl")

        val request = HttpRequest.newBuilder()
            .uri(URI(feedUrl))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            System.err.println("AndroidStudioBlog HTTP status=${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                System.err.println(
                    "AndroidStudioBlog: non-2xx, body snippet=${resp.body().take(200)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            System.err.println("AndroidStudioBlog: failed to fetch $feedUrl: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            System.err.println("AndroidStudioBlog: failed to parse XML: ${e.message}")
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
            var summary: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName) {
                    "published" -> publishedStr = node.textContent
                    "title" -> title = node.textContent
                    "summary" -> summary = node.textContent
                    "link" -> {
                        val rel = node.attributes?.getNamedItem("rel")?.nodeValue
                        if (rel == "alternate") {
                            linkHref = node.attributes
                                ?.getNamedItem("href")
                                ?.nodeValue
                        }
                    }
                    "category" -> {
                        val term = node.attributes?.getNamedItem("term")?.nodeValue
                        val label = term ?: node.textContent
                        cleanText(label)?.let { categories += it }
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

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                summary = cleanAndTruncate(summary),
                categories = categories
            )
        }

        System.err.println(
            "AndroidStudioBlog: total=$total, parsed=$parsed, afterFilter=$afterFilter, " +
                    "result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}

object AndroidWeeklyProvider: NewsProvider<SimpleNewsItem> {

    private val feedUrls = listOf(
        ANDROID_WEEKLY_URL,
        ANDROID_WEEKLY_FALLBACK_URL
    )

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

        var chosenFeed: String? = null
        var xml: String? = null

        for (feedUrl in feedUrls) {
            logInfo("AndroidWeekly: fetching $feedUrl")

            val request = HttpRequest.newBuilder()
                .uri(URI(feedUrl))
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/124.0.0.0 Safari/537.36"
                )
                .header(
                    "Accept",
                    "application/rss+xml,application/xml;q=0.9,text/xml;q=0.8,*/*;q=0.7"
                )
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build()

            val body = try {
                val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
                logInfo("AndroidWeekly: HTTP status for $feedUrl = ${resp.statusCode()}")
                if (resp.statusCode() != 200) {
                    logWarn(
                        "AndroidWeekly: non-200 status for $feedUrl, " +
                                "body snippet=${resp.body().take(300)}"
                    )
                    null
                } else {
                    resp.body()
                }
            } catch (e: Exception) {
                logWarn("AndroidWeekly: failed to fetch $feedUrl: ${e.message}")
                null
            }

            if (body != null) {
                xml = body
                chosenFeed = feedUrl
                break
            }
        }

        if (xml == null || chosenFeed == null) {
            return emptyList()
        }

        logInfo("AndroidWeekly: using feed $chosenFeed")

        val feedXml = xml ?: return emptyList()

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(feedXml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("AndroidWeekly: failed to parse XML, error=${e.message}")
            logWarn("AndroidWeekly raw snippet: ${feedXml.take(300)}")
            return emptyList()
        }

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0

        // RSS2: <item> внутри <channel>
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i)
            val children = item.childNodes

            totalItems++

            var title: String? = null
            var linkHref: String? = null
            var pubDateStr: String? = null
            var description: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                    "pubdate" -> pubDateStr = node.textContent
                    "description" -> description = node.textContent
                    "category" -> cleanText(node.textContent)?.let { categories += it }
                }
            }

            if (pubDateStr == null) continue

            val published: Instant = try {
                ZonedDateTime.parse(pubDateStr.trim(), rfc1123Formatter).toInstant()
            } catch (e: Exception) {
                logWarn("AndroidWeekly: cannot parse pubDate '$pubDateStr': ${e.message}")
                continue
            }

            parsedItems++

            if (published <= lastCheck) {
                continue
            }

            afterFilterItems++

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                summary = cleanAndTruncate(description),
                categories = categories
            )
        }

        logInfo("AndroidWeekly: items total=$totalItems, parsed=$parsedItems, " + "afterFilter=$afterFilterItems, result=${result.size}")
        return result.sortedBy { it.published }
    }
}

object AndroidxReleaseNotesProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = ANDROIDX_RELEASE_NOTES_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

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

            result += SimpleNewsItem(
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

object DevToProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = DEV_TO_ANDROID_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }

        logInfo("Dev.to: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/124.0.0.0 Safari/537.36"
            )
            .header(
                "Accept",
                "application/rss+xml,application/xml;q=0.9,text/xml;q=0.8,*/*;q=0.7"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .GET()
            .build()

        val xml = try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("Dev.to: HTTP status=${response.statusCode()}")
            if (response.statusCode() !in 200..299) {
                logWarn(
                    "Dev.to: non-2xx status for $FEED_URL, body snippet=${response.body().take(300)}"
                )
                return emptyList()
            }
            response.body()
        } catch (e: Exception) {
            logWarn("Dev.to: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("Dev.to: failed to parse XML: ${e.message}")
            logWarn("Dev.to raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        val items = doc.getElementsByTagName("item")
        val result = mutableListOf<SimpleNewsItem>()

        var total = 0
        var parsed = 0
        var afterFilter = 0

        for (i in 0 until items.length) {
            total += 1
            val item = items.item(i)
            val children = item.childNodes

            var title: String? = null
            var linkHref: String? = null
            var publishedRaw: String? = null
            var author: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                    "pubdate", "published", "updated" -> publishedRaw = node.textContent
                    "author", "dc:creator" -> author = node.textContent
                    "category" -> cleanText(node.textContent)?.let { categories += it }
                }
            }

            val published = publishedRaw?.let(::parseDate) ?: continue
            parsed += 1

            if (published <= lastCheck) {
                continue
            }

            afterFilter += 1

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                summary = null,
                categories = categories
            )
        }

        logInfo(
            "Dev.to items: total=$total, parsed=$parsed, afterFilter=$afterFilter, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }

    private fun parseDate(raw: String): Instant? {
        val iso = Timestamp.parseIso(raw)
        if (iso != null) return iso

        return try {
            ZonedDateTime.parse(raw.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (e: Exception) {
            logWarn("Dev.to: cannot parse date '$raw': ${e.message}")
            null
        }
    }
}

object FirebaseBlogProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = FIREBASE_BLOG_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
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

        val result = mutableListOf<SimpleNewsItem>()
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

            result += SimpleNewsItem(
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

object GithubReleasesProvider: NewsProvider<GithubReleaseItem> {

    override fun fetchItems(lastCheck: Instant): List<GithubReleaseItem> {
        val repos = GITHUB_REPOS
        if (repos.isEmpty()) return emptyList()

        val client = HttpClient.newHttpClient()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }

        val result = mutableListOf<GithubReleaseItem>()

        for (repoUrl in repos) {
            val trimmed = repoUrl.trim().removeSuffix("/")
            val label = trimmed
                .removePrefix("https://github.com/")
                .removePrefix("http://github.com/")
                .ifEmpty { trimmed }

            val feedUrl = "$trimmed/releases.atom"
            System.err.println("==== GitHub releases: fetching $feedUrl for $label")

            val request = HttpRequest.newBuilder()
                .uri(URI(feedUrl))
                .GET()
                .build()

            val xml = try {
                val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
                System.err.println("GitHub releases HTTP status=${resp.statusCode()} for $label")
                if (resp.statusCode() !in 200..299) {
                    System.err.println(
                        "GitHub releases: non-2xx for $label, " +
                                "body snippet=${resp.body().take(200)}"
                    )
                    continue
                }
                resp.body()
            } catch (e: Exception) {
                System.err.println("GitHub releases: failed to fetch $feedUrl for $label: ${e.message}")
                continue
            }

            val doc = try {
                val builder = factory.newDocumentBuilder()
                builder
                    .parse(xml.byteInputStream())
                    .apply { documentElement.normalize() }
            } catch (e: Exception) {
                System.err.println("GitHub releases: failed to parse XML for $label: ${e.message}")
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
                var updatedStr: String? = null
                var title: String? = null
                var linkHref: String? = null
                var author: String? = null
                var content: String? = null
                val categories = mutableListOf(label)

                for (j in 0 until children.length) {
                    val node = children.item(j)
                    when (node.nodeName.lowercase()) {
                        "published" -> publishedStr = node.textContent
                        "updated" -> updatedStr = node.textContent
                        "title" -> title = node.textContent
                        "author" -> {
                            val nameNode = node.childNodes
                                .let { nodes ->
                                    (0 until nodes.length)
                                        .map(nodes::item)
                                        .firstOrNull { it.nodeName.equals("name", ignoreCase = true) }
                                }
                            author = nameNode?.textContent ?: node.textContent
                        }
                        "link" -> {
                            val rel = node.attributes?.getNamedItem("rel")?.nodeValue
                            if (rel == "alternate") {
                                linkHref = node.attributes
                                    ?.getNamedItem("href")
                                    ?.nodeValue
                            }
                        }
                        "content" -> content = node.textContent
                    }
                }

                val dateRaw = publishedStr ?: updatedStr ?: continue
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

                result += GithubReleaseItem(
                    published = published,
                    repo = label,
                    title = safeTitle,
                    url = url,
                    author = cleanText(author),
                    summary = cleanAndTruncate(content),
                    categories = categories
                )
            }

            System.err.println(
                "GitHub releases for $label: total=$total, parsed=$parsed, " +
                        "afterFilter=$afterFilter"
            )
        }

        System.err.println("GitHub releases items collected total: ${result.size}")

        return result.sortedBy { it.published }
    }

    private fun parseDate(raw: String): Instant? {
        val iso = Timestamp.parseIso(raw)
        if (iso != null) return iso

        return try {
            ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (e: Exception) {
            System.err.println("GitHub releases: cannot parse date '$raw': ${e.message}")
            null
        }
    }
}

object GithubBlogProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = GITHUB_BLOG_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

        logInfo("GithubBlog: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("GithubBlog: HTTP status for $FEED_URL = ${resp.statusCode()}")
            if (resp.statusCode() != 200) {
                logWarn(
                    "GithubBlog: non-200 status for $FEED_URL, " +
                            "body snippet=${resp.body().take(300)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("GithubBlog: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("GithubBlog: failed to parse XML, error=${e.message}")
            logWarn("GithubBlog raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0

        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i)
            val children = item.childNodes

            totalItems++

            var title: String? = null
            var linkHref: String? = null
            var guid: String? = null
            var pubDateStr: String? = null
            var author: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                    "guid" -> guid = node.textContent
                    "pubdate" -> pubDateStr = node.textContent
                    "dc:creator", "author" -> author = node.textContent
                    "description", "content:encoded" -> { /* description ignored */ }
                    "category" -> cleanText(node.textContent)?.let { categories += it }
                }
            }

            if (pubDateStr == null) continue

            val published: Instant = try {
                ZonedDateTime.parse(pubDateStr.trim(), rfc1123Formatter).toInstant()
            } catch (e: Exception) {
                logWarn("GithubBlog: cannot parse pubDate '$pubDateStr': ${e.message}")
                continue
            }

            parsedItems++

            if (published <= lastCheck) {
                continue
            }

            afterFilterItems++

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: guid
                    ?.trim()
                    .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                categories = categories
            )
        }

        logInfo(
            "GithubBlog: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}

object GithubTrendingKotlinProvider: NewsProvider<GithubTrendingKotlinItem> {

    private val nameRegex = Regex(
        pattern = """<h2[^>]*>.*?<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val descriptionRegex = Regex(
        pattern = """<p[^>]*>(.*?)</p>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val starsRegex = Regex(
        pattern = """<a[^>]+href="[^"]+/stargazers"[^>]*>.*?([\d,.]+)\s*</a>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // Поддерживаем и старую разметку с /network/members, и новую с /forks
    private val forksRegex = Regex(
        pattern = """<a[^>]+href="[^"]+/(?:network/members|forks)[^"]*"[^>]*>.*?([\d,.]+)\s*</a>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    override fun fetchItems(lastCheck: Instant): List<GithubTrendingKotlinItem> {
        val client = HttpClient.newHttpClient()

        val request = HttpRequest.newBuilder()
            .uri(URI(GITHUB_TRENDING_KOTLIN_URL))
            .GET()
            .build()

        val html = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            System.err.println("GitHub trending Kotlin HTTP status=${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                System.err.println(
                    "GitHub trending Kotlin: non-2xx, body snippet=${resp.body().take(200)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            System.err.println("GitHub trending Kotlin: failed to fetch: ${e.message}")
            return emptyList()
        }

        val articles = html.split("<article", ignoreCase = true).drop(1)
        val result = mutableListOf<GithubTrendingKotlinItem>()

        for (article in articles) {
            val nameMatch = nameRegex.find(article) ?: continue
            val href = nameMatch.groupValues.getOrNull(1)?.trim().orEmpty()
            val repoName = nameMatch.groupValues.getOrNull(2)?.trim().orEmpty()
            if (href.isEmpty() || repoName.isEmpty()) continue

            val description = descriptionRegex
                .find(article)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::cleanupHtml)
                ?.ifBlank { null }

            val stars = starsRegex
                .find(article)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::parseNumber)
                ?: 0

            val forks = forksRegex
                .find(article)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::parseNumber)
                ?: 0

            val url = if (href.startsWith("http")) {
                href
            } else {
                "https://github.com$href"
            }

            result += GithubTrendingKotlinItem(
                published = Instant.now(),
                title = cleanupHtml(repoName),
                url = url,
                description = description,
                stars = stars,
                forks = forks
            )

            if (result.size >= 10) break
        }

        System.err.println("GitHub trending Kotlin items collected: ${result.size}")

        return result
    }

    private fun cleanupHtml(raw: String): String {
        val withoutTags = raw
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

        // Убираем "Star android / nowinandroid" и подобные CTA
        val withoutCta = withoutTags.replace(
            Regex("""Star\s+\S+\s*/\s*\S+""", RegexOption.IGNORE_CASE),
            " "
        )

        return withoutCta
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseNumber(raw: String): Int {
        val digitsOnly = raw.replace(Regex("[^0-9]"), "")
        return digitsOnly.toIntOrNull() ?: 0
    }
}

object GradleBlogProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = GRADLE_BLOG_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

        logInfo("GradleBlog: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("GradleBlog: HTTP status for $FEED_URL = ${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                logWarn(
                    "GradleBlog: non-2xx status for $FEED_URL, " +
                            "body snippet=${resp.body().take(300)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("GradleBlog: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("GradleBlog: failed to parse XML, error=${e.message}")
            logWarn("GradleBlog raw snippet: ${xml.take(300)}")
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

            result += SimpleNewsItem(
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
                        logWarn("GradleBlog: cannot parse pubDate '$it': ${e.message}")
                        null
                    }
                }
                addItem(title, linkHref, published, author, categories)
            }
        }

        logInfo(
            "GradleBlog: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}

object HabrAiProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = HABR_AI_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

        logInfo("HabrAI: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("HabrAI: HTTP status for $FEED_URL = ${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                logWarn(
                    "HabrAI: non-2xx status for $FEED_URL, " +
                            "body snippet=${resp.body().take(300)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("HabrAI: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("HabrAI: failed to parse XML, error=${e.message}")
            logWarn("HabrAI raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0

        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i)
            val children = item.childNodes

            totalItems++

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
                    "author" -> author = node.textContent
                    "description" -> { /* description ignored */ }
                    "category" -> cleanText(node.textContent)?.let { categories += it }
                }
            }

            if (pubDateStr == null) continue

            val published: Instant = try {
                ZonedDateTime.parse(pubDateStr.trim(), rfc1123Formatter).toInstant()
            } catch (e: Exception) {
                logWarn("HabrAI: cannot parse pubDate '$pubDateStr': ${e.message}")
                continue
            }

            parsedItems++

            if (published <= lastCheck) {
                continue
            }

            afterFilterItems++

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                categories = categories
            )
        }

        logInfo(
            "HabrAI: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}

object HabrAndroidProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = HABR_ANDROID_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

        logInfo("HabrAndroid: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("HabrAndroid: HTTP status for $FEED_URL = ${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                logWarn(
                    "HabrAndroid: non-2xx status for $FEED_URL, " +
                            "body snippet=${resp.body().take(300)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("HabrAndroid: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("HabrAndroid: failed to parse XML, error=${e.message}")
            logWarn("HabrAndroid raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0

        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i)
            val children = item.childNodes

            totalItems++

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
                    "author" -> author = node.textContent
                    "description" -> { /* description ignored */ }
                    "category" -> cleanText(node.textContent)?.let { categories += it }
                }
            }

            if (pubDateStr == null) continue

            val published: Instant = try {
                ZonedDateTime.parse(pubDateStr.trim(), rfc1123Formatter).toInstant()
            } catch (e: Exception) {
                logWarn("HabrAndroid: cannot parse pubDate '$pubDateStr': ${e.message}")
                continue
            }

            parsedItems++

            if (published <= lastCheck) {
                continue
            }

            afterFilterItems++

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                categories = categories
            )
        }

        logInfo(
            "HabrAndroid: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}

object HabrCareerProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = HABR_CAREER_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

        logInfo("HabrCareer: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("HabrCareer: HTTP status for $FEED_URL = ${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                logWarn(
                    "HabrCareer: non-2xx status for $FEED_URL, " +
                            "body snippet=${resp.body().take(300)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("HabrCareer: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("HabrCareer: failed to parse XML, error=${e.message}")
            logWarn("HabrCareer raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0

        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i)
            val children = item.childNodes

            totalItems++

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
                    "author" -> author = node.textContent
                    "description" -> { /* description ignored */ }
                    "category" -> cleanText(node.textContent)?.let { categories += it }
                }
            }

            if (pubDateStr == null) continue

            val published: Instant = try {
                ZonedDateTime.parse(pubDateStr.trim(), rfc1123Formatter).toInstant()
            } catch (e: Exception) {
                logWarn("HabrCareer: cannot parse pubDate '$pubDateStr': ${e.message}")
                continue
            }

            parsedItems++

            if (published <= lastCheck) {
                continue
            }

            afterFilterItems++

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                categories = categories
            )
        }

        logInfo(
            "HabrCareer: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}

object HabrProgrammingProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = HABR_PROGRAMMING_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

        logInfo("HabrProgramming: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("HabrProgramming: HTTP status for $FEED_URL = ${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                logWarn(
                    "HabrProgramming: non-2xx status for $FEED_URL, " +
                            "body snippet=${resp.body().take(300)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("HabrProgramming: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("HabrProgramming: failed to parse XML, error=${e.message}")
            logWarn("HabrProgramming raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0

        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i)
            val children = item.childNodes

            totalItems++

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
                    "author" -> author = node.textContent
                    "description" -> { /* description ignored */ }
                    "category" -> cleanText(node.textContent)?.let { categories += it }
                }
            }

            if (pubDateStr == null) continue

            val published: Instant = try {
                ZonedDateTime.parse(pubDateStr.trim(), rfc1123Formatter).toInstant()
            } catch (e: Exception) {
                logWarn("HabrProgramming: cannot parse pubDate '$pubDateStr': ${e.message}")
                continue
            }

            parsedItems++

            if (published <= lastCheck) {
                continue
            }

            afterFilterItems++

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                categories = categories
            )
        }

        logInfo(
            "HabrProgramming: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}

object JetBrainsBlogProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = JETBRAINS_BLOG_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

        logInfo("JetBrainsBlog: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("JetBrainsBlog: HTTP status for $FEED_URL = ${resp.statusCode()}")
            if (resp.statusCode() != 200) {
                logWarn(
                    "JetBrainsBlog: non-200 status for $FEED_URL, " +
                            "body snippet=${resp.body().take(300)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("JetBrainsBlog: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("JetBrainsBlog: failed to parse XML, error=${e.message}")
            logWarn("JetBrainsBlog raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0

        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i)
            val children = item.childNodes

            totalItems++

            var title: String? = null
            var linkHref: String? = null
            var guid: String? = null
            var pubDateStr: String? = null
            var author: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                    "guid" -> guid = node.textContent
                    "pubdate" -> pubDateStr = node.textContent
                    "dc:creator", "author" -> author = node.textContent
                    "description", "content:encoded" -> { /* description ignored */ }
                    "category" -> cleanText(node.textContent)?.let { categories += it }
                }
            }

            if (pubDateStr == null) continue

            val published: Instant = try {
                ZonedDateTime.parse(pubDateStr.trim(), rfc1123Formatter).toInstant()
            } catch (e: Exception) {
                logWarn("JetBrainsBlog: cannot parse pubDate '$pubDateStr': ${e.message}")
                continue
            }

            parsedItems++

            if (published <= lastCheck) {
                continue
            }

            afterFilterItems++

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: guid
                    ?.trim()
                    .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                categories = categories
            )
        }

        logInfo(
            "JetBrainsBlog: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}

object KotlinBlogProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = KOTLIN_BLOG_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

        logInfo("KotlinBlog: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("KotlinBlog: HTTP status for $FEED_URL = ${resp.statusCode()}")
            if (resp.statusCode() != 200) {
                logWarn(
                    "KotlinBlog: non-200 status for $FEED_URL, " +
                            "body snippet=${resp.body().take(300)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("KotlinBlog: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("KotlinBlog: failed to parse XML, error=${e.message}")
            logWarn("KotlinBlog raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0

        // RSS2 формат: элементы в теге <item>
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i)
            val children = item.childNodes

            totalItems++

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
                    "description", "content:encoded" -> { /* description ignored */ }
                    "category" -> cleanText(node.textContent)?.let { categories += it }
                }
            }

            if (pubDateStr == null) continue

            val published: Instant = try {
                ZonedDateTime.parse(pubDateStr.trim(), rfc1123Formatter).toInstant()
            } catch (e: Exception) {
                logWarn("KotlinBlog: cannot parse pubDate '$pubDateStr': ${e.message}")
                continue
            }

            parsedItems++

            if (published <= lastCheck) {
                continue
            }

            afterFilterItems++

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                categories = categories
            )
        }

        logInfo(
            "KotlinBlog: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}

object KotlinDiscussionsProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = KOTLIN_DISCUSSIONS_URL

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(Redirect.NORMAL)
        .build()

    private val rfc1123Formatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

        logInfo("KotlinDiscussions: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("KotlinDiscussions: HTTP status for $FEED_URL = ${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                logWarn(
                    "KotlinDiscussions: non-2xx status for $FEED_URL, " +
                            "body snippet=${resp.body().take(300)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            logWarn("KotlinDiscussions: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("KotlinDiscussions: failed to parse XML, error=${e.message}")
            logWarn("KotlinDiscussions raw snippet: ${xml.take(300)}")
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

            result += SimpleNewsItem(
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
                        logWarn("KotlinDiscussions: cannot parse pubDate '$it': ${e.message}")
                        null
                    }
                }
                addItem(title, linkHref, published, author, categories)
            }
        }

        logInfo(
            "KotlinDiscussions: items total=$totalItems, parsed=$parsedItems, " +
                    "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }
}

object MediumAndroidProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = MEDIUM_ANDROID_DEVELOPERS_URL

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val client = HttpClient.newHttpClient()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }

        val result = mutableListOf<SimpleNewsItem>()

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
            var author: String? = null
            var description: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "pubdate" -> pubDateStr = node.textContent
                    "updated" -> updatedStr = node.textContent
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                    "dc:creator", "author" -> author = node.textContent
                    "description", "content:encoded" -> description = node.textContent
                    "category" -> cleanText(node.textContent)?.let { categories += it }
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

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                summary = cleanAndTruncate(description),
                categories = categories
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

object MediumGoogleProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = MEDIUM_GOOGLE_DEVELOPER_EXPERTS_URL

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val client = HttpClient.newHttpClient()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }

        val result = mutableListOf<SimpleNewsItem>()

        System.err.println("==== Medium Google: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
            System.err.println(
                "Medium Google HTTP status=${resp.statusCode()}"
            )
            if (resp.statusCode() !in 200..299) {
                System.err.println(
                    "Medium Google: non-2xx, body snippet=${resp.body().take(200)}"
                )
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            System.err.println(
                "Medium Google: failed to fetch $FEED_URL: ${e.message}"
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
                "Medium Google: failed to parse XML: ${e.message}"
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
            var author: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "pubdate" -> pubDateStr = node.textContent
                    "updated" -> updatedStr = node.textContent
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                    "dc:creator", "author" -> author = node.textContent
                    "description", "content:encoded" -> { /* description ignored */ }
                    "category" -> cleanText(node.textContent)?.let { categories += it }
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

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                categories = categories
            )
        }

        System.err.println(
            "Medium Google items: total=$total, parsed=$parsed, afterFilter=$afterFilter, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }

    private fun parseDate(raw: String): Instant? {
        val iso = Timestamp.parseIso(raw)
        if (iso != null) return iso

        return try {
            ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (e: Exception) {
            System.err.println("Medium Google: cannot parse date '$raw': ${e.message}")
            null
        }
    }
}

object NineToFiveGoogleProvider : NewsProvider<SimpleNewsItem> {

    private const val FEED_URL = NINE_TO_FIVE_GOOGLE_URL

    private val client: HttpClient = HttpClient.newHttpClient()
    private val rfc1123Formatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

        logInfo("9to5Google: fetching $FEED_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            logInfo("9to5Google: HTTP status=${response.statusCode()}")
            if (response.statusCode() !in 200..299) {
                logWarn("9to5Google: non-2xx status, body snippet=${response.body().take(300)}")
                return emptyList()
            }
            response.body()
        } catch (e: Exception) {
            logWarn("9to5Google: failed to fetch $FEED_URL: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            logWarn("9to5Google: failed to parse XML: ${e.message}")
            logWarn("9to5Google raw snippet: ${xml.take(300)}")
            return emptyList()
        }

        var totalItems = 0
        var parsedItems = 0
        var afterFilterItems = 0

        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            totalItems++
            val item = items.item(i)
            val children = item.childNodes

            var title: String? = null
            var linkHref: String? = null
            var pubDateStr: String? = null
            var author: String? = null
            var description: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                    "pubdate" -> pubDateStr = node.textContent
                    "dc:creator", "author" -> author = node.textContent
                    "description", "content:encoded" -> description = node.textContent
                    "category" -> cleanText(node.textContent)?.let { categories += it }
                }
            }

            val published = parseDate(pubDateStr) ?: continue
            parsedItems++

            if (published <= lastCheck) continue

            afterFilterItems++

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                summary = cleanAndTruncate(description),
                categories = categories
            )
        }

        logInfo(
            "9to5Google: items total=$totalItems, parsed=$parsedItems, " +
                "afterFilter=$afterFilterItems, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }

    private fun parseDate(raw: String?): Instant? {
        raw ?: return null
        return try {
            ZonedDateTime.parse(raw.trim(), rfc1123Formatter).toInstant()
        } catch (e: Exception) {
            logWarn("9to5Google: cannot parse pubDate '$raw': ${e.message}")
            null
        }
    }
}

object ProAndroidDevProvider: NewsProvider<SimpleNewsItem> {

    private const val FEED_URL: String = PRO_ANDROID_DEV_URL

    // НЕБЕЗОПАСНО: доверяем всем сертификатам, только для этого провайдера
    private val insecureClient: HttpClient by lazy {
        val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) = Unit

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) = Unit

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }

        HttpClient.newBuilder()
            .sslContext(sslContext)
            .build()
    }

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }

        System.err.println("==== ProAndroidDev: fetching $FEED_URL (insecure trust-all SSL)")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = insecureClient.send(request, HttpResponse.BodyHandlers.ofString())
            System.err.println("ProAndroidDev HTTP status=${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                System.err.println("ProAndroidDev: non-2xx, body snippet=${resp.body().take(200)}")
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            System.err.println("ProAndroidDev: failed to fetch: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            System.err.println("ProAndroidDev: failed to parse XML: ${e.message}")
            return emptyList()
        }

        val nodes = doc.getElementsByTagName("item")
        val result = mutableListOf<SimpleNewsItem>()

        var total = 0
        var parsed = 0
        var afterFilter = 0

        for (i in 0 until nodes.length) {
            total += 1
            val item = nodes.item(i)
            val children = item.childNodes

            var publishedStr: String? = null
            var title: String? = null
            var linkHref: String? = null
            var author: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "pubdate" -> publishedStr = node.textContent
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                    "dc:creator", "author" -> author = node.textContent
                    "description", "content:encoded" -> { /* description ignored */ }
                    "category" -> cleanText(node.textContent)?.let { categories += it }
                }
            }

            if (publishedStr == null) continue
            val published = parsePublished(publishedStr) ?: continue
            if (published <= lastCheck) continue

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            parsed += 1
            afterFilter += 1

            result += SimpleNewsItem(
                published = published,
                title = safeTitle,
                url = url,
                author = cleanText(author),
                categories = categories
            )
        }

        System.err.println(
            "ProAndroidDev items collected: total=$total, parsed=$parsed, " +
                    "afterFilter=$afterFilter, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }

    private fun parsePublished(raw: String): Instant? {
        val iso = Timestamp.parseIso(raw)
        if (iso != null) return iso

        return try {
            ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (e: Exception) {
            System.err.println("ProAndroidDev: cannot parse date '$raw': ${e.message}")
            null
        }
    }
}

object TechRadarProvider: NewsProvider<SimpleNewsItem> {

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

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
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
        val result = mutableListOf<SimpleNewsItem>()
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

            result += SimpleNewsItem(
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

object YoutubeProvider: NewsProvider<SimpleNewsItem> {

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        val feeds = YOUTUBE_CHANNELS
        if (feeds.isEmpty()) return emptyList()

        val client = HttpClient.newHttpClient()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val result = mutableListOf<SimpleNewsItem>()

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
            var author: String? = null

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName) {
                    "published" -> publishedStr = node.textContent
                    "title" -> title = node.textContent
                    "yt:videoId" -> videoId = node.textContent
                    "author" -> {
                        val nameNode = node.childNodes
                            .let { nodes ->
                                (0 until nodes.length)
                                    .map(nodes::item)
                                    .firstOrNull { it.nodeName == "name" }
                            }
                        author = nameNode?.textContent ?: node.textContent
                    }
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

                result += SimpleNewsItem(
                    published = published,
                    title = safeTitle,
                    url = url,
                    author = cleanText(author)
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
