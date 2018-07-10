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

package im.vector.dialogs

import android.app.Activity
import android.os.Bundle
import android.support.v7.app.AlertDialog
import im.vector.Matrix
import im.vector.R
import im.vector.activity.SimpleWebViewActivity
import im.vector.activity.interfaces.Restorable
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.util.Log

class ConsentNotGivenHelper(private val activity: Activity, savedInstanceState: Bundle?) :
        Restorable {

    /* ==========================================================================================
     * Data
     * ========================================================================================== */

    // Ensure the dialog is not displayed multiple times
    private var isDialogDisplayed = savedInstanceState?.getBoolean(KEY_DIALOG_IS_DISPLAYED, false) == true

    /* ==========================================================================================
     * Public methods
     * ========================================================================================== */

    /**
     * Display the consent dialog, if not already displayed
     */
    fun displayDialog(matrixError: MatrixError) {
        if (isDialogDisplayed) {
            // Filter this request
            Log.w(LOG_TAG, "Filtered dialog request")
            return
        }

        // Check required parameter
        if (matrixError.consentUri == null) {
            Log.e(LOG_TAG, "Missing required parameter 'consent_uri'")
            return
        }

        isDialogDisplayed = true

        AlertDialog.Builder(activity)
                .setTitle(R.string.settings_app_term_conditions)
                .setMessage(activity.getString(R.string.dialog_user_consent_content,
                        Matrix.getInstance(activity).defaultSession.homeServerConfig.homeserverUri.host))
                .setPositiveButton(R.string.dialog_user_consent_submit) { _, _ ->
                    openWebViewActivity(matrixError.consentUri)
                    isDialogDisplayed = false
                }
                .setNegativeButton(R.string.later) { _, _ ->
                    isDialogDisplayed = false
                }
                .setOnCancelListener { isDialogDisplayed = false }
                .show()
    }

    /* ==========================================================================================
     * Implements Restorable
     * ========================================================================================== */

    override fun saveState(outState: Bundle) {
        outState.putBoolean(KEY_DIALOG_IS_DISPLAYED, isDialogDisplayed)
    }

    /* ==========================================================================================
     * Private
     * ========================================================================================== */

    private fun openWebViewActivity(consentUri: String) {
        activity.startActivity(SimpleWebViewActivity.getIntent(activity,
                consentUri,
                R.string.settings_app_term_conditions))
    }

    /* ==========================================================================================
     * COMPANION
     * ========================================================================================== */

    companion object {
        private const val LOG_TAG = "ConsentNotGivenHelper"

        private const val KEY_DIALOG_IS_DISPLAYED = "ConsentNotGivenHelper.KEY_DIALOG_IS_DISPLAYED"
    }
}