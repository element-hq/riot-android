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

package im.vector.services

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.support.v4.content.ContextCompat
import android.text.TextUtils
import android.view.WindowManager
import im.vector.Matrix
import im.vector.VectorApp
import im.vector.notifications.NotificationUtils
import im.vector.util.CallsManager
import org.matrix.androidsdk.core.Log

/**
 * Foreground service to manage calls
 */
class CallService : VectorService() {

    /**
     * call in progress (foreground notification)
     */
    private var mCallIdInProgress: String? = null

    /**
     * incoming (foreground notification)
     */
    private var mIncomingCallId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Service started again by the system.
            // TODO What do we do here?
            return START_STICKY
        }

        when (intent.action) {
            ACTION_INCOMING_CALL -> displayIncomingCallNotification(intent)
            ACTION_PENDING_CALL -> displayCallInProgressNotification(intent)
            ACTION_NO_ACTIVE_CALL -> hideCallNotifications()
            else ->
                // Should not happen
                myStopSelf()
        }

        // We want the system to restore the service if killed
        return START_STICKY
    }

    //================================================================================
    // Call notification management
    //================================================================================

    /**
     * Display a permanent notification when there is an incoming call.
     *
     * @param session  the session
     * @param isVideo  true if this is a video call, false for voice call
     * @param room     the room
     * @param callId   the callId
     */
    private fun displayIncomingCallNotification(intent: Intent) {
        Log.d(LOG_TAG, "displayIncomingCallNotification")

        // the incoming call in progress is already displayed
        if (!TextUtils.isEmpty(mIncomingCallId)) {
            Log.d(LOG_TAG, "displayIncomingCallNotification : the incoming call in progress is already displayed")
        } else if (!TextUtils.isEmpty(mCallIdInProgress)) {
            Log.d(LOG_TAG, "displayIncomingCallNotification : a 'call in progress' notification is displayed")
        } else if (null == CallsManager.getSharedInstance().activeCall) {
            val callId = intent.getStringExtra(EXTRA_CALL_ID)

            Log.d(LOG_TAG, "displayIncomingCallNotification : display the dedicated notification")
            val notification = NotificationUtils.buildIncomingCallNotification(
                    this,
                    intent.getBooleanExtra(EXTRA_IS_VIDEO, false),
                    intent.getStringExtra(EXTRA_ROOM_NAME),
                    intent.getStringExtra(EXTRA_MATRIX_ID),
                    callId)
            startForeground(NOTIFICATION_ID, notification)

            mIncomingCallId = callId

            // turn the screen on for 3 seconds
            if (Matrix.getInstance(VectorApp.getInstance())!!.pushManager.isScreenTurnedOn) {
                try {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    val wl = pm.newWakeLock(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            CallService::class.java.simpleName)
                    wl.acquire(3000)
                    wl.release()
                } catch (re: RuntimeException) {
                    Log.i(LOG_TAG, "displayIncomingCallNotification : failed to turn screen on ", re)
                }

            }
        } else {
            Log.i(LOG_TAG, "displayIncomingCallNotification : do not display the incoming call notification because there is a pending call")
        }// test if there is no active call
    }

    /**
     * Display a call in progress notification.
     */
    private fun displayCallInProgressNotification(intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID)

        val notification = NotificationUtils.buildPendingCallNotification(applicationContext,
                intent.getBooleanExtra(EXTRA_IS_VIDEO, false),
                intent.getStringExtra(EXTRA_ROOM_NAME),
                intent.getStringExtra(EXTRA_ROOM_ID),
                intent.getStringExtra(EXTRA_MATRIX_ID),
                callId)

        startForeground(NOTIFICATION_ID, notification)

        mCallIdInProgress = callId
    }

    /**
     * Hide the permanent call notifications
     */
    private fun hideCallNotifications() {
        val notification = NotificationUtils.buildCallEndedNotification(applicationContext)

        // It's mandatory to startForeground to avoid crash
        startForeground(NOTIFICATION_ID, notification)

        myStopSelf()
    }

    companion object {
        private const val LOG_TAG = "CallService"

        private const val NOTIFICATION_ID = 6480

        private const val ACTION_INCOMING_CALL = "im.vector.services.CallService.INCOMING_CALL"
        private const val ACTION_PENDING_CALL = "im.vector.services.CallService.PENDING_CALL"
        private const val ACTION_NO_ACTIVE_CALL = "im.vector.services.CallService.NO_ACTIVE_CALL"

        private const val EXTRA_IS_VIDEO = "EXTRA_IS_VIDEO"
        private const val EXTRA_ROOM_NAME = "EXTRA_ROOM_NAME"
        private const val EXTRA_ROOM_ID = "EXTRA_ROOM_ID"
        private const val EXTRA_MATRIX_ID = "EXTRA_MATRIX_ID"
        private const val EXTRA_CALL_ID = "EXTRA_CALL_ID"

        fun onIncomingCall(context: Context,
                           isVideo: Boolean,
                           roomName: String,
                           roomId: String,
                           matrixId: String,
                           callId: String) {
            val intent = Intent(context, CallService::class.java)
                    .apply {
                        action = ACTION_INCOMING_CALL
                        putExtra(EXTRA_IS_VIDEO, isVideo)
                        putExtra(EXTRA_ROOM_NAME, roomName)
                        putExtra(EXTRA_ROOM_ID, roomId)
                        putExtra(EXTRA_MATRIX_ID, matrixId)
                        putExtra(EXTRA_CALL_ID, callId)
                    }

            ContextCompat.startForegroundService(context, intent)
        }

        fun onPendingCall(context: Context,
                          isVideo: Boolean,
                          roomName: String,
                          roomId: String,
                          matrixId: String,
                          callId: String) {
            val intent = Intent(context, CallService::class.java)
                    .apply {
                        action = ACTION_PENDING_CALL
                        putExtra(EXTRA_IS_VIDEO, isVideo)
                        putExtra(EXTRA_ROOM_NAME, roomName)
                        putExtra(EXTRA_ROOM_ID, roomId)
                        putExtra(EXTRA_MATRIX_ID, matrixId)
                        putExtra(EXTRA_CALL_ID, callId)
                    }

            ContextCompat.startForegroundService(context, intent)
        }

        fun onNoActiveCall(context: Context) {
            val intent = Intent(context, CallService::class.java)
                    .apply {
                        action = ACTION_NO_ACTIVE_CALL
                    }

            ContextCompat.startForegroundService(context, intent)
        }
    }
}