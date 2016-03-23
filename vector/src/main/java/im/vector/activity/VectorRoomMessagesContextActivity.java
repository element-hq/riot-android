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
import im.vector.util.VectorUtils;

/**
 * Displays a room messages search
 */
public class VectorRoomMessagesContextActivity extends MXCActionBarActivity implements VectorRoomMessageContextFragment.IContextEventsListener {
    private static final String LOG_TAG = "MessageContextActivity";

    public static final String EXTRA_ROOM_ID = "VectorRoomMessageContextActivity.EXTRA_ROOM_ID";
    public static final String EXTRA_EVENT_ID = "VectorRoomMessageContextActivity.EXTRA_EVENT_ID";

    private static final String TAG_FRAGMENT_CONTEXT_MESSAGE = "VectorRoomMessagesContextActivity.TAG_FRAGMENT_CONTEXT_MESSAGE";

    // fragement
    private VectorRoomMessageContextFragment mMessagesListFragment;

    // the session
    private MXSession mSession;

    // spinners
    private View mForwardPaginationSpinner;
    private View mBackPaginationSpinner;
    private View mInitializationSpinner;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_room_message_context);

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

        mForwardPaginationSpinner = findViewById(R.id.context_forward_progress);
        mBackPaginationSpinner = findViewById(R.id.context_back_progress);
        mInitializationSpinner = findViewById(R.id.context_initial_loading);

        // set general room information
        mRoom = mSession.getDataHandler().getRoom(roomId);
        setTitle(VectorUtils.getRoomDisplayname(this, mSession, mRoom));

        if (null != mRoom) {
            setTopic(mRoom.getTopic());
        }

        if (mMessagesListFragment == null) {
            mMessagesListFragment = VectorRoomMessageContextFragment.newInstance(myUserId, roomId, eventId, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
            fm.beginTransaction().add(R.id.context_fragment_container, mMessagesListFragment, TAG_FRAGMENT_CONTEXT_MESSAGE).commit();
        }
    }

    /**
     * Init the room topic
     * @param topic the topic
     */
    private void setTopic(String topic) {
        if (null != this.getSupportActionBar()) {
            this.getSupportActionBar().setSubtitle(topic);
        }
    }

    @Override
    public void onDestroy() {
        if (null != mMessagesListFragment) {
            mMessagesListFragment.onDestroy();
            mMessagesListFragment = null;
        }

        super.onDestroy();
    }

    /**
     * Show a spinner when a back pagination is started.
     */
    @Override
    public void showBackPaginationSpinner() {
        mBackPaginationSpinner.setVisibility(View.VISIBLE);
    }

    /**
     * Hide a spinner when a back pagination is ended.
     */
    @Override
    public void hideBackPaginationSpinner() {
        mBackPaginationSpinner.setVisibility(View.GONE);
    }

    /**
     * Show a spinner when a foward pagination is started.
     */
    @Override
    public void showForwardPaginationSpinner() {
        mForwardPaginationSpinner.setVisibility(View.VISIBLE);
    }

    /**
     * Hide a spinner when a foward pagination is started.
     */
    @Override
    public void hideForwardPaginationSpinner() {
        mForwardPaginationSpinner.setVisibility(View.GONE);
    }

    /**
     * Display a spinner when the global initialization is started.
     */
    @Override
    public void showGlobalInitpinner() {
        mInitializationSpinner.setVisibility(View.VISIBLE);
    }

    /**
     * Hide a spinner when the global initialization is done.
     */
    @Override
    public void hideGlobalInitpinner() {
        mInitializationSpinner.setVisibility(View.GONE);
    }

}


