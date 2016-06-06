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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
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

import org.matrix.androidsdk.MXDataHandler;
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
public class VectorRoomSummaryAdapter extends BaseExpandableListAdapter {
    public interface RoomEventListener {
        void onPreviewRoom(MXSession session, String roomId);
        void onRejectInvitation(MXSession session, String roomId);

        void onToggleRoomNotifications(MXSession session, String roomId);

        void moveToFavorites(MXSession session, String roomId);
        void moveToConversations(MXSession session, String roomId);
        void moveToLowPriority(MXSession session, String roomId);

        void onLeaveRoom(MXSession session, String roomId);
        void onGroupCollapsedNotif(int aGroupPosition);
        void onGroupExpandedNotif(int aGroupPosition);
    }

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final int mChildLayoutResourceId;
    private final int mHeaderLayoutResourceId;

    private final MXSession mMxSession;
    private ArrayList<ArrayList<RoomSummary>> mSummaryListByGroupPosition;

    private int mRoomByAliasGroupPosition = -1; // the user wants to join  by room id or alias
    private int mDirectoryGroupPosition = -1;  // public rooms index
    private int mInvitedGroupPosition = -1;  // "Invited" index
    private int mFavouritesGroupPosition = -1;// "Favourites" index
    private int mNoTagGroupPosition = -1;    // "Rooms" index
    private int mLowPriorGroupPosition = -1;  // "Low Priority" index

    private final String DBG_CLASS_NAME;

    // search mode
    private String mSearchedPattern;
    private boolean mIsSearchMode;
    // when set to true, avoid empty history by displaying the directory group
    private boolean mDisplayDirectoryGroupWhenEmpty;

    // public room search
    private List<PublicRoom> mPublicRooms;
    private ArrayList<PublicRoom> mMatchedPublicRooms;

    // the listener
    private RoomEventListener mListener;

    // drag and drop mode
    private boolean mIsDragAndDropMode = false;

    /**
     * Constructor
     * @param aContext the context.
     * @param session the linked session.
     * @param isSearchMode true if the adapter is in search mode
     * @param displayDirectoryGroupWhenEmpty true to avoid empty history
     * @param aChildLayoutResourceId the room child layout
     * @param aGroupHeaderLayoutResourceId the room section header layout
     * @param listener the events listener
     */
    public VectorRoomSummaryAdapter(Context aContext, MXSession session, boolean isSearchMode, boolean displayDirectoryGroupWhenEmpty, int aChildLayoutResourceId, int aGroupHeaderLayoutResourceId, RoomEventListener listener)  {
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
        mDisplayDirectoryGroupWhenEmpty = displayDirectoryGroupWhenEmpty;
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

        if (mRoomByAliasGroupPosition == groupPosition) {
            retValue = mContext.getResources().getString(R.string.room_recents_join);
        } else if (mDirectoryGroupPosition == groupPosition) {
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

            if (!TextUtils.isEmpty(mSearchedPattern)) {
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
        if (mIsSearchMode && !TextUtils.isEmpty(mSearchedPattern)) {
            String displayname = publicRoom.getDisplayName(mMxSession.getMyUserId());

            res = (!TextUtils.isEmpty(displayname) && (displayname.toLowerCase().indexOf(mSearchedPattern) >= 0));
        }

        return res;
    }

    /**
     * Tell if the group position is the join by
     * @param groupPosition the group position to test.
     * @return true if it is room id group
     */
    public boolean isRoomByIdGroupPosition(int groupPosition) {
        return (mRoomByAliasGroupPosition == groupPosition);
    }

    /**
     * Test if the group position is the directory one.
     * @param groupPosition the group position to test.
     * @return true if it is directory group.
     */
    public boolean isDirectoryGroupPosition(int groupPosition) {
        return (mDirectoryGroupPosition == groupPosition);
    }

    /**
     * @return true if the directory group is displayed
     */
    public boolean isDirectoryGroupDisplayed() {
        return (-1 != mDirectoryGroupPosition);
    }

    @Override
    public void onGroupCollapsed(int groupPosition) {
        super.onGroupCollapsed(groupPosition);
        if(null != mListener) {
            mListener.onGroupCollapsedNotif(groupPosition);
        }
    }

    @Override
    public void onGroupExpanded(int groupPosition) {
        super.onGroupExpanded(groupPosition);
        if(null != mListener) {
            mListener.onGroupExpandedNotif(groupPosition);
        }
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
                        diff = VectorUtils.getPublicRoomDisplayName(r1).compareTo(VectorUtils.getPublicRoomDisplayName(r2));
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
        mRoomByAliasGroupPosition = -1;
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
                } else if (null == room) {
                    Log.e(DBG_CLASS_NAME, "buildSummariesBySections " + roomSummaryId + " has no known room");
                }
            }

            // Adding sections
            // Note the order here below: first the "invitations",  "favourite", then "no tag" and then "low priority"
            int groupIndex = 0;

            // in search mode
            // the public rooms have a dedicated section
            if (mIsSearchMode || mDisplayDirectoryGroupWhenEmpty) {
                mMatchedPublicRooms = new ArrayList<PublicRoom>();

                if (null != mPublicRooms) {
                    for (PublicRoom publicRoom : mPublicRooms) {
                        if (isMatchedPattern(publicRoom)) {
                            mMatchedPublicRooms.add(publicRoom);
                        }
                    }
                }

                // detect if the pattern might a room ID or an alias
                if (!TextUtils.isEmpty(mSearchedPattern)) {
                    // a room id is !XXX:server.ext
                    // a room alias is #XXX:server.ext

                    boolean isRoomId = false;
                    boolean isRoomAlias = false;

                    if (mSearchedPattern.startsWith("!")) {
                        int sep = mSearchedPattern.indexOf(":");

                        if (sep > 0) {
                            sep = mSearchedPattern.indexOf(".", sep);
                        }

                        isRoomId = sep > 0;
                    } else if (mSearchedPattern.startsWith("#")) {
                        int sep = mSearchedPattern.indexOf(":");

                        if (sep > 0) {
                            sep = mSearchedPattern.indexOf(".", sep);
                        }

                        isRoomAlias = sep > 0;
                    }

                    if (isRoomId || isRoomAlias) {
                        mRoomByAliasGroupPosition = groupIndex++;
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
                groupIndex++;
            }

            // in avoiding empty history mode
            // check if there is really nothing else
            if (mDisplayDirectoryGroupWhenEmpty && (groupIndex > 1)) {
                summaryListByGroupsRetValue.remove(mDirectoryGroupPosition);
                mRoomByAliasGroupPosition = -1;
                mDirectoryGroupPosition = -1;
                mInvitedGroupPosition--;
                mFavouritesGroupPosition--;
                mNoTagGroupPosition--;
                mLowPriorGroupPosition--;
            }
        }

        return summaryListByGroupsRetValue;
    }

    /**
     * Return the summary
     * @param aGroupPosition group position
     * @param aChildPosition child position
     * @return the corresponding room summary
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

        ArrayList<RoomSummary> summariesList = mSummaryListByGroupPosition.get(aSection);
        if(null != summariesList) {
            for (int summaryIdx = 0; summaryIdx < summariesList.size(); summaryIdx++) {
                retCode |= resetUnreadCount(aSection, summaryIdx);
            }
        }
        else {
            Log.w(DBG_CLASS_NAME, "## resetUnreadCounts(): section " + aSection + " was not found in the sections summary list");
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
        else if(null == (session=Matrix.getMXSession(mContext, matrixId)) || (!session.isAlive())) {
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
            // sanity check
            MXDataHandler dataHandler = mMxSession.getDataHandler();
            if((null == dataHandler) || (null == dataHandler.getStore())) {
                Log.w(DBG_CLASS_NAME,"## refreshSummariesList(): unexpected null values - return");
                return;
            }

            // update/retrieve the complete summary list
            ArrayList<RoomSummary> roomSummariesCompleteList = new ArrayList<RoomSummary>(dataHandler.getStore().getSummaries());

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
        if (!mIsDragAndDropMode) {
            refreshSummariesList();
        }
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
        // same for the join by room alias or ID
        if ((mDirectoryGroupPosition == groupPosition) || (mRoomByAliasGroupPosition == groupPosition)) {
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

        if (mIsSearchMode) {
            imageView.setVisibility(View.GONE);
        } else {
            if (!isExpanded) {
                imageView.setImageResource(R.drawable.ic_material_expand_less_black);
            } else {
                imageView.setImageResource(R.drawable.ic_material_expand_more_black);
            }
        }
        return convertView;
    }

    /**
     * Apply a rounded (sides) rectangle as a background to the view provided in aTargetView.
     * @param aTargetView view to apply the background
     * @param aBackgroundColor background colour
     */
    private static void setUnreadBackground(View aTargetView, int aBackgroundColor)
    {
        if(null != aTargetView) {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadius(100);
            shape.setColor(aBackgroundColor);
            aTargetView.setBackground(shape);
        }
    }

    /**
     * Compute the View that should be used to render the child,
     * given its position and its group’s position
     */
    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        // sanity check
        if (null == mSummaryListByGroupPosition){
            return null;
        }
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mChildLayoutResourceId, parent, false);
        }

        if (!mMxSession.isAlive()) {
            return convertView;
        }

        int roomNameBlack = mContext.getResources().getColor(R.color.vector_text_black_color);
        int fushiaColor = mContext.getResources().getColor(R.color.vector_fuchsia_color);
        int vectorDarkGreyColor = mContext.getResources().getColor(R.color.vector_4d_gray);
        int vectorDefaultTimeStampColor = mContext.getResources().getColor(R.color.vector_0_54_black_color);
        int vectorGreenColor = mContext.getResources().getColor(R.color.vector_green_color);
        int vectorSilverColor = mContext.getResources().getColor(R.color.vector_silver_color);


        // retrieve the UI items
        ImageView avatarImageView = (ImageView)convertView.findViewById(R.id.room_avatar_image_view);
        TextView roomNameTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);
        TextView roomMsgTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomMessage);
        View bingUnreadMsgView = convertView.findViewById(R.id.bing_indicator_unread_message);
        TextView timestampTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
        View separatorView = convertView.findViewById(R.id.recents_separator);
        View separatorGroupView = convertView.findViewById(R.id.recents_groups_separator_line);
        final View actionView = convertView.findViewById(R.id.roomSummaryAdapter_action);
        final ImageView actionImageView = (ImageView) convertView.findViewById(R.id.roomSummaryAdapter_action_image);
        TextView unreadCountTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_unread_count);


        View invitationView = convertView.findViewById(R.id.recents_groups_invitation_group);
        Button preViewButton = (Button)convertView.findViewById(R.id.recents_invite_preview_button);
        Button rejectButton = (Button)convertView.findViewById(R.id.recents_invite_reject_button);

        View showMoreView = convertView.findViewById(R.id.roomSummaryAdapter_show_more_layout);
        View actionClickArea = convertView.findViewById(R.id.roomSummaryAdapter_action_click_area);

        // directory management
        if ((mDirectoryGroupPosition == groupPosition) || (mRoomByAliasGroupPosition == groupPosition)) {
            // some items are show
            bingUnreadMsgView.setVisibility(View.INVISIBLE);
            timestampTxtView.setVisibility(View.GONE);
            actionImageView.setVisibility(View.GONE);
            invitationView.setVisibility(View.GONE);
            separatorView.setVisibility(View.GONE);
            separatorGroupView.setVisibility(View.VISIBLE);
            showMoreView.setVisibility(View.VISIBLE);
            actionClickArea.setVisibility(View.GONE);

            if (mDirectoryGroupPosition == groupPosition) {
                if (null == mPublicRooms) {
                    roomNameTxtView.setText(mContext.getResources().getString(R.string.directory_searching_title));
                    roomMsgTxtView.setText("");
                } else {
                    roomNameTxtView.setText(mContext.getResources().getString(R.string.directory_search_results_title));

                    if (TextUtils.isEmpty(mSearchedPattern)) {
                        if (mMatchedPublicRooms.size() > 1) {
                            roomMsgTxtView.setText(mContext.getResources().getString(R.string.directory_search_rooms, mMatchedPublicRooms.size()));
                        } else {
                            roomMsgTxtView.setText(mContext.getResources().getString(R.string.directory_search_room, mMatchedPublicRooms.size()));
                        }
                    } else {
                        if (mMatchedPublicRooms.size() > 1) {
                            roomMsgTxtView.setText(mContext.getResources().getString(R.string.directory_search_rooms_for, mMatchedPublicRooms.size(), mSearchedPattern));
                        } else {
                            roomMsgTxtView.setText(mContext.getResources().getString(R.string.directory_search_room_for, mMatchedPublicRooms.size(), mSearchedPattern));
                        }
                    }
                }

                avatarImageView.setImageBitmap(VectorUtils.getAvatar(avatarImageView.getContext(), VectorUtils.getAvatarcolor(null), null, true));
            } else {
                roomNameTxtView.setText(mSearchedPattern);
                roomMsgTxtView.setText("");
                avatarImageView.setImageBitmap(VectorUtils.getAvatar(avatarImageView.getContext(), VectorUtils.getAvatarcolor(null), "@", true));
            }

            return convertView;
        }

        showMoreView.setVisibility(View.GONE);

        RoomSummary childRoomSummary = mSummaryListByGroupPosition.get(groupPosition).get(childPosition);
        final Room childRoom =  mMxSession.getDataHandler().getStore().getRoom(childRoomSummary.getRoomId());
        int unreadMsgCount = childRoomSummary.getUnreadEventsCount();
        int highlightCount = 0;
        int notificationCount = 0;

        if (null != childRoom) {
            highlightCount = childRoom.getHighlightCount();
            notificationCount = childRoom.getNotificationCount();
        }

        // get last message to be displayed
        CharSequence lastMsgToDisplay = getChildMessageToDisplay(childRoomSummary);

        // display the room avatar
        avatarImageView.setBackgroundColor(mContext.getResources().getColor(android.R.color.transparent));
        final String roomName = VectorUtils.getRoomDisplayname(mContext, mMxSession, childRoom);
        VectorUtils.loadRoomAvatar(mContext, mMxSession, avatarImageView, childRoom);

        // display the room name
        roomNameTxtView.setText(roomName);
        roomNameTxtView.setTextColor(roomNameBlack);
        roomNameTxtView.setTypeface(null, (0 != unreadMsgCount) ? Typeface.BOLD : Typeface.NORMAL);

        // display the last message
        roomMsgTxtView.setText(lastMsgToDisplay);

        // set the timestamp
        timestampTxtView.setText(getFormattedTimestamp(childRoomSummary.getLatestEvent()));
        timestampTxtView.setTextColor(vectorDefaultTimeStampColor);
        timestampTxtView.setTypeface(null, Typeface.NORMAL);

        // set bing view background colour
        int bingUnreadColor;
        if ((0 != highlightCount) || childRoomSummary.isHighlighted()) {
            bingUnreadColor = fushiaColor;
        } else if (0 != notificationCount) {
            bingUnreadColor = vectorGreenColor;
        } else if (0 != unreadMsgCount) {
            bingUnreadColor = vectorSilverColor;
        } else {
            bingUnreadColor = Color.TRANSPARENT;
        }
        bingUnreadMsgView.setBackgroundColor(bingUnreadColor);

        // display the unread badge counter
        if( (0 != notificationCount)) {
            unreadCountTxtView.setVisibility(View.VISIBLE);
            unreadCountTxtView.setText(String.valueOf(notificationCount));
            unreadCountTxtView.setTypeface(null, Typeface.BOLD);
            setUnreadBackground(unreadCountTxtView,bingUnreadColor);
        } else {
            unreadCountTxtView.setVisibility(View.GONE);
        }

        // some items are shown
        boolean isInvited = false;

        if (null != childRoom) {
            isInvited = childRoom.isInvited();
        }

        bingUnreadMsgView.setVisibility(isInvited ? View.INVISIBLE : View.VISIBLE);
        timestampTxtView.setVisibility((isInvited || mIsSearchMode) ? View.INVISIBLE : View.VISIBLE);
        actionImageView.setVisibility((isInvited || mIsSearchMode) ? View.INVISIBLE : View.VISIBLE);
        invitationView.setVisibility(isInvited ? View.VISIBLE : View.GONE);

        final String fRoomId = childRoomSummary.getRoomId();

        if (isInvited) {
            actionClickArea.setVisibility(View.GONE);

            preViewButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mListener) {
                        mListener.onPreviewRoom(mMxSession, fRoomId);
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

            actionClickArea.setVisibility(View.VISIBLE);
            actionClickArea.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    displayPopupMenu(childRoom, actionView, isFavorite, isLowPrior);
                }
            });
        }

        separatorView.setVisibility(isLastChild ? View.GONE : View.VISIBLE);
        separatorGroupView.setVisibility((isLastChild && ((groupPosition + 1) < getGroupCount())) ? View.VISIBLE : View.GONE);

        return convertView;
    }

    /**
     * Display the recents action popup.
     * @param childRoom the room in which the actions should be triggered in.
     * @param actionView the anchor view.
     * @param isFavorite true if it is a favorite room
     * @param isLowPrior true it it is a low priority room
     */
    @SuppressLint("NewApi")
    private void displayPopupMenu(final Room childRoom, final View actionView, final boolean isFavorite, final boolean isLowPrior) {
        // sanity check
        if (null == childRoom) {
            return;
        }

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
        else if((null == (session = Matrix.getMXSession(mContext, aMatrixId))) || (!session.isAlive())) {
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

        if (null != aChildRoomSummary) {
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

                if (TextUtils.equals(mMxSession.getMyUserId(), aChildRoomSummary.getMatrixId())) {
                    messageToDisplayRetValue = mContext.getString(org.matrix.androidsdk.R.string.notice_room_invite_you, inviterUserId);
                } else {
                    messageToDisplayRetValue = mContext.getString(org.matrix.androidsdk.R.string.notice_room_invite, inviterUserId, myName);
                }
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
     * @return the searched pattern
     */
    public String getSearchedPattern() {
        return mSearchedPattern;
    }

    /**
     * Update the public rooms list.
     * null means that there is a pending request.
     * @param publicRoomsList
     */
    public void setPublicRoomsList(List<PublicRoom> publicRoomsList) {
        mPublicRooms = publicRoomsList;
    }

    /**
     * @return true if the adapter is in drag and drop mode.
     */
    public boolean isInDragAndDropMode() {
        return mIsDragAndDropMode;
    }

    /**
     * Set the drag and drop mode i.e. there is no automatic room summaries lists refresh.
     * @param isDragAndDropMode the drag and drop mode
     */
    public void setIsDragAndDropMode(boolean isDragAndDropMode) {
        mIsDragAndDropMode = isDragAndDropMode;
    }

    /**
     * Move a childview in the roomSummary dir tree
     * @param fromGroupPosition the group position origin
     * @param fromChildPosition the child position origin
     * @param toGroupPosition the group position destination
     * @param toChildPosition the child position destination
     */
    public void moveChildView(int fromGroupPosition, int fromChildPosition, int toGroupPosition, int toChildPosition) {
        ArrayList<RoomSummary> fromList = mSummaryListByGroupPosition.get(fromGroupPosition);
        ArrayList<RoomSummary> toList = mSummaryListByGroupPosition.get(toGroupPosition);

        RoomSummary summary = fromList.get(fromChildPosition);
        fromList.remove(fromChildPosition);

        if (toChildPosition >= toList.size()) {
            toList.add(summary);
        } else {
            toList.add(toChildPosition, summary);
        }
    }

    /**
     * Tell if a group position is the invited one.
     * @param groupPos the proup position.
     * @return true if the  group position is the invited one.
     */
    public boolean isInvitedRoomPosition(int groupPos) {
        return mInvitedGroupPosition == groupPos;
    }

    /**
     * Tell if a group position is the favourite one.
     * @param groupPos the proup position.
     * @return true if the  group position is the favourite one.
     */
    public boolean isFavouriteRoomPosition(int groupPos) {
        return mFavouritesGroupPosition == groupPos;
    }

    /**
     * Tell if a group position is the no tag one.
     * @param groupPos the proup position.
     * @return true if the  group position is the no tag one.
     */
    public boolean isNoTagRoomPosition(int groupPos) {
        return mNoTagGroupPosition == groupPos;
    }

    /**
     * Tell if a group position is the low priority one.
     * @param groupPos the proup position.
     * @return true if the  group position is the low priority one.
     */
    public boolean isLowPriorityRoomPosition(int groupPos) {
        return mLowPriorGroupPosition == groupPos;
    }
}
