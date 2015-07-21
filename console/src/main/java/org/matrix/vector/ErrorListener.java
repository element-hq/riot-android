/* 
 * Copyright 2014 OpenMarket Ltd
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
package org.matrix.vector;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import org.matrix.androidsdk.rest.callback.ApiFailureCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.vector.R;
import org.matrix.vector.activity.CommonActivityUtils;

public class ErrorListener implements ApiFailureCallback {

    private static final String LOG_TAG = "ErrorListener";

    private Activity mActivity;

    public ErrorListener(Activity activity) {
        mActivity = activity;
    }

    @Override
    public void onNetworkError(Exception e) {
        Log.e(LOG_TAG, "Network error: " + e.getMessage());

        // do not trigger toaster if the application is in background
        if (!VectorApplication.isAppInBackground()) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                Toast.makeText(mActivity, mActivity.getString(R.string.network_error), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onMatrixError(MatrixError e) {
        Log.e(LOG_TAG, "Matrix error: " + e.errcode + " - " + e.error);
        // The access token was not recognized: log out
        if (MatrixError.UNKNOWN_TOKEN.equals(e.errcode)) {
            CommonActivityUtils.logout(mActivity);
        }
    }

    @Override
    public void onUnexpectedError(Exception e) {
        Log.e(LOG_TAG, "Unexpected error: " + e.getMessage());
    }
}
