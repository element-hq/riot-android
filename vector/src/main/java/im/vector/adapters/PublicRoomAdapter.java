/*
 * Copyright 2017 Vector Creations Ltd
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
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.PublicRoom;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.Matrix;
import im.vector.R;
import im.vector.util.VectorUtils;

public class PublicRoomAdapter extends AbsListAdapter<PublicRoom, PublicRoomAdapter.PublicRoomViewHolder> {

    private final Context mContext;
    private final MXSession mSession;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public PublicRoomAdapter(final Context context, final OnSelectItemListener<PublicRoom> listener) {
        super(R.layout.adapter_item_room_view, listener);
        mContext = context;
        mSession = Matrix.getInstance(context).getDefaultSession();
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected PublicRoomViewHolder createViewHolder(View itemView) {
        return new PublicRoomViewHolder(itemView);
    }

    @Override
    protected void populateViewHolder(PublicRoomViewHolder viewHolder, PublicRoom item) {
        viewHolder.populateViews(item);
    }


    /**
     * Tells if a public room matches a pattern.
     *
     * @param publicRoom the roomState to test
     * @param pattern    the pattern
     * @return true if it matches
     */
    private static boolean match(PublicRoom publicRoom, Pattern pattern) {
        List<String> itemsToTest = new ArrayList<>();

        if (null != publicRoom.name) {
            itemsToTest.add(publicRoom.name);
        }

        if (null != publicRoom.topic) {
            itemsToTest.add(publicRoom.topic);
        }

        if (null != publicRoom.alias) {
            itemsToTest.add(publicRoom.alias);
        }

        if (null != publicRoom.roomId) {
            itemsToTest.add(publicRoom.roomId);
        }

        if (null != publicRoom.aliases) {
            itemsToTest.addAll(publicRoom.aliases);
        }

        for (String item : itemsToTest) {
            if (pattern.matcher(item).find()) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected List<PublicRoom> getFilterItems(List<PublicRoom> publicRooms, String text) {
        Pattern pattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);

        List<PublicRoom> filteredRoomStates = new ArrayList<>();

        for (final PublicRoom state : publicRooms) {
            if (match(state, pattern)) {
                filteredRoomStates.add(state);
            }
        }
        return filteredRoomStates;
    }

    /*
     * *********************************************************************************************
     * View holder
     * *********************************************************************************************
     */

    class PublicRoomViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.room_avatar)
        ImageView vRoomAvatar;

        @BindView(R.id.room_name)
        TextView vRoomName;

        @BindView(R.id.room_message)
        TextView vRoomLastMessage;

        @BindView(R.id.room_update_date)
        TextView vRoomTimestamp;

        private PublicRoomViewHolder(final View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        private void populateViews(final PublicRoom publicRoom) {
            String roomName = !TextUtils.isEmpty(publicRoom.name) ? publicRoom.name : VectorUtils.getPublicRoomDisplayName(publicRoom);

            // display the room avatar
            vRoomAvatar.setBackgroundColor(mContext.getResources().getColor(android.R.color.transparent));
            VectorUtils.loadUserAvatar(mContext, mSession, vRoomAvatar, publicRoom.getAvatarUrl(), publicRoom.roomId, roomName);

            // set the topic
            vRoomLastMessage.setText(publicRoom.topic);

            // display the room name
            vRoomName.setText(roomName);

            // display the number of users
            String usersText;
            if (publicRoom.numJoinedMembers > 1) {
                usersText = publicRoom.numJoinedMembers + " " + mContext.getResources().getString(R.string.users);
            } else {
                usersText = publicRoom.numJoinedMembers + " " + mContext.getResources().getString(R.string.user);
            }

            vRoomTimestamp.setText(usersText);
        }
    }
}
