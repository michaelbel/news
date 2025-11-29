package news.habr

import news.NewsItem
import java.time.Instant

data class HabrAndroidItem(
    override val published: Instant,
    override val title: String,
    override val url: String
): NewsItem