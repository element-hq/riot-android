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

import android.content.Context
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.widget.ImageView
import im.vector.util.RiotEventDisplay
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.data.RoomState
import org.matrix.androidsdk.data.store.IMXStore
import org.matrix.androidsdk.rest.model.Event
import org.matrix.androidsdk.rest.model.bingrules.BingRule

class NotifiableEventResolver(val context: Context) {

    private val eventDisplay = RiotEventDisplay(context)

    fun resolveEvent(event: Event, roomState: RoomState, bingRule: BingRule, session: MXSession): NotifiableEvent? {
        val store = session.dataHandler.store
        if (store == null) {
            Log.e(LOG_TAG, "## NotifiableEventResolver, unable to get store")
            //TODO notifiy somehow that something did fail?
            return null
        }

        when (event.type) {
            Event.EVENT_TYPE_MESSAGE -> {
                return resolveMessageEvent(event, bingRule, session, store)
            }
            Event.EVENT_TYPE_MESSAGE_ENCRYPTED -> {
                val messageEvent = resolveMessageEvent(event, bingRule, session, store)
                messageEvent?.lockScreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                return messageEvent
            }
            Event.EVENT_TYPE_STATE_ROOM_MEMBER -> {
                return resolveStateRoomEvent(event, bingRule, session)
            }
            else -> {

                //If the event can be displayed, display it as is
                eventDisplay.getTextualDisplay(event, roomState)?.toString()?.let { body ->
                    return SimpleNotifiableEvent(
                            eventId = event.eventId,
                            noisy = bingRule.notificationSound != null,
                            timestamp = event.originServerTs,
                            description = body,
                            soundName = bingRule.notificationSound,
                            title = "New Event",
                            type = event.type)
                }

                //Unsupported event
                Log.i(LOG_TAG, "NotifiableEventResolver Received an unsupported event matching a bing rule")
                return null
            }
        }
    }

    private fun resolveMessageEvent(event: Event, bingRule: BingRule, session: MXSession, store: IMXStore): NotifiableEvent? {
        //If we are here, that means that the event should be notified to the user, we check now how it should be presented (sound)
        val soundName = bingRule.notificationSound
        val noisy = bingRule.notificationSound != null

        //The event only contains an eventId, and roomId (type is m.room.*) , we need to get the displayable content (names, avatar, text, etc...)
        val room = store?.getRoom(event.roomId /*roomID cannot be null (see Matrix SDK code)*/)

        if (room == null) {
            Log.e(LOG_TAG, "## NotifiableEventResolver: Unable to resolve room for eventId [${event.eventId}] and roomID [${event.roomId}]")
            return null
        } else {
            val body = eventDisplay.getTextualDisplay(event, room.state)?.toString() ?: "New Event"
            val roomName = room.getRoomDisplayName(context)
            val senderDisplayName = room.state.getMemberName(event.sender) ?: event.sender ?: ""

            val notifiableEvent = NotifiableMessageEvent(
                    eventId = event.eventId,
                    timestamp = event.originServerTs,
                    noisy = noisy,
                    senderName = senderDisplayName,
                    body = body,
                    roomId = event.roomId,
                    roomName = roomName)

            notifiableEvent.soundName = soundName


            val roomAvatarPath = session.mediaCache?.thumbnailCacheFile(room.avatarUrl, 50)
            if (roomAvatarPath != null) {
                notifiableEvent.roomAvatarPath = roomAvatarPath.path
            } else {
                // prepare for the next time
                session.mediaCache?.loadAvatarThumbnail(session.homeServerConfig, ImageView(context), room.avatarUrl, 50)
            }

            room.state.getMemberByEventId(event.eventId)?.avatarUrl?.let {
                val userAvatarUrlPath =session.mediaCache?.thumbnailCacheFile(it, 50)
                if (userAvatarUrlPath != null) {
                    notifiableEvent.senderAvatarPath = userAvatarUrlPath.path
                } else {
                    // prepare for the next time
                    session.mediaCache?.loadAvatarThumbnail(session.homeServerConfig, ImageView(context), it, 50)
                }
            }

            return notifiableEvent
        }

        return null
    }

    private fun resolveStateRoomEvent(event: Event, bingRule: BingRule, session: MXSession): NotifiableEvent? {
        return null
    }

    companion object {
        private val LOG_TAG = NotifiableEventResolver::class.java.simpleName
    }
}