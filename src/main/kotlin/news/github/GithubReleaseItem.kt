package news.github

import news.NewsItem
import java.time.Instant

data class GithubReleaseItem(
    override val published: Instant,
    val repo: String,
    override val title: String,
    override val url: String
): NewsItem