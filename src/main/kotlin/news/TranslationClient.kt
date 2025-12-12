package news

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Translation client powered by Hugging Face Inference API using the
 * `Helsinki-NLP/opus-mt-en-ru` model. This keeps infrastructure light while
 * delivering reasonable EN→RU quality on a free tier.
 *
 * Environment variables:
 * - HUGGING_FACE_TOKEN — required. Hugging Face access token with "Read" scope.
 *   Without it, translation is skipped with a one-time warning.
 *
 * The client POSTs JSON `{ "inputs": "<text>" }` to the model endpoint and
 * expects a JSON array with `translation_text` fields in the response.
 */
object TranslationClient {

    private const val endpoint =
        "https://api-inference.huggingface.co/models/Helsinki-NLP/opus-mt-en-ru"

    private const val targetLang = "ru"

    private val apiToken: String? = System.getenv("HUGGING_FACE_TOKEN")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val cache = ConcurrentHashMap<String, String>()

    private val warnedMissingToken = AtomicBoolean(false)

    fun translateToTarget(text: String, sourceLang: String? = null): String? {
        val token = apiToken
        if (token == null) {
            if (warnedMissingToken.compareAndSet(false, true)) {
                logWarn(
                    "Translate: HUGGING_FACE_TOKEN is not set, skipping translations"
                )
            }
            return null
        }

        cache[text]?.let { return it }

        val payload = """{\n  \"inputs\": \"${escapeJson(text)}\"\n}"""

        val request = HttpRequest.newBuilder()
            .uri(URI(endpoint))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            logWarn("Translate: failed to call Hugging Face: ${e.message}")
            return null
        }

        if (response.statusCode() !in 200..299) {
            logWarn(
                "Translate: non-2xx status ${response.statusCode()}, body=${response.body().take(200)}"
            )
            return null
        }

        val translated = parseTranslatedText(response.body())
        if (translated == null) {
            logWarn("Translate: cannot parse response body: ${response.body().take(200)}")
            return null
        }

        cache[text] = translated
        logInfo(
            "Translate: '${text.take(40)}' -> '${translated.take(40)}', target=$targetLang, model=Helsinki-NLP/opus-mt-en-ru"
        )
        return translated
    }

    private fun escapeJson(text: String): String {
        return buildString(text.length + 10) {
            text.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }

    private fun parseTranslatedText(body: String): String? {
        val regex = Regex("\"translation_text\"\\s*:\\s*\"(.*?)\"", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(body) ?: return null
        return match.groupValues.getOrNull(1)
            ?.replace("\\n", "\n")
            ?.replace("\\r", "\r")
            ?.replace("\\\"", "\"")
            ?.replace("\\/", "/")
    }
}