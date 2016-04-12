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
package im.vector.util;

import android.app.Activity;
import android.widget.Toast;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import im.vector.R;

public class UIUtils {

     public static boolean hasFieldChanged(String oldVal, String newVal) {
        if (oldVal == null) {
            return (newVal != null) && (newVal.length() != 0);
        }
        else {
            return !oldVal.equals(newVal);
        }
    }

    public static ApiCallback<Void> buildOnChangeCallback(final Activity activity) {
        return new SimpleApiCallback<Void>(activity) {
            @Override
            public void onSuccess(Void info) {
                if (null != activity) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, R.string.message_changes_successful, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }

            @Override
            public void onMatrixError(final MatrixError e) {
                if (null != activity) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, e.error, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        };
    }
}
