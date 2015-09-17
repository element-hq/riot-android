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

package im.vector.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.VectorAddParticipantsAdapter;

public class VectorAddParticipantsActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = "VectorAddActivity";

    // exclude the room ID
    public static final String EXTRA_ROOM_ID = "VectorAddParticipantsActivity.EXTRA_ROOM_ID";

    private MXSession mSession;
    private String mRoomId;
    private Room mRoom;
    private MXMediasCache mxMediasCache;

    private EditText mSearchEdit;
    private TextView mListViewHeaderView;
    private Button mCancelButton;
    private ListView mParticantsListView;

    private VectorAddParticipantsAdapter mAdapter;

    private MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            VectorAddParticipantsActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                        mAdapter.listOtherMembers();
                    }
                }
            });
        }
    };

    /**
     * Refresh the ListView Header.
     * It is displayed only when there is no search in progress.
     */
    private void refreshListViewHeader() {
        if (TextUtils.isEmpty(mSearchEdit.getText())) {
            mListViewHeaderView.setVisibility(View.VISIBLE);
            mListViewHeaderView.setText(getString(R.string.room_participants_multi_participants, mAdapter.getCount()));
        } else {
            mListViewHeaderView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }

        String matrixId = null;
        if (intent.hasExtra(EXTRA_MATRIX_ID)) {
            matrixId = intent.getStringExtra(EXTRA_MATRIX_ID);
        }

        mSession = Matrix.getInstance(getApplicationContext()).getSession(matrixId);

        if (mSession == null) {
            finish();
            return;
        }
        mRoomId = intent.getStringExtra(EXTRA_ROOM_ID);
        mRoom = mSession.getDataHandler().getRoom(mRoomId);
        mxMediasCache = mSession.getMediasCache();

        setContentView(R.layout.activity_vector_add_participants);

        mListViewHeaderView = (TextView)findViewById(R.id.add_participants_listview_header_textview);

        mParticantsListView = (ListView)findViewById(R.id.add_participants_members_list);
        mAdapter = new VectorAddParticipantsAdapter(this, R.layout.adapter_item_vector_add_participants, mSession, mRoomId, mxMediasCache);
        mParticantsListView.setAdapter(mAdapter);

        mSearchEdit = (EditText)findViewById(R.id.add_participants_search_participants);
        mSearchEdit.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                mAdapter.setSearchedPattern(s.toString());
                refreshListViewHeader();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        mCancelButton = (Button)findViewById(R.id.add_participants_cancel_search_button);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSearchEdit.setText("");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mRoom.addEventListener(mEventListener);
        mAdapter.listOtherMembers();
        mAdapter.refresh();
        refreshListViewHeader();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mRoom.removeEventListener(mEventListener);
    }
}
