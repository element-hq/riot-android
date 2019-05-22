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
import im.vector.BuildConfig
import im.vector.util.PreferencesManager
import org.matrix.androidsdk.core.Log

class AppAnalytics(private val context: Context, private vararg val analytics: Analytics) : Analytics {

    override fun trackScreen(screen: String, title: String?) {
        if (!BuildConfig.DEBUG && PreferencesManager.useAnalytics(context)) {
            analytics.forEach {
                it.trackScreen(screen, title)
            }
        } else {
            Log.d("Analytics - screen", screen)
        }
    }

    override fun visitVariable(index: Int, name: String, value: String) {
        if (!BuildConfig.DEBUG && PreferencesManager.useAnalytics(context)) {
            analytics.forEach {
                it.visitVariable(index, name, value)
            }
        } else {
            Log.d("Analytics - visit variable", "Variable $name at index $index: $value")
        }
    }

    override fun trackEvent(event: TrackingEvent) {
        if (!BuildConfig.DEBUG && PreferencesManager.useAnalytics(context)) {
            analytics.forEach {
                it.trackEvent(event)
            }
        } else {
            Log.d("Analytics - event", event.toString())
        }
    }

    override fun forceDispatch() {
        analytics.forEach {
            it.forceDispatch()
        }
    }

}