package news.devto

import news.NewsItem
import java.time.Instant

data class DevToItem(
    override val published: Instant,
    override val title: String,
    override val url: String,
    override val author: String? = null,
    override val summary: String? = null,
    override val categories: List<String> = emptyList()
) : NewsItem
