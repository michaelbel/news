package news

import java.time.Instant
import java.time.OffsetDateTime

object Timestamp {

    private const val ONE_DAY_SECONDS: Long = 24L * 60L * 60L

    fun parseIso(value: String): Instant? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null

        return try {
            Instant.parse(trimmed)
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(trimmed).toInstant()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun readLastCheck(): Instant {
        val fromEnv = System.getenv("LAST_CHECK")?.trim().orEmpty()
        if (fromEnv.isEmpty()) {
            val fallback = Instant.now().minusSeconds(ONE_DAY_SECONDS)
            System.err.println("LAST_CHECK is empty, fallback to $fallback")
            return fallback
        }

        val parsed = parseIso(fromEnv)
        if (parsed == null) {
            val fallback = Instant.now().minusSeconds(ONE_DAY_SECONDS)
            System.err.println("Cannot parse LAST_CHECK=$fromEnv, fallback to $fallback")
            return fallback
        }

        return parsed
    }
}