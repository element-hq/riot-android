/*
 * Copyright 2016 OpenMarket Ltd
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

package im.vector.activity;

import android.content.Context;
import android.content.Intent;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.FileContentUtils;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.data.RoomMediaMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Dummy activity used to manage the shared
 */
public class VectorSharedFilesActivity extends VectorAppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    private static final String LOG_TAG = VectorSharedFilesActivity.class.getSimpleName();

    private final String SHARED_FOLDER = "VectorShared";

    @Override
    public int getLayoutRes() {
        return R.layout.activity_empty;
    }

    @Override
    public void initUiAndData() {
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
                    Log.e(LOG_TAG, "## onCreate() : failed " + e.getMessage(), e);
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
        } else {
            Log.d(LOG_TAG, "onCreate : null intent");

            Intent homeIntent = new Intent(this, VectorHomeActivity.class);
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
        }

        finish();
    }

    /**
     * Extract the medias list, copy them into a tmp directory and provide them to the home activity
     *
     * @param intent        the intent
     * @param isAppLaunched true if the application is resumed
     */
    private void launchActivity(Intent intent, boolean isAppLaunched) {
        File sharedFolder = new File(getCacheDir(), SHARED_FOLDER);

        /**
         * Clear the existing folder to reduce storage memory usage
         */
        if (sharedFolder.exists()) {
            FileContentUtils.deleteDirectory(sharedFolder);
        }

        sharedFolder.mkdir();

        List<RoomMediaMessage> cachedFiles = new ArrayList<>(RoomMediaMessage.listRoomMediaMessages(intent));

        if (null != cachedFiles) {
            for (RoomMediaMessage sharedDataItem : cachedFiles) {
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
            shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, (ArrayList) cachedFiles);
            shareIntent.setExtrasClassLoader(RoomMediaMessage.class.getClassLoader());
            shareIntent.setType("*/*");

            // files to share
            activityIntent.putExtra(VectorHomeActivity.EXTRA_SHARED_INTENT_PARAMS, shareIntent);
        }

        startActivity(activityIntent);
    }
}
