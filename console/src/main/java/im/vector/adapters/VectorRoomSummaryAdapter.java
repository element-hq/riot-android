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
import android.graphics.Color;
import android.os.Build;
import android.support.v4.app.FragmentActivity;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.EventDisplay;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.util.VectorUtils;

/**
 * An adapter which can display room information.
 */
public class VectorRoomSummaryAdapter extends BaseExpandableListAdapter /*ConsoleRoomSummaryAdapter*/ {
    public static interface RoomEventListener {
        public void onJoinRoom(MXSession session, String roomId);
        public void onRejectInvitation(MXSession session, String roomId);

        public void onToggleRoomNotifications(MXSession session, String roomId);

        public void moveToFavorites(MXSession session, String roomId);
        public void moveToConversations(MXSession session, String roomId);
        public void moveToLowPriority(MXSession session, String roomId);

        public void onLeaveRoom(MXSession session, String roomId);
    }

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final int mChildLayoutResourceId;
    private final int mHeaderLayoutResourceId;

    private final MXSession mMxSession;
    private ArrayList<ArrayList<RoomSummary>> mSummaryListByGroupPosition;

    private int mDirectoryGroupPosition = -1;  // public rooms index
    private int mInvitedGroupPosition = -1;  // "Invited" index
    private int mFavouritesGroupPosition = -1;// "Favourites" index
    private int mNoTagGroupPosition = -1;    // "Rooms" index
    private int mLowPriorGroupPosition = -1;  // "Low Priority" index

    private final String DBG_CLASS_NAME;

    // search mode
    private String mSearchedPattern;
    private Boolean mIsSearchMode;

    // public room search
    private List<PublicRoom> mPublicRooms;
    private ArrayList<PublicRoom> mMatchedPublicRooms;

    // the listener
    private RoomEventListener mListener = null;

    /**
     * Constructor
     * @param aContext the context.
     * @param session the linked session.
     * @param isSearchMode true if the adapter is in search mode
     * @param aChildLayoutResourceId the room child layout
     * @param aGroupHeaderLayoutResourceId the room section header layout
     * @param listener the events listener
     */
    public VectorRoomSummaryAdapter(Context aContext, MXSession session, boolean isSearchMode, int aChildLayoutResourceId, int aGroupHeaderLayoutResourceId, RoomEventListener listener)  {
        // init internal fields
        mContext = aContext;
        mLayoutInflater = LayoutInflater.from(mContext);
        mChildLayoutResourceId = aChildLayoutResourceId;
        mHeaderLayoutResourceId = aGroupHeaderLayoutResourceId;
        DBG_CLASS_NAME = getClass().getName();

        // get the complete summary list
        mMxSession = session;
        mListener = listener;

        mIsSearchMode = isSearchMode;
    }

    /**
     * Provides the formatted timestamp to display.
     * null means that the timestamp text must be hidden.
     * @param event the event.
     * @return  the formatted timestamp to display.
     */
    private String getFormattedTimestamp(Event event) {
        String text =  AdapterUtils.tsToString(mContext, event.getOriginServerTs(), false);

        // don't display the today before the time
        String today = mContext.getString(R.string.today) + " ";
        if (text.startsWith(today)) {
            text = text.substring(today.length());
        }

        return text;
    }

    /**
     * Compute the name of the group according to its position.
     * @param groupPosition index of the section
     * @return group title corresponding to the index
     */
    private String getGroupTitle(int groupPosition) {
        String retValue;

        if (mDirectoryGroupPosition == groupPosition) {
            retValue = mContext.getResources().getString(R.string.room_recents_directory);
        } else if (mFavouritesGroupPosition == groupPosition) {
            retValue = mContext.getResources().getString(R.string.room_recents_favourites);
        }
        else if (mNoTagGroupPosition == groupPosition) {
            retValue = mContext.getResources().getString(R.string.room_recents_conversations);
        }
        else if (mLowPriorGroupPosition == groupPosition) {
            retValue = mContext.getResources().getString(R.string.room_recents_low_priority);
        }
        else if (mInvitedGroupPosition == groupPosition) {
            retValue = mContext.getResources().getString(R.string.room_recents_invites);
        }
        else {
            // unknown section
            retValue = "??";
        }

    return retValue;
    }

    /**
     * Fullfill an array list with a pattern.
     * @param list the list to fill.
     * @param value the pattern.
     * @param count the number of occurences
     * @return the updated list
     */
    private ArrayList<RoomSummary> fillList(ArrayList<RoomSummary> list, RoomSummary value, int count) {
        for(int i = 0; i < count; i++) {
            list.add(value);
        }

        return list;
    }

    /**
     * Check a room name contains the searched pattern.
     * @param room the room.
     * @return true of the pattern is found.
     */
    private boolean isMatchedPattern(Room room) {
        boolean res = true;

        // test only in search
        if (mIsSearchMode) {
            res = false;

            if (null != mSearchedPattern) {
                String roomName = VectorUtils.getRoomDisplayname(mContext, mMxSession, room);
                res = (!TextUtils.isEmpty(roomName) && (roomName.toLowerCase().indexOf(mSearchedPattern) >= 0));
            }
        }

        return res;
    }

    /**
     * Check a public room contains a patter,
     * @param publicRoom the public room.
     * @return true of the pattern is found.
     */
    private boolean isMatchedPattern(PublicRoom publicRoom) {
        boolean res = true;

        // test only in search
        if (mIsSearchMode) {
            res = false;

            if (null != mSearchedPattern) {
                String displayname = publicRoom.getDisplayName(mMxSession.getMyUser().userId);
                res = (!TextUtils.isEmpty(displayname) && (displayname.toLowerCase().indexOf(mSearchedPattern) >= 0));

                if (res) {
                    res = true;
                }
            }
        }

        return res;
    }

    /**
     * Test if the group position is the directory one.
     * @param groupPosition the group position test.
     * @return true if it is directory group.
     */
    public boolean isDirectoryGroupPosition(int groupPosition) {
        return (mDirectoryGroupPosition == groupPosition);
    }

    /**
     * @return the matched public rooms list
     */
    public List<PublicRoom> getMatchedPublicRooms() {

        if (null != mMatchedPublicRooms) {
            Collections.sort(mMatchedPublicRooms, new Comparator<PublicRoom>() {
                @Override
                public int compare(PublicRoom r1, PublicRoom r2) {
                    int diff = r2.numJoinedMembers - r1.numJoinedMembers;

                    if (0 == diff) {
                        diff  = VectorUtils.getPublicRoomDisplayName(r1).compareTo(VectorUtils.getPublicRoomDisplayName(r2));
                    }

                    return diff;
                }
            });
        }

        return mMatchedPublicRooms;
    }

    /**
     * Build an array of RoomSummary objects organized according to the room tags (sections).
     * So far we have 4 sections
     * - the invited rooms
     * - the rooms with tags ROOM_TAG_FAVOURITE
     * - the rooms with tags ROOM_TAG_LOW_PRIORITY
     * - the rooms with tags ROOM_TAG_NO_TAG (displayed as "ROOMS")
     * The section indexes: mFavouriteSectionIndex, mNoTagSectionIndex and mFavouriteSectionIndex are
     * also computed in this method.
     * @param aRoomSummaryCollection the complete list of RoomSummary objects
     * @return an array of summary lists splitted by sections
     */
    private ArrayList<ArrayList<RoomSummary>> buildSummariesByGroups(final Collection<RoomSummary> aRoomSummaryCollection) {
        ArrayList<ArrayList<RoomSummary>> summaryListByGroupsRetValue = new ArrayList<ArrayList<RoomSummary>>();
        String roomSummaryId;

        // init index with default values
        mDirectoryGroupPosition = -1;
        mInvitedGroupPosition = -1;
        mFavouritesGroupPosition = -1;
        mNoTagGroupPosition = -1;
        mLowPriorGroupPosition = -1;

        if(null != aRoomSummaryCollection) {

            RoomSummary dummyRoomSummary = new RoomSummary();

            // Retrieve lists of room IDs(strings) according to their tags
            final List<String> favouriteRoomIdList = mMxSession.roomIdsWithTag(RoomTag.ROOM_TAG_FAVOURITE);
            final List<String> lowPriorityRoomIdList = mMxSession.roomIdsWithTag(RoomTag.ROOM_TAG_LOW_PRIORITY);

            // ArrayLists allocations: will contain the RoomSummary objects deduced from roomIdsWithTag()
            ArrayList<RoomSummary> inviteRoomSummaryList = new ArrayList<RoomSummary>();
            ArrayList<RoomSummary> favouriteRoomSummaryList = new ArrayList<RoomSummary>(favouriteRoomIdList.size());
            ArrayList<RoomSummary> lowPriorityRoomSummaryList = new ArrayList<RoomSummary>();
            ArrayList<RoomSummary> noTagRoomSummaryList = new ArrayList<RoomSummary>(lowPriorityRoomIdList.size());

            fillList(favouriteRoomSummaryList, dummyRoomSummary, favouriteRoomIdList.size());
            fillList(lowPriorityRoomSummaryList, dummyRoomSummary, lowPriorityRoomIdList.size());

            // Search loop going through all the summaries:
            // here we translate the roomIds (Strings) to their corresponding RoomSummary objects
            for(RoomSummary roomSummary : aRoomSummaryCollection) {
                roomSummaryId = roomSummary.getRoomId();

                Room room = mMxSession.getDataHandler().getStore().getRoom(roomSummaryId);

                // check if the room exists
                if ((null != room) && isMatchedPattern(room)) {
                    // list first the summary
                    if (room.isInvited()) {
                        inviteRoomSummaryList.add(roomSummary);
                    } else {
                        int pos;

                        // search for each room Id in the room Id lists, retrieved from their corresponding tags
                        pos = favouriteRoomIdList.indexOf(roomSummaryId);
                        if (pos >= 0) {
                            // update the favourites list
                            // the favorites are ordered
                            favouriteRoomSummaryList.set(pos, roomSummary);
                        } else if ((pos = lowPriorityRoomIdList.indexOf(roomSummaryId)) >= 0) {
                            // update the low priority list
                            // the low priority are ordered
                            lowPriorityRoomSummaryList.set(pos,roomSummary);
                        } else {
                            // default case: update the no tag list
                            noTagRoomSummaryList.add(roomSummary);
                        }
                    }
                } else {
                    Log.e(DBG_CLASS_NAME, "buildSummariesBySections " + roomSummaryId + " has no known room");
                }
            }

            // Adding sections
            // Note the order here below: first the "invitations",  "favourite", then "no tag" and then "low priority"
            int groupIndex = 0;

            // in search mode
            // the public rooms have a dedicated section
            if (mIsSearchMode) {
                mMatchedPublicRooms = new ArrayList<PublicRoom>();

                if (null != mPublicRooms) {
                    for (PublicRoom publicRoom : mPublicRooms) {
                        if (isMatchedPattern(publicRoom)) {
                            mMatchedPublicRooms.add(publicRoom);
                        }
                    }
                }

                mDirectoryGroupPosition = groupIndex++;
                // create a dummy entry to keep match between section index <-> summaries list
                summaryListByGroupsRetValue.add(new ArrayList<RoomSummary>());
            }

            // first the invitations
            if (0 != inviteRoomSummaryList.size()) {
                // the invitations are sorted from the older to the oldest to the more recent ones
                Collections.reverse(inviteRoomSummaryList);
                summaryListByGroupsRetValue.add(inviteRoomSummaryList);
                mInvitedGroupPosition = groupIndex;
                groupIndex++;
            }

            // favourite
            while(favouriteRoomSummaryList.remove(dummyRoomSummary));
            if (0 != favouriteRoomSummaryList.size()) {
                summaryListByGroupsRetValue.add(favouriteRoomSummaryList);
                mFavouritesGroupPosition = groupIndex; // save section index
                groupIndex++;
            }

            // no tag
            if (0 != noTagRoomSummaryList.size()) {
                summaryListByGroupsRetValue.add(noTagRoomSummaryList);
                mNoTagGroupPosition = groupIndex; // save section index
                groupIndex++;
            }

            // low priority
            while(lowPriorityRoomSummaryList.remove(dummyRoomSummary));
            if (0 != lowPriorityRoomSummaryList.size()) {
                summaryListByGroupsRetValue.add(lowPriorityRoomSummaryList);
                mLowPriorGroupPosition = groupIndex; // save section index
            }
        }

        return summaryListByGroupsRetValue;
    }

    /**
     * Return the summary
     * @param aGroupPosition
     * @param aChildPosition
     * @return
     */
    public RoomSummary getRoomSummaryAt(int aGroupPosition, int aChildPosition) {
        RoomSummary roomSummaryRetValue = mSummaryListByGroupPosition.get(aGroupPosition).get(aChildPosition);
        return roomSummaryRetValue;
    }

    /**
     * Reset the count of the unread messages of the room set at this particular child position.
     * @param aGroupPosition group position
     * @param aChildPosition child position
     * @return true if unread count reset was effective, false if unread count was yet reseted
     */
    public boolean resetUnreadCount(int aGroupPosition, int aChildPosition) {
        boolean retCode = false;
        RoomSummary roomSummary = getRoomSummaryAt(aGroupPosition, aChildPosition);

        if(null != roomSummary) {
            Room room = this.roomFromRoomSummary(roomSummary);
            if(null != room) {
                room.sendReadReceipt();
            }

            // reset the highlight
            retCode = roomSummary.setHighlighted(Boolean.valueOf(false));
        }

        return retCode;
    }

    /**
     * Reset the count of the unread messages of the section whose index is given in aSection
     * @param aSection the section index
     * @return true if at least one summary had a unread count reseted
     */
    public boolean resetUnreadCounts(int aSection) {
        boolean retCode = false;

        ArrayList<RoomSummary> summariesList = (ArrayList<RoomSummary>)mSummaryListByGroupPosition.get(aSection);
        if(null != summariesList) {
            for (int summaryIdx = 0; summaryIdx < summariesList.size(); summaryIdx++) {
                retCode |= resetUnreadCount(aSection, summaryIdx);
            }
        }
        else {
            Log.w(DBG_CLASS_NAME,"## resetUnreadCounts(): section "+aSection +" was not found in the sections summary list");
        }

        return retCode;
    }

    /**
     * Retrieve a Room from a room summary
     * @param roomSummary the room roomId to retrieve.
     * @return the Room.
     */
    private Room roomFromRoomSummary(RoomSummary roomSummary) {
        Room roomRetValue;
        MXSession session;
        String matrixId;

        // sanity check
        if ((null == roomSummary) || (null == (matrixId=roomSummary.getMatrixId()))) {
            roomRetValue = null;
        }
        // get session and check if the session is active
        else if(null == (session=Matrix.getMXSession(mContext, matrixId)) || (!session.isActive())) {
            roomRetValue = null;
        }
        else {
            roomRetValue = session.getDataHandler().getStore().getRoom(roomSummary.getRoomId());
        }

        return roomRetValue;
    }

    /**
     * Find a summary from its room Ids.
     * @param aSectionIndex the section to search withing
     * @param aRoomId the room Id
     * @return the room summary if it is found.
     */
    public RoomSummary getSummaryByRoomId(int aSectionIndex, String aRoomId) {
        RoomSummary roomSummaryRetValue = null;
        String roomIdStr;

        if (null != mSummaryListByGroupPosition) {
            ArrayList<RoomSummary> summariesList = mSummaryListByGroupPosition.get(aSectionIndex);
            if (null != summariesList) {
                for (int summaryIdx = 0; summaryIdx < summariesList.size(); summaryIdx++) {
                    roomIdStr = (summariesList.get(summaryIdx)).getRoomId();
                    if (aRoomId.equals(roomIdStr)) {
                        roomSummaryRetValue = summariesList.get(summaryIdx);
                        break;
                    }
                }
            }
        }

        if(null == roomSummaryRetValue) {
            Log.w(DBG_CLASS_NAME, "## getSummaryByRoomId(): no summary list found for: section=" + aSectionIndex + " roomId=" + aRoomId);
        }

        return roomSummaryRetValue;
    }


    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    private void refreshSummariesList() {
        if (null != mMxSession) {
            // update/retrieve the complete summary list
            ArrayList<RoomSummary> roomSummariesCompleteList = new ArrayList<RoomSummary>(mMxSession.getDataHandler().getStore().getSummaries()) ;

            // define comparator logic
            Comparator<RoomSummary> summaryComparator = new Comparator<RoomSummary>() {
                public int compare(RoomSummary aLeftObj, RoomSummary aRightObj) {
                    int retValue;
                    long deltaTimestamp;

                    if((null == aLeftObj) || (null == aLeftObj.getLatestEvent())){
                        retValue = 1;
                    }
                    else if((null == aRightObj) || (null == aRightObj.getLatestEvent())){
                        retValue = -1;
                    }
                    else if((deltaTimestamp = aRightObj.getLatestEvent().getOriginServerTs() - aLeftObj.getLatestEvent().getOriginServerTs()) > 0) {
                        retValue = 1;
                    }
                    else if (deltaTimestamp < 0) {
                        retValue = -1;
                    }
                    else {
                        retValue = 0;
                    }

                    return retValue;
                }
            };

            Collections.sort(roomSummariesCompleteList, summaryComparator);

            // init data model used to be be displayed in the list view
            mSummaryListByGroupPosition = buildSummariesByGroups(roomSummariesCompleteList);
        }
    }

    @Override
    public void notifyDataSetChanged() {
        refreshSummariesList();
        super.notifyDataSetChanged();
    }

    @Override
    public int getGroupCount() {
        if (null != mSummaryListByGroupPosition) {
            return mSummaryListByGroupPosition.size();
        }

        return 0;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return getGroupTitle(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return getGroupTitle(groupPosition).hashCode();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        // the directory section has always only one entry
        if (mDirectoryGroupPosition == groupPosition) {
            return 1;
        }

        int countRetValue = mSummaryListByGroupPosition.get(groupPosition).size();
        return countRetValue;
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return null;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return 0L;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {

        if (null == convertView) {
            convertView = this.mLayoutInflater.inflate(this.mHeaderLayoutResourceId, null);
        }

        TextView sectionNameTxtView = (TextView)convertView.findViewById(org.matrix.androidsdk.R.id.heading);

        if (null != sectionNameTxtView) {
            sectionNameTxtView.setText(getGroupTitle(groupPosition));
        }

        ImageView imageView = (ImageView) convertView.findViewById(org.matrix.androidsdk.R.id.heading_image);

        if (!isExpanded) {
            imageView.setImageResource(R.drawable.ic_material_expand_less_black);
        } else {
            imageView.setImageResource(R.drawable.ic_material_expand_more_black);
        }

        return convertView;
    }

    /**
     * Compute the View that should be used to render the child,
     * given its position and its group’s position
     */
    @SuppressLint("NewApi")
    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        // sanity check
        if (null == mSummaryListByGroupPosition){
            return null;
        }
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mChildLayoutResourceId, parent, false);
        }

        int vectorGreenColor = mContext.getResources().getColor(R.color.vector_green_color);
        int vectorSilverColor = mContext.getResources().getColor(R.color.vector_recents_bing_gray_color);
        int vectorDefaultTimeStampColor = mContext.getResources().getColor(R.color.vector_0_54_black_color);

        // retrieve the UI items
        ImageView avatarImageView = (ImageView)convertView.findViewById(R.id.avatar_img_vector);
        TextView roomNameTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);
        TextView roomMsgTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomMessage);
        View bingUnreadMsgView = convertView.findViewById(R.id.bing_indicator_unread_message);
        TextView timestampTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
        View separatorView = convertView.findViewById(R.id.recents_separator);
        View separatorGroupView = convertView.findViewById(R.id.recents_groups_separator_line);
        final View actionView = convertView.findViewById(R.id.roomSummaryAdapter_action);
        final ImageView actionImageView = (ImageView) convertView.findViewById(R.id.roomSummaryAdapter_action_image);

        View invitationView = convertView.findViewById(R.id.recents_groups_invitation_group);
        Button joinButton = (Button)convertView.findViewById(R.id.recents_invite_join_button);
        Button rejectButton = (Button)convertView.findViewById(R.id.recents_invite_reject_button);

        // directory management
        if (mDirectoryGroupPosition == groupPosition) {
            // some items are show
            bingUnreadMsgView.setVisibility(View.INVISIBLE);
            timestampTxtView.setVisibility(View.INVISIBLE);
            actionImageView.setVisibility(View.INVISIBLE);
            invitationView.setVisibility(View.GONE);
            separatorView.setVisibility(View.GONE);
            separatorGroupView.setVisibility(View.VISIBLE);

            roomNameTxtView.setText(mContext.getResources().getString(R.string.directory_search_results_title));

            if (null == mPublicRooms) {
                roomMsgTxtView.setText(mContext.getResources().getString(R.string.directory_searching_title));
            } else {
                roomMsgTxtView.setText(mContext.getResources().getString(R.string.directory_search_results, mMatchedPublicRooms.size(), mSearchedPattern));
            }

            avatarImageView.setBackgroundColor(mContext.getResources().getColor(R.color.vector_green_color));
            avatarImageView.setImageBitmap(null);
            return convertView;
        }

        RoomSummary childRoomSummary = mSummaryListByGroupPosition.get(groupPosition).get(childPosition);
        final Room childRoom =  mMxSession.getDataHandler().getStore().getRoom(childRoomSummary.getRoomId());
        int unreadMsgCount = childRoomSummary.getUnreadEventsCount();

        // get last message to be displayed
        CharSequence lastMsgToDisplay = getChildMessageToDisplay(childRoomSummary);

        // display the room avatar
        avatarImageView.setBackgroundColor(mContext.getResources().getColor(android.R.color.transparent));
        final String roomName = VectorUtils.getRoomDisplayname(mContext, mMxSession, childRoom);
        VectorUtils.setRoomVectorAvatar(avatarImageView, childRoom.getRoomId(), roomName);

        String roomAvatarUrl = childRoom.getAvatarUrl();
        if (null != roomAvatarUrl) {
            int size = mContext.getResources().getDimensionPixelSize(org.matrix.androidsdk.R.dimen.chat_avatar_size);
            mMxSession.getMediasCache().loadAvatarThumbnail(mMxSession.getHomeserverConfig(), avatarImageView, roomAvatarUrl, size);
        }

        // display the room name
        roomNameTxtView.setText(roomName);

        // display the last message
        roomMsgTxtView.setText(lastMsgToDisplay);

        // set the timestamp
        timestampTxtView.setText(getFormattedTimestamp(childRoomSummary.getLatestEvent()));
        timestampTxtView.setTextColor(childRoomSummary.isHighlighted() ? vectorGreenColor : vectorDefaultTimeStampColor);

        // bing view
        bingUnreadMsgView.setBackgroundColor(childRoomSummary.isHighlighted() ? vectorGreenColor : ((0 != unreadMsgCount) ? vectorSilverColor : Color.TRANSPARENT));

        // some items are show
        bingUnreadMsgView.setVisibility(childRoom.isInvited() ? View.INVISIBLE : View.VISIBLE);
        timestampTxtView.setVisibility(childRoom.isInvited() ? View.INVISIBLE : View.VISIBLE);
        actionImageView.setVisibility(childRoom.isInvited() ? View.INVISIBLE : View.VISIBLE);
        invitationView.setVisibility(childRoom.isInvited() ? View.VISIBLE : View.GONE);

        final String fRoomId = childRoom.getRoomId();

        if (childRoom.isInvited()) {
            joinButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mListener) {
                        mListener.onJoinRoom(mMxSession, fRoomId);
                    }
                }
            });

            rejectButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mListener) {
                        mListener.onRejectInvitation(mMxSession, fRoomId);
                    }
                }
            });
        } else {

            final boolean isFavorite = groupPosition == mFavouritesGroupPosition;
            final boolean isLowPrior = groupPosition == mLowPriorGroupPosition;

            actionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PopupMenu popup;

                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        popup = new PopupMenu(VectorRoomSummaryAdapter.this.mContext, actionView.findViewById(R.id.roomSummaryAdapter_action_anchor), Gravity.END);
                    } else {
                        popup = new PopupMenu(VectorRoomSummaryAdapter.this.mContext, actionView.findViewById(R.id.roomSummaryAdapter_action_anchor));
                    }
                    popup.getMenuInflater().inflate(R.menu.vector_home_room_settings, popup.getMenu());

                    MenuItem item;

                    final BingRulesManager bingRulesManager = mMxSession.getDataHandler().getBingRulesManager();

                    if (bingRulesManager.isRoomNotificationsDisabled(childRoom)) {
                        item = popup.getMenu().getItem(0);
                        item.setIcon(null);
                    }

                    if (!isFavorite) {
                        item = popup.getMenu().getItem(1);
                        item.setIcon(null);
                    }

                    if (!isLowPrior) {
                        item = popup.getMenu().getItem(2);
                        item.setIcon(null);
                    }

                    item = popup.getMenu().getItem(3);
                    SpannableString s = new SpannableString(item.getTitle());
                    s.setSpan(new ForegroundColorSpan(mContext.getResources().getColor(R.color.vector_text_gray_color)), 0, s.length(), 0);
                    item.setTitle(s);

                    // force to display the icon
                    try {
                        Field[] fields = popup.getClass().getDeclaredFields();
                        for (Field field : fields) {
                            if ("mPopup".equals(field.getName())) {
                                field.setAccessible(true);
                                Object menuPopupHelper = field.get(popup);
                                Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
                                Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                                setForceIcons.invoke(menuPopupHelper, true);
                                break;
                            }
                        }
                    } catch (Exception e) {
                    }

                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(final MenuItem item) {

                            switch (item.getItemId()) {
                                case R.id.ic_action_select_notifications: {
                                    mListener.onToggleRoomNotifications(mMxSession, childRoom.getRoomId());
                                    break;
                                }
                                case R.id.ic_action_select_fav: {
                                    if (isFavorite) {
                                        mListener.moveToConversations(mMxSession, childRoom.getRoomId());
                                    } else {
                                        mListener.moveToFavorites(mMxSession, childRoom.getRoomId());
                                    }
                                    break;
                                }
                                case R.id.ic_action_select_deprioritize: {
                                    if (isLowPrior) {
                                        mListener.moveToConversations(mMxSession, childRoom.getRoomId());
                                    } else {
                                        mListener.moveToLowPriority(mMxSession, childRoom.getRoomId());
                                    }
                                    break;
                                }
                                case R.id.ic_action_select_remove: {
                                    mListener.onLeaveRoom(mMxSession, childRoom.getRoomId());
                                    break;
                                }
                            }
                            return false;
                        }
                    });

                    popup.show();
                }
            });
        }

        separatorView.setVisibility(isLastChild ? View.GONE : View.VISIBLE);
        separatorGroupView.setVisibility((isLastChild && ((groupPosition + 1) < getGroupCount())) ? View.VISIBLE : View.GONE);

        return convertView;
    }

    /**
     * Get the displayable name of the user whose ID is passed in aUserId.
     * @param aMatrixId matrix ID
     * @param aUserId user ID
     * @return the user display name
     */
    private String getMemberDisplayNameFromUserId(String aMatrixId, String aUserId) {
        String displayNameRetValue;
        MXSession session;

        if((null == aMatrixId) || (null == aUserId)){
            displayNameRetValue = null;
        }
        else if((null == (session = Matrix.getMXSession(mContext, aMatrixId))) || (!session.isActive())) {
            displayNameRetValue = null;
        }
        else {
            User user = session.getDataHandler().getStore().getUser(aUserId);

            if ((null != user) && !TextUtils.isEmpty(user.displayname)) {
                displayNameRetValue = user.displayname;
            }
            else {
                displayNameRetValue = aUserId;
            }
        }

        return displayNameRetValue;
    }

    private CharSequence getChildMessageToDisplay(RoomSummary aChildRoomSummary) {
        CharSequence messageToDisplayRetValue=null;
        EventDisplay eventDisplay;

        if(null != aChildRoomSummary) {
            if (aChildRoomSummary.getLatestEvent() != null) {
                eventDisplay = new EventDisplay(mContext, aChildRoomSummary.getLatestEvent(), aChildRoomSummary.getLatestRoomState());
                eventDisplay.setPrependMessagesWithAuthor(true);
                messageToDisplayRetValue = eventDisplay.getTextualDisplay(mContext.getResources().getColor(R.color.vector_text_gray_color));
            }

            // check if this is an invite
            if (aChildRoomSummary.isInvited() && (null != aChildRoomSummary.getInviterUserId())) {
                RoomState latestRoomState = aChildRoomSummary.getLatestRoomState();
                String inviterUserId = aChildRoomSummary.getInviterUserId();
                String myName = aChildRoomSummary.getMatrixId();

                if (null != latestRoomState) {
                    inviterUserId = latestRoomState.getMemberName(inviterUserId);
                    myName = latestRoomState.getMemberName(myName);
                }
                else {
                    inviterUserId = getMemberDisplayNameFromUserId(aChildRoomSummary.getMatrixId(), inviterUserId);
                    myName = getMemberDisplayNameFromUserId(aChildRoomSummary.getMatrixId(), myName);
                }

                // format returned message
                messageToDisplayRetValue = mContext.getString(org.matrix.androidsdk.R.string.notice_room_invite, inviterUserId, myName);
            }
        }

        return messageToDisplayRetValue;
    }

    /**
     * Defines the new searched pattern
     * @param pattern the new searched pattern
     */
    public void setSearchPattern(String pattern) {
        if (!TextUtils.equals(pattern, mSearchedPattern)) {

            if (null != pattern) {
                pattern.trim().toLowerCase();
            }

            mSearchedPattern = TextUtils.getTrimmedLength(pattern) == 0 ? null : pattern;

            // refresh the layout
            this.notifyDataSetChanged();
        }
    }

    /**
     * Update the public rooms list.
     * null means that there is a pending request.
     * @param publicRoomsList
     */
    public void setPublicRoomsList(List<PublicRoom> publicRoomsList) {
        mPublicRooms = publicRoomsList;
    }
}
