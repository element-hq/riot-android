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

package im.vector.features.logout

import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import im.vector.R
import im.vector.activity.VectorHomeActivity
import im.vector.util.PreferencesManager
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.crypto.cryptostore.db.hash


class ProposeLogout(private val session: MXSession,
                    private val activity: VectorHomeActivity) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(activity)

    fun process() {
        detectUpgrade()

        maybeShowDialog()
    }

    private fun detectUpgrade() {
        val version = preferences.getInt(PreferencesManager.VERSION_BUILD, 0)

        if (version in 1..90300) {
            // This is an upgrade, check identity server value
            val identityServerUrl = session.homeServerConfig.identityServerUri.toString()
            val homeServerUrl = session.homeServerConfig.homeserverUri.toString()

            if ((identityServerUrl == "https://matrix.org" || identityServerUrl == "https://vector.im")
                    && (homeServerUrl == "https://matrix.org" || homeServerUrl.endsWith("modular.im"))) {
                // We can skip the dialog
            } else {
                preferences.edit {
                    putString(ACCESS_TOKEN_HASH, session.homeServerConfig.credentials.accessToken.hash())
                }
            }
        }
    }

    private fun maybeShowDialog() {
        if (preferences.getString(ACCESS_TOKEN_HASH, "") == session.homeServerConfig.credentials.accessToken.hash()) {
            // Prompt the user to perform a logout
            showDialog()
        }
    }

    private fun showDialog() {
        val v = LayoutInflater.from(activity).inflate(R.layout.dialog_html_text, null)

        v.findViewById<TextView>(R.id.dialog_text).text = activity.getString(R.string.security_warning_identity_server,
                session.homeServerConfig.identityServerUri.toString(),
                session.homeServerConfig.identityServerUri.toString())

        AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_title_warning)
                .setIcon(R.drawable.vector_warning_red)
                .setView(v)
                .setCancelable(false)
                .setPositiveButton(R.string.ssl_logout_account) { _, _ ->
                    activity.signOut(false)
                }
                .setNegativeButton(R.string.ignore) { _, _ ->
                    preferences.edit {
                        remove(ACCESS_TOKEN_HASH)
                    }
                }
                .show()
    }

    companion object {
        const val ACCESS_TOKEN_HASH = "ACCESS_TOKEN_HASH"
    }
}