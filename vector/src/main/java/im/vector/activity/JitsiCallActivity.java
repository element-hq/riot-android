/*
 * Copyright 2017 Vector Creations Ltd
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


import org.jitsi.meet.sdk.JitsiMeetView;
import org.jitsi.meet.sdk.JitsiMeetViewListener;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.Log;

import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.Matrix;
import im.vector.R;
import im.vector.widgets.Widget;
import im.vector.widgets.WidgetsManager;

public class JitsiCallActivity extends RiotAppCompatActivity {
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

    @BindView(R.id.jitsi_progress_layout)
    View mProgressLayout;

    /**
     * Widget events listener
     */
    private final WidgetsManager.onWidgetUpdateListener mWidgetListener = new WidgetsManager.onWidgetUpdateListener() {
        @Override
        public void onWidgetUpdate(Widget widget) {
            if (TextUtils.equals(widget.getWidgetId(), mWidget.getWidgetId())) {
                if (!widget.isActive()) {
                    JitsiCallActivity.this.finish();
                }
            }
        }
    };

    @Override
    @SuppressLint("NewApi")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_jitsi_call);
        ButterKnife.bind(this);

        mWidget = (Widget) getIntent().getSerializableExtra(EXTRA_WIDGET_ID);
        mIsVideoCall = getIntent().getBooleanExtra(EXTRA_ENABLE_VIDEO, true);

        try {
            Uri uri = Uri.parse(mWidget.getUrl());
            String confId = uri.getQueryParameter("confId");
            mCallUrl = JITSI_SERVER_URL + confId;
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onCreate() failed : " + e.getMessage());
            this.finish();
            return;
        }

        mSession = Matrix.getMXSession(this, mWidget.getSessionId());
        if (null == mSession) {
            Log.e(LOG_TAG, "## onCreate() : undefined session ");
            this.finish();
            return;
        }


        mRoom = mSession.getDataHandler().getRoom(mWidget.getRoomId());
        if (null == mRoom) {
            Log.e(LOG_TAG, "## onCreate() : undefined room " + mWidget.getRoomId());
            this.finish();
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
                mProgressLayout.setVisibility(View.VISIBLE);
                WidgetsManager.getSharedInstance().closeWidget(mSession, mRoom, mWidget.getWidgetId(), new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        JitsiCallActivity.this.finish();
                    }

                    private void onError(String errorMessage) {
                        mProgressLayout.setVisibility(View.GONE);
                        CommonActivityUtils.displayToast(JitsiCallActivity.this, errorMessage);
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
                JitsiCallActivity.this.finish();
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
            Log.e(LOG_TAG, "## loadURL() failed : " + e.getMessage());
            this.finish();
        }

        RelativeLayout layout = findViewById(R.id.call_layout);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        layout.setVisibility(View.VISIBLE);
        layout.addView(mJitsiView, 0, params);

        mJitsiView.setListener(new JitsiMeetViewListener() {
            @Override
            public void onConferenceFailed(Map<String, Object> map) {
                Log.e(LOG_TAG, "## onConferenceFailed() : " + map);
                JitsiCallActivity.this.finish();
            }

            @Override
            public void onConferenceJoined(Map<String, Object> map) {
                Log.d(LOG_TAG, "## onConferenceJoined() : " + map);

                JitsiCallActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mConnectingTextView.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onConferenceLeft(Map<String, Object> map) {
                Log.d(LOG_TAG, "## onConferenceLeft() : " + map);
                JitsiCallActivity.this.finish();
            }

            @Override
            public void onConferenceWillJoin(Map<String, Object> map) {
                Log.d(LOG_TAG, "## onConferenceWillJoin() : " + map);

                JitsiCallActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgressLayout.setVisibility(View.GONE);
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
                this.finish();
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

    /**
     * Force to render the activity in fullscreen
     */
    private void displayInFullScreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onResume() {
        super.onResume();

        displayInFullScreen();
        JitsiMeetView.onHostResume(this);
        WidgetsManager.addListener(mWidgetListener);
        refreshStatusBar();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            displayInFullScreen();
        }
    }

    @Override
    public void onBackPressed() {
        if (!JitsiMeetView.onBackPressed()) {
            super.onBackPressed();
        }
    }
}