/*
 * Copyright 2017 Vector Creation Ltd
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

package im.vector.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import org.matrix.androidsdk.util.Log;

import im.vector.activity.CommonActivityUtils;
import im.vector.util.PreferencesManager;

public class VectorBootReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = VectorBootReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(LOG_TAG, "## onReceive() : " + intent.getAction());

        if (TextUtils.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {
            if (PreferencesManager.autoStartOnBoot(context)) {
                Log.d(LOG_TAG, "## onReceive() : starts the application");
                CommonActivityUtils.startEventStreamService(context);
            } else {
                Log.d(LOG_TAG, "## onReceive() : the autostart is disabled");
            }
        }
    }
}
