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
import im.vector.R
import org.matomo.sdk.Matomo
import org.matomo.sdk.QueryParams
import org.matomo.sdk.Tracker
import org.matomo.sdk.TrackerBuilder
import org.matomo.sdk.extra.CustomVariables
import org.matomo.sdk.extra.TrackHelper

/**
 * A class implementing the Analytics interface for the Matomo solution
 */
class MatomoAnalytics(context: Context) : Analytics {
    private val tracker: Tracker

    init {
        val builder = TrackerBuilder(context.getString(R.string.matomo_server_url),
                context.getString(R.string.matomo_site_id).toInt(),
                context.getString(R.string.matomo_tracker_name))
        tracker = builder.build(Matomo.getInstance(context))
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
