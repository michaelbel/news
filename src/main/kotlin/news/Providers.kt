package news

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpClient.Redirect
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

private val defaultClient: HttpClient = HttpClient.newBuilder()
    .followRedirects(Redirect.NORMAL)
    .build()

private val rfc1123Formatter: DateTimeFormatter =
    DateTimeFormatter.RFC_1123_DATE_TIME

private data class RssItemFields(
    val title: String?,
    val linkHref: String?,
    val pubDateStr: String?,
    val description: String?,
    val content: String?,
    val categories: List<String>
)

private data class AtomEntryFields(
    val title: String?,
    val linkHref: String?,
    val publishedStr: String?,
    val updatedStr: String?,
    val summary: String?,
    val categories: List<String>
)

private fun fetchXml(
    logPrefix: String,
    feedUrl: String,
    client: HttpClient = defaultClient,
    headers: Map<String, String> = emptyMap()
): String? {
    logInfo("$logPrefix: fetching $feedUrl")

    val requestBuilder = HttpRequest.newBuilder()
        .uri(URI(feedUrl))
        .GET()

    headers.forEach { (name, value) -> requestBuilder.header(name, value) }

    val request = requestBuilder.build()

    val xml = try {
        val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
        logInfo("$logPrefix: HTTP status for $feedUrl = ${resp.statusCode()}")
        if (resp.statusCode() !in 200..299) {
            logWarn(
                "$logPrefix: non-2xx status for $feedUrl, " +
                        "body snippet=${resp.body().take(300)}"
            )
            return null
        }
        resp.body()
    } catch (e: Exception) {
        logWarn("$logPrefix: failed to fetch $feedUrl: ${e.message}")
        null
    }

    return xml
}

private fun parseXml(
    logPrefix: String,
    xml: String,
    namespaceAware: Boolean = true
): org.w3c.dom.Document? {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = namespaceAware
    }

    return try {
        val builder = factory.newDocumentBuilder()
        builder
            .parse(xml.byteInputStream())
            .apply { documentElement.normalize() }
    } catch (e: Exception) {
        logWarn("$logPrefix: failed to parse XML, error=${e.message}")
        logWarn("$logPrefix raw snippet: ${xml.take(300)}")
        null
    }
}

private fun parseRssItems(doc: org.w3c.dom.Document): List<RssItemFields> {
    val items = doc.getElementsByTagName("item")
    val result = ArrayList<RssItemFields>(items.length)

    for (i in 0 until items.length) {
        val item = items.item(i)
        val children = item.childNodes

        var title: String? = null
        var linkHref: String? = null
        var pubDateStr: String? = null
        var description: String? = null
        var content: String? = null
        val categories = mutableListOf<String>()

        for (j in 0 until children.length) {
            val node = children.item(j)
            when (node.nodeName.lowercase()) {
                "title" -> title = node.textContent
                "link" -> linkHref = node.textContent
                "pubdate" -> pubDateStr = node.textContent
                "description" -> description = node.textContent
                "content:encoded" -> content = node.textContent
                "category" -> cleanText(node.textContent)?.let { categories += it }
            }
        }

        result += RssItemFields(
            title = title,
            linkHref = linkHref,
            pubDateStr = pubDateStr,
            description = description,
            content = content,
            categories = categories
        )
    }

    return result
}

private fun parseAtomEntries(doc: org.w3c.dom.Document): List<AtomEntryFields> {
    val entries = doc.getElementsByTagName("entry")
    val result = ArrayList<AtomEntryFields>(entries.length)

    for (i in 0 until entries.length) {
        val entry = entries.item(i)
        val children = entry.childNodes

        var publishedStr: String? = null
        var updatedStr: String? = null
        var title: String? = null
        var linkHref: String? = null
        var summary: String? = null
        val categories = mutableListOf<String>()

        for (j in 0 until children.length) {
            val node = children.item(j)
            when (node.nodeName.lowercase()) {
                "published" -> publishedStr = node.textContent
                "updated" -> updatedStr = node.textContent
                "title" -> title = node.textContent
                "summary" -> summary = node.textContent
                "link" -> {
                    val rel = node.attributes?.getNamedItem("rel")?.nodeValue
                    val href = node.attributes?.getNamedItem("href")?.nodeValue
                    if (linkHref == null && (rel == null || rel == "alternate")) {
                        linkHref = href
                    }
                }
                "category" -> {
                    val term = node.attributes?.getNamedItem("term")?.nodeValue
                    val label = term ?: node.textContent
                    cleanText(label)?.let { categories += it }
                }
            }
        }

        result += AtomEntryFields(
            title = title,
            linkHref = linkHref,
            publishedStr = publishedStr,
            updatedStr = updatedStr,
            summary = summary,
            categories = categories
        )
    }

    return result
}

private fun parseRfc1123Date(logPrefix: String, raw: String): Instant? {
    return try {
        ZonedDateTime.parse(raw.trim(), rfc1123Formatter).toInstant()
    } catch (e: Exception) {
        logWarn("$logPrefix: cannot parse pubDate '$raw': ${e.message}")
        null
    }
}

private fun buildSimpleItemsFromRss(
    logPrefix: String,
    items: List<RssItemFields>,
    lastCheck: Instant,
    summaryProvider: (RssItemFields) -> String? = { null }
): List<SimpleNewsItem> {
    val result = mutableListOf<SimpleNewsItem>()
    var totalItems = 0
    var parsedItems = 0
    var afterFilterItems = 0

    for (item in items) {
        totalItems++

        val pubDateStr = item.pubDateStr ?: continue
        val published = parseRfc1123Date(logPrefix, pubDateStr) ?: continue

        parsedItems++
        if (published <= lastCheck) continue

        afterFilterItems++

        val safeTitle = item.title
            ?.trim()
            .takeUnless { it.isNullOrEmpty() }
            ?: "(no title)"

        val url = item.linkHref
            ?.trim()
            .takeUnless { it.isNullOrEmpty() }
            ?: continue

        result += SimpleNewsItem(
            published = published,
            title = safeTitle,
            url = url,
            summary = summaryProvider(item),
            categories = item.categories
        )
    }

    logInfo(
        "$logPrefix: items total=$totalItems, parsed=$parsedItems, " +
                "afterFilter=$afterFilterItems, result=${result.size}"
    )

    return result.sortedBy { it.published }
}

private fun buildSimpleItemsFromAtom(
    logPrefix: String,
    items: List<AtomEntryFields>,
    lastCheck: Instant,
    summaryProvider: (AtomEntryFields) -> String? = { null }
): List<SimpleNewsItem> {
    val result = mutableListOf<SimpleNewsItem>()
    var totalItems = 0
    var parsedItems = 0
    var afterFilterItems = 0

    for (item in items) {
        totalItems++

        val dateRaw = item.publishedStr ?: item.updatedStr ?: continue
        val published = Timestamp.parseIso(dateRaw.trim())
        if (published == null) {
            logWarn("$logPrefix: cannot parse published '$dateRaw'")
            continue
        }

        parsedItems++
        if (published <= lastCheck) continue

        afterFilterItems++

        val safeTitle = item.title
            ?.trim()
            .takeUnless { it.isNullOrEmpty() }
            ?: "(no title)"

        val url = item.linkHref
            ?.trim()
            .takeUnless { it.isNullOrEmpty() }
            ?: continue

        result += SimpleNewsItem(
            published = published,
            title = safeTitle,
            url = url,
            summary = summaryProvider(item),
            categories = item.categories
        )
    }

    logInfo(
        "$logPrefix: items total=$totalItems, parsed=$parsedItems, " +
                "afterFilter=$afterFilterItems, result=${result.size}"
    )

    return result.sortedBy { it.published }
}

private fun fetchSimpleRssFeed(
    logPrefix: String,
    feedUrl: String,
    lastCheck: Instant,
    summaryProvider: (RssItemFields) -> String? = { null }
): List<SimpleNewsItem> {
    val xml = fetchXml(logPrefix, feedUrl) ?: return emptyList()
    val doc = parseXml(logPrefix, xml) ?: return emptyList()
    val items = parseRssItems(doc)
    return buildSimpleItemsFromRss(logPrefix, items, lastCheck, summaryProvider)
}

private fun fetchSimpleAtomFeed(
    logPrefix: String,
    feedUrl: String,
    lastCheck: Instant,
    summaryProvider: (AtomEntryFields) -> String? = { null }
): List<SimpleNewsItem> {
    val xml = fetchXml(logPrefix, feedUrl) ?: return emptyList()
    val doc = parseXml(logPrefix, xml) ?: return emptyList()
    val entries = parseAtomEntries(doc)
    return buildSimpleItemsFromAtom(logPrefix, entries, lastCheck, summaryProvider)
}

private fun fetchSimpleAtomOrRssFeed(
    logPrefix: String,
    feedUrl: String,
    lastCheck: Instant,
    atomSummaryProvider: (AtomEntryFields) -> String? = { null },
    rssSummaryProvider: (RssItemFields) -> String? = { null }
): List<SimpleNewsItem> {
    val xml = fetchXml(logPrefix, feedUrl) ?: return emptyList()
    val doc = parseXml(logPrefix, xml) ?: return emptyList()
    val entries = parseAtomEntries(doc)
    return if (entries.isNotEmpty()) {
        buildSimpleItemsFromAtom(logPrefix, entries, lastCheck, atomSummaryProvider)
    } else {
        val items = parseRssItems(doc)
        buildSimpleItemsFromRss(logPrefix, items, lastCheck, rssSummaryProvider)
    }
}

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
            var description: String? = null
            val categories = mutableListOf<String>()

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                    "pubdate" -> pubDateStr = node.textContent
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
        return fetchSimpleAtomFeed(
            logPrefix = "AndroidBlog",
            feedUrl = ANDROID_DEVELOPERS_BLOG_URL,
            lastCheck = lastCheck,
            summaryProvider = { cleanAndTruncate(it.summary) }
        )
    }
}

object AndroidPoliceProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "AndroidPolice",
            feedUrl = ANDROID_POLICE_URL,
            lastCheck = lastCheck,
        )
    }
}

object AndroidStudioBlogProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleAtomFeed(
            logPrefix = "AndroidStudioBlog",
            feedUrl = ANDROID_STUDIO_BLOG_URL,
            lastCheck = lastCheck,
            summaryProvider = { cleanAndTruncate(it.summary) }
        )
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

    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
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

        val feedXml = xml
        val doc = parseXml("AndroidWeekly", feedXml) ?: return emptyList()
        val items = parseRssItems(doc)
        return buildSimpleItemsFromRss(
            logPrefix = "AndroidWeekly",
            items = items,
            lastCheck = lastCheck,
            summaryProvider = { cleanAndTruncate(it.description) }
        )
    }
}

object AndroidxReleaseNotesProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleAtomOrRssFeed(
            logPrefix = "AndroidXReleaseNotes",
            feedUrl = ANDROIDX_RELEASE_NOTES_URL,
            lastCheck = lastCheck
        )
    }
}

object DevToProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "DevTo",
            feedUrl = DEV_TO_ANDROID_URL,
            lastCheck = lastCheck,
        )
    }
}

object FirebaseBlogProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "FirebaseBlog",
            feedUrl = FIREBASE_BLOG_URL,
            lastCheck = lastCheck,
            summaryProvider = { cleanAndTruncate(it.description) }
        )
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
                var content: String? = null
                val categories = mutableListOf(label)

                for (j in 0 until children.length) {
                    val node = children.item(j)
                    when (node.nodeName.lowercase()) {
                        "published" -> publishedStr = node.textContent
                        "updated" -> updatedStr = node.textContent
                        "title" -> title = node.textContent
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
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "GithubBlog",
            feedUrl = GITHUB_BLOG_URL,
            lastCheck = lastCheck,
        )
    }
}

private val githubTrendingNameRegex = Regex(
    pattern = """<h2[^>]*>.*?<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

private val githubTrendingDescriptionRegex = Regex(
    pattern = """<p[^>]*>(.*?)</p>""",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

private val githubTrendingStarsRegex = Regex(
    pattern = """<a[^>]+href="[^"]+/stargazers"[^>]*>.*?([\d,.]+)\s*</a>""",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

// Поддерживаем и старую разметку с /network/members, и новую с /forks
private val githubTrendingForksRegex = Regex(
    pattern = """<a[^>]+href="[^"]+/(?:network/members|forks)[^"]*"[^>]*>.*?([\d,.]+)\s*</a>""",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

private fun fetchGithubTrendingItems(
    url: String,
    sourceLabel: String
): List<GithubTrendingItem> {
    val client = HttpClient.newHttpClient()

    val request = HttpRequest.newBuilder()
        .uri(URI(url))
        .GET()
        .build()

    val html = try {
        val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
        System.err.println("GitHub trending $sourceLabel HTTP status=${resp.statusCode()}")
        if (resp.statusCode() !in 200..299) {
            System.err.println(
                "GitHub trending $sourceLabel: non-2xx, body snippet=${resp.body().take(200)}"
            )
            return emptyList()
        }
        resp.body()
    } catch (e: Exception) {
        System.err.println("GitHub trending $sourceLabel: failed to fetch: ${e.message}")
        return emptyList()
    }

    val articles = html.split("<article", ignoreCase = true).drop(1)
    val result = mutableListOf<GithubTrendingItem>()

    for (article in articles) {
        val nameMatch = githubTrendingNameRegex.find(article) ?: continue
        val href = nameMatch.groupValues.getOrNull(1)?.trim().orEmpty()
        val repoName = nameMatch.groupValues.getOrNull(2)?.trim().orEmpty()
        if (href.isEmpty() || repoName.isEmpty()) continue

        val description = githubTrendingDescriptionRegex
            .find(article)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::cleanupGithubTrendingHtml)
            ?.ifBlank { null }

        val stars = githubTrendingStarsRegex
            .find(article)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::parseGithubTrendingNumber)
            ?: 0

        val forks = githubTrendingForksRegex
            .find(article)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::parseGithubTrendingNumber)
            ?: 0

        val repoUrl = if (href.startsWith("http")) {
            href
        } else {
            "https://github.com$href"
        }

        result += GithubTrendingItem(
            published = Instant.now(),
            title = cleanupGithubTrendingHtml(repoName),
            url = repoUrl,
            description = description,
            stars = stars,
            forks = forks
        )

        if (result.size >= 10) break
    }

    System.err.println("GitHub trending $sourceLabel items collected: ${result.size}")

    return result
}

private fun cleanupGithubTrendingHtml(raw: String): String {
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

private fun parseGithubTrendingNumber(raw: String): Int {
    val digitsOnly = raw.replace(Regex("[^0-9]"), "")
    return digitsOnly.toIntOrNull() ?: 0
}

object GithubTrendingKotlinProvider: NewsProvider<GithubTrendingItem> {
    override fun fetchItems(lastCheck: Instant): List<GithubTrendingItem> {
        return fetchGithubTrendingItems(
            url = GITHUB_TRENDING_KOTLIN_URL,
            sourceLabel = "Kotlin"
        )
    }
}

object GithubTrendingAllProvider: NewsProvider<GithubTrendingItem> {
    override fun fetchItems(lastCheck: Instant): List<GithubTrendingItem> {
        return fetchGithubTrendingItems(
            url = GITHUB_TRENDING_ALL_URL,
            sourceLabel = "All"
        )
    }
}

object GradleBlogProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleAtomOrRssFeed(
            logPrefix = "GradleBlog",
            feedUrl = GRADLE_BLOG_URL,
            lastCheck = lastCheck
        )
    }
}

object HabrAiProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "HabrAI",
            feedUrl = HABR_AI_URL,
            lastCheck = lastCheck,
        )
    }
}

object HabrAndroidProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "HabrAndroid",
            feedUrl = HABR_ANDROID_URL,
            lastCheck = lastCheck,
        )
    }
}

object HabrCareerProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "HabrCareer",
            feedUrl = HABR_CAREER_URL,
            lastCheck = lastCheck,
        )
    }
}

object HabrProgrammingProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "HabrProgramming",
            feedUrl = HABR_PROGRAMMING_URL,
            lastCheck = lastCheck,
        )
    }
}

object JetBrainsBlogProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "JetBrainsBlog",
            feedUrl = JETBRAINS_BLOG_URL,
            lastCheck = lastCheck,
        )
    }
}

object KotlinBlogProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "KotlinBlog",
            feedUrl = KOTLIN_BLOG_URL,
            lastCheck = lastCheck,
        )
    }
}

object KotlinDiscussionsProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleAtomOrRssFeed(
            logPrefix = "KotlinDiscussions",
            feedUrl = KOTLIN_DISCUSSIONS_URL,
            lastCheck = lastCheck
        )
    }
}

object MediumAndroidProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "MediumAndroid",
            feedUrl = MEDIUM_ANDROID_DEVELOPERS_URL,
            lastCheck = lastCheck,
        )
    }
}

object MediumGoogleProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "MediumGoogle",
            feedUrl = MEDIUM_GOOGLE_DEVELOPER_EXPERTS_URL,
            lastCheck = lastCheck,
        )
    }
}

object NineToFiveGoogleProvider : NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "9to5Google",
            feedUrl = NINE_TO_FIVE_GOOGLE_URL,
            lastCheck = lastCheck,
        )
    }
}

object ProAndroidDevProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "ProAndroidDev",
            feedUrl = PRO_ANDROID_DEV_URL,
            lastCheck = lastCheck,
        )
    }
}

object TechRadarProvider: NewsProvider<SimpleNewsItem> {
    override fun fetchItems(lastCheck: Instant): List<SimpleNewsItem> {
        return fetchSimpleRssFeed(
            logPrefix = "TechRadar",
            feedUrl = TECHRADAR_ANDROID_URL,
            lastCheck = lastCheck,
        )
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

                result += SimpleNewsItem(
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
