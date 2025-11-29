package news.proandroiddev

import news.PRO_ANDROID_DEV_URL
import news.Timestamp
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.DocumentBuilderFactory

data class ProAndroidDevItem(
    val published: Instant,
    val title: String,
    val url: String
)

object ProAndroidDevProvider {

    private const val FEED_URL: String = PRO_ANDROID_DEV_URL

    // НЕБЕЗОПАСНО: доверяем всем сертификатам, только для этого провайдера
    private val insecureClient: HttpClient by lazy {
        val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) = Unit

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) = Unit

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }

        HttpClient.newBuilder()
            .sslContext(sslContext)
            .build()
    }

    fun fetchItems(lastCheck: Instant): List<ProAndroidDevItem> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }

        System.err.println("==== ProAndroidDev: fetching $FEED_URL (insecure trust-all SSL)")

        val request = HttpRequest.newBuilder()
            .uri(URI(FEED_URL))
            .GET()
            .build()

        val xml = try {
            val resp = insecureClient.send(request, HttpResponse.BodyHandlers.ofString())
            System.err.println("ProAndroidDev HTTP status=${resp.statusCode()}")
            if (resp.statusCode() !in 200..299) {
                System.err.println("ProAndroidDev: non-2xx, body snippet=${resp.body().take(200)}")
                return emptyList()
            }
            resp.body()
        } catch (e: Exception) {
            System.err.println("ProAndroidDev: failed to fetch: ${e.message}")
            return emptyList()
        }

        val doc = try {
            val builder = factory.newDocumentBuilder()
            builder
                .parse(xml.byteInputStream())
                .apply { documentElement.normalize() }
        } catch (e: Exception) {
            System.err.println("ProAndroidDev: failed to parse XML: ${e.message}")
            return emptyList()
        }

        val nodes = doc.getElementsByTagName("item")
        val result = mutableListOf<ProAndroidDevItem>()

        var total = 0
        var parsed = 0
        var afterFilter = 0

        for (i in 0 until nodes.length) {
            total += 1
            val item = nodes.item(i)
            val children = item.childNodes

            var publishedStr: String? = null
            var title: String? = null
            var linkHref: String? = null

            for (j in 0 until children.length) {
                val node = children.item(j)
                when (node.nodeName.lowercase()) {
                    "pubdate" -> publishedStr = node.textContent
                    "title" -> title = node.textContent
                    "link" -> linkHref = node.textContent
                }
            }

            if (publishedStr == null) continue
            val published = parsePublished(publishedStr) ?: continue
            if (published <= lastCheck) continue

            val safeTitle = title
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "(no title)"

            val url = linkHref
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: continue

            parsed += 1
            afterFilter += 1

            result += ProAndroidDevItem(
                published = published,
                title = safeTitle,
                url = url
            )
        }

        System.err.println(
            "ProAndroidDev items collected: total=$total, parsed=$parsed, " +
                    "afterFilter=$afterFilter, result=${result.size}"
        )

        return result.sortedBy { it.published }
    }

    private fun parsePublished(raw: String): Instant? {
        val iso = Timestamp.parseIso(raw)
        if (iso != null) return iso

        return try {
            ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (e: Exception) {
            System.err.println("ProAndroidDev: cannot parse date '$raw': ${e.message}")
            null
        }
    }
}