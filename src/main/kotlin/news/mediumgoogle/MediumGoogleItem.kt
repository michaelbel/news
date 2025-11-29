package news.mediumgoogle

import news.NewsItem
import java.time.Instant

data class MediumGoogleItem(
    override val published: Instant,
    override val title: String,
    override val url: String
): NewsItem