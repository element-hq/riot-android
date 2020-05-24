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
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.facebook.react.modules.core.PermissionListener;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jitsi.meet.sdk.JitsiMeetActivityDelegate;
import org.jitsi.meet.sdk.JitsiMeetActivityInterface;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import org.jitsi.meet.sdk.JitsiMeetUserInfo;
import org.jitsi.meet.sdk.JitsiMeetView;
import org.jitsi.meet.sdk.JitsiMeetViewListener;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.data.Room;

import java.net.URL;
import java.util.Map;

import butterknife.BindView;
import im.vector.Matrix;
import im.vector.R;
import im.vector.widgets.Widget;
import im.vector.widgets.WidgetManagerProvider;
import im.vector.widgets.WidgetsManager;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Inspired from JitsiMeetActivity
 */
public class JitsiCallActivity extends VectorAppCompatActivity implements JitsiMeetActivityInterface {
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
    public static final String JITSI_SERVER_URL = "https://jitsi.riot.im/";

    // the jitsi view
    private JitsiMeetView mJitsiView;

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

    @BindView(R.id.jitsi_layout)
    FrameLayout mJitsiContainer;

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

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
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

        loadURL();
    }

    /**
     * Load the jitsi call
     */
    private void loadURL() {
        try {
            JitsiMeetUserInfo userInfo = new JitsiMeetUserInfo();
            userInfo.setDisplayName(mSession.getMyUser().displayname);
            try {
                String avatarUrl = mSession.getMyUser().avatar_url;
                if (avatarUrl != null) {
                    String downloadableUrl = mSession.getContentManager().getDownloadableUrl(avatarUrl, false);
                    if (downloadableUrl != null) {
                        userInfo.setAvatar(new URL(downloadableUrl));
                    }
                }
            } catch (Exception e) {
                //nop
            }
            JitsiMeetConferenceOptions jitsiMeetConferenceOptions = new JitsiMeetConferenceOptions.Builder()
                    .setVideoMuted(!mIsVideoCall)
                    .setUserInfo(userInfo)
                    // Configure the title of the screen
                    // TODO config.putString("callDisplayName", mRoom.getRoomDisplayName(this));
                    .setRoom(mCallUrl)
                    .build();

            mJitsiView.join(jitsiMeetConferenceOptions);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## join() failed : " + e.getMessage(), e);
            finish();
            return;
        }

        FrameLayout.LayoutParams params
                = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);

        mJitsiContainer.addView(mJitsiView, 0, params);

        mJitsiView.setListener(new JitsiMeetViewListener() {

            @Override
            public void onConferenceJoined(Map<String, Object> map) {
                Log.d(LOG_TAG, "## onConferenceJoined() : " + map);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideWaitingView();
                    }
                });
            }

            @Override
            public void onConferenceTerminated(Map<String, Object> map) {
                Log.d(LOG_TAG, "## onConferenceTerminated() : " + map);
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
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (null != mJitsiView) {
            ViewGroup parent = (ViewGroup) (mJitsiView.getParent());

            if (null != parent) {
                parent.removeView(mJitsiView);
            }

            mJitsiView.setListener(null);

            // mJitsiView.leave();
            mJitsiView.dispose();
            mJitsiView = null;
        }

        JitsiMeetActivityDelegate.onHostDestroy(this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        JitsiMeetActivityDelegate.onNewIntent(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        JitsiMeetActivityDelegate.onHostPause(this);
        WidgetsManager wm = getWidgetManager();
        if (wm != null) {
            wm.removeListener(mWidgetListener);
        }
    }

    @Override
    public boolean displayInFullscreen() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        JitsiMeetActivityDelegate.onHostResume(this);
        WidgetsManager wm = getWidgetManager();
        if (wm != null) {
            wm.addListener(mWidgetListener);
        }
    }

    @Nullable
    private WidgetsManager getWidgetManager() {
        if (mSession == null) return null;
        WidgetManagerProvider widgetManagerProvider = Matrix.getInstance(this).getWidgetManagerProvider(mSession);
        if (widgetManagerProvider == null) return null;
        return widgetManagerProvider.getWidgetManager(this);
    }

    @Override
    public void onBackPressed() {
        JitsiMeetActivityDelegate.onBackPressed();
    }

    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {
        JitsiMeetActivityDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void requestPermissions(String[] permissions, int requestCode, PermissionListener listener) {
        JitsiMeetActivityDelegate.requestPermissions(this, permissions, requestCode, listener);
    }
}