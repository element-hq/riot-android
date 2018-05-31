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
import android.widget.ImageView;
import android.widget.TextView;

import im.vector.R;
import im.vector.util.VectorUtils;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.publicroom.PublicRoom;

/**
 * An adapter which can display m.room.member content.
 */
public class VectorPublicRoomsAdapter extends ArrayAdapter<PublicRoom> {

    private final MXSession mSession;
    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final int mLayoutResourceId;

    /**
     * Constructor of a public rooms adapter.
     *
     * @param context          the context
     * @param layoutResourceId the layout
     */
    public VectorPublicRoomsAdapter(Context context, int layoutResourceId, MXSession session) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
        mSession = session;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        PublicRoom publicRoom = getItem(position);
        String roomName = !TextUtils.isEmpty(publicRoom.name) ? publicRoom.name : VectorUtils.getPublicRoomDisplayName(publicRoom);

        // retrieve the UI items
        ImageView avatarImageView = convertView.findViewById(R.id.room_avatar);
        TextView roomNameTxtView = convertView.findViewById(R.id.roomSummaryAdapter_roomName);
        TextView roomMessageTxtView = convertView.findViewById(R.id.roomSummaryAdapter_roomMessage);

        TextView timestampTxtView = convertView.findViewById(R.id.roomSummaryAdapter_ts);
        View separatorView = convertView.findViewById(R.id.recents_separator);

        // display the room avatar
        VectorUtils.loadUserAvatar(mContext, mSession, avatarImageView, publicRoom.getAvatarUrl(), publicRoom.roomId, roomName);

        // set the topic
        roomMessageTxtView.setText(publicRoom.topic);

        // display the room name
        roomNameTxtView.setText(roomName);

        // display the number of users
        String usersText;
        if (publicRoom.numJoinedMembers > 1) {
            usersText = publicRoom.numJoinedMembers + " " + mContext.getResources().getString(R.string.users);
        } else {
            usersText = publicRoom.numJoinedMembers + " " + mContext.getResources().getString(R.string.user);
        }

        timestampTxtView.setText(usersText);

        // separator
        separatorView.setVisibility(View.VISIBLE);

        convertView.findViewById(R.id.bing_indicator_unread_message).setVisibility(View.INVISIBLE);
        convertView.findViewById(R.id.recents_groups_separator_line).setVisibility(View.GONE);
        convertView.findViewById(R.id.roomSummaryAdapter_action).setVisibility(View.GONE);
        convertView.findViewById(R.id.roomSummaryAdapter_action_image).setVisibility(View.GONE);

        convertView.findViewById(R.id.recents_groups_invitation_group).setVisibility(View.GONE);

        // theses settings are not yet properly designed.
       /*boolean isWordReadable = (null != publicRoom.worldReadable) ? publicRoom.worldReadable : false;
        boolean isGuessAccessed = (null != publicRoom.guestCanJoin) ? publicRoom.guestCanJoin : false;

        if (!isWordReadable && !isGuessAccessed) {
            wordReadableView.setVisibility(View.GONE);
            guestAccessView.setVisibility(View.GONE);
        } else {
            wordReadableView.setVisibility(isWordReadable ? View.VISIBLE : View.INVISIBLE);
            guestAccessView.setVisibility(isGuessAccessed ? View.VISIBLE : View.INVISIBLE);
        }*/

        return convertView;
    }
}
