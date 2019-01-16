/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.push.fcm

import android.os.Handler
import android.os.Looper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.JsonParser
import im.vector.BuildConfig
import im.vector.Matrix
import im.vector.VectorApp
import im.vector.activity.CommonActivityUtils
import im.vector.services.EventStreamService
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.data.store.IMXStore
import org.matrix.androidsdk.rest.model.Event
import org.matrix.androidsdk.util.Log

/**
 * Class extending FirebaseMessagingService.
 */
class VectorFirebaseMessagingService : FirebaseMessagingService() {

    // Tells if the events service running state has been tested
    private var mCheckLaunched: Boolean = false

    // UI handler
    private val mUIHandler by lazy {
        Handler(Looper.getMainLooper())
    }

    /**
     * Called when message is received.
     *
     * @param message the message
     */
    override fun onMessageReceived(message: RemoteMessage?) {
        if (message == null || message.data == null) return
        if (BuildConfig.DEBUG) {
            Log.i(LOG_TAG, "%%%%%%%% :" + message.data.toString())
            Log.i(LOG_TAG, "%%%%%%%%  ## onMessageReceived() from FCM with priority " + message.priority)
        }

        //TODO if the app is in foreground, we could just ignore this. The sync loop is already going?

        // Ensure event stream service is started
        if (EventStreamService.getInstance() == null) {
            CommonActivityUtils.startEventStreamService(this)
        }

        mUIHandler.post { onMessageReceivedInternal(message.data) }
    }

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is also called
     * when the InstanceID token is initially generated, so this is where
     * you retrieve the token.
     */
    override fun onNewToken(refreshedToken: String?) {
        Log.i(LOG_TAG, "onNewToken: FCM Token has been updated")
        FcmHelper.storeFcmToken(this, refreshedToken)
        Matrix.getInstance(this)?.pushManager?.resetFCMRegistration(refreshedToken)
    }

    override fun onDeletedMessages() {
        Log.d(LOG_TAG, "## onDeletedMessages()")
    }

    /**
     * Internal receive method
     *
     * @param data Data map containing message data as key/value pairs.
     * For Set of keys use data.keySet().
     */
    private fun onMessageReceivedInternal(data: Map<String, String>) {
        try {
            var unreadCount = 0
            var roomId: String? = null
            var eventId: String? = null

            if (null != data && data.containsKey("unread")) {
                if (data.containsKey("unread")) {
                    unreadCount = Integer.parseInt(data["unread"])
                }

                if (data.containsKey("room_id")) {
                    roomId = data["room_id"]
                }

                if (data.containsKey("event_id")) {
                    eventId = data["event_id"]
                }
            }

            Log.i(LOG_TAG, "## onMessageReceivedInternal() : roomId $roomId eventId $eventId unread $unreadCount")

            // update the badge counter
            CommonActivityUtils.updateBadgeCount(applicationContext, unreadCount)

            val pushManager = Matrix.getInstance(applicationContext).pushManager

            if (!pushManager.areDeviceNotificationsAllowed()) {
                Log.i(LOG_TAG, "## onMessageReceivedInternal() : the notifications are disabled")
                return
            }


            val session = Matrix.getInstance(applicationContext)?.defaultSession
            val store = session?.dataHandler?.store

            if (VectorApp.isAppInBackground() && !pushManager.isBackgroundSyncAllowed) {
                //Notification contains metadata and data information
                handleNotificationWithoutSyncingMode(data, roomId, session, store, unreadCount)
                return
            }

            // check if the application has been launched once
            // the first FCM event could have been triggered whereas the application is not yet launched.
            // so it is required to create the sessions and to start/resume event stream
            if (!mCheckLaunched && null != session) {
                CommonActivityUtils.startEventStreamService(this)
                mCheckLaunched = true
            }

            // check if the event was not yet received
            // a previous catchup might have already retrieved the notified event
            if (null != eventId && null != roomId) {
                try {
                    val sessions = Matrix.getInstance(applicationContext)!!.sessions

                    if (null != sessions && !sessions.isEmpty()) {
                        for (session in sessions) {
                            if (session.dataHandler?.store?.isReady == true) {
                                if (null != session.dataHandler.store!!.getEvent(eventId, roomId)) {
                                    Log.e(LOG_TAG, "## onMessageReceivedInternal() : ignore the event " + eventId
                                            + " in room " + roomId + " because it is already known")
                                    return
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "## onMessageReceivedInternal() : failed to check if the event was already defined " + e.message, e)
                }

            }

            CommonActivityUtils.catchupEventStream(this)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "## onMessageReceivedInternal() failed : " + e.message, e)
        }

    }

    fun handleNotificationWithoutSyncingMode(data: Map<String, String>, roomId: String?, session: MXSession?, store: IMXStore?, unreadCount: Int) {
        val eventStreamService = EventStreamService.getInstance()
        val event = parseEvent(data)

        var roomName: String? = data["room_name"]
        if (null == roomName && null != roomId) {
            // Try to get the room name from our store
            if (null != session && store != null && store.isReady) {
                val room = store.getRoom(roomId)
                if (null != room) {
                    roomName = room.getRoomDisplayName(this)
                }
            }
        }
//        VectorApp.getInstance().notificationDrawerManager.onNotifiableEventReceived()
        Log.i(LOG_TAG, "## onMessageReceivedInternal() : the background sync is disabled with eventStreamService " + eventStreamService!!)

        EventStreamService.onStaticNotifiedEvent(applicationContext, event, roomName, data["sender_display_name"], unreadCount)
    }

    /**
     * Try to create an event from the FCM data
     *
     * @param data the FCM data
     * @return the event
     */
    private fun parseEvent(data: Map<String, String>?): Event? {
        // accept only event with room id.
        if (null == data || !data.containsKey("room_id") || !data.containsKey("event_id")) {
            return null
        }

        try {
            val event = Event()
            event.eventId = data["event_id"]
            event.sender = data["sender"]
            event.roomId = data["room_id"]
            event.setType(data["type"])

            if (data.containsKey("content")) {
                event.updateContent(JsonParser().parse(data["content"]).asJsonObject)
            }

            return event
        } catch (e: Exception) {
            Log.e(LOG_TAG, "buildEvent fails " + e.localizedMessage, e)
        }

        return null
    }

    companion object {
        private val LOG_TAG = VectorFirebaseMessagingService::class.java.simpleName
    }
}
