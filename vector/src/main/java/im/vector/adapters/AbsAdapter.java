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
import android.support.annotation.IdRes;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;

import org.matrix.androidsdk.rest.model.group.Group;
import org.matrix.androidsdk.util.Log;

import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;
import im.vector.util.GroupUtils;
import im.vector.util.RoomUtils;
import im.vector.util.StickySectionHelper;
import im.vector.view.SectionView;

public abstract class AbsAdapter extends AbsFilterableAdapter {
    private static final String LOG_TAG = AbsAdapter.class.getSimpleName();

    static final int TYPE_HEADER_DEFAULT = -1;

    static final int TYPE_ROOM_INVITATION = -2;

    static final int TYPE_ROOM = -3;

    static final int TYPE_GROUP = -4;

    static final int TYPE_GROUP_INVITATION = -5;

    // Helper handling the sticky view for each section
    private StickySectionHelper mStickySectionHelper;

    // List of sections with the position of their header view
    /// Ex <0, section 1 with 2 items>, <3, section 2>
    private final List<Pair<Integer, AdapterSection>> mSections;

    private AdapterSection<Room> mInviteSection;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    AbsAdapter(final Context context, final RoomInvitationListener invitationListener, final MoreRoomActionListener moreActionListener) {
        super(context, invitationListener, moreActionListener);

        registerAdapterDataObserver(new AdapterDataObserver());

        mSections = new ArrayList<>();

        mInviteSection = new AdapterSection<>(context, context.getString(R.string.room_recents_invites), -1, R.layout.adapter_item_room_view,
                TYPE_HEADER_DEFAULT, TYPE_ROOM_INVITATION, new ArrayList<Room>(), null);
        mInviteSection.setEmptyViewPlaceholder(null, context.getString(R.string.no_result_placeholder));
        mInviteSection.setIsHiddenWhenEmpty(true);
        addSection(mInviteSection);
    }


    AbsAdapter(final Context context, final GroupInvitationListener invitationListener, final MoreGroupActionListener moreActionListener) {
        super(context, invitationListener, moreActionListener);
        registerAdapterDataObserver(new AdapterDataObserver());
        mSections = new ArrayList<>();
    }

    AbsAdapter(final Context context) {
        super(context);
        registerAdapterDataObserver(new AdapterDataObserver());
        mSections = new ArrayList<>();
    }

    /*
     * *********************************************************************************************
     * RecyclerView.Adapter methods
     * *********************************************************************************************
     */

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);

        mStickySectionHelper = new StickySectionHelper(recyclerView, mSections);
    }

    @Override
    public int getItemViewType(int position) {
        for (Pair<Integer, AdapterSection> section : mSections) {
            if (section.first == position) {
                return section.second.getHeaderViewType();
            } else if (position <= section.first + section.second.getNbItems()) {
                return section.second.getContentViewType();
            }
        }
        return 0;
    }

    @Override
    public int getItemCount() {
        final Pair<Integer, AdapterSection> lastSection = mSections.get(mSections.size() - 1); // each header is one item
        return lastSection.first + lastSection.second.getNbItems() + 1;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        Log.i(LOG_TAG, " onCreateViewHolder for viewType:" + viewType);
        final LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        switch (viewType) {
            case TYPE_HEADER_DEFAULT:
                View headerItemView = inflater.inflate(R.layout.adapter_section_header, viewGroup, false);
                return new HeaderViewHolder(headerItemView);
            case TYPE_ROOM_INVITATION:
                View invitationView = inflater.inflate(R.layout.adapter_item_room_invite, viewGroup, false);
                return new RoomInvitationViewHolder(invitationView);
            default:
                return createSubViewHolder(viewGroup, viewType);
        }
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder viewHolder, int position) {
        Log.i(LOG_TAG, " onBindViewHolder for position:" + position);
        final int viewType = getItemViewType(position);

        switch (viewType) {
            case TYPE_HEADER_DEFAULT:
                final HeaderViewHolder headerViewHolder = (HeaderViewHolder) viewHolder;
                for (Pair<Integer, AdapterSection> adapterSection : mSections) {
                    if (adapterSection.first == position) {
                        if (adapterSection.second.shouldBeHidden()) {
                            headerViewHolder.itemView.setVisibility(View.GONE);
                            headerViewHolder.itemView.getLayoutParams().height = 0;
                            headerViewHolder.itemView.requestLayout();
                            headerViewHolder.populateViews(null);
                        } else {
                            if (headerViewHolder.itemView.getVisibility() != View.VISIBLE) {
                                headerViewHolder.itemView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                                headerViewHolder.itemView.getLayoutParams().height = headerViewHolder.itemView.getMeasuredHeight();
                                headerViewHolder.itemView.setVisibility(View.VISIBLE);
                            }
                            headerViewHolder.populateViews(adapterSection.second);
                        }
                        break;
                    }
                }
                break;
            case TYPE_ROOM_INVITATION:
                final RoomInvitationViewHolder invitationViewHolder = (RoomInvitationViewHolder) viewHolder;
                final Room room = (Room) getItemForPosition(position);
                invitationViewHolder.populateViews(mContext, mSession, room, mRoomInvitationListener, mMoreRoomActionListener);
                break;
            default:
                populateViewHolder(viewType, viewHolder, position);
        }
    }

    @Override
    protected Filter createFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                final FilterResults results = new FilterResults();

                String filterPattern = null;
                if (!TextUtils.isEmpty(constraint)) {
                    filterPattern = constraint.toString().trim();
                }

                results.count = applyFilter(filterPattern) + filterRoomSection(mInviteSection, filterPattern);

                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                onFilterDone(constraint);
                updateSections();

                if (mStickySectionHelper != null) {
                    mStickySectionHelper.resetSticky(mSections);
                }
            }
        };
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    /**
     * Feed the adapter with room invites
     *
     * @param rooms
     */
    public void setInvitation(final List<Room> rooms) {
        if (null != mInviteSection) {
            mInviteSection.setItems(rooms, mCurrentFilterPattern);
            if (!TextUtils.isEmpty(mCurrentFilterPattern)) {
                filterRoomSection(mInviteSection, String.valueOf(mCurrentFilterPattern));
            }

            updateSections();
        }
    }

    /**
     * Get the section view at the given index
     * Used to add listeners on header sub view
     *
     * @param index
     * @return section view
     */
    public SectionView getSectionViewForSectionIndex(int index) {
        return mStickySectionHelper != null ? mStickySectionHelper.getSectionViewForSectionIndex(index) : null;
    }

    /**
     * Get a view from section views
     * Used to add listeners on header sub view
     *
     * @param viewId
     * @return view
     */
    public View findSectionSubViewById(@IdRes final int viewId) {
        if (mStickySectionHelper != null) {
            return mStickySectionHelper.findSectionSubViewById(viewId);
        }
        return null;
    }

    /**
     * Get the item at the given position in the adapter
     *
     * @param position
     * @return item at the given position
     */
    Object getItemForPosition(final int position) {
        for (int i = 0; i < mSections.size(); i++) {
            Pair<Integer, AdapterSection> section = mSections.get(i);
            if (position > section.first) {
                final int indexInSectionList = position - section.first - 1;
                if (indexInSectionList < section.second.getFilteredItems().size()) {
                    return section.second.getFilteredItems().get(indexInSectionList);
                }
            }
        }
        return null;
    }

    /**
     * Add a section
     *
     * @param section
     */
    void addSection(AdapterSection section) {
        addSection(section, -1);
    }

    /**
     * Add a section at the given index
     *
     * @param section
     * @param index
     */
    private void addSection(AdapterSection section, int index) {
        int headerPos = 0;
        if (mSections.size() > 0) {
            int prevPos = mSections.get(mSections.size() - 1).first;
            headerPos = 1 + prevPos + mSections.get(mSections.size() - 1).second.getNbItems();
        }
        if (index != -1) {
            mSections.add(index, new Pair<>(headerPos, section));
        } else {
            mSections.add(new Pair<>(headerPos, section));
        }
        Log.i(LOG_TAG, "New section " + section.getTitle() + ", header at " + headerPos + " with nbItem " + section.getNbItems());
    }

    /**
     * Notify that sections have changed and must be updated internally
     */
    void updateSections() {
        List<AdapterSection> list = getSections();
        mSections.clear();
        for (AdapterSection section : list) {
            addSection(section);
        }
        notifyDataSetChanged();
    }

    /**
     * @return the number of sections
     */
    public int getSectionsCount() {
        return mSections.size();
    }

    /**
     * Get the list of sections currently managed by the adapter
     *
     * @return list of sections
     */
    List<AdapterSection> getSections() {
        List<AdapterSection> list = new ArrayList<>();
        for (int i = 0; i < mSections.size(); i++) {
            AdapterSection section = mSections.get(i).second;
            list.add(section);
        }
        return list;
    }

    /**
     * Get the list of sections currently with their header position
     *
     * @return
     */
    List<Pair<Integer, AdapterSection>> getSectionsArray() {
        return new ArrayList<>(mSections);
    }

    /**
     * Get the position of the header of the given section
     *
     * @param section
     * @return section header position
     */
    int getSectionHeaderPosition(final AdapterSection section) {
        for (Pair<Integer, AdapterSection> adapterSection : mSections) {
            if (adapterSection.second == section) {
                return adapterSection.first;
            }
        }
        return -1;
    }

    /**
     * Filter the given section of rooms with the given pattern
     *
     * @param section
     * @param filterPattern
     * @return nb of items matching the filter
     */
    int filterRoomSection(final AdapterSection<Room> section, final String filterPattern) {
        if (null != section) {
            if (!TextUtils.isEmpty(filterPattern)) {
                List<Room> filteredRoom = RoomUtils.getFilteredRooms(mContext, mSession, section.getItems(), filterPattern);
                section.setFilteredItems(filteredRoom, filterPattern);
            } else {
                section.resetFilter();
            }
            return section.getFilteredItems().size();
        } else {
            return 0;
        }
    }

    /**
     * Filter the given section of groups with the given pattern
     *
     * @param section
     * @param filterPattern
     * @return nb of items matching the filter
     */
    int filterGroupSection(final AdapterSection<Group> section, final String filterPattern) {
        if (null != section) {
            if (!TextUtils.isEmpty(filterPattern)) {
                List<Group> filteredGroups = GroupUtils.getFilteredGroups(section.getItems(), filterPattern);
                section.setFilteredItems(filteredGroups, filterPattern);
            } else {
                section.resetFilter();
            }
            return section.getFilteredItems().size();
        } else {
            return 0;
        }
    }

    /*
     * *********************************************************************************************
     * ViewHolder
     * *********************************************************************************************
     */

    public class HeaderViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.section_title)
        TextView vSectionTitle;

        private AdapterSection mSection;

        HeaderViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        void populateViews(AdapterSection item) {
            mSection = item;
            vSectionTitle.setText(item != null ? item.getTitle() : null);
        }

        public AdapterSection getSection() {
            return mSection;
        }
    }

    /*
     * *********************************************************************************************
     * Inner class
     * *********************************************************************************************
     */

    private class AdapterDataObserver extends RecyclerView.AdapterDataObserver {

        private void resetStickyHeaders() {
            if (mStickySectionHelper != null) {
                mStickySectionHelper.resetSticky(mSections);
            }
        }

        @Override
        public void onChanged() {
            // Triggered by notifyDataSetChanged()
            resetStickyHeaders();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            resetStickyHeaders();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            resetStickyHeaders();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            resetStickyHeaders();
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            resetStickyHeaders();
        }
    }

    public interface RoomInvitationListener {
        void onPreviewRoom(MXSession session, String roomId);

        void onRejectInvitation(MXSession session, String roomId);
    }

    public interface GroupInvitationListener {
        void onJoinGroup(MXSession session, String groupId);

        void onRejectInvitation(MXSession session, String groupId);
    }

    public interface MoreRoomActionListener {
        void onMoreActionClick(View itemView, Room room);
    }

    public interface MoreGroupActionListener {
        void onMoreActionClick(View itemView, Group group);
    }

    /*
     * *********************************************************************************************
     * Abstract methods
     * *********************************************************************************************
     */

    protected abstract RecyclerView.ViewHolder createSubViewHolder(ViewGroup viewGroup, int viewType);

    protected abstract void populateViewHolder(int viewType, final RecyclerView.ViewHolder viewHolder, final int position);

    protected abstract int applyFilter(final String pattern);

}
