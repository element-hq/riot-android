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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import im.vector.Matrix;
import im.vector.PublicRoomsManager;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.util.RiotEventDisplay;
import im.vector.util.RoomUtils;
import im.vector.util.ThemeUtils;
import im.vector.util.VectorUtils;

/**
 * An adapter which can display room information.
 */
public class VectorRoomSummaryAdapter extends BaseExpandableListAdapter {
    public interface RoomEventListener {
        void onPreviewRoom(MXSession session, String roomId);

        void onRejectInvitation(MXSession session, String roomId);

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

    // search mode set to true : display nothing if the search pattern is empty
    // search mode set to false : display all the known entries if the search pattern is empty
    private final boolean mIsSearchMode;
    // when set to true, avoid empty history by displaying the directory group
    private final boolean mDisplayDirectoryGroupWhenEmpty;
    // force to display the directory group
    private boolean mForceDirectoryGroupDisplay;

    // public room search
    private Integer mPublicRoomsCount;
    private Integer mMatchedPublicRoomsCount;

    // the listener
    private final RoomUtils.MoreActionListener mMoreActionListener;
    private final RoomEventListener mListener;

    // drag and drop mode
    private boolean mIsDragAndDropMode = false;

    // the direct
    private List<String> mDirectChatRoomIdsList = new ArrayList<>();

    /**
     * Constructor
     *
     * @param aContext                       the context.
     * @param session                        the linked session.
     * @param isSearchMode                   true if the adapter is in search mode
     * @param displayDirectoryGroupWhenEmpty true to avoid empty history
     * @param aChildLayoutResourceId         the room child layout
     * @param aGroupHeaderLayoutResourceId   the room section header layout
     * @param listener                       the events listener
     */
    public VectorRoomSummaryAdapter(Context aContext, MXSession session, boolean isSearchMode,
                                    boolean displayDirectoryGroupWhenEmpty, int aChildLayoutResourceId,
                                    int aGroupHeaderLayoutResourceId, RoomEventListener listener, RoomUtils.MoreActionListener moreActionListener) {
        // init internal fields
        mContext = aContext;
        mLayoutInflater = LayoutInflater.from(mContext);
        mChildLayoutResourceId = aChildLayoutResourceId;
        mHeaderLayoutResourceId = aGroupHeaderLayoutResourceId;
        DBG_CLASS_NAME = getClass().getName();

        // get the complete summary list
        mMxSession = session;
        mListener = listener;
        mMoreActionListener = moreActionListener;

        mIsSearchMode = isSearchMode;
        mDisplayDirectoryGroupWhenEmpty = displayDirectoryGroupWhenEmpty;
    }

    /**
     * Set to true to always display the directory group.
     *
     * @param forceDirectoryGroupDisplay true to always display the directory group.
     */
    public void setForceDirectoryGroupDisplay(boolean forceDirectoryGroupDisplay) {
        mForceDirectoryGroupDisplay = forceDirectoryGroupDisplay;
    }

    /**
     * Provides the formatted timestamp to display.
     * null means that the timestamp text must be hidden.
     *
     * @param event the event.
     * @return the formatted timestamp to display.
     */
    private String getFormattedTimestamp(Event event) {
        String text = AdapterUtils.tsToString(mContext, event.getOriginServerTs(), false);

        // don't display the today before the time
        String today = mContext.getString(R.string.today) + " ";
        if (text.startsWith(today)) {
            text = text.substring(today.length());
        }

        return text;
    }

    /**
     * Compute the name of the group according to its position.
     *
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
        } else if (mNoTagGroupPosition == groupPosition) {
            retValue = mContext.getResources().getString(R.string.room_recents_conversations);
        } else if (mLowPriorGroupPosition == groupPosition) {
            retValue = mContext.getResources().getString(R.string.room_recents_low_priority);
        } else if (mInvitedGroupPosition == groupPosition) {
            retValue = mContext.getResources().getString(R.string.room_recents_invites);
        } else {
            // unknown section
            retValue = "??";
        }

        return retValue;
    }

    /**
     * Fullfill an array list with a pattern.
     *
     * @param list  the list to fill.
     * @param value the pattern.
     * @param count the number of occurences
     */
    private void fillList(ArrayList<RoomSummary> list, RoomSummary value, int count) {
        for (int i = 0; i < count; i++) {
            list.add(value);
        }
    }

    /**
     * Check a room name contains the searched pattern.
     *
     * @param room the room.
     * @return true of the pattern is found.
     */
    private boolean isMatchedPattern(Room room) {
        boolean res = !mIsSearchMode;

        if (!TextUtils.isEmpty(mSearchedPattern)) {
            String roomName = VectorUtils.getRoomDisplayName(mContext, mMxSession, room);
            res = (!TextUtils.isEmpty(roomName) && (roomName.toLowerCase(VectorApp.getApplicationLocale()).contains(mSearchedPattern)));
        }

        return res;
    }

    /**
     * Tell if the group position is the join by
     *
     * @param groupPosition the group position to test.
     * @return true if it is room id group
     */
    public boolean isRoomByIdGroupPosition(int groupPosition) {
        return (mRoomByAliasGroupPosition == groupPosition);
    }

    /**
     * Test if the group position is the directory one.
     *
     * @param groupPosition the group position to test.
     * @return true if it is directory group.
     */
    public boolean isDirectoryGroupPosition(int groupPosition) {
        return (mDirectoryGroupPosition == groupPosition);
    }

    /**
     * @return the directory group position
     */
    public int getDirectoryGroupPosition() {
        return mDirectoryGroupPosition;
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
        if (null != mListener) {
            mListener.onGroupCollapsedNotif(groupPosition);
        }
    }

    @Override
    public void onGroupExpanded(int groupPosition) {
        super.onGroupExpanded(groupPosition);
        if (null != mListener) {
            mListener.onGroupExpandedNotif(groupPosition);
        }
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
     *
     * @param aRoomSummaryCollection the complete list of RoomSummary objects
     * @return an array of summary lists splitted by sections
     */
    private ArrayList<ArrayList<RoomSummary>> buildSummariesByGroups(final Collection<RoomSummary> aRoomSummaryCollection) {
        ArrayList<ArrayList<RoomSummary>> summaryListByGroupsRetValue = new ArrayList<>();
        String roomSummaryId;

        // init index with default values
        mRoomByAliasGroupPosition = -1;
        mDirectoryGroupPosition = -1;
        mInvitedGroupPosition = -1;
        mFavouritesGroupPosition = -1;
        mNoTagGroupPosition = -1;
        mLowPriorGroupPosition = -1;

        if (null != aRoomSummaryCollection) {

            RoomSummary dummyRoomSummary = new RoomSummary();

            // Retrieve lists of room IDs(strings) according to their tags
            final List<String> favouriteRoomIdList = mMxSession.roomIdsWithTag(RoomTag.ROOM_TAG_FAVOURITE);
            final List<String> lowPriorityRoomIdList = mMxSession.roomIdsWithTag(RoomTag.ROOM_TAG_LOW_PRIORITY);
            mDirectChatRoomIdsList = mMxSession.getDirectChatRoomIdsList();

            // ArrayLists allocations: will contain the RoomSummary objects deduced from roomIdsWithTag()
            ArrayList<RoomSummary> inviteRoomSummaryList = new ArrayList<>();
            ArrayList<RoomSummary> favouriteRoomSummaryList = new ArrayList<>(favouriteRoomIdList.size());
            ArrayList<RoomSummary> lowPriorityRoomSummaryList = new ArrayList<>();
            ArrayList<RoomSummary> noTagRoomSummaryList = new ArrayList<>(lowPriorityRoomIdList.size());

            fillList(favouriteRoomSummaryList, dummyRoomSummary, favouriteRoomIdList.size());
            fillList(lowPriorityRoomSummaryList, dummyRoomSummary, lowPriorityRoomIdList.size());

            // Search loop going through all the summaries:
            // here we translate the roomIds (Strings) to their corresponding RoomSummary objects
            for (RoomSummary roomSummary : aRoomSummaryCollection) {
                roomSummaryId = roomSummary.getRoomId();
                Room room = mMxSession.getDataHandler().getStore().getRoom(roomSummaryId);

                // check if the room exists
                // the user conference rooms are not displayed.
                if ((null != room) && isMatchedPattern(room) && !room.isConferenceUserRoom()) {
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
                            lowPriorityRoomSummaryList.set(pos, roomSummary);
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
            if (mIsSearchMode || mDisplayDirectoryGroupWhenEmpty || mForceDirectoryGroupDisplay) {

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
            while (favouriteRoomSummaryList.remove(dummyRoomSummary)) ;
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
            while (lowPriorityRoomSummaryList.remove(dummyRoomSummary)) ;
            if (0 != lowPriorityRoomSummaryList.size()) {
                summaryListByGroupsRetValue.add(lowPriorityRoomSummaryList);
                mLowPriorGroupPosition = groupIndex; // save section index
                groupIndex++;
            }

            // in avoiding empty history mode
            // check if there is really nothing else
            if (mDisplayDirectoryGroupWhenEmpty && !mForceDirectoryGroupDisplay && (groupIndex > 1)) {
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
     *
     * @param aGroupPosition group position
     * @param aChildPosition child position
     * @return the corresponding room summary
     */
    public RoomSummary getRoomSummaryAt(int aGroupPosition, int aChildPosition) {
        return mSummaryListByGroupPosition.get(aGroupPosition).get(aChildPosition);
    }

    /**
     * Reset the count of the unread messages of the room set at this particular child position.
     *
     * @param aGroupPosition group position
     * @param aChildPosition child position
     * @return true if unread count reset was effective, false if unread count was yet reseted
     */
    public boolean resetUnreadCount(int aGroupPosition, int aChildPosition) {
        boolean retCode = false;
        RoomSummary roomSummary = getRoomSummaryAt(aGroupPosition, aChildPosition);

        if (null != roomSummary) {
            Room room = this.roomFromRoomSummary(roomSummary);
            if (null != room) {
                room.sendReadReceipt();
            }
        }

        return retCode;
    }

    /**
     * Retrieve a Room from a room summary
     *
     * @param roomSummary the room roomId to retrieve.
     * @return the Room.
     */
    private Room roomFromRoomSummary(RoomSummary roomSummary) {
        Room roomRetValue;
        MXSession session;
        String matrixId;

        // sanity check
        if ((null == roomSummary) || (null == (matrixId = roomSummary.getMatrixId()))) {
            roomRetValue = null;
        }
        // get session and check if the session is active
        else if (null == (session = Matrix.getMXSession(mContext, matrixId)) || (!session.isAlive())) {
            roomRetValue = null;
        } else {
            roomRetValue = session.getDataHandler().getStore().getRoom(roomSummary.getRoomId());
        }

        return roomRetValue;
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
            if ((null == dataHandler) || (null == dataHandler.getStore())) {
                Log.w(DBG_CLASS_NAME, "## refreshSummariesList(): unexpected null values - return");
                return;
            }

            // update/retrieve the complete summary list
            ArrayList<RoomSummary> roomSummariesCompleteList = new ArrayList<>(dataHandler.getStore().getSummaries());

            // define comparator logic
            Comparator<RoomSummary> summaryComparator = new Comparator<RoomSummary>() {
                public int compare(RoomSummary aLeftObj, RoomSummary aRightObj) {
                    int retValue;
                    long deltaTimestamp;

                    if ((null == aLeftObj) || (null == aLeftObj.getLatestReceivedEvent())) {
                        retValue = 1;
                    } else if ((null == aRightObj) || (null == aRightObj.getLatestReceivedEvent())) {
                        retValue = -1;
                    } else if ((deltaTimestamp = aRightObj.getLatestReceivedEvent().getOriginServerTs() - aLeftObj.getLatestReceivedEvent().getOriginServerTs()) > 0) {
                        retValue = 1;
                    } else if (deltaTimestamp < 0) {
                        retValue = -1;
                    } else {
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

        return mSummaryListByGroupPosition.get(groupPosition).size();
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

        TextView sectionNameTxtView = convertView.findViewById(R.id.heading);

        if (null != sectionNameTxtView) {
            sectionNameTxtView.setText(getGroupTitle(groupPosition));
        }

        ImageView imageView = convertView.findViewById(R.id.heading_image);

        if (mIsSearchMode) {
            imageView.setVisibility(View.GONE);
        } else {
            if (isExpanded) {
                imageView.setImageResource(R.drawable.ic_material_expand_less_black);
            } else {
                imageView.setImageResource(R.drawable.ic_material_expand_more_black);
            }
        }
        return convertView;
    }

    /**
     * Apply a rounded (sides) rectangle as a background to the view provided in aTargetView.
     *
     * @param aTargetView      view to apply the background
     * @param aBackgroundColor background colour
     */
    private static void setUnreadBackground(View aTargetView, int aBackgroundColor) {
        if (null != aTargetView) {
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
        if (null == mSummaryListByGroupPosition) {
            return null;
        }
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mChildLayoutResourceId, parent, false);
        }

        if (!mMxSession.isAlive()) {
            return convertView;
        }

        int roomNameBlack = ThemeUtils.getColor(mContext, R.attr.riot_primary_text_color);
        int fushiaColor = ContextCompat.getColor(mContext, R.color.vector_fuchsia_color);
        int vectorDefaultTimeStampColor = ThemeUtils.getColor(mContext, R.attr.default_text_light_color);
        int vectorGreenColor = ContextCompat.getColor(mContext, R.color.vector_green_color);
        int vectorSilverColor = ContextCompat.getColor(mContext, R.color.vector_silver_color);

        // retrieve the UI items
        ImageView avatarImageView = convertView.findViewById(R.id.room_avatar);
        TextView roomNameTxtView = convertView.findViewById(R.id.roomSummaryAdapter_roomName);
        TextView roomMsgTxtView = convertView.findViewById(R.id.roomSummaryAdapter_roomMessage);
        View bingUnreadMsgView = convertView.findViewById(R.id.bing_indicator_unread_message);
        TextView timestampTxtView = convertView.findViewById(R.id.roomSummaryAdapter_ts);
        View separatorView = convertView.findViewById(R.id.recents_separator);
        View separatorGroupView = convertView.findViewById(R.id.recents_groups_separator_line);
        final View actionView = convertView.findViewById(R.id.roomSummaryAdapter_action);
        final ImageView actionImageView = convertView.findViewById(R.id.roomSummaryAdapter_action_image);
        TextView unreadCountTxtView = convertView.findViewById(R.id.roomSummaryAdapter_unread_count);
        View directChatIcon = convertView.findViewById(R.id.room_avatar_direct_chat_icon);
        View encryptedIcon = convertView.findViewById(R.id.room_avatar_encrypted_icon);

        View invitationView = convertView.findViewById(R.id.recents_groups_invitation_group);
        Button preViewButton = convertView.findViewById(R.id.recents_invite_preview_button);
        Button rejectButton = convertView.findViewById(R.id.recents_invite_reject_button);

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
            unreadCountTxtView.setVisibility(View.GONE);
            directChatIcon.setVisibility(View.GONE);
            encryptedIcon.setVisibility(View.GONE);

            if (mDirectoryGroupPosition == groupPosition) {
                roomNameTxtView.setText(mContext.getResources().getString(R.string.directory_search_results_title));

                if (!TextUtils.isEmpty(mSearchedPattern)) {
                    if (null == mMatchedPublicRoomsCount) {
                        roomMsgTxtView.setText(mContext.getResources().getString(R.string.directory_searching_title));
                    } else if (mMatchedPublicRoomsCount < 2) {
                        roomMsgTxtView.setText(mContext.getResources().getString(R.string.directory_search_room_for, mMatchedPublicRoomsCount, mSearchedPattern));
                    } else {
                        String value = mMatchedPublicRoomsCount.toString();

                        if (mMatchedPublicRoomsCount >= PublicRoomsManager.PUBLIC_ROOMS_LIMIT) {
                            value = "> " + PublicRoomsManager.PUBLIC_ROOMS_LIMIT;
                        }

                        roomMsgTxtView.setText(mContext.getResources().getString(R.string.directory_search_rooms_for, value, mSearchedPattern));
                    }
                } else {
                    if (null == mPublicRoomsCount) {
                        roomMsgTxtView.setText(null);
                    } else if (mPublicRoomsCount > 1) {
                        roomMsgTxtView.setText(mContext.getResources().getString(R.string.directory_search_rooms, mPublicRoomsCount));
                    } else {
                        roomMsgTxtView.setText(mContext.getResources().getString(R.string.directory_search_room, mPublicRoomsCount));
                    }
                }

                avatarImageView.setImageBitmap(VectorUtils.getAvatar(avatarImageView.getContext(), VectorUtils.getAvatarColor(null), null, true));
            } else {
                roomNameTxtView.setText(mSearchedPattern);
                roomMsgTxtView.setText("");
                avatarImageView.setImageBitmap(VectorUtils.getAvatar(avatarImageView.getContext(), VectorUtils.getAvatarColor(null), "@", true));
            }
            return convertView;
        }

        showMoreView.setVisibility(View.GONE);

        RoomSummary childRoomSummary = mSummaryListByGroupPosition.get(groupPosition).get(childPosition);
        final Room childRoom = mMxSession.getDataHandler().getStore().getRoom(childRoomSummary.getRoomId());
        int unreadMsgCount = childRoomSummary.getUnreadEventsCount();
        int highlightCount = 0;
        int notificationCount = 0;

        if (null != childRoom) {
            highlightCount = childRoom.getHighlightCount();
            notificationCount = childRoom.getNotificationCount();

            if (mMxSession.getDataHandler().getBingRulesManager().isRoomMentionOnly(childRoom.getRoomId())) {
                notificationCount = highlightCount;
            }
        }

        // get last message to be displayed
        CharSequence lastMsgToDisplay = getChildMessageToDisplay(childRoomSummary);

        // display the room avatar
        final String roomName = VectorUtils.getRoomDisplayName(mContext, mMxSession, childRoom);
        VectorUtils.loadRoomAvatar(mContext, mMxSession, avatarImageView, childRoom);

        // display the room name
        roomNameTxtView.setText(roomName);
        roomNameTxtView.setTextColor(roomNameBlack);
        roomNameTxtView.setTypeface(null, (0 != unreadMsgCount) ? Typeface.BOLD : Typeface.NORMAL);

        // display the last message
        roomMsgTxtView.setText(lastMsgToDisplay);

        // set the timestamp
        timestampTxtView.setText(getFormattedTimestamp(childRoomSummary.getLatestReceivedEvent()));
        timestampTxtView.setTextColor(vectorDefaultTimeStampColor);
        timestampTxtView.setTypeface(null, Typeface.NORMAL);

        // set bing view background colour
        int bingUnreadColor;
        if (0 != highlightCount) {
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
        if ((0 != notificationCount)) {
            unreadCountTxtView.setVisibility(View.VISIBLE);
            unreadCountTxtView.setText(String.valueOf(notificationCount));
            unreadCountTxtView.setTypeface(null, Typeface.BOLD);
            setUnreadBackground(unreadCountTxtView, bingUnreadColor);
        } else {
            unreadCountTxtView.setVisibility(View.GONE);
        }

        // some items are shown
        boolean isInvited = false;

        if (null != childRoom) {
            isInvited = childRoom.isInvited();
        }

        if (null != childRoom) {
            directChatIcon.setVisibility(mDirectChatRoomIdsList.indexOf(childRoom.getRoomId()) < 0 ? View.GONE : View.VISIBLE);
            encryptedIcon.setVisibility(childRoom.isEncrypted() ? View.VISIBLE : View.GONE);
        } else {
            directChatIcon.setVisibility(View.GONE);
            encryptedIcon.setVisibility(View.GONE);
        }

        bingUnreadMsgView.setVisibility(isInvited ? View.INVISIBLE : View.VISIBLE);
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

            // display an exclamation mark like the webclient
            unreadCountTxtView.setVisibility(View.VISIBLE);
            unreadCountTxtView.setText("!");
            unreadCountTxtView.setTypeface(null, Typeface.BOLD);
            setUnreadBackground(unreadCountTxtView, fushiaColor);
            timestampTxtView.setVisibility(View.GONE);
            actionImageView.setVisibility(View.GONE);
        } else {

            final boolean isFavorite = groupPosition == mFavouritesGroupPosition;
            final boolean isLowPrior = groupPosition == mLowPriorGroupPosition;

            actionClickArea.setVisibility(View.VISIBLE);
            actionClickArea.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    RoomUtils.displayPopupMenu(mContext, mMxSession, childRoom, actionView, isFavorite, isLowPrior, mMoreActionListener);
                }
            });

            timestampTxtView.setVisibility(mIsSearchMode ? View.INVISIBLE : View.VISIBLE);
            actionImageView.setVisibility(mIsSearchMode ? View.INVISIBLE : View.VISIBLE);
        }

        separatorView.setVisibility(isLastChild ? View.GONE : View.VISIBLE);
        separatorGroupView.setVisibility((isLastChild && ((groupPosition + 1) < getGroupCount())) ? View.VISIBLE : View.GONE);

        return convertView;
    }

    /**
     * Get the displayable name of the user whose ID is passed in aUserId.
     *
     * @param aMatrixId matrix ID
     * @param aUserId   user ID
     * @return the user display name
     */
    private String getMemberDisplayNameFromUserId(String aMatrixId, String aUserId) {
        String displayNameRetValue;
        MXSession session;

        if ((null == aMatrixId) || (null == aUserId)) {
            displayNameRetValue = null;
        } else if ((null == (session = Matrix.getMXSession(mContext, aMatrixId))) || (!session.isAlive())) {
            displayNameRetValue = null;
        } else {
            User user = session.getDataHandler().getStore().getUser(aUserId);

            if ((null != user) && !TextUtils.isEmpty(user.displayname)) {
                displayNameRetValue = user.displayname;
            } else {
                displayNameRetValue = aUserId;
            }
        }

        return displayNameRetValue;
    }

    /**
     * Retrieves the text to display for a RoomSummary.
     *
     * @param aChildRoomSummary the roomSummary.
     * @return the text to display.
     */
    private CharSequence getChildMessageToDisplay(RoomSummary aChildRoomSummary) {
        CharSequence messageToDisplayRetValue = null;
        EventDisplay eventDisplay;

        if (null != aChildRoomSummary) {
            if (aChildRoomSummary.getLatestReceivedEvent() != null) {
                eventDisplay = new RiotEventDisplay(mContext, aChildRoomSummary.getLatestReceivedEvent(), aChildRoomSummary.getLatestRoomState());
                eventDisplay.setPrependMessagesWithAuthor(true);
                messageToDisplayRetValue = eventDisplay.getTextualDisplay(ThemeUtils.getColor(mContext, R.attr.riot_primary_text_color));
            }

            // check if this is an invite
            if (aChildRoomSummary.isInvited() && (null != aChildRoomSummary.getInviterUserId())) {
                RoomState latestRoomState = aChildRoomSummary.getLatestRoomState();
                String inviterUserId = aChildRoomSummary.getInviterUserId();
                String myName = aChildRoomSummary.getMatrixId();

                if (null != latestRoomState) {
                    inviterUserId = latestRoomState.getMemberName(inviterUserId);
                    myName = latestRoomState.getMemberName(myName);
                } else {
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
     *
     * @param pattern the new searched pattern
     */
    public void setSearchPattern(String pattern) {
        String trimmedPattern = pattern;

        if (null != pattern) {
            trimmedPattern = pattern.trim().toLowerCase(VectorApp.getApplicationLocale());
            trimmedPattern = TextUtils.getTrimmedLength(trimmedPattern) == 0 ? null : trimmedPattern;
        }

        if (!TextUtils.equals(trimmedPattern, mSearchedPattern)) {

            mSearchedPattern = trimmedPattern;
            mMatchedPublicRoomsCount = null;

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
     * Update the public rooms list count and refresh the display.
     *
     * @param roomsListCount the new public rooms count
     */
    public void setPublicRoomsCount(Integer roomsListCount) {
        if (roomsListCount != mPublicRoomsCount) {
            mPublicRoomsCount = roomsListCount;
            super.notifyDataSetChanged();
        }
    }

    /**
     * @return the matched public rooms count
     */
    public int getMatchedPublicRoomsCount() {
        return (null == mMatchedPublicRoomsCount) ? 0 : mMatchedPublicRoomsCount;
    }

    /**
     * Update the matched public rooms list count and refresh the display.
     *
     * @param roomsListCount the new public rooms count
     */
    public void setMatchedPublicRoomsCount(Integer roomsListCount) {
        if (roomsListCount != mMatchedPublicRoomsCount) {
            mMatchedPublicRoomsCount = roomsListCount;
            super.notifyDataSetChanged();
        }
    }

    /**
     * @return true if the adapter is in drag and drop mode.
     */
    public boolean isInDragAndDropMode() {
        return mIsDragAndDropMode;
    }

    /**
     * Set the drag and drop mode i.e. there is no automatic room summaries lists refresh.
     *
     * @param isDragAndDropMode the drag and drop mode
     */
    public void setIsDragAndDropMode(boolean isDragAndDropMode) {
        mIsDragAndDropMode = isDragAndDropMode;
    }

    /**
     * Move a childview in the roomSummary dir tree
     *
     * @param fromGroupPosition the group position origin
     * @param fromChildPosition the child position origin
     * @param toGroupPosition   the group position destination
     * @param toChildPosition   the child position destination
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
     *
     * @param groupPos the proup position.
     * @return true if the  group position is the invited one.
     */
    public boolean isInvitedRoomPosition(int groupPos) {
        return mInvitedGroupPosition == groupPos;
    }

    /**
     * Tell if a group position is the favourite one.
     *
     * @param groupPos the proup position.
     * @return true if the  group position is the favourite one.
     */
    public boolean isFavouriteRoomPosition(int groupPos) {
        return mFavouritesGroupPosition == groupPos;
    }

    /**
     * Tell if a group position is the no tag one.
     *
     * @param groupPos the proup position.
     * @return true if the  group position is the no tag one.
     */
    public boolean isNoTagRoomPosition(int groupPos) {
        return mNoTagGroupPosition == groupPos;
    }

    /**
     * Tell if a group position is the low priority one.
     *
     * @param groupPos the proup position.
     * @return true if the  group position is the low priority one.
     */
    public boolean isLowPriorityRoomPosition(int groupPos) {
        return mLowPriorGroupPosition == groupPos;
    }
}
