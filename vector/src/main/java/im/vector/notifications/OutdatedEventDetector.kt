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
package im.vector.notifications

import android.content.Context
import im.vector.Matrix
import org.matrix.androidsdk.core.Log

class OutdatedEventDetector(val context: Context) {

    /**
     * Returns true if the given event is outdated.
     * Used to clean up notifications if a displayed message has been read on an
     * other device.
     */
    fun isMessageOutdated(notifiableEvent: NotifiableEvent): Boolean {
        if (notifiableEvent is NotifiableMessageEvent) {
            val eventID = notifiableEvent.eventId
            val roomID = notifiableEvent.roomId
            Matrix.getMXSession(context.applicationContext, notifiableEvent.matrixID)?.let { session ->
                //find the room
                if (session.isAlive) {
                    session.dataHandler.getRoom(roomID)?.let { room ->
                        if (room.isEventRead(eventID)) {
                            Log.d(LOG_TAG, "Notifiable Event $eventID is read, and should be removed")
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    companion object {
        private val LOG_TAG = OutdatedEventDetector::class.java.simpleName
    }
}