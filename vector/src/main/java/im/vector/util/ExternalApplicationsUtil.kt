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

package im.vector.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Browser
import android.provider.MediaStore
import androidx.core.widget.toast
import im.vector.R
import org.matrix.androidsdk.util.Log
import java.text.SimpleDateFormat
import java.util.*

private const val LOG_TAG = "ExternalApplicationsUtil"

/**
 * Open a url in the internet browser of the system
 */
fun openUrlInExternalBrowser(context: Context, url: String?) {
    url?.let {
        openUrlInExternalBrowser(context, Uri.parse(it))
    }
}

/**
 * Open a uri in the internet browser of the system
 */
fun openUrlInExternalBrowser(context: Context, uri: Uri?) {
    uri?.let {
        val browserIntent = Intent(Intent.ACTION_VIEW, it).apply {
            putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
        }

        try {
            context.startActivity(browserIntent)
        } catch (activityNotFoundException: ActivityNotFoundException) {
            context.toast(R.string.error_no_external_application_found)
        }
    }
}

/**
 * Open sound recorder external application
 */
fun openSoundRecorder(activity: Activity, requestCode: Int) {
    val recordSoundIntent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)

    // Create chooser
    val chooserIntent = Intent.createChooser(recordSoundIntent, activity.getString(R.string.go_on_with))

    try {
        activity.startActivityForResult(chooserIntent, requestCode)
    } catch (activityNotFoundException: ActivityNotFoundException) {
        activity.toast(R.string.error_no_external_application_found)
    }
}

/**
 * Open file selection activity
 */
fun openFileSelection(activity: Activity, requestCode: Int) {
    val fileIntent = Intent(Intent.ACTION_GET_CONTENT)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }

    fileIntent.type = "*/*"

    try {
        activity.startActivityForResult(fileIntent, requestCode)
    } catch (activityNotFoundException: ActivityNotFoundException) {
        activity.toast(R.string.error_no_external_application_found)
    }
}

/**
 * Open external video recorder
 */
fun openVideoRecorder(activity: Activity, requestCode: Int) {
    val captureIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)

    // lowest quality
    captureIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0)

    try {
        activity.startActivityForResult(captureIntent, requestCode)
    } catch (activityNotFoundException: ActivityNotFoundException) {
        activity.toast(R.string.error_no_external_application_found)
    }
}

/**
 * Open external camera
 * @return the latest taken picture camera uri
 */
fun openCamera(activity: Activity, titlePrefix: String, requestCode: Int): String? {
    val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

    // the following is a fix for buggy 2.x devices
    val date = Date()
    val formatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
    val values = ContentValues()
    values.put(MediaStore.Images.Media.TITLE, titlePrefix + formatter.format(date))
    // The Galaxy S not only requires the name of the file to output the image to, but will also not
    // set the mime type of the picture it just took (!!!). We assume that the Galaxy S takes image/jpegs
    // so the attachment uploader doesn't freak out about there being no mimetype in the content database.
    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    var dummyUri: Uri? = null
    try {
        dummyUri = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        if (null == dummyUri) {
            Log.e(LOG_TAG, "Cannot use the external storage media to save image")
        }
    } catch (uoe: UnsupportedOperationException) {
        Log.e(LOG_TAG, "Unable to insert camera URI into MediaStore.Images.Media.EXTERNAL_CONTENT_URI"
                + " - no SD card? Attempting to insert into device storage.")
    } catch (e: Exception) {
        Log.e(LOG_TAG, "Unable to insert camera URI into MediaStore.Images.Media.EXTERNAL_CONTENT_URI. $e")
    }

    if (null == dummyUri) {
        try {
            dummyUri = activity.contentResolver.insert(MediaStore.Images.Media.INTERNAL_CONTENT_URI, values)
            if (null == dummyUri) {
                Log.e(LOG_TAG, "Cannot use the internal storage to save media to save image")
            }

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Unable to insert camera URI into internal storage. Giving up. $e")
        }
    }

    if (dummyUri != null) {
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, dummyUri)
        Log.d(LOG_TAG, "trying to take a photo on " + dummyUri.toString())
    } else {
        Log.d(LOG_TAG, "trying to take a photo with no predefined uri")
    }

    // Store the dummy URI which will be set to a placeholder location. When all is lost on Samsung devices,
    // this will point to the data we're looking for.
    // Because Activities tend to use a single MediaProvider for all their intents, this field will only be the
    // *latest* TAKE_PICTURE Uri. This is deemed acceptable as the normal flow is to create the intent then immediately
    // fire it, meaning onActivityResult/getUri will be the next thing called, not another createIntentFor.
    val result = if (dummyUri == null) null else dummyUri.toString()

    try {
        activity.startActivityForResult(captureIntent, requestCode)

        return result
    } catch (activityNotFoundException: ActivityNotFoundException) {
        activity.toast(R.string.error_no_external_application_found)
    }

    return null
}