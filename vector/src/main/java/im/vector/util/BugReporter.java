/*
 * Copyright 2016 OpenMarket Ltd
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

package im.vector.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.util.Log;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.Matrix;

/**
 * BugReporter creates and sends the bug reports.
 */
public class BugReporter {
    private static final String LOG_TAG = "BugReporter";

    /**
     * Bug report upload listener
     */
    private interface IMXBugReportListener {
        /**
         * The bug report has been cancelled
         */
        void onUploadCancelled();

        /**
         * The bug report upload failed.
         *
         * @param reason the failure reason
         */
        void onUploadFailed(String reason);

        /**
         * The upload progress (in percent)
         *
         * @param progress the upload progress
         */
        void onProgress(int progress);

        /**
         * The bug report upload succeeded.
         */
        void onUploadSucceed();
    }

    // filenames
    private static final String LOG_CAT_ERROR_FILENAME = "logcatError.log";
    private static final String LOG_CAT_FILENAME = "logcat.log";
    private static final String LOG_CAT_SCREENSHOT_FILENAME = "screenshot.png";


    // the http client
    private static final OkHttpClient mOkHttpClient = new OkHttpClient();

    // the pending bug report call
    private static Call mBugReportCall = null;


    // boolean to cancel the bug report
    private static boolean mIsCancelled = false;

    /**
     * Send a bug report.
     *
     * @param context the application context
     * @param withDevicesLogs true to include the device log
     * @param withCrashLogs true to include the crash logs
     * @param withScreenshot true to include the screenshot
     * @param bugDescription the bug description
     * @param listener the listener
     */
    private static void sendBugReport(final Context context, final boolean withDevicesLogs, final boolean withCrashLogs, final boolean withScreenshot, final String bugDescription, final IMXBugReportListener listener) {
        new AsyncTask<Void, Integer, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                String serverError = null;

                List<File> gzippedFiles = new ArrayList<>();

                if (withDevicesLogs) {
                    List<File> files = org.matrix.androidsdk.util.Log.addLogFiles(new ArrayList<File>());

                    for (File f : files) {
                        if (!mIsCancelled) {
                            File gzippedFile = compressFile(f);

                            if (null != gzippedFile) {
                                gzippedFiles.add(gzippedFile);
                            }
                        }
                    }
                }

                if (!mIsCancelled && (withCrashLogs || withDevicesLogs)) {
                    File gzippedLogcat = saveLogCat(context, false);

                    if (null != gzippedLogcat) {
                        gzippedFiles.add(gzippedLogcat);
                    }
                }

                MXSession session = Matrix.getInstance(context).getDefaultSession();

                String deviceId = null;
                String userId = null;

                if (null != session) {
                    userId = session.getMyUserId();
                    deviceId = session.getCredentials().deviceId;
                }

                if (TextUtils.isEmpty(userId)) {
                    userId = "";
                }

                if (TextUtils.isEmpty(deviceId)) {
                    deviceId = "";
                }

                if (!mIsCancelled) {
                    // build the multi part request
                    BugReporterMultipartBody.Builder builder = new BugReporterMultipartBody.Builder()
                            .addFormDataPart("text", bugDescription)
                            .addFormDataPart("app", "riot-android")
                            .addFormDataPart("user_agent", "Android")
                            .addFormDataPart("user_id", userId)
                            .addFormDataPart("device_id", deviceId)
                            .addFormDataPart("version", Matrix.getInstance(context).getVersion(true))
                            .addFormDataPart("branch_name", context.getString(R.string.git_branch_name))
                            .addFormDataPart("matrix_sdk_version", Matrix.getInstance(context).getDefaultSession().getVersion(true))
                            .addFormDataPart("olm_version", Matrix.getInstance(context).getDefaultSession().getCryptoVersion(context, true))
                            .addFormDataPart("device", Build.MODEL.trim())
                            .addFormDataPart("os", Build.VERSION.INCREMENTAL + " " + Build.VERSION.RELEASE + " " + Build.VERSION.CODENAME);

                    // add the gzipped files
                    for (File file : gzippedFiles) {
                        builder.addFormDataPart("compressed-log", file.getName(), RequestBody.create(MediaType.parse("application/octet-stream"), file));
                    }

                    if (withScreenshot) {
                        Bitmap bitmap = takeScreenshot();

                        if (null != bitmap) {
                            File logCatScreenshotFile = new File(context.getCacheDir().getAbsolutePath(), LOG_CAT_SCREENSHOT_FILENAME);

                            if (logCatScreenshotFile.exists()) {
                                logCatScreenshotFile.delete();
                            }

                            try {
                                FileOutputStream fos = new FileOutputStream(logCatScreenshotFile);
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                fos.flush();
                                fos.close();

                                builder.addFormDataPart("file", logCatScreenshotFile.getName(), RequestBody.create(MediaType.parse("application/octet-stream"), logCatScreenshotFile));
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## saveLogCat() : fail to write logcat" + e.toString());
                            }
                        }
                    }

                    // add some github tags
                    try {
                        PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                        builder.addFormDataPart("label", pInfo.versionName);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## sendBugReport() : cannot retrieve the appname " + e.getMessage());
                    }

                    builder.addFormDataPart("label", context.getResources().getString(R.string.flavor_description));
                    builder.addFormDataPart("label", context.getString(R.string.git_branch_name));


                    BugReporterMultipartBody requestBody = builder.build();

                    // add a progress listener
                    requestBody.setWriteListener(new BugReporterMultipartBody.WriteListener() {
                        @Override
                        public void onWrite(long totalWritten, long contentLength) {
                            int percentage;

                            if (-1 != contentLength) {
                                if (totalWritten > contentLength) {
                                    percentage = 100;
                                } else {
                                    percentage = (int) (totalWritten * 100 / contentLength);
                                }
                            } else {
                                percentage = 0;
                            }

                            if (mIsCancelled && (null != mBugReportCall)) {
                                mBugReportCall.cancel();
                            }

                            Log.d(LOG_TAG, "## onWrite() : " + percentage + "%");
                            publishProgress(percentage);
                        }
                    });

                    // build the request
                    Request request = new Request.Builder()
                            .url(context.getResources().getString(R.string.bug_report_url))
                            .post(requestBody)
                            .build();

                    int responseCode = HttpURLConnection.HTTP_INTERNAL_ERROR;
                    Response response = null;

                    // trigger the request
                    try {
                        mBugReportCall = mOkHttpClient.newCall(request);
                        response = mBugReportCall.execute();
                        responseCode = response.code();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "response " + e.getMessage());
                    }

                    // if the upload failed, try to retrieve the reason
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        if ((null == response) || (null == response.body())) {
                            serverError = "Failed with error " + responseCode;
                        } else {
                            InputStream is = response.body().byteStream();

                            if (null != is) {
                                try {
                                    int ch;
                                    StringBuilder b = new StringBuilder();
                                    while ((ch = is.read()) != -1) {
                                        b.append((char) ch);
                                    }
                                    serverError = b.toString();
                                    is.close();

                                    // check if the error message
                                    try {
                                        JSONObject responseJSON = new JSONObject(serverError);
                                        serverError = responseJSON.getString("error");
                                    } catch (JSONException e) {
                                        Log.e(LOG_TAG, "doInBackground ; Json conversion failed " + e.getMessage());
                                    }

                                    // should never happen
                                    if (null == serverError) {
                                        serverError = "Failed with error " + responseCode;
                                    }
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "## sendBugReport() : failed to parse error " + e.getMessage());
                                } finally {
                                    try {
                                        is.close();
                                    } catch (Exception e) {
                                        Log.e(LOG_TAG, "## sendBugReport() : failed to close the error stream " + e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                }

                return serverError;
            }

            @Override
            protected void onProgressUpdate(Integer... progress) {
                super.onProgressUpdate(progress);

                if (null != listener) {
                    try {
                        listener.onProgress((null == progress) ? 0 : progress[0]);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## onProgress() : failed " + e.getMessage());
                    }
                }
            }

            @Override
            protected void onPostExecute(String reason) {
                mBugReportCall = null;

                if (null != listener) {
                    try {
                        if (mIsCancelled) {
                            listener.onUploadCancelled();
                        } else if (null == reason) {
                            listener.onUploadSucceed();
                        } else {
                            listener.onUploadFailed(reason);
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## onPostExecute() : failed " + e.getMessage());
                    }
                }
            }
        }.execute();
    }

    /**
     * Send a bug report either with email or with Vector.
     */
    public static void sendBugReport() {
        final Activity currentActivity = VectorApp.getCurrentActivity();

        // no current activity so cannot display an alert
        if (null == currentActivity) {
            sendBugReport(VectorApp.getInstance().getApplicationContext(), true, true, true, "", null);
            return;
        }

        final Context appContext = currentActivity.getApplicationContext();
        LayoutInflater inflater = currentActivity.getLayoutInflater();
        View dialogLayout = inflater.inflate(R.layout.dialog_bug_report, null);

        final AlertDialog.Builder dialog = new AlertDialog.Builder(currentActivity);
        dialog.setTitle(R.string.send_bug_report);
        dialog.setView(dialogLayout);

        final EditText bugReportText = (EditText) dialogLayout.findViewById(R.id.bug_report_edit_text);
        final CheckBox includeLogsButton = (CheckBox) dialogLayout.findViewById(R.id.bug_report_button_include_logs);
        final CheckBox includeCrashLogsButton = (CheckBox) dialogLayout.findViewById(R.id.bug_report_button_include_crash_logs);
        final CheckBox includeScreenShotButton = (CheckBox) dialogLayout.findViewById(R.id.bug_report_button_include_screenshot);

        final ProgressBar progressBar = (ProgressBar) dialogLayout.findViewById(R.id.bug_report_progress_view);
        final TextView progressTextView = (TextView) dialogLayout.findViewById(R.id.bug_report_progress_text_view);

        dialog.setPositiveButton(R.string.send, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // will be overridden to avoid dismissing the dialog while displaying the progress
            }
        });

        dialog.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // will be overridden to avoid dismissing the dialog while displaying the progress
            }
        });

        //
        final AlertDialog bugReportDialog = dialog.show();
        final Button cancelButton = bugReportDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        final Button sendButton = bugReportDialog.getButton(AlertDialog.BUTTON_POSITIVE);

        if (null != cancelButton) {
            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // check if there is no upload in progress
                    if (includeLogsButton.isEnabled()) {
                        bugReportDialog.dismiss();
                    } else {
                        mIsCancelled = true;
                        cancelButton.setEnabled(false);
                    }
                }
            });
        }

        if (null != sendButton) {
            sendButton.setEnabled(false);

            sendButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // disable the active area while uploading the bug report
                    bugReportText.setEnabled(false);
                    sendButton.setEnabled(false);
                    includeLogsButton.setEnabled(false);
                    includeCrashLogsButton.setEnabled(false);
                    includeScreenShotButton.setEnabled(false);

                    progressTextView.setVisibility(View.VISIBLE);
                    progressTextView.setText(appContext.getString(R.string.send_bug_report_progress, 0 + ""));

                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(0);

                    sendBugReport(VectorApp.getInstance(), includeLogsButton.isChecked(), includeCrashLogsButton.isChecked(), includeScreenShotButton.isChecked(), bugReportText.getText().toString(), new IMXBugReportListener() {
                        @Override
                        public void onUploadFailed(String reason) {
                            try {
                                if (null != VectorApp.getInstance() && !TextUtils.isEmpty(reason)) {
                                    Toast.makeText(VectorApp.getInstance(), VectorApp.getInstance().getString(R.string.send_bug_report_failed, reason), Toast.LENGTH_LONG).show();
                                }
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## onUploadFailed() : failed to display the toast " + e.getMessage());
                            }

                            try {
                                // restore the dialog if the upload failed
                                bugReportText.setEnabled(true);
                                sendButton.setEnabled(true);
                                includeLogsButton.setEnabled(true);
                                includeCrashLogsButton.setEnabled(true);
                                includeScreenShotButton.setEnabled(true);
                                cancelButton.setEnabled(true);

                                progressTextView.setVisibility(View.GONE);
                                progressBar.setVisibility(View.GONE);
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## onUploadFailed() : failed to restore the dialog button " + e.getMessage());

                                try {
                                    bugReportDialog.dismiss();
                                } catch (Exception e2) {
                                    Log.e(LOG_TAG, "## onUploadFailed() : failed to dismiss the dialog " + e2.getMessage());
                                }
                            }

                            mIsCancelled = false;
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

                            progressBar.setProgress(progress);
                            progressTextView.setText(appContext.getString(R.string.send_bug_report_progress, progress + ""));
                        }

                        @Override
                        public void onUploadSucceed() {
                            try {
                                if (null != VectorApp.getInstance()) {
                                    Toast.makeText(VectorApp.getInstance(), VectorApp.getInstance().getString(R.string.send_bug_report_sent), Toast.LENGTH_LONG).show();
                                }
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## onUploadSucceed() : failed to dismiss the toast " + e.getMessage());
                            }

                            try {
                                bugReportDialog.dismiss();
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## onUploadSucceed() : failed to dismiss the dialog " + e.getMessage());
                            }
                        }
                    });
                }
            });
        }

        bugReportText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (null != sendButton) {
                    sendButton.setEnabled(bugReportText.getText().toString().length() > 10);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    //==============================================================================================================
    // Screenshot management
    //==============================================================================================================

    /**
     * Take a screenshot of the display.
     *
     * @return the screenshot
     */
    private static Bitmap takeScreenshot() {
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
        } catch (OutOfMemoryError oom) {
            Log.e(LOG_TAG, "Cannot get drawing cache for " + VectorApp.getCurrentActivity() + " OOM.");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Cannot get snapshot of screen: " + e);
        }
        return null;
    }

    //==============================================================================================================
    // Logcat management
    //==============================================================================================================

    /**
     * Save the logcat
     *
     * @param context       the context
     * @param isErrorLogcat true to save the error logcat
     * @return the file if the operation succeeds
     */
    private static File saveLogCat(Context context, boolean isErrorLogcat) {
        File logCatErrFile = new File(context.getCacheDir().getAbsolutePath(), isErrorLogcat ? LOG_CAT_ERROR_FILENAME : LOG_CAT_FILENAME);

        if (logCatErrFile.exists()) {
            logCatErrFile.delete();
        }

        try {
            FileOutputStream fos = new FileOutputStream(logCatErrFile);
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            getLogCatError(osw, isErrorLogcat);
            osw.close();

            fos.flush();
            fos.close();

            return compressFile(logCatErrFile);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## saveLogCat() : fail to write logcat" + e.toString());
        }

        return null;
    }

    private static final int BUFFER_SIZE = 1024 * 1024 * 5;

    private static final String[] LOGCAT_CMD_ERROR = new String[]{
            "logcat", ///< Run 'logcat' command
            "-d",  ///< Dump the log rather than continue outputting it
            "-v", // formatting
            "threadtime", // include timestamps
            "AndroidRuntime:E " + ///< Pick all AndroidRuntime errors (such as uncaught exceptions)"communicatorjni:V " + ///< All communicatorjni logging
                    "libcommunicator:V " + ///< All libcommunicator logging
                    "DEBUG:V " + ///< All DEBUG logging - which includes native land crashes (seg faults, etc)
                    "*:S" ///< Everything else silent, so don't pick it..
    };

    private static final String[] LOGCAT_CMD_DEBUG = new String[]{
            "logcat",
            "-d",
            "-v",
            "threadtime",
            "Retrofit:S",
            "ProgressBar:S",
            "AbsListView:S",
            "dalvikvm:S",
            "OpenGLRenderer:S",
            "NativeCrypto:S",
            "VelocityTracker:S",
            "MaliEGL:S",
            "GraphicBuffer:S",
            "WifiStateMachine:S",
            "ActivityThread:S",
            "PowerManagerService:S",
            "BufferQueue:S",
            "KeyguardUpdateMonitor:S",
            "wpa_supplicant:S",
            "ANRManager:S",
            "InputReader:S",
            "PowerUI:S",
            "BatteryService:S",
            "qdhwcomposer:S",
            "ServiceDumpSys:S",
            "DisplayPowerController:S",
            "View:S",
            "ListView:S",
            "Posix:S",
            "chatty:S",
            "ViewRootImpl:S",
            "TextView:S",
            "MotionRecognitionManager:S",
            "DisplayListCanvas:S",
            "AudioManager:S",
            "irsc_util:S",
            "QCamera2HWI:S",
            "audio_hw_primary:S",
            "msm8974_platform:S",
            "ACDB-LOADER:S",
            "platform_parser:S",
            "audio_hw_ssr:S",
            "audio_hw_spkr_prot:S",
            "Thermal-Lib:S",
            "AudioFlinger:S",
            "EffectDiracSound:S",
            "BufferProvider:S",
            "MonoPipe:S",
            "bt_a2dp_hw:S",
            "r_submix:S",
            "AudioPolicyManagerCustom:S",
            "RadioService:S",
            "mediaserver:S",
            "ListenService:S",
            "InstallerConnection:S",
            "SystemServer:S",
            "SystemServiceManager:S",
            "BaseMiuiBroadcastManager:S",
            "BatteryStatsImpl:S",
            "IntentFirewall:S",
            "ServiceThread:S",
            "AppOps:S",
            "DisplayManagerService:S",
            "SELinuxMMAC:S",
            "PackageManager:S",
            "PackageParser:S",
            "PreinstallApp:S",
            "VoldConnector:S",
            "SoundTriggerHelper:S",
            "AutomaticBrightnessController:S",
            "KeyguardServiceDelegate:S",
            "VoiceInteractionManagerService:S",
            "SystemServer:S",
            "UsbAlsaManager:S",
            "Telecom:S",
            "LocationManagerInjector:S",
            "LocationPolicy:S",
            "MmsServiceBroker:S",
            "MountService:S",
            "ACodec:S",
            "OMXNodeInstance:S",
            "MM_OSAL:S",
            "OMXNodeInstance:S",
            "SoftMPEG4Encoder:S",
            "audio_hw_extn:S",
            "audio_hw_fm:S",
            "ContextImpl:S",
            "ActiveAndroid:S",
            "bt_a2dp_hw:S",
            "BroadcastQueueInjector:S",
            "AutoStartManagerService:S",
            "Ext4Crypt:S",
            "MccTable:S",
            "DiracAPI:S",
            "skia:S",
            "libc-netbsd:S",
            "chromium:S",
            "v8:S",
            "PreferenceGroup:S",
            "Preference:S",
            "*:*"
    };

    /**
     * Retrieves the logs
     *
     * @param streamWriter  the stream writer
     * @param isErrorLogCat true to save the error logs
     */
    private static void getLogCatError(OutputStreamWriter streamWriter, boolean isErrorLogCat) {
        Process logcatProc;

        try {
            logcatProc = Runtime.getRuntime().exec(isErrorLogCat ? LOGCAT_CMD_ERROR : LOGCAT_CMD_DEBUG);
        } catch (IOException e1) {
            return;
        }

        BufferedReader reader = null;
        try {
            String separator = System.getProperty("line.separator");
            reader = new BufferedReader(new InputStreamReader(logcatProc.getInputStream()), BUFFER_SIZE);
            String line;
            while ((line = reader.readLine()) != null) {
                streamWriter.append(line);
                streamWriter.append(separator);
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "getLog fails with " + e.getLocalizedMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "getLog fails with " + e.getLocalizedMessage());
                }
            }
        }
    }

    //==============================================================================================================
    // File compression management
    //==============================================================================================================

    /**
     * GZip a file
     *
     * @param fin the input file
     * @return the gzipped file
     */
    private static File compressFile(File fin) {
        Log.d(LOG_TAG, "## compressFile() : compress " + fin.getName());

        File dstFile = new File(fin.getParent(), fin.getName() + ".gz");

        if (dstFile.exists()) {
            dstFile.delete();
        }

        FileOutputStream fos = null;
        GZIPOutputStream gos = null;
        InputStream inputStream = null;
        try {
            fos = new FileOutputStream(dstFile);
            gos = new GZIPOutputStream(fos);

            inputStream = new FileInputStream(fin);
            int n;

            byte[] buffer = new byte[2048];
            while ((n = inputStream.read(buffer)) != -1) {
                gos.write(buffer, 0, n);
            }

            gos.close();
            inputStream.close();

            Log.d(LOG_TAG, "## compressFile() : " + fin.length() + " compressed to " + dstFile.length() + " bytes");
            return dstFile;
        } catch (Exception e) {
            Log.e(LOG_TAG, "## compressFile() failed " + e.getMessage());
        } catch (OutOfMemoryError oom) {
            Log.e(LOG_TAG, "## compressFile() failed " + oom.getMessage());
        } finally {
            try {
                if (null != fos) {
                    fos.close();
                }
                if (null != gos) {
                    gos.close();
                }
                if (null != inputStream) {
                    inputStream.close();
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## compressFile() failed to close inputStream " + e.getMessage());
            }
        }

        return null;
    }
}
