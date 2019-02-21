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

import android.support.transition.TransitionManager
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import butterknife.BindView
import im.vector.R

/**
 * Simple activity with a toolbar, a waiting overlay, and a fragment container and a mxSession.
 */
open class SimpleFragmentActivity : MXCActionBarActivity() {

    override fun getLayoutRes() = R.layout.activity


    @BindView(R.id.waiting_view_status_text)
    lateinit var waitingStatusText: TextView

    @BindView(R.id.waiting_view_content)
    lateinit var waitingViewParent: ViewGroup

    @BindView(R.id.waiting_view_status_horizontal_progress)
    lateinit var waitingStatusProgress: ProgressBar

    override fun initUiAndData() {
        mSession = getSession(intent)
        configureToolbar()
        waitingView = findViewById(R.id.waiting_view)
    }

    /**
     * Displays a progress indicator with a message to the user.
     * Blocks user interactions.
     */
    fun showWaitingView(status: String) {
        waitingStatusText.text = status
        waitingStatusProgress.isVisible = false
        showWaitingView()
    }

    fun showWaitingViewWithProgress(status: String, progress: Int, progressTotal: Int) {
        waitingStatusProgress.progress = progress
        waitingStatusProgress.max = progressTotal
        waitingStatusProgress.isVisible = true
        waitingStatusText.text = status
        showWaitingView()
    }

    override fun showWaitingView() {
        dismissKeyboard(this)
        waitingStatusText.isGone = waitingStatusText.text.isNullOrBlank()
        super.showWaitingView()
    }

    override fun hideWaitingView() {
        waitingStatusText.text = null
        waitingStatusText.isGone = true
        waitingStatusProgress.progress = 0
        waitingStatusProgress.isVisible = false
        super.hideWaitingView()
    }

    //updates the status while is loading
    fun updateWaitingStatus(status: String) {
        TransitionManager.beginDelayedTransition(waitingViewParent)
        waitingStatusText.text = status
        waitingStatusText.isGone = status.isNullOrBlank()
    }

    fun updateWaitingProgress(visible: Boolean, progress: Int, progressTotal: Int) {
        if (visible) {
            waitingStatusProgress.progress = progress
            waitingStatusProgress.max = progressTotal
            waitingStatusProgress.isVisible = true
        } else {
            waitingStatusProgress.progress = 0
            waitingStatusProgress.isVisible = false
        }
    }

    override fun onBackPressed() {
        if (waitingView!!.isVisible) {
            //ignore
            return
        }
        super.onBackPressed()
    }
}