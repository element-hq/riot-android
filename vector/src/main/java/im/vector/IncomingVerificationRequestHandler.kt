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

import im.vector.activity.SASVerificationActivity
import org.matrix.androidsdk.crypto.verification.CancelCode
import org.matrix.androidsdk.crypto.verification.SASVerificationTransaction
import org.matrix.androidsdk.crypto.verification.VerificationManager
import org.matrix.androidsdk.crypto.verification.VerificationTransaction

/**
 * Listens to the VerificationManager and a new notification when an incoming request is detected.
 */
object IncomingVerificationRequestHandler : VerificationManager.VerificationManagerListener {


    override fun transactionCreated(tx: VerificationTransaction) {}

    override fun transactionUpdated(tx: VerificationTransaction) {
        if (tx is SASVerificationTransaction) {
            when (tx.state) {
                SASVerificationTransaction.SASVerificationTxState.OnStarted -> {
                    //Add a notification for every incoming request
                    val context = VectorApp.getInstance()
                    val session = Matrix.getInstance(context).defaultSession
                    val name = session.dataHandler.getUser(tx.otherUserId)?.displayname
                            ?: tx.otherUserId

                    val alert = PopupAlertManager.VectorAlert(
                            "kvr_${tx.transactionId}",
                            context.getString(R.string.sas_incoming_request_notif_title),
                            context.getString(R.string.sas_incoming_request_notif_content, name),
                            R.drawable.shield
                    ).apply {
                        contentAction = Runnable {
                            val intent = SASVerificationActivity.incomingIntent(context,
                                    session.myUserId,
                                    tx.otherUserId,
                                    tx.transactionId)
                            weakCurrentActivity?.get()?.startActivity(intent)
                        }
                        dismissedAction = Runnable {
                            tx.cancel(session, CancelCode.User)
                        }
                        addButton(
                                context.getString(R.string.ignore),
                                Runnable {
                                    tx.cancel(session, CancelCode.User)
                                }
                        )
                        addButton(
                                context.getString(R.string.action_open),
                                Runnable {
                                    val intent = SASVerificationActivity.incomingIntent(context,
                                            session.myUserId,
                                            tx.otherUserId,
                                            tx.transactionId)
                                    weakCurrentActivity?.get()?.startActivity(intent)
                                }
                        )
                        //10mn expiration
                        expirationTimestamp = System.currentTimeMillis() + (10 * 60 * 1000L)

                    }
                    PopupAlertManager.postVectorAlert(alert)
                }
                SASVerificationTransaction.SASVerificationTxState.Cancelled,
                SASVerificationTransaction.SASVerificationTxState.OnCancelled,
                SASVerificationTransaction.SASVerificationTxState.Verified -> {
                    //cancel related notification
                    PopupAlertManager.cancelAlert("kvr_${tx.transactionId}")
                }
                else -> Unit
            }
        }
    }

    fun initialize(verificationManager: VerificationManager) {
        verificationManager.addListener(this)
    }

    override fun markedAsManuallyVerified(userId: String, deviceId: String) {

    }
}