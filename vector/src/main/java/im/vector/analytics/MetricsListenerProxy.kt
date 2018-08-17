package im.vector.analytics

import org.matrix.androidsdk.data.metrics.MetricsListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A proxy implementing the MetricsListener. It does then dispatch the metrics to analytics, with a one time check by session.
 */
class MetricsListenerProxy(val analytics: Analytics) : MetricsListener {

    private val firstSyncDispatched = AtomicBoolean()
    private val incrementalSyncDispatched = AtomicBoolean()
    private val storePreloadDispatched = AtomicBoolean()
    private val roomsLoadedDispatched = AtomicBoolean()

    override fun onInitialSyncFinished(duration: Long) {
        if (!firstSyncDispatched.getAndSet(true)) {
            val event = TrackingEvent.InitialSync(duration)
            analytics.trackEvent(event)
        }
    }

    override fun onIncrementalSyncFinished(duration: Long) {
        if (!incrementalSyncDispatched.getAndSet(true)) {
            val event = TrackingEvent.IncrementalSync(duration)
            analytics.trackEvent(event)
        }
    }

    override fun onStorePreloaded(duration: Long) {
        if (!storePreloadDispatched.getAndSet(true)) {
            val event = TrackingEvent.StorePreload(duration)
            analytics.trackEvent(event)
        }
    }

    override fun onRoomsLoaded(nbOfRooms: Int) {
        if (!roomsLoadedDispatched.getAndSet(true)) {
            val event = TrackingEvent.Rooms(nbOfRooms)
            analytics.trackEvent(event)
        }
    }
}