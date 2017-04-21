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
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.Matrix;
import im.vector.R;
import im.vector.util.StickySectionHelper;
import im.vector.util.VectorUtils;
import im.vector.view.SectionView;

public abstract class AbsAdapter extends RecyclerView.Adapter implements Filterable {

    private static final String LOG_TAG = AbsAdapter.class.getSimpleName();

    protected static final int TYPE_HEADER_DEFAULT = -1;

    protected static final int TYPE_ROOM_INVITATION = -2;

    protected static final int TYPE_ROOM = -3;

    // Helper handling the sticky view for each section
    private StickySectionHelper mStickySectionHelper;

    // List of sections with the position of their header view
    /// Ex <0, section 1 with 2 items>, <3, section 2>
    private List<Pair<Integer, AdapterSection>> mSections;

    protected CharSequence mCurrentFilterPattern;

    private final AdapterSection<Room> mInviteSection;

    private Filter mFilter;

    protected final Context mContext;
    protected final MXSession mSession;

    private final InvitationListener mInvitationListener;
    protected final MoreRoomActionListener mMoreActionListener;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    protected AbsAdapter(final Context context, final InvitationListener invitationListener, final MoreRoomActionListener moreActionListener) {
        mContext = context;
        mSession = Matrix.getInstance(context).getDefaultSession();
        mInvitationListener = invitationListener;
        mMoreActionListener = moreActionListener;

        registerAdapterDataObserver(new AdapterDataObserver());

        mSections = new ArrayList<>();

        mFilter = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                final FilterResults results = new FilterResults();

                String filterPattern = null;
                if (!TextUtils.isEmpty(constraint)) {
                    filterPattern = constraint.toString().trim();
                }

                results.count = applyFilter(filterPattern) + filterRooms(mInviteSection, filterPattern);

                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                mCurrentFilterPattern = constraint;
                updateSections();

                if (mStickySectionHelper != null) {
                    mStickySectionHelper.resetSticky(mSections);
                }
            }
        };

        mInviteSection = new AdapterSection<>(context.getString(R.string.room_recents_invites), -1, R.layout.adapter_item_room_view,
                TYPE_HEADER_DEFAULT, TYPE_ROOM_INVITATION, new ArrayList<Room>(), null);
        mInviteSection.setEmptyViewPlaceholder(null, context.getString(R.string.no_result_placeholder));
        mInviteSection.setIsHiddenWhenEmpty(true);
        addSection(mInviteSection);
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
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
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
                return new InvitationViewHolder(invitationView);
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
                        if (adapterSection.second.hideWhenEmpty() && adapterSection.second.getItems().isEmpty()) {
                            headerViewHolder.itemView.setVisibility(View.GONE);
                            headerViewHolder.itemView.getLayoutParams().height = 0;
                            headerViewHolder.itemView.requestLayout();
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
                final InvitationViewHolder invitationViewHolder = (InvitationViewHolder) viewHolder;
                final Room room = (Room) getItemForPosition(position);
                invitationViewHolder.populateViews(room);
                break;
            default:
                populateViewHolder(viewType, viewHolder, position);
        }
    }

    @Override
    public Filter getFilter() {
        return mFilter;
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
        mInviteSection.setItems(rooms, mCurrentFilterPattern);
        if (!TextUtils.isEmpty(mCurrentFilterPattern)) {
            filterRooms(mInviteSection, String.valueOf(mCurrentFilterPattern));
        }

        updateSections();
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
    public Object getItemForPosition(final int position) {
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
    protected void addSection(AdapterSection section) {
        addSection(section, -1);
    }

    /**
     * Add a section at the given index
     *
     * @param section
     * @param index
     */
    protected void addSection(AdapterSection section, int index) {
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
    protected void updateSections() {
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
    protected List<AdapterSection> getSections() {
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
    protected List<Pair<Integer, AdapterSection>> getSectionsArray() {
        return new ArrayList<>(mSections);
    }

    /**
     * Get the position of the header of the given section
     *
     * @param section
     * @return section header position
     */
    protected int getSectionHeaderPosition(final AdapterSection section) {
        for (Pair<Integer, AdapterSection> adapterSection : mSections) {
            if (adapterSection.second == section) {
                return adapterSection.first;
            }
        }
        return -1;
    }

    /**
     * Refresh data of a section
     */
    public void refreshSection(final AdapterSection section) {
        int startPos = getSectionHeaderPosition(section) + 1;
        notifyItemRangeChanged(startPos, startPos + section.getNbItems() - 1);
    }

    public void onFilterDone(CharSequence currentPattern) {
        mCurrentFilterPattern = currentPattern;
    }

    /**
     * Filter the given section (of rooms) with the given pattern
     *
     * @param section
     * @param pattern
     * @return nb of items matching the filter
     */
    protected int filterRooms(final AdapterSection<Room> section, final String pattern) {
        if (!TextUtils.isEmpty(pattern)) {
            List<Room> filteredRoom = new ArrayList<>();
            for (final Room room : section.getItems()) {

                final String roomName = VectorUtils.getRoomDisplayName(mContext, mSession, room);
                if (Pattern.compile(Pattern.quote(pattern), Pattern.CASE_INSENSITIVE)
                        .matcher(roomName)
                        .find()) {
                    filteredRoom.add(room);
                }
            }
            section.setFilteredItems(filteredRoom, pattern);
        } else {
            section.resetFilter();
        }
        return section.getFilteredItems().size();
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
            vSectionTitle.setText(item.getTitle());
        }

        public AdapterSection getSection() {
            return mSection;
        }
    }

    class InvitationViewHolder extends RoomViewHolder {

        @BindView(R.id.recents_invite_reject_button)
        Button vRejectButton;

        @BindView(R.id.recents_invite_preview_button)
        Button vPreViewButton;

        InvitationViewHolder(View itemView) {
            super(mContext, mSession, itemView, AbsAdapter.this.mMoreActionListener);
        }

        void populateViews(final Room room) {
            super.populateViews(room, room.isDirectChatInvitation(), true);

            vPreViewButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mInvitationListener) {
                        mInvitationListener.onPreviewRoom(mSession, room.getRoomId());
                    }
                }
            });

            vRejectButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mInvitationListener) {
                        mInvitationListener.onRejectInvitation(mSession, room.getRoomId());
                    }
                }
            });
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

    public interface InvitationListener {
        void onPreviewRoom(MXSession session, String roomId);

        void onRejectInvitation(MXSession session, String roomId);
    }

    public interface MoreRoomActionListener {
        void onMoreActionClick(View itemView, Room room);
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
