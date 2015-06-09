/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.console.gcm;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.console.ErrorListener;
import org.matrix.console.Matrix;
import org.matrix.console.activity.CommonActivityUtils;
import org.matrix.console.services.EventStreamService;

import java.util.ArrayList;
import java.util.Collection;

public class MatrixGcmListenerService extends GcmListenerService {

    private static final String LOG_TAG = "GcmListenerService";
    private Boolean mCheckLaunched = false;

    /**
     * Called when message is received.
     *
     * @param from SenderID of the sender.
     * @param data Data bundle containing message data as key/value pairs.
     *             For Set of keys use data.keySet().
     */
    @Override
    public void onMessageReceived(String from, Bundle data) {
        Log.d(LOG_TAG, " onMessageReceived ");

        for (String key : data.keySet()) {
            Log.e(LOG_TAG, " >>> " + key + " : " + data.get(key));
        }
        // check if the application has been launched once
        // the first GCM event could have been triggered whereas the application is not yet launched.
        // so it is required to create the sessions and to start/resume event stream
        if (!mCheckLaunched && (null != Matrix.getInstance(getApplicationContext()).getDefaultSession())) {
            ArrayList<String> matrixIds = new ArrayList<String>();
            Collection<MXSession> sessions = Matrix.getInstance(getApplicationContext()).getSessions();

            Log.d(LOG_TAG, " onMessageReceived getSessions " + sessions.size());

            for(MXSession session : sessions) {
                Boolean isSessionReady = session.getDataHandler().getStore().isReady();

                if (!isSessionReady) {
                    session.getDataHandler().getStore().open();
                }

                // session to activate
                matrixIds.add(session.getCredentials().userId);
            }

            if (EventStreamService.getInstance() == null) {
                Log.d(LOG_TAG, " The application is not yet launched");
                // Start the event stream service
                Intent intent = new Intent(this, EventStreamService.class);
                intent.putExtra(EventStreamService.EXTRA_MATRIX_IDS, matrixIds.toArray(new String[matrixIds.size()]));
                intent.putExtra(EventStreamService.EXTRA_STREAM_ACTION, EventStreamService.StreamAction.START.ordinal());
                startService(intent);
            }

            mCheckLaunched = true;
        }
        
        CommonActivityUtils.catchupEventStream(this);
    }
}
