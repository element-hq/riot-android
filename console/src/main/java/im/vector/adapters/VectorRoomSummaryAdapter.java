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
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.EventDisplay;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
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
    private Collection<RoomSummary> mRoomSummariesCompleteList;
    private ArrayList<ArrayList<RoomSummary>> mSummaryListBySections;
    private int mFavouriteSectionIndex = -1;// "Favourites" index
    private int mNoTagSectionIndex = -1;    // "Rooms" index
    private int mLowPrioSectionIndex = -1;  // "Low Priority" index
    private final String DBG_CLASS_NAME;

    /**
     * Recycle view holder class.
     * Used in the child views of the expandable list view.
     */
    private static class SummaryChildViewHolder {
        final ImageView mAvatarImageView;
        final TextView mRoomNameTxtView;
        final TextView mRoomMsgTxtView;
        final TextView mBingUnreadMsgTxtView;
        final TextView mTimestampTxtView;

        SummaryChildViewHolder(View aParentView){
            mAvatarImageView = (ImageView)aParentView.findViewById(R.id.avatar_img_vector);
            mRoomNameTxtView = (TextView) aParentView.findViewById(R.id.roomSummaryAdapter_roomName);
            mRoomMsgTxtView = (TextView) aParentView.findViewById(R.id.roomSummaryAdapter_roomMessage);
            mBingUnreadMsgTxtView = (TextView) aParentView.findViewById(R.id.bing_indicator_unread_message);
            mTimestampTxtView = (TextView) aParentView.findViewById(R.id.roomSummaryAdapter_ts);
        }
    }


    /**
     * Constructor
     * @param aContext activity context
     * @param aSessions accounts list
     * @param aChildLayoutResourceId child resource ID for the BaseExpandableListAdapter
     * @param aGroupHeaderLayoutResourceId group resource ID for the BaseExpandableListAdapter
     */
    public VectorRoomSummaryAdapter(Context aContext, Collection<MXSession> aSessions, int aChildLayoutResourceId, int aGroupHeaderLayoutResourceId)  {
        //super(context, sessions, childLayoutResourceId, groupHeaderLayoutResourceId);

        // init internal fields
        mContext = aContext;
        mLayoutInflater = LayoutInflater.from(mContext);
        mChildLayoutResourceId = aChildLayoutResourceId;
        mHeaderLayoutResourceId = aGroupHeaderLayoutResourceId;
        DBG_CLASS_NAME = getClass().getName();

        // get the complete summary list
        mMxSession = Matrix.getInstance(aContext).getDefaultSession();
        if (null != mMxSession) {
            mRoomSummariesCompleteList = mMxSession.getDataHandler().getStore().getSummaries();
        }

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
        return AdapterUtils.tsToString(mContext, event.getOriginServerTs(), false);
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
        else {
            // unknown section
            retValue = "";
        }

    return retValue;
    }

    /**
     * Build an array of RoomSummary objects organized according to the room tags (sections).
     * So far we have 3 sections for the following tags:
     * - ROOM_TAG_FAVOURITE
     * - ROOM_TAG_LOW_PRIORITY
     * - ROOM_TAG_NO_TAG (displayed as "ROOMS")
     * Section indexes: mFavouriteSectionIndex, mNoTagSectionIndex and mFavouriteSectionIndex are also
     *  computed in this method.
     * @param aRoomSummaryCollection the complete list of RoomSummary objects
     * @return an array of summary lists splitted by sections
     */
    private ArrayList<ArrayList<RoomSummary>> buildSummariesBySections(final Collection<RoomSummary> aRoomSummaryCollection) {
        ArrayList<ArrayList<RoomSummary>> summaryListBySectionsRetValue = new ArrayList<ArrayList<RoomSummary>>();
        RoomSummary roomSummary;
        String roomSummaryId;
        boolean isFound;
        // init index with default values
        mFavouriteSectionIndex = -1;
        mNoTagSectionIndex = -1;
        mLowPrioSectionIndex = -1;

        if(null != aRoomSummaryCollection) {
            // ArrayLists allocations: will contain the RoomSummary objects deduced from roomIdsWithTag()
            ArrayList<RoomSummary> favouriteRoomSummaryList = new ArrayList<RoomSummary>();
            ArrayList<RoomSummary> lowPriorityRoomSummaryList = new ArrayList<RoomSummary>();
            ArrayList<RoomSummary> noTagRoomSummaryList = new ArrayList<RoomSummary>();

            // Retrieve lists of room IDs(strings) according to their tags
            final List<String> favouriteRoomIdList = mMxSession.roomIdsWithTag(RoomTag.ROOM_TAG_FAVOURITE);
            final List<String> lowPrioRoomIdList = mMxSession.roomIdsWithTag(RoomTag.ROOM_TAG_LOW_PRIORITY);
            final List<String> noTagRoomIdList = mMxSession.roomIdsWithTag(RoomTag.ROOM_TAG_NO_TAG);

            // Main search loop going through all the summaries:
            // here we translate the roomIds (Strings) to their corresponding RoomSummary objects
            for(Iterator iterator = aRoomSummaryCollection.iterator(); iterator.hasNext(); ) {
                isFound = false;
                roomSummary = (RoomSummary) iterator.next();
                roomSummaryId = roomSummary.getRoomId();

                // favourite search to build the favourite list
                for (String roomId : favouriteRoomIdList) {
                    if (roomId.equals(roomSummaryId)) {
                        favouriteRoomSummaryList.add(roomSummary);
                        isFound = true;
                        break;
                    }
                }

                // low priority search to build the low priority list
                if (false == isFound) {
                    for (String roomId : lowPrioRoomIdList) {
                        if (roomId.equals(roomSummaryId)) {
                            lowPriorityRoomSummaryList.add(roomSummary);
                            isFound = true;
                            break;
                        }
                    }
                }

                // no tag search to build the no tag list
                if (false == isFound) {
                    for (String roomId : noTagRoomIdList) {
                        if (roomId.equals(roomSummaryId)) {
                            noTagRoomSummaryList.add(roomSummary);
                            break;
                        }
                    }
                }
            }

            // Adding sections
            // Note the order here below: first the "favourite", then "no tag" and then "low priority"

            // First section: add favourite list section
            int groupIndex = 0;
            if(0!=favouriteRoomSummaryList.size()) {
                summaryListBySectionsRetValue.add(favouriteRoomSummaryList);
                mFavouriteSectionIndex = groupIndex; // save section index
                groupIndex++;
            }

            // Second section: add no tag list section
            if(0!=noTagRoomSummaryList.size()) {
                summaryListBySectionsRetValue.add(noTagRoomSummaryList);
                mNoTagSectionIndex = groupIndex; // save section index
                groupIndex++;
            }

            // Third section: add low priority list section
            if(0!=lowPriorityRoomSummaryList.size()) {
                summaryListBySectionsRetValue.add(lowPriorityRoomSummaryList);
                mLowPrioSectionIndex = groupIndex; // save section index
            }
        }

        return summaryListBySectionsRetValue;
    }

    /**
     * Sort the summaries list by date (timestamp).
     *
     */
    public void sortSummaries(){
        if(null != mSummaryListBySections) {

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

            // go through all the sections and apply "sort"
            for(int section = 0; section < mSummaryListBySections.size(); ++section) {
                ArrayList<RoomSummary> summariesList = (ArrayList<RoomSummary>)mSummaryListBySections.get(section);
                Collections.sort(summariesList, summaryComparator);
            }
        }
        else {
            Log.w(DBG_CLASS_NAME, "## sortSummaries(): mSummaryListBySections = null");
        }

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


    /**
     * upidate the event and the state on given room summary.
     * The event provides the room ID identifying the room summary.
     * @param aSectionIndex section index
     * @param aEvent the event to set
     * @param aRoomState
     * @param aIsRefreshed
     * @return true if operation succeed, false otherwise
     */
    public boolean setLatestEvent(int aSectionIndex, Event aEvent, RoomState aRoomState, Boolean aIsRefreshed) {
        boolean retCode = false;
        RoomSummary summary = getSummaryByRoomId(aSectionIndex, aEvent.roomId);

        if(null != summary) {
            retCode = true;
            summary.setLatestEvent(aEvent);
            summary.setLatestRoomState(aRoomState);
            if(aIsRefreshed.booleanValue()) {
                sortSummaries();
                notifyDataSetChanged();
            }
        }

        return retCode;
    }

    private int getUnreadMessageBackgroundColor() {
        return mContext.getResources().getColor(R.color.vector_silver_color);
    }

    private int getNotifiedUnreadMessageBackgroundColor() {
        return mContext.getResources().getColor(R.color.vector_green_color);
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
            mRoomSummariesCompleteList = mMxSession.getDataHandler().getStore().getSummaries();
            // init data model used to be be displayed in the list view
            mSummaryListBySections = buildSummariesBySections(mRoomSummariesCompleteList);
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
        /*
        if(this.isRecentsGroupIndex(groupPosition)) {
            ArrayList index1 = this.mSearchedPattern.length() > 0?this.mFilteredRecentsSummariesList:this.mRecentsSummariesList;
            return null != index1 && index1.size() > groupPosition?((ArrayList)index1.get(groupPosition)).size():0;
        } else {
            int index = groupPosition - this.mPublicsGroupStartIndex;
            return !this.displayPublicRooms()?0:(null == this.mPublicRoomsLists?1:(((List)this.mPublicRoomsLists.get(index)).size() == 0?0:(this.mSearchedPattern.length() > 0?((ArrayList)this.mFilteredPublicRoomsList.get(index)).size():((List)this.mPublicRoomsLists.get(index)).size())));
        }
        */
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
        if(convertView == null) {
            convertView = this.mLayoutInflater.inflate(this.mHeaderLayoutResourceId, (ViewGroup)null);
        }

        TextView sectionNameTxtView = (TextView)convertView.findViewById(org.matrix.androidsdk.R.id.heading);
        ImageView sectionExpanderImageView = (ImageView)convertView.findViewById(org.matrix.androidsdk.R.id.heading_image);

        String roomTitle = getSectionTitle(groupPosition);
        sectionNameTxtView.setText(roomTitle);

        if(isExpanded) {
            sectionExpanderImageView.setImageResource(org.matrix.androidsdk.R.drawable.expander_close_holo_light);
        } else {
            sectionExpanderImageView.setImageResource(org.matrix.androidsdk.R.drawable.expander_open_holo_light);
        }

        /*
        if(this.isRecentsGroupIndex(groupPosition)) {
            int imageView = 0;
            Collection summaries = ((HashMap)this.mSummaryMapsBySection.get(groupPosition)).values();

            RoomSummary summary;
            for(Iterator header = summaries.iterator(); header.hasNext(); imageView += summary.getUnreadEventsCount()) {
                summary = (RoomSummary)header.next();
            }

            String header1 = this.myRoomsTitle(groupPosition);
            if(imageView > 0) {
                header1 = header1 + " (" + imageView + ")";
            }

            heading.setText(header1);
        } else {
            heading.setText(this.publicRoomsTitle(groupPosition));
        }

        heading.setTextColor(this.mSectionTitleColor);

        if(isExpanded) {
            imageView1.setImageResource(org.matrix.androidsdk.R.drawable.expander_close_holo_light);
        } else {
            imageView1.setImageResource(org.matrix.androidsdk.R.drawable.expander_open_holo_light);
        }
        */
        return convertView;
    }
    /**
     * Compute the View that should be used to render the child,
     * given its position and its group’s position
     */
    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        SummaryChildViewHolder viewHolder;

        // sanity check
        if(null == mSummaryListBySections){
            return null;
        }

        RoomSummary childRoomSummary = mSummaryListBySections.get(groupPosition).get(childPosition);
        Room childRoom =  mMxSession.getDataHandler().getStore().getRoom(childRoomSummary.getRoomId());
        int unreadMsgCount = childRoomSummary.getUnreadEventsCount();

        // get last message to be displayed
        CharSequence lastMsgToDisplay = getChildMessageToDisplay(childRoomSummary);

        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mChildLayoutResourceId, parent, false);
            viewHolder = new SummaryChildViewHolder(convertView);
            convertView.setTag(viewHolder);
        }
        else {
            // recylce previously created view..
            viewHolder = (SummaryChildViewHolder)convertView.getTag();
        }

        // update UI: avatar, room name, room last message, timestamp and unread message information
        VectorUtils.setRoomVectorAvatar(viewHolder.mAvatarImageView, childRoom.getRoomId(), childRoom.getName(mMxSession.getMyUser().userId));
        viewHolder.mRoomNameTxtView.setText(childRoomSummary.getRoomName());
        viewHolder.mRoomMsgTxtView.setText(lastMsgToDisplay); // childRoomSummary.getRoomTopic();
        viewHolder.mTimestampTxtView.setText(getFormattedTimestamp(childRoomSummary.getLatestEvent()));
        // TODO remove asap viewHolder.mTimestampTxtView.setVisibility(View.VISIBLE);

        // init colour with its default value
        int newColourToApply = mContext.getResources().getColor(R.color.vector_text_black_color);
        // update colour of the bing zone background and the timestamp text
        if (childRoomSummary.isHighlighted()) {
            newColourToApply = getNotifiedUnreadMessageBackgroundColor();
            viewHolder.mBingUnreadMsgTxtView.setBackgroundColor(newColourToApply);
            viewHolder.mTimestampTxtView.setTextColor(newColourToApply);
        }
        else if (0 != unreadMsgCount) {
            // basic unread messages
            newColourToApply = getUnreadMessageBackgroundColor();
            viewHolder.mBingUnreadMsgTxtView.setBackgroundColor(newColourToApply);
            viewHolder.mTimestampTxtView.setTextColor(newColourToApply);
        }
        else {
            // reset the colours to its default values
            viewHolder.mBingUnreadMsgTxtView.setBackgroundColor(Color.TRANSPARENT);
            viewHolder.mTimestampTxtView.setTextColor(newColourToApply);
        }

/*
        // display a spinner while loading the public rooms
        // detect if the view contains the spinner widget: progressbar_waiting_room_members
        // TODO  why not use convertView.getId(); to check if adapter_item_waiting_room_members is displayed?
        View spinner = null;
        if (null != convertView) {
            spinner = convertView.findViewById(R.id.progressbar_waiting_room_members);
        }

        // assume that some public rooms are defined
        if (isPublicsGroupIndex(groupPosition) && (null == mPublicRoomsLists)) {
            if (null == spinner) {
                // display a spinner layout
                convertView = mLayoutInflater.inflate(R.layout.adapter_item_waiting_room_members, parent, false);
            }
            return convertView;
        }

        // must not reuse the view if it is not the right type
        if (null != spinner) {
            convertView = null;
        }

        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mChildLayoutResourceId, parent, false);
        }

        try {
            // default UI
            // when a room is deleting, the UI is dimmed
            final View deleteProgress = (View) convertView.findViewById(R.id.roomSummaryAdapter_delete_progress);
            deleteProgress.setVisibility(View.GONE);
            convertView.setAlpha(1.0f);

            int textColor = getDefaultTextColor();

            if (isRecentsGroupIndex(groupPosition)) {
                List<RoomSummary> summariesList = (mSearchedPattern.length() > 0) ? mFilteredRecentsSummariesList.get(groupPosition) : mRecentsSummariesList.get(groupPosition);

                // should never happen but in some races conditions, it happened.
                if (0 == summariesList.size()) {
                    return convertView;
                }

                RoomSummary summary = (childPosition < summariesList.size()) ? summariesList.get(childPosition) : summariesList.get(summariesList.size() - 1);
                Integer unreadCount = summary.getUnreadEventsCount();

                CharSequence message = summary.getRoomTopic();
                String timestamp = null;

                // background color
                if (summary.isHighlighted()) {
                    convertView.setBackgroundColor(mHighlightColor);
                    textColor = getHighlightMessageTextColor();
                } else if ((unreadCount == null) || (unreadCount == 0)) {
                    convertView.setBackgroundColor(Color.TRANSPARENT);
                } else {
                    textColor = getUnreadMessageTextColor();
                    convertView.setBackgroundColor(textColor);
                }

                TextView textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);

                RoomState latestRoomState = summary.getLatestRoomState();
                if (null == latestRoomState) {
                    Room room = roomFromRoomSummary(summary);

                    if ((null != room) && (null != room.getLiveState())) {
                        latestRoomState = room.getLiveState().deepCopy();
                        // store it to avoid retrieving it once
                        summary.setLatestRoomState(latestRoomState);
                    }
                }

                // the public rooms are displayed with bold fonts
                if ((null != latestRoomState) && (null != latestRoomState.visibility) && latestRoomState.visibility.equals(RoomState.VISIBILITY_PUBLIC)) {
                    textView.setTypeface(null, Typeface.BOLD);
                } else {
                    textView.setTypeface(null, Typeface.NORMAL);
                }

                textView.setTextColor(textColor);

                // display the unread messages count
                String roomNameMessage = ((latestRoomState != null) && !summary.isInvited()) ? latestRoomState.getDisplayName(summary.getMatrixId()) : summary.getRoomName();

                if (null != roomNameMessage) {
                    if ((null != unreadCount) && (unreadCount > 0) && !summary.isInvited()) {
                        roomNameMessage += " (" + unreadCount + ")";
                    }
                }

                textView.setText(roomNameMessage);

                if (summary.getLatestEvent() != null) {
                    EventDisplay display = new EventDisplay(mContext, summary.getLatestEvent(), latestRoomState);
                    display.setPrependMessagesWithAuthor(true);
                    message = display.getTextualDisplay();
                    timestamp = getFormattedTimestamp(summary.getLatestEvent());
                }

                // check if this is an invite
                if (summary.isInvited() && (null != summary.getInviterUserId())) {
                    String inviterName = summary.getInviterUserId();
                    String myName = summary.getMatrixId();

                    if (null != latestRoomState) {
                        inviterName = latestRoomState.getMemberName(inviterName);
                        myName = latestRoomState.getMemberName(myName);
                    } else {
                        inviterName = memberDisplayName(summary.getMatrixId(), inviterName);
                        myName = memberDisplayName(summary.getMatrixId(), myName);
                    }

                    message = mContext.getString(R.string.notice_room_invite, inviterName, myName);
                }

                textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_message);
                textView.setText(message);
                textView.setTextColor(textColor);
                textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
                textView.setVisibility(View.VISIBLE);
                textView.setText(timestamp);
                textView.setTextColor(textColor);

                Room room = roomFromRoomSummary(summary);

                if ((null != room) && room.isLeaving()) {
                    convertView.setAlpha(0.3f);
                    deleteProgress.setVisibility(View.VISIBLE);
                }
            } else {
                int index = groupPosition - mPublicsGroupStartIndex;
                List<PublicRoom> publicRoomsList = null;

                if (mSearchedPattern.length() > 0) {
                    // add sanity checks
                    // GA issue : could crash while rotating the screen
                    if ((null != mFilteredPublicRoomsList) && (index < mFilteredPublicRoomsList.size())) {
                        publicRoomsList = mFilteredPublicRoomsList.get(index);
                    }
                } else {
                    // add sanity checks
                    // GA issue : could crash while rotating the screen
                    if ((null != mPublicRoomsLists) && (index < mPublicRoomsLists.size())) {
                        publicRoomsList = mPublicRoomsLists.get(index);
                    }
                }

                // sanity checks failed.
                if (null == publicRoomsList) {
                    TextView textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);
                    textView.setTypeface(null, Typeface.BOLD);
                    textView.setTextColor(textColor);
                    textView.setText("");

                    textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_message);
                    textView.setTextColor(textColor);
                    textView.setText("");

                    textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
                    textView.setTextColor(textColor);
                    textView.setVisibility(View.VISIBLE);
                    textView.setText("");

                    convertView.setBackgroundColor(0);
                } else {
                    PublicRoom publicRoom = publicRoomsList.get(childPosition);

                    String matrixId = null;

                    if ((mRecentsSummariesList.size() > 0) && (mRecentsSummariesList.get(0).size() > 0)) {
                        matrixId = mRecentsSummariesList.get(0).get(0).getMatrixId();
                    }

                    String displayName = publicRoom.getDisplayName(matrixId);

                    TextView textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);
                    textView.setTypeface(null, Typeface.BOLD);
                    textView.setTextColor(textColor);
                    textView.setText(displayName);

                    textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_message);
                    textView.setText(publicRoom.topic);
                    textView.setTextColor(textColor);

                    textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
                    textView.setVisibility(View.VISIBLE);
                    textView.setTextColor(textColor);

                    if (publicRoom.numJoinedMembers > 1) {
                        textView.setText(publicRoom.numJoinedMembers + " " + mContext.getString(R.string.users));
                    } else {
                        textView.setText(publicRoom.numJoinedMembers + " " + mContext.getString(R.string.user));
                    }

                    String alias = publicRoom.getAlias();

                    if ((null != alias) && (mHighLightedRooms.indexOf(alias) >= 0)) {
                        convertView.setBackgroundColor(mPublicHighlightColor);
                    } else {
                        convertView.setBackgroundColor(0);
                    }
                }
            }
        } catch (Exception e) {
            // prefer having a weird UI instead of a crash
        }
*/
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
                messageToDisplayRetValue = eventDisplay.getTextualDisplay();
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
    public void setPublicRoomsList(List<List<PublicRoom>> aRoomsListList, List<String> aHomeServerNamesList) {
        Log.w(DBG_CLASS_NAME,"## setPublicRoomsList(): NOT IMPLEMENTED");
    }

    public void addRoomSummary(int aSection, RoomSummary aRoomSummary) {
        Log.w(DBG_CLASS_NAME,"## addRoomSummary(): NOT IMPLEMENTED");
    }

    public void setDisplayAllGroups(boolean displayAllGroups) {
        Log.w(DBG_CLASS_NAME,"## setDisplayAllGroups(): NOT IMPLEMENTED");
    }
}
