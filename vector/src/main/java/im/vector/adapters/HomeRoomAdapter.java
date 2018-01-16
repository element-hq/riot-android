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
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;

import org.matrix.androidsdk.data.Room;

import java.util.ArrayList;
import java.util.List;

import im.vector.R;
import im.vector.util.RoomUtils;

public class HomeRoomAdapter extends AbsFilterableAdapter<RoomViewHolder> {
    private final int mLayoutRes;
    private final List<Room> mRooms;
    private final List<Room> mFilteredRooms;
    private final OnSelectRoomListener mListener;

    private final AbsAdapter.MoreRoomActionListener mMoreActionListener;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public HomeRoomAdapter(final Context context, @LayoutRes final int layoutRes, final OnSelectRoomListener listener,
                           final AbsAdapter.RoomInvitationListener invitationListener, final AbsAdapter.MoreRoomActionListener moreActionListener) {
        super(context, invitationListener, moreActionListener);

        mRooms = new ArrayList<>();
        mFilteredRooms = new ArrayList<>();

        mLayoutRes = layoutRes;
        mListener = listener;
        mMoreActionListener = moreActionListener;
    }

    /*
     * *********************************************************************************************
     * RecyclerView.Adapter methods
     * *********************************************************************************************
     */

    @Override
    public RoomViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        final LayoutInflater layoutInflater = LayoutInflater.from(viewGroup.getContext());
        final View view = layoutInflater.inflate(mLayoutRes, viewGroup, false);
        return mLayoutRes == R.layout.adapter_item_room_invite ? new RoomInvitationViewHolder(view) : new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final RoomViewHolder viewHolder, int position) {
        // reported by a rage shake
        if (position < mFilteredRooms.size()) {
            final Room room = mFilteredRooms.get(position);
            if (mLayoutRes == R.layout.adapter_item_room_invite) {
                final RoomInvitationViewHolder invitationViewHolder = (RoomInvitationViewHolder) viewHolder;
                invitationViewHolder.populateViews(mContext, mSession, room, mRoomInvitationListener, mMoreActionListener);
            } else {
                viewHolder.populateViews(mContext, mSession, room, mSession.getDirectChatRoomIdsList().contains(room.getRoomId()), false, mMoreActionListener);
                viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mListener.onSelectRoom(room, viewHolder.getAdapterPosition());
                    }
                });
                viewHolder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        mListener.onLongClickRoom(v, room, viewHolder.getAdapterPosition());
                        return true;
                    }
                });
            }
        }
    }

    @Override
    public int getItemCount() {
        return mFilteredRooms.size();
    }

    @Override
    protected Filter createFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                final FilterResults results = new FilterResults();

                filterRooms(constraint);

                results.values = mFilteredRooms;
                results.count = mFilteredRooms.size();

                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                onFilterDone(constraint);
                notifyDataSetChanged();
            }
        };
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    /**
     * Feed the adapter with items
     *
     * @param rooms the new room list
     */
    @CallSuper
    public void setRooms(final List<Room> rooms) {
        if (rooms != null) {
            mRooms.clear();
            mRooms.addAll(rooms);
            filterRooms(mCurrentFilterPattern);
        }
        notifyDataSetChanged();
    }

    /**
     * Provides the item at the dedicated position
     *
     * @param position the position
     * @return the item
     */
    public Room getRoom(int position) {
        if (position < mRooms.size()) {
            return mRooms.get(position);
        }

        return null;
    }

    /**
     * Return whether the section (not filtered) is empty or not
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return mRooms.isEmpty();
    }

    /**
     * Return whether the section (filtered) is empty or not
     *
     * @return true if empty
     */
    public boolean hasNoResult() {
        return mFilteredRooms.isEmpty();
    }

    /**
     * Return the sum of notifications for all the displayed rooms
     *
     * @return badge value
     */
    public int getBadgeCount() {
        int badgeCount = 0;
        for (Room room : mFilteredRooms) {
            // sanity checks : reported by GA
            if (null != room.getDataHandler() && (null != room.getDataHandler().getBingRulesManager())) {
                if (room.getDataHandler().getBingRulesManager().isRoomMentionOnly(room.getRoomId())) {
                    badgeCount += room.getHighlightCount();
                } else {
                    badgeCount += room.getNotificationCount();
                }
            }
        }
        return badgeCount;
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Filter the room list according to the given pattern
     *
     * @param constraint
     */
    private void filterRooms(CharSequence constraint) {
        mFilteredRooms.clear();
        mFilteredRooms.addAll(RoomUtils.getFilteredRooms(mContext, mSession, mRooms, constraint));
    }

    /*
     * *********************************************************************************************
     * Listeners
     * *********************************************************************************************
     */

    public interface OnSelectRoomListener {
        void onSelectRoom(Room room, int position);

        void onLongClickRoom(View v, Room room, int position);
    }
}
