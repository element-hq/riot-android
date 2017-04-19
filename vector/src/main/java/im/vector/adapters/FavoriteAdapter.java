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
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import im.vector.Matrix;
import im.vector.util.VectorUtils;

public class FavoriteAdapter extends RecyclerView.Adapter<RoomViewHolder> implements Filterable {

    private final Context mContext;
    private final MXSession mSession;

    private final int mLayoutRes;
    private final List<Room> mRooms;
    private final List<Room> mFilteredRooms;
    private final OnFavoritesListener mListener;

    private final AbsAdapter.MoreRoomActionListener mMoreActionListener;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public FavoriteAdapter(final Context context, @LayoutRes final int layoutRes, final OnFavoritesListener listener, AbsAdapter.MoreRoomActionListener moreActionListener) {
        mLayoutRes = layoutRes;
        mRooms = new ArrayList<>();
        mFilteredRooms = new ArrayList<>();
        mListener = listener;
        mMoreActionListener = moreActionListener;

        mContext = context;
        mSession =  Matrix.getInstance(context).getDefaultSession();
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
        return new RoomViewHolder(mContext, mSession, view, mMoreActionListener);
    }

    @Override
    public void onBindViewHolder(final RoomViewHolder viewHolder, int position) {
        final Room room = mFilteredRooms.get(position);
        viewHolder.populateViews(room, mSession.getDirectChatRoomIdsList().contains(room.getRoomId()), false);
        viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onSelectFavorite(room, viewHolder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return mFilteredRooms.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                final FilterResults results = new FilterResults();

                mFilteredRooms.clear();
                if (TextUtils.isEmpty(constraint)) {
                    mFilteredRooms.addAll(mRooms);
                } else {
                    final String filterPattern = constraint.toString().trim();
                    for (final Room room : mRooms) {

                        final String roomName = VectorUtils.getRoomDisplayName(mContext, mSession, room);
                        if (Pattern.compile(Pattern.quote(filterPattern), Pattern.CASE_INSENSITIVE)
                                .matcher(roomName)
                                .find()) {
                            mFilteredRooms.add(room);
                        }
                    }
                }

                results.values = mFilteredRooms;
                results.count = mFilteredRooms.size();

                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
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

            mFilteredRooms.clear();
            mFilteredRooms.addAll(rooms);
        }

        notifyDataSetChanged();
    }

    /**
     * Provides the item at the dedicated position
     * @param position the position
     * @return the item
     */
    public Room getRoom(int position) {
        if (position < mRooms.size()) {
            return mRooms.get(position);
        }

        return null;
    }

    /*
     * *********************************************************************************************
     * Listeners
     * *********************************************************************************************
     */

    public interface OnFavoritesListener {
        void onSelectFavorite(Room room, int position);
    }
}
