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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
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

import im.vector.Matrix;
import im.vector.R;
import im.vector.contacts.Contact;
import im.vector.contacts.ContactsManager;
import im.vector.util.UIUtils;

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
    }

    //
    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private MXMediasCache mMediasCache;

    private MXSession mSession;
    private String mRoomId;
    private Room mRoom;
    private int mLayoutResourceId;
    private Boolean mIsEditionMode;
    private ArrayList<ParticipantAdapterItem> mCreationParticipantsList = new ArrayList<ParticipantAdapterItem>();

    ArrayList<ParticipantAdapterItem> mUnusedParticipants = null;
    String mPattern = "";

    OnParticipantsListener mOnParticipantsListener = null;

    public VectorAddParticipantsAdapter(Context context, int layoutResourceId, MXSession session, Boolean isEditionMode, String roomId, MXMediasCache mediasCache) {
        super(context, layoutResourceId);

        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
        mLayoutResourceId = layoutResourceId;
        mMediasCache = mediasCache;
        mSession = session;
        mIsEditionMode = isEditionMode;

        if (mIsEditionMode) {
            mRoomId = roomId;
            mRoom = mSession.getDataHandler().getRoom(roomId);
        } else {
            MyUser myUser = mSession.getMyUser();

            ParticipantAdapterItem item = new ParticipantAdapterItem(myUser.displayname, myUser.avatarUrl, myUser.userId);
            this.add(item);
            mCreationParticipantsList.add(item);
        }
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
    public void addParticipantAdapterItem(ParticipantAdapterItem participant) {
        if (!mIsEditionMode) {
            mUnusedParticipants.remove(participant);
            mCreationParticipantsList.add(participant);
            this.add(participant);
        }
    }

    /**
     * Remove a participant from the edition list.
     * @param index the participant index to remove.
     */
    public void removeMemberAt(int index) {
        if (!mIsEditionMode) {
            // the index 0 is oneself
            if ((0 != index) && (index < mCreationParticipantsList.size())) {
                ParticipantAdapterItem member = mCreationParticipantsList.get(index);
                mCreationParticipantsList.remove(member);
                mUnusedParticipants.add(member);
                this.remove(member);
            }
        }
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
                idsToIgnore.add(member.getUserId());
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
                        unusedParticipants.add(new ParticipantAdapterItem(member));
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
            if (mIsEditionMode) {
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

        ImageView thumbView = (ImageView) convertView.findViewById(R.id.avatar_img);
        thumbView.setImageResource(org.matrix.androidsdk.R.drawable.ic_contact_picture_holo_light);

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

        return convertView;
    }
}
