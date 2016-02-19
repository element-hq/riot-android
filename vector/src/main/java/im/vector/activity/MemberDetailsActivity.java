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

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.RoomMembersAdapter;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;

import im.vector.R;
import im.vector.adapters.MemberDetailsAdapter;
import im.vector.adapters.MemberDetailsAdapter.AdapterMemberActionItems;
import im.vector.util.VectorUtils;

import java.util.ArrayList;
import java.util.Collection;

public class MemberDetailsActivity extends MXCActionBarActivity implements MemberDetailsAdapter.IEnablingActions {

    private static final String LOG_TAG = "MemberDetailsActivity";

    public static final String EXTRA_ROOM_ID = "MemberDetailsActivity.EXTRA_ROOM_ID";
    public static final String EXTRA_MEMBER_ID = "MemberDetailsActivity.EXTRA_MEMBER_ID";

    // list view items associated actions
    public static final int ITEM_ACTION_START_NEW_ROOM = 0;
    public static final int ITEM_ACTION_MAKE_ADMIN = 1;
    public static final int ITEM_ACTION_REMOVE_FROM_ROOM = 2;
    public static final int ITEM_ACTION_BLOCK = 3;

    // internal info
    private Room mRoom;
    private String mRoomId;
    private String mMemberId;       // member whose details area displayed (provided in EXTRAS)
    private RoomMember mRoomMember; // room member corresponding to mMemberId
    private MXSession mSession;
    private ArrayList<MemberDetailsAdapter.AdapterMemberActionItems> mActionItemsArrayList;
    private MemberDetailsAdapter mListViewAdapter;

    // UI widgets
    private ImageView mMemberAvatarImageView;
    private TextView mMatrixIdTextView;
    private TextView mPresenceTextView;
    private ListView mActionItemsListView;
    //private ArrayList<Button>mButtonsList;

    // MX event listener
    private final MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            MemberDetailsActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // check if the event is received for the current room
                    // check if there is a member update
                    if ((Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) || (Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS.equals(event.type))) {

                        // update only if it is the current user
                        if ((null != event.getSender()) && (event.getSender().equals(mMemberId))) {
                            MemberDetailsActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateRoomMember();
                                    updateUi();
                                }
                            });
                        }
                    }
                }
            });
        }

        @Override
        public void onPresenceUpdate(Event event, final User user) {
            if (mMemberId.equals(user.userId)) {
                // Someone's presence has changed, reprocess the whole list
                MemberDetailsActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updatePresenceInfoUi();
                    }
                });
            }
        }

        /**
         * User presences was synchronized..
         */
        @Override
        public void onPresencesSyncComplete() {
            MemberDetailsActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updatePresenceInfoUi();
                }
            });
        }
    };

    // Room action listeners. Each time an action is detected the UI must be updated.
    private final ApiCallback roomActionsListener = new SimpleApiCallback<Void>(this) {
        @Override
        public void onMatrixError(MatrixError e) {
            if (MatrixError.FORBIDDEN.equals(e.errcode)) {
                Toast.makeText(MemberDetailsActivity.this, e.error, Toast.LENGTH_LONG).show();
            }
            updateUi();
        }

        @Override
        public void onSuccess(Void info) {
            updateUi();
        }

        @Override
        public void onNetworkError(Exception e) {
            Toast.makeText(MemberDetailsActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            updateUi();
        }

        @Override
        public void onUnexpectedError(Exception e) {
            Toast.makeText(MemberDetailsActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            updateUi();
        }
    };


    // *********************************************************************************************
    // IEnablingActions interface implementation
    /**
     * Compute if the action is allowed or not, according to the
     * power levels.
     *
     * @param aActionType the action associated to the list row
     * @return true if the action must be enabled, false otherwise
     */
    @Override
    public boolean isActionEnabled(int aActionType) {
        boolean retCode = false;

        if ((null != mRoom) && (null != mSession)) {
            // get the PowerLevels object associated to the room and the user ID
            PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();
            String currentUserId = mSession.getMyUser().userId;

            if(null != powerLevels) {
                // get power levels from myself and from the member of the room
                int memberPowerLevel = powerLevels.getUserPowerLevel(mMemberId);
                int myPowerLevel = powerLevels.getUserPowerLevel(currentUserId);

                switch (aActionType) {
                    case ITEM_ACTION_START_NEW_ROOM:
                        retCode = true;
                        break;

                    case ITEM_ACTION_MAKE_ADMIN:
                        // I need to have the max power of the room to enable admin action
                        retCode = (myPowerLevel == getRoomMaxPowerLevel());
                        break;

                    case ITEM_ACTION_REMOVE_FROM_ROOM:
                        retCode = (myPowerLevel >= powerLevels.kick) && (myPowerLevel >= memberPowerLevel);
                        break;

                    case ITEM_ACTION_BLOCK:
                        retCode = (myPowerLevel >= powerLevels.ban) && (myPowerLevel >= memberPowerLevel);
                        break;

                    default:
                        // unknown action
                        retCode = false;
                        break;
                }
            }
        }
        return retCode;
    }

    /**
     * Start the corresponding action given by aActionType value.
     *
     * @param aActionType the action associated to the list row
     */
    @Override
    public void performAction(int aActionType) {
        if((null != mRoom)&& (null != mRoomMember)) {
            switch (aActionType) {

                case ITEM_ACTION_START_NEW_ROOM:
                    MemberDetailsActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            CommonActivityUtils.goToOneToOneRoom(mSession, mMemberId, MemberDetailsActivity.this, roomActionsListener);
                        }
                    });
                    break;

                case ITEM_ACTION_MAKE_ADMIN:
                    // update the member power with the max power level of the room
                    PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();
                    powerLevels.setUserPowerLevel(mMemberId, getRoomMaxPowerLevel());
                    break;

                case ITEM_ACTION_REMOVE_FROM_ROOM:
                    mRoom.kick(mRoomMember.getUserId(), roomActionsListener);
                    break;

                case ITEM_ACTION_BLOCK:
                    mRoom.ban(mRoomMember.getUserId(), null, roomActionsListener);
                    break;

                default:
                    // unknown action
                    Log.w(LOG_TAG,"## performAction(): unknown action type = " + aActionType);
                    break;
            }
        }
    }
    // *********************************************************************************************


    /**
     * Helper method to populate the list view items with AdapterMemberActionItems objects.
     */
    private void populateListViewItems(){
        mActionItemsArrayList = new ArrayList<AdapterMemberActionItems>();

        // build the "start new room" item
        int imageResource = R.drawable.ic_person_add_black;
        String actionText = getResources().getString(R.string.member_details_action_start_new_room);
        mActionItemsArrayList.add(new AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_START_NEW_ROOM));

        // build the "make admin" item
        imageResource = R.drawable.ic_verified_user_black;
        actionText = getResources().getString(R.string.member_details_action_make_admin);
        mActionItemsArrayList.add(new AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_MAKE_ADMIN));

        // build the "remove from" item (ban)
        imageResource = R.drawable.ic_remove_circle_outline_red;
        actionText = getResources().getString(R.string.member_details_action_remove_from_room);
        mActionItemsArrayList.add(new AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_REMOVE_FROM_ROOM));

        // build the "block" item (block)
        imageResource = R.drawable.ic_block_black;
        actionText = getResources().getString(R.string.member_details_action_block);
        mActionItemsArrayList.add(new AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_BLOCK));
    }




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            Log.e(LOG_TAG, "Restart the application");
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_member_details);

        Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "room ID missing in extra");
            finish();
            return;
        }
        mRoomId = intent.getStringExtra(EXTRA_ROOM_ID);

        if (!intent.hasExtra(EXTRA_MEMBER_ID)) {
            Log.e(LOG_TAG, "member ID missing in extra");
            finish();
            return;
        }
        mMemberId = intent.getStringExtra(EXTRA_MEMBER_ID);

        // get session
        mSession = getSession(intent);
        if (null == mSession) {
            Log.e(LOG_TAG, "Invalid session");
            finish();
            return;
        }

        // check the case where the current logged user is asking its own details
        // if it is the case, just abort the activity launch
        if(isDetailsRequiredForMySelf(mSession, mMemberId)){
            Log.w(LOG_TAG, "Cancel member details for user himself");
            finish();
            return;
        }

        // get room
        mRoom = mSession.getDataHandler().getRoom(mRoomId);
        if (null == mRoom) {
            Log.e(LOG_TAG, "The room is not found");
            finish();
            return;
        }

        // find out the room member to set mRoomMember field
        // => pay attention that updateRoomMember() can also finish the activity
        // if mRoomMember is not found in the members of the room
        updateRoomMember();

        // bind UI widgets
        mMemberAvatarImageView = (ImageView) findViewById(R.id.avatar_img);
        mMatrixIdTextView = (TextView) findViewById(R.id.member_details_name);
        mPresenceTextView = (TextView)findViewById(R.id.member_details_presence);
        mActionItemsListView = (ListView)findViewById(R.id.member_details_actions_list_view);

        // setup the list view
        populateListViewItems();
        mListViewAdapter = new MemberDetailsAdapter((Context)this, R.layout.vector_adapter_member_details_items, mActionItemsArrayList);
        mListViewAdapter.setActionListener(this);
        mActionItemsListView.setAdapter(mListViewAdapter);

        // update the UI
        updateUi();
    }

    /**
     * Test if the current user is requiring details for himself.
     * @param aSession current uer session
     * @param aMemberIdDetailsAsking
     * @return true if the current user is asking details for himself, false otherwise
     */
    private boolean isDetailsRequiredForMySelf(MXSession aSession, String aMemberIdDetailsAsking) {
        boolean retCode = true;
        if((null != aSession) && (null != aMemberIdDetailsAsking)){
            String sessionUserId = aSession.getMyUser().userId;
            retCode = aMemberIdDetailsAsking.equals(sessionUserId);
        }
        return retCode;
    }


    /**
     * Search if the member is present in the list of the members of
     * the room
     */
    private void updateRoomMember(){
        if (null != mRoom){
            // reset the value before any new search
            mRoomMember = null;
            // find out the room member
            Collection<RoomMember> members = mRoom.getMembers();
            for (RoomMember member : members) {
                if (member.getUserId().equals(mMemberId)) {
                    mRoomMember = member;
                    break;
                }
            }

            // the member is not (anymore) present the room, just finish the activity
            // and return. This can happen if the member has left the room in another
            // client(ie. web client) and someone is looking at its details
            if(null == mRoomMember) {
                Log.e(LOG_TAG, "The user " + mMemberId + " is not in the room " + mRoomId);
                finish();
                return;
            }
        }
    }

    private int getRoomMaxPowerLevel() {
        int maxPowerLevel = 0;

        if (null != mRoom){
            int tempPowerLevel = 0;
            PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

            // find out the room member
            Collection<RoomMember> members = mRoom.getMembers();
            for (RoomMember member : members) {
                tempPowerLevel = powerLevels.getUserPowerLevel(member.getUserId());
                if(tempPowerLevel > maxPowerLevel) {
                    maxPowerLevel = tempPowerLevel;
                }
            }
        }
        return maxPowerLevel;
    }

    /**
     * updateUi each activity views
     */
    private void updateUi() {
        if((null != mMatrixIdTextView) && (null != mRoomMember)) {
            mMatrixIdTextView.setText(mMemberId);
            setTitle(mRoomMember.displayname); // TODO TBC
        }
        updateMemberAvatarUi();
        updatePresenceInfoUi();

        if(null != mListViewAdapter){
            mListViewAdapter.notifyDataSetChanged();
        }
    }

    private void updatePresenceInfoUi() {
        // update the presence ring
        IMXStore store = mSession.getDataHandler().getStore();
        User user = store.getUser(mMemberId);
        String onlineStatus;
        int onlineStatusColour = Color.BLACK;

        // sanity check
        if (null != mPresenceTextView) {
            if ((user == null) || (user.lastActiveAgo == null)) {
                mPresenceTextView.setVisibility(View.GONE);
            } else {
                mPresenceTextView.setVisibility(View.VISIBLE);

                // set the presence status text and its colour
                /* further use
                if (User.PRESENCE_ONLINE.equals(user.presence)) {
                    onlineStatus = getResources().getString(R.string.presence_online);
                }*/
                if (User.PRESENCE_OFFLINE.equals(user.presence)) {
                    onlineStatus = getResources().getString(R.string.presence_offline);
                    onlineStatusColour = Color.RED;
                } else if (User.PRESENCE_UNAVAILABLE.equals(user.presence)) {
                    onlineStatus = getResources().getString(R.string.presence_unavailable);
                } else if (User.PRESENCE_HIDDEN.equals(user.presence)) {
                    onlineStatus = getResources().getString(R.string.presence_hidden);
                } else {
                    // online: display "last seen since.."
                    onlineStatus = buildLastActiveDisplay(user.getRealLastActiveAgo());
                }

                mPresenceTextView.setText(onlineStatus);
                mPresenceTextView.setTextColor(onlineStatusColour);
            }
        }
    }

    /**
     * update the profile avatar
     */
    private void updateMemberAvatarUi() {
        // set default image
        VectorUtils.setMemberAvatar(mMemberAvatarImageView, mMemberId, mRoomMember.displayname);

        if (mRoomMember.avatarUrl != null) {
            int size = getResources().getDimensionPixelSize(R.dimen.profile_avatar_size);
            mSession.getMediasCache().loadAvatarThumbnail(mSession.getHomeserverConfig(), mMemberAvatarImageView, mRoomMember.avatarUrl, size);
        }
    }

    private String buildLastActiveDisplay(final long lastActiveAgo) {
        return RoomMembersAdapter.buildLastActiveDisplay(this, lastActiveAgo);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mSession.getDataHandler().getRoom(mRoom.getRoomId()).removeEventListener(mEventListener);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mSession.getDataHandler().getRoom(mRoom.getRoomId()).addEventListener(mEventListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // add sanity check
        if ((null != mRoom) && (null != mEventListener)) {
            mRoom.removeEventListener(mEventListener);
        }
    }
}
