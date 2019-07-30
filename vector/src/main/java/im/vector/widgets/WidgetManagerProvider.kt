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
import im.vector.R
import im.vector.util.PreferencesManager
import java.net.MalformedURLException
import java.net.URL

object WidgetManagerProvider {

    private var widgetsManager: WidgetsManager? = null

    fun getWidgetManager(context: Context): WidgetsManager? {
        if (widgetsManager != null) {
            return widgetsManager
        }
        val serverURL = PreferencesManager.getIntegrationServerUrl(context)
        if (serverURL.isNullOrBlank()) return null
        return try {
            URL(serverURL)
            val defaultWhitelist = ArrayList(PreferencesManager.getIntegrationWhiteListedUrl(context))
            defaultWhitelist.add(0,"$serverURL/api")

            val config = IntegrationManagerConfig(
                    uiUrl = "$serverURL/",
                    apiUrl = "$serverURL/api",
                    jitsiUrl = "$serverURL/api/widgets/jitsi.html",
                    whiteListedUrls = defaultWhitelist)
            WidgetsManager(config).also {
                this.widgetsManager = it
            }
        } catch (e: MalformedURLException) {
            null
        }

    }
}