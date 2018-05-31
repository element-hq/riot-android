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

package im.vector.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.pid.RoomThirdPartyInvite;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.util.ThemeUtils;
import im.vector.util.VectorUtils;

/**
 * An adapter which can display room information.
 */
public class VectorRoomDetailsMembersAdapter extends BaseExpandableListAdapter {
    public interface OnParticipantsListener {
        /**
         * The user taps on the dedicated "Remove" button
         *
         * @param aRoomParticipant the participant to remove
         */
        void onRemoveClick(final ParticipantAdapterItem aRoomParticipant);

        /**
         * The user taps on "Leave" button
         */
        void onLeaveClick();

        /**
         * The user selects / deselects a member.
         *
         * @param userId the selected user id.
         */
        void onSelectUserId(String userId);

        /**
         * The user taps on a cell.
         * The standard onClickListener might not work because
         * the upper view is scrollable.
         *
         * @param aRoomParticipant the clicked participant
         */
        void onClick(final ParticipantAdapterItem aRoomParticipant);

        // group expanding state management
        void onGroupCollapsedNotif(int aGroupPosition);

        void onGroupExpandedNotif(int aGroupPosition);
    }

    // search events listener
    public interface OnRoomMembersSearchListener {
        /**
         * The search is ended.
         *
         * @param aSearchCountResult the number of matched members
         * @param aIsSearchPerformed true if the search has been performed, false otherwise
         */
        void onSearchEnd(final int aSearchCountResult, final boolean aIsSearchPerformed);
    }

    private final String LOG_TAG = VectorRoomDetailsMembersAdapter.class.getSimpleName();
    private final Context mContext;
    private final LayoutInflater mLayoutInflater;

    private View mSwipingCellView;

    private final MXSession mSession;
    private final Room mRoom;
    private final int mChildLayoutResourceId;
    private final int mGroupLayoutResourceId;

    private boolean mIsMultiSelectionMode;
    private ArrayList<String> mSelectedUserIds = new ArrayList<>();

    private ArrayList<ArrayList<ParticipantAdapterItem>> mRoomMembersListByGroupPosition;

    private ArrayList<String> mDisplayNamesList = new ArrayList<>();

    private int mGroupIndexInvitedMembers = -1;  // "Invited" index
    private int mGroupIndexPresentMembers = -1; // "Favourites" index

    // search list view: list view displaying the result of the search based on "mSearchPattern"
    private String mSearchPattern = "";

    //ParticipantAdapterItem mFirstEntry;
    private OnParticipantsListener mOnParticipantsListener;

    /**
     * Recycle view holder class.
     * Used in the group views of the expandable list view.
     */
    private static class GroupViewHolder {
        final TextView mTitleTxtView;
        final ImageView mExpanderLogoImageView;

        GroupViewHolder(View aView) {
            mTitleTxtView = aView.findViewById(R.id.heading);
            mExpanderLogoImageView = aView.findViewById(R.id.heading_image);
        }
    }

    /**
     * Recycle view holder class.
     * Used in the child views of the expandable list view.
     */
    private static class ChildMemberViewHolder {
        final ImageView mMemberAvatarImageView;
        final ImageView mMemberAvatarBadgeImageView;
        final TextView mMemberNameTextView;
        final TextView mMemberStatusTextView;
        final View mHiddenListActionsView;
        final View mDeleteActionsView;
        final RelativeLayout mSwipeCellLayout;
        final CheckBox mMultipleSelectionCheckBox;

        ChildMemberViewHolder(View aParentView) {
            mMemberAvatarImageView = aParentView.findViewById(R.id.filtered_list_avatar);
            mMemberAvatarBadgeImageView = aParentView.findViewById(R.id.filtered_list_avatar_badge);
            mMemberNameTextView = aParentView.findViewById(R.id.filtered_list_name);
            mMemberStatusTextView = aParentView.findViewById(R.id.filtered_list_status);
            mHiddenListActionsView = aParentView.findViewById(R.id.filtered_list_actions);
            mSwipeCellLayout = aParentView.findViewById(R.id.filtered_list_cell);
            mMultipleSelectionCheckBox = aParentView.findViewById(R.id.filtered_list_checkbox);
            mDeleteActionsView = aParentView.findViewById(R.id.filtered_list_delete_action);
        }
    }

    /**
     * Constructor.
     *
     * @param aContext                     the context.
     * @param aChildLayoutResourceId       the child layout of the expandable list view
     * @param aGroupHeaderLayoutResourceId the group layout of the expandable list view
     * @param aSession                     the session
     * @param aRoomId                      the room id
     * @param aMediasCache                 the medias cache
     */
    public VectorRoomDetailsMembersAdapter(Context aContext, int aChildLayoutResourceId, int aGroupHeaderLayoutResourceId, MXSession aSession, String aRoomId, MXMediasCache aMediasCache) {
        mContext = aContext;
        mLayoutInflater = LayoutInflater.from(aContext);
        mChildLayoutResourceId = aChildLayoutResourceId;// R.layout.adapter_item_vector_add_participants
        mGroupLayoutResourceId = aGroupHeaderLayoutResourceId; // R.layout.adapter_item_vector_recent_header
        mSession = aSession;
        mRoom = mSession.getDataHandler().getRoom(aRoomId);

        // display check box to select multiple items
        // by default, they are not displayed
        mIsMultiSelectionMode = false;
    }

    /**
     * Search a pattern in the known members list.
     *
     * @param aPattern         the pattern to search
     * @param searchListener   the search listener
     * @param aIsRefreshForced set to tru to force the refresh
     */
    @SuppressLint("LongLogTag")
    public void setSearchedPattern(String aPattern, final OnRoomMembersSearchListener searchListener, boolean aIsRefreshForced) {
        if (TextUtils.isEmpty(aPattern)) {
            // refresh list members without any pattern filter (nominal display)
            mSearchPattern = null;
            updateRoomMembersDataModel(searchListener);
        } else {
            // new pattern different from previous one?
            if (!aPattern.trim().equals(mSearchPattern) || aIsRefreshForced) {
                mSearchPattern = aPattern.trim().toLowerCase(VectorApp.getApplicationLocale());
                updateRoomMembersDataModel(searchListener);
            } else {
                // search pattern is identical, notify listener and exit
                if (null != searchListener) {
                    int searchItemsCount = getItemsCount();
                    searchListener.onSearchEnd(searchItemsCount, false/*search not updated*/);
                }
            }
        }
    }

    /**
     * @return the total number of items
     */
    public int getItemsCount() {
        int itemsCount = getChildrenCount(mGroupIndexInvitedMembers);
        itemsCount += getChildrenCount(mGroupIndexPresentMembers);

        return itemsCount;
    }

    /**
     * Update the participants listener
     *
     * @param onParticipantsListener the listener.
     */
    public void setOnParticipantsListener(OnParticipantsListener onParticipantsListener) {
        mOnParticipantsListener = onParticipantsListener;
    }

    /**
     * @return the list of selected user ids
     */
    public ArrayList<String> getSelectedUserIds() {
        return mSelectedUserIds;
    }

    /**
     * @param isMultiSelectionMode the new selection mode
     */
    public void setMultiSelectionMode(boolean isMultiSelectionMode) {
        mIsMultiSelectionMode = isMultiSelectionMode;
        mSelectedUserIds = new ArrayList<>();
    }

    /**
     * Test if the adapter data model is filtered with the search pattern.
     *
     * @return true if search mode is enabled, false otherwise
     */
    private boolean isSearchModeEnabled() {
        return (!TextUtils.isEmpty(mSearchPattern));
    }

    /**
     * Compare 2 string and returns sort order.
     *
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

    /**
     * Update the data model of the adapter which is based on a set of ParticipantAdapterItem objects.
     *
     * @param aSearchListener search events listener, set to null if search not enabled
     */
    public void updateRoomMembersDataModel(final OnRoomMembersSearchListener aSearchListener) {
        if (!mSession.isAlive()) {
            Log.e(LOG_TAG, "updateRoomMembersDataModel the session is not anymore valid");
            return;
        }

        final Handler uiHandler = new Handler();
        final String fPattern = mSearchPattern;

        Thread t = new Thread(new Runnable() {
            public void run() {
                ParticipantAdapterItem participantItem;

                final boolean isSearchEnabled = isSearchModeEnabled();
                final ArrayList<ParticipantAdapterItem> presentMembersList = new ArrayList<>();
                final ArrayList<ArrayList<ParticipantAdapterItem>> roomMembersListByGroupPosition = new ArrayList<>();
                final ArrayList<String> displayNamesList = new ArrayList<>();

                // retrieve the room members
                final ArrayList<ParticipantAdapterItem> actualParticipants = new ArrayList<>();
                final ArrayList<ParticipantAdapterItem> invitedMembers = new ArrayList<>();

                Collection<RoomMember> activeMembers = mRoom.getActiveMembers();
                String myUserId = mSession.getMyUserId();
                final PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

                // search loop to extract the following members: current user, invited, administrator and others
                for (RoomMember member : activeMembers) {
                    participantItem = new ParticipantAdapterItem(member);

                    // if search is enabled, just skipp the member if pattern does not match
                    if (isSearchEnabled && (!participantItem.contains(mSearchPattern))) {
                        continue;
                    }

                    // oneself member ("You") is displayed on first raw
                    if (member.getUserId().equals(myUserId)) {
                        presentMembersList.add(participantItem);
                    } else {
                        if (RoomMember.MEMBERSHIP_INVITE.equals(member.membership)) {
                            // invited members
                            invitedMembers.add(participantItem);
                        } else {
                            // the other members..
                            actualParticipants.add(participantItem);
                        }
                    }

                    if (!TextUtils.isEmpty(participantItem.mDisplayName)) {
                        displayNamesList.add(participantItem.mDisplayName);
                    }
                }

                // add 3rd party invite
                Collection<RoomThirdPartyInvite> thirdPartyInvites = mRoom.getLiveState().thirdPartyInvites();

                for (RoomThirdPartyInvite invite : thirdPartyInvites) {
                    // If the home server has converted the 3pid invite into a room member, do not show it
                    if (null == mRoom.getLiveState().memberWithThirdPartyInviteToken(invite.token)) {
                        ParticipantAdapterItem participant = new ParticipantAdapterItem(invite.display_name, "", null, true);

                        if ((!isSearchEnabled) || participant.contains(mSearchPattern)) {
                            invitedMembers.add(participant);
                        }
                    }
                }

                final MXDataHandler fDataHandler = mSession.getDataHandler();

                // Comparator to order members alphabetically
                final Comparator<ParticipantAdapterItem> comparator = new Comparator<ParticipantAdapterItem>() {
                    private final HashMap<String, User> usersMap = new HashMap<>();

                    /**
                     * Get an user snapshot from an user id.
                     * @param userId the user id to find out
                     * @return teh user if it exists
                     */
                    private User getUser(String userId) {
                        User user = null;

                        if (null != userId) {
                            user = usersMap.get(userId);

                            if (null == user) {
                                user = fDataHandler.getUser(userId);

                                if (null != user) {
                                    // create a snapshot to avoid error while sorting the list
                                    // some exceptions could be triggered because of updates.
                                    user = user.deepCopy();
                                    usersMap.put(userId, user);
                                }
                            }
                        }

                        return user;
                    }

                    @Override
                    public int compare(ParticipantAdapterItem part1, ParticipantAdapterItem part2) {
                        User userA = getUser(part1.mUserId);
                        User userB = getUser(part2.mUserId);

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

                        int powerLevelA = 0;
                        int powerLevelB = 0;

                        if (null != powerLevels) {
                            if ((null != userA) && (null != userA.user_id)) {
                                powerLevelA = powerLevels.getUserPowerLevel(userA.user_id);
                            }

                            if ((null != userB) && (null != userB.user_id)) {
                                powerLevelB = powerLevels.getUserPowerLevel(userB.user_id);
                            }
                        }

                        if ((null == userA) && (null == userB)) {
                            return alphaComparator(userADisplayName, userBDisplayName);
                        } else if ((null != userA) && (null == userB)) {
                            return +1;
                        } else if ((null == userA) && (null != userB)) {
                            return -1;
                        } else if (isUserA_Active && isUserB_Active) {
                            if (powerLevelA == powerLevelB) {
                                return alphaComparator(userADisplayName, userBDisplayName);
                            } else {
                                return (powerLevelB - powerLevelA) > 0 ? +1 : -1;
                            }
                        }

                        if (isUserA_Active && !isUserB_Active) {
                            return -1;
                        }
                        if (!isUserA_Active && isUserB_Active) {
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
                };

                // create "members present in the room" list
                try {
                    Collections.sort(actualParticipants, comparator);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## updateRoomMembersDataModel failed while sorting " + e.getMessage());

                    if (TextUtils.equals(fPattern, mSearchPattern)) {

                        // most of the sort exception are triggered with
                        //  java.lang.IllegalArgumentException: Comparison method violates its general contract!
                        // it is triggered because the presences are updated while sorting.
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                updateRoomMembersDataModel(aSearchListener);
                            }
                        });
                    }

                    return;
                }

                presentMembersList.addAll(actualParticipants);

                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // test if the pattern has been updated while searching the items.
                        if (TextUtils.equals(mSearchPattern, fPattern)) {
                            mDisplayNamesList = displayNamesList;
                            mRoomMembersListByGroupPosition = roomMembersListByGroupPosition;
                            mGroupIndexPresentMembers = -1;
                            mGroupIndexPresentMembers = -1;

                            int groupIndex = 0;

                            // first group: members present in the room
                            if (0 != presentMembersList.size()) {
                                roomMembersListByGroupPosition.add(presentMembersList);
                                mGroupIndexPresentMembers = groupIndex;
                                groupIndex++;
                            }

                            // second group: invited members only
                            if (0 != invitedMembers.size()) {
                                Collections.sort(invitedMembers, comparator);
                                roomMembersListByGroupPosition.add(invitedMembers);
                                mGroupIndexInvitedMembers = groupIndex;
                            }

                            // notify end of search if listener is provided
                            if (null != aSearchListener) {
                                try {
                                    aSearchListener.onSearchEnd(getItemsCount(), isSearchEnabled);
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "## updateRoomMembersDataModel() : onSearchEnd fails " + e.getMessage());
                                }
                            }

                            // force UI rendering update
                            notifyDataSetChanged();
                        }
                    }
                });
            }
        });

        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /**
     * @return the participant User Ids except oneself.
     */
    public ArrayList<String> getUserIdsList() {
        ArrayList<String> idsListRetValue = new ArrayList<>();

        if (mGroupIndexPresentMembers >= 0) {
            int listSize = mRoomMembersListByGroupPosition.get(mGroupIndexPresentMembers).size();

            // the first item is always oneself, so skipp first element
            for (int index = 1; index < listSize; index++) {
                ParticipantAdapterItem item = mRoomMembersListByGroupPosition.get(mGroupIndexPresentMembers).get(index);

                // sanity check
                if (null != item.mUserId) {
                    idsListRetValue.add(item.mUserId);
                }
            }
        }

        return idsListRetValue;
    }

    /**
     * Compute the name of the group according to its position.
     *
     * @param aGroupPosition index of the section
     * @return group title corresponding to the index
     */
    private String getGroupTitle(int aGroupPosition) {
        String retValue;

        if (mGroupIndexInvitedMembers == aGroupPosition) {
            retValue = mContext.getResources().getString(R.string.room_details_people_invited_group_name);
        } else if (mGroupIndexPresentMembers == aGroupPosition) {
            retValue = mContext.getResources().getString(R.string.room_details_people_present_group_name);
        } else {
            // unknown section - should not happen
            retValue = "??";
        }

        return retValue;
    }


    // =============================================================================================
    // BaseExpandableListAdapter implementation
    @Override
    public void onGroupCollapsed(int aGroupPosition) {
        super.onGroupCollapsed(aGroupPosition);
        if (null != mOnParticipantsListener) {
            mOnParticipantsListener.onGroupCollapsedNotif(aGroupPosition);
        }
    }

    @Override
    public void onGroupExpanded(int aGroupPosition) {
        super.onGroupExpanded(aGroupPosition);
        if (null != mOnParticipantsListener) {
            mOnParticipantsListener.onGroupExpandedNotif(aGroupPosition);
        }
    }

    @Override
    public int getGroupCount() {
        if (null != mRoomMembersListByGroupPosition) {
            return mRoomMembersListByGroupPosition.size();
        } else {
            return 0;
        }
    }

    @SuppressLint("LongLogTag")
    @Override
    public int getChildrenCount(int aGroupPosition) {
        int countRetValue = 0;
        try {
            if ((null != mRoomMembersListByGroupPosition) && (-1 != aGroupPosition)) {
                countRetValue = mRoomMembersListByGroupPosition.get(aGroupPosition).size();
            }
        } catch (Exception ex) {
            Log.e(LOG_TAG, "## getChildrenCount(): Exception Msg=" + ex.getMessage());
        }

        return countRetValue;
    }

    @Override
    public Object getGroup(int aGroupPosition) {
        return getGroupTitle(aGroupPosition);
    }

    @Override
    public Object getChild(int aGroupPosition, int aChildPosition) {
        Object reValueObject = null;
        if (null != mRoomMembersListByGroupPosition) {
            reValueObject = mRoomMembersListByGroupPosition.get(aGroupPosition).get(aChildPosition);
        }
        return reValueObject;
    }

    @Override
    public long getGroupId(int aGroupPosition) {
        return getGroupTitle(aGroupPosition).hashCode();
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return 0L;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }


    @Override
    public View getGroupView(int aGroupPosition, boolean aIsExpanded, View aConvertView, ViewGroup aParentView) {
        GroupViewHolder viewHolder;

        if (aConvertView == null) {
            aConvertView = mLayoutInflater.inflate(mGroupLayoutResourceId, null);
            viewHolder = new GroupViewHolder(aConvertView);
            aConvertView.setTag(viewHolder);
        } else {
            viewHolder = (GroupViewHolder) aConvertView.getTag();
        }

        // set the group title
        String titleValue = getGroupTitle(aGroupPosition);
        viewHolder.mTitleTxtView.setText(titleValue);

        // set the expander logo
        int expanderLogoResId = aIsExpanded ? R.drawable.ic_material_expand_more_black : R.drawable.ic_material_expand_less_black;
        viewHolder.mExpanderLogoImageView.setImageResource(expanderLogoResId);

        return aConvertView;
    }

    @Override
    public View getChildView(final int aGroupPosition, final int aChildPosition, boolean isLastChild, View aConvertView, ViewGroup aParentView) {
        final ChildMemberViewHolder viewHolder;
        boolean isActionsMenuHidden;
        final ParticipantAdapterItem participant;
        boolean isSearchMode = isSearchModeEnabled();
        final boolean isLoggedUserPosition = ((0 == aChildPosition) && (mGroupIndexPresentMembers == aGroupPosition));

        participant = mRoomMembersListByGroupPosition.get(aGroupPosition).get(aChildPosition);

        if (aConvertView == null) {
            aConvertView = mLayoutInflater.inflate(mChildLayoutResourceId, aParentView, false);
            viewHolder = new ChildMemberViewHolder(aConvertView);
            aConvertView.setTag(viewHolder);
        } else {
            viewHolder = (ChildMemberViewHolder) aConvertView.getTag();
        }

        if (!mSession.isAlive()) {
            Log.e(LOG_TAG, "getChildView : the session is not anymore valid");
            return aConvertView;
        }

        // 1 - display member avatar
        if (null != participant.getAvatarBitmap()) {
            viewHolder.mMemberAvatarImageView.setImageBitmap(participant.getAvatarBitmap());
        } else {
            if (TextUtils.isEmpty(participant.mUserId)) {
                VectorUtils.loadUserAvatar(mContext, mSession, viewHolder.mMemberAvatarImageView, participant.mAvatarUrl, participant.mDisplayName, participant.mDisplayName);
            } else {

                // try to provide a better display for a participant when the user is known.
                if (TextUtils.equals(participant.mUserId, participant.mDisplayName) || TextUtils.isEmpty(participant.mAvatarUrl)) {
                    User user = mSession.getDataHandler().getStore().getUser(participant.mUserId);

                    if (null != user) {
                        if (TextUtils.equals(participant.mUserId, participant.mDisplayName) && !TextUtils.isEmpty(user.displayname)) {
                            participant.mDisplayName = user.displayname;
                        }

                        if (null == participant.mAvatarUrl) {
                            participant.mAvatarUrl = user.avatar_url;
                        }
                    }
                }

                VectorUtils.loadUserAvatar(mContext, mSession, viewHolder.mMemberAvatarImageView, participant.mAvatarUrl, participant.mUserId, participant.mDisplayName);
            }
        }

        // 2 - display member name
        String memberName = participant.mDisplayName;

        // detect if the displayname is used several times
        if (!TextUtils.isEmpty(memberName)) {
            int pos = mDisplayNamesList.indexOf(memberName);

            if (pos >= 0) {
                if (pos == mDisplayNamesList.lastIndexOf(memberName)) {
                    pos = -1;
                }
            }

            if ((pos >= 0) && !TextUtils.isEmpty(participant.mUserId)) {
                memberName += " (" + participant.mUserId + ")";
            }
        }
        viewHolder.mMemberNameTextView.setText(memberName);

        // 2b admin badge
        viewHolder.mMemberAvatarBadgeImageView.setVisibility(View.GONE);

        PowerLevels powerLevels = null;
        if (null != mRoom) {
            if (null != (powerLevels = mRoom.getLiveState().getPowerLevels())) {
                if (powerLevels.getUserPowerLevel(participant.mUserId) >= CommonActivityUtils.UTILS_POWER_LEVEL_ADMIN) {
                    viewHolder.mMemberAvatarBadgeImageView.setVisibility(View.VISIBLE);
                    viewHolder.mMemberAvatarBadgeImageView.setImageResource(R.drawable.admin_icon);
                } else if (powerLevels.getUserPowerLevel(participant.mUserId) >= CommonActivityUtils.UTILS_POWER_LEVEL_MODERATOR) {
                    viewHolder.mMemberAvatarBadgeImageView.setVisibility(View.VISIBLE);
                    viewHolder.mMemberAvatarBadgeImageView.setImageResource(R.drawable.mod_icon);
                }
            }
        }
        // 3 - display member status
        viewHolder.mMemberStatusTextView.setText(VectorUtils.getUserOnlineStatus(mContext, mSession, participant.mUserId, null));

        // add "remove member from room" action
        viewHolder.mDeleteActionsView.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("LongLogTag")
            @Override
            public void onClick(View v) {
                if (null != mOnParticipantsListener) {
                    try {
                        if (isLoggedUserPosition) {
                            // logged user's leaving the room..
                            mOnParticipantsListener.onLeaveClick();
                        } else {
                            mOnParticipantsListener.onRemoveClick(participant);
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## Delete action listener: Exception Msg=" + e.getMessage());
                    }
                }
            }
        });

        // manage the swipe to display actions
        if (null != mSwipingCellView) {
            mSwipingCellView.setTranslationX(0);
            mSwipingCellView = null;
        }

        // cancel any translation
        viewHolder.mSwipeCellLayout.setTranslationX(0);

        // during a room creation, there is no dedicated power level
        if (null != powerLevels) {
            int myPowerLevel;
            int memberPowerLevel;
            int kickPowerLevel;

            myPowerLevel = powerLevels.getUserPowerLevel(mSession.getCredentials().userId);
            memberPowerLevel = powerLevels.getUserPowerLevel(participant.mUserId);
            kickPowerLevel = powerLevels.kick;

            if (isLoggedUserPosition) {
                // always offer possibility to leave the room to the logged member
                isActionsMenuHidden = false;
            } else {
                // hide actions menu if my power level is lower or equal than the member's one
                isActionsMenuHidden = (((myPowerLevel <= memberPowerLevel) || (myPowerLevel < kickPowerLevel)));
            }
        } else {
            isActionsMenuHidden = (null == mRoom);
        }

        // set swipe layout click handler: notify the listener of the adapter
        viewHolder.mSwipeCellLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mOnParticipantsListener) {
                    if (!TextUtils.isEmpty(participant.mUserId)) {
                        String userId = participant.mUserId;

                        // check if the userId is valid
                        if (android.util.Patterns.EMAIL_ADDRESS.matcher(userId).matches() || (userId.startsWith("@") && (userId.indexOf(":") > 1))) {
                            mOnParticipantsListener.onClick(participant);
                        } else {
                            Toast.makeText(mContext, R.string.malformed_id, Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }
        });

        // the cellLayout setOnLongClickListener might be trapped by the scroll management
        // so add it to some UI items.
        viewHolder.mSwipeCellLayout.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });

        // SWIPE: the swipe should be enabled when there is no search and the user can kick other members
        if (isSearchMode || isActionsMenuHidden || (null == participant.mRoomMember)) {
            viewHolder.mSwipeCellLayout.setOnTouchListener(null);
        } else {
            viewHolder.mSwipeCellLayout.setOnTouchListener(new View.OnTouchListener() {
                private float mStartX = 0;

                @Override
                public boolean onTouch(final View v, MotionEvent event) {
                    final int hiddenViewWidth = viewHolder.mHiddenListActionsView.getWidth();
                    boolean isMotionTrapped = true;

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN: {
                            // cancel hidden view display
                            if (null == mSwipingCellView) {
                                mSwipingCellView = viewHolder.mSwipeCellLayout;
                            }

                            mStartX = event.getX();
                            break;
                        }
                        case MotionEvent.ACTION_MOVE: {
                            float x = event.getX() + v.getTranslationX();
                            float deltaX = Math.max(Math.min(x - mStartX, 0), -hiddenViewWidth);
                            viewHolder.mSwipeCellLayout.setTranslationX(deltaX);
                        }
                        break;
                        case MotionEvent.ACTION_CANCEL:
                        case MotionEvent.ACTION_UP: {
                            float x = event.getX() + v.getTranslationX();

                            // assume it is a tap
                            if (Math.abs(x - mStartX) < 10) {
                                // ignore the cancel event
                                if (event.getAction() == MotionEvent.ACTION_UP) {
                                    if (null != mOnParticipantsListener) {
                                        mOnParticipantsListener.onClick(participant);
                                    }
                                }
                                isMotionTrapped = false;
                            } else {
                                float deltaX = -Math.max(Math.min(x - mStartX, 0), -hiddenViewWidth);

                                if (deltaX > (hiddenViewWidth / 2)) {
                                    viewHolder.mSwipeCellLayout.setTranslationX(-hiddenViewWidth);
                                } else {
                                    viewHolder.mSwipeCellLayout.setTranslationX(0);
                                    mSwipingCellView = null;
                                }
                            }
                            break;
                        }

                        default:
                            isMotionTrapped = false;

                    }
                    return isMotionTrapped;
                }
            });
        }

        int backgroundColor = ThemeUtils.getColor(mContext, R.attr.riot_primary_background_color);

        // multi selections mode
        // do not display a checkbox for oneself
        if (mIsMultiSelectionMode && !TextUtils.equals(mSession.getMyUserId(), participant.mUserId) && (null != participant.mRoomMember)) {
            viewHolder.mMultipleSelectionCheckBox.setVisibility(View.VISIBLE);

            viewHolder.mMultipleSelectionCheckBox.setChecked(mSelectedUserIds.indexOf(participant.mUserId) >= 0);

            if (viewHolder.mMultipleSelectionCheckBox.isChecked()) {
                backgroundColor = ThemeUtils.getColor(mContext, R.attr.multi_selection_background_color);
            }

            viewHolder.mMultipleSelectionCheckBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (viewHolder.mMultipleSelectionCheckBox.isChecked()) {
                        mSelectedUserIds.add(participant.mUserId);
                        viewHolder.mSwipeCellLayout.setBackgroundColor(ThemeUtils.getColor(mContext, R.attr.multi_selection_background_color));
                    } else {
                        mSelectedUserIds.remove(participant.mUserId);
                        viewHolder.mSwipeCellLayout.setBackgroundColor(ThemeUtils.getColor(mContext, R.attr.riot_primary_background_color));
                    }

                    if (null != mOnParticipantsListener) {
                        mOnParticipantsListener.onSelectUserId(participant.mUserId);
                    }
                }
            });
        } else {
            viewHolder.mMultipleSelectionCheckBox.setVisibility(View.GONE);
        }

        viewHolder.mSwipeCellLayout.setBackgroundColor(backgroundColor);

        return aConvertView;
    }

    @Override
    public boolean isChildSelectable(int i, int i1) {
        return false;
    }
    // =============================================================================================
}
