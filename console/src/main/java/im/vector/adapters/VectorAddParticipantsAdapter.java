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
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.db.MXMediasCache;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import im.vector.R;

public class VectorAddParticipantsAdapter extends ArrayAdapter<RoomMember> {

    public interface OnParticipantsListener {
        /**
         * The user taps on the dedicated "Remove" button
         * @param roomMember the room member to remove
         */
        void onRemoveClick(final RoomMember roomMember);

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
    private ArrayList<RoomMember> mCreationMembersList = new ArrayList<RoomMember>();

    ArrayList<RoomMember> mOtherRoomsMembers = null;
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
            // in create mode, the oneself member is displayed at top
            RoomMember oneselfMember = new RoomMember();
            oneselfMember.displayname = mSession.getMyUser().displayname;
            oneselfMember.avatarUrl = mSession.getMyUser().avatarUrl;
            this.add(oneselfMember);
            mCreationMembersList.add(oneselfMember);
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
     * Add a room member to the edition list.
     * @param roomMember the room member to add.
     */
    public void addRoomMember(RoomMember roomMember) {
        if (!mIsEditionMode) {
            mCreationMembersList.add(roomMember);
            this.add(roomMember);
        }
    }

    /**
     * Remove a room member from the edition list.
     * @param index the room member index to remove.
     */
    public void removeMemberAt(int index) {
        if (!mIsEditionMode) {
            // the index 0 is oneself
            if ((0 != index) && (index < mCreationMembersList.size())) {
                RoomMember member = mCreationMembersList.get(index);
                mCreationMembersList.remove(member);
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

        ArrayList<RoomMember> otherMembers = new ArrayList<RoomMember>();
        IMXStore store = mSession.getDataHandler().getStore();

        // list the used members IDs
        ArrayList<String> idsToIgnore = new ArrayList<String>();
        Room fromRoom = store.getRoom(mRoomId);
        Collection<RoomMember> currentMembers = fromRoom.getMembers();

        for(RoomMember member : currentMembers) {
            idsToIgnore.add(member.getUserId());
        }

        // checks for each room
        Collection<RoomSummary> summaries = mSession.getDataHandler().getStore().getSummaries();

        for(RoomSummary summary : summaries) {
            // not the current summary
            if (!summary.getRoomId().equals(mRoomId)) {
                Room curRoom = mSession.getDataHandler().getRoom(summary.getRoomId());
                Collection<RoomMember> otherRoomMembers = curRoom.getMembers();

                if (otherRoomMembers.size() < 100) {
                    for (RoomMember member : otherRoomMembers) {
                        String userID = member.getUserId();

                        // accepted User ID or still active users
                        if ((idsToIgnore.indexOf(userID) < 0) && (RoomMember.MEMBERSHIP_JOIN.equals(member.membership))) {
                            otherMembers.add(member);
                            idsToIgnore.add(member.getUserId());
                        }
                    }
                }
            }
        }

        mOtherRoomsMembers = otherMembers;
    }

    /**
     * refresh the list
     */
    public void refresh() {
        this.setNotifyOnChange(false);
        this.clear();
        ArrayList<RoomMember> nextMembersList = new ArrayList<RoomMember>();

        if (TextUtils.isEmpty(mPattern)) {
            // retrieve the room members
            if (mIsEditionMode) {
                ArrayList<RoomMember> admins = new ArrayList<RoomMember>();
                ArrayList<RoomMember> otherMembers = new ArrayList<RoomMember>();

                Collection<RoomMember> activeMembers = mRoom.getActiveMembers();
                String myUserId = mSession.getMyUser().userId;
                PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

                for (RoomMember member : activeMembers) {
                    // oneself member is displayed at top
                    if (member.getUserId().equals(myUserId)) {
                        nextMembersList.add(member);
                    } else {
                        if (powerLevels.getUserPowerLevel(member.getUserId()) == 100) {
                            admins.add(member);
                        } else {
                            otherMembers.add(member);
                        }
                    }
                }

                Collections.sort(admins, RoomMember.alphaComparator);
                nextMembersList.addAll(admins);

                Collections.sort(otherMembers, RoomMember.alphaComparator);
                nextMembersList.addAll(otherMembers);

                mOtherRoomsMembers = null;
            } else {
                nextMembersList = mCreationMembersList;
            }
        } else {
            if (null == mOtherRoomsMembers) {
                listOtherMembers();
            }

            // check if each member matches the pattern
            for(RoomMember member : mOtherRoomsMembers) {
                if (member.matchWith(mPattern)) {
                    nextMembersList.add(member);
                }
            }

            Collections.sort(nextMembersList, RoomMember.alphaComparator);
        }

        this.setNotifyOnChange(true);
        this.addAll(nextMembersList);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        final RoomMember member = getItem(position);
        boolean isSearchMode = !TextUtils.isEmpty(mPattern);

        ImageView thumbView = (ImageView) convertView.findViewById(R.id.avatar_img);
        thumbView.setImageResource(org.matrix.androidsdk.R.drawable.ic_contact_picture_holo_light);

        int size = getContext().getResources().getDimensionPixelSize(org.matrix.androidsdk.R.dimen.chat_avatar_size);
        mMediasCache.loadAvatarThumbnail(thumbView, member.avatarUrl, size);

        PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

        TextView textView = (TextView) convertView.findViewById(R.id.filtered_list_name);
        String text = member.getName();

        if (!isSearchMode) {
            // show the admin
            if (100 == powerLevels.getUserPowerLevel(member.getUserId())) {
                text = mContext.getString(R.string.room_participants_admin_name, text);
            }
        }
        textView.setText(text);

        final Button button = (Button) convertView.findViewById(R.id.filtered_list_button);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mOnParticipantsListener) {
                    try {
                        if (mContext.getString(R.string.leave).equals(button.getText())) {
                            mOnParticipantsListener.onLeaveClick();
                        } else {
                            // assume that the tap on remove
                            mOnParticipantsListener.onRemoveClick(member);
                        }
                    } catch (Exception e) {
                    }
                }
            }
        });

        ImageView imageView = (ImageView) convertView.findViewById(R.id.filtered_list_image);

        if (isSearchMode) {
            button.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageResource(R.drawable.ic_material_add_circle);
        } else {
            if (mIsEditionMode) {
                imageView.setVisibility(View.GONE);
                int myPowerLevel = powerLevels.getUserPowerLevel(mSession.getCredentials().userId);
                int userPowerLevel = powerLevels.getUserPowerLevel(member.getUserId());

                String buttonText = "";

                if (0 == position) {
                    buttonText = mContext.getText(R.string.leave).toString();
                } else {
                    // check if the user can kick the user
                    if ((myPowerLevel >= powerLevels.kick) && (myPowerLevel >= userPowerLevel)) {
                        buttonText = mContext.getText(R.string.remove).toString();
                    }
                }
                button.setText(buttonText);
                button.setVisibility(TextUtils.isEmpty(buttonText) ? View.GONE : View.VISIBLE);
            } else {
                button.setVisibility(View.GONE);
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageResource(R.drawable.ic_material_remove_circle);
            }
        }

        return convertView;
    }
}
