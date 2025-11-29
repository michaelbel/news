package news.proandroiddev

import news.NewsItem
import java.time.Instant

data class ProAndroidDevItem(
    override val published: Instant,
    override val title: String,
    override val url: String
): NewsItem