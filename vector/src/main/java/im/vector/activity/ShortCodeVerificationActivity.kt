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

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import im.vector.R
import im.vector.activity.util.WaitingViewData
import im.vector.fragments.verification.SASVerificationEmojiFragment
import im.vector.fragments.verification.SASVerificationIncomingFragment
import im.vector.fragments.verification.SASVerificationStartFragment
import im.vector.fragments.verification.SasVerificationViewModel
import org.matrix.androidsdk.crypto.verification.SASVerificationTransaction

class ShortCodeDeviceVerificationActivity : SimpleFragmentActivity() {


    companion object {

        val EXTRA_TRANSACTION_ID = "EXTRA_TRANSACTION_ID"
        val EXTRA_OTHER_USER_ID = "EXTRA_OTHER_USER_ID"

        fun intent(context: Context, matrixID: String, otherUserId: String, transactionID: String): Intent {
            val intent = Intent(context, ShortCodeDeviceVerificationActivity::class.java)
            intent.putExtra(EXTRA_MATRIX_ID, matrixID)
            intent.putExtra(EXTRA_TRANSACTION_ID, transactionID)
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
        viewModel.initSession(mSession, intent.getStringExtra(EXTRA_OTHER_USER_ID), transactionID)

        if (isFirstCreation()) {
            when (viewModel.transactionState.value) {
                SASVerificationTransaction.SASVerificationTxState.OnStarted,
                SASVerificationTransaction.SASVerificationTxState.KeySent,
                SASVerificationTransaction.SASVerificationTxState.Accepted -> {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.container, SASVerificationIncomingFragment.newInstance())
                            .commitNow()
                }
                SASVerificationTransaction.SASVerificationTxState.ShortCodeReady -> {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.container, SASVerificationEmojiFragment.newInstance())
                            .commitNow()
                }
                else -> {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.container, SASVerificationStartFragment.newInstance())
                            .commitNow()
                }
            }
        }

        viewModel.navigateEvent.observe(this, Observer { uxStateEvent ->
            when (uxStateEvent?.getContentIfNotHandled()) {
                SasVerificationViewModel.NAVIGATE_FINISH -> {
                    finish()
                }
                SasVerificationViewModel.NAVIGATE_EMOJI -> {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.container, SASVerificationEmojiFragment.newInstance())
                            .commitNow()
                }
//                KeysBackupRestoreSharedViewModel.NAVIGATE_TO_SUCCESS -> {
//                    supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
//                    supportFragmentManager.beginTransaction()
//                            .replace(R.id.container, KeysBackupRestoreSuccessFragment.newInstance())
//                            .commit()
//                }
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
