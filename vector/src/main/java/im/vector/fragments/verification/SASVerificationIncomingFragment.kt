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
package im.vector.fragments.verification

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import butterknife.BindView
import butterknife.OnClick
import im.vector.R
import im.vector.fragments.VectorBaseFragment
import im.vector.util.VectorUtils
import org.matrix.androidsdk.crypto.verification.IncomingSASVerificationTransaction

class SASVerificationIncomingFragment : VectorBaseFragment() {

    companion object {
        fun newInstance() = SASVerificationIncomingFragment()
    }

    @BindView(R.id.sas_incoming_request_user_id)
    lateinit var otherUserIdTextView: TextView


    @BindView(R.id.sas_incoming_request_user_device)
    lateinit var otherDeviceTextView: TextView

    @BindView(R.id.sas_incoming_request_user_avatar)
    lateinit var avatarImageView: ImageView

    override fun getLayoutResId() = R.layout.fragment_sas_verification_incoming_request

    private lateinit var viewModel: SasVerificationViewModel

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(SasVerificationViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        otherUserIdTextView.text = viewModel.otherUserId
        otherDeviceTextView.text = viewModel.otherDevice

        viewModel.otherUser?.let {
            VectorUtils.loadUserAvatar(this.context, viewModel.session, avatarImageView, it.avatarUrl, it.user_id, it.displayname)
        }

        viewModel.transactionState.observe(this, Observer {
            val uxState = (it as? IncomingSASVerificationTransaction)?.uxState
            when (uxState) {
                IncomingSASVerificationTransaction.State.WAIT_FOR_KEY_AGREEMENT -> {
                    viewModel.loadingLiveEvent.value = R.string.sas_waiting_for_partner
                }
                IncomingSASVerificationTransaction.State.SHOW_SAS -> {
                    viewModel.shortCodeReady()
                }
                IncomingSASVerificationTransaction.State.CANCELLED_BY_ME,
                IncomingSASVerificationTransaction.State.CANCELLED_BY_OTHER -> {
                    viewModel.navigateCancel()
                }
            }
        })

    }

    @OnClick(R.id.sas_request_continue_button)
    fun didAccept() {
        viewModel.acceptTransaction()
    }

    @OnClick(R.id.sas_request_cancel_button)
    fun didCancel() {
        viewModel.interrupt()
    }
}