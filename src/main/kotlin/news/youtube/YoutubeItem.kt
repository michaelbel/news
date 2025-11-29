package news.youtube

import news.NewsItem
import java.time.Instant

data class YoutubeItem(
    override val published: Instant,
    override val title: String,
    override val url: String
): NewsItem