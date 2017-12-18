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
import android.graphics.Color;
import android.support.annotation.CallSuper;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.publicroom.PublicRoom;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;
import im.vector.util.RoomUtils;
import im.vector.util.VectorUtils;

public class RoomAdapter extends AbsAdapter {

    private static final String LOG_TAG = RoomAdapter.class.getSimpleName();

    private static final int TYPE_HEADER_PUBLIC_ROOM = 0;

    private static final int TYPE_PUBLIC_ROOM = 1;

    private final AdapterSection<Room> mRoomsSection;
    private final PublicRoomsAdapterSection mPublicRoomsSection;

    private final OnSelectItemListener mListener;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public RoomAdapter(final Context context, final OnSelectItemListener listener, final RoomInvitationListener invitationListener, final MoreRoomActionListener moreActionListener) {
        super(context, invitationListener, moreActionListener);

        mListener = listener;

        mRoomsSection = new AdapterSection<>(context, context.getString(R.string.rooms_header), -1,
                R.layout.adapter_item_room_view, TYPE_HEADER_DEFAULT, TYPE_ROOM, new ArrayList<Room>(), RoomUtils.getRoomsDateComparator(mSession, false));
        mRoomsSection.setEmptyViewPlaceholder(context.getString(R.string.no_room_placeholder), context.getString(R.string.no_result_placeholder));

        mPublicRoomsSection = new PublicRoomsAdapterSection(context, context.getString(R.string.rooms_directory_header),
                R.layout.adapter_public_room_sticky_header_subview, R.layout.adapter_item_public_room_view,
                TYPE_HEADER_PUBLIC_ROOM, TYPE_PUBLIC_ROOM, new ArrayList<PublicRoom>(), null);
        mPublicRoomsSection.setEmptyViewPlaceholder(context.getString(R.string.no_public_room_placeholder), context.getString(R.string.no_result_placeholder));

        addSection(mRoomsSection);
        addSection(mPublicRoomsSection);
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected RecyclerView.ViewHolder createSubViewHolder(ViewGroup viewGroup, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());

        View itemView;

        if (viewType == TYPE_HEADER_PUBLIC_ROOM) {
            //TODO replace by a empty view ?
            itemView = inflater.inflate(R.layout.adapter_section_header_public_room, viewGroup, false);
            itemView.setBackgroundColor(Color.MAGENTA);
            return new HeaderViewHolder(itemView);
        } else {
            switch (viewType) {
                case TYPE_ROOM:
                    itemView = inflater.inflate(R.layout.adapter_item_room_view, viewGroup, false);
                    return new RoomViewHolder(itemView);
                case TYPE_PUBLIC_ROOM:
                    itemView = inflater.inflate(R.layout.adapter_item_public_room_view, viewGroup, false);
                    return new PublicRoomViewHolder(itemView);
            }
        }
        return null;
    }

    @Override
    protected void populateViewHolder(int viewType, RecyclerView.ViewHolder viewHolder, int position) {
        switch (viewType) {
            case TYPE_HEADER_PUBLIC_ROOM:
                // Local header
                final HeaderViewHolder headerViewHolder = (HeaderViewHolder) viewHolder;
                for (Pair<Integer, AdapterSection> adapterSection : getSectionsArray()) {
                    if (adapterSection.first == position) {
                        headerViewHolder.populateViews(adapterSection.second);
                        break;
                    }
                }
                break;
            case TYPE_ROOM:
                final RoomViewHolder roomViewHolder = (RoomViewHolder) viewHolder;
                final Room room = (Room) getItemForPosition(position);
                roomViewHolder.populateViews(mContext, mSession, room, false, false, mMoreRoomActionListener);
                roomViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mListener.onSelectItem(room, -1);
                    }
                });
                break;
            case TYPE_PUBLIC_ROOM:
                final PublicRoomViewHolder publicRoomViewHolder = (PublicRoomViewHolder) viewHolder;
                final PublicRoom publicRoom = (PublicRoom) getItemForPosition(position);
                publicRoomViewHolder.populateViews(publicRoom);
                break;
        }
    }

    @Override
    protected int applyFilter(String pattern) {
        int nbResults = 0;

        nbResults += filterRoomSection(mRoomsSection, pattern);

        // The public rooms search is done by a server request.
        // The result is also paginated so it make no sense to be done in the adapter

        return nbResults;
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    public void setRooms(final List<Room> rooms) {
        mRoomsSection.setItems(rooms, mCurrentFilterPattern);
        if (!TextUtils.isEmpty(mCurrentFilterPattern)) {
            filterRoomSection(mRoomsSection, String.valueOf(mCurrentFilterPattern));
        }
        updateSections();
    }

    public void setPublicRooms(final List<PublicRoom> publicRooms) {
        mPublicRoomsSection.setItems(publicRooms, mCurrentFilterPattern);
        updateSections();
    }

    public void setEstimatedPublicRoomsCount(int estimatedCount) {
        mPublicRoomsSection.setEstimatedPublicRoomsCount(estimatedCount);
    }

    public void setNoMorePublicRooms(boolean noMore) {
        mPublicRoomsSection.setHasMoreResults(noMore);
    }

    /**
     * Add more public rooms to the current list
     *
     * @param publicRooms
     */
    @CallSuper
    public void addPublicRooms(final List<PublicRoom> publicRooms) {
        final List<PublicRoom> newPublicRooms = new ArrayList<>();

        newPublicRooms.addAll(mPublicRoomsSection.getItems());
        newPublicRooms.addAll(publicRooms);
        mPublicRoomsSection.setItems(newPublicRooms, mCurrentFilterPattern);
        updateSections();
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
            if (null == publicRoom) {
                Log.e(LOG_TAG, "## populateViews() : null publicRoom");
                return;
            }

            String roomName = !TextUtils.isEmpty(publicRoom.name) ? publicRoom.name : VectorUtils.getPublicRoomDisplayName(publicRoom);

            // display the room avatar
            vPublicRoomAvatar.setBackgroundColor(ContextCompat.getColor(mContext, android.R.color.transparent));
            VectorUtils.loadUserAvatar(mContext, mSession, vPublicRoomAvatar, publicRoom.getAvatarUrl(), publicRoom.roomId, roomName);

            // set the topic
            vRoomTopic.setText(publicRoom.topic);

            // display the room name
            vPublicRoomName.setText(roomName);

            // members count
            vPublicRoomsMemberCountTextView.setText(mContext.getResources().getQuantityString(R.plurals.public_room_nb_users,
                    publicRoom.numJoinedMembers, publicRoom.numJoinedMembers));

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.onSelectItem(publicRoom);
                }
            });
        }
    }

    /*
     * *********************************************************************************************
     * Inner classes
     * *********************************************************************************************
     */

    public interface OnSelectItemListener {
        void onSelectItem(Room item, int position);

        void onSelectItem(PublicRoom publicRoom);
    }
}
