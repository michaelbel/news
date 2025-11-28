package news

object NewsSources {

    object Youtube {

        data class Feed(
            val url: String,
            val label: String
        )

        val feeds: List<Feed> = listOf(
            Feed(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCVHFbqXqoYvEWM1Ddxl0QDg",
                label = "AndroidDevelopers"
            ),
            Feed(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCKNTZMRHPLXfqlbdOI7mCkg",
                label = "PhilippLackner"
            ),
            Feed(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCKsqMPIIhev3qbMxCL8Emvw",
                label = "AndroidBroadcast"
            ),
            Feed(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCQFhFs4Ff1BP__DWMD5gC5g",
                label = "gulyaev_it"
            ),
            Feed(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UC71omjio31Esx7LytaZ2ytA",
                label = "JovMit"
            ),
            Feed(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCofyDdGnCssPNwABNkxLFKg",
                label = "randrushchenko"
            ),
            Feed(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCP7uiEZIqci43m22KDl0sNw",
                label = "Kotlin"
            ),
            Feed(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCIjEgHA1vatSR2K4rfcdNRg",
                label = "AristiDevs"
            ),
            Feed(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCSmp6mEF9bml3sinNuKcZZg",
                label = "offer_factory"
            ),
            Feed(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCy9dX2OpiYIDRF-wduZSXPQ",
                label = "ievetrov"
            ),
            Feed(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCYLAirIEMMXtWOECuZAtjqQ",
                label = "StevdzaSan"
            ),
            Feed(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCFaLqiT8yyi4UYeA7NMPRIA",
                label = "typealias"
            ),
            Feed(
                url = "https://www.youtube.com/feeds/videos.xml?channel_id=UCTjQSpx2waqXTC37AgM8qyA",
                label = "NativeMobileBits"
            )
        )
    }

    object AndroidBlog {
        const val FEED_URL: String = "http://android-developers.googleblog.com/atom.xml"
    }

    object KotlinBlog {
        const val FEED_URL: String = "https://blog.jetbrains.com/kotlin/feed/"
    }

    object AndroidWeekly {
        const val FEED_URL: String = "https://androidweekly.net/rss"
    }

    object ProAndroidDev {
        const val FEED_URL: String = "https://proandroiddev.com/feed"
    }

    object Github {
        val repos: List<String> = listOf(
            "https://github.com/square/retrofit",
            "https://github.com/material-components/material-components-android",
            "https://github.com/InsertKoinIO/koin",
            "https://github.com/coil-kt/coil",
            "https://github.com/Kotlin/kotlinx.coroutines",
            "https://github.com/google/dagger",
            "https://github.com/ReactiveX/RxJava",
            "https://github.com/airbnb/lottie-android",
            "https://github.com/google/gson",
            "https://github.com/androidbroadcast/ViewBindingPropertyDelegate",
            "https://github.com/facebook/facebook-android-sdk",
            "https://github.com/JakeWharton/timber",
            "https://github.com/square/leakcanary",
            "https://github.com/square/okhttp",
            "https://github.com/Kotlin/kotlinx.serialization",
            "https://github.com/ktorio/ktor",
            "https://github.com/JetBrains/compose-multiplatform",
            "https://github.com/ReactiveX/RxAndroid",
            "https://github.com/ReactiveX/RxKotlin"
        )
    }
}