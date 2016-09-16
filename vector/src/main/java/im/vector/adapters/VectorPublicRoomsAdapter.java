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

import org.matrix.androidsdk.rest.model.PublicRoom;

/**
 * An adapter which can display m.room.member content.
 */
public class VectorPublicRoomsAdapter extends ArrayAdapter<PublicRoom> {

    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;

    /**
     * Constructor of a public rooms adapter.
     * @param context the context
     * @param layoutResourceId the layout
     */
    public VectorPublicRoomsAdapter(Context context, int layoutResourceId) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        PublicRoom publicRoom = getItem(position);
        String roomName = !TextUtils.isEmpty(publicRoom.name) ? publicRoom.name : VectorUtils.getPublicRoomDisplayName(publicRoom);

        // retrieve the UI items
        ImageView avatarImageView = (ImageView)convertView.findViewById(R.id.room_avatar_image_view);
        TextView roomNameTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);
        TextView roomMessageTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomMessage);
        ImageView wordReadableView = (ImageView)convertView.findViewById(R.id.roomSummaryAdapter_word_readable);
        ImageView guestAccessView = (ImageView)convertView.findViewById(R.id.roomSummaryAdapter_guest_access);

        TextView timestampTxtView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
        View separatorView = convertView.findViewById(R.id.recents_separator);

        // display the room avatar
        avatarImageView.setBackgroundColor(mContext.getResources().getColor(android.R.color.transparent));
        VectorUtils.setDefaultRoomVectorAvatar(avatarImageView, publicRoom.roomId, roomName);

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
