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
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.User;


import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.contacts.Contact;
import im.vector.contacts.ContactsManager;
import im.vector.util.UIUtils;
import im.vector.util.VectorUtils;

public class VectorAddParticipantsAdapter extends ArrayAdapter<ParticipantAdapterItem> {
    public interface OnParticipantsListener {
        /**
         * The user taps on the dedicated "Remove" button
         * @param participant the participant to remove
         */
        void onRemoveClick(final ParticipantAdapterItem participant);

        /**
         * The user taps on "Leave" button
         */
        void onLeaveClick();

        /**
         * The user selects / deselects a member.
         * @param userId
         */
        void onSelectUserId(String userId);
    }

    //
    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private MXMediasCache mMediasCache;

    private View mSwipingCellView = null;

    private MXSession mSession;
    private String mRoomId;
    private Room mRoom;
    private int mLayoutResourceId;

    private boolean mIsMultiSelectionMode;
    private ArrayList<String> mSelectedUserIds = new ArrayList<String>();

    private ArrayList<ParticipantAdapterItem> mCreationParticipantsList = new ArrayList<ParticipantAdapterItem>();

    ArrayList<ParticipantAdapterItem> mUnusedParticipants = null;
    String mPattern = "";

    OnParticipantsListener mOnParticipantsListener = null;

    /**
     * Create a room member adapter.
     * If a room id is defined, the adapter is in edition mode : the user can add / remove dynamically members or leave the room.
     * If there is none, the room is in creation mode : the user can add/remove members to create a new room.
     * @param context the context.
     * @param layoutResourceId the layout.
     * @param session the session.
     * @param roomId the room id.
     * @param mediasCache the medias cache.
     */
    public VectorAddParticipantsAdapter(Context context, int layoutResourceId, MXSession session, String roomId, boolean multiSelectionMode, MXMediasCache mediasCache) {
        super(context, layoutResourceId);

        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
        mLayoutResourceId = layoutResourceId;
        mMediasCache = mediasCache;
        mSession = session;

        // retrieve the room
        if (null != roomId) {
            mRoomId = roomId;
            mRoom = mSession.getDataHandler().getRoom(roomId);
        }

        if (null == mRoom) {
            MyUser myUser = mSession.getMyUser();

            ParticipantAdapterItem item = new ParticipantAdapterItem(myUser.displayname, myUser.getAvatarUrl(), myUser.userId);
            this.add(item);
            mCreationParticipantsList.add(item);
        }

        // display check box to select multiple items
        mIsMultiSelectionMode = multiSelectionMode;
    }

    /**
     *  search management
     */
    public void setSearchedPattern(String pattern) {
        if (null == pattern) {
            pattern = "";
        }

        if (!pattern.trim().equals(mPattern)) {
            mPattern = pattern.trim().toLowerCase();
            refresh();
        }
    }

    /**
     * @return the searched pattern.
     */
    public String getSearchedPattern() {
        return mPattern;
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
            this.add(participant);
        }
    }

    /**
     * Remove a participant from the edition list.
     * @param participant the participant to remove.
     */
    public void removeParticipant(ParticipantAdapterItem participant) {
        if (null == mRoom) {
            mCreationParticipantsList.remove(participant);
            mUnusedParticipants.add(participant);
            this.remove(participant);
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

    /**
     * Refresh the un-invited members
     */
    public void listOtherMembers() {
        // refresh only when performing a search
        if (TextUtils.isEmpty(mPattern)) {
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

        // check from any other known users
        // because theirs presence have been received
        Collection<User> users = mSession.getDataHandler().getStore().getUsers();
        for(User user : users) {
            // accepted User ID or still active users
            if (idsToIgnore.indexOf(user.userId) < 0) {
                unusedParticipants.add(new ParticipantAdapterItem(user.userId, null, user.userId));
                idsToIgnore.add(user.userId);
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
     * refresh the list
     */
    public void refresh() {
        this.setNotifyOnChange(false);
        this.clear();
        ArrayList<ParticipantAdapterItem> nextMembersList = new ArrayList<ParticipantAdapterItem>();

        if (TextUtils.isEmpty(mPattern)) {
            // retrieve the room members
            if (null != mRoom) {
                ArrayList<ParticipantAdapterItem> admins = new ArrayList<ParticipantAdapterItem>();
                ArrayList<ParticipantAdapterItem> otherMembers = new ArrayList<ParticipantAdapterItem>();

                Collection<RoomMember> activeMembers = mRoom.getActiveMembers();
                String myUserId = mSession.getMyUser().userId;
                PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

                for (RoomMember member : activeMembers) {
                    // oneself member is displayed at top
                    if (member.getUserId().equals(myUserId)) {
                        nextMembersList.add(new ParticipantAdapterItem(member));
                    } else {
                        if (powerLevels.getUserPowerLevel(member.getUserId()) == 100) {
                            admins.add(new ParticipantAdapterItem(member));
                        } else {
                            otherMembers.add(new ParticipantAdapterItem(member));
                        }
                    }
                }

                Collections.sort(admins, ParticipantAdapterItem.alphaComparator);
                nextMembersList.addAll(admins);

                Collections.sort(otherMembers, ParticipantAdapterItem.alphaComparator);
                nextMembersList.addAll(otherMembers);
                mUnusedParticipants = null;
            } else {
                nextMembersList = mCreationParticipantsList;
            }
        } else {
            if (null == mUnusedParticipants) {
                listOtherMembers();
            }

            // remove trailing spaces.
            String pattern = mPattern.trim().toLowerCase();

            // check if each member matches the pattern
            for(ParticipantAdapterItem item: mUnusedParticipants) {
                if (item.matchWithPattern(pattern)) {
                    nextMembersList.add(item);
                }
            }

            Collections.sort(nextMembersList, ParticipantAdapterItem.alphaComparator);
        }

        this.setNotifyOnChange(true);
        this.addAll(nextMembersList);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        final ParticipantAdapterItem participant = getItem(position);
        boolean isSearchMode = !TextUtils.isEmpty(mPattern);

        ImageView thumbView = (ImageView) convertView.findViewById(R.id.filtered_list_avatar);

        VectorUtils.setMemberAvatar(thumbView, participant.mUserId, participant.mDisplayName);

        if (null != participant.mAvatarBitmap) {
            thumbView.setImageBitmap(participant.mAvatarBitmap);
        } else {
            int size = getContext().getResources().getDimensionPixelSize(org.matrix.androidsdk.R.dimen.chat_avatar_size);
            mMediasCache.loadAvatarThumbnail(mSession.getHomeserverConfig(), thumbView, participant.mAvatarUrl, size);
        }

        PowerLevels powerLevels = null;

        if (null != mRoom) {
            powerLevels = mRoom.getLiveState().getPowerLevels();
        }

        TextView nameTextView = (TextView) convertView.findViewById(R.id.filtered_list_name);
        String text = ((0 == position) && !isSearchMode) ? (String)mContext.getText(R.string.you) : participant.mDisplayName;

        if (!isSearchMode && (null != powerLevels)) {
            // show the admin
            if (100 == powerLevels.getUserPowerLevel(participant.mUserId)) {
                text = mContext.getString(R.string.room_participants_admin_name, text);
            }
        }
        nameTextView.setText(text);

        TextView statusTextView = (TextView) convertView.findViewById(R.id.filtered_list_status);
        String status = "";

        if ((null != participant.mRoomMember) && (null != participant.mRoomMember.membership) && !TextUtils.equals(participant.mRoomMember.membership, RoomMember.MEMBERSHIP_JOIN)) {
            if (TextUtils.equals(participant.mRoomMember.membership, RoomMember.MEMBERSHIP_INVITE)) {
                status = mContext.getString(R.string.room_participants_invite);
            } else if (TextUtils.equals(participant.mRoomMember.membership, RoomMember.MEMBERSHIP_LEAVE)) {
                status = mContext.getString(R.string.room_participants_leave);
            } else if (TextUtils.equals(participant.mRoomMember.membership, RoomMember.MEMBERSHIP_BAN)) {
                status = mContext.getString(R.string.room_participants_ban);
            }
        } else if (null != participant.mUserId) {
            User user = null;

            // retrieve the linked user
            ArrayList<MXSession> sessions = Matrix.getMXSessions(mContext);

            for(MXSession session : sessions) {

                if (null == user) {
                    user = session.getDataHandler().getUser(participant.mUserId);
                }
            }

            // find a related user
            if (null != user) {
                if (TextUtils.equals(user.presence, User.PRESENCE_ONLINE)) {
                    status = mContext.getString(R.string.room_participants_active);
                } else {
                    Long lastActiveMs = user.lastActiveAgo;

                    if ((null != lastActiveMs) &&  (-1 != lastActiveMs)) {
                        Long lastActivehour = lastActiveMs / 1000 / 60 / 60;
                        Long lastActiveDays = lastActivehour / 24;

                        if (lastActivehour < 1) {
                            status = mContext.getString(R.string.room_participants_active_less_1_hour);
                        }
                        else if (lastActivehour < 24) {
                            status = mContext.getString(R.string.room_participants_active_less_x_hours, lastActivehour);
                        }
                        else {
                            status = mContext.getString(R.string.room_participants_active_less_x_days, lastActiveDays);
                         }
                    }
                }
            }
        }

        statusTextView.setText(status);

        //
        final int fpos = position;

        View deleteActionsView = convertView.findViewById(R.id.filtered_list_delete_action);
        deleteActionsView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mOnParticipantsListener) {
                    try {
                        if (0 == fpos) {
                            mOnParticipantsListener.onLeaveClick();
                        } else {
                            // assume that the tap on remove
                            mOnParticipantsListener.onRemoveClick(participant);
                        }
                    } catch (Exception e) {
                    }
                }
            }
        });

        // manage the swipe to display actions
        final RelativeLayout cellLayout = (RelativeLayout) convertView.findViewById(R.id.filtered_list_cell);

        if (null != mSwipingCellView) {
            mSwipingCellView.setTranslationX(0);
            mSwipingCellView = null;
        }

        // cancel any translation
        cellLayout.setTranslationX(0);

        Boolean hideDisplayActionsMenu = false;

        // during a room creation, there is no dedicated power level
        if (null != powerLevels) {
            int myPowerLevel;
            int userPowerLevel;
            int kickPowerLevel;

            myPowerLevel = powerLevels.getUserPowerLevel(mSession.getCredentials().userId);
            userPowerLevel = powerLevels.getUserPowerLevel(participant.mUserId);
            kickPowerLevel = powerLevels.kick;

            hideDisplayActionsMenu = ((0 != position) && (myPowerLevel < userPowerLevel) && (myPowerLevel < kickPowerLevel));
        } else {
            hideDisplayActionsMenu = (null == mRoom) && (0 == position);
        }

        // the swipe should be enabled when there is no search and the user can kick other members
        if (isSearchMode || hideDisplayActionsMenu) {
            cellLayout.setOnTouchListener(null);
        } else {
            final View hiddenView = convertView.findViewById(R.id.filtered_list_actions);
            cellLayout.setOnTouchListener(new View.OnTouchListener() {
                private float mStartX = 0;

                @Override
                public boolean onTouch(final View v, MotionEvent event) {
                    final int hiddenViewWidth = hiddenView.getWidth();

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN: {

                            // cancel hidden view display
                            if (null != mSwipingCellView) {
                                mSwipingCellView.setTranslationX(0);
                                mSwipingCellView = null;
                                return false;
                            }

                            mSwipingCellView = cellLayout;
                            mStartX = event.getX();
                            break;
                        }
                        case MotionEvent.ACTION_MOVE: {
                            float x = event.getX() + v.getTranslationX();
                            float deltaX = Math.max(Math.min(x - mStartX, 0), -hiddenViewWidth);
                            cellLayout.setTranslationX(deltaX);
                        }
                        break;
                        case MotionEvent.ACTION_CANCEL:
                        case MotionEvent.ACTION_UP: {
                            float x = event.getX() + v.getTranslationX();
                            float aa = hiddenViewWidth;
                            float deltaX = -Math.max(Math.min(x - mStartX, 0), -hiddenViewWidth);

                            if (deltaX > (hiddenViewWidth / 2)) {
                                cellLayout.setTranslationX(-hiddenViewWidth);
                            } else {
                                cellLayout.setTranslationX(0);
                                mSwipingCellView = null;
                            }

                            break;
                        }

                        default:
                            return false;
                    }
                    return true;
                }
            });
        }

        final CheckBox checkBox = (CheckBox)convertView.findViewById(R.id.filtered_list_checkbox);

        int backgroundColor = mContext.getResources().getColor(android.R.color.white);

        // multi selections mode
        // do not display a checkbox for oneself
        if (mIsMultiSelectionMode && !TextUtils.equals(mSession.getMyUser().userId, participant.mUserId)) {
            checkBox.setVisibility(View.VISIBLE);

            checkBox.setChecked(mSelectedUserIds.indexOf(participant.mUserId) >= 0);

            if (checkBox.isChecked()) {
                backgroundColor = mContext.getResources().getColor(R.color.vector_05_gray);
            }

            checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (checkBox.isChecked()) {
                        mSelectedUserIds.add(participant.mUserId);
                        cellLayout.setBackgroundColor(mContext.getResources().getColor(R.color.vector_05_gray));
                    } else {
                        mSelectedUserIds.remove(participant.mUserId);
                        cellLayout.setBackgroundColor(mContext.getResources().getColor(android.R.color.white));
                    }

                    if (null != mOnParticipantsListener) {
                        mOnParticipantsListener.onSelectUserId(participant.mUserId);
                    }
                }
            });
        } else {
            checkBox.setVisibility(View.GONE);
        }

        cellLayout.setBackgroundColor(backgroundColor);

        return convertView;
    }

    /**
     * @return the participant User Ids except oneself.
     */
    public ArrayList<String> getUserIdsList() {
        ArrayList<String> idsList = new ArrayList<String>();

        // the first item is always oneself
        for(int index = 1; index < getCount(); index++) {
            ParticipantAdapterItem item = getItem(index);

            // sanity check
            if (null != item.mUserId) {
                idsList.add(item.mUserId);
            }
        }

        return idsList;
    }
}
