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
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorAddParticipantsAdapter;

/**
 * This class provides a way to search other user to invite them in a dedicated room
 */
public class VectorRoomInviteMembersActivity extends VectorBaseSearchActivity {
    private static final String LOG_TAG = "VectorInviteMembersAct";

    // search in the room
    public static final String EXTRA_ROOM_ID = "VectorInviteMembersActivity.EXTRA_ROOM_ID";
    public static final String EXTRA_SELECTED_USER_ID =  "VectorInviteMembersActivity.EXTRA_SELECTED_USER_ID";

    // account data
    private String mRoomId;
    private String mMatrixId;

    // main UI items
    private ListView mListView;
    private ImageView mBackgroundImageView;
    private View mNoResultView;
    private View mLoadingView;

    private VectorAddParticipantsAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        if (CommonActivityUtils.shouldRestartApp()) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }

        if (intent.hasExtra(EXTRA_MATRIX_ID)) {
            mMatrixId = intent.getStringExtra(EXTRA_MATRIX_ID);
        }

        // get current session
        mSession = Matrix.getInstance(getApplicationContext()).getSession(mMatrixId);
        if (null == mSession) {
            finish();
            return;
        }

        mRoomId = intent.getStringExtra(EXTRA_ROOM_ID);

        setContentView(R.layout.activity_vector_invite_members);

        mBackgroundImageView = (ImageView)findViewById(R.id.search_background_imageview);
        mNoResultView = findViewById(R.id.search_no_result_textview);
        mLoadingView = findViewById(R.id.search_in_progress_view);

        mListView = (ListView) findViewById(R.id.room_details_members_list);
        mAdapter = new VectorAddParticipantsAdapter(this, R.layout.adapter_item_vector_add_participants, mSession, mRoomId, false, mSession.getMediasCache());
        mListView.setAdapter(mAdapter);

        mAdapter.setOnParticipantsListener(new VectorAddParticipantsAdapter.OnParticipantsListener() {
            @Override
            public void onRemoveClick(ParticipantAdapterItem participant) {
            }

            @Override
            public void onLeaveClick() {
            }

            @Override
            public void onSelectUserId(String userId) {

            }

            @Override
            public void onClick(int position) {
                // returns the selected user
                Intent intent = new Intent();
                intent.putExtra(EXTRA_SELECTED_USER_ID, mAdapter.getItem(position).mUserId);
                setResult(RESULT_OK, intent);
                finish();
            }
        });

        /*
        -> do not use the on click listener because the adapter manages scrollview inside the cell view
        So the scrollview events must manage the click events.
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            }
        });*/

        manageBackground();
    }

    /**
     * The search pattern has been updated
     */
    protected void onPatternUpdate() {
        manageBackground();

        String pattern = mPatternToSearchEditText.getText().toString();

        ParticipantAdapterItem firstEntry = null;

        if (!TextUtils.isEmpty(pattern)) {
            firstEntry = new ParticipantAdapterItem(pattern, null, pattern);
        }

        mLoadingView.setVisibility(View.VISIBLE);

        mAdapter.setSearchedPattern(pattern, firstEntry, new VectorAddParticipantsAdapter.OnParticipantsSearchListener() {
            @Override
            public void onSearchEnd(final int count) {
                mListView.post(new Runnable() {
                    @Override
                    public void run() {
                        mLoadingView.setVisibility(View.GONE);

                        boolean hasPattern = !TextUtils.isEmpty(mPatternToSearchEditText.getText());
                        boolean hasResult = (0 != count);
                        mNoResultView.setVisibility((hasPattern && !hasResult) ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });
    }

    /**
     * Hide/show background/listview according to the text length
     */
    private void manageBackground() {
        boolean emptyText = TextUtils.isEmpty(mPatternToSearchEditText.getText().toString());

        mBackgroundImageView.setVisibility(emptyText ? View.VISIBLE : View.GONE);
        mListView.setVisibility(emptyText ? View.GONE : View.VISIBLE);
    }
}