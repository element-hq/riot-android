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
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.features.integrationmanager.IntegrationManager
import org.matrix.androidsdk.features.integrationmanager.isEmptyConfig
import java.net.MalformedURLException
import java.net.URL

class WidgetManagerProvider(val session: MXSession) : IntegrationManager.IntegrationManagerManagerListener {


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
        val userDefinedConfig = session.integrationManager.integrationServerConfig
        var sdkConfig: IntegrationManagerConfig? = null

        if (userDefinedConfig != null) {
            if (userDefinedConfig.isEmptyConfig()) {
                //The user forced a null config so we don't use the default one
                currentConfig = null
                widgetsManager = null
                return null
            }
            val defaultWhitelist = ArrayList(PreferencesManager.getIntegrationWhiteListedUrl(context))
            defaultWhitelist.add(0, userDefinedConfig.apiUrl)
            sdkConfig = IntegrationManagerConfig(
                    uiUrl = userDefinedConfig.uiUrl,
                    apiUrl = userDefinedConfig.apiUrl,
                    jitsiUrl = "${userDefinedConfig.apiUrl}/widgets/jitsi.html",
                    whiteListedUrls = defaultWhitelist)
        } else {
            //Use the default IM
            // TODO we should try to get the one suggested by the HS well-known
            sdkConfig = IntegrationManagerConfig(
                    uiUrl = context.getString(R.string.integrations_ui_url),
                    apiUrl = context.getString(R.string.integrations_rest_url),
                    jitsiUrl = context.getString(R.string.integrations_jitsi_widget_url),
                    whiteListedUrls = ArrayList(PreferencesManager.getIntegrationWhiteListedUrl(context)))
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