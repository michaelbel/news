package news

import java.time.Instant

interface NewsItem {
    val published: Instant
    val title: String
    val url: String
    val author: String?
    val summary: String?
    val categories: List<String>
}

data class SimpleNewsItem(
    override val published: Instant,
    override val title: String,
    override val url: String,
    override val author: String? = null,
    override val summary: String? = null,
    override val categories: List<String> = emptyList()
): NewsItem
