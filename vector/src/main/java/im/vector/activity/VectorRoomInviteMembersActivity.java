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
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;

import java.util.Comparator;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorParticipantsAdapter;
import im.vector.contacts.Contact;
import im.vector.contacts.ContactsManager;

/**
 * This class provides a way to search other user to invite them in a dedicated room
 */
public class VectorRoomInviteMembersActivity extends VectorBaseSearchActivity {
    private static final String LOG_TAG = "VectorInviteMembersAct";

    // search in the room
    public static final String EXTRA_ROOM_ID = "VectorInviteMembersActivity.EXTRA_ROOM_ID";
    public static final String EXTRA_SELECTED_USER_ID =  "VectorInviteMembersActivity.EXTRA_SELECTED_USER_ID";

    // account data
    private String mMatrixId;

    // main UI items
    private ListView mListView;
    private ImageView mBackgroundImageView;
    private View mNoResultView;
    private View mLoadingView;
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
                    mAdapter.onContactUpdate(contact, matrixId, mListView.getFirstVisiblePosition(), mListView.getLastVisiblePosition());
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
                    int firstIndex = mListView.getFirstVisiblePosition();
                    int lastIndex = mListView.getLastVisiblePosition();

                    for(int index = firstIndex; index <= lastIndex; index++) {
                        if (TextUtils.equals(user.user_id,  mAdapter.getItem(index).mUserId)) {
                            mAdapter.notifyDataSetChanged();
                            break;
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

        String roomId = intent.getStringExtra(EXTRA_ROOM_ID);

        setContentView(R.layout.activity_vector_invite_members);

        // the user defines a
        if (null != mPatternToSearchEditText) {
            mPatternToSearchEditText.setHint(R.string.room_participants_invite_search_another_user);
        }

        mBackgroundImageView = (ImageView)findViewById(R.id.search_background_imageview);
        mNoResultView = findViewById(R.id.search_no_result_textview);
        mLoadingView = findViewById(R.id.search_in_progress_view);

        mListView = (ListView) findViewById(R.id.room_details_members_list);
        mAdapter = new VectorParticipantsAdapter(this, R.layout.adapter_item_vector_add_participants, mSession, roomId);

        mAdapter.setSortMethod(new Comparator<ParticipantAdapterItem>() {
            /**
             * Compare 2 string and returns sort order.
             * @param s1 string 1.
             * @param s2 string 2.
             * @return the sort order.
             */
            private int alphaComparator(String s1, String s2) {
                if (s1 == null) {
                    return -1;
                } else if (s2 == null) {
                    return 1;
                }

                return String.CASE_INSENSITIVE_ORDER.compare(s1, s2);
            }

            @Override
            public int compare(ParticipantAdapterItem part1, ParticipantAdapterItem part2) {
                User userA = mSession.getDataHandler().getUser(part1.mUserId);
                User userB = mSession.getDataHandler().getUser(part2.mUserId);

                String userADisplayName = part1.getComparisonDisplayName();
                String userBDisplayName = part2.getComparisonDisplayName();

                boolean isUserA_Active = false;
                boolean isUserB_Active = false;

                if ((null != userA) && (null != userA.currently_active)) {
                    isUserA_Active = userA.currently_active;
                }

                if ((null != userB) && (null != userB.currently_active)) {
                    isUserB_Active = userB.currently_active;
                }

                if ((null == userA) && (null == userB)) {
                    return alphaComparator(userADisplayName, userBDisplayName);
                } else if ((null != userA) && (null == userB)) {
                    return +1;
                } else if ((null == userA) && (null != userB)) {
                    return -1;
                } else if (isUserA_Active && isUserB_Active) {
                    return alphaComparator(userADisplayName, userBDisplayName);
                }

                if (isUserA_Active && !isUserB_Active) {
                    return -1;
                } if (!isUserA_Active && isUserB_Active) {
                    return +1;
                }

                // Finally, compare the timestamps
                long lastActiveAgoA = (null != userA) ? userA.getAbsoluteLastActiveAgo() : 0;
                long lastActiveAgoB = (null != userB) ? userB.getAbsoluteLastActiveAgo() : 0;

                long diff = lastActiveAgoA - lastActiveAgoB;

                if (diff == 0) {
                    return alphaComparator(userADisplayName, userBDisplayName);
                }
                
                // if only one member has a lastActiveAgo, prefer it
                if (0 == lastActiveAgoA) {
                    return +1;
                } else if (0 == lastActiveAgoB) {
                    return -1;
                }

                return (diff > 0) ? +1 : -1;
            }
        });

        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                // returns the selected user
                Intent intent = new Intent();
                intent.putExtra(EXTRA_SELECTED_USER_ID, mAdapter.getItem(position).mUserId);
                setResult(RESULT_OK, intent);
                finish();
            }
        });

        manageBackground();
    }

    /**
     * The search pattern has been updated
     */
    @Override
    protected void onPatternUpdate(boolean isTypingUpdate) {
        manageBackground();

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

        // display a spinner while the other room memebers are listed
        if (!mAdapter.isKnownMembersInitialized()) {
            mLoadingView.setVisibility(View.VISIBLE);
        }

        mAdapter.setSearchedPattern(pattern, VectorParticipantsAdapter.SEARCH_METHOD_STARTS_WITH, firstEntry, new VectorParticipantsAdapter.OnParticipantsSearchListener() {
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