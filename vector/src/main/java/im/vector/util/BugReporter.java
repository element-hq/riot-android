/*
 * Copyright 2016 OpenMarket Ltd
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

package im.vector.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.util.Log;

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
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.BugReportActivity;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * BugReporter creates and sends the bug reports.
 */
public class BugReporter {
    private static final String LOG_TAG = BugReporter.class.getSimpleName();

    /**
     * Bug report upload listener
     */
    public interface IMXBugReportListener {
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
    private static final String CRASH_FILENAME = "crash.log";


    // the http client
    private static final OkHttpClient mOkHttpClient = new OkHttpClient();

    // the pending bug report call
    private static Call mBugReportCall = null;


    // boolean to cancel the bug report
    private static boolean mIsCancelled = false;

    /**
     * Send a bug report.
     *
     * @param context           the application context
     * @param withDevicesLogs   true to include the device log
     * @param withCrashLogs     true to include the crash logs
     * @param withScreenshot    true to include the screenshot
     * @param theBugDescription the bug description
     * @param listener          the listener
     */
    public static void sendBugReport(final Context context,
                                     final boolean withDevicesLogs,
                                     final boolean withCrashLogs,
                                     final boolean withScreenshot,
                                     final String theBugDescription,
                                     final IMXBugReportListener listener) {
        new AsyncTask<Void, Integer, String>() {

            // enumerate files to delete
            final List<File> mBugReportFiles = new ArrayList<>();

            @Override
            protected String doInBackground(Void... voids) {
                String bugDescription = theBugDescription;
                String serverError = null;
                String crashCallStack = getCrashDescription(context);

                if (null != crashCallStack) {
                    bugDescription += "\n\n\n\n--------------------------------- crash call stack ---------------------------------\n";
                    bugDescription += crashCallStack;
                }

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
                        if (gzippedFiles.size() == 0) {
                            gzippedFiles.add(gzippedLogcat);
                        } else {
                            gzippedFiles.add(0, gzippedLogcat);
                        }
                    }

                    File crashDescription = getCrashFile(context);
                    if (crashDescription.exists()) {
                        File compressedCrashDescription = compressFile(crashDescription);

                        if (null != compressedCrashDescription) {
                            if (gzippedFiles.size() == 0) {
                                gzippedFiles.add(compressedCrashDescription);
                            } else {
                                gzippedFiles.add(0, compressedCrashDescription);
                            }
                        }
                    }
                }

                MXSession session = Matrix.getInstance(context).getDefaultSession();

                String deviceId = "undefined";
                String userId = "undefined";
                String matrixSdkVersion = "undefined";
                String olmVersion = "undefined";


                if (null != session) {
                    userId = session.getMyUserId();
                    deviceId = session.getCredentials().deviceId;
                    matrixSdkVersion = session.getVersion(true);
                    olmVersion = session.getCryptoVersion(context, true);
                }

                if (!mIsCancelled) {
                    // build the multi part request
                    BugReporterMultipartBody.Builder builder = new BugReporterMultipartBody.Builder()
                            .addFormDataPart("text", bugDescription)
                            .addFormDataPart("app", "riot-android")
                            .addFormDataPart("user_agent", "Android")
                            .addFormDataPart("user_id", userId)
                            .addFormDataPart("device_id", deviceId)
                            .addFormDataPart("version", Matrix.getInstance(context).getVersion(true, false))
                            .addFormDataPart("branch_name", context.getString(R.string.git_branch_name))
                            .addFormDataPart("matrix_sdk_version", matrixSdkVersion)
                            .addFormDataPart("olm_version", olmVersion)
                            .addFormDataPart("device", Build.MODEL.trim())
                            .addFormDataPart("os", Build.VERSION.INCREMENTAL + " " + Build.VERSION.RELEASE + " " + Build.VERSION.CODENAME)
                            .addFormDataPart("locale", Locale.getDefault().toString())
                            .addFormDataPart("app_language", VectorApp.getApplicationLocale().toString())
                            .addFormDataPart("default_app_language", VectorApp.getDeviceLocale().toString());

                    String buildNumber = context.getString(R.string.build_number);
                    if (!TextUtils.isEmpty(buildNumber) && !buildNumber.equals("0")) {
                        builder.addFormDataPart("build_number", buildNumber);
                    }

                    // add the gzipped files
                    for (File file : gzippedFiles) {
                        builder.addFormDataPart("compressed-log", file.getName(), RequestBody.create(MediaType.parse("application/octet-stream"), file));
                    }

                    mBugReportFiles.addAll(gzippedFiles);

                    if (withScreenshot) {
                        Bitmap bitmap = mScreenshot;

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

                                builder.addFormDataPart("file",
                                        logCatScreenshotFile.getName(), RequestBody.create(MediaType.parse("application/octet-stream"), logCatScreenshotFile));
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## saveLogCat() : fail to write logcat" + e.toString());
                            }
                        }
                    }

                    mScreenshot = null;

                    // add some github tags
                    try {
                        PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                        builder.addFormDataPart("label", pInfo.versionName);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## sendBugReport() : cannot retrieve the appname " + e.getMessage());
                    }

                    builder.addFormDataPart("label", context.getString(R.string.flavor_description));
                    builder.addFormDataPart("label", context.getString(R.string.git_branch_name));

                    if (getCrashFile(context).exists()) {
                        builder.addFormDataPart("label", "crash");
                        deleteCrashFile(context);
                    }

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
                            .url(context.getString(R.string.bug_report_url))
                            .post(requestBody)
                            .build();

                    int responseCode = HttpURLConnection.HTTP_INTERNAL_ERROR;
                    Response response = null;
                    String errorMessage = null;

                    // trigger the request
                    try {
                        mBugReportCall = mOkHttpClient.newCall(request);
                        response = mBugReportCall.execute();
                        responseCode = response.code();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "response " + e.getMessage());
                        errorMessage = e.getLocalizedMessage();
                    }

                    // if the upload failed, try to retrieve the reason
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        if (null != errorMessage) {
                            serverError = "Failed with error " + errorMessage;
                        } else if ((null == response) || (null == response.body())) {
                            serverError = "Failed with error " + responseCode;
                        } else {
                            InputStream is = null;

                            try {
                                is = response.body().byteStream();

                                if (null != is) {
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
                                }
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## sendBugReport() : failed to parse error " + e.getMessage());
                            } finally {
                                try {
                                    if (null != is) {
                                        is.close();
                                    }
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "## sendBugReport() : failed to close the error stream " + e.getMessage());
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

                // delete when the bug report has been successfully sent
                for (File file : mBugReportFiles) {
                    file.delete();
                }

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

    private static Bitmap mScreenshot = null;

    /**
     * Send a bug report either with email or with Vector.
     */
    public static void sendBugReport() {
        mScreenshot = takeScreenshot();

        final Activity currentActivity = VectorApp.getCurrentActivity();

        // no current activity so cannot display an alert
        if (null == currentActivity) {
            sendBugReport(VectorApp.getInstance().getApplicationContext(), true, true, true, "", null);
            return;
        }

        Intent intent = new Intent(currentActivity, BugReportActivity.class);
        currentActivity.startActivity(intent);
    }

    //==============================================================================================================
    // crash report management
    //==============================================================================================================

    /**
     * Provides the crash file
     *
     * @param context the context
     * @return the crash file
     */
    private static File getCrashFile(Context context) {
        return new File(context.getCacheDir().getAbsolutePath(), CRASH_FILENAME);
    }

    /**
     * Remove the crash file
     *
     * @param context
     */
    public static void deleteCrashFile(Context context) {
        File crashFile = getCrashFile(context);

        if (crashFile.exists()) {
            crashFile.delete();
        }
    }

    /**
     * Save the crash report
     *
     * @param context          the context
     * @param crashDescription teh crash description
     */
    public static void saveCrashReport(Context context, String crashDescription) {
        File crashFile = getCrashFile(context);

        if (crashFile.exists()) {
            crashFile.delete();
        }

        if (!TextUtils.isEmpty(crashDescription)) {
            try {
                FileOutputStream fos = new FileOutputStream(crashFile);
                OutputStreamWriter osw = new OutputStreamWriter(fos);
                osw.write(crashDescription);
                osw.close();

                fos.flush();
                fos.close();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## saveCrashReport() : fail to write " + e.toString());
            }
        }
    }

    /**
     * Read the crash description file and return its content.
     *
     * @param context teh context
     * @return the crash description
     */
    private static String getCrashDescription(Context context) {
        String crashDescription = null;
        File crashFile = getCrashFile(context);

        if (crashFile.exists()) {
            try {
                FileInputStream fis = new FileInputStream(crashFile);
                InputStreamReader isr = new InputStreamReader(fis);

                char[] buffer = new char[fis.available()];
                int len = isr.read(buffer, 0, fis.available());
                crashDescription = String.valueOf(buffer, 0, len);
                isr.close();
                fis.close();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getCrashDescription() : fail to read " + e.toString());
            }
        }

        return crashDescription;
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
        } catch (OutOfMemoryError error) {
            Log.e(LOG_TAG, "## saveLogCat() : fail to write logcat" + error.toString());
        } catch (Exception e) {
            Log.e(LOG_TAG, "## saveLogCat() : fail to write logcat" + e.toString());
        }

        return null;
    }

    private static final int BUFFER_SIZE = 1024 * 1024 * 50;

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
