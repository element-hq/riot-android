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
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.features.integrationmanager.IntegrationManager
import java.net.MalformedURLException
import java.net.URL

class WidgetManagerProvider(private val session: MXSession) : IntegrationManager.IntegrationManagerManagerListener {


    override fun onIntegrationManagerChange(managerConfig: IntegrationManager) {
        //Clear caches
        currentConfig = null
        widgetsManager = null
    }

    init {
        session.integrationManager.addListener(this)
    }

    private var widgetsManager: WidgetsManager? = null
    private var currentConfig: IntegrationManagerConfig? = null

    fun getWidgetManager(context: Context): WidgetsManager? {
        if (!session.integrationManager.integrationAllowed) return null

        val userDefinedConfig = session.integrationManager.integrationServerConfig
        val sdkConfig: IntegrationManagerConfig?
        val defaultWhitelist = context.resources.getStringArray(R.array.integrations_widgets_urls).asList()

        if (userDefinedConfig != null) {

            val whiteList = defaultWhitelist + userDefinedConfig.apiUrl
            sdkConfig = IntegrationManagerConfig(
                    uiUrl = userDefinedConfig.uiUrl,
                    apiUrl = userDefinedConfig.apiUrl,
                    jitsiUrl = "${userDefinedConfig.apiUrl}/widgets/jitsi.html",
                    whiteListedUrls = whiteList)
        } else {
            //Use the default IM
            // Check if a config is defined in wellknown
            val wellKnownIM = session.integrationManager.getWellKnownIntegrationManagerConfigs().firstOrNull()
            sdkConfig = if (wellKnownIM != null) {
                IntegrationManagerConfig(
                        uiUrl = wellKnownIM.uiUrl,
                        apiUrl = wellKnownIM.apiUrl,
                        jitsiUrl = "${wellKnownIM.apiUrl}/widgets/jitsi.html",
                        whiteListedUrls = listOf(wellKnownIM.apiUrl)
                )
            } else {
                IntegrationManagerConfig(
                        uiUrl = context.getString(R.string.integrations_ui_url),
                        apiUrl = context.getString(R.string.integrations_rest_url),
                        jitsiUrl = context.getString(R.string.integrations_jitsi_widget_url),
                        whiteListedUrls = defaultWhitelist)
            }
        }



        if (currentConfig == sdkConfig && widgetsManager != null) {
            return widgetsManager
        }

        if (sdkConfig.uiUrl.isBlank() || sdkConfig.apiUrl.isBlank() || sdkConfig.jitsiUrl.isBlank()) {
            currentConfig = null
            widgetsManager = null
            return null
        }

        return try {
            //Very basic validity check (well formed url)
            URL(sdkConfig.uiUrl)
            URL(sdkConfig.apiUrl)
            URL(sdkConfig.jitsiUrl)

            currentConfig = sdkConfig

            WidgetsManager(sdkConfig).also {
                this.widgetsManager = it
            }
        } catch (e: MalformedURLException) {
            null
        }

    }
}