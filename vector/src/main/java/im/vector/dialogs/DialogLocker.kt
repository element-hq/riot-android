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

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import im.vector.activity.interfaces.Restorable
import org.matrix.androidsdk.core.Log

private const val KEY_DIALOG_IS_DISPLAYED = "DialogLocker.KEY_DIALOG_IS_DISPLAYED"
private const val LOG_TAG = "DialogLocker"

/**
 * Class to avoid displaying twice the same dialog
 */
class DialogLocker(savedInstanceState: Bundle?) : Restorable {

    private var isDialogDisplayed = savedInstanceState?.getBoolean(KEY_DIALOG_IS_DISPLAYED, false) == true

    private fun unlock() {
        isDialogDisplayed = false
    }

    private fun lock() {
        isDialogDisplayed = true
    }

    fun displayDialog(builder: () -> AlertDialog.Builder): AlertDialog? {
        return if (isDialogDisplayed) {
            Log.w(LOG_TAG, "Filtered dialog request")
            null
        } else {
            builder
                    .invoke()
                    .create()
                    .apply {
                        setOnShowListener { lock() }
                        setOnCancelListener { unlock() }
                        setOnDismissListener { unlock() }
                        show()
                    }
        }
    }

    override fun saveState(outState: Bundle) {
        outState.putBoolean(KEY_DIALOG_IS_DISPLAYED, isDialogDisplayed)
    }
}