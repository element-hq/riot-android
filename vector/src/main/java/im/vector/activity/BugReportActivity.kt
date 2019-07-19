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

import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.core.view.isVisible
import androidx.core.widget.toast
import butterknife.BindView
import butterknife.OnCheckedChanged
import butterknife.OnTextChanged
import im.vector.R
import im.vector.VectorApp
import im.vector.util.BugReporter
import org.matrix.androidsdk.core.Log

/**
 * Form to send a bug report
 */
class BugReportActivity : MXCActionBarActivity() {

    /* ==========================================================================================
     * UI
     * ========================================================================================== */

    @BindView(R.id.bug_report_edit_text)
    lateinit var mBugReportText: EditText

    @BindView(R.id.bug_report_button_include_logs)
    lateinit var mIncludeLogsButton: CheckBox

    @BindView(R.id.bug_report_button_include_crash_logs)
    lateinit var mIncludeCrashLogsButton: CheckBox

    @BindView(R.id.bug_report_button_include_screenshot)
    lateinit var mIncludeScreenShotButton: CheckBox

    @BindView(R.id.bug_report_screenshot_preview)
    lateinit var mScreenShotPreview: ImageView

    @BindView(R.id.bug_report_progress_view)
    lateinit var mProgressBar: ProgressBar

    @BindView(R.id.bug_report_progress_text_view)
    lateinit var mProgressTextView: TextView

    @BindView(R.id.bug_report_scrollview)
    lateinit var mScrollView: View

    @BindView(R.id.bug_report_mask_view)
    lateinit var mMaskView: View

    override fun getLayoutRes() = R.layout.activity_bug_report

    override fun initUiAndData() {
        configureToolbar()

        if (BugReporter.getScreenshot() != null) {
            mScreenShotPreview.setImageBitmap(BugReporter.getScreenshot())
        } else {
            mScreenShotPreview.isVisible = false
            mIncludeScreenShotButton.isChecked = false
            mIncludeScreenShotButton.isEnabled = false
        }
    }

    override fun getMenuRes() = R.menu.bug_report

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.ic_action_send_bug_report)?.let {
            val isValid = mBugReportText.text.toString().trim().length > 10
                    && !mMaskView.isVisible

            it.isEnabled = isValid
            it.icon.alpha = if (isValid) 255 else 100
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.ic_action_send_bug_report -> {
                sendBugReport()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    /**
     * Send the bug report
     */
    private fun sendBugReport() {
        mScrollView.alpha = 0.3f
        mMaskView.isVisible = true

        invalidateOptionsMenu()

        mProgressTextView.isVisible = true
        mProgressTextView.text = getString(R.string.send_bug_report_progress, 0.toString() + "")

        mProgressBar.isVisible = true
        mProgressBar.progress = 0

        BugReporter.sendBugReport(VectorApp.getInstance(),
                mIncludeLogsButton.isChecked,
                mIncludeCrashLogsButton.isChecked,
                mIncludeScreenShotButton.isChecked,
                mBugReportText.text.toString(),
                object : BugReporter.IMXBugReportListener {
                    override fun onUploadFailed(reason: String?) {
                        try {
                            if (null != VectorApp.getInstance() && !TextUtils.isEmpty(reason)) {
                                Toast.makeText(VectorApp.getInstance(),
                                        VectorApp.getInstance().getString(R.string.send_bug_report_failed, reason), Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Log.e(LOG_TAG, "## onUploadFailed() : failed to display the toast " + e.message, e)
                        }

                        mMaskView.isVisible = false
                        mProgressBar.isVisible = false
                        mProgressTextView.isVisible = false
                        mScrollView.alpha = 1.0f

                        invalidateOptionsMenu()
                    }

                    override fun onUploadCancelled() {
                        onUploadFailed(null)
                    }

                    override fun onProgress(progress: Int) {
                        var progress = progress
                        if (progress > 100) {
                            Log.e(LOG_TAG, "## onProgress() : progress > 100")
                            progress = 100
                        } else if (progress < 0) {
                            Log.e(LOG_TAG, "## onProgress() : progress < 0")
                            progress = 0
                        }

                        mProgressBar.progress = progress
                        mProgressTextView.text = getString(R.string.send_bug_report_progress, progress.toString() + "")
                    }

                    override fun onUploadSucceed() {
                        try {
                            VectorApp.getInstance()?.toast(R.string.send_bug_report_sent, Toast.LENGTH_LONG)
                        } catch (e: Exception) {
                            Log.e(LOG_TAG, "## onUploadSucceed() : failed to dismiss the toast " + e.message, e)
                        }

                        try {
                            finish()
                        } catch (e: Exception) {
                            Log.e(LOG_TAG, "## onUploadSucceed() : failed to dismiss the dialog " + e.message, e)
                        }

                    }
                })
    }

    /* ==========================================================================================
     * UI Event
     * ========================================================================================== */

    @OnTextChanged(R.id.bug_report_edit_text)
    internal fun textChanged() {
        invalidateOptionsMenu()
    }

    @OnCheckedChanged(R.id.bug_report_button_include_screenshot)
    internal fun onSendScreenshotChanged() {
        mScreenShotPreview.isVisible = mIncludeScreenShotButton.isChecked && BugReporter.getScreenshot() != null
    }

    override fun onBackPressed() {
        // Ensure there is no crash status remaining, which will be sent later on by mistake
        BugReporter.deleteCrashFile(this)

        super.onBackPressed()
    }

    /* ==========================================================================================
     * Companion
     * ========================================================================================== */

    companion object {
        private val LOG_TAG = BugReportActivity::class.java.simpleName
    }
}
