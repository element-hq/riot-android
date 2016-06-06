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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import im.vector.receiver.VectorRegistrationReceiver;
import im.vector.receiver.VectorUniversalLinkReceiver;

/**
 * Dummy activity used to dispatch the vector URL links.
 */
@SuppressLint("LongLogTag")
public class VectorUniversalLinkActivity extends Activity {
    private static final String LOG_TAG = "VectorUniversalLinkActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String intentAction = VectorUniversalLinkReceiver.BROADCAST_ACTION_UNIVERSAL_LINK;

        try {
            // dispatch on the right receiver
            if (VectorRegistrationReceiver.SUPPORTED_PATH_ACCOUNT_EMAIL_VALIDATION.equals(getIntent().getData().getPath())) {

                // logout current session, before starting any mail validation
                // to have the LoginActivity always in a "no credentials state".
                CommonActivityUtils.logout(this, false);
                intentAction = VectorRegistrationReceiver.BROADCAST_ACTION_REGISTRATION;
            } else {
                intentAction = VectorUniversalLinkReceiver.BROADCAST_ACTION_UNIVERSAL_LINK;
            }
        } catch (Exception ex){
            Log.e(LOG_TAG,"## onCreate(): Exception - Msg="+ex.getMessage());
        }

        Intent myBroadcastIntent = new Intent(intentAction, getIntent().getData());
        sendBroadcast(myBroadcastIntent);


        finish();
    }
}
