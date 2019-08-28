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

package im.vector.features.hhs

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import androidx.appcompat.app.AlertDialog
import com.binaryfork.spanny.Spanny
import im.vector.R
import im.vector.activity.interfaces.Restorable
import im.vector.dialogs.DialogLocker
import im.vector.util.openUri
import org.matrix.androidsdk.core.model.MatrixError

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
        dialogLocker.displayDialog {
            val title = Spanny(activity.getString(R.string.resource_limit_exceeded_title), StyleSpan(Typeface.BOLD))
            val message = formatter.format(matrixError, ResourceLimitErrorFormatter.Mode.Hard, separator = "\n\n")

            val builder = AlertDialog.Builder(activity, R.style.AppTheme_Dialog_Light)
                    .setTitle(title)
                    .setMessage(message)

            if (matrixError.adminUri != null) {
                builder
                        .setPositiveButton(R.string.resource_limit_contact_action) { _, _ ->
                            openUri(activity, matrixError.adminUri!!)
                        }
                        .setNegativeButton(R.string.cancel, null)

            } else {
                builder.setPositiveButton(R.string.ok, null)
            }

            builder
        }
    }

}