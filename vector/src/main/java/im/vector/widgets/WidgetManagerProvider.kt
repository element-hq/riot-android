/*
 * Copyright 2019 New Vector Ltd
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
package im.vector.widgets

import android.content.Context
import im.vector.util.PreferencesManager
import java.net.MalformedURLException
import java.net.URL

object WidgetManagerProvider {

    private var widgetsManager: WidgetsManager? = null

    fun getWidgetManager(context: Context): WidgetsManager? {
        if (widgetsManager != null) {
            return widgetsManager
        }
        val uiURl = PreferencesManager.getIntegrationManagerUiUrl(context)
        val apiURL = PreferencesManager.getIntegrationManagerApiUrl(context)
        val jitsiUrl = PreferencesManager.getIntegrationManagerJitsiUrl(context)
        if (uiURl.isNullOrBlank() || apiURL.isNullOrBlank() || jitsiUrl.isNullOrBlank()) return null
        return try {
            //Very basic validity check (well formed url)
            URL(uiURl)
            URL(apiURL)
            URL(jitsiUrl)
            val defaultWhitelist = ArrayList(PreferencesManager.getIntegrationWhiteListedUrl(context))
            defaultWhitelist.add(0, apiURL)

            val config = IntegrationManagerConfig(
                    uiUrl = uiURl,
                    apiUrl = apiURL,
                    jitsiUrl = jitsiUrl,
                    whiteListedUrls = defaultWhitelist)
            WidgetsManager(config).also {
                this.widgetsManager = it
            }
        } catch (e: MalformedURLException) {
            null
        }

    }
}