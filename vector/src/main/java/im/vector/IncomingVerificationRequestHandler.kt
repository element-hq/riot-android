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
package im.vector

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AlertDialog
import android.widget.ImageView
import im.vector.activity.ShortCodeDeviceVerificationActivity
import im.vector.notifications.NotificationUtils
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.crypto.verification.CancelCode
import org.matrix.androidsdk.crypto.verification.SASVerificationTransaction
import org.matrix.androidsdk.crypto.verification.VerificationManager
import org.matrix.androidsdk.crypto.verification.VerificationTransaction
import org.matrix.androidsdk.util.Log

/**
 * Listens to the VerificationManager and a new notification when an incoming request is detected.
 */
object IncomingVerificationRequestHandler : VerificationManager.ManagerListener {


    override fun transactionCreated(tx: VerificationTransaction) {}

    override fun transactionUpdated(tx: VerificationTransaction) {
        if (tx is SASVerificationTransaction) {
            when (tx.state) {
                SASVerificationTransaction.SASVerificationTxState.OnStarted -> {
                    //Add a notification for every incoming request
                    val context = VectorApp.getInstance()
                    val session = Matrix.getInstance(context).defaultSession
                    val name = session.dataHandler.getUser(tx.otherUserID)?.displayname ?: tx.otherUserID
                    NotificationUtils.buildIncomingKeyVerificationNotification(context, tx.otherUserID, name, tx.transactionId, getUserAvatarBitmap(session,tx.otherUserID))?.let {
                        NotificationUtils.showNotificationMessage(context, tx.transactionId, 100, it)
                    }
                }
                SASVerificationTransaction.SASVerificationTxState.Cancelled,
                SASVerificationTransaction.SASVerificationTxState.OnCancelled,
                SASVerificationTransaction.SASVerificationTxState.Verified -> {
                    //cancel related notification
                    NotificationUtils.cancelNotificationMessage(VectorApp.getInstance(),tx.transactionId,100)
                }
            }
        }
    }

    fun initialize(mgr: VerificationManager) {
        mgr.addListener(this)
    }

    private fun getUserAvatarBitmap(session: MXSession, userId: String): Bitmap? {

        session.dataHandler.getUser(userId)?.avatarUrl?.let {
            val userAvatarUrlPath = session.mediaCache?.thumbnailCacheFile(it, 40)
            if (userAvatarUrlPath != null) {
                val options = BitmapFactory.Options()
                options.inPreferredConfig = Bitmap.Config.ARGB_8888
                try {
                    return BitmapFactory.decodeFile(userAvatarUrlPath.absolutePath, options)
                } catch (oom: OutOfMemoryError) {
                    Log.e(IncomingVerificationRequestHandler::class.simpleName, "decodeFile failed with an oom", oom)
                }

            } else {
                // prepare for the next time
                session.mediaCache?.loadAvatarThumbnail(session.homeServerConfig, ImageView(VectorApp.getInstance()), it, 40)
            }
        }
        return null
    }
}