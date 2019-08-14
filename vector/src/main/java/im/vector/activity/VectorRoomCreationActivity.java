/*
 * Copyright 2015 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import org.jetbrains.anko.ToastsKt;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.model.CreateRoomParams;
import org.matrix.androidsdk.rest.model.RoomMember;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import im.vector.R;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorRoomCreationAdapter;

public class VectorRoomCreationActivity extends MXCActionBarActivity {
    // tags
    private final String LOG_TAG = VectorRoomCreationActivity.class.getSimpleName();

    // participants list
    private static final String PARTICIPANTS_LIST = "PARTICIPANTS_LIST";

    //
    private static final int INVITE_USER_REQUEST_CODE = 456;

    // UI items
    private ListView membersListView;
    private VectorRoomCreationAdapter mAdapter;

    // the search is displayed at first call
    private boolean mIsFirstResume = true;

    // direct message
    private final ApiCallback<String> mCreateDirectMessageCallBack = new ApiCallback<String>() {
        @Override
        public void onSuccess(final String roomId) {
            Map<String, Object> params = new HashMap<>();
            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
            params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
            params.put(VectorRoomActivity.EXTRA_EXPAND_ROOM_HEADER, true);

            Log.d(LOG_TAG, "## mCreateDirectMessageCallBack: onSuccess - start goToRoomPage");
            CommonActivityUtils.goToRoomPage(VectorRoomCreationActivity.this, mSession, params);
        }

        private void onError(final String message) {
            membersListView.post(new Runnable() {
                @Override
                public void run() {
                    if (null != message) {
                        Toast.makeText(VectorRoomCreationActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                    hideWaitingView();
                }
            });
        }

        @Override
        public void onNetworkError(Exception e) {
            onError(e.getLocalizedMessage());
        }

        @Override
        public void onMatrixError(final MatrixError e) {
            if (MatrixError.M_CONSENT_NOT_GIVEN.equals(e.errcode)) {
                hideWaitingView();

                getConsentNotGivenHelper().displayDialog(e);
            } else {
                onError(e.getLocalizedMessage());
            }
        }

        @Override
        public void onUnexpectedError(final Exception e) {
            onError(e.getLocalizedMessage());
        }
    };

    // displayed participants
    private List<ParticipantAdapterItem> mParticipants = new ArrayList<>();

    @Override
    public int getLayoutRes() {
        return R.layout.activity_vector_room_creation;
    }

    @Override
    public void initUiAndData() {
        configureToolbar();

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "onCreate : Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        final Intent intent = getIntent();

        mSession = getSession(intent);

        if (mSession == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }

        // get the UI items
        setWaitingView(findViewById(R.id.room_creation_spinner_views));
        membersListView = findViewById(R.id.room_creation_members_list_view);
        mAdapter = new VectorRoomCreationAdapter(this,
                R.layout.adapter_item_vector_creation_add_member, R.layout.adapter_item_vector_add_participants, mSession);

        // init the content
        if (!isFirstCreation() && getSavedInstanceState().containsKey(PARTICIPANTS_LIST)) {
            mParticipants.clear();
            mParticipants = new ArrayList<>((List<ParticipantAdapterItem>) getSavedInstanceState().getSerializable(PARTICIPANTS_LIST));
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
        intent.putExtra(VectorRoomInviteMembersActivity.EXTRA_HIDDEN_PARTICIPANT_ITEMS, (ArrayList) mParticipants);
        startActivityForResult(intent, INVITE_USER_REQUEST_CODE);
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
        savedInstanceState.putSerializable(PARTICIPANTS_LIST, (ArrayList) mParticipants);
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
                List<ParticipantAdapterItem> items =
                        (List<ParticipantAdapterItem>) data.getSerializableExtra(VectorRoomInviteMembersActivity.EXTRA_OUT_SELECTED_PARTICIPANT_ITEMS);
                mParticipants.addAll(items);
                mAdapter.addAll(items);
                mAdapter.sort(mAlphaComparator);
            } else if (1 == mParticipants.size()) {
                // the user cancels the first user selection so assume he wants to cancel the room creation.
                finish();
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
            } else if (rhs == null) {
                return 1;
            }

            return String.CASE_INSENSITIVE_ORDER.compare(lhs, rhs);
        }
    };


    //================================================================================
    // Menu management
    //================================================================================


    @Override
    public int getMenuRes() {
        return R.menu.vector_room_creation;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // the application is in a weird state
        // GA : mSession is null
        if (CommonActivityUtils.shouldRestartApp(this) || (null == mSession)) {
            return false;
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_create_room:
                if (mParticipants.isEmpty()) {
                    createRoom(mParticipants);
                } else {
                    // the first entry is self so ignore
                    mParticipants.remove(0);

                    if (mParticipants.isEmpty()) {
                        // standalone case : should be accepted ?
                        createRoom(mParticipants);
                    } else if (mParticipants.size() > 1) {
                        createRoom(mParticipants);
                    } else {
                        // 1 other participant
                        openOrCreateDirectChatRoom(mParticipants.get(0).mUserId);
                    }
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void openOrCreateDirectChatRoom(final String otherUserId) {
        doesDirectChatRoomAlreadyExist(otherUserId, new ApiCallback<String>() {
            @Override
            public void onSuccess(String existingRoomId) {
                if (null != existingRoomId) {
                    Map<String, Object> params = new HashMap<>();
                    params.put(VectorRoomActivity.EXTRA_MATRIX_ID, otherUserId);
                    params.put(VectorRoomActivity.EXTRA_ROOM_ID, existingRoomId);
                    CommonActivityUtils.goToRoomPage(VectorRoomCreationActivity.this, mSession, params);
                } else {
                    // direct message flow
                    showWaitingView();
                    mSession.createDirectMessageRoom(otherUserId, mCreateDirectMessageCallBack);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                ToastsKt.toast(VectorRoomCreationActivity.this, e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                ToastsKt.toast(VectorRoomCreationActivity.this, e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                ToastsKt.toast(VectorRoomCreationActivity.this, e.getLocalizedMessage());
            }
        });
    }


    //================================================================================
    // Room creation
    //================================================================================

    /**
     * Return the first direct chat room for a given user ID.
     *
     * @param aUserId  user ID to search for
     * @param callback callback to return a room ID if search succeed, null otherwise.
     */
    private void doesDirectChatRoomAlreadyExist(final String aUserId, final ApiCallback<String> callback) {
        if (null != mSession) {
            IMXStore store = mSession.getDataHandler().getStore();

            Map<String, List<String>> directChatRoomsDict;

            if (null != store.getDirectChatRoomsDict()) {
                directChatRoomsDict = new HashMap<>(store.getDirectChatRoomsDict());

                if (directChatRoomsDict.containsKey(aUserId)) {
                    List<String> roomIdsList = new ArrayList<>(directChatRoomsDict.get(aUserId));

                    doesDirectChatRoomAlreadyExistRecursive(roomIdsList, 0, aUserId, callback);
                } else {
                    callback.onSuccess(null);
                }
            } else {
                callback.onSuccess(null);
            }
        } else {
            callback.onSuccess(null);
        }
    }

    private void doesDirectChatRoomAlreadyExistRecursive(final List<String> roomIdsList,
                                                         final int index,
                                                         final String aUserId,
                                                         final ApiCallback<String> callback) {
        if (index >= roomIdsList.size()) {
            Log.d(LOG_TAG, "## doesDirectChatRoomAlreadyExist(): for user=" + aUserId + " no found room");
            callback.onSuccess(null);
        } else {
            Room room = mSession.getDataHandler().getRoom(roomIdsList.get(index), false);

            // check if the room is already initialized
            if (room != null && room.isReady() && !room.isInvited() && !room.isLeaving()) {
                room.getActiveMembersAsync(new SimpleApiCallback<List<RoomMember>>(callback) {
                    @Override
                    public void onSuccess(List<RoomMember> members) {
                        // test if the member did not leave the room
                        for (RoomMember member : members) {
                            if (TextUtils.equals(member.getUserId(), aUserId)) {
                                Log.d(LOG_TAG, "## doesDirectChatRoomAlreadyExist(): for user=" + aUserId + " roomFound=" + roomIdsList.get(index));
                                callback.onSuccess(roomIdsList.get(index));
                                return;
                            }
                        }

                        // Try next one
                        doesDirectChatRoomAlreadyExistRecursive(roomIdsList, index + 1, aUserId, callback);
                    }
                });
            } else {
                // Try next one
                doesDirectChatRoomAlreadyExistRecursive(roomIdsList, index + 1, aUserId, callback);
            }
        }
    }

    /**
     * Create a room with a list of participants.
     *
     * @param participants the list of participant
     */
    private void createRoom(final List<ParticipantAdapterItem> participants) {
        showWaitingView();

        CreateRoomParams params = new CreateRoomParams();

        List<String> ids = new ArrayList<>();
        for (ParticipantAdapterItem item : participants) {
            if (null != item.mUserId) {
                ids.add(item.mUserId);
            }
        }

        boolean res = params.addParticipantIds(mSession.getHomeServerConfig(), ids);

        if (res) {
            Toast.makeText(this, R.string.identity_server_not_defined, Toast.LENGTH_LONG).show();
        } else {
            mSession.createRoom(params, new ApiCallback<String>() {
                @Override
                public void onSuccess(final String roomId) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Map<String, Object> params = new HashMap<>();
                            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                            params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
                            CommonActivityUtils.goToRoomPage(VectorRoomCreationActivity.this, mSession, params);
                        }
                    });
                }

                private void onError(final String message) {
                    membersListView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (null != message) {
                                Toast.makeText(VectorRoomCreationActivity.this, message, Toast.LENGTH_LONG).show();
                            }
                            hideWaitingView();
                        }
                    });
                }

                @Override
                public void onNetworkError(Exception e) {
                    onError(e.getLocalizedMessage());
                }

                @Override
                public void onMatrixError(final MatrixError e) {
                    if (MatrixError.M_CONSENT_NOT_GIVEN.equals(e.errcode)) {
                        hideWaitingView();

                        getConsentNotGivenHelper().displayDialog(e);
                    } else {
                        onError(e.getLocalizedMessage());
                    }
                }

                @Override
                public void onUnexpectedError(final Exception e) {
                    onError(e.getLocalizedMessage());
                }
            });
        }
    }
}
