package news.githubtrending

import news.NewsItem
import java.time.Instant

data class GithubTrendingKotlinItem(
    override val published: Instant,
    override val title: String,
    override val url: String,
    val description: String?,
    val stars: Int,
    val forks: Int
): NewsItem