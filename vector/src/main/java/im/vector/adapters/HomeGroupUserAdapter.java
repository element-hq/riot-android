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

import org.matrix.androidsdk.rest.model.group.GroupUser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class HomeGroupUserAdapter extends AbsFilterableAdapter<GroupUserViewHolder> {
    private final int mLayoutRes;
    private final List<GroupUser> mGroupUsers;
    private final List<GroupUser> mFilteredGroupUsers;
    private final OnSelectGroupUserListener mListener;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public HomeGroupUserAdapter(final Context context, @LayoutRes final int layoutRes,
                                final OnSelectGroupUserListener listener) {
        super(context);

        mGroupUsers = new ArrayList<>();
        mFilteredGroupUsers = new ArrayList<>();

        mLayoutRes = layoutRes;
        mListener = listener;
    }

    /*
     * *********************************************************************************************
     * RecyclerView.Adapter methods
     * *********************************************************************************************
     */

    @Override
    public GroupUserViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        final LayoutInflater layoutInflater = LayoutInflater.from(viewGroup.getContext());
        final View view = layoutInflater.inflate(mLayoutRes, viewGroup, false);
        return new GroupUserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final GroupUserViewHolder viewHolder, int position) {
        // reported by a rage shake
        if (position < mFilteredGroupUsers.size()) {
            final GroupUser groupUser = mFilteredGroupUsers.get(position);

            viewHolder.populateViews(mContext, mSession, groupUser);
            viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.onSelectGroupUser(groupUser, viewHolder.getAdapterPosition());
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return mFilteredGroupUsers.size();
    }

    @Override
    protected Filter createFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                final FilterResults results = new FilterResults();

                filterGroupUsers(constraint);

                results.values = mFilteredGroupUsers;
                results.count = mFilteredGroupUsers.size();

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
     * @param groupUsers the new groupUser list
     */
    public void setGroupUsers(final List<GroupUser> groupUsers) {
        if (groupUsers != null) {
            mGroupUsers.clear();
            mGroupUsers.addAll(groupUsers);
            filterGroupUsers(mCurrentFilterPattern);
        }
        notifyDataSetChanged();
    }

    /**
     * Return whether the section (not filtered) is empty or not
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return mGroupUsers.isEmpty();
    }

    /**
     * Return whether the section (filtered) is empty or not
     *
     * @return true if empty
     */
    public boolean hasNoResult() {
        return mFilteredGroupUsers.isEmpty();
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Filter a groupUsers list.
     *
     * @param GroupUsersToFilter the list to filter
     * @param constraint         the constraint
     * @return the filtered list
     */
    private static List<GroupUser> getFilteredGroupUsers(final List<GroupUser> GroupUsersToFilter, final CharSequence constraint) {

        final String filterPattern = constraint != null ? constraint.toString().trim() : null;
        if (!TextUtils.isEmpty(filterPattern)) {
            List<GroupUser> filteredGroupUsers = new ArrayList<>();
            Pattern pattern = Pattern.compile(Pattern.quote(filterPattern), Pattern.CASE_INSENSITIVE);
            for (final GroupUser user : GroupUsersToFilter) {
                if (pattern.matcher(user.getDisplayname()).find()) {
                    filteredGroupUsers.add(user);
                }
            }
            return filteredGroupUsers;
        } else {
            return GroupUsersToFilter;
        }
    }

    /**
     * Filter the room list according to the given pattern
     *
     * @param constraint the constraint
     */
    private void filterGroupUsers(CharSequence constraint) {
        mFilteredGroupUsers.clear();
        mFilteredGroupUsers.addAll(getFilteredGroupUsers(mGroupUsers, constraint));
    }

    /*
     * *********************************************************************************************
     * Listeners
     * *********************************************************************************************
     */

    public interface OnSelectGroupUserListener {
        void onSelectGroupUser(GroupUser groupUser, int position);
    }
}
