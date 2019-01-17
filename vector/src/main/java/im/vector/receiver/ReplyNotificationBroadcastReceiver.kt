/*
 * Copyright 2016 OpenMarket Ltd
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
package im.vector.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.support.v4.app.RemoteInput
import android.text.TextUtils
import android.widget.Toast
import im.vector.Matrix
import im.vector.VectorApp
import im.vector.notifications.NotifiableMessageEvent
import im.vector.notifications.NotificationUtils
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.crypto.MXCryptoError
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.model.Event
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.rest.model.message.Message
import org.matrix.androidsdk.util.Log

class ReplyNotificationBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return
        Log.d(LOG_TAG, "ReplyNotificationBroadcastReceiver received : $intent")
        val action = intent.action
        if (action != null) {
            when (action) {
                NotificationUtils.SMART_REPLY_ACTION -> {
                    handleSmartReply(intent, context)
                }
                NotificationUtils.DISMISS_ROOM_NOTIF_ACTION -> {
                    intent.getStringExtra(KEY_ROOM_ID)?.let {
                        VectorApp.getInstance().notificationDrawerManager.clearMessageEventOfRoom(it)
                    }
                }
                NotificationUtils.DISMISS_SUMMARY_ACTION -> {
                    VectorApp.getInstance().notificationDrawerManager.clearAllEvents()
                }
                else -> {
                    //nop
                }
            }
        }
    }

    private fun handleSmartReply(intent: Intent?, context: Context?) {
        val message = getReplyMessage(intent)
        val roomId = intent?.getStringExtra(KEY_ROOM_ID)

        if (TextUtils.isEmpty(message) || TextUtils.isEmpty(roomId)) {
            //ignore this event
            //Can this happen? should we update notification?
            return
        }
        val matrixId = intent?.getStringExtra(EXTRA_MATRIX_ID)
        Matrix.getInstance(VectorApp.getInstance().baseContext)?.getSession(matrixId)?.let { session ->
            session?.dataHandler?.getRoom(roomId)?.let { room ->
                sendMatrixEvent(message!!, session, roomId!!, room, context)
            }
        }
        return
    }

    private fun sendMatrixEvent(message: String, session: MXSession, roomId: String, room: Room, context: Context?) {
        val mxMessage = Message()
        mxMessage.msgtype = Message.MSGTYPE_TEXT
        mxMessage.body = message

        val event = Event(mxMessage, session.credentials.userId, roomId)
        room.storeOutgoingEvent(event)
        room.sendEvent(event, object : ApiCallback<Void?> {
            override fun onSuccess(info: Void?) {
                Log.d(LOG_TAG, "Send message : onSuccess ")
                val notifiableMessageEvent = NotifiableMessageEvent(
                        event.eventId,
                        false,
                        System.currentTimeMillis(),
                        session.myUser?.displayname
                                ?: "Me",
                        message,
                        roomId,
                        room.getRoomDisplayName(context))
                notifiableMessageEvent.outGoingMessage = true
                VectorApp.getInstance().notificationDrawerManager.onNotifiableEventReceived(notifiableMessageEvent, session.myUserId, session.myUser.displayname)
                VectorApp.getInstance().notificationDrawerManager.refreshNotificationDrawer()
            }

            override fun onNetworkError(e: Exception) {
                Log.d(LOG_TAG, "Send message : onNetworkError " + e.message, e)
                onSmartReplyFailed(e.localizedMessage)
            }

            override fun onMatrixError(e: MatrixError) {
                Log.d(LOG_TAG, "Send message : onMatrixError " + e.message)
                if (e is MXCryptoError) {
                    Toast.makeText(context, e.detailedErrorDescription, Toast.LENGTH_SHORT).show()
                    onSmartReplyFailed(e.detailedErrorDescription)
                } else {
                    Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    onSmartReplyFailed(e.localizedMessage)
                }
            }

            override fun onUnexpectedError(e: Exception) {
                Log.d(LOG_TAG, "Send message : onUnexpectedError " + e.message, e)
                onSmartReplyFailed(e.message)
            }


            fun onSmartReplyFailed(reason: String?) {
                val notifiableMessageEvent = NotifiableMessageEvent(
                        event.eventId,
                        false,
                        System.currentTimeMillis(),
                        session.myUser?.displayname
                                ?: "Me",
                        message,
                        roomId,
                        room.getRoomDisplayName(context))
                notifiableMessageEvent.outGoingMessage = true
                notifiableMessageEvent.outGoingMessageFailed = true

                VectorApp.getInstance().notificationDrawerManager.onNotifiableEventReceived(notifiableMessageEvent, session.myUserId, session.myUser.displayname)
                VectorApp.getInstance().notificationDrawerManager.refreshNotificationDrawer()
            }
        })
    }


    private fun getReplyMessage(intent: Intent?): String? {
        if (intent != null) {
            val remoteInput = RemoteInput.getResultsFromIntent(intent);
            if (remoteInput != null) {
                return remoteInput.getCharSequence(KEY_TEXT_REPLY)?.toString()
            }
        }
        return null
    }

    companion object {
        val KEY_ROOM_ID = "roomID"
        val KEY_TEXT_REPLY = "key_text_reply"
        val EXTRA_MATRIX_ID = "EXTRA_MATRIX_ID"

        val LOG_TAG = ReplyNotificationBroadcastReceiver::class.java.name
    }
}