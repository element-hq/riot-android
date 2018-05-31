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

import org.matrix.androidsdk.util.Log;

import im.vector.R;
import im.vector.VectorApp;

/**
 * Manages the rage sakes
 */
public class RageShake implements SensorEventListener {
    private static final String LOG_TAG = RageShake.class.getSimpleName();

    // the context
    private Context mContext;

    // the sensor
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private boolean mIsStarted;

    /**
     * Constructor
     */
    public RageShake(Context context) {
        mContext = context;

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        if (null != mSensorManager) {
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        if (null == mSensor) {
            Log.e(LOG_TAG, "No accelerometer in this device. Cannot use rage shake.");
            mSensorManager = null;
        }

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

        try {
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
                            PreferencesManager.setUseRageshake(mContext, false);
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
        } catch (Exception e) {
            Log.e(LOG_TAG, "promptForReport " + e.getMessage());
        }
    }


    /**
     * start the sensor detector
     */
    public void start() {
        if ((null != mSensorManager) && PreferencesManager.useRageshake(mContext) && !VectorApp.isAppInBackground() && !mIsStarted) {
            mIsStarted = true;
            mLastUpdate = 0;
            mLastShake = 0;
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    /**
     * Stop the sensor detector
     */
    public void stop() {
        if (null != mSensorManager) {
            mSensorManager.unregisterListener(this, mSensor);
        }
        mIsStarted = false;
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
        } else {
            long timeDiff = now - mLastUpdate;

            if (timeDiff > 0) {
                float force = Math.abs(x + y + z - mLastX - mLastY - mLastZ);
                if (Float.compare(force, mThreshold) > 0) {
                    if (((now - mLastShake) >= mIntervalNanos) && ((System.currentTimeMillis() - mLastShakeTimestamp) > mTimeToNextShakeMs)) {
                        Log.d(LOG_TAG, "Shaking detected.");
                        mLastShakeTimestamp = System.currentTimeMillis();

                        if (PreferencesManager.useRageshake(mContext)) {
                            promptForReport();
                        }
                    } else {
                        Log.d(LOG_TAG, "Suppress shaking - not passed interval. Ms to go: " + (mTimeToNextShakeMs -
                                (System.currentTimeMillis() - mLastShakeTimestamp)) + " ms");
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
