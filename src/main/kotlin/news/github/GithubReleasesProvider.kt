package news.github

import news.Timestamp
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

data class GithubReleaseItem(
    val published: Instant,
    val repo: String,
    val title: String,
    val url: String
)

private data class GithubRepo(
    val owner: String,
    val name: String,
    val label: String
)

private val repos = listOf(
    GithubRepo(
        owner = "square",
        name = "retrofit",
        label = "square/retrofit"
    ),
    GithubRepo(
        owner = "material-components",
        name = "material-components-android",
        label = "material-components/material-components-android"
    )
)

object GithubReleasesProvider {

    fun fetchItems(lastCheck: Instant): List<GithubReleaseItem> {
        if (repos.isEmpty()) return emptyList()

        val client = HttpClient.newHttpClient()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }

        val result = mutableListOf<GithubReleaseItem>()

        for (repo in repos) {
            val feedUrl = "https://github.com/${repo.owner}/${repo.name}/releases.atom"
            System.err.println("==== GitHub releases: fetching $feedUrl for ${repo.label}")

            val request = HttpRequest.newBuilder()
                .uri(URI(feedUrl))
                .GET()
                .build()

            val xml = try {
                val resp = client.send(request, HttpResponse.BodyHandlers.ofString())
                System.err.println("GitHub releases HTTP status=${resp.statusCode()} for ${repo.label}")
                if (resp.statusCode() !in 200..299) {
                    System.err.println("GitHub releases: non-2xx for ${repo.label}, body snippet=${resp.body().take(200)}")
                    continue
                }
                resp.body()
            } catch (e: Exception) {
                System.err.println("GitHub releases: failed to fetch $feedUrl for ${repo.label}: ${e.message}")
                continue
            }

            val doc = try {
                val builder = factory.newDocumentBuilder()
                builder
                    .parse(xml.byteInputStream())
                    .apply { documentElement.normalize() }
            } catch (e: Exception) {
                System.err.println("GitHub releases: failed to parse XML for ${repo.label}: ${e.message}")
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
                    }
                }

                val dateRaw = publishedStr ?: updatedStr ?: continue
                val published = parseDate(dateRaw) ?: continue
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

                result += GithubReleaseItem(
                    published = published,
                    repo = repo.label,
                    title = safeTitle,
                    url = url
                )
            }

            System.err.println(
                "GitHub releases for ${repo.label}: total=$total, parsed=$parsed, " +
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