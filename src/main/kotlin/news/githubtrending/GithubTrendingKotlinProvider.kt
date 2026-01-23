package news.githubtrending

import news.GITHUB_TRENDING_KOTLIN_URL
import news.NewsProvider
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

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
