package im.vector.analytics

/**
 * A category to be linked to an {@link im.vector.analytics.Event}
 * @param value to log into your analytics console
 */
enum class Category(val value: String) {
    METRICS("Metrics")
}

/**
 * An action to be linked to an {@link im.vector.analytics.Event}
 * @param value to log into your analytics console
 */
enum class Action(val value: String) {
    STARTUP("android.startup"),
    STATS("android.stats")
}

/**
 * Represents all the analytics events, to be dispatched to {@link im.vector.analytics.Analytic#trackEvent()}
 * @param category the category associated with the event
 * @param action the action associated with the event
 * @param title the title associated with the event
 * @param value the optional value associated with the event
 */
sealed class Event(val category: Category, val action: Action, val title: String? = null, val value: Float? = null) {
    data class InitialSync(val duration: Long) : Event(Category.METRICS, Action.STARTUP, "initialSync", duration.toFloat())
    data class IncrementalSync(val duration: Long) : Event(Category.METRICS, Action.STARTUP, "incrementalSync", duration.toFloat())
    data class StorePreload(val duration: Long) : Event(Category.METRICS, Action.STARTUP, "storePreload", duration.toFloat())
    data class LaunchScreen(val duration: Long) : Event(Category.METRICS, Action.STARTUP, "launchScreen", duration.toFloat())
    data class Rooms(val nbOfRooms: Int) : Event(Category.METRICS, Action.STATS, "rooms", nbOfRooms.toFloat())
}