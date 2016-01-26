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

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.EventDisplay;

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
    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final int mChildLayoutResourceId;
    private final int mHeaderLayoutResourceId;

    private final MXSession mMxSession;
    private ArrayList<ArrayList<RoomSummary>> mSummaryListBySections;

    private int mInvitedSectionIndex = -1;  // "Invited" index
    private int mFavouriteSectionIndex = -1;// "Favourites" index
    private int mNoTagSectionIndex = -1;    // "Rooms" index
    private int mLowPrioSectionIndex = -1;  // "Low Priority" index
    private final String DBG_CLASS_NAME;

    /**
     * Constructor
     * @param aContext activity context
     * @param aChildLayoutResourceId child resource ID for the BaseExpandableListAdapter
     * @param aGroupHeaderLayoutResourceId group resource ID for the BaseExpandableListAdapter
     */
    public VectorRoomSummaryAdapter(Context aContext, int aChildLayoutResourceId, int aGroupHeaderLayoutResourceId)  {
        //super(context, sessions, childLayoutResourceId, groupHeaderLayoutResourceId);

        // init internal fields
        mContext = aContext;
        mLayoutInflater = LayoutInflater.from(mContext);
        mChildLayoutResourceId = aChildLayoutResourceId;
        mHeaderLayoutResourceId = aGroupHeaderLayoutResourceId;
        DBG_CLASS_NAME = getClass().getName();

        // get the complete summary list
        mMxSession = Matrix.getInstance(aContext).getDefaultSession();

        // init data model used to be be displayed in the list view
        notifyDataSetChanged();
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
     * Compute the name of the section according to its index.
     * @param aSectionIndex index of the section
     * @return section title corresponding to the index
     */
    private String getSectionTitle(int aSectionIndex) {
        String retValue;

        if (mFavouriteSectionIndex == aSectionIndex) {
            retValue = mContext.getResources().getString(R.string.room_recents_favourites);
        }
        else if (mNoTagSectionIndex == aSectionIndex) {
            retValue = mContext.getResources().getString(R.string.room_recents_conversations);
        }
        else if (mLowPrioSectionIndex == aSectionIndex) {
            retValue = mContext.getResources().getString(R.string.room_recents_low_priority);
        }
        else if (mInvitedSectionIndex == aSectionIndex) {
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
    private ArrayList<ArrayList<RoomSummary>> buildSummariesBySections(final Collection<RoomSummary> aRoomSummaryCollection) {
        ArrayList<ArrayList<RoomSummary>> summaryListBySectionsRetValue = new ArrayList<ArrayList<RoomSummary>>();
        String roomSummaryId;

        // init index with default values
        mInvitedSectionIndex = -1;
        mFavouriteSectionIndex = -1;
        mNoTagSectionIndex = -1;
        mLowPrioSectionIndex = -1;


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
                if (null != room) {
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
            // Note the order here below: first the "favourite", then "no tag" and then "low priority"
            // First section: add favourite list section
            int groupIndex = 0;

            if (0 != inviteRoomSummaryList.size()) {
                summaryListBySectionsRetValue.add(inviteRoomSummaryList);
                mInvitedSectionIndex = groupIndex;
                groupIndex++;
            }

            // remove unknown room summary
            while(favouriteRoomSummaryList.remove(dummyRoomSummary));
            if (0 != favouriteRoomSummaryList.size()) {
                summaryListBySectionsRetValue.add(favouriteRoomSummaryList);
                mFavouriteSectionIndex = groupIndex; // save section index
                groupIndex++;
            }

            // Second section: add no tag list section
            if (0 != noTagRoomSummaryList.size()) {
                summaryListBySectionsRetValue.add(noTagRoomSummaryList);
                mNoTagSectionIndex = groupIndex; // save section index
                groupIndex++;
            }

            // Third section: add low priority list section
            while(lowPriorityRoomSummaryList.remove(dummyRoomSummary));
            if (0 != lowPriorityRoomSummaryList.size()) {
                summaryListBySectionsRetValue.add(lowPriorityRoomSummaryList);
                mLowPrioSectionIndex = groupIndex; // save section index
            }
        }

        return summaryListBySectionsRetValue;
    }

    public RoomSummary getRoomSummaryAt(int aGroupPosition, int aChildPosition) {
        RoomSummary roomSummaryRetValue = mSummaryListBySections.get(aGroupPosition).get(aChildPosition);

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

        ArrayList<RoomSummary> summariesList = (ArrayList<RoomSummary>)mSummaryListBySections.get(aSection);
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

    public RoomSummary getSummaryByRoomId(int aSectionIndex, String aRoomId) {
        RoomSummary roomSummaryRetValue = null;
        String roomIdStr;

        if(null != mSummaryListBySections) {
            ArrayList<RoomSummary> summariesList = (ArrayList<RoomSummary>) mSummaryListBySections.get(aSectionIndex);
            if (null != summariesList) {
                for (int summaryIdx = 0; summaryIdx < summariesList.size(); summaryIdx++) {
                    roomIdStr = ((RoomSummary) summariesList.get(summaryIdx)).getRoomId();
                    if (aRoomId.equals(roomIdStr)) {
                        roomSummaryRetValue = (RoomSummary) summariesList.get(summaryIdx);
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

    @Override
    public void notifyDataSetChanged() {
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
            mSummaryListBySections = buildSummariesBySections(roomSummariesCompleteList);
        }

        super.notifyDataSetChanged();
    }

    @Override
    public int getGroupCount() {
        return mSummaryListBySections.size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return null;
    }

    @Override
    public long getGroupId(int groupPosition) {
        return 0L;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        int countRetValue = mSummaryListBySections.get(groupPosition).size();
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
            sectionNameTxtView.setText(getSectionTitle(groupPosition));
        }

        ImageView imageView = (ImageView) convertView.findViewById(org.matrix.androidsdk.R.id.heading_image);

        if (!isExpanded) {
            imageView.setImageResource(org.matrix.androidsdk.R.drawable.expander_close_holo_light);
        } else {
            imageView.setImageResource(org.matrix.androidsdk.R.drawable.expander_open_holo_light);
        }

        return convertView;
    }
    /**
     * Compute the View that should be used to render the child,
     * given its position and its group’s position
     */
    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        // sanity check
        if (null == mSummaryListBySections){
            return null;
        }

        int vectorGreenColor = mContext.getResources().getColor(R.color.vector_green_color);
        int vectorSilverColor = mContext.getResources().getColor(R.color.vector_silver_color);


        RoomSummary childRoomSummary = mSummaryListBySections.get(groupPosition).get(childPosition);
        Room childRoom =  mMxSession.getDataHandler().getStore().getRoom(childRoomSummary.getRoomId());
        int unreadMsgCount = childRoomSummary.getUnreadEventsCount();

        // get last message to be displayed
        CharSequence lastMsgToDisplay = getChildMessageToDisplay(childRoomSummary);

        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mChildLayoutResourceId, parent, false);
        }

        // retrieve the UI items
        ImageView avatarImageView = (ImageView)convertView.findViewById(R.id.avatar_img_vector);
        TextView roomNameTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);
        TextView roomMsgTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomMessage);
        View bingUnreadMsgView = convertView.findViewById(R.id.bing_indicator_unread_message);
        TextView timestampTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
        View separatorView = convertView.findViewById(R.id.recents_separator);

        // display the room avatar
        String roomName = VectorUtils.getRoomDisplayname(mContext, mMxSession, childRoom);
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
        timestampTxtView.setTextColor(childRoomSummary.isHighlighted() ? vectorGreenColor : vectorSilverColor);

        // bing view
        bingUnreadMsgView.setBackgroundColor(childRoomSummary.isHighlighted() ? vectorGreenColor : ((0 != unreadMsgCount) ? vectorSilverColor : Color.TRANSPARENT));

        separatorView.setVisibility(isLastChild ? View.GONE : View.VISIBLE);

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

        if((null==aMatrixId) || (null==aUserId)){
            displayNameRetValue = null;
        }
        else if((null == (session=Matrix.getMXSession(mContext, aMatrixId))) || (!session.isActive())) {
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


    public boolean removeRoomSummary(int aSectionIndex, RoomSummary aRoomSummaryToRemove){
        boolean retCode = mSummaryListBySections.get(aSectionIndex).remove(aRoomSummaryToRemove);
        return retCode;
    }

    // ****************************************************************************************
    // stubbed methods:
    // TODO remove asap
    public void addRoomSummary(int aSection, RoomSummary aRoomSummary) {
        Log.w(DBG_CLASS_NAME,"## addRoomSummary(): NOT IMPLEMENTED");
    }

    public void setDisplayAllGroups(boolean displayAllGroups) {
        Log.w(DBG_CLASS_NAME,"## setDisplayAllGroups(): NOT IMPLEMENTED");
    }
}
