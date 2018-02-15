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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.matrix.androidsdk.rest.model.group.GroupRoom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import im.vector.R;
import im.vector.util.GroupUtils;

public class GroupDetailsRoomsAdapter extends AbsAdapter {
    private static final int TYPE_GROUP_ROOMS = 22;

    private static final Comparator<GroupRoom> mComparator = new Comparator<GroupRoom>() {
        @Override
        public int compare(GroupRoom lhs, GroupRoom rhs) {
            return lhs.getDisplayName().compareTo(rhs.getDisplayName());
        }
    };

    private final GroupAdapterSection<GroupRoom> mGroupRoomsSection;
    private final OnSelectRoomListener mListener;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public GroupDetailsRoomsAdapter(final Context context, final OnSelectRoomListener listener) {
        super(context);
        mListener = listener;

        mGroupRoomsSection = new GroupAdapterSection<>(context, context.getString(R.string.rooms), -1,
                R.layout.adapter_item_group_user_room_view, TYPE_HEADER_DEFAULT, TYPE_GROUP_ROOMS, new ArrayList<GroupRoom>(), mComparator);
        mGroupRoomsSection.setEmptyViewPlaceholder(null, context.getString(R.string.no_result_placeholder));

        addSection(mGroupRoomsSection);
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected RecyclerView.ViewHolder createSubViewHolder(ViewGroup viewGroup, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());

        if (viewType == TYPE_GROUP_ROOMS) {
            return new GroupRoomViewHolder(inflater.inflate(R.layout.adapter_item_group_user_room_view, viewGroup, false));
        }
        return null;
    }

    @Override
    protected void populateViewHolder(int viewType, RecyclerView.ViewHolder viewHolder, int position) {
        switch (viewType) {
            case TYPE_GROUP_ROOMS:
                final GroupRoomViewHolder groupRoomViewHolder = (GroupRoomViewHolder) viewHolder;
                final GroupRoom groupRoom = (GroupRoom) getItemForPosition(position);
                groupRoomViewHolder.populateViews(mContext, mSession, groupRoom);

                groupRoomViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mListener.onSelectItem(groupRoom, -1);
                    }
                });
                break;
        }
    }

    /**
     * Filter the given section of rooms with the given pattern
     *
     * @param section
     * @param filterPattern
     * @return nb of items matching the filter
     */
    int filterGroupRoomsSection(final AdapterSection<GroupRoom> section, final String filterPattern) {
        if (null != section) {
            if (!TextUtils.isEmpty(filterPattern)) {
                List<GroupRoom> filteredGroupRooms = GroupUtils.getFilteredGroupRooms(section.getItems(), filterPattern);
                section.setFilteredItems(filteredGroupRooms, filterPattern);
            } else {
                section.resetFilter();
            }
            return section.getFilteredItems().size();
        } else {
            return 0;
        }
    }


    @Override
    protected int applyFilter(String pattern) {
        return filterGroupRoomsSection(mGroupRoomsSection, pattern);
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    public void setGroupRooms(final List<GroupRoom> rooms) {
        mGroupRoomsSection.setItems(rooms, mCurrentFilterPattern);
        updateSections();
    }

    /*
     * *********************************************************************************************
     * Inner classes
     * *********************************************************************************************
     */

    public interface OnSelectRoomListener {
        void onSelectItem(GroupRoom groupRoom, int position);
    }
}
