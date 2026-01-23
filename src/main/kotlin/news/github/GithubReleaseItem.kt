package news.github

import news.NewsItem
import java.time.Instant

data class GithubReleaseItem(
    override val published: Instant,
    val repo: String,
    override val title: String,
    override val url: String,
    override val author: String? = null,
    override val summary: String? = null,
    override val categories: List<String> = emptyList()
): NewsItem
