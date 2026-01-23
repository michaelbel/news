package news

import java.time.Instant

interface NewsProvider<T: NewsItem> {
    fun fetchItems(lastCheck: Instant): List<T>
}
