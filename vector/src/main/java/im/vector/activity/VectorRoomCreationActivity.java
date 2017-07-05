/*
 * Copyright 2015 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;


import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import im.vector.R;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorRoomCreationAdapter;


public class VectorRoomCreationActivity extends MXCActionBarActivity {
    // tags
    private final String LOG_TAG = "VRoomCreationActivity";

    // participants list
    private static final String PARTICIPANTS_LIST = "PARTICIPANTS_LIST";

    //
    private static final int INVITE_USER_REQUEST_CODE = 456;

    // UI items
    private VectorRoomCreationAdapter mAdapter;
    private View mSpinnerView;

    // the search is displayed at first call
    private boolean mIsFirstResume = true;

    // direct message
    private final ApiCallback<String> mCreateDirectMessageCallBack = new ApiCallback<String>() {
        @Override
        public void onSuccess(final String roomId) {
            HashMap<String, Object> params = new HashMap<>();
            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
            params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
            params.put(VectorRoomActivity.EXTRA_EXPAND_ROOM_HEADER, true);

            Log.d(LOG_TAG, "## mCreateDirectMessageCallBack: onSuccess - start goToRoomPage");
            CommonActivityUtils.goToRoomPage(VectorRoomCreationActivity.this, mSession, params);
        }

        private void onError(final String message) {
            mSpinnerView.post(new Runnable() {
                @Override
                public void run() {
                    if (null != message) {
                        Toast.makeText(VectorRoomCreationActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                    mSpinnerView.setVisibility(View.GONE);
                }
            });
        }

        @Override
        public void onNetworkError(Exception e) {
            onError(e.getLocalizedMessage());
        }

        @Override
        public void onMatrixError(final MatrixError e) {
            onError(e.getLocalizedMessage());
        }

        @Override
        public void onUnexpectedError(final Exception e) {
            onError(e.getLocalizedMessage());
        }
    };

    // displayed participants
    private ArrayList<ParticipantAdapterItem> mParticipants = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_room_creation);

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "onCreate : Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        final Intent intent = getIntent();

        mSession = getSession(this, intent);

        if (mSession == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }

        // get the UI items
        mSpinnerView = findViewById(R.id.room_creation_spinner_views);
        ListView membersListView = (ListView) findViewById(R.id.room_creation_members_list_view);
        mAdapter = new VectorRoomCreationAdapter(this, R.layout.adapter_item_vector_creation_add_member, R.layout.adapter_item_vector_add_participants, mSession);

        // init the content
        if ((null != savedInstanceState) && savedInstanceState.containsKey(PARTICIPANTS_LIST)) {
            mParticipants.clear();
            mParticipants = new ArrayList<>((List<ParticipantAdapterItem>)savedInstanceState.getSerializable(PARTICIPANTS_LIST));
        } else {
            mParticipants.add(new ParticipantAdapterItem(mSession.getMyUser()));
        }
        mAdapter.addAll(mParticipants);

        membersListView.setAdapter(mAdapter);

        mAdapter.setRoomCreationAdapterListener(new VectorRoomCreationAdapter.IRoomCreationAdapterListener() {
            @Override
            public void OnRemoveParticipantClick(ParticipantAdapterItem item) {
                mParticipants.remove(item);
                mAdapter.remove(item);
            }
        });

        membersListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // the first one is "add a member"
                if (0 == position) {
                    launchSearchActivity();
                }
            }
        });
    }

    /***
     * Launch the people search activity
     */
    private void launchSearchActivity() {
        Intent intent = new Intent(VectorRoomCreationActivity.this, VectorRoomInviteMembersActivity.class);
        intent.putExtra(VectorRoomInviteMembersActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
        intent.putExtra(VectorRoomInviteMembersActivity.EXTRA_HIDDEN_PARTICIPANT_ITEMS, mParticipants);
        VectorRoomCreationActivity.this.startActivityForResult(intent, INVITE_USER_REQUEST_CODE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mIsFirstResume) {
            mIsFirstResume = false;
            launchSearchActivity();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putSerializable(PARTICIPANTS_LIST, mParticipants);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if (null != savedInstanceState) {
            if (savedInstanceState.containsKey(PARTICIPANTS_LIST)) {
                mParticipants = new ArrayList<>((List<ParticipantAdapterItem>) savedInstanceState.getSerializable(PARTICIPANTS_LIST));
            } else {
                mParticipants.clear();
                mParticipants.add(new ParticipantAdapterItem(mSession.getMyUser()));
            }
            mAdapter.clear();
            mAdapter.addAll(mParticipants);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == INVITE_USER_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                List<ParticipantAdapterItem> items = (List<ParticipantAdapterItem>) data.getSerializableExtra(VectorRoomInviteMembersActivity.EXTRA_OUT_SELECTED_PARTICIPANT_ITEMS);
                mParticipants.addAll(items);
                mAdapter.addAll(items);
                mAdapter.sort(mAlphaComparator);
            } else if (1 == mParticipants.size()) {
                // the user cancels the first user selection so assume he wants to cancel the room creation.
                this.finish();
            }
        }
    }

    // Comparator to order members alphabetically
    // the self item is always kept at top
    private final Comparator<ParticipantAdapterItem> mAlphaComparator = new Comparator<ParticipantAdapterItem>() {
        @Override
        public int compare(ParticipantAdapterItem part1, ParticipantAdapterItem part2) {
            // keep the self user id at top
            if (TextUtils.equals(part1.mUserId, mSession.getMyUserId())) {
                return -1;
            }

            if (TextUtils.equals(part2.mUserId, mSession.getMyUserId())) {
                return +1;
            }

            String lhs = part1.getComparisonDisplayName();
            String rhs = part2.getComparisonDisplayName();

            if (lhs == null) {
                return -1;
            }
            else if (rhs == null) {
                return 1;
            }

            return String.CASE_INSENSITIVE_ORDER.compare(lhs, rhs);
        }
    };


    //================================================================================
    // Menu management
    //================================================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // the application is in a weird state
        // GA : mSession is null
        if (CommonActivityUtils.shouldRestartApp(this) || (null == mSession)) {
            return false;
        }

        getMenuInflater().inflate(R.menu.vector_room_creation, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        String existingRoomId;

        if (id == R.id.action_create_room) {
            if (0 == mParticipants.size()) {
                createRoom(mParticipants);
            } else {
                // the first entry is self so ignore
                mParticipants.remove(0);

                // standalone case : should be accepted ?
                if (0 == mParticipants.size()) {
                    createRoom(mParticipants);
                } else if (mParticipants.size() > 1) {
                    createRoom(mParticipants);
                } else if (null != (existingRoomId = isDirectChatRoomAlreadyExist(mParticipants.get(0).mUserId))) {
                    HashMap<String, Object> params = new HashMap<>();
                    params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mParticipants.get(0).mUserId);
                    params.put(VectorRoomActivity.EXTRA_ROOM_ID, existingRoomId);
                    CommonActivityUtils.goToRoomPage(this, mSession, params);
                } else {
                    // direct message flow
                    mSpinnerView.setVisibility(View.VISIBLE);
                    mSession.createRoomDirectMessage(mParticipants.get(0).mUserId, mCreateDirectMessageCallBack);
                }
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    //================================================================================
    // Room creation
    //================================================================================

    /**
     * Return the first direct chat room for a given user ID.
     * @param aUserId user ID to search for
     * @return a room ID if search succeed, null otherwise.
     */
    private String isDirectChatRoomAlreadyExist(String aUserId) {
        if(null != mSession) {
            IMXStore store = mSession.getDataHandler().getStore();

            HashMap<String, List<String>> directChatRoomsDict;

            if (null != store.getDirectChatRoomsDict()) {
                directChatRoomsDict = new HashMap<>(store.getDirectChatRoomsDict());

                if (directChatRoomsDict.containsKey(aUserId)) {
                    ArrayList<String> roomIdsList = new ArrayList<>(directChatRoomsDict.get(aUserId));

                    if (0 != roomIdsList.size()) {
                        for(String roomId : roomIdsList) {
                            Room room = mSession.getDataHandler().getRoom(roomId, false);

                            // check if the room is already initialized
                            if ((null != room) && room.isReady() && !room.isInvited() && !room.isLeaving()) {
                                // test if the member did not leave the room
                                Collection<RoomMember> members = room.getActiveMembers();

                                for(RoomMember member : members) {
                                    if (TextUtils.equals(member.getUserId(), aUserId)) {
                                        Log.d(LOG_TAG,"## isDirectChatRoomAlreadyExist(): for user="+aUserId+" roomFound=" + roomId);
                                        return roomId;
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }
        Log.d(LOG_TAG,"## isDirectChatRoomAlreadyExist(): for user=" + aUserId + " no found room");
        return null;
    }

    /**
     * Create a room with a list of participants.
     * @param participants the list of participant
     */
    private void createRoom(final List<ParticipantAdapterItem> participants) {
        mSpinnerView.setVisibility(View.VISIBLE);

        mSession.createRoom(new SimpleApiCallback<String>(VectorRoomCreationActivity.this) {
            @Override
            public void onSuccess(final String roomId) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        inviteParticipants(mSession.getDataHandler().getRoom(roomId), participants, 0);
                    }
                });
            }

            private void onError(final String message) {
                mSpinnerView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != message) {
                            Toast.makeText(VectorRoomCreationActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                        mSpinnerView.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(final MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                onError(e.getLocalizedMessage());
            }
        });
    }

    /**
     * Invite some participants.
     * @param room the room
     * @param participants the participants list
     * @param index the start index
     */
    private void inviteParticipants(final Room room, final List<ParticipantAdapterItem> participants, final int index) {
        // detect if all members have been invited
        if (index >= participants.size()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // update the read markers
                    room.markAllAsRead(null);
                    HashMap<String, Object> params = new HashMap<>();
                    params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                    params.put(VectorRoomActivity.EXTRA_ROOM_ID, room.getRoomId());
                    CommonActivityUtils.goToRoomPage(VectorRoomCreationActivity.this, mSession, params);
                }
            });

            return;
        }

        final ApiCallback<Void> callback = new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        inviteParticipants(room, participants, index + 1);
                    }
                });
            }

            public void onError(final String errorMessage) {
                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(VectorRoomCreationActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                                inviteParticipants(room, participants, index + 1);
                            }
                        });
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError(e.getLocalizedMessage());
            }
        };

        String userId = participants.get(index).mUserId;

        if (android.util.Patterns.EMAIL_ADDRESS.matcher(userId).matches()) {
            room.inviteByEmail(userId, callback);
        } else {
            ArrayList<String> userIDs = new ArrayList<>();
            userIDs.add(userId);
            room.invite(userIDs, callback);
        }
    }
}
