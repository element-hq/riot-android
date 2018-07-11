package im.vector.analytics

enum class Category(val value: String) {
    METRICS("Metrics")
}

enum class Action(val value: String) {
    STARTUP("android.startup"),
    STATS("android.stats")
}

sealed class Event(val category: Category, val action: Action, val title: String? = null, val value: Float? = null) {
    data class InitialSync(val duration: Long) : Event(Category.METRICS, Action.STARTUP, "initialSync", duration.toFloat())
    data class IncrementalSync(val duration: Long) : Event(Category.METRICS, Action.STARTUP, "incrementalSync", duration.toFloat())
    data class StorePreload(val duration: Long) : Event(Category.METRICS, Action.STARTUP, "storePreload", duration.toFloat())
    data class LaunchScreen(val duration: Long) : Event(Category.METRICS, Action.STARTUP, "launchScreen", duration.toFloat())
    data class Rooms(val nbOfRooms: Int) : Event(Category.METRICS, Action.STATS, "rooms", nbOfRooms.toFloat())
}