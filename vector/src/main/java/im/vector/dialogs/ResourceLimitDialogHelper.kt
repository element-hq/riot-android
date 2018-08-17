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
import android.app.AlertDialog
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import im.vector.R
import im.vector.activity.interfaces.Restorable
import im.vector.error.ResourceLimitErrorFormatter
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.util.Log

private const val LOG_TAG = "ResourceLimitDialogHelper"

class ResourceLimitDialogHelper private constructor(private val activity: Activity,
                                                    private val dialogLocker: DialogLocker) :

        Restorable by dialogLocker {

    constructor(activity: Activity, savedInstanceState: Bundle?) : this(activity, DialogLocker(savedInstanceState))

    private val formatter = ResourceLimitErrorFormatter(activity)

    /* ==========================================================================================
     * Public methods
     * ========================================================================================== */

    /**
     * Display the resource limit dialog, if not already displayed
     */
    fun displayDialog(matrixError: MatrixError) {
        if (matrixError.adminContact == null) {
            Log.e(LOG_TAG, "Missing required parameter 'admin_contact'")
            return
        }
        val dialog = dialogLocker.displayDialog {
            val message = formatter.format(ResourceLimitErrorFormatter.Mode.NonActive, matrixError)
            AlertDialog.Builder(activity)
                    .setIcon(R.drawable.error)
                    .setTitle(R.string.dialog_title_warning)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, null)
        }
        dialog?.apply {
            findViewById<TextView>(android.R.id.message).movementMethod = LinkMovementMethod.getInstance()
        }
    }

    /* ==========================================================================================
     * Private
     * ========================================================================================== */

}