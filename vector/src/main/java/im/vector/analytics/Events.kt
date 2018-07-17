package im.vector.analytics

import im.vector.analytics.e2e.DecryptionFailureReason

/**
 * A category to be linked to an {@link im.vector.analytics.TrackingEvent}
 * @param value to log into your analytics console
 */
enum class Category(val value: String) {
    METRICS("Metrics"),
    E2E("E2E")
}

/**
 * An action to be linked to an {@link im.vector.analytics.TrackingEvent}
 * @param value to log into your analytics console
 */
enum class Action(val value: String) {
    STARTUP("android.startup"),
    STATS("android.stats"),
    DECRYPTION_FAILURE("Decryption failure")
}

/**
 * Represents all the analytics events, to be dispatched to {@link im.vector.analytics.Analytic#trackEvent()}
 * @param category the category associated with the event
 * @param action the action associated with the event
 * @param title the title associated with the event
 * @param value the optional value associated with the event
 */
sealed class TrackingEvent(val category: Category, val action: Action, val title: String? = null, val value: Float? = null) {
    data class InitialSync(val duration: Long) : TrackingEvent(Category.METRICS, Action.STARTUP, "initialSync", duration.toFloat())
    data class IncrementalSync(val duration: Long) : TrackingEvent(Category.METRICS, Action.STARTUP, "incrementalSync", duration.toFloat())
    data class StorePreload(val duration: Long) : TrackingEvent(Category.METRICS, Action.STARTUP, "storePreload", duration.toFloat())
    data class LaunchScreen(val duration: Long) : TrackingEvent(Category.METRICS, Action.STARTUP, "launchScreen", duration.toFloat())
    data class Rooms(val nbOfRooms: Int) : TrackingEvent(Category.METRICS, Action.STATS, "rooms", nbOfRooms.toFloat())

    data class DecryptionFailure(private val reason: DecryptionFailureReason,
                                 private val failureCount: Int) :
            TrackingEvent(Category.E2E, Action.DECRYPTION_FAILURE, reason.value, failureCount.toFloat())


}