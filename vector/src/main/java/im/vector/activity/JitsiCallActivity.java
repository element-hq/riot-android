/*
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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;
import org.jitsi.meet.sdk.JitsiMeetView;
import org.jitsi.meet.sdk.JitsiMeetViewListener;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.Log;

import java.util.Map;

import butterknife.BindView;
import im.vector.Matrix;
import im.vector.R;
import im.vector.widgets.Widget;
import im.vector.widgets.WidgetsManager;
import kotlin.Triple;

public class JitsiCallActivity extends VectorAppCompatActivity {
    private static final String LOG_TAG = JitsiCallActivity.class.getSimpleName();

    /**
     * The linked widget
     */
    public static final String EXTRA_WIDGET_ID = "EXTRA_WIDGET_ID";

    /**
     * set to true to start a video call
     */
    public static final String EXTRA_ENABLE_VIDEO = "EXTRA_ENABLE_VIDEO";

    /**
     * Base server URL
     */
    private static final String JITSI_SERVER_URL = "https://jitsi.riot.im/";

    // permission request code
    private final static int CAN_DRAW_OVERLAY_REQUEST_CODE = 1234;

    // the jitsi view
    private JitsiMeetView mJitsiView = null;

    // the linked widget
    private Widget mWidget = null;

    // video call
    private boolean mIsVideoCall;

    // call URL
    private String mCallUrl;

    // the session
    private MXSession mSession;

    // the room
    private Room mRoom;

    @BindView(R.id.jsti_back_to_app_icon)
    View mBackToAppIcon;

    @BindView(R.id.jsti_close_widget_icon)
    View mCloseWidgetIcon;

    @BindView(R.id.jsti_connecting_text_view)
    View mConnectingTextView;

    /**
     * Widget events listener
     */
    private final WidgetsManager.onWidgetUpdateListener mWidgetListener = new WidgetsManager.onWidgetUpdateListener() {
        @Override
        public void onWidgetUpdate(Widget widget) {
            if (TextUtils.equals(widget.getWidgetId(), mWidget.getWidgetId())) {
                if (!widget.isActive()) {
                    finish();
                }
            }
        }
    };

    @NotNull
    @Override
    public Triple getOtherThemes() {
        return new Triple(R.style.AppTheme_NoActionBar_Dark, R.style.AppTheme_NoActionBar_Black, R.style.AppTheme_NoActionBar_Status);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_jitsi_call;
    }

    @Override
    @SuppressLint("NewApi")
    public void initUiAndData() {
        // Waiting View
        setWaitingView(findViewById(R.id.jitsi_progress_layout));

        mWidget = (Widget) getIntent().getSerializableExtra(EXTRA_WIDGET_ID);
        mIsVideoCall = getIntent().getBooleanExtra(EXTRA_ENABLE_VIDEO, true);

        try {
            Uri uri = Uri.parse(mWidget.getUrl());
            String confId = uri.getQueryParameter("confId");
            mCallUrl = JITSI_SERVER_URL + confId;
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onCreate() failed : " + e.getMessage(), e);
            finish();
            return;
        }

        mSession = Matrix.getMXSession(this, mWidget.getSessionId());
        if (null == mSession) {
            Log.e(LOG_TAG, "## onCreate() : undefined session ");
            finish();
            return;
        }


        mRoom = mSession.getDataHandler().getRoom(mWidget.getRoomId());
        if (null == mRoom) {
            Log.e(LOG_TAG, "## onCreate() : undefined room " + mWidget.getRoomId());
            finish();
            return;
        }

        mJitsiView = new JitsiMeetView(this);

        refreshStatusBar();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, CAN_DRAW_OVERLAY_REQUEST_CODE);
            } else {
                loadURL();
            }
        } else {
            loadURL();
        }
    }

    /**
     * Refresh the status bar
     */
    private void refreshStatusBar() {
        boolean canCloseWidget = (null == WidgetsManager.getSharedInstance().checkWidgetPermission(mSession, mRoom));

        // close widget button
        mCloseWidgetIcon.setVisibility(canCloseWidget ? View.VISIBLE : View.GONE);
        mCloseWidgetIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showWaitingView();
                WidgetsManager.getSharedInstance().closeWidget(mSession, mRoom, mWidget.getWidgetId(), new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        finish();
                    }

                    private void onError(String errorMessage) {
                        hideWaitingView();
                        Toast.makeText(JitsiCallActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        onError(e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        onError(e.getLocalizedMessage());
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        onError(e.getLocalizedMessage());
                    }
                });
            }
        });

        mBackToAppIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    /**
     * Load the jitsi call
     */
    private void loadURL() {
        try {
            Bundle config = new Bundle();
            //config.putBoolean("startWithAudioMuted", true);
            config.putBoolean("startWithVideoMuted", !mIsVideoCall);
            Bundle urlObject = new Bundle();
            urlObject.putBundle("config", config);
            urlObject.putString("url", mCallUrl);
            mJitsiView.loadURLObject(urlObject);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## loadURL() failed : " + e.getMessage(), e);
            finish();
        }

        RelativeLayout layout = findViewById(R.id.call_layout);
        RelativeLayout.LayoutParams params
                = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        layout.setVisibility(View.VISIBLE);
        layout.addView(mJitsiView, 0, params);

        mJitsiView.setListener(new JitsiMeetViewListener() {
            @Override
            public void onConferenceFailed(Map<String, Object> map) {
                Log.e(LOG_TAG, "## onConferenceFailed() : " + map);
                finish();
            }

            @Override
            public void onConferenceJoined(Map<String, Object> map) {
                Log.d(LOG_TAG, "## onConferenceJoined() : " + map);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mConnectingTextView.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onConferenceLeft(Map<String, Object> map) {
                Log.d(LOG_TAG, "## onConferenceLeft() : " + map);
                finish();
            }

            @Override
            public void onConferenceWillJoin(Map<String, Object> map) {
                Log.d(LOG_TAG, "## onConferenceWillJoin() : " + map);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideWaitingView();
                    }
                });
            }

            @Override
            public void onConferenceWillLeave(Map<String, Object> map) {
                Log.d(LOG_TAG, "## onConferenceWillLeave() : " + map);
            }

            @Override
            public void onLoadConfigError(Map<String, Object> data) {
                Log.d(LOG_TAG, "## onLoadConfigError() : " + data);
            }
        });
    }

    @Override
    @SuppressLint("NewApi")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAN_DRAW_OVERLAY_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                loadURL();
            } else {
                Log.e(LOG_TAG, "## onActivityResult() : cannot draw overlay");
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (null != mJitsiView) {
            ViewGroup parent = (ViewGroup) (mJitsiView.getParent());

            if (null != parent) {
                parent.removeView(mJitsiView);
            }

            mJitsiView.dispose();
            mJitsiView = null;
        }

        JitsiMeetView.onHostDestroy(this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        JitsiMeetView.onNewIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        JitsiMeetView.onHostPause(this);
        WidgetsManager.removeListener(mWidgetListener);
    }

    @Override
    public boolean displayInFullscreen() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        JitsiMeetView.onHostResume(this);
        WidgetsManager.addListener(mWidgetListener);
        refreshStatusBar();
    }

    @Override
    public void onBackPressed() {
        if (!JitsiMeetView.onBackPressed()) {
            super.onBackPressed();
        }
    }
}