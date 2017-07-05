/*
 * Copyright 2015 OpenMarket Ltd
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
import android.text.TextUtils;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;

import im.vector.Matrix;

/**
 * JoinScreenActivity is a dummy activity to join / reject a room invitation
 */
public class JoinScreenActivity extends VectorActivity {
    public static final String LOG_TAG = "JoinScreenActivity";

    public static final String EXTRA_ROOM_ID = "EXTRA_ROOM_ID";
    public static final String EXTRA_MATRIX_ID = "EXTRA_MATRIX_ID";
    // boolean : true to join the room without opening the application
    public static final String EXTRA_JOIN = "EXTRA_JOIN";
    // boolean : true to reject the room invitation
    public static final String EXTRA_REJECT = "EXTRA_REJECT";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        String roomId = intent.getStringExtra(EXTRA_ROOM_ID);
        String matrixId = intent.getStringExtra(EXTRA_MATRIX_ID);
        boolean join = intent.getBooleanExtra(EXTRA_JOIN, false);
        boolean reject = intent.getBooleanExtra(EXTRA_REJECT, false);

        if (TextUtils.isEmpty(roomId) || TextUtils.isEmpty(matrixId)) {
            Log.e(LOG_TAG, "## onCreate() : invalid parameters");
            finish();
            return;
        }

        MXSession session = Matrix.getInstance(getApplicationContext()).getSession(matrixId);
        Room room = session.getDataHandler().getRoom(roomId);

        if ((null == session) || (null == room)) {
            Log.e(LOG_TAG, "## onCreate() : undefined parameters");
            finish();
            return;
        }

        if (join) {
            Log.d(LOG_TAG, "## onCreate() : Join the room " + roomId);

            room.join(new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void v) {
                    Log.d(LOG_TAG, "## onCreate() : join succeeds");
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.e(LOG_TAG, "## onCreate() : join fails " + e.getMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.e(LOG_TAG, "## onCreate() : join fails " + e.getLocalizedMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.e(LOG_TAG, "## onCreate() : join fails " + e.getMessage());
                }
            });
        } else if (reject) {
            Log.d(LOG_TAG, "## onCreate() : Leave the room " + roomId);

            room.leave(new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    Log.d(LOG_TAG, "## onCreate() : Leave succeeds");
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.e(LOG_TAG, "## onCreate() : Leave fails " + e.getMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.e(LOG_TAG, "## onCreate() : Leave fails " + e.getLocalizedMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.e(LOG_TAG, "## onCreate() : Leave fails " + e.getMessage());
                }
            });
        }

        finish();
    }
}
