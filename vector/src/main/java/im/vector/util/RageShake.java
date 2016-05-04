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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.zip.GZIPOutputStream;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

import org.matrix.androidsdk.MXSession;
import im.vector.VectorApp;
import im.vector.Matrix;
import org.matrix.androidsdk.data.MyUser;

/**
 * manages the rageshakes
 */
public class RageShake implements SensorEventListener {

    private static final String LOG_TAG = "RageShake";

    // the instance
    private static RageShake instance;

    // the context
    private Context mContext;

    /**
     * Contrustor
     */
    protected RageShake() {

        // Samsung devices for some reason seem to be less sensitive than others so the threshold is being
        // lowered for them. A possible lead for a better formula is the fact that the sensitivity detected
        // with the calculated force below seems to relate to the sample rate: The higher the sample rate,
        // the higher the sensitivity.
        String model = Build.MODEL.trim();
        // S3, S1(Brazil), Galaxy Pocket
        if ("GT-I9300".equals(model) || "GT-I9000B".equals(model) || "GT-S5300B".equals(model)) {
            threshold = 20.0f;
        }
    }

    /**
     * @return the rageshake instance
     */
    public synchronized static RageShake getInstance() {
        if (instance == null) {
            instance = new RageShake();
        }
        return instance;
    }

    /**
     * Send the bug report
     */
    public void sendBugReport() {
        Bitmap screenShot = this.takeScreenshot();

        if (null != screenShot) {
            try {
                // store the file in shared place
                String path = MediaStore.Images.Media.insertImage(mContext.getContentResolver(), screenShot, "screenshot-" + new Date(), null);
                Uri screenUri = null;

                if (null == path) {
                    try {
                        File file  = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "screenshot-" + new Date() + ".jpg");
                        FileOutputStream out = new FileOutputStream(file);
                        screenShot.compress(Bitmap.CompressFormat.JPEG, 100, out);
                        screenUri = Uri.fromFile(file);
                    } catch (Exception e) {
                    }
                } else {
                    screenUri = Uri.parse(path);
                }

                Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                intent.setType("text/html");
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"rageshake@vector.im"});
                intent.putExtra(Intent.EXTRA_SUBJECT, "Vector bug report");

                String message = "Something went wrong on my Vector client : \n\n\n";
                message += "-----> my comments <-----\n\n\n";

                message += "---------------------------------------------------------------------\n";
                message += "Application info\n";

                Collection<MXSession> sessions = Matrix.getMXSessions(mContext);
                int profileIndex = 1;

                for(MXSession session : sessions) {
                    message += "Profile " + profileIndex + " :\n";
                    profileIndex++;

                    MyUser mMyUser = session.getMyUser();
                    message += "userId : "+ mMyUser.user_id + "\n";
                    message += "displayname : " + mMyUser.displayname + "\n";
                    message += "homeServer :" + session.getCredentials().homeServer + "\n";
                }

                message += "\n";
                message += "---------------------------------------------------------------------\n";
                message += "Phone : " + Build.MODEL.trim() + " (" + Build.VERSION.INCREMENTAL + " " + Build.VERSION.RELEASE + " " + Build.VERSION.CODENAME + ")\n";
                message += "Vector version: " + Matrix.getInstance(mContext).getVersion(true) + "\n";
                message += "SDK version:  " + Matrix.getInstance(mContext).getDefaultSession().getVersion(true) + "\n";
                message += "\n";
                message += "---------------------------------------------------------------------\n";
                message += "Memory statuses \n";

                long freeSize = 0L;
                long totalSize = 0L;
                long usedSize = -1L;
                try {
                    Runtime info = Runtime.getRuntime();
                    freeSize = info.freeMemory();
                    totalSize = info.totalMemory();
                    usedSize = totalSize - freeSize;
                } catch (Exception e) {
                    e.printStackTrace();
                }

                message += "---------------------------------------------------------------------\n";
                message += "usedSize   " + (usedSize / 1048576L) + " MB\n";
                message += "freeSize   " + (freeSize / 1048576L) + " MB\n";
                message += "totalSize   " + (totalSize / 1048576L) + " MB\n";
                message += "---------------------------------------------------------------------\n";

                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                ActivityManager activityManager = (ActivityManager) VectorApp.getCurrentActivity().getSystemService(Context.ACTIVITY_SERVICE);
                activityManager.getMemoryInfo(mi);

                message += "availMem   " + (mi.availMem / 1048576L) + " MB\n";
                message += "totalMem   " + (mi.totalMem / 1048576L) + " MB\n";
                message += "threshold  " + (mi.threshold / 1048576L) + " MB\n";
                message += "lowMemory  " + mi.lowMemory + "\n";

                message += "---------------------------------------------------------------------\n";

                intent.putExtra(Intent.EXTRA_TEXT, message);

                ArrayList<Uri> attachmentUris = new ArrayList<Uri>();

                if (null != screenUri) {
                    // attachments
                    attachmentUris.add(screenUri);
                }

                String errorLog = LogUtilities.getLogCatError();
                String debugLog = LogUtilities.getLogCatDebug();

                errorLog += "\n\n\n\n\n\n\n\n\n\n------------------ Debug logs ------------------\n\n\n\n\n\n\n\n";
                errorLog += debugLog;

                try {

                    // add the current device logs
                    {
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        GZIPOutputStream gzip = new GZIPOutputStream(os);
                        gzip.write(errorLog.getBytes());
                        gzip.finish();

                        File debugLogFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "logs-" + new Date() + ".gz");
                        FileOutputStream fos = new FileOutputStream(debugLogFile);
                        os.writeTo(fos);
                        os.flush();
                        os.close();

                        attachmentUris.add(Uri.fromFile(debugLogFile));
                    }

                    // add the stored logs
                    ArrayList<File> logsList = LogUtilities.getLogsFileList();

                    long marker = System.currentTimeMillis();

                    for(File file : logsList) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        GZIPOutputStream glogzip = new GZIPOutputStream(bos);

                        FileInputStream inputStream = new FileInputStream(file);

                        byte[] buffer = new byte[1024 * 10];
                        int len;
                        while ((len = inputStream.read(buffer)) != -1) {
                            glogzip.write(buffer, 0, len);
                        }
                        glogzip.finish();

                        File storedLogFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), marker + "-" + file.getName() + ".gz");
                        FileOutputStream flogOs = new FileOutputStream(storedLogFile);
                        bos.writeTo(flogOs);
                        flogOs.flush();
                        flogOs.close();

                        attachmentUris.add(Uri.fromFile(storedLogFile));
                    }
                }
                catch (IOException e) {
                    Log.e(LOG_TAG, "" + e);
                }

                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachmentUris);

                VectorApp.getCurrentActivity().startActivity(intent);
            } catch (Exception e) {
                Log.e(LOG_TAG, "" + e);
            }
        }
    }

    /**
     * Display a dialog to let the user chooses if he would like to send a bnug report.
     */
    public void promptForReport() {
        // Cannot prompt for bug, no active activity.
        if (VectorApp.getCurrentActivity() == null) {
            return;
        }

        // The user is trying to leave with unsaved changes. Warn about that
        new AlertDialog.Builder(VectorApp.getCurrentActivity())
                .setMessage("You seem to be shaking the phone in frustration. Would you like to submit a bug report?")
                .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        sendBugReport();
                    }
                })
                .setNeutralButton("Disable", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);

                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putBoolean(mContext.getString(im.vector.R.string.settings_key_use_rage_shake), false);
                        editor.commit();

                        dialog.dismiss();
                    }
                })
                .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create()
                .show();

    }

    /**
     * Take a screenshot of the display
     * @return
     */
    private Bitmap takeScreenshot() {
        // sanity check
        if (VectorApp.getCurrentActivity() == null) {
            return null;
        }
        // get content view
        View contentView = VectorApp.getCurrentActivity().findViewById(android.R.id.content);
        if (contentView == null) {
            Log.e(LOG_TAG, "Cannot find content view on " + VectorApp.getCurrentActivity() + ". Cannot take screenshot.");
            return null;
        }

        // get the root view to snapshot
        View rootView = contentView.getRootView();
        if (rootView == null) {
            Log.e(LOG_TAG, "Cannot find root view on " + VectorApp.getCurrentActivity() + ". Cannot take screenshot.");
            return null;
        }
        // refresh it
        rootView.setDrawingCacheEnabled(false);
        rootView.setDrawingCacheEnabled(true);

        try {
           return rootView.getDrawingCache();
        }
        catch (OutOfMemoryError oom) {
            Log.e(LOG_TAG, "Cannot get drawing cache for "+ VectorApp.getCurrentActivity() +" OOM.");
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "Cannot get snapshot of screen: "+e);
        }
        return null;
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

    private long now = 0;
    private long timeDiff = 0;
    private long lastUpdate = 0;
    private long lastShake = 0;

    private float x = 0;
    private float y = 0;
    private float z = 0;
    private float lastX = 0;
    private float lastY = 0;
    private float lastZ = 0;
    private float force = 0;

    private float threshold = 35.0f;

    private long intervalNanos = 3L * 1000L * 1000L; // 3 sec

    private long timeToNextShakeMs = 10 * 1000;
    private long lastShakeTimestamp = 0L;

    @Override
    public void onSensorChanged(SensorEvent event) {
        // ignore the sensor events when the application is in background
        if (VectorApp.isAppInBackground()) {
            lastUpdate = 0;
            return;
        }

        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        now = event.timestamp;

        x = event.values[0];
        y = event.values[1];
        z = event.values[2];

        if (lastUpdate == 0) {
            // set some default vals
            lastUpdate = now;
            lastShake = now;
            lastX = x;
            lastY = y;
            lastZ = z;
        }
        else {
            timeDiff = now - lastUpdate;

            if (timeDiff > 0) {
                force = Math.abs(x + y + z - lastX - lastY - lastZ);
                if (Float.compare(force, threshold) > 0) {
                    if (((now - lastShake) >= intervalNanos) && ((System.currentTimeMillis() - lastShakeTimestamp) > timeToNextShakeMs)) {
                        Log.d(LOG_TAG, "Shaking detected.");
                        lastShakeTimestamp = System.currentTimeMillis();

                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);

                        if (preferences.getBoolean(mContext.getString(im.vector.R.string.settings_key_use_rage_shake), true)) {
                            promptForReport();
                        }
                    }
                    else {
                        Log.d(LOG_TAG, "Suppress shaking - not passed interval. Ms to go: "+(timeToNextShakeMs -
                                (System.currentTimeMillis() - lastShakeTimestamp))+" ms");
                    }
                    lastShake = now;
                }
                lastX = x;
                lastY = y;
                lastZ = z;
                lastUpdate = now;
            }
        }
    }
}
