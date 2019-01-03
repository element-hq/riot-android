/*
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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