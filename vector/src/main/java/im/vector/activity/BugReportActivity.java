/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

package im.vector.activity;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.util.Log;

import butterknife.BindView;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.util.BugReporter;
import im.vector.util.ThemeUtils;

/**
 * LoggingOutActivity displays an animation while a session log out is in progress.
 */
public class BugReportActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = BugReportActivity.class.getSimpleName();

    @BindView(R.id.bug_report_edit_text)
    EditText mBugReportText;

    @BindView(R.id.bug_report_button_include_logs)
    CheckBox mIncludeLogsButton;

    @BindView(R.id.bug_report_button_include_crash_logs)
    CheckBox mIncludeCrashLogsButton;

    @BindView(R.id.bug_report_button_include_screenshot)
    CheckBox mIncludeScreenShotButton;

    @BindView(R.id.bug_report_progress_view)
    ProgressBar mProgressBar;

    @BindView(R.id.bug_report_progress_text_view)
    TextView mProgressTextView;

    @BindView(R.id.bug_report_scrollview)
    View mScrollView;

    @BindView(R.id.bug_report_mask_view)
    View mMaskView;

    //
    private MenuItem mSendBugReportItem;

    @Override
    public int getLayoutRes() {
        return R.layout.activity_bug_report;
    }

    @Override
    public void initUiAndData() {
        if (null != getSupportActionBar()) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mBugReportText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshSendButton();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.bug_report, menu);

        CommonActivityUtils.tintMenuIcons(menu, ThemeUtils.getColor(this, R.attr.icon_tint_on_dark_action_bar_color));
        mSendBugReportItem = menu.findItem(R.id.ic_action_send_bug_report);

        refreshSendButton();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.ic_action_send_bug_report:
                sendBugReport();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onResume() {
        super.onResume();
        refreshSendButton();
    }

    /**
     * Refresh the send button visibility
     */
    private void refreshSendButton() {
        if ((null != mSendBugReportItem) && (null != mBugReportText)) {
            boolean isValid = (null != mBugReportText.getText()) && (mBugReportText.getText().toString().trim().length() > 10);
            mSendBugReportItem.setEnabled(isValid);
            mSendBugReportItem.getIcon().setAlpha(isValid ? 255 : 100);
        }
    }

    /**
     * Send the bug report
     */
    private void sendBugReport() {
        mScrollView.setAlpha(0.3f);
        mMaskView.setVisibility(View.VISIBLE);
        mSendBugReportItem.setEnabled(false);

        mProgressTextView.setVisibility(View.VISIBLE);
        mProgressTextView.setText(getString(R.string.send_bug_report_progress, 0 + ""));

        mProgressBar.setVisibility(View.VISIBLE);
        mProgressBar.setProgress(0);

        BugReporter.sendBugReport(VectorApp.getInstance(),
                mIncludeLogsButton.isChecked(),
                mIncludeCrashLogsButton.isChecked(),
                mIncludeScreenShotButton.isChecked(),
                mBugReportText.getText().toString(),
                new BugReporter.IMXBugReportListener() {
                    @Override
                    public void onUploadFailed(String reason) {
                        try {
                            if (null != VectorApp.getInstance() && !TextUtils.isEmpty(reason)) {
                                Toast.makeText(VectorApp.getInstance(),
                                        VectorApp.getInstance().getString(R.string.send_bug_report_failed, reason), Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## onUploadFailed() : failed to display the toast " + e.getMessage());
                        }

                        mMaskView.setVisibility(View.GONE);
                        mProgressBar.setVisibility(View.GONE);
                        mProgressTextView.setVisibility(View.GONE);
                        mScrollView.setAlpha(1.0f);
                        mSendBugReportItem.setEnabled(true);
                    }

                    @Override
                    public void onUploadCancelled() {
                        onUploadFailed(null);
                    }

                    @Override
                    public void onProgress(int progress) {
                        if (progress > 100) {
                            Log.e(LOG_TAG, "## onProgress() : progress > 100");
                            progress = 100;
                        } else if (progress < 0) {
                            Log.e(LOG_TAG, "## onProgress() : progress < 0");
                            progress = 0;
                        }

                        mProgressBar.setProgress(progress);
                        mProgressTextView.setText(getString(R.string.send_bug_report_progress, progress + ""));
                    }

                    @Override
                    public void onUploadSucceed() {
                        try {
                            if (null != VectorApp.getInstance()) {
                                Toast.makeText(VectorApp.getInstance(), VectorApp.getInstance().getString(R.string.send_bug_report_sent), Toast.LENGTH_LONG)
                                        .show();
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## onUploadSucceed() : failed to dismiss the toast " + e.getMessage());
                        }

                        try {
                            BugReportActivity.this.finish();
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## onUploadSucceed() : failed to dismiss the dialog " + e.getMessage());
                        }
                    }
                });
    }
}
