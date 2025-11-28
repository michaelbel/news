package news

private const val COLOR_CYAN = "\u001B[36m"
private const val COLOR_YELLOW = "\u001B[33m"
private const val COLOR_RED = "\u001B[31m"
private const val COLOR_RESET = "\u001B[0m"

/**
 * Обычный заметный лог в CI.
 */
fun logInfo(message: String) {
    System.err.println("$COLOR_CYAN[NEWS] $message$COLOR_RESET")
}

/**
 * Предупреждение.
 */
fun logWarn(message: String) {
    System.err.println("$COLOR_YELLOW[NEWS][WARN] $message$COLOR_RESET")
}

/**
 * Ошибочный лог (но без падения, просто ярче).
 */
fun logError(message: String) {
    System.err.println("$COLOR_RED[NEWS][ERROR] $message$COLOR_RESET")
}

/**
 * Начало лог-группы в GitHub Actions.
 * Будет сворачиваемый блок с заголовком.
 */
fun logSection(title: String) {
    // Команда GitHub Actions должна быть без пробелов в начале строки.
    println("::group::[NEWS] $title")
}

/**
 * Конец лог-группы в GitHub Actions.
 */
fun endSection() {
    println("::endgroup::")
}