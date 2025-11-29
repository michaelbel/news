package news.mediumandroid

import news.NewsItem
import java.time.Instant

data class MediumAndroidItem(
    override val published: Instant,
    override val title: String,
    override val url: String
): NewsItem