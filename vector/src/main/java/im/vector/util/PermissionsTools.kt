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

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.text.TextUtils
import android.widget.Toast
import im.vector.R
import im.vector.contacts.ContactsManager
import org.matrix.androidsdk.core.Log
import java.util.*


private const val LOG_TAG = "PermissionUtils"

// Android M permission request code management
private const val PERMISSIONS_GRANTED = true
private const val PERMISSIONS_DENIED = !PERMISSIONS_GRANTED

// Permission bit
private const val PERMISSION_BYPASSED = 0x0
const val PERMISSION_CAMERA = 0x1
private const val PERMISSION_WRITE_EXTERNAL_STORAGE = 0x1 shl 1
private const val PERMISSION_RECORD_AUDIO = 0x1 shl 2
private const val PERMISSION_READ_CONTACTS = 0x1 shl 3

// Permissions sets
const val PERMISSIONS_FOR_AUDIO_IP_CALL = PERMISSION_RECORD_AUDIO
const val PERMISSIONS_FOR_VIDEO_IP_CALL = PERMISSION_CAMERA or PERMISSION_RECORD_AUDIO
const val PERMISSIONS_FOR_TAKING_PHOTO = PERMISSION_CAMERA or PERMISSION_WRITE_EXTERNAL_STORAGE
const val PERMISSIONS_FOR_MEMBERS_SEARCH = PERMISSION_READ_CONTACTS
const val PERMISSIONS_FOR_MEMBER_DETAILS = PERMISSION_READ_CONTACTS
const val PERMISSIONS_FOR_ROOM_AVATAR = PERMISSION_CAMERA
const val PERMISSIONS_FOR_VIDEO_RECORDING = PERMISSION_CAMERA or PERMISSION_RECORD_AUDIO
const val PERMISSIONS_FOR_WRITING_FILES = PERMISSION_WRITE_EXTERNAL_STORAGE

private const val PERMISSIONS_EMPTY = PERMISSION_BYPASSED

// Request code to ask permission to the system (arbitrary values)
const val PERMISSION_REQUEST_CODE = 567
const val PERMISSION_REQUEST_CODE_LAUNCH_CAMERA = 568
const val PERMISSION_REQUEST_CODE_LAUNCH_NATIVE_CAMERA = 569
const val PERMISSION_REQUEST_CODE_LAUNCH_NATIVE_VIDEO_CAMERA = 570
const val PERMISSION_REQUEST_CODE_AUDIO_CALL = 571
const val PERMISSION_REQUEST_CODE_VIDEO_CALL = 572
const val PERMISSION_REQUEST_CODE_EXPORT_KEYS = 573
const val PERMISSION_REQUEST_CODE_CHANGE_AVATAR = 574

/**
 * Log the used permissions statuses.
 */
fun logPermissionStatuses(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val permissions = Arrays.asList(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_CONTACTS)

        Log.d(LOG_TAG, "## logPermissionStatuses() : log the permissions status used by the app")

        for (permission in permissions) {
            Log.d(LOG_TAG, ("Status of [$permission] : " +
                    if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(context, permission))
                        "PERMISSION_GRANTED"
                    else
                        "PERMISSION_DENIED"))
        }
    }
}


/**
 * See [.checkPermissions]
 *
 * @param permissionsToBeGrantedBitMap
 * @param activity
 * @return true if the permissions are granted (synchronous flow), false otherwise (asynchronous flow)
 */
fun checkPermissions(permissionsToBeGrantedBitMap: Int,
                     activity: Activity,
                     requestCode: Int = PERMISSION_REQUEST_CODE): Boolean {
    return checkPermissions(permissionsToBeGrantedBitMap, activity, null, requestCode)
}

/**
 * See [.checkPermissions]
 *
 * @param permissionsToBeGrantedBitMap
 * @param fragment
 * @return true if the permissions are granted (synchronous flow), false otherwise (asynchronous flow)
 */
fun checkPermissions(permissionsToBeGrantedBitMap: Int,
                     fragment: Fragment,
                     requestCode: Int = PERMISSION_REQUEST_CODE): Boolean {
    return checkPermissions(permissionsToBeGrantedBitMap, fragment.activity, fragment, requestCode)
}

/**
 * Check if the permissions provided in the list are granted.
 * This is an asynchronous method if permissions are requested, the final response
 * is provided in onRequestPermissionsResult(). In this case checkPermissions()
 * returns false.
 * <br></br>If checkPermissions() returns true, the permissions were already granted.
 * The permissions to be granted are given as bit map in permissionsToBeGrantedBitMap (ex: [.PERMISSIONS_FOR_TAKING_PHOTO]).
 * <br></br>permissionsToBeGrantedBitMap is passed as the request code in onRequestPermissionsResult().
 *
 *
 * If a permission was already denied by the user, a popup is displayed to
 * explain why vector needs the corresponding permission.
 *
 * @param permissionsToBeGrantedBitMap the permissions bit map to be granted
 * @param activity                     the calling Activity that is requesting the permissions (or fragment parent)
 * @param fragment                     the calling fragment that is requesting the permissions
 * @return true if the permissions are granted (synchronous flow), false otherwise (asynchronous flow)
 */
private fun checkPermissions(permissionsToBeGrantedBitMap: Int,
                             activity: Activity?,
                             fragment: Fragment?,
                             requestCode: Int): Boolean {
    var isPermissionGranted = false

    // sanity check
    if (null == activity) {
        Log.w(LOG_TAG, "## checkPermissions(): invalid input data")
        isPermissionGranted = false
    } else if (PERMISSIONS_EMPTY == permissionsToBeGrantedBitMap) {
        isPermissionGranted = true
    } else if (PERMISSIONS_FOR_AUDIO_IP_CALL != permissionsToBeGrantedBitMap
            && PERMISSIONS_FOR_VIDEO_IP_CALL != permissionsToBeGrantedBitMap
            && PERMISSIONS_FOR_TAKING_PHOTO != permissionsToBeGrantedBitMap
            && PERMISSIONS_FOR_MEMBERS_SEARCH != permissionsToBeGrantedBitMap
            && PERMISSIONS_FOR_MEMBER_DETAILS != permissionsToBeGrantedBitMap
            && PERMISSIONS_FOR_ROOM_AVATAR != permissionsToBeGrantedBitMap
            && PERMISSIONS_FOR_VIDEO_RECORDING != permissionsToBeGrantedBitMap
            && PERMISSIONS_FOR_WRITING_FILES != permissionsToBeGrantedBitMap) {
        Log.w(LOG_TAG, "## checkPermissions(): permissions to be granted are not supported")
        isPermissionGranted = false
    } else {
        val permissionListAlreadyDenied = ArrayList<String>()
        val permissionsListToBeGranted = ArrayList<String>()
        var isRequestPermissionRequired = false
        var explanationMessage = ""

        // retrieve the permissions to be granted according to the request code bit map
        if (PERMISSION_CAMERA == permissionsToBeGrantedBitMap and PERMISSION_CAMERA) {
            val permissionType = Manifest.permission.CAMERA
            isRequestPermissionRequired = isRequestPermissionRequired or
                    updatePermissionsToBeGranted(activity, permissionListAlreadyDenied, permissionsListToBeGranted, permissionType)
        }

        if (PERMISSION_RECORD_AUDIO == permissionsToBeGrantedBitMap and PERMISSION_RECORD_AUDIO) {
            val permissionType = Manifest.permission.RECORD_AUDIO
            isRequestPermissionRequired = isRequestPermissionRequired or
                    updatePermissionsToBeGranted(activity, permissionListAlreadyDenied, permissionsListToBeGranted, permissionType)
        }

        if (PERMISSION_WRITE_EXTERNAL_STORAGE == permissionsToBeGrantedBitMap and PERMISSION_WRITE_EXTERNAL_STORAGE) {
            val permissionType = Manifest.permission.WRITE_EXTERNAL_STORAGE
            isRequestPermissionRequired = isRequestPermissionRequired or
                    updatePermissionsToBeGranted(activity, permissionListAlreadyDenied, permissionsListToBeGranted, permissionType)
        }

        // the contact book access is requested for any android platforms
        // for android M, we use the system preferences
        // for android < M, we use a dedicated settings
        if (PERMISSION_READ_CONTACTS == permissionsToBeGrantedBitMap and PERMISSION_READ_CONTACTS) {
            val permissionType = Manifest.permission.READ_CONTACTS

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                isRequestPermissionRequired = isRequestPermissionRequired or
                        updatePermissionsToBeGranted(activity, permissionListAlreadyDenied, permissionsListToBeGranted, permissionType)
            } else {
                if (!ContactsManager.getInstance().isContactBookAccessRequested) {
                    isRequestPermissionRequired = true
                    permissionsListToBeGranted.add(permissionType)
                }
            }
        }

        // if some permissions were already denied: display a dialog to the user before asking again.
        if (!permissionListAlreadyDenied.isEmpty()) {
            if (permissionsToBeGrantedBitMap == PERMISSIONS_FOR_VIDEO_IP_CALL || permissionsToBeGrantedBitMap == PERMISSIONS_FOR_AUDIO_IP_CALL) {
                // Permission request for VOIP call
                if (permissionListAlreadyDenied.contains(Manifest.permission.CAMERA)
                        && permissionListAlreadyDenied.contains(Manifest.permission.RECORD_AUDIO)) {
                    // Both missing
                    explanationMessage += activity.getString(R.string.permissions_rationale_msg_camera_and_audio)
                } else if (permissionListAlreadyDenied.contains(Manifest.permission.RECORD_AUDIO)) {
                    // Audio missing
                    explanationMessage += activity.getString(R.string.permissions_rationale_msg_record_audio)
                    explanationMessage += activity.getString(R.string.permissions_rationale_msg_record_audio_explanation)
                } else if (permissionListAlreadyDenied.contains(Manifest.permission.CAMERA)) {
                    // Camera missing
                    explanationMessage += activity.getString(R.string.permissions_rationale_msg_camera)
                    explanationMessage += activity.getString(R.string.permissions_rationale_msg_camera_explanation)
                }
            } else {
                permissionListAlreadyDenied.forEach {
                    when (it) {
                        Manifest.permission.CAMERA -> {
                            if (!TextUtils.isEmpty(explanationMessage)) {
                                explanationMessage += "\n\n"
                            }
                            explanationMessage += activity.getString(R.string.permissions_rationale_msg_camera)
                        }
                        Manifest.permission.RECORD_AUDIO -> {
                            if (!TextUtils.isEmpty(explanationMessage)) {
                                explanationMessage += "\n\n"
                            }
                            explanationMessage += activity.getString(R.string.permissions_rationale_msg_record_audio)
                        }
                        Manifest.permission.WRITE_EXTERNAL_STORAGE -> {
                            if (!TextUtils.isEmpty(explanationMessage)) {
                                explanationMessage += "\n\n"
                            }
                            explanationMessage += activity.getString(R.string.permissions_rationale_msg_storage)
                        }
                        Manifest.permission.READ_CONTACTS -> {
                            if (!TextUtils.isEmpty(explanationMessage)) {
                                explanationMessage += "\n\n"
                            }
                            explanationMessage += activity.getString(R.string.permissions_rationale_msg_contacts)
                        }
                        else -> Log.d(LOG_TAG, "## checkPermissions(): already denied permission not supported")
                    }
                }
            }

            // display the dialog with the info text
            AlertDialog.Builder(activity)
                    .setTitle(R.string.permissions_rationale_popup_title)
                    .setMessage(explanationMessage)
                    .setOnCancelListener { Toast.makeText(activity, R.string.missing_permissions_warning, Toast.LENGTH_SHORT).show() }
                    .setPositiveButton(R.string.ok) { _, _ ->
                        if (!permissionsListToBeGranted.isEmpty()) {
                            fragment?.requestPermissions(permissionsListToBeGranted.toTypedArray(), requestCode)
                                    ?: run {
                                        ActivityCompat.requestPermissions(activity, permissionsListToBeGranted.toTypedArray(), requestCode)
                                    }
                        }
                    }
                    .show()
        } else {
            // some permissions are not granted, ask permissions
            if (isRequestPermissionRequired) {
                val permissionsArrayToBeGranted = permissionsListToBeGranted.toTypedArray()

                // for android < M, we use a custom dialog to request the contacts book access.
                if (permissionsListToBeGranted.contains(Manifest.permission.READ_CONTACTS)
                        && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    AlertDialog.Builder(activity)
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .setTitle(R.string.permissions_rationale_popup_title)
                            .setMessage(R.string.permissions_msg_contacts_warning_other_androids)
                            // gives the contacts book access
                            .setPositiveButton(R.string.yes) { _, _ ->
                                ContactsManager.getInstance().setIsContactBookAccessAllowed(true)
                                fragment?.requestPermissions(permissionsArrayToBeGranted, requestCode)
                                        ?: run {
                                            ActivityCompat.requestPermissions(activity, permissionsArrayToBeGranted, requestCode)
                                        }
                            }
                            // or reject it
                            .setNegativeButton(R.string.no) { _, _ ->
                                ContactsManager.getInstance().setIsContactBookAccessAllowed(false)
                                fragment?.requestPermissions(permissionsArrayToBeGranted, requestCode)
                                        ?: run {
                                            ActivityCompat.requestPermissions(activity, permissionsArrayToBeGranted, requestCode)
                                        }
                            }
                            .show()
                } else {
                    fragment?.requestPermissions(permissionsArrayToBeGranted, requestCode)
                            ?: run {
                                ActivityCompat.requestPermissions(activity, permissionsArrayToBeGranted, requestCode)
                            }
                }
            } else {
                // permissions were granted, start now.
                isPermissionGranted = true
            }
        }
    }

    return isPermissionGranted
}


/**
 * Helper method used in [.checkPermissions] to populate the list of the
 * permissions to be granted (permissionsListToBeGranted_out) and the list of the permissions already denied (permissionAlreadyDeniedList_out).
 *
 * @param activity                        calling activity
 * @param permissionAlreadyDeniedList_out list to be updated with the permissions already denied by the user
 * @param permissionsListToBeGranted_out  list to be updated with the permissions to be granted
 * @param permissionType                  the permission to be checked
 * @return true if the permission requires to be granted, false otherwise
 */
private fun updatePermissionsToBeGranted(activity: Activity,
                                         permissionAlreadyDeniedList_out: MutableList<String>,
                                         permissionsListToBeGranted_out: MutableList<String>,
                                         permissionType: String): Boolean {
    var isRequestPermissionRequested = false

    // add permission to be granted
    permissionsListToBeGranted_out.add(permissionType)

    if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(activity.applicationContext, permissionType)) {
        isRequestPermissionRequested = true

        // add permission to the ones that were already asked to the user
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permissionType)) {
            permissionAlreadyDeniedList_out.add(permissionType)
        }
    }
    return isRequestPermissionRequested
}

/**
 * Helper method to process [.PERMISSIONS_FOR_AUDIO_IP_CALL]
 * on onRequestPermissionsResult() methods.
 *
 * @param context      App context
 * @param grantResults permissions granted results
 * @return true if audio IP call is permitted, false otherwise
 */
fun onPermissionResultAudioIpCall(context: Context, grantResults: IntArray): Boolean {
    val arePermissionsGranted = allGranted(grantResults)

    if (!arePermissionsGranted) {
        Toast.makeText(context, R.string.permissions_action_not_performed_missing_permissions, Toast.LENGTH_SHORT).show()
    }

    return arePermissionsGranted
}

/**
 * Helper method to process [.PERMISSIONS_FOR_VIDEO_IP_CALL]
 * on onRequestPermissionsResult() methods.
 * For video IP calls, record audio and camera permissions are both mandatory.
 *
 * @param context      App context
 * @param grantResults permissions granted results
 * @return true if video IP call is permitted, false otherwise
 */
fun onPermissionResultVideoIpCall(context: Context, grantResults: IntArray): Boolean {
    val arePermissionsGranted = allGranted(grantResults)

    if (!arePermissionsGranted) {
        Toast.makeText(context, R.string.permissions_action_not_performed_missing_permissions, Toast.LENGTH_SHORT).show()
    }

    return arePermissionsGranted
}

/**
 * Return true if all permissions are granted, false if not or if permission request has been cancelled
 */
fun allGranted(grantResults: IntArray): Boolean {
    if (grantResults.isEmpty()) {
        // A cancellation occurred
        return false
    }

    var granted = true

    grantResults.forEach {
        granted = granted && PackageManager.PERMISSION_GRANTED == it
    }

    return granted
}