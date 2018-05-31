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

import org.matrix.androidsdk.rest.model.group.Group;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.List;

import im.vector.R;

public class GroupAdapter extends AbsAdapter {
    private static final String LOG_TAG = GroupAdapter.class.getSimpleName();

    private final AdapterSection<Group> mInvitedGroupsSection;
    private final AdapterSection<Group> mGroupsSection;

    private final OnGroupSelectItemListener mListener;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public GroupAdapter(final Context context, final OnGroupSelectItemListener listener, final GroupInvitationListener invitationListener, final MoreGroupActionListener moreActionListener) {
        super(context, invitationListener, moreActionListener);

        mListener = listener;

        mInvitedGroupsSection = new AdapterSection<>(context, context.getString(R.string.groups_invite_header), -1,
                R.layout.adapter_item_group_invite, TYPE_HEADER_DEFAULT, TYPE_GROUP_INVITATION, new ArrayList<Group>(), Group.mGroupsComparator);
        mInvitedGroupsSection.setEmptyViewPlaceholder(context.getString(R.string.no_group_placeholder), context.getString(R.string.no_result_placeholder));
        mInvitedGroupsSection.setIsHiddenWhenEmpty(true);

        mGroupsSection = new AdapterSection<>(context, context.getString(R.string.groups_header), -1,
                R.layout.adapter_item_group_view, TYPE_HEADER_DEFAULT, TYPE_GROUP, new ArrayList<Group>(), Group.mGroupsComparator);
        mGroupsSection.setEmptyViewPlaceholder(context.getString(R.string.no_group_placeholder), context.getString(R.string.no_result_placeholder));

        addSection(mInvitedGroupsSection);
        addSection(mGroupsSection);
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected RecyclerView.ViewHolder createSubViewHolder(ViewGroup viewGroup, int viewType) {
        Log.i(LOG_TAG, " onCreateViewHolder for viewType:" + viewType);
        final LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());

        switch (viewType) {
            case TYPE_GROUP:
                return new GroupViewHolder(inflater.inflate(R.layout.adapter_item_group_view, viewGroup, false));
            case TYPE_GROUP_INVITATION:
                return new GroupInvitationViewHolder(inflater.inflate(R.layout.adapter_item_group_invite, viewGroup, false));
        }

        return null;
    }

    @Override
    protected void populateViewHolder(int viewType, RecyclerView.ViewHolder viewHolder, int position) {
        View groupView = null;
        Group group = null;

        switch (viewType) {
            case TYPE_GROUP: {
                final GroupViewHolder groupViewHolder = (GroupViewHolder) viewHolder;
                group = (Group) getItemForPosition(position);
                groupViewHolder.populateViews(mContext, mSession, group, null, false, mMoreGroupActionListener);
                groupView = groupViewHolder.itemView;

                break;
            }
            case TYPE_GROUP_INVITATION: {
                final GroupInvitationViewHolder groupViewHolder = (GroupInvitationViewHolder) viewHolder;
                group = (Group) getItemForPosition(position);
                groupViewHolder.populateViews(mContext, mSession, group, mGroupInvitationListener, true, mMoreGroupActionListener);
                groupView = groupViewHolder.itemView;
                break;
            }
        }

        if (null != groupView) {
            final Group fGroup = group;

            groupView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.onSelectItem(fGroup, -1);
                }
            });

            groupView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return mListener.onLongPressItem(fGroup, -1);
                }
            });
        }
    }

    @Override
    protected int applyFilter(String pattern) {
        int nbResults = 0;

        nbResults += filterGroupSection(mInvitedGroupsSection, pattern);
        nbResults += filterGroupSection(mGroupsSection, pattern);

        return nbResults;
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    public void setGroups(final List<Group> groups) {
        mGroupsSection.setItems(groups, mCurrentFilterPattern);
        if (!TextUtils.isEmpty(mCurrentFilterPattern)) {
            filterGroupSection(mGroupsSection, String.valueOf(mCurrentFilterPattern));
        }
        updateSections();
    }

    public void setInvitedGroups(final List<Group> groups) {
        mInvitedGroupsSection.setItems(groups, mCurrentFilterPattern);
        if (!TextUtils.isEmpty(mCurrentFilterPattern)) {
            filterGroupSection(mInvitedGroupsSection, String.valueOf(mCurrentFilterPattern));
        }
        updateSections();
    }

    /*
     * *********************************************************************************************
     * Inner classes
     * *********************************************************************************************
     */

    public interface OnGroupSelectItemListener {
        void onSelectItem(Group item, int position);
        boolean onLongPressItem(Group item, int position);
    }
}
