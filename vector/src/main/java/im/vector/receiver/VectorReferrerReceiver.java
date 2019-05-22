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
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import org.matrix.androidsdk.core.Log;

import java.net.URLDecoder;

import im.vector.VectorApp;
import im.vector.activity.LoginActivity;
import im.vector.repositories.ServerUrlsRepository;

public class VectorReferrerReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = VectorReferrerReceiver.class.getSimpleName();

    private static final String INSTALL_REFERRER_ACTION = "com.android.vending.INSTALL_REFERRER";
    private static final String KEY_REFERRER = "referrer";

    private static final String KEY_HS = "hs";
    private static final String KEY_IS = "is";

    private static final String UTM_SOURCE = "utm_source";
    private static final String UTM_CONTENT = "utm_content";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (null == intent) {
            Log.e(LOG_TAG, "No intent");
            return;
        }

        Log.d(LOG_TAG, "## onReceive() : " + intent.getAction());
        if (TextUtils.equals(intent.getAction(), INSTALL_REFERRER_ACTION)) {

            Bundle extras = intent.getExtras();
            if (null == extras) {
                Log.e(LOG_TAG, "No extra");
                return;
            }

            String hs = "";
            String is = "";

            try {
                String referrer = (String) extras.get(KEY_REFERRER);

                Log.d(LOG_TAG, "## onReceive() : referrer " + referrer);

                if (!TextUtils.isEmpty(referrer)) {
                    Uri dummyUri = Uri.parse("https://dummy?" + URLDecoder.decode(referrer, "utf-8"));

                    String utm_source = dummyUri.getQueryParameter(UTM_SOURCE);
                    String utm_content = dummyUri.getQueryParameter(UTM_CONTENT);

                    Log.d(LOG_TAG, "## onReceive() : utm_source " + utm_source + " -- utm_content " + utm_content);

                    if (null != utm_content) {
                        dummyUri = Uri.parse("https://dummy?" + URLDecoder.decode(utm_content, "utf-8"));

                        hs = dummyUri.getQueryParameter(KEY_HS);
                        is = dummyUri.getQueryParameter(KEY_IS);
                    }
                }
            } catch (Throwable t) {
                Log.e(LOG_TAG, "## onReceive() : failed " + t.getMessage(), t);
            }

            Log.d(LOG_TAG, "## onReceive() : HS " + hs);
            Log.d(LOG_TAG, "## onReceive() : IS " + is);


            if (!TextUtils.isEmpty(hs) || !TextUtils.isEmpty(is)) {
                ServerUrlsRepository.INSTANCE.setDefaultUrlsFromReferrer(context, hs, is);

                if ((null != VectorApp.getCurrentActivity()) && (VectorApp.getCurrentActivity() instanceof LoginActivity)) {
                    Log.d(LOG_TAG, "## onReceive() : warn loginactivity");
                    ((LoginActivity) VectorApp.getCurrentActivity()).onServerUrlsUpdateFromReferrer();
                }
            }
        }
    }
}
