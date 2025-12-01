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