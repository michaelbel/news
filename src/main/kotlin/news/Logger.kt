package news

private const val COLOR_CYAN = "\u001B[36m"
private const val COLOR_YELLOW = "\u001B[33m"
private const val COLOR_RED = "\u001B[31m"
private const val COLOR_RESET = "\u001B[0m"

fun logInfo(message: String) {
    System.err.println("$COLOR_CYAN[NEWS] $message$COLOR_RESET")
}

fun logWarn(message: String) {
    System.err.println("$COLOR_YELLOW[NEWS][WARN] $message$COLOR_RESET")
}

fun logError(message: String) {
    System.err.println("$COLOR_RED[NEWS][ERROR] $message$COLOR_RESET")
}

fun logSection(title: String) {
    // Команда GitHub Actions должна быть без пробелов в начале строки.
    println("::group::[NEWS] $title")
}

fun endSection() {
    println("::endgroup::")
}
