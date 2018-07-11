package im.vector.analytics

import org.matrix.androidsdk.data.metrics.MetricsListener
import java.util.concurrent.atomic.AtomicBoolean

class MetricsListenerProxy(val analytics: Analytics) : MetricsListener {

    private val firstSyncDispatched = AtomicBoolean()
    private val incrementalSyncDispatched = AtomicBoolean()
    private val storePreloadDispatched = AtomicBoolean()
    private val roomsLoadedDispatched = AtomicBoolean()

    override fun onInitialSyncFinished(duration: Long) {
        if (!firstSyncDispatched.getAndSet(true)) {
            val event = Event.InitialSync(duration)
            analytics.trackEvent(event)
        }
    }

    override fun onIncrementalSyncFinished(duration: Long) {
        if (!incrementalSyncDispatched.getAndSet(true)) {
            val event = Event.IncrementalSync(duration)
            analytics.trackEvent(event)
        }
    }

    override fun onStorePreloaded(duration: Long) {
        if (!storePreloadDispatched.getAndSet(true)) {
            val event = Event.StorePreload(duration)
            analytics.trackEvent(event)
        }
    }

    override fun onRoomsLoaded(nbOfRooms: Int) {
        if (!roomsLoadedDispatched.getAndSet(true)) {
            val event = Event.Rooms(nbOfRooms)
            analytics.trackEvent(event)
        }
    }

}