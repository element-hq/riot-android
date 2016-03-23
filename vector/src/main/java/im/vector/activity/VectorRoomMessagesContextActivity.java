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

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;

import im.vector.R;
import im.vector.fragments.VectorRoomMessageContextFragment;
import im.vector.fragments.VectorSearchMessagesListFragment;

/**
 * Displays a room messages search
 */
public class VectorRoomMessagesContextActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = "MessageContextActivity";

    public static final String EXTRA_ROOM_ID = "VectorRoomMessageContextActivity.EXTRA_ROOM_ID";
    public static final String EXTRA_EVENT_ID = "VectorRoomMessageContextActivity.EXTRA_EVENT_ID";

    private static final String TAG_FRAGMENT_CONTEXT_MESSAGE = "VectorRoomMessagesContextActivity.TAG_FRAGMENT_CONTEXT_MESSAGE";

    private VectorRoomMessageContextFragment mMessagesListFragment;

    private MXSession mSession;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_room_messages_search);

        Intent intent = getIntent();

        mSession = getSession(intent);
        if (mSession == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }

        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }

        FragmentManager fm = getSupportFragmentManager();

        mMessagesListFragment = (VectorRoomMessageContextFragment) fm.findFragmentByTag(TAG_FRAGMENT_CONTEXT_MESSAGE);

        String roomId = intent.getStringExtra(EXTRA_ROOM_ID);
        String eventId = intent.getStringExtra(EXTRA_EVENT_ID);
        String myUserId = mSession.getMyUserId();

        if (mMessagesListFragment == null) {
            mMessagesListFragment = VectorRoomMessageContextFragment.newInstance(myUserId, roomId, eventId, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
            fm.beginTransaction().add(R.id.search_fragment_container, mMessagesListFragment, TAG_FRAGMENT_CONTEXT_MESSAGE).commit();
        }
    }
}


