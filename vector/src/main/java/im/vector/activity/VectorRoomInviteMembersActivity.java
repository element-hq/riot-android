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
import org.matrix.androidsdk.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ExpandableListView;
import android.widget.ListView;

import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorParticipantsAdapter;
import im.vector.contacts.Contact;
import im.vector.contacts.ContactsManager;
import im.vector.util.VectorUtils;

/**
 * This class provides a way to search other user to invite them in a dedicated room
 */
public class VectorRoomInviteMembersActivity extends VectorBaseSearchActivity {
    private static final String LOG_TAG = "VectorInviteMembersAct";

    // search in the room
    public static final String EXTRA_ROOM_ID = "VectorInviteMembersActivity.EXTRA_ROOM_ID";
    public static final String EXTRA_HIDDEN_PARTICIPANT_ITEMS = "VectorInviteMembersActivity.EXTRA_HIDDEN_PARTICIPANT_ITEMS";
    public static final String EXTRA_SELECTED_USER_ID =  "VectorInviteMembersActivity.EXTRA_SELECTED_USER_ID";
    public static final String EXTRA_SELECTED_PARTICIPANT_ITEM =  "VectorInviteMembersActivity.EXTRA_SELECTED_PARTICIPANT_ITEM";

    // account data
    private String mMatrixId;

    // main UI items
    private ExpandableListView mListView;
    private View mNoResultView;
    private View mLoadingView;
    private List<ParticipantAdapterItem> mParticipantItems = new ArrayList<>();
    private VectorParticipantsAdapter mAdapter;


    // retrieve a matrix Id from an email
    private final ContactsManager.ContactsManagerListener mContactsListener = new ContactsManager.ContactsManagerListener() {
        @Override
        public void onRefresh() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
                }
            });
        }

        @Override
        public void onContactPresenceUpdate(final Contact contact, final String matrixId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAdapter.onContactUpdate(contact, matrixId, VectorUtils.getVisibleChildViews(mListView, mAdapter));
                }
            });
        }
    };

    // refresh the presence asap
    private final MXEventListener mEventsListener = new MXEventListener() {
        @Override
        public void onPresenceUpdate(final Event event, final User user) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Map<Integer, List<Integer>> visibleChildViews = VectorUtils.getVisibleChildViews(mListView, mAdapter);

                    for(Integer groupPosition : visibleChildViews.keySet()) {
                        List<Integer> childPositions = visibleChildViews.get(groupPosition);

                        for(Integer childPosition : childPositions) {
                            Object item =  mAdapter.getChild(groupPosition, childPosition);

                            if (item instanceof ParticipantAdapterItem) {
                                ParticipantAdapterItem participantAdapterItem = (ParticipantAdapterItem)item;

                                if (TextUtils.equals(user.user_id,  participantAdapterItem.mUserId)) {
                                    mAdapter.notifyDataSetChanged();
                                    break;
                                }
                            }
                        }
                    }
                }
            });
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        if (CommonActivityUtils.isGoingToSplash(this)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen");
            return;
        }

        Intent intent = getIntent();

        if (intent.hasExtra(EXTRA_MATRIX_ID)) {
            mMatrixId = intent.getStringExtra(EXTRA_MATRIX_ID);
        }

        // get current session
        mSession = Matrix.getInstance(getApplicationContext()).getSession(mMatrixId);
        if (null == mSession) {
            finish();
            return;
        }

        if (intent.hasExtra(EXTRA_HIDDEN_PARTICIPANT_ITEMS)) {
            mParticipantItems = (List<ParticipantAdapterItem>)intent.getSerializableExtra(EXTRA_HIDDEN_PARTICIPANT_ITEMS);
        }

        String roomId = intent.getStringExtra(EXTRA_ROOM_ID);

        setContentView(R.layout.activity_vector_invite_members);

        // the user defines a
        if (null != mPatternToSearchEditText) {
            mPatternToSearchEditText.setHint(R.string.room_participants_invite_search_another_user);
        }

        mNoResultView = findViewById(R.id.search_no_result_textview);
        mLoadingView = findViewById(R.id.search_in_progress_view);

        mListView = (ExpandableListView) findViewById(R.id.room_details_members_list);
        // the chevron is managed in the header view
        mListView.setGroupIndicator(null);

        mAdapter = new VectorParticipantsAdapter(this,
                R.layout.adapter_item_vector_add_participants,
                R.layout.adapter_item_vector_people_header,
                mSession, roomId);
        mAdapter.setHiddenParticipantItems(mParticipantItems);
        mListView.setAdapter(mAdapter);

        mListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                Object item = mAdapter.getChild(groupPosition, childPosition);

                if (item instanceof ParticipantAdapterItem) {
                    ParticipantAdapterItem participantAdapterItem = (ParticipantAdapterItem)item;

                    // returns the selected user
                    Intent intent = new Intent();
                    intent.putExtra(EXTRA_SELECTED_USER_ID, participantAdapterItem.mUserId);
                    intent.putExtra(EXTRA_SELECTED_PARTICIPANT_ITEM, participantAdapterItem);

                    setResult(RESULT_OK, intent);
                    finish();

                    return true;
                }
                return false;
            }
        });
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {


            }
        });
    }

    /**
     * The search pattern has been updated
     */
    @Override
    protected void onPatternUpdate(boolean isTypingUpdate) {
        String pattern = mPatternToSearchEditText.getText().toString();

        ParticipantAdapterItem firstEntry = null;

        if (!TextUtils.isEmpty(pattern)) {
            // remove useless spaces
            pattern = pattern.trim();

            // test if the pattern could describe a matrix id.
            // matrix id syntax @XXX:XXX.XX
            if (pattern.startsWith("@")) {
                int pos = pattern.indexOf(":");

                if (pattern.indexOf(".", pos) >= 0) {
                    firstEntry = new ParticipantAdapterItem(pattern, null, pattern);
                }
            } else {
                // email
                if (null != android.util.Patterns.EMAIL_ADDRESS.matcher(pattern)) {
                    firstEntry = new ParticipantAdapterItem(pattern, null, pattern);
                }
            }
        }

        // display a spinner while the other room members are listed
        if (!mAdapter.isKnownMembersInitialized()) {
            mLoadingView.setVisibility(View.VISIBLE);
        }

        mAdapter.setSearchedPattern(pattern, firstEntry, new VectorParticipantsAdapter.OnParticipantsSearchListener() {
            @Override
            public void onSearchEnd(final int count) {
                mListView.post(new Runnable() {
                    @Override
                    public void run() {
                        mLoadingView.setVisibility(View.GONE);

                        boolean hasPattern = !TextUtils.isEmpty(mPatternToSearchEditText.getText());
                        boolean hasResult = (0 != count);
                        mNoResultView.setVisibility((hasPattern && !hasResult) ? View.VISIBLE : View.GONE);

                        mListView.post(new Runnable() {
                            @Override
                            public void run() {
                                // TODO manage expand according to the settings
                                for(int i = 0 ; i < mAdapter.getGroupCount(); i++) {
                                    mListView.expandGroup(i);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        mSession.getDataHandler().removeListener(mEventsListener);
        ContactsManager.removeListener(mContactsListener);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mSession.getDataHandler().addListener(mEventsListener);
        ContactsManager.addListener(mContactsListener);
    }
}