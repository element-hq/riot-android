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

import java.util.Collection;

public class MemberDetailsActivity extends MXCActionBarActivity implements MemberDetailsAdapter.IEnablingActions {
    private static final String LOG_TAG = "MemberDetailsActivity";

    public static final String EXTRA_ROOM_ID = "MemberDetailsActivity.EXTRA_ROOM_ID";
    public static final String EXTRA_MEMBER_ID = "MemberDetailsActivity.EXTRA_MEMBER_ID";

    // list view items associated actions
    public static final int ITEM_ACTION_START_CHAT = 0;
    public static final int ITEM_ACTION_MAKE_ADMIN = 1;
    public static final int ITEM_ACTION_REMOVE_FROM_ROOM = 2;
    public static final int ITEM_ACTION_BLOCK = 3;

    // internal info
    private Room mRoom;
    private String mRoomId;
    private String mMemberId;       // member whose details area displayed (provided in EXTRAS)
    private RoomMember mRoomMember; // room member corresponding to mMemberId
    private boolean mIsMemberJoined;
    private MXSession mSession;
    //private ArrayList<MemberDetailsAdapter.AdapterMemberActionItems> mActionItemsArrayList;
    private MemberDetailsAdapter mListViewAdapter;

    // UI widgets
    private ImageView mMemberAvatarImageView;
    private TextView mMatrixIdTextView;
    private TextView mPresenceTextView;
    private ListView mActionItemsListView;
    private View mProgressBarView;
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
                        MemberDetailsActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(checkRoomMemberStatus()) {
                                    updateUi();
                                } else {
                                    // exit from the activity
                                    finish();
                                }
                            }
                        });
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

    // Room action listeners. Every time an action is detected the UI must be updated.
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
     * @param aActionType the action to be enabled/disabled
     * @return true if the action must be enabled, false otherwise
     */
    @Override
    public boolean isItemActionEnabled(int aActionType) {
        boolean retCode = false;

        if ((null != mRoom) && (null != mSession) && (null != mRoomMember)) {
            // get the PowerLevels object associated to the room and the user ID
            PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();
            String currentUserId = mSession.getMyUser().userId;

            if(null != powerLevels) {
                // get power levels from myself and from the member of the room
                int memberPowerLevel = powerLevels.getUserPowerLevel(mMemberId);
                int myPowerLevel = powerLevels.getUserPowerLevel(currentUserId);

                switch (aActionType) {
                    case ITEM_ACTION_START_CHAT:
                        // enable action if more than two persons are present in the room
                        if(mRoom.getActiveMembers().size()>2) {
                            retCode = true;
                        }
                        break;

                    case ITEM_ACTION_MAKE_ADMIN:
                        // if the member is already admin, then disable the action
                        int maxPowerLevel = getRoomMaxPowerLevel();
                        if(memberPowerLevel == maxPowerLevel) {
                            retCode = false;
                        } else {
                            // only an admin user (max power) can enable an admin action
                            retCode = (myPowerLevel == maxPowerLevel);
                        }
                        break;

                    case ITEM_ACTION_REMOVE_FROM_ROOM:
                        // action is enabled if: user power level is greater than the kick threshold and the member joins the room
                        retCode = (myPowerLevel >= powerLevels.kick) && (myPowerLevel > memberPowerLevel) && mIsMemberJoined;
                        Log.d(LOG_TAG,"## isItemActionEnabled() Remove action enabled? "+retCode);
                        break;

                    case ITEM_ACTION_BLOCK:
                        // "ban" action is enabled only if the member joins the room: isMemberJoined
                        retCode = (myPowerLevel >= powerLevels.ban) && (myPowerLevel > memberPowerLevel) && mIsMemberJoined;
                        Log.d(LOG_TAG,"## isItemActionEnabled() Block action enabled? "+retCode);
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
    public void performItemAction(int aActionType) {
        if((null != mRoom)&& (null != mRoomMember)) {
            switch (aActionType) {

                case ITEM_ACTION_START_CHAT:
                    Log.d(LOG_TAG,"## performItemAction(): Start new room");
                    CommonActivityUtils.displaySnack(mActionItemsListView, "Start new room");
                    MemberDetailsActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            CommonActivityUtils.goToOneToOneRoom(mSession, mMemberId, MemberDetailsActivity.this, roomActionsListener);
                        }
                    });
                    break;

                case ITEM_ACTION_MAKE_ADMIN:
                    // update the member power with the max power level of the room to make him admin
                    PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();
                    powerLevels.setUserPowerLevel(mMemberId, getRoomMaxPowerLevel());
                    //CommonActivityUtils.displaySnack(mActionItemsListView, (CharSequence) mRoomMember.displayname+" is now Admin" );
                    Log.d(LOG_TAG,"## performItemAction(): Make Admin");
                    break;

                case ITEM_ACTION_REMOVE_FROM_ROOM:
                    enableProgressBarView(CommonActivityUtils.UTILS_DISPLAY_PROGRESS_BAR);
                    mRoom.kick(mRoomMember.getUserId(), roomActionsListener);
                    Log.d(LOG_TAG,"## performItemAction(): Remove from room (Kick)");
                    break;

                case ITEM_ACTION_BLOCK:
                    enableProgressBarView(CommonActivityUtils.UTILS_DISPLAY_PROGRESS_BAR);
                    mRoom.ban(mRoomMember.getUserId(), null, roomActionsListener);
                    Log.d(LOG_TAG,"## performItemAction(): Block (Ban)");
                    break;

                default:
                    // unknown action
                    Log.w(LOG_TAG,"## performItemAction(): unknown action type = " + aActionType);
                    break;
            }
        }
    }
    // *********************************************************************************************

    /**
     * Helper method to populate the list view items with AdapterMemberActionItems objects.
     */
    /*private void populateListViewItems(){
        mActionItemsArrayList = new ArrayList<AdapterMemberActionItems>();

        // build the "start chat" item
        int imageResource = R.drawable.ic_person_add_black;
        String actionText = getResources().getString(R.string.member_details_action_start_new_room);
        mActionItemsArrayList.add(new AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_START_CHAT));

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
    }*/

    /**
     * Update items actions list view.
     * The item actions present in the list view are updated dynamically
     * according to the power levels.
     */
    private void updateAdapterListViewItems() {
        int imageResource;
        String actionText;

        // sanity check
        if((null == mListViewAdapter)){
            Log.w(LOG_TAG,"## updateListViewItemsContent(): list view adapter not initialized");
        } else {
            // reset action lists & allocate items list
            mListViewAdapter.clear();

            // build the "start chat" item
            if (isItemActionEnabled(ITEM_ACTION_START_CHAT)) {
                imageResource = R.drawable.ic_person_add_black;
                actionText = getResources().getString(R.string.member_details_action_start_new_room);
                mListViewAdapter.add(new AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_START_CHAT));
            }

            // build the "make admin" item
            if (isItemActionEnabled(ITEM_ACTION_MAKE_ADMIN)) {
                imageResource = R.drawable.ic_verified_user_black;
                actionText = getResources().getString(R.string.member_details_action_make_admin);
                mListViewAdapter.add(new AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_MAKE_ADMIN));
            }

            // build the "remove from" item (ban)
            if (isItemActionEnabled(ITEM_ACTION_REMOVE_FROM_ROOM)) {
                imageResource = R.drawable.ic_remove_circle_outline_red;
                actionText = getResources().getString(R.string.member_details_action_remove_from_room);
                mListViewAdapter.add(new AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_REMOVE_FROM_ROOM));
            }

            // build the "block" item (block)
            if (isItemActionEnabled(ITEM_ACTION_BLOCK)) {
                imageResource = R.drawable.ic_block_black;
                actionText = getResources().getString(R.string.member_details_action_block);
                mListViewAdapter.add(new AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_BLOCK));
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (CommonActivityUtils.shouldRestartApp()) {
            Log.e(LOG_TAG, "Restart the application");
            CommonActivityUtils.restartApp(this);
        }

        // retrieve the parameters contained extras and setup other
        // internal state values such as the; session, room..
        if(!initContextStateValues()){
            // init failed, just return
            Log.e(LOG_TAG, "## onCreate(): Parameters init failure");
            finish();
        }
        // find out the room member to set mRoomMember field.
        // if mRoomMember is not found among the members of the room, just finish the activity
        else if(!checkRoomMemberStatus()){
            Log.e(LOG_TAG, "## onCreate(): The user " + mMemberId + " is not in the room " + mRoomId);
            finish();
        } else {
            // setup UI view and bind the widgets
            setContentView(R.layout.activity_member_details);
            mMemberAvatarImageView = (ImageView) findViewById(R.id.avatar_img);
            mMatrixIdTextView = (TextView) findViewById(R.id.member_details_name);
            mPresenceTextView = (TextView) findViewById(R.id.member_details_presence);
            mActionItemsListView = (ListView) findViewById(R.id.member_details_actions_list_view);
            mProgressBarView = (View) findViewById(R.id.member_details_list_view_progress_bar);

            // setup the list view
            mListViewAdapter = new MemberDetailsAdapter((Context) this, R.layout.vector_adapter_member_details_items);
            mListViewAdapter.setActionListener(this);
            updateAdapterListViewItems();
            mActionItemsListView.setAdapter(mListViewAdapter);

            // update the UI
            updateUi();
        }
    }

    /**
     * Retrieve all the state values required to run the activity.
     * If values are not provided in the intent extars or are some are
     * null, then the activiy can not continue to run and must be finished
     * @return true if init succeed, false otherwise
     */
    private boolean initContextStateValues(){
        Intent intent = getIntent();
        boolean isParamInitSucceed = false;

        if(null != intent) {
            if (null == (mRoomId = intent.getStringExtra(EXTRA_ROOM_ID))) {
                Log.e(LOG_TAG, "room ID missing in extra");
            } else if (null == (mMemberId = intent.getStringExtra(EXTRA_MEMBER_ID))) {
                Log.e(LOG_TAG, "member ID missing in extra");
            } else if (null == (mSession = getSession(intent))) {
                Log.e(LOG_TAG, "Invalid session");
            } else if (isDetailsRequiredForMySelf(mSession, mMemberId)) {
                // check the case where the current logged user is asking its own details
                // if it is the case, just abort the activity launch
                Log.w(LOG_TAG, "Cancel member details for user himself");
            } else if (null == (mRoom = mSession.getDataHandler().getRoom(mRoomId))) {
                Log.e(LOG_TAG, "The room is not found");
            } else {
                // Everything is OK
                Log.d(LOG_TAG, "Parameters init succeed");
                isParamInitSucceed = true;
            }
        }

        return isParamInitSucceed;
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
     * @return true if member was found in the room , false otherwise
     */
    private boolean checkRoomMemberStatus() {
        boolean isMemberPresent = false;

        // reset output values, before re processing them
        mIsMemberJoined= false;
        mRoomMember = null;

        if (null != mRoom){
            // find out the room member
            Collection<RoomMember> members = mRoom.getMembers();
            for (RoomMember member : members) {
                if (member.getUserId().equals(mMemberId)) {
                    mRoomMember = member;
                    break;
                }
            }

            if(null == mRoomMember) {
                // The member is not (anymore) present in the room.
                // This can happen if the member has left the room in another
                // client(for any reason, kicked, left himself..) and its details are displayed
                isMemberPresent = false;
            } else if((RoomMember.MEMBERSHIP_LEAVE.equals(mRoomMember.membership))) {
                isMemberPresent = false;
            } else if((RoomMember.MEMBERSHIP_BAN.equals(mRoomMember.membership))) {
                isMemberPresent = false;
            } else {
                isMemberPresent = true;
                mIsMemberJoined = (RoomMember.MEMBERSHIP_JOIN.equals(mRoomMember.membership)) || (RoomMember.MEMBERSHIP_INVITE.equals(mRoomMember.membership));
            }
        }
        return isMemberPresent;
    }

    /**
     * Helper method to retrieve the max power level cont&ined in the room.
     * This value is used to indicate what is the power level value required
     * to be admin of the room.
     * @return max power level of the current room
     */
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
     * Update the UI 
     */
    private void updateUi() {
        if((null != mMatrixIdTextView) && (null != mRoomMember)) {
            mMatrixIdTextView.setText(mMemberId);
            setTitle(mRoomMember.displayname); // TODO TBC
        }

        // disable the progress bar
        enableProgressBarView(CommonActivityUtils.UTILS_HIDE_PROGRESS_BAR);

        // UI updates
        updateMemberAvatarUi();
        updatePresenceInfoUi();

        // Update adapter list view:
        // notify the list view to update the items that could
        // have changed according to the new rights of the member
        updateAdapterListViewItems();
        if(null != mListViewAdapter) {
            mListViewAdapter.notifyDataSetChanged();
        }
    }

    private void updatePresenceInfoUi() {
        // update the presence ring
        IMXStore store = mSession.getDataHandler().getStore();
        User user = store.getUser(mMemberId);
        String onlineStatus = "";
        int onlineStatusColour = Color.BLACK;

        // sanity check
        if (null != mPresenceTextView) {
            if ((null == user) || (null == user.presence)) {
                onlineStatus = "";
            } else {
                if (User.PRESENCE_ONLINE.equals(user.presence)) {
                    onlineStatus = getResources().getString(R.string.presence_online);
                } else {
                    // if different from online, add the "last active" info
                    String lastActiveInfo = (null != user.lastActiveAgo)?buildLastActiveDisplay(user.getRealLastActiveAgo()):"";
                    if (User.PRESENCE_OFFLINE.equals(user.presence)) {
                        onlineStatus = getResources().getString(R.string.presence_offline);
                        onlineStatusColour = Color.RED;
                    } else if (User.PRESENCE_UNAVAILABLE.equals(user.presence)) {
                        onlineStatus = getResources().getString(R.string.presence_unavailable);
                    } else if (User.PRESENCE_HIDDEN.equals(user.presence)) {
                        onlineStatus = getResources().getString(R.string.presence_hidden);
                    } else {
                        lastActiveInfo = "";
                    }

                    // add "last active ago" info
                    onlineStatus += " "+lastActiveInfo;
                }
            }

            // update presence UI
            mPresenceTextView.setText(onlineStatus);
            mPresenceTextView.setTextColor(onlineStatusColour);
        }
    }

    /**
     * update the profile avatar
     */
    private void updateMemberAvatarUi() {
        if((null != mMemberAvatarImageView) && (null != mRoomMember) && (null != mMemberId)) {
            // set default image
            VectorUtils.setMemberAvatar(mMemberAvatarImageView, mMemberId, mRoomMember.displayname);

            if (mRoomMember.avatarUrl != null) {
                int size = getResources().getDimensionPixelSize(R.dimen.profile_avatar_size);
                mSession.getMediasCache().loadAvatarThumbnail(mSession.getHomeserverConfig(), mMemberAvatarImageView, mRoomMember.avatarUrl, size);
            }
        }
    }

    /**
     * "last active" info formatter helper method.
     * @param aLastActiveAgo time info
     * @return a formatted string
     */
    private String buildLastActiveDisplay(final long aLastActiveAgo) {
        return (RoomMembersAdapter.buildLastActiveDisplay(this, aLastActiveAgo) + " ago");
    }

    /**
     * Halper method to enable/disable the progress bar view used when a
     * remote server action is on progress.
     *
     * @param aIsProgressBarDisplayed true to show the progress bar screen, false to hide it
     */
    private void enableProgressBarView(boolean aIsProgressBarDisplayed){
        if(null != mProgressBarView) {
            if (aIsProgressBarDisplayed)
                mProgressBarView.setVisibility(View.VISIBLE);
            else
                mProgressBarView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if((null != mSession) && (null != mRoom)) {
            mSession.getDataHandler().getRoom(mRoom.getRoomId()).removeEventListener(mEventListener);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        if((null != mSession) && (null != mRoom)) {
            mSession.getDataHandler().getRoom(mRoom.getRoomId()).addEventListener(mEventListener);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (null != mRoom) {
            mRoom.removeEventListener(mEventListener);
        }
    }
}
