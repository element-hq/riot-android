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

package im.vector.disclaimer

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AlertDialog
import im.vector.BuildConfig
import im.vector.R
import im.vector.util.openPlayStore
import org.jetbrains.anko.toast

fun showDisclaimerDialog(activity: Activity, allowAppAccess: Boolean) {
    // Element is only available on Android 5.0
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        return
    }

    val dialogLayout = activity.layoutInflater.inflate(R.layout.dialog_disclaimer_content, null)

    AlertDialog.Builder(activity)
            .setView(dialogLayout)
            .setCancelable(allowAppAccess)
            .apply {
                if (allowAppAccess) {
                    setNegativeButton(R.string.element_disclaimer_negative_button, null)
                } else {
                    setNegativeButton(R.string.action_close) { _, _ -> activity.finish() }
                    setNeutralButton(R.string.element_disclaimer_uninstall_button) { _, _ -> uninstall(activity) }
                }
            }
            .setPositiveButton(R.string.element_disclaimer_positive_button) { _, _ ->
                openPlayStore(activity, "im.vector.app")
            }
            .show()
}

private fun uninstall(activity: Activity) {
    @Suppress("DEPRECATION")
    val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE)
    intent.data = Uri.parse("package:" + BuildConfig.APPLICATION_ID)
    try {
        activity.startActivity(intent)
    } catch (anfe: ActivityNotFoundException) {
        activity.toast(R.string.error_no_external_application_found)
    }
}
