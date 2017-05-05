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


import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.net.HttpURLConnection;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.adapters.AdapterUtils;


public class CheckForUpdates {
    private static final String LOG_TAG = "CheckForUpdates";

    // the http client
    private static final OkHttpClient mOkHttpClient = new OkHttpClient();


    public static void checkForUpdates() {
        final Activity currentActivity = VectorApp.getCurrentActivity();

        // no current activity so cannot display an alert
        if (null == currentActivity) {
            return;
        }

        final Context appContext = currentActivity.getApplicationContext();
        LayoutInflater inflater = currentActivity.getLayoutInflater();
        View dialogLayout = inflater.inflate(R.layout.dialog_check_for_updates, null);

        final AlertDialog.Builder dialog = new AlertDialog.Builder(currentActivity);
        dialog.setTitle("Update");
        dialog.setView(dialogLayout);

        final TextView textView = (TextView) dialogLayout.findViewById(R.id.check_for_updates_text_view);
        final ProgressBar progressBar = (ProgressBar) dialogLayout.findViewById(R.id.check_for_updates_progress_view);

        dialog.setPositiveButton(R.string.send, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // will be overridden to avoid dismissing the dialog while displaying the progress
            }
        });

        dialog.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        final AlertDialog checkForUpdatesDialog = dialog.show();
        final int currentBuildNumber = appContext.getResources().getInteger(R.integer.jenkins_build_number);

        final Button cancelButton = checkForUpdatesDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        final Button sendButton = checkForUpdatesDialog.getButton(AlertDialog.BUTTON_POSITIVE);

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkForUpdatesDialog.dismiss();
            }
        });

        sendButton.setEnabled(false);

        final String jenkinsJobUrl = appContext.getString(R.string.jenkins_job_url);

        Request request = new Request.Builder()
            .url(jenkinsJobUrl + "api/json?tree=lastSuccessfulBuild[*]")
            .get()
            .build();

        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                currentActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                        textView.setText("Failed to check for updates");
                    }
                });
            }

            @Override
            public void onResponse(final Response response) throws IOException {
                currentActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);

                        if (response.code() != HttpURLConnection.HTTP_OK) {
                            textView.setText("Failed to check for updates");
                        }

                        try {
                            JSONObject json = new JSONObject(response.body().string());
                            JSONObject lastBuild = json.getJSONObject("lastSuccessfulBuild");
                            final int number = lastBuild.getInt("number");

                            if (number == currentBuildNumber) {
                                textView.setText("No new updates available.\n\nCurrent build: #" + number);
                                return;
                            }

                            long timestamp = lastBuild.getLong("timestamp");
                            String formatted_time = AdapterUtils.tsToString(appContext, timestamp, true);
                            textView.setText(
                                String.format(
                                    "Old Build:  #%d\nNew Build:  #%d\nBuilt at:  %s\n",
                                    currentBuildNumber, number, formatted_time
                                )
                            );

                            final Uri apkUri = Uri.parse(
                                    jenkinsJobUrl + "lastSuccessfulBuild/artifact/vector/build/outputs/apk/vector-app-debug.apk"
                            );

                            sendButton.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    DownloadManager downloadManager = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);

                                    DownloadManager.Request request = new DownloadManager.Request(apkUri);
                                    request.setTitle("Riot Update");
                                    request.allowScanningByMediaScanner();
                                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "vector-app-debug.apk");
                                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                                    downloadManager.enqueue(request);
                                    checkForUpdatesDialog.dismiss();
                                }
                            });

                            sendButton.setEnabled(true);
                        } catch (Exception e) {
                            textView.setText("Failed to check for updates");
                        }
                    }
                });
            }
        });
    }
}
