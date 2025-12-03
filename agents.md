# Automation guidance

## Project overview
- Kotlin/JVM application built with Gradle. Kotlin 2.0.21 targets JVM 21 via the Gradle wrapper.
- Entry point: `news.NewsBotMainKt` (configured in `application` plugin).
- Purpose: aggregate news from multiple providers (RSS/Atom feeds, GitHub releases/trending, YouTube channels) and build formatted messages.

## Build and run
- Prefer the bundled wrapper: `./gradlew build` for compilation and checks.
- Run the bot locally with `./gradlew run` (network access required to reach feeds/APIs).
- Keep Gradle files free of trailing partial lines; ensure edits add a terminal newline.

## Repository layout
- Core contracts: `NewsItem` and `NewsProvider` interfaces.
- Feature toggles: `NewsFeatures.kt` contains `*_ENABLED` flags to switch providers on/off.
- Source URLs and lists: `NewsSources.kt` defines feed URLs, GitHub repos, and YouTube channels.
- Each provider lives in its own package (e.g., `news.androidblog`, `news.github`) with a pair of files: `*Item.kt` (data) and `*Provider.kt` (fetch/parse logic).
- Logging helpers in `Logger.kt` emit GitHub Actions fold groups and color-coded stderr lines.

## Adding or adjusting providers
- Create a new package under `src/main/kotlin/news/` with `YourSourceItem` implementing `NewsItem` and `YourSourceProvider` implementing `NewsProvider`.
- Update `NewsFeatures.kt` with a corresponding `*_ENABLED` flag and default value.
- Add URLs or collections to `NewsSources.kt` if the provider needs centralized configuration.
- Wire the provider into `NewsBotMain.collectItems` invocation alongside existing sources, preserving section logging.

## Formatting and style
- Favor data classes for immutable items and keep parsing/HTTP logic inside provider classes.
- Avoid try/catch around imports; handle network/parse failures inside provider code paths.
- Keep log messages concise and in English or Russian as currently used, and wrap groups with `logSection`/`endSection` for CI readability.

## Testing expectations
- There are no dedicated test suites; running `./gradlew build` is the main check. Execute it before committing when practical.
