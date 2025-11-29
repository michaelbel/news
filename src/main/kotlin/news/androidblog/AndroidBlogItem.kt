package news.androidblog

import news.NewsItem
import java.time.Instant

data class AndroidBlogItem(
    override val published: Instant,
    override val title: String,
    override val url: String
): NewsItem