package news.firebaseblog

import news.NewsItem
import java.time.Instant

data class FirebaseBlogItem(
    override val published: Instant,
    override val title: String,
    override val url: String
) : NewsItem
