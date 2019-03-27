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

import android.support.v7.app.AlertDialog
import im.vector.activity.ShortCodeDeviceVerificationActivity
import org.matrix.androidsdk.crypto.verification.CancelCode
import org.matrix.androidsdk.crypto.verification.SASVerificationTransaction
import org.matrix.androidsdk.crypto.verification.ShortCodeVerificationManager
import org.matrix.androidsdk.crypto.verification.VerificationTransaction

object IncomingVerificationRequestHandler : ShortCodeVerificationManager.ManagerListener {

    private var mAlertDialog: AlertDialog? = null

    override fun transactionCreated(tx: VerificationTransaction) {

    }

    override fun transactionUpdated(tx: VerificationTransaction) {
        if (tx is SASVerificationTransaction) {
            if (tx.state == SASVerificationTransaction.SASVerificationTxState.OnStarted) {
                //We should display a dialog
                onSasTransactionStarted(tx)
            }
        }
    }

    private fun onSasTransactionStarted(tx: SASVerificationTransaction) {
        if (null == VectorApp.getCurrentActivity()) {
            //TODO: wait until an activity is ready
            return
        }
        val activity = VectorApp.getCurrentActivity()

        val session = Matrix.getInstance(activity).defaultSession


        mAlertDialog = AlertDialog.Builder(activity)
                .setMessage(activity.getString(R.string.sas_incoming_verification_request_dialog))
                .setCancelable(false)
                .setNegativeButton(R.string.ignore_request) { dialog, which ->
                    //onDisplayKeyShareDialogClose(false, true)
                    tx.cancel(session, CancelCode.User)
                }.setPositiveButton(R.string.sas_view_request_action
                ) { dialog, id ->
                    val intent = ShortCodeDeviceVerificationActivity.intent(activity, session.myUserId, tx.otherUserID, tx.transactionId)
                    activity.startActivity(intent)
                }
                .setOnCancelListener {
                    tx.cancel(session, CancelCode.User)
                }
                .show()
    }

    fun initialize(mgr: ShortCodeVerificationManager) {
        mgr.addListener(this)
    }
}