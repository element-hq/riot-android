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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
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
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

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
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.Util;

import okio.Buffer;
import okio.BufferedSink;
import okio.ByteString;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.Matrix;
import okio.BufferedSink;
import okio.ByteString;

/**
 * BugReporter creates and sends the bug reports.
 */
public class BugReporter {
    private static final String LOG_TAG = "BugReporter";

    public static class MultipartBody extends RequestBody {
        /**
         * The media-type multipart/form-data follows the rules of all multipart MIME data streams as
         * outlined in RFC 2046. In forms, there are a series of fields to be supplied by the user who
         * fills out the form. Each field has a name. Within a given form, the names are unique.
         */
        public static final MediaType FORM = MediaType.parse("multipart/form-data");

        private static final byte[] COLONSPACE = {':', ' '};
        private static final byte[] CRLF = {'\r', '\n'};
        private static final byte[] DASHDASH = {'-', '-'};

        private final ByteString boundary;
        private final MediaType originalType;
        private final MediaType contentType;
        private final List<Part> parts;
        private long contentLength = -1L;

        MultipartBody(ByteString boundary, MediaType type, List<Part> parts) {
            this.boundary = boundary;
            this.originalType = type;
            this.contentType = MediaType.parse(type + "; boundary=" + boundary.utf8());
            this.parts = Util.immutableList(parts);
        }

        public MediaType type() {
            return originalType;
        }

        public String boundary() {
            return boundary.utf8();
        }

        /** The number of parts in this multipart body. */
        public int size() {
            return parts.size();
        }

        public List<Part> parts() {
            return parts;
        }

        public Part part(int index) {
            return parts.get(index);
        }

        /** A combination of {@link #type()} and {@link #boundary()}. */
        @Override public MediaType contentType() {
            return contentType;
        }

        @Override public long contentLength() throws IOException {
            long result = contentLength;
            if (result != -1L) return result;
            return contentLength = writeOrCountBytes(null, true);
        }

        @Override public void writeTo(BufferedSink sink) throws IOException {
            writeOrCountBytes(sink, false);
        }

        /**
         * Either writes this request to {@code sink} or measures its content length. We have one method
         * do double-duty to make sure the counting and content are consistent, particularly when it comes
         * to awkward operations like measuring the encoded length of header strings, or the
         * length-in-digits of an encoded integer.
         */
        private long writeOrCountBytes(BufferedSink sink, boolean countBytes) throws IOException {
            long byteCount = 0L;

            Buffer byteCountBuffer = null;
            if (countBytes) {
                sink = byteCountBuffer = new Buffer();
            }

            for (int p = 0, partCount = parts.size(); p < partCount; p++) {
                Part part = parts.get(p);
                Headers headers = part.headers;
                RequestBody body = part.body;

                sink.write(DASHDASH);
                sink.write(boundary);
                sink.write(CRLF);

                if (headers != null) {
                    for (int h = 0, headerCount = headers.size(); h < headerCount; h++) {
                        sink.writeUtf8(headers.name(h))
                                .write(COLONSPACE)
                                .writeUtf8(headers.value(h))
                                .write(CRLF);
                    }
                }

                MediaType contentType = body.contentType();
                if (contentType != null) {
                    sink.writeUtf8("Content-Type: ")
                            .writeUtf8(contentType.toString())
                            .write(CRLF);
                }

                int contentLength = (int)body.contentLength();
                if (contentLength != -1) {
                    sink.writeUtf8("Content-Length: ")
                            .writeUtf8(contentLength+"")
                            .write(CRLF);
                } else if (countBytes) {
                    // We can't measure the body's size without the sizes of its components.
                    byteCountBuffer.clear();
                    return -1L;
                }

                sink.write(CRLF);

                if (countBytes) {
                    byteCount += contentLength;
                } else {
                    body.writeTo(sink);
                }

                sink.write(CRLF);
            }

            sink.write(DASHDASH);
            sink.write(boundary);
            sink.write(DASHDASH);
            sink.write(CRLF);

            if (countBytes) {
                byteCount += byteCountBuffer.size();
                byteCountBuffer.clear();
            }

            return byteCount;
        }

        /**
         * Appends a quoted-string to a StringBuilder.
         *
         * <p>RFC 2388 is rather vague about how one should escape special characters in form-data
         * parameters, and as it turns out Firefox and Chrome actually do rather different things, and
         * both say in their comments that they're not really sure what the right approach is. We go with
         * Chrome's behavior (which also experimentally seems to match what IE does), but if you actually
         * want to have a good chance of things working, please avoid double-quotes, newlines, percent
         * signs, and the like in your field names.
         */
        static StringBuilder appendQuotedString(StringBuilder target, String key) {
            target.append('"');
            for (int i = 0, len = key.length(); i < len; i++) {
                char ch = key.charAt(i);
                switch (ch) {
                    case '\n':
                        target.append("%0A");
                        break;
                    case '\r':
                        target.append("%0D");
                        break;
                    case '"':
                        target.append("%22");
                        break;
                    default:
                        target.append(ch);
                        break;
                }
            }
            target.append('"');
            return target;
        }

        public static final class Part {
            public static Part create(RequestBody body) {
                return create(null, body);
            }

            public static Part create(Headers headers, RequestBody body) {
                if (body == null) {
                    throw new NullPointerException("body == null");
                }
                if (headers != null && headers.get("Content-Type") != null) {
                    throw new IllegalArgumentException("Unexpected header: Content-Type");
                }
                if (headers != null && headers.get("Content-Length") != null) {
                    throw new IllegalArgumentException("Unexpected header: Content-Length");
                }
                return new Part(headers, body);
            }

            public static Part createFormData(String name, String value) {
                return createFormData(name, null, RequestBody.create(null, value));
            }

            public static Part createFormData(String name, String filename, RequestBody body) {
                if (name == null) {
                    throw new NullPointerException("name == null");
                }
                StringBuilder disposition = new StringBuilder("form-data; name=");
                appendQuotedString(disposition, name);

                if (filename != null) {
                    disposition.append("; filename=");
                    appendQuotedString(disposition, filename);
                }

                return create(Headers.of("Content-Disposition", disposition.toString()), body);
            }

            final Headers headers;
            final RequestBody body;

            private Part(Headers headers, RequestBody body) {
                this.headers = headers;
                this.body = body;
            }

            public Headers headers() {
                return headers;
            }

            public RequestBody body() {
                return body;
            }
        }

        public static final class Builder {
            private final ByteString boundary;
            private MediaType type = MultipartBody.FORM;
            private final List<Part> parts = new ArrayList<>();

            public Builder() {
                this(UUID.randomUUID().toString());
            }

            public Builder(String boundary) {
                this.boundary = ByteString.encodeUtf8(boundary);
            }
            
            /** Add a form data part to the body. */
            public Builder addFormDataPart(String name, String value) {
                return addPart(Part.createFormData(name, value));
            }

            /** Add a form data part to the body. */
            public Builder addFormDataPart(String name, String filename, RequestBody body) {
                return addPart(Part.createFormData(name, filename, body));
            }

            /** Add a part to the body. */
            public Builder addPart(Part part) {
                if (part == null) throw new NullPointerException("part == null");
                parts.add(part);
                return this;
            }

            /** Assemble the specified parts into a request body. */
            public MultipartBody build() {
                if (parts.isEmpty()) {
                    throw new IllegalStateException("Multipart body must have at least one part.");
                }
                return new MultipartBody(boundary, type, parts);
            }
        }
    }

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
     * GZip a file
     *
     * @param fin the input file
     * @return the gzipped file
     */
    private static File compressFile(File fin) {
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
            try {
                int n;

                byte[] buffer = new byte[2048];
                while ((n = inputStream.read(buffer)) != -1) {
                    gos.write(buffer, 0, n);
                }

                gos.close();
                inputStream.close();
            } finally {
                try {
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## compressFile() failed to close inputStream " + e.getMessage());
                }
            }

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

    // boolean to cancel the bug report
    private static boolean mIsCancelled = false;

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
                File bugReportFile = new File(context.getApplicationContext().getFilesDir(), "bug_report");

                if (bugReportFile.exists()) {
                    bugReportFile.delete();
                }

                ArrayList<File> gzippedFiles = new ArrayList<>();

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
                    // TODO add locat
                    // getLogCatError()
                }

                /*

                body.append('text', opts.userText || "User did not supply any additional text.");
    body.append('app', 'riot-web');
    body.append('version', version);
    body.append('user_agent', userAgent);

                 */
                String version = "";

                if (null != Matrix.getInstance(context).getDefaultSession()) {
                    version += "User : " + Matrix.getInstance(context).getDefaultSession().getMyUserId() + "\n";
                }

                version += "Phone : " + Build.MODEL.trim() + " (" + Build.VERSION.INCREMENTAL + " " + Build.VERSION.RELEASE + " " + Build.VERSION.CODENAME + ")\n";
                version += "Vector version: " + Matrix.getInstance(context).getVersion(true) + "\n";
                version += "SDK version:  " + Matrix.getInstance(context).getDefaultSession().getVersion(true) + "\n";
                version += "Olm version:  " + Matrix.getInstance(context).getDefaultSession().getCryptoVersion(context, true) + "\n";


                MultipartBody.Builder builder = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("text", bugDescription)
                        .addFormDataPart("app", "Android")
                        .addFormDataPart("user_agent", "Android")
                        .addFormDataPart("version", version);


                for(File file : gzippedFiles) {
                    builder.addFormDataPart("compressed-log", file.getName(),
                            RequestBody.create(MediaType.parse("application/octet-stream"), file));
                }

                       // .addFormDataPart("image", "logo-square.png",
                          //      RequestBody.create( MediaType.parse("image/png"), new File("website/static/logo-square.png"))
                        //
                RequestBody requestBody =  builder.build();

                Request request = new Request.Builder()
                        //.header("Authorization", "Client-ID")
                        .url(context.getResources().getString(R.string.bug_report_url))
                        .post(requestBody)
                        .build();

                OkHttpClient client = new OkHttpClient();

                Response response = null;

                try {
                    Call call = client.newCall(request);

                    //call.cancel();

                    response = call.execute();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "response " + e.getMessage());
                }

                Log.e(LOG_TAG, "test " + response);


                /*try () {
                    if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                    System.out.println(response.body().string());
                }*/







                return "";
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

                    progressTextView.setVisibility(View.VISIBLE);
                    progressTextView.setText(appContext.getString(R.string.send_bug_report_progress, 0 + ""));

                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(0);

                    sendBugReport(VectorApp.getInstance(), includeLogsButton.isChecked(),includeCrashLogsButton.isChecked(),  bugReportText.getText().toString(), new IMXBugReportListener() {
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
