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
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;
import org.matrix.androidsdk.rest.json.ConditionDeserializer;
import org.matrix.androidsdk.rest.model.bingrules.Condition;
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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.Matrix;

/**
 * BugReporter creates and sends the bug reports.
 */
public class BugReporter {

    private static final String LOG_TAG = "BugReporter";

    /**
     * Bug report parameters class
     */
    private static class BugReportParams {
        // logs
        List<Map<String, String>> logs;

        // bug description
        String text;

        // the application version
        String version;

        // the user agent
        final String user_agent = "Android";
    }

    /**
     * Bug report upload listener
     */
    private interface IMXBugReportListener {
        /**
         * The bug report upload failed.
         *
         * @param reason the failure reason
         */
        void onUploadFailed(String reason);

        /**
         * The upload progress (in percent)
         * @param progress the upload progress
         */
        void onProgress(int progress);

        /**
         * The bug report upload succeeded.
         */
        void onUploadSucceed();
    }

    /**
     * GSON management
     */
    private static final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .registerTypeAdapter(Condition.class, new ConditionDeserializer())
            .create();

    /**
     * Read the file content as String
     *
     * @param fin the input file
     * @return the file content as String
     */
    private static String convertStreamToString(File fin) {
        Reader reader = null;

        try {
            Writer writer = new StringWriter();
            InputStream inputStream = new FileInputStream(fin);
            try {
                reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                int n;

                char[] buffer = new char[2048];
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                try {
                    if (null != reader) {
                        reader.close();
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## convertStreamToString() failed to close inputStream " + e.getMessage());
                }
            }
            return writer.toString();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## convertStreamToString() failed " + e.getMessage());
        } catch (OutOfMemoryError oom) {
            Log.e(LOG_TAG, "## convertStreamToString() failed " + oom.getMessage());
        } finally {
            try {
                if (null != reader) {
                    reader.close();
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## convertStreamToString() failed to close inputStream " + e.getMessage());
            }
        }

        return "";
    }

    /**
     * Send a bug report.
     *
     * @param context         the application context
     * @param withDevicesLogs true to include the device logs
     * @param withCrashLogs   true to include the crash logs
     */
    private static void sendBugReport(final Context context, final boolean withDevicesLogs, final boolean withCrashLogs, final String bugDescription, final IMXBugReportListener listener) {
        new AsyncTask<Void, Integer, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                BugReportParams params = new BugReportParams();

                params.logs = new ArrayList<>();

                // the logs are optional
                if (withDevicesLogs) {
                    try {
                        List<File> files = org.matrix.androidsdk.util.Log.addLogFiles(new ArrayList<File>());
                        for (File f : files) {
                            HashMap<String, String> map = new HashMap<>();
                            map.put("lines", convertStreamToString(f));
                            params.logs.add(map);
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "doInBackground ; failed to build bug report " + e.getMessage());

                        if (TextUtils.isEmpty(e.getLocalizedMessage())) {
                            return "Failed to upload";
                        }

                        return e.getLocalizedMessage();
                    } catch (OutOfMemoryError oom) {
                        Log.e(LOG_TAG, "doInBackground ; failed to build bug report " + oom.getMessage());

                        if (TextUtils.isEmpty(oom.getLocalizedMessage())) {
                            return"Out ouf memory";
                        }

                        return oom.getLocalizedMessage();
                    }
                }

                if (withCrashLogs || withDevicesLogs) {
                    try {
                        HashMap<String, String> map = new HashMap<>();
                        map.put("lines", getLogCatError());
                        params.logs.add(map);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "doInBackground ; failed to build bug report " + e.getMessage());

                        if (TextUtils.isEmpty(e.getLocalizedMessage())) {
                            return "Failed to upload";
                        }

                        return e.getLocalizedMessage();
                    } catch (OutOfMemoryError oom) {
                        Log.e(LOG_TAG, "doInBackground ; failed to build bug report " + oom.getMessage());

                        if (TextUtils.isEmpty(oom.getLocalizedMessage())) {
                            return"Out ouf memory";
                        }

                        return oom.getLocalizedMessage();
                    }
                }

                params.text = bugDescription;

                // provides the version of each items
                params.version = "";

                if (null != Matrix.getInstance(context).getDefaultSession()) {
                    params.version += "User : " + Matrix.getInstance(context).getDefaultSession().getMyUserId() + "\n";
                }

                params.version += "Phone : " + Build.MODEL.trim() + " (" + Build.VERSION.INCREMENTAL + " " + Build.VERSION.RELEASE + " " + Build.VERSION.CODENAME + ")\n";
                params.version += "Vector version: " + Matrix.getInstance(context).getVersion(true) + "\n";
                params.version += "SDK version:  " + Matrix.getInstance(context).getDefaultSession().getVersion(true) + "\n";
                params.version += "Olm version:  " + Matrix.getInstance(context).getDefaultSession().getCryptoVersion(context, true) + "\n";

                // the screenshot is defined here
                // File screenFile = new File(VectorApp.mLogsDirectoryFile, "screenshot.jpg");
                ByteArrayInputStream inputStream = null;
                String serverError = null;
                HttpURLConnection conn = null;
                try {
                    inputStream = new ByteArrayInputStream(gson.toJsonTree(params).toString().getBytes("UTF-8"));

                    final int dataLen = inputStream.available();

                    // should never happen
                    if (0 == dataLen) {
                        return "No data";
                    }

                    URL url = new URL(context.getResources().getString(R.string.bug_report_url));
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setDoInput(true);
                    conn.setDoOutput(true);
                    conn.setUseCaches(false);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Content-Length", Integer.toString(dataLen));
                    // avoid caching data before really sending them.
                    conn.setFixedLengthStreamingMode(inputStream.available());

                    conn.connect();

                    DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

                    byte[] buffer = new byte[2048];

                    // read file and write it into form...
                    int bytesRead;
                    int totalWritten = 0;

                    while ((bytesRead = inputStream.read(buffer, 0, buffer.length)) > 0) {
                        dos.write(buffer, 0, bytesRead);
                        totalWritten += bytesRead;
                        publishProgress(totalWritten * 100 / dataLen);
                    }

                    dos.flush();
                    dos.close();

                    int mResponseCode;

                    try {
                        // Read the SERVER RESPONSE
                        mResponseCode = conn.getResponseCode();
                    } catch (EOFException eofEx) {
                        mResponseCode = HttpURLConnection.HTTP_INTERNAL_ERROR;
                    }

                    // if the upload failed, try to retrieve the reason
                    if (mResponseCode != HttpURLConnection.HTTP_OK) {
                        serverError = null;
                        InputStream is = conn.getErrorStream();

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
                                serverError = "Failed with error " + mResponseCode;
                            }

                            is.close();
                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "doInBackground ; failed with error " + e.getClass() + " - " + e.getMessage());
                    serverError = e.getLocalizedMessage();

                    if (TextUtils.isEmpty(serverError)) {
                        serverError = "Failed to upload";
                    }
                } catch (OutOfMemoryError oom) {
                    Log.e(LOG_TAG, "doInBackground ; failed to send the bug report " + oom.getMessage());
                    serverError = oom.getLocalizedMessage();

                    if (TextUtils.isEmpty(serverError)) {
                        serverError = "Out ouf memory";
                    }

                } finally {
                    try {
                        if (null != conn) {
                            conn.disconnect();
                        }
                    } catch (Exception e2) {
                        Log.e(LOG_TAG, "doInBackground : conn.disconnect() failed " + e2.getMessage());
                    }
                }

                if (null != inputStream) {
                    try {
                        inputStream.close();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "doInBackground ; failed to close the inputStream " + e.getMessage());
                    }
                }

                return serverError;
            }

            @Override
            protected void onProgressUpdate(Integer ... progress) {
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
                if (null != listener) {
                    try {
                        if (null == reason) {
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
            sendBugReport(VectorApp.getInstance().getApplicationContext(), true, true,  "", null);
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
                dialog.dismiss();
            }
        });

        //
        final AlertDialog bugReportDialog = dialog.show();
        final Button cancelButton = bugReportDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        final Button sendButton = bugReportDialog.getButton(AlertDialog.BUTTON_POSITIVE);

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

                    if (null != cancelButton) {
                        cancelButton.setEnabled(false);
                    }

                    progressTextView.setVisibility(View.VISIBLE);
                    progressTextView.setText(appContext.getString(R.string.send_bug_report_progress, 0 + ""));

                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(0);

                    sendBugReport(VectorApp.getInstance(), includeLogsButton.isChecked(),includeCrashLogsButton.isChecked(),  bugReportText.getText().toString(), new IMXBugReportListener() {
                        @Override
                        public void onUploadFailed(String reason) {
                            try {
                                if (null != VectorApp.getInstance()) {
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

                                if (null != cancelButton) {
                                    cancelButton.setEnabled(true);
                                }

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

    private static final int BUFFER_SIZE = 1024 * 1024 * 5;
    private static final String[] LOGCAT_CMD = new String[]{
            "logcat", ///< Run 'logcat' command
            "-d",  ///< Dump the log rather than continue outputting it
            "-v", // formatting
            "threadtime", // include timestamps
            "AndroidRuntime:E " + ///< Pick all AndroidRuntime errors (such as uncaught exceptions)"communicatorjni:V " + ///< All communicatorjni logging
                    "libcommunicator:V " + ///< All libcommunicator logging
                    "DEBUG:V " + ///< All DEBUG logging - which includes native land crashes (seg faults, etc)
                    "*:S" ///< Everything else silent, so don't pick it..
    };


    /**
     * Retrieves the logs
     * @return the logs.
     */
    private static String getLogCatError() {
        Process logcatProc;

        try {
            logcatProc = Runtime.getRuntime().exec(LOGCAT_CMD);
        } catch (IOException e1) {
            return "";
        }

        BufferedReader reader = null;
        String response = "";
        try {
            String separator = System.getProperty("line.separator");
            StringBuilder sb = new StringBuilder();
            reader = new BufferedReader(new InputStreamReader(logcatProc.getInputStream()), BUFFER_SIZE);
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append(separator);
            }
            response = sb.toString();
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
        return response;
    }
}
