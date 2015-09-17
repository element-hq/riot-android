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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.util.JsonUtils;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import im.vector.R;

public class VectorAddParticipantsAdapter extends ArrayAdapter<RoomMember> {
    //
    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private MXMediasCache mMediasCache;

    private MXSession mSession;
    private String mRoomId;
    private Room mRoom;
    private int mLayoutResourceId;

    ArrayList<RoomMember> mOtherRoomsMembers = null;
    String mPattern = "";

    public VectorAddParticipantsAdapter(Context context, int layoutResourceId, MXSession session, String roomId, MXMediasCache mediasCache) {
        super(context, layoutResourceId);

        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
        mLayoutResourceId = layoutResourceId;
        mMediasCache = mediasCache;
        mSession = session;
        mRoomId = roomId;
        mRoom = mSession.getDataHandler().getRoom(roomId);
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
            ArrayList<RoomMember> admins = new ArrayList<RoomMember>();
            ArrayList<RoomMember> otherMembers = new ArrayList<RoomMember>();

            Collection<RoomMember> activeMembers = mRoom.getActiveMembers();
            String myUserId = mSession.getMyUser().userId;
            PowerLevels powerLevels =  mRoom.getLiveState().getPowerLevels();

            for(RoomMember member : activeMembers) {
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

        TextView textView = (TextView) convertView.findViewById(R.id.filtered_list_name);

        String text = member.getName();

        if (!isSearchMode) {
            // show the admin
            if (100 == mRoom.getLiveState().getPowerLevels().getUserPowerLevel(member.getUserId())) {
                text = mContext.getString(R.string.room_participants_admin_name, text);
            }
        }

        textView.setText(text);

        return convertView;
    }
}
