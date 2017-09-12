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


import org.jitsi.meet.sdk.JitsiMeetView;

import java.net.URL;

import im.vector.VectorApp;

public class JitsiActivity extends AppCompatActivity {
    private static final String LOG_TAG = "JitsiActivity";

    // the jitsi view
    private JitsiMeetView mJitsiView;

    private String mCallUrl = "https://meet.jit.si/RennesSync";

    // permission request code
    public final static int CAN_DRAW_OVERLAY_REQUEST_CODE = 1234;

    @Override
    @SuppressLint("NewApi")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mJitsiView = new JitsiMeetView(this);

        if (!Settings.canDrawOverlays(this)) {
            if (Build.VERSION.SDK_INT >= 23) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, CAN_DRAW_OVERLAY_REQUEST_CODE);
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

        setContentView(mJitsiView);
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

        mJitsiView.dispose();
        mJitsiView = null;

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