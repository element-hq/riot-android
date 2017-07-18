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

package im.vector.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.util.ContentUtils;

import java.io.File;
import java.util.ArrayList;

import im.vector.Matrix;
import im.vector.util.SharedDataItem;

/**
 * Dummy activity used to manage the shared
 */
public class VectorSharedFilesActivity extends VectorActivity {
    private static final String LOG_TAG = "VectorSharedFilesAct";

    final String SHARED_FOLDER = "VectorShared";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // retrieve the current intent
        Intent anIntent = getIntent();

        if (null != anIntent) {
            String action = anIntent.getAction();
            String type = anIntent.getType();

            Log.d(LOG_TAG, "onCreate : action " + action + " type " + type);

            // send files from external application
            // check the params
            if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
                boolean hasCredentials = false;
                boolean isLaunched = false;

                try {
                    MXSession session = Matrix.getInstance(this).getDefaultSession();

                    if (null != session) {
                        hasCredentials = true;
                        isLaunched = session.getDataHandler().getStore().isReady();
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## onCreate() : failed " + e.getMessage());
                }

                // go to the home screen if the application is launched
                if (hasCredentials) {
                    launchActivity(anIntent, isLaunched);
                } else {
                    Log.d(LOG_TAG, "onCreate : go to login screen");

                    // don't know what to do, go to the login screen
                    Intent loginIntent = new Intent(this, LoginActivity.class);
                    startActivity(loginIntent);
                }
            } else {
                Log.d(LOG_TAG, "onCreate : unsupported action");

                Intent homeIntent = new Intent(this, VectorHomeActivity.class);
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(homeIntent);
            }
        }  else {
            Log.d(LOG_TAG, "onCreate : null intent");

            Intent homeIntent = new Intent(this, VectorHomeActivity.class);
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
        }

        finish();
    }

    /**
     * Extract the medias list, copy them into a tmp directory and provide them to the home activity
     * @param intent the intent
     * @param isAppLaunched true if the application is resumed
     */
    private void launchActivity(Intent intent, boolean isAppLaunched) {
        File sharedFolder = new File(getCacheDir(), SHARED_FOLDER);

        /**
         * Clear the existing folder to reduce storage memory usage
         */
        if (sharedFolder.exists()) {
            ContentUtils.deleteDirectory(sharedFolder);
        }

        sharedFolder.mkdir();

        ArrayList<SharedDataItem> cachedFiles = new ArrayList<>(SharedDataItem.listSharedDataItems(intent));

        if (null != cachedFiles) {
            for(SharedDataItem sharedDataItem : cachedFiles) {
                sharedDataItem.saveMedia(this, sharedFolder);
            }
        }

        Log.d(LOG_TAG, "onCreate : launch home activity with the files list " + cachedFiles.size() + " files");

        Intent activityIntent;

        if (isAppLaunched) {
            activityIntent = new Intent(this, VectorHomeActivity.class);
        } else {
            activityIntent = new Intent(this, SplashActivity.class);
        }

        activityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);

        if (0 != cachedFiles.size()) {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
            shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, cachedFiles);
            shareIntent.setExtrasClassLoader(SharedDataItem.class.getClassLoader());
            shareIntent.setType("*/*");

            // files to share
            activityIntent.putExtra(VectorHomeActivity.EXTRA_SHARED_INTENT_PARAMS, shareIntent);
        }

        startActivity(activityIntent);
    }
}
