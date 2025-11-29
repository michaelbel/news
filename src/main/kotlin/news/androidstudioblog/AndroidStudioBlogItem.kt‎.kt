package news.androidstudioblog

import news.NewsItem
import java.time.Instant

data class AndroidStudioBlogItem(
    override val published: Instant,
    override val title: String,
    override val url: String
): NewsItem