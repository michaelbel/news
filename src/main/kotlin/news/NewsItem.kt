package news

import java.time.Instant

interface NewsItem {
    val published: Instant
    val title: String
    val url: String
    val summary: String?
    val categories: List<String>
    val imageUrl: String? get() = null
}

data class SimpleNewsItem(
    override val published: Instant,
    override val title: String,
    override val url: String,
    override val summary: String? = null,
    override val categories: List<String> = emptyList(),
    override val imageUrl: String? = null
): NewsItem

data class GithubReleaseItem(
    override val published: Instant,
    val repo: String,
    override val title: String,
    override val url: String,
    override val summary: String? = null,
    override val categories: List<String> = emptyList()
): NewsItem

data class GithubTrendingItem(
    override val published: Instant,
    override val title: String,
    override val url: String,
    val description: String?,
    val stars: Int,
    val forks: Int,
    override val summary: String? = null,
    override val categories: List<String> = emptyList()
): NewsItem
