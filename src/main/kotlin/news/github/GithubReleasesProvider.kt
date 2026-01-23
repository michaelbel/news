package news.github

import news.GITHUB_REPOS
import news.NewsProvider
import news.Timestamp
import news.cleanAndTruncate
import news.cleanText
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

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
