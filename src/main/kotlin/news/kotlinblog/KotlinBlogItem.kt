package news.kotlinblog

import news.NewsItem
import java.time.Instant

data class KotlinBlogItem(
    override val published: Instant,
    override val title: String,
    override val url: String
): NewsItem