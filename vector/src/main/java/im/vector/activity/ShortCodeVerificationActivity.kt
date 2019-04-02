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
package im.vector.activity

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.support.v7.app.AlertDialog
import im.vector.R
import im.vector.activity.util.WaitingViewData
import im.vector.fragments.verification.*
import org.matrix.androidsdk.crypto.verification.CancelCode
import org.matrix.androidsdk.crypto.verification.IncomingSASVerificationTransaction
import org.matrix.androidsdk.crypto.verification.OutgoingSASVerificationRequest
import org.matrix.androidsdk.crypto.verification.SASVerificationTransaction

class ShortCodeDeviceVerificationActivity : SimpleFragmentActivity() {


    companion object {

        val EXTRA_TRANSACTION_ID = "EXTRA_TRANSACTION_ID"
        val EXTRA_OTHER_USER_ID = "EXTRA_OTHER_USER_ID"
        val EXTRA_OTHER_DEVICE_ID = "EXTRA_OTHER_DEVICE_ID"

        fun intent(context: Context, matrixID: String, otherUserId: String, transactionID: String): Intent {
            val intent = Intent(context, ShortCodeDeviceVerificationActivity::class.java)
            intent.putExtra(EXTRA_MATRIX_ID, matrixID)
            intent.putExtra(EXTRA_TRANSACTION_ID, transactionID)
            intent.putExtra(EXTRA_OTHER_USER_ID, otherUserId)
            return intent
        }

        fun outgoingIntent(context: Context, matrixID: String, otherUserId: String, otherDeviceId: String): Intent {
            val intent = Intent(context, ShortCodeDeviceVerificationActivity::class.java)
            intent.putExtra(EXTRA_MATRIX_ID, matrixID)
            intent.putExtra(EXTRA_OTHER_DEVICE_ID, otherDeviceId)
            intent.putExtra(EXTRA_OTHER_USER_ID, otherUserId)
            return intent
        }
    }

    override fun getTitleRes() = R.string.title_activity_verify_device


    private lateinit var viewModel: SasVerificationViewModel

    override fun initUiAndData() {
        super.initUiAndData()
        viewModel = ViewModelProviders.of(this).get(SasVerificationViewModel::class.java)
        var transactionID: String? = null

        if (intent.hasExtra(EXTRA_TRANSACTION_ID)) {
            transactionID = intent.getStringExtra(EXTRA_TRANSACTION_ID)
        }

        if (transactionID != null) {
            viewModel.initSession(mSession, intent.getStringExtra(EXTRA_OTHER_USER_ID), transactionID)
        } else {
            viewModel.initOutgoing(mSession, intent.getStringExtra(EXTRA_OTHER_USER_ID), intent.getStringExtra(EXTRA_OTHER_DEVICE_ID))
        }

        if (isFirstCreation()) {

            if (viewModel.transaction == null) {
                //can only be a non started outgoing
                supportFragmentManager.beginTransaction()
                        .replace(R.id.container, SASVerificationStartFragment.newInstance())
                        .commitNow()
            } else if (viewModel.transaction is IncomingSASVerificationTransaction) {
                val incoming = viewModel.transaction as IncomingSASVerificationTransaction
                when (incoming.uxState) {
                    IncomingSASVerificationTransaction.State.UNKNOWN,
                    IncomingSASVerificationTransaction.State.SHOW_ACCEPT,
                    IncomingSASVerificationTransaction.State.WAIT_FOR_KEY_AGREEMENT -> {
                        supportActionBar?.setTitle(R.string.sas_incoming_request_title)
                        supportFragmentManager.beginTransaction()
                                .replace(R.id.container, SASVerificationIncomingFragment.newInstance())
                                .commitNow()
                    }
                    IncomingSASVerificationTransaction.State.WAIT_FOR_VERIFICATION,
                    IncomingSASVerificationTransaction.State.SHOW_SAS -> {
                        supportFragmentManager.beginTransaction()
                                .replace(R.id.container, SASVerificationShortCodeFragment.newInstance())
                                .commitNow()
                    }
                    IncomingSASVerificationTransaction.State.VERIFIED -> {
                        supportFragmentManager.beginTransaction()
                                .replace(R.id.container, SASVerificationVerifiedFragment.newInstance())
                                .commitNow()
                    }
                    IncomingSASVerificationTransaction.State.CANCELLED_BY_ME,
                    IncomingSASVerificationTransaction.State.CANCELLED_BY_OTHER -> {
                        viewModel.navigateCancel()
                    }
                }
            } else {
                val outgoing = viewModel.transaction as OutgoingSASVerificationRequest
                when (outgoing.uxState) {
                    OutgoingSASVerificationRequest.State.UNKNOWN,
                    OutgoingSASVerificationRequest.State.WAIT_FOR_START,
                    OutgoingSASVerificationRequest.State.WAIT_FOR_KEY_AGREEMENT -> {

                    }
                    OutgoingSASVerificationRequest.State.SHOW_SAS,
                    OutgoingSASVerificationRequest.State.WAIT_FOR_VERIFICATION -> {
                        supportFragmentManager.beginTransaction()
                                .replace(R.id.container, SASVerificationShortCodeFragment.newInstance())
                                .commitNow()
                    }
                    OutgoingSASVerificationRequest.State.VERIFIED -> {
                        supportFragmentManager.beginTransaction()
                                .replace(R.id.container, SASVerificationVerifiedFragment.newInstance())
                                .commitNow()
                    }
                    IncomingSASVerificationTransaction.State.CANCELLED_BY_ME,
                    IncomingSASVerificationTransaction.State.CANCELLED_BY_OTHER -> {
                        viewModel.navigateCancel()
                    }
                }
            }
        }

        viewModel.navigateEvent.observe(this, Observer { uxStateEvent ->
            when (uxStateEvent?.getContentIfNotHandled()) {
                SasVerificationViewModel.NAVIGATE_FINISH -> {
                    Activity.RESULT_CANCELED
                    finish()
                }
                SasVerificationViewModel.NAVIGATE_FINISH_SUCCESS -> {
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                SasVerificationViewModel.NAVIGATE_EMOJI -> {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.container, SASVerificationShortCodeFragment.newInstance())
                            .commitNow()
                }
                SasVerificationViewModel.NAVIGATE_SUCCESS -> {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.container, SASVerificationVerifiedFragment.newInstance())
                            .commitNow()
                }
                SasVerificationViewModel.NAVIGATE_CANCELLED -> {
                    val isCancelledByMe = viewModel.transaction?.state == SASVerificationTransaction.SASVerificationTxState.Cancelled
                    val humanReadableReason = when (viewModel.transaction?.cancelledReason) {
                        CancelCode.User -> getString(R.string.sas_error_m_user)
                        CancelCode.Timeout -> getString(R.string.sas_error_m_user)
                        CancelCode.UnknownTransaction -> getString(R.string.sas_error_m_unknown_transaction)
                        CancelCode.UnknownMethod -> getString(R.string.sas_error_m_unknown_method)
                        CancelCode.MismatchedCommitment -> getString(R.string.sas_error_m_mismatched_commitment)
                        CancelCode.MismatchedSas -> getString(R.string.sas_error_m_mismatched_sas)
                        CancelCode.UnexpectedMessage -> getString(R.string.sas_error_m_unexpected_message)
                        CancelCode.InvalidMessage -> getString(R.string.sas_error_m_invalid_message)
                        CancelCode.MismatchedKeys -> getString(R.string.sas_error_m_key_mismatch)
                        CancelCode.UserMismatchError -> getString(R.string.sas_error_m_user_error)
                        null -> getString(R.string.sas_error_unknown)
                    }
                    val message =
                            if (isCancelledByMe) getString(R.string.sas_cancelled_by_me, humanReadableReason)
                            else getString(R.string.sas_cancelled_by_other, humanReadableReason)
                    //Show a dialog
                    AlertDialog.Builder(this)
                            .setTitle(R.string.sas_cancelled_dialog_title)
                            .setMessage(message)
                            .setCancelable(false)
                            .setPositiveButton(R.string.ok) { _, _ ->
                                //nop
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            }
                            .show()
                }
            }
        })

        viewModel.loadingLiveEvent.observe(this, Observer {
            if (it == null) {
                hideWaitingView()
            } else {
                val status = if (it == -1) "" else getString(it)
                updateWaitingView(WaitingViewData(status, isIndeterminate = true))
            }
        })
    }
}
