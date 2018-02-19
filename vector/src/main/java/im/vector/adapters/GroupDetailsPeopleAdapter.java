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

import org.matrix.androidsdk.rest.model.group.GroupUser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import im.vector.R;
import im.vector.util.GroupUtils;

public class GroupDetailsPeopleAdapter extends AbsAdapter {
    private static final int TYPE_JOINED_USERS = 12;
    private static final int TYPE_INVITED_USERS = 13;

    private final GroupAdapterSection<GroupUser> mJoinedUsersSection;
    private final GroupAdapterSection<GroupUser> mInvitedUsersSection;

    private final OnSelectUserListener mListener;

    private static final Comparator<GroupUser> mComparator = new Comparator<GroupUser>() {
        @Override
        public int compare(GroupUser lhs, GroupUser rhs) {
            return lhs.getDisplayname().compareTo(rhs.getDisplayname());
        }
    };

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public GroupDetailsPeopleAdapter(final Context context, final OnSelectUserListener listener) {
        super(context);

        mListener = listener;

        mJoinedUsersSection = new GroupAdapterSection<>(context, context.getString(R.string.joined), -1,
                R.layout.adapter_item_group_user_room_view, TYPE_HEADER_DEFAULT, TYPE_JOINED_USERS, new ArrayList<GroupUser>(), mComparator);
        mJoinedUsersSection.setEmptyViewPlaceholder(context.getString(R.string.no_users_placeholder), context.getString(R.string.no_result_placeholder));

        mInvitedUsersSection = new GroupAdapterSection<>(context, context.getString(R.string.invited), -1,
                R.layout.adapter_item_group_user_room_view, TYPE_HEADER_DEFAULT, TYPE_INVITED_USERS, new ArrayList<GroupUser>(), mComparator);
        mInvitedUsersSection.setEmptyViewPlaceholder(context.getString(R.string.no_users_placeholder), context.getString(R.string.no_result_placeholder));
        mInvitedUsersSection.setIsHiddenWhenEmpty(true);

        addSection(mJoinedUsersSection);
        addSection(mInvitedUsersSection);
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected RecyclerView.ViewHolder createSubViewHolder(ViewGroup viewGroup, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());

        if ((viewType == TYPE_JOINED_USERS) || (viewType == TYPE_INVITED_USERS)) {
            return new GroupUserViewHolder(inflater.inflate(R.layout.adapter_item_group_user_room_view, viewGroup, false));
        }
        return null;
    }

    @Override
    protected void populateViewHolder(int viewType, RecyclerView.ViewHolder viewHolder, int position) {
        switch (viewType) {
            case TYPE_JOINED_USERS:
            case TYPE_INVITED_USERS:
                final GroupUserViewHolder groupUserViewHolder = (GroupUserViewHolder) viewHolder;
                final GroupUser groupUser = (GroupUser) getItemForPosition(position);
                groupUserViewHolder.populateViews(mContext, mSession, groupUser);

                groupUserViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mListener.onSelectItem(groupUser, -1);
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
    int filterGroupUsersSection(final AdapterSection<GroupUser> section, final String filterPattern) {
        if (null != section) {
            if (!TextUtils.isEmpty(filterPattern)) {
                List<GroupUser> filteredGroupUsers = GroupUtils.getFilteredGroupUsers(section.getItems(), filterPattern);
                section.setFilteredItems(filteredGroupUsers, filterPattern);
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
        int nbResults = 0;

        nbResults += filterGroupUsersSection(mJoinedUsersSection, pattern);
        nbResults += filterGroupUsersSection(mInvitedUsersSection, pattern);

        return nbResults;
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    public void setJoinedGroupUsers(final List<GroupUser> users) {
        mJoinedUsersSection.setItems(users, mCurrentFilterPattern);
        if (!TextUtils.isEmpty(mCurrentFilterPattern)) {
            filterGroupUsersSection(mJoinedUsersSection, String.valueOf(mCurrentFilterPattern));
        }
        updateSections();
    }

    public void setInvitedGroupUsers(final List<GroupUser> users) {
        mInvitedUsersSection.setItems(users, mCurrentFilterPattern);
        if (!TextUtils.isEmpty(mCurrentFilterPattern)) {
            filterGroupUsersSection(mInvitedUsersSection, String.valueOf(mCurrentFilterPattern));
        }
        updateSections();
    }

    /*
     * *********************************************************************************************
     * Inner classes
     * *********************************************************************************************
     */

    public interface OnSelectUserListener {
        void onSelectItem(GroupUser user, int position);
    }
}
