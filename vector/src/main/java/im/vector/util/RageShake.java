/*
 * Copyright 2016 OpenMarket Ltd
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

package im.vector.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import im.vector.R;
import im.vector.VectorApp;

/**
 * Manages the rage sakes
 */
public class RageShake implements SensorEventListener {

    private static final String LOG_TAG = "RageShake";

    // the context
    private Context mContext;

    /**
     * Constructor
     */
    public RageShake() {
        // Samsung devices for some reason seem to be less sensitive than others so the threshold is being
        // lowered for them. A possible lead for a better formula is the fact that the sensitivity detected
        // with the calculated force below seems to relate to the sample rate: The higher the sample rate,
        // the higher the sensitivity.
        String model = Build.MODEL.trim();
        // S3, S1(Brazil), Galaxy Pocket
        if ("GT-I9300".equals(model) || "GT-I9000B".equals(model) || "GT-S5300B".equals(model)) {
            mThreshold = 20.0f;
        }
    }

    /**
     * Display a dialog to let the user chooses if he would like to send a bnug report.
     */
    private void promptForReport() {
        // Cannot prompt for bug, no active activity.
        if (VectorApp.getCurrentActivity() == null) {
            return;
        }

        // The user is trying to leave with unsaved changes. Warn about that
        new AlertDialog.Builder(VectorApp.getCurrentActivity())
                .setMessage(R.string.send_bug_report_alert_message)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        BugReporter.sendBugReport();
                    }
                })
                .setNeutralButton(R.string.disable, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);

                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putBoolean(mContext.getString(im.vector.R.string.settings_key_use_rage_shake), false);
                        editor.commit();

                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create()
                .show();

    }

    /**
     * start the sensor detector
     */
    public void start(Context context) {
        mContext = context;

        SensorManager sm = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (s == null) {
            Log.e(LOG_TAG, "No accelerometer in this device. Cannot use rage shake.");
            return;
        }
        sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // don't care
    }

    private static final long mTimeToNextShakeMs = 10 * 1000;
    private static final long mIntervalNanos = 3L * 1000L * 1000L; // 3 sec
    private static float mThreshold = 35.0f;

    private long mLastUpdate = 0;
    private long mLastShake = 0;

    private float mLastX = 0;
    private float mLastY = 0;
    private float mLastZ = 0;

    private long mLastShakeTimestamp = 0L;

    @Override
    public void onSensorChanged(SensorEvent event) {
        // ignore the sensor events when the application is in background
        if (VectorApp.isAppInBackground()) {
            mLastUpdate = 0;
            return;
        }

        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        long now = event.timestamp;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        if (mLastUpdate == 0) {
            // set some default vals
            mLastUpdate = now;
            mLastShake = now;
            mLastX = x;
            mLastY = y;
            mLastZ = z;
        }
        else {
            long timeDiff = now - mLastUpdate;

            if (timeDiff > 0) {
                float force = Math.abs(x + y + z - mLastX - mLastY - mLastZ);
                if (Float.compare(force, mThreshold) > 0) {
                    if (((now - mLastShake) >= mIntervalNanos) && ((System.currentTimeMillis() - mLastShakeTimestamp) > mTimeToNextShakeMs)) {
                        Log.d(LOG_TAG, "Shaking detected.");
                        mLastShakeTimestamp = System.currentTimeMillis();

                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);

                        if (preferences.getBoolean(mContext.getString(im.vector.R.string.settings_key_use_rage_shake), true)) {
                            promptForReport();
                        }
                    }
                    else {
                        Log.d(LOG_TAG, "Suppress shaking - not passed interval. Ms to go: "+(mTimeToNextShakeMs -
                                (System.currentTimeMillis() - mLastShakeTimestamp))+" ms");
                    }
                    mLastShake = now;
                }
                mLastX = x;
                mLastY = y;
                mLastZ = z;
                mLastUpdate = now;
            }
        }
    }
}
