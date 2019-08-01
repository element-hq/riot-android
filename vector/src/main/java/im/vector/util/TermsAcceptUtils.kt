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

package im.vector.util

import android.app.Activity
import android.content.Intent
import android.support.v7.app.AlertDialog
import im.vector.R
import im.vector.widgets.WidgetManagerProvider
import org.matrix.androidsdk.MXSession


fun checkTermsForIntegrationMgr(activity: Activity, session: MXSession, successIntent: Intent, requestCode: Int?) {
    val wm = WidgetManagerProvider.getWidgetManager(activity) ?: return
    if (PreferencesManager.hasAgreedToIntegrationManager(activity, session.myUserId, wm.uiUrl)) {
        if (requestCode != null) {
            activity.startActivityForResult(successIntent, requestCode)
        } else {
            activity.startActivity(successIntent)
        }
    } else {
        //Need to ask for consent
        val dialog = AlertDialog.Builder(activity)
                .setTitle(R.string.widget_integration_accept_terms_dialog_title)
                .setMessage(R.string.widget_integration_accept_terms_dialog_message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setNeutralButton(R.string.review, null)
                .setPositiveButton(R.string.accept) { _, _ ->
                    if (requestCode != null) {
                        activity.startActivityForResult(successIntent, requestCode)
                    } else {
                        activity.startActivity(successIntent)
                    }
                }
                .setNegativeButton(R.string.decline, null)
                .create()

        //On tap review we don't close the dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                openUrlInExternalBrowser(activity, "https://example.org/tos/v1/fr.html")
            }
        }
        dialog.show()
    }
}