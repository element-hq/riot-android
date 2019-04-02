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
import android.support.transition.TransitionManager
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import butterknife.BindView
import butterknife.OnClick
import im.vector.R
import im.vector.activity.CommonActivityUtils
import im.vector.fragments.VectorBaseFragment
import im.vector.listeners.YesNoListener
import org.matrix.androidsdk.crypto.data.MXDeviceInfo
import org.matrix.androidsdk.crypto.verification.OutgoingSASVerificationRequest
import org.matrix.androidsdk.rest.callback.SimpleApiCallback

class SASVerificationStartFragment : VectorBaseFragment() {

    companion object {
        fun newInstance() = SASVerificationStartFragment()
    }

    override fun getLayoutResId() = R.layout.fragment_sas_verification_start

    private lateinit var viewModel: SasVerificationViewModel


    @BindView(R.id.rootLayout)
    lateinit var rootLayout: ViewGroup

    @BindView(R.id.sas_start_button)
    lateinit var startButton: Button

    @BindView(R.id.sas_start_button_loading)
    lateinit var startButtonLoading: ProgressBar

    @BindView(R.id.sas_verifying_keys)
    lateinit var loadingText: TextView


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(SasVerificationViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        viewModel.transactionState.observe(this, Observer {
            val uxState = (viewModel.transaction as? OutgoingSASVerificationRequest)?.uxState
            when (uxState) {
                OutgoingSASVerificationRequest.State.WAIT_FOR_KEY_AGREEMENT -> {
                    //display loading
                    TransitionManager.beginDelayedTransition(this.rootLayout)
                    this.loadingText.isGone = false
                    this.startButton.isInvisible = true
                    this.startButton.isEnabled = false
                    this.startButtonLoading.isVisible = true
                    this.startButtonLoading.animate()

                }
                OutgoingSASVerificationRequest.State.SHOW_SAS -> {
                    viewModel.shortCodeReady()
                }
                OutgoingSASVerificationRequest.State.CANCELLED_BY_ME,
                OutgoingSASVerificationRequest.State.CANCELLED_BY_OTHER -> {
                    viewModel.navigateCancel()
                }
                else -> {
                    TransitionManager.beginDelayedTransition(this.rootLayout)
                    this.loadingText.isGone = true
                    this.startButton.isVisible = true
                    this.startButton.isEnabled = true
                    this.startButtonLoading.isGone = true
                }
            }
        })

    }

    @OnClick(R.id.sas_start_button)
    fun doStart() {
        viewModel.beginSasKeyVerification()
    }

    @OnClick(R.id.sas_legacy_verification)
    fun doLegacy() {
        viewModel.session.crypto?.getDeviceInfo(viewModel.otherUserId ?: "", viewModel.otherDevice
                ?: "", object : SimpleApiCallback<MXDeviceInfo>() {
            override fun onSuccess(info: MXDeviceInfo?) {
                info?.let {

                    CommonActivityUtils.displayDeviceVerificationDialogLegacy<Any>(it, it.userId, viewModel.session, activity, object : YesNoListener {
                        override fun yes() {
                            viewModel.finishSuccess()
                        }

                        override fun no() {

                        }
                    })
                }
            }
        })
    }

    @OnClick(R.id.sas_cancel_button)
    fun doCancel() {
        viewModel.interrupt()
    }


}