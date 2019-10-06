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

package im.vector.activity

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import im.vector.Matrix
import im.vector.R
import im.vector.VectorApp
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.data.Room

/**
 * JoinRoomActivity is a dummy activity to join / reject a room invitation
 */
class JoinRoomActivity : VectorAppCompatActivity() {

    override fun getLayoutRes() = R.layout.activity_empty

    override fun initUiAndData() {
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID)
        val matrixId = intent.getStringExtra(EXTRA_MATRIX_ID)
        val join = intent.getBooleanExtra(EXTRA_JOIN, false)
        val reject = intent.getBooleanExtra(EXTRA_REJECT, false)

        if (TextUtils.isEmpty(roomId) || TextUtils.isEmpty(matrixId)) {
            Log.e(LOG_TAG, "## onCreate() : invalid parameters")
            finish()
            return
        }

        val session: MXSession? = Matrix.getInstance(applicationContext)!!.getSession(matrixId)

        if (null == session || !session.isAlive) {
            Log.e(LOG_TAG, "## onCreate() : undefined parameters")
            finish()
            return
        }

        val room: Room? = session.dataHandler.getRoom(roomId)

        if (null == room) {
            Log.e(LOG_TAG, "## onCreate() : undefined parameters")
            finish()
            return
        }

        if (join) {
            Log.d(LOG_TAG, "## onCreate() : Join the room $roomId")

            room.join(object : ApiCallback<Void> {
                override fun onSuccess(v: Void?) {
                    Log.d(LOG_TAG, "## onCreate() : join succeeds")

                    // Cancel the notification
                    VectorApp.getInstance().notificationDrawerManager.clearMemberShipNotificationForRoom(roomId)

                    // TODO It should be great to open the just join room, but this callback is not called fast, because
                    // TODO Room waits for initial sync and it can be quite long.
                    /*
                    // Open the just join Room
                    val intent = Intent(this@JoinRoomActivity, VectorRoomActivity::class.java)
                            .putExtra(VectorRoomActivity.EXTRA_ROOM_ID, roomId)
                    startActivity(intent)
                    */
                }

                override fun onNetworkError(e: Exception) {
                    Log.e(LOG_TAG, "## onCreate() : join fails " + e.message, e)
                }

                override fun onMatrixError(e: MatrixError) {
                    Log.e(LOG_TAG, "## onCreate() : join fails " + e.localizedMessage)
                }

                override fun onUnexpectedError(e: Exception) {
                    Log.e(LOG_TAG, "## onCreate() : join fails " + e.message, e)
                }
            })
        } else if (reject) {
            Log.d(LOG_TAG, "## onCreate() : reject the invitation to room $roomId")

            room.leave(object : ApiCallback<Void> {
                override fun onSuccess(info: Void?) {
                    Log.d(LOG_TAG, "## onCreate() : reject succeeds")
                    // Cancel the notification
                    VectorApp.getInstance().notificationDrawerManager.clearMemberShipNotificationForRoom(roomId)
                }

                override fun onNetworkError(e: Exception) {
                    Log.e(LOG_TAG, "## onCreate() : reject fails " + e.message, e)
                }

                override fun onMatrixError(e: MatrixError) {
                    Log.e(LOG_TAG, "## onCreate() : reject fails " + e.localizedMessage)
                }

                override fun onUnexpectedError(e: Exception) {
                    Log.e(LOG_TAG, "## onCreate() : reject fails " + e.message, e)
                }
            })
        }

        finish()
    }

    companion object {
        private val LOG_TAG = JoinRoomActivity::class.java.simpleName

        private const val EXTRA_ROOM_ID = "EXTRA_ROOM_ID"
        private const val EXTRA_MATRIX_ID = "EXTRA_MATRIX_ID"
        // boolean : true to join the room without opening the application
        private const val EXTRA_JOIN = "EXTRA_JOIN"
        // boolean : true to reject the room invitation
        private const val EXTRA_REJECT = "EXTRA_REJECT"

        fun getJoinRoomIntent(context: Context, roomId: String, matrixId: String): Intent {
            return Intent(context, JoinRoomActivity::class.java)
                    .putExtra(EXTRA_ROOM_ID, roomId)
                    .putExtra(EXTRA_MATRIX_ID, matrixId)
                    .putExtra(EXTRA_JOIN, true)
        }

        fun getRejectRoomIntent(context: Context, roomId: String, matrixId: String): Intent {
            return Intent(context, JoinRoomActivity::class.java)
                    .putExtra(EXTRA_ROOM_ID, roomId)
                    .putExtra(EXTRA_MATRIX_ID, matrixId)
                    .putExtra(EXTRA_REJECT, true)
        }
    }
}
