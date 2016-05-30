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
import android.util.Log;

import im.vector.Matrix;

/**
 * Dummy activity used to manage the shared
 */
public class VectorSharedFilesActivity extends Activity {
    private static final String LOG_TAG = "VectorSharedFilesAct";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // retrieve the current intent
        Intent anIntent = getIntent();

        String action = anIntent.getAction();
        String type = anIntent.getType();

        Log.d(LOG_TAG, "onCreate : action " + action + " type " + type);

        // send files from external application
        // check the params
        if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
            boolean isAppLaunched = false;

            try {
                isAppLaunched = Matrix.getInstance(this).getDefaultSession() != null;
            } catch (Exception e) {
            }

            // go to the home screen if the application is launched
            if (isAppLaunched) {
                Log.d(LOG_TAG, "onCreate : launch home activity with the files list");

                Intent homeIntent = new Intent(this, VectorHomeActivity.class);
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                // files to share
                homeIntent.putExtra(VectorHomeActivity.EXTRA_SHARED_INTENT_PARAMS, anIntent);
                startActivity(homeIntent);
            } else {
                Log.d(LOG_TAG, "onCreate : go to login screen");

                // don't know what to do, go to the login screen
                Intent loginIntent = new Intent(this, LoginActivity.class);
                startActivity(loginIntent);
            }
        } else {
            Log.d(LOG_TAG, "onCreate : unsupported action");
        }

        finish();
    }
}
