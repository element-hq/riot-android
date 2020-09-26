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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.data.RoomPreviewData;

import im.vector.Matrix;
import im.vector.R;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Dummy activity used to trigger the room activity in preview mode,
 * when the user press the "Open" button within the invitation notification.
 * <p>
 * The use of a dummy Activity is required to delay the build of the {@link RoomPreviewData}
 * after the user pressed the "Open" button on the notification.
 * <br/> Otherwise, {@link VectorRoomActivity#sRoomPreviewData}
 * is overridden too soon and may mess up with any room activity in preview mode
 * currently displayed.
 */
@SuppressLint("LongLogTag")
public class VectorFakeRoomPreviewActivity extends VectorAppCompatActivity {
    private static final String LOG_TAG = VectorFakeRoomPreviewActivity.class.getSimpleName();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_empty;
    }

    @Override
    public void initUiAndData() {
        // keep theme ?

        Intent receivedIntent = getIntent();
        String matrixId;
        MXSession session;

        // check session validity
        if (null == receivedIntent) {
            Log.w(LOG_TAG, "## onCreate(): Failure - received intent is null");
        } else if (null == (matrixId = receivedIntent.getStringExtra(VectorRoomActivity.EXTRA_ROOM_ID))) {
            Log.w(LOG_TAG, "## onCreate(): Failure - matrix ID is null");
        } else {
            // get the session
            if (null == (session = Matrix.getInstance(getApplicationContext()).getSession(matrixId))) {
                session = Matrix.getInstance(getApplicationContext()).getDefaultSession();
            }

            if ((null != session) && session.isAlive()) {
                String roomId = receivedIntent.getStringExtra(VectorRoomActivity.EXTRA_ROOM_ID);
                String roomAlias = receivedIntent.getStringExtra(VectorRoomActivity.EXTRA_ROOM_PREVIEW_ROOM_ALIAS);

                // set preview data object
                RoomPreviewData roomPreviewData = new RoomPreviewData(session, roomId, null, roomAlias, null);
                VectorRoomActivity.sRoomPreviewData = roomPreviewData;

                // forward received intent, to VectorRoomActivity
                Intent nextIntent = new Intent(receivedIntent);
                nextIntent.setClass(this, VectorRoomActivity.class);
                nextIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(nextIntent);
            } else {
                Log.w(LOG_TAG, "## onCreate(): Failure - session is null");
            }
        }
        finish();
    }
}
