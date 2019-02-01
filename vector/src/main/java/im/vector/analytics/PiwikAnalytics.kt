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

import android.content.Context
import org.piwik.sdk.Piwik
import org.piwik.sdk.QueryParams
import org.piwik.sdk.Tracker
import org.piwik.sdk.TrackerConfig
import org.piwik.sdk.extra.CustomVariables
import org.piwik.sdk.extra.TrackHelper

/**
 * A class implementing the Analytics interface for the Piwik solution
 */
class PiwikAnalytics(context: Context) : Analytics {
    private val tracker: Tracker

    init {
        val config = TrackerConfig("https://piwik.riot.im/", 1, " AndroidPiwikTracker")
        tracker = Piwik.getInstance(context).newTracker(config)
    }

    override fun trackScreen(screen: String, title: String?) {
        TrackHelper.track()
                .screen(screen)
                .title(title)
                .with(tracker)
    }

    override fun trackEvent(event: TrackingEvent) {
        TrackHelper.track()
                .event(event.category.value, event.action.value)
                .name(event.title)
                .value(event.value)
                .with(tracker)
    }

    @Suppress("DEPRECATION")
    override fun visitVariable(index: Int, name: String, value: String) {
        val customVariables = CustomVariables(tracker.defaultTrackMe.get(QueryParams.VISIT_SCOPE_CUSTOM_VARIABLES))
        customVariables.put(index, name, value)
        tracker.defaultTrackMe.set(QueryParams.VISIT_SCOPE_CUSTOM_VARIABLES, customVariables.toString())
    }

    override fun forceDispatch() {
        tracker.dispatch()
    }
}
