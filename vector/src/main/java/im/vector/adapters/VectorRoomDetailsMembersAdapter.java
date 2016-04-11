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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
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

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomThirdPartyInvite;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.contacts.Contact;
import im.vector.contacts.ContactsManager;
import im.vector.util.VectorUtils;

/**
 * An adapter which can display room information.
 */
public class VectorRoomDetailsMembersAdapter extends BaseExpandableListAdapter {
    public interface OnParticipantsListener {
        /**
         * The user taps on the dedicated "Remove" button
         * @param aRoomParticipant the participant to remove
         */
        void onRemoveClick(final ParticipantAdapterItem aRoomParticipant);

        /**
         * The user taps on "Leave" button
         */
        void onLeaveClick();

        /**
         * The user selects / deselects a member.
         * @param userId
         */
        void onSelectUserId(String userId);

        /**
         * The user taps on a cell.
         * The standard onClickListener might not work because
         * the upper view is scrollable.
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
         * @param aSearchCountResult the number of matched members
         * @param aIsSearchPerformed true if the search has been performed, false otherwise
         */
        void onSearchEnd(final int aSearchCountResult, final boolean aIsSearchPerformed);
    }

    private final String LOG_TAG ="VectorRoomDetailsMembersAdapter";
    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final MXMediasCache mMediasCache;

    private View mSwipingCellView;

    private final MXSession mSession;
    private String mRoomId;
    private Room mRoom;
    private final int mChildLayoutResourceId;
    private final int mGroupLayoutResourceId;

    private boolean mIsMultiSelectionMode;
    private ArrayList<String> mSelectedUserIds = new ArrayList<String>();

    private final ArrayList<ParticipantAdapterItem> mCreationParticipantsList = new ArrayList<ParticipantAdapterItem>();
    private ArrayList<ArrayList<ParticipantAdapterItem>> mRoomMembersListByGroupPosition;

    private int mGroupIndexInvitedMembers = -1;  // "Invited" index
    private int mGroupIndexPresentMembers = -1;// "Favourites" index

    // search list view: list view displaying the result of the search based on "mSearchPattern"
    //ArrayAdapter<ParticipantAdapterItem> mSearchResultsListViewAdapter;
    private ArrayList<ParticipantAdapterItem> mUnusedParticipants;
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

        GroupViewHolder(View aView){
            mTitleTxtView = (TextView) aView.findViewById(org.matrix.androidsdk.R.id.heading);
            mExpanderLogoImageView = (ImageView)aView.findViewById(org.matrix.androidsdk.R.id.heading_image);
        }
    }

    /**
     * Recycle view holder class.
     * Used in the child views of the expandable list view.
     */
    private static class ChildMemberViewHolder {
        final ImageView mAvatarImageView;
        final TextView mMemberNameTextView;
        final TextView mMemberStatusTextView;
        final View mHiddenListActionsView;
        final RelativeLayout mSwipeCellLayout;
        final CheckBox mMultipleSelectionCheckBox;

        ChildMemberViewHolder(View aParentView){
            mAvatarImageView = (ImageView)aParentView.findViewById(R.id.filtered_list_avatar);
            mMemberNameTextView = (TextView) aParentView.findViewById(R.id.filtered_list_name);
            mMemberStatusTextView = (TextView) aParentView.findViewById(R.id.filtered_list_status);
            mHiddenListActionsView = (View) aParentView.findViewById(R.id.filtered_list_actions);
            mSwipeCellLayout = (RelativeLayout) aParentView.findViewById(R.id.filtered_list_cell);
            mMultipleSelectionCheckBox = (CheckBox)aParentView.findViewById(R.id.filtered_list_checkbox);
        }
    }

    /**
     * Constructor.
     * @param aContext the context.
     * @param aChildLayoutResourceId the child layout of the expandable list view
     * @param aGroupHeaderLayoutResourceId the group layout of the expandable list view
     * @param aSession the session
     * @param aRoomId the room id
     * @param aMediasCache the medias cache
     */
    public VectorRoomDetailsMembersAdapter(Context aContext, int aChildLayoutResourceId, int aGroupHeaderLayoutResourceId, MXSession aSession, String aRoomId, boolean aMultiSelectionMode, MXMediasCache aMediasCache) {
        mContext = aContext;
        mLayoutInflater = LayoutInflater.from(aContext);
        mChildLayoutResourceId = aChildLayoutResourceId;// R.layout.adapter_item_vector_add_participants
        mGroupLayoutResourceId = aGroupHeaderLayoutResourceId; // R.layout.adapter_item_vector_recent_header
        mMediasCache = aMediasCache;
        mSession = aSession;

        // retrieve the room
        if (null != aRoomId) {
            mRoomId = aRoomId;
            mRoom = mSession.getDataHandler().getRoom(aRoomId);
        }

        if (null == mRoom) {
            MyUser myUser = mSession.getMyUser();

            ParticipantAdapterItem item = new ParticipantAdapterItem(myUser.displayname, myUser.getAvatarUrl(), myUser.user_id);
            //this.add(item); TODO TBC old listview
            mCreationParticipantsList.add(item);
        }

        // display check box to select multiple items
        mIsMultiSelectionMode = aMultiSelectionMode;
    }

    /**
     * Search a pattern in the known members list.
     * @param pattern the pattern to search
     */
    public void setSearchedPattern(String pattern, final OnRoomMembersSearchListener searchListener, boolean aIsRefreshForced) {
        setSearchedPattern(pattern, null, searchListener, aIsRefreshForced);
    }

    /**
     * Search a pattern in the known members list.
     * @param aPattern the pattern to search
     * @param aFirstEntry the entry to display in the results list.
     * @param aIsRefreshForced true to force a refresh whatever pattern value.
     */
    @SuppressLint("LongLogTag")
    private void setSearchedPattern(String aPattern, ParticipantAdapterItem aFirstEntry, OnRoomMembersSearchListener searchListener, boolean aIsRefreshForced) {
        if(null == aPattern) {
            // refresh list members without any pattern filter (nominal display)
            mSearchPattern = null;
            refresh(searchListener);
        }
        else if(!TextUtils.isEmpty(aPattern)) {
            // new pattern different from previous one?
            if (!aPattern.trim().equals(mSearchPattern) || aIsRefreshForced) {
                mSearchPattern = aPattern.trim().toLowerCase();
                refresh(searchListener);
            } else {
                // search pattern is identical, notify listener and exit
                if (null != searchListener) {
                    int searchItemsCount = getItemsCount();
                    searchListener.onSearchEnd(searchItemsCount, false/*search not updated*/);
                }
            }
        } else {
            Log.w(LOG_TAG,"## setSearchedPattern(): no search performed due to empty pattern");

            if (null != searchListener) {
                searchListener.onSearchEnd(0, false/*search not performed*/);
            }
        }
    }

    /**
     * @return the searched pattern.
     */
    public String getSearchedPattern() {
        return mSearchPattern;
    }


    public int getItemsCount() {
        int itemsCount = getChildrenCount(mGroupIndexInvitedMembers);
        itemsCount += getChildrenCount(mGroupIndexPresentMembers);

        return itemsCount;
    }

    /**
     * Refresh the un-invited members
     */
    public void listOtherMembers() {
        // refresh only when performing a search
        if (!isSearchModeEnabled()) {
            return;
        }

        ArrayList<ParticipantAdapterItem> unusedParticipants = new ArrayList<ParticipantAdapterItem>();
        IMXStore store = mSession.getDataHandler().getStore();

        // list the used members IDs
        ArrayList<String> idsToIgnore = new ArrayList<String>();

        if (null != mRoomId) {
            Room fromRoom = store.getRoom(mRoomId);
            Collection<RoomMember> members = fromRoom.getMembers();
            for(RoomMember member : members) {
                if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN) || TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_INVITE)) {
                    idsToIgnore.add(member.getUserId());
                }
            }

        } else {
            for(ParticipantAdapterItem item : mCreationParticipantsList) {
                idsToIgnore.add(item.mUserId);
            }
        }


        // check from any other known users
        // because theirs presence have been received
        Collection<User> users = mSession.getDataHandler().getStore().getUsers();
        for(User user : users) {
            // accepted User ID or still active users
            if (idsToIgnore.indexOf(user.user_id) < 0) {
                unusedParticipants.add(new ParticipantAdapterItem(user));
                idsToIgnore.add(user.user_id);
            }
        }

        // checks for each room
        Collection<RoomSummary> summaries = mSession.getDataHandler().getStore().getSummaries();

        for(RoomSummary summary : summaries) {
            // not the current summary
            if (!summary.getRoomId().equals(mRoomId)) {
                Room curRoom = mSession.getDataHandler().getRoom(summary.getRoomId());
                Collection<RoomMember> otherRoomMembers = curRoom.getMembers();

                for (RoomMember member : otherRoomMembers) {
                    String userID = member.getUserId();

                    // accepted User ID or still active users
                    if (idsToIgnore.indexOf(userID) < 0) {
                        unusedParticipants.add(new ParticipantAdapterItem(member.getName(), member.avatarUrl, member.getUserId()));
                        idsToIgnore.add(member.getUserId());
                    }
                }
            }
        }

        // contacts
        Collection<Contact> contacts = ContactsManager.getLocalContactsSnapshot(mContext);

        for(Contact contact : contacts) {
            if (contact.hasMatridIds(mContext)) {
                Contact.MXID mxId = contact.getFirstMatrixId();

                if (idsToIgnore.indexOf(mxId.mMatrixId) < 0) {
                    unusedParticipants.add(new ParticipantAdapterItem(contact, mContext));
                    idsToIgnore.add(mxId.mMatrixId);
                }
            } else {
                unusedParticipants.add(new ParticipantAdapterItem(contact, mContext));
            }
        }

        mUnusedParticipants = unusedParticipants;
    }

    /**
     * Update the paticipants listener
     * @param onParticipantsListener
     */
    public void setOnParticipantsListener(OnParticipantsListener onParticipantsListener) {
        mOnParticipantsListener = onParticipantsListener;
    }

    /**
     * Add a participant to the edition list.
     * @param participant the participant to add.
     */
    public void addParticipant(ParticipantAdapterItem participant) {
        if (null == mRoom) {
            mUnusedParticipants.remove(participant);
            mCreationParticipantsList.add(participant);
        }
    }

    /**
     * Remove a participant from the edition list.
     * @param aParticipant the participant to remove.
     */
    public void removeParticipant(ParticipantAdapterItem aParticipant) {
        if (null == mRoom) {
            mCreationParticipantsList.remove(aParticipant);
            mUnusedParticipants.add(aParticipant);

            removeMember(aParticipant.mReferenceGroupPosition, aParticipant.mReferenceChildPosition);
        }
    }

    @SuppressLint("LongLogTag")
    private void removeMember(int aGroupPosition, int aChildPosition) {
        if(null != mRoomMembersListByGroupPosition){
            try {
                mRoomMembersListByGroupPosition.get(aGroupPosition).remove(aChildPosition);
            } catch(Exception ex){
                // In particular, any "out of bounds" exception.. (unlikely)
                Log.w(LOG_TAG,"## removeCurrentMember(): Exception Msg="+ex.getMessage());
            }
        }
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
        mSelectedUserIds = new ArrayList<String>();
    }

    // Comparator to order members alphabetically
    public static Comparator<RoomMember> alphaComparator = new Comparator<RoomMember>() {
        @Override
        public int compare(RoomMember member1, RoomMember member2) {
            String lhs = member1.getName();
            String rhs = member2.getName();

            if (lhs == null) {
                return -1;
            }
            else if (rhs == null) {
                return 1;
            }
            if (lhs.startsWith("@")) {
                lhs = lhs.substring(1);
            }
            if (rhs.startsWith("@")) {
                rhs = rhs.substring(1);
            }
            return String.CASE_INSENSITIVE_ORDER.compare(lhs, rhs);
        }
    };

    /**
     * Refresh the list of the room members
     */
    /*public void refresh() {
        // Reset search pattern to force an update of the
        // member list without any search filter
        mSearchPattern = null;

        refresh(null);
    }*/


    /**
     * Test if the adapter data model is filtered with the search pattern.
     * @return true if search mode is enabled, false otherwise
     */
    public boolean isSearchModeEnabled() {
        return (!TextUtils.isEmpty(mSearchPattern));
    }

    /**
     * Update the data model of the adapter which is based on a set of ParticipantAdapterItem objects.
     * @param aSearchListener search events listener, set to null if search not enabled
     */
    // TODO rename refresh() updateRoomMembersDataModel()
    private void refresh(final OnRoomMembersSearchListener aSearchListener) {
        boolean isSearchEnabled = false;
        int groupIndex = 0;
        int searchItemsCount = 0;
        ParticipantAdapterItem participantItem;
        ArrayList<ParticipantAdapterItem> presentMembersList = new ArrayList<ParticipantAdapterItem>();

        if (isSearchModeEnabled()) {
            isSearchEnabled = true;
        }

        if (null == mRoomMembersListByGroupPosition) {
            mRoomMembersListByGroupPosition = new ArrayList<ArrayList<ParticipantAdapterItem>>();
        } else {
            mRoomMembersListByGroupPosition.clear();
        }

        // reset group indexes
        mGroupIndexPresentMembers = -1;
        mGroupIndexInvitedMembers = -1;

        // retrieve the room members
        if (null != mRoom) {
            ArrayList<ParticipantAdapterItem> adminMembers = new ArrayList<ParticipantAdapterItem>();
            ArrayList<ParticipantAdapterItem> otherMembers = new ArrayList<ParticipantAdapterItem>();
            ArrayList<ParticipantAdapterItem> invitedMembers = new ArrayList<ParticipantAdapterItem>();

            Collection<RoomMember> activeMembers = mRoom.getActiveMembers();
            String myUserId = mSession.getMyUserId();
            PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

            // search loop to extract the following members: current user, invited, administrator and others
            for (RoomMember member : activeMembers) {
                participantItem = new ParticipantAdapterItem(member);

                // if search is enabled, just skipp the member if pattern does not match
                if(isSearchEnabled && (!participantItem.matchWithPattern(mSearchPattern))){
                    continue;
                }

                // oneself member ("You") is displayed on first raw
                if (member.getUserId().equals(myUserId)) {
                    presentMembersList.add(participantItem);
                } else {
                    if (RoomMember.MEMBERSHIP_INVITE.equals(member.membership)) {
                        // invited members
                        invitedMembers.add(participantItem);
                    } else if ((null != powerLevels) && (powerLevels.getUserPowerLevel(member.getUserId()) >= CommonActivityUtils.UTILS_POWER_LEVEL_ADMIN)) {
                        // administrator members
                        adminMembers.add(participantItem);
                    } else {
                        // the other members..
                        otherMembers.add(participantItem);
                    }
                }
            }

            // add 3rd party invite
            Collection<RoomThirdPartyInvite> thirdPartyInvites = mRoom.getLiveState().thirdPartyInvites();

            for (RoomThirdPartyInvite invite : thirdPartyInvites) {
                // If the home server has converted the 3pid invite into a room member, do not show it
                if (null == mRoom.getLiveState().memberWithThirdPartyInviteToken(invite.token)) {
                    invitedMembers.add(new ParticipantAdapterItem(invite.display_name, "", null));
                }
            }

            // create "members present in the room" list
            Collections.sort(adminMembers, ParticipantAdapterItem.alphaComparator);
            presentMembersList.addAll(adminMembers);

            Collections.sort(otherMembers, ParticipantAdapterItem.alphaComparator);
            presentMembersList.addAll(otherMembers);

            // first group: members present in the room
            if (0 != presentMembersList.size()) {
                mRoomMembersListByGroupPosition.add(presentMembersList);
                mGroupIndexPresentMembers = groupIndex;
                groupIndex++;
            }

            // second group: invited members only
            if (0 != invitedMembers.size()) {
                Collections.sort(invitedMembers, ParticipantAdapterItem.alphaComparator);
                mRoomMembersListByGroupPosition.add(invitedMembers);
                mGroupIndexInvitedMembers = groupIndex;
            }

            mUnusedParticipants = null;
        } else {
            Collections.sort(mCreationParticipantsList, ParticipantAdapterItem.alphaComparator);
            mRoomMembersListByGroupPosition.add(mCreationParticipantsList);
            mGroupIndexInvitedMembers = groupIndex;
        }

        // notify end of search if listener is provided
        if (null != aSearchListener) {
            searchItemsCount = getItemsCount();
            aSearchListener.onSearchEnd(searchItemsCount, isSearchEnabled);
        }

        // force UI rendering update
        notifyDataSetChanged();
    }

    /**
     *
     * @return the participant User Ids except oneself.
     */
    public ArrayList<String> getUserIdsList() {
        ArrayList<String> idsListRetValue = new ArrayList<String>();

        if(mGroupIndexPresentMembers >= 0) {
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
     * @param aGroupPosition index of the section
     * @return group title corresponding to the index
     */
    private String getGroupTitle(int aGroupPosition) {
        String retValue;

        if (mGroupIndexInvitedMembers == aGroupPosition) {
            retValue = mContext.getResources().getString(R.string.room_details_people_invited_group_name);
        } else if (mGroupIndexPresentMembers== aGroupPosition) {
            retValue = mContext.getResources().getString(R.string.room_details_people_present_group_name);
        }
        else {
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

    /*@Override
    public void notifyDataSetChanged() {
        refresh();
        super.notifyDataSetChanged();
    }*/

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
            if ( (null != mRoomMembersListByGroupPosition) && (-1 != aGroupPosition)) {
                countRetValue = mRoomMembersListByGroupPosition.get(aGroupPosition).size();
            }
        } catch(Exception ex) {
          Log.e(LOG_TAG,"## getChildrenCount(): Exception Msg=" + ex.getMessage());
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
        if(null != mRoomMembersListByGroupPosition) {
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
            // TODO aConvertView = mLayoutInflater.inflate(mGroupLayoutResourceId, aParentView, false);
            viewHolder =  new GroupViewHolder(aConvertView);
            aConvertView.setTag(viewHolder);
        } else {
            viewHolder = (GroupViewHolder)aConvertView.getTag();
        }

        // set the group title
        String titleValue = getGroupTitle(aGroupPosition);
        viewHolder.mTitleTxtView.setText(titleValue);

        // set the expander logo
        int expanderLogoResId = aIsExpanded?R.drawable.ic_material_expand_more_black:R.drawable.ic_material_expand_less_black;
        viewHolder.mExpanderLogoImageView.setImageResource(expanderLogoResId);

        return aConvertView;
    }

    @Override
    public View getChildView(final int aGroupPosition, final int aChildPosition, boolean isLastChild, View aConvertView, ViewGroup aParentView) {
        boolean isActionsMenuHidden = true;
        final ParticipantAdapterItem participant;
        boolean isSearchMode = isSearchModeEnabled();
        final boolean isLoggedUserPosition = ((0==aChildPosition) && (mGroupIndexPresentMembers==aGroupPosition));

        participant = mRoomMembersListByGroupPosition.get(aGroupPosition).get(aChildPosition);

        // set group/child positions
        participant.mReferenceGroupPosition = aGroupPosition;
        participant.mReferenceChildPosition = aChildPosition;

        if (aConvertView == null) {
            aConvertView = mLayoutInflater.inflate(mChildLayoutResourceId, aParentView, false);
        }

        final ImageView memberAvatarImageView = (ImageView) aConvertView.findViewById(R.id.filtered_list_avatar);
        final TextView memberNameTextView = (TextView) aConvertView.findViewById(R.id.filtered_list_name);
        final TextView memberStatusTextView = (TextView) aConvertView.findViewById(R.id.filtered_list_status);
        final View hiddenListActionsView = aConvertView.findViewById(R.id.filtered_list_actions);
        final RelativeLayout swipeCellLayout = (RelativeLayout) aConvertView.findViewById(R.id.filtered_list_cell); // swipe management
        final CheckBox multipleSelectionCheckBox = (CheckBox)aConvertView.findViewById(R.id.filtered_list_checkbox);
        View deleteActionsView = aConvertView.findViewById(R.id.filtered_list_delete_action);

        // 1 - display member avatar
        if (null != participant.mAvatarBitmap) {
            memberAvatarImageView.setImageBitmap(participant.mAvatarBitmap);
        } else {
            {
                if (TextUtils.isEmpty(participant.mUserId)) {
                    VectorUtils.loadUserAvatar(mContext, mSession, memberAvatarImageView, participant.mAvatarUrl, participant.mDisplayName, participant.mDisplayName);
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

                    VectorUtils.loadUserAvatar(mContext, mSession, memberAvatarImageView, participant.mAvatarUrl, participant.mUserId, participant.mDisplayName);
                }
            }
        }

        // 2 - display member name
        // Specific member name: member is "You" - at 0 position we must find the logged user, we then do not display its name, but R.string.you
        String memberName = (isLoggedUserPosition && !isSearchMode) ? (String)mContext.getText(R.string.you) : participant.mDisplayName;

        // Specific member name: member is "admin" - "admin" text is added next to the name
        PowerLevels powerLevels = null;
        if (null != mRoom) {
            if (!isSearchMode && (null != (powerLevels = mRoom.getLiveState().getPowerLevels()))) {
                if (powerLevels.getUserPowerLevel(participant.mUserId) >= CommonActivityUtils.UTILS_POWER_LEVEL_ADMIN) {
                    memberName = mContext.getString(R.string.room_participants_admin_name, memberName);
                }
            }
        }
        memberNameTextView.setText(memberName);

        // 3 - display member status
        String status = "";
        if ((null != participant.mRoomMember) && (null != participant.mRoomMember.membership) && !TextUtils.equals(participant.mRoomMember.membership, RoomMember.MEMBERSHIP_JOIN)) {
            if (TextUtils.equals(participant.mRoomMember.membership, RoomMember.MEMBERSHIP_INVITE)) {
                status = mContext.getString(R.string.room_participants_invite);
            } else if (TextUtils.equals(participant.mRoomMember.membership, RoomMember.MEMBERSHIP_LEAVE)) {
                status = mContext.getString(R.string.room_participants_leave);
            } else if (TextUtils.equals(participant.mRoomMember.membership, RoomMember.MEMBERSHIP_BAN)) {
                status = mContext.getString(R.string.room_participants_ban);
            }
        } else if ((null == participant.mUserId) && (null == participant.mRoomMember) && (!isSearchMode))  {
            // 3rd party invitation
            status = mContext.getString(R.string.room_participants_invite);
        } else if (null != participant.mUserId) {
            User user = null;
            MXSession matchedSession = null;
            // retrieve the linked user
            ArrayList<MXSession> sessions = Matrix.getMXSessions(mContext);

            for(MXSession session : sessions) {
                if (null == user) {
                    matchedSession = session;
                    user = session.getDataHandler().getUser(participant.mUserId);
                } else {
                    break;
                }
            }

            // find a related user
            if (null != user) {
                status = VectorUtils.getUserOnlineStatus(mContext, matchedSession, participant.mUserId);
            }
        }
        memberStatusTextView.setText(status);

        // add "remove member from room" action
        deleteActionsView.setOnClickListener(new View.OnClickListener() {
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
                        Log.e(LOG_TAG,"## Delete action listener: Exception Msg="+e.getMessage());
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
        swipeCellLayout.setTranslationX(0);

        // during a room creation, there is no dedicated power level
        if (null != powerLevels) {
            int myPowerLevel;
            int memberPowerLevel;
            int kickPowerLevel;

            myPowerLevel = powerLevels.getUserPowerLevel(mSession.getCredentials().userId);
            memberPowerLevel = powerLevels.getUserPowerLevel(participant.mUserId);
            kickPowerLevel = powerLevels.kick;

            if(isLoggedUserPosition) {
                // always offer possibility to leave the room to the logged member
                isActionsMenuHidden = false;
            } else {
                // hide actions menu if my power level is lower than the member's one
                isActionsMenuHidden = (((myPowerLevel < memberPowerLevel) || (myPowerLevel < kickPowerLevel)));
            }
        } else {
            isActionsMenuHidden = (null == mRoom);
        }

        // set swipe layout click handler: notify the listener of the adapter
        swipeCellLayout.setOnClickListener(new View.OnClickListener() {
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

        // set long click handler: copy member name to clipboard
        View.OnLongClickListener onLongClickListener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("", memberNameTextView.getText());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(mContext, mContext.getResources().getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();

                return true;
            }
        };
        // the cellLayout setOnLongClickListener might be trapped by the scroll management
        // so add it to some UI items.
        swipeCellLayout.setOnLongClickListener(onLongClickListener);
        memberNameTextView.setOnLongClickListener(onLongClickListener);
        memberAvatarImageView.setOnLongClickListener(onLongClickListener);

        // SWIPE: the swipe should be enabled when there is no search and the user can kick other members
        if (isSearchMode || isActionsMenuHidden || (null == participant.mRoomMember)) {
            swipeCellLayout.setOnTouchListener(null);
        } else {
            swipeCellLayout.setOnTouchListener(new View.OnTouchListener() {
                private float mStartX = 0;

                @Override
                public boolean onTouch(final View v, MotionEvent event) {
                    final int hiddenViewWidth = hiddenListActionsView.getWidth();
                    boolean isMotionTrapped = true;

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN: {
                            // cancel hidden view display
                            if (null != mSwipingCellView) {
                                mSwipingCellView.setTranslationX(0);
                                mSwipingCellView = null;
                                return false;
                            }

                            mSwipingCellView = swipeCellLayout;
                            mStartX = event.getX();
                            break;
                        }
                        case MotionEvent.ACTION_MOVE: {
                            float x = event.getX() + v.getTranslationX();
                            float deltaX = Math.max(Math.min(x - mStartX, 0), -hiddenViewWidth);
                            swipeCellLayout.setTranslationX(deltaX);
                        }
                        break;
                        case MotionEvent.ACTION_CANCEL:
                        case MotionEvent.ACTION_UP: {
                            float x = event.getX() + v.getTranslationX();

                            // assume it is a tap
                            if (Math.abs(x - mStartX) < 3) {
                                if (null != mOnParticipantsListener) {
                                    mOnParticipantsListener.onClick(participant);
                                }
                                isMotionTrapped = false;
                            } else {
                                float deltaX = -Math.max(Math.min(x - mStartX, 0), -hiddenViewWidth);


                                if (deltaX > (hiddenViewWidth / 2)) {
                                    swipeCellLayout.setTranslationX(-hiddenViewWidth);
                                } else {
                                    swipeCellLayout.setTranslationX(0);
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

        int backgroundColor = mContext.getResources().getColor(android.R.color.white);

        // multi selections mode
        // do not display a checkbox for oneself
        if (mIsMultiSelectionMode && !TextUtils.equals(mSession.getMyUserId(), participant.mUserId) && (null != participant.mRoomMember)) {
            multipleSelectionCheckBox.setVisibility(View.VISIBLE);

            multipleSelectionCheckBox.setChecked(mSelectedUserIds.indexOf(participant.mUserId) >= 0);

            if (multipleSelectionCheckBox.isChecked()) {
                backgroundColor = mContext.getResources().getColor(R.color.vector_05_gray);
            }

            multipleSelectionCheckBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (multipleSelectionCheckBox.isChecked()) {
                        mSelectedUserIds.add(participant.mUserId);
                        swipeCellLayout.setBackgroundColor(mContext.getResources().getColor(R.color.vector_05_gray));
                    } else {
                        mSelectedUserIds.remove(participant.mUserId);
                        swipeCellLayout.setBackgroundColor(mContext.getResources().getColor(android.R.color.white));
                    }

                    if (null != mOnParticipantsListener) {
                        mOnParticipantsListener.onSelectUserId(participant.mUserId);
                    }
                }
            });
        } else {
            multipleSelectionCheckBox.setVisibility(View.GONE);
        }

        swipeCellLayout.setBackgroundColor(backgroundColor);

        return aConvertView;
    }

    @Override
    public boolean isChildSelectable(int i, int i1) {
        return false;
    }
    // =============================================================================================
}
