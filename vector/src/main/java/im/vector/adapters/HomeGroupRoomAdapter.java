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
import android.support.annotation.LayoutRes;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;

import org.matrix.androidsdk.rest.model.group.GroupRoom;
import org.matrix.androidsdk.rest.model.group.GroupUser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class HomeGroupRoomAdapter extends AbsFilterableAdapter<GroupRoomViewHolder> {
    private final int mLayoutRes;
    private final List<GroupRoom> mGroupRooms;
    private final List<GroupRoom> mFilteredGroupRooms;
    private final OnSelectGroupRoomListener mListener;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public HomeGroupRoomAdapter(final Context context, @LayoutRes final int layoutRes,
                                final OnSelectGroupRoomListener listener) {
        super(context);

        mGroupRooms = new ArrayList<>();
        mFilteredGroupRooms = new ArrayList<>();

        mLayoutRes = layoutRes;
        mListener = listener;
    }

    /*
     * *********************************************************************************************
     * RecyclerView.Adapter methods
     * *********************************************************************************************
     */

    @Override
    public GroupRoomViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        final LayoutInflater layoutInflater = LayoutInflater.from(viewGroup.getContext());
        final View view = layoutInflater.inflate(mLayoutRes, viewGroup, false);
        return new GroupRoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final GroupRoomViewHolder viewHolder, int position) {
        // reported by a rage shake
        if (position < mFilteredGroupRooms.size()) {
            final GroupRoom groupRoom = mFilteredGroupRooms.get(position);

            viewHolder.populateViews(mContext, mSession, groupRoom);
            viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.onSelectGroupRoom(groupRoom, viewHolder.getAdapterPosition());
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return mFilteredGroupRooms.size();
    }

    @Override
    protected Filter createFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                final FilterResults results = new FilterResults();

                filterGroupRooms(constraint);

                results.values = mFilteredGroupRooms;
                results.count = mFilteredGroupRooms.size();

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
     * @param groupRooms the new group rooms list
     */
    public void setGroupRooms(final List<GroupRoom> groupRooms) {
        if (groupRooms != null) {
            mGroupRooms.clear();
            mGroupRooms.addAll(groupRooms);
            filterGroupRooms(mCurrentFilterPattern);
        }
        notifyDataSetChanged();
    }

    /**
     * Return whether the section (not filtered) is empty or not
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return mGroupRooms.isEmpty();
    }

    /**
     * Return whether the section (filtered) is empty or not
     *
     * @return true if empty
     */
    public boolean hasNoResult() {
        return mFilteredGroupRooms.isEmpty();
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Filter a groupRooms list.
     *
     * @param groupRoomsToFilter the list to filter
     * @param constraint         the constraint
     * @return the filtered list
     */
    private static List<GroupRoom> getFilteredGroupRooms(final List<GroupRoom> groupRoomsToFilter, final CharSequence constraint) {

        final String filterPattern = constraint != null ? constraint.toString().trim() : null;
        if (!TextUtils.isEmpty(filterPattern)) {
            List<GroupRoom> filteredGroupRooms = new ArrayList<>();
            Pattern pattern = Pattern.compile(Pattern.quote(filterPattern), Pattern.CASE_INSENSITIVE);
            for (final GroupRoom room : groupRoomsToFilter) {
                if (pattern.matcher(room.getDisplayName()).find()) {
                    filteredGroupRooms.add(room);
                }
            }
            return filteredGroupRooms;
        } else {
            return groupRoomsToFilter;
        }
    }

    /**
     * Filter the room list according to the given pattern
     *
     * @param constraint the constraint
     */
    private void filterGroupRooms(CharSequence constraint) {
        mFilteredGroupRooms.clear();
        mFilteredGroupRooms.addAll(getFilteredGroupRooms(mGroupRooms, constraint));
    }

    /*
     * *********************************************************************************************
     * Listeners
     * *********************************************************************************************
     */

    public interface OnSelectGroupRoomListener {
        void onSelectGroupRoom(GroupRoom groupRoom, int position);
    }
}
