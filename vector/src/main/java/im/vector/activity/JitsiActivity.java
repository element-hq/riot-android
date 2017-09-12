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
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;


import org.jitsi.meet.sdk.JitsiMeetView;
import org.jitsi.meet.sdk.JitsiMeetViewListener;

import java.net.URL;
import java.util.Map;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.widgets.Widget;

public class JitsiActivity extends AppCompatActivity {
    private static final String LOG_TAG = "JitsiActivity";

    /**
     * The linked widget
     */
    public static final String EXTRA_WIDGET_ID = "EXTRA_WIDGET_ID";

    /**
     * Base server URL
     */
    public static final String JITSI_SERVER_URL = "https://jitsi.riot.im/";

    // permission request code
    public final static int CAN_DRAW_OVERLAY_REQUEST_CODE = 1234;

    // the jitsi view
    private JitsiMeetView mJitsiView;

    // the linked widget
    private Widget mWidget;

    // call URL
    private String mCallUrl;

    @Override
    @SuppressLint("NewApi")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_jitsiview);

        mWidget = (Widget)getIntent().getSerializableExtra(EXTRA_WIDGET_ID);

        try {
            Uri uri = Uri.parse(mWidget.getUrl());
            String confId = uri.getQueryParameter("confId");
            mCallUrl = JITSI_SERVER_URL + confId;
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onCreate() failed : " + e.getMessage());
            this.finish();
            return;
        }

        mJitsiView = new JitsiMeetView(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if ( !Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, CAN_DRAW_OVERLAY_REQUEST_CODE);
            } else {
                Log.e(LOG_TAG, "## onCreate() : the user did not grant the overlay settings")
                this.finish();
            }
        } else {
            loadURL();
        }
    }

    /**
     * Load the jitsi call
     */
    private void loadURL() {
        try {
            mJitsiView.loadURL(new URL(mCallUrl));
        } catch (Exception e) {
            Log.e(LOG_TAG, "## loadURL() failed : " + e.getMessage());
            this.finish();
        }

        RelativeLayout layout = (RelativeLayout)findViewById(R.id.call_layout);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        layout.setVisibility(View.VISIBLE);
        layout.addView(mJitsiView, 0, params);

        mJitsiView.setListener(new JitsiMeetViewListener() {
            @Override
            public void onConferenceFailed(Map<String, Object> map) {
                Log.e(LOG_TAG, "## onConferenceFailed() : " + map);
                JitsiActivity.this.finish();
            }

            @Override
            public void onConferenceJoined(Map<String, Object> map) {
                Log.d(LOG_TAG, "## onConferenceJoined() : " + map);

                JitsiActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        findViewById(R.id.call_connecting_layout).setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onConferenceLeft(Map<String, Object> map) {
                Log.d(LOG_TAG, "## onConferenceLeft() : " + map);
                JitsiActivity.this.finish();
            }

            @Override
            public void onConferenceWillJoin(Map<String, Object> map) {
                Log.d(LOG_TAG, "## onConferenceWillJoin() : " + map);

                JitsiActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        findViewById(R.id.call_connecting_progress_layout).setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onConferenceWillLeave(Map<String, Object> map) {
                Log.d(LOG_TAG, "## onConferenceWillLeave() : " + map);
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
            mJitsiView.dispose();
            mJitsiView = null;
            JitsiMeetView.onHostDestroy(this);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        JitsiMeetView.onNewIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        JitsiMeetView.onHostPause(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        JitsiMeetView.onHostResume(this);
    }

    @Override
    public void onBackPressed() {
        if (!JitsiMeetView.onBackPressed()) {
            super.onBackPressed();
        }
    }
}