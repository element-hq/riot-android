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
package im.vector.notifications

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v4.app.NotificationCompat
import android.text.TextUtils
import org.matrix.androidsdk.util.Log
import java.io.*


data class RoomEventGroupInfo(
        val roomId: String
) {
    var roomDisplayName: String = ""
    var roomAvatarPath: String? = null
    var hasNewEvent: Boolean = false //An event in the list has not yet been display
    var shouldBing: Boolean = false //true if at least one on the not yet displayed event is noisy
    var customSound: String? = null
}

class NotificationDrawerManager(val context: Context) {

    init {
        loadEventInfo()
    }

    private var eventList: MutableList<NotifiableEvent> = ArrayList()
    private var myUserDisplayName: String = ""

    //Keeps a mapping between a notification ID
    //and the list of eventIDs represented by the notification
    //private val notificationToEventsMap: MutableMap<String, List<String>> = HashMap()
    //private val notificationToRoomIdMap: MutableMap<Int, String> = HashMap()

    private var currentRoomId: String? = null


    /*
    * Should be called as soon as a new event is ready to be displayed.
    * The notification corresponding to this event will not be displayed until
    * #refreshNotificationDrawer() is called.
    * Events might be grouped and there might not be one notification per event!
    *
     */
    fun onNotifiableEventReceived(notifiableEvent: NotifiableEvent, userId: String, userDisplayName: String?) {
        //If we support multi session, event list should be per userId
        //Currently only manage single session

        //Log.d(LOG_TAG, "%%%%%%%% onNotifiableEventReceived ${notifiableEvent}")
        synchronized(this) {
            myUserDisplayName = userDisplayName ?: userId
            eventList.add(notifiableEvent)
        }
    }

    /**
     * Should be called when the application is currently opened and showing timeline for the given roomId.
     * Used to ignore events related to that room (no need to display notification) and clean any existing notification on this room.
     */
    fun setCurrentRoom(roomId: String?) {
        var hasChanged = false
        synchronized(this) {
            hasChanged = roomId != currentRoomId
            currentRoomId = roomId
        }
        if (hasChanged) {
            if (roomId != null) {
                eventList.removeAll { e ->
                    if (e is NotifiableMessageEvent) {
                        return@removeAll e.roomId == roomId
                    }
                    return@removeAll false
                }
                NotificationUtils.cancelNotificationMessage(context,roomId, ROOM_MESSAGES_NOTIFICATION_ID)
            }
            refreshNotificationDrawer()
        }
    }


    fun refreshNotificationDrawer() {
        synchronized(this) {

            //Log.d(LOG_TAG, "%%%%%%%% REFRESH NOTIFICATION DRAWER ")
            //TMP code
            var hasNewEvent = false
            var summaryIsNoisy = false

            //group events by room to create a single MessagingStyle notif
            val roomIdToEventMap: MutableMap<String, ArrayList<NotifiableMessageEvent>> = HashMap()
            var notifications: ArrayList<Notification> = ArrayList()

            for (event in eventList) {
                if (event is NotifiableMessageEvent) {
                    val roomId = event.roomId
                    if (!shouldIgnoreEventInRoom(roomId)) {
                        val roomId = roomId
                        var roomEvents = roomIdToEventMap[roomId]
                        if (roomEvents == null) {
                            roomEvents = ArrayList()
                            roomIdToEventMap[roomId] = roomEvents
                        }
                        roomEvents.add(event)
                    }
                }
            }


            //Log.d(LOG_TAG, "%%%%%%%% REFRESH NOTIFICATION DRAWER ${roomIdToEventMap.size} room groups")

            //events have been grouped
            for ((roomId, events) in roomIdToEventMap) {
                val roomGroup = RoomEventGroupInfo(roomId)
                roomGroup.hasNewEvent = false
                roomGroup.shouldBing = false
                val senderDisplayName = events[0].senderName ?: ""
                val roomName = events[0].roomName ?: events[0].senderName ?: ""
                val style = NotificationCompat.MessagingStyle(myUserDisplayName)
                roomGroup.roomDisplayName = roomName
                if (roomName != senderDisplayName) {
                    style.conversationTitle = roomName
                }

                var largeBitmap = getRoomBitmap(events)


                for (event in events) {
                    //if all events in this room have already been displayed there is no need to update it
                    if (!event.hasBeenDisplayed) {
                        roomGroup.shouldBing = roomGroup.shouldBing || event.noisy
                        roomGroup.customSound = event.soundName
                    }
                    roomGroup.hasNewEvent = roomGroup.hasNewEvent || !event.hasBeenDisplayed
                    //TODO update to compat-28 in order to support media and sender as a Person object
                    style.addMessage(event.body, event.timestamp, event.senderName)
                    event.hasBeenDisplayed = true //we can consider it as displayed
                }


                if (roomGroup.hasNewEvent) { //SHould update displayed notification

                    //Log.d(LOG_TAG, "%%%%%%%% REFRESH NOTIFICATION DRAWER ${roomId} need refresh")
                    NotificationUtils.buildMessagesListNotification(context, style, roomGroup, largeBitmap, myUserDisplayName)?.let {
                        //is there an id for this room?
                        notifications.add(it)
                        NotificationUtils.showNotificationMessage(context, roomId, ROOM_MESSAGES_NOTIFICATION_ID, it)
                    }
                    hasNewEvent = true
                    summaryIsNoisy = summaryIsNoisy || roomGroup.shouldBing
                } else {
                    //Log.d(LOG_TAG, "%%%%%%%% REFRESH NOTIFICATION DRAWER ${roomId} is up to date")
                }
            }


            //======== Build summary notification =========
            //On Android 7.0 (API level 24) and higher, the system automatically builds a summary for
            // your group using snippets of text from each notification. The user can expand this
            // notification to see each separate notification, as shown in figure 1.
            // To support older versions, which cannot show a nested group of notifications,
            // you must create an extra notification that acts as the summary.
            // This appears as the only notification and the system hides all the others.
            // So this summary should include a snippet from all the other notifications,
            // which the user can tap to open your app.
            // The behavior of the group summary may vary on some device types such as wearables.
            // To ensure the best experience on all devices and versions, always include a group summary when you create a group
            // https://developer.android.com/training/notify-user/group

            if (roomIdToEventMap.isEmpty()) {
                NotificationUtils.cancelNotificationMessage(context, null, SUMMARY_NOTIFICATION_ID)
            } else {
                val style = NotificationCompat.InboxStyle()
                style.setBigContentTitle("${eventList.size} notifications")
                NotificationUtils.buildSummaryListNotification(
                        context,
                        style, "${eventList.size} notifications",
                        noisy = hasNewEvent && summaryIsNoisy
                )?.let {
                    notifications.add(it)
                    NotificationUtils.showNotificationMessage(context, null, SUMMARY_NOTIFICATION_ID, it)
                }
            }

            saveEventInfo()
        }
    }

    private fun getRoomBitmap(events: ArrayList<NotifiableMessageEvent>): Bitmap? {
        if (events.isEmpty()) return null;

        //Use the last event (most recent?)
        val roomAvatarPath = events[events.size - 1].roomAvatarPath
                ?: events[events.size - 1].senderAvatarPath
        if (!TextUtils.isEmpty(roomAvatarPath)) {
            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            try {
                return BitmapFactory.decodeFile(roomAvatarPath, options)
            } catch (oom: OutOfMemoryError) {
                Log.e(LOG_TAG, "decodeFile failed with an oom", oom)
            }

        }
        return null
    }

    private fun shouldIgnoreEventInRoom(roomId: String?): Boolean {
        return roomId != null && roomId == currentRoomId
    }


    private fun saveEventInfo() {
        if (eventList == null || eventList.isEmpty()) {
            deleteCachedRoomNotifications(context)
            return
        }
        try {
            val file = File(context.applicationContext.cacheDir, ROOMS_NOTIFICATIONS_FILE_NAME)
            val fileOut = FileOutputStream(file)
            val out = ObjectOutputStream(fileOut)
            out.writeObject(eventList)
            out.close()
            fileOut.close()
        } catch (e: Throwable) {
            Log.e(LOG_TAG, "## Failed to save cached notification info", e)
        }
    }

    private fun loadEventInfo() {
        try {
            val file = File(context.applicationContext.cacheDir, ROOMS_NOTIFICATIONS_FILE_NAME)
            if (file.exists()) {
                val fileIn = FileInputStream(file)
                val ois = ObjectInputStream(fileIn)
                val readObject = ois.readObject()
                (readObject as? ArrayList<NotifiableEvent>)?.let {
                    this.eventList = it
                }
            }
        } catch (e: Throwable) {
            Log.e(LOG_TAG, "## Failed to load cached notification info", e)
        }
    }

    private fun deleteCachedRoomNotifications(context: Context) {
        val file = File(context.applicationContext.cacheDir, ROOMS_NOTIFICATIONS_FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
    }

    companion object {
        private const val SUMMARY_NOTIFICATION_ID = 0
        private const val ROOM_MESSAGES_NOTIFICATION_ID = 1

        private const val ROOMS_NOTIFICATIONS_FILE_NAME = "im.vector.notifications.cache"
        private val LOG_TAG = NotificationDrawerManager::class.java.simpleName
    }
}
