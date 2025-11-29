package news.androidweekly

import news.NewsItem
import java.time.Instant

data class AndroidWeeklyItem(
    override val published: Instant,
    override val title: String,
    override val url: String
): NewsItem