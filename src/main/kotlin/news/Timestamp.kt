package news

import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

object Timestamp {

    private const val ONE_DAY_SECONDS: Long = 24L * 60L * 60L
    private const val GITHUB_TRENDING_LAST_DATE_ENV = "GITHUB_TRENDING_LAST_DATE"
    private const val GITHUB_TRENDING_TIMESTAMP_FILE_ENV = "GITHUB_TRENDING_TIMESTAMP_FILE"

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
            logWarn("LAST_CHECK is empty, fallback to $fallback")
            return fallback
        }

        val parsed = parseIso(fromEnv)
        if (parsed == null) {
            val fallback = Instant.now().minusSeconds(ONE_DAY_SECONDS)
            logWarn("Cannot parse LAST_CHECK=$fromEnv, fallback to $fallback")
            return fallback
        }

        return parsed
    }

    fun readGithubTrendingLastDate(): LocalDate? {
        val fromEnv = System.getenv(GITHUB_TRENDING_LAST_DATE_ENV)?.trim().orEmpty()
        if (fromEnv.isEmpty()) {
            logInfo("$GITHUB_TRENDING_LAST_DATE_ENV is empty")
            return null
        }

        return try {
            LocalDate.parse(fromEnv)
        } catch (_: Exception) {
            logWarn("Cannot parse $GITHUB_TRENDING_LAST_DATE_ENV=$fromEnv")
            null
        }
    }

    fun shouldSendGithubTrending(
        lastSentDate: LocalDate?,
        today: LocalDate = LocalDate.now(ZoneOffset.UTC)
    ): Boolean {
        val isScheduledDay = when (today.dayOfWeek) {
            DayOfWeek.MONDAY,
            DayOfWeek.THURSDAY -> true
            else -> false
        }

        if (!isScheduledDay) {
            logInfo("GitHub Trending is not scheduled for ${today.dayOfWeek} ($today)")
            return false
        }

        if (lastSentDate == today) {
            logInfo("GitHub Trending was already sent on $today")
            return false
        }

        logInfo("GitHub Trending is scheduled for ${today.dayOfWeek} ($today)")
        return true
    }

    fun writeGithubTrendingLastDate(
        date: LocalDate = LocalDate.now(ZoneOffset.UTC)
    ) {
        val filePath = System.getenv(GITHUB_TRENDING_TIMESTAMP_FILE_ENV)?.trim().orEmpty()
        if (filePath.isEmpty()) {
            logWarn("$GITHUB_TRENDING_TIMESTAMP_FILE_ENV is empty, skip state update")
            return
        }

        Files.writeString(
            Path.of(filePath),
            "$date\n"
        )
        logInfo("GitHub Trending last sent date updated to $date in $filePath")
    }
}
