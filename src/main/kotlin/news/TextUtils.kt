package news

fun cleanText(value: String?): String? {
    return value
        ?.replace("\\s+".toRegex(), " ")
        ?.trim()
        ?.takeUnless { it.isEmpty() }
}

fun cleanAndTruncate(value: String?, maxLength: Int = 280): String? {
    val cleaned = cleanText(value) ?: return null
    if (cleaned.length <= maxLength) return cleaned
    return cleaned.take(maxLength - 3) + "..."
}