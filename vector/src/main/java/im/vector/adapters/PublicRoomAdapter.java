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
        super(R.layout.adapter_item_public_room_view, listener);
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

    @Override
    protected List<PublicRoom> getFilterItems(List<PublicRoom> publicRooms, String text) {
        // no local search
        return new ArrayList<>();
    }

    /*
     * *********************************************************************************************
     * View holder
     * *********************************************************************************************
     */

    class PublicRoomViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.public_room_avatar)
        ImageView vPublicRoomAvatar;

        @BindView(R.id.public_room_name)
        TextView vPublicRoomName;

        @BindView(R.id.public_room_topic)
        TextView vRoomTopic;

        @BindView(R.id.public_room_members_count)
        TextView vPublicRoomsMemberCountTextView;

        private PublicRoomViewHolder(final View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        private void populateViews(final PublicRoom publicRoom) {
            String roomName = !TextUtils.isEmpty(publicRoom.name) ? publicRoom.name : VectorUtils.getPublicRoomDisplayName(publicRoom);

            // display the room avatar
            vPublicRoomAvatar.setBackgroundColor(mContext.getResources().getColor(android.R.color.transparent));
            VectorUtils.loadUserAvatar(mContext, mSession, vPublicRoomAvatar, publicRoom.getAvatarUrl(), publicRoom.roomId, roomName);

            // set the topic
            vRoomTopic.setText(publicRoom.topic);

            // display the room name
            vPublicRoomName.setText(roomName);

            // members count
            vPublicRoomsMemberCountTextView.setText(publicRoom.numJoinedMembers + "");
        }
    }
}
