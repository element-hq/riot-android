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

package im.vector.fragments;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomAccountData;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.listeners.MXEventListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import butterknife.BindView;
import im.vector.R;
import im.vector.adapters.HomeRoomAdapter;
import im.vector.util.RoomUtils;
import im.vector.view.HomeSectionView;

public class HomeFragment extends AbsHomeFragment implements HomeRoomAdapter.OnSelectRoomListener {
    private static final String LOG_TAG = HomeFragment.class.getSimpleName();

    @BindView(R.id.invitations_section)
    HomeSectionView mInvitationsSection;

    @BindView(R.id.favourites_section)
    HomeSectionView mFavouritesSection;

    @BindView(R.id.direct_chats_section)
    HomeSectionView mDirectChatsSection;

    @BindView(R.id.rooms_section)
    HomeSectionView mRoomsSection;

    @BindView(R.id.low_priority_section)
    HomeSectionView mLowPrioritySection;

    private HomeRoomAdapter mInvitationsAdapter;
    private HomeRoomAdapter mFavouritesAdapter;
    private HomeRoomAdapter mPeopleAdapter;
    private HomeRoomAdapter mRoomsAdapter;
    private HomeRoomAdapter mLowPriorityAdapter;

    private final MXEventListener mEventsListener = new MXEventListener() {
        //TODO
    };

    private List<AsyncTask> mSortingAsyncTasks = new ArrayList<>();

    /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static HomeFragment newInstance() {
        return new HomeFragment();
    }

    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initViews();

        // Eventually restore the pattern of adapter after orientation change
        mFavouritesAdapter.onFilterDone(mCurrentFilter);

        mActivity.showWaitingView();
    }

    @Override
    public void onResume() {
        super.onResume();
        mSession.getDataHandler().addListener(mEventsListener);
        initData();
    }

    @Override
    public void onPause() {
        super.onPause();
        mSession.getDataHandler().removeListener(mEventsListener);
    }

    @Override
    public void onStop() {
        super.onStop();

        // Cancel running async tasks to prevent memory leaks
        for (AsyncTask asyncTask : mSortingAsyncTasks) {
            asyncTask.cancel(true);
        }
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected void onFloatingButtonClick() {
        //TODO
    }

    @Override
    protected List<Room> getRooms() {
        return new ArrayList<>();
    }

    @Override
    protected void onFilter(String pattern, final OnFilterListener listener) {
        mFavouritesAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                listener.onFilterDone(count);
            }
        });
    }

    @Override
    protected void onResetFilter() {
        mFavouritesAdapter.getFilter().filter("", new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                Log.i(LOG_TAG, "onResetFilter " + count);
            }
        });
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {
        // Invitations
        mInvitationsSection.setTitle(R.string.invitations_header);
        mInvitationsSection.setHideIfEmpty(true);
        mInvitationsSection.setPlaceholders(null, getString(R.string.no_result_placeholder));
        mInvitationsSection.setupRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false),
                R.layout.adapter_item_room_invite, false, this, this, this);
        mInvitationsAdapter = mInvitationsSection.getAdapter();

        // Favourites
        mFavouritesSection.setTitle(R.string.bottom_action_favourites);
        mFavouritesSection.setHideIfEmpty(true);
        mFavouritesSection.setPlaceholders(null, getString(R.string.no_result_placeholder));
        mFavouritesSection.setupRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_room_view, true, this, null, this);
        mFavouritesAdapter = mFavouritesSection.getAdapter();

        // People
        mDirectChatsSection.setTitle(R.string.bottom_action_people);
        mDirectChatsSection.setPlaceholders(getString(R.string.no_conversation_placeholder), getString(R.string.no_result_placeholder));
        mDirectChatsSection.setupRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_room_view, true, this, null, this);
        mPeopleAdapter = mDirectChatsSection.getAdapter();

        // Rooms
        mRoomsSection.setTitle(R.string.bottom_action_rooms);
        mRoomsSection.setPlaceholders(getString(R.string.no_room_placeholder), getString(R.string.no_result_placeholder));
        mRoomsSection.setupRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_room_view, true, this, null, this);
        mRoomsAdapter = mRoomsSection.getAdapter();

        // Low priority
        mLowPrioritySection.setTitle(R.string.low_priority_header);
        mLowPrioritySection.setHideIfEmpty(true);
        mLowPrioritySection.setPlaceholders(null, getString(R.string.no_result_placeholder));
        mLowPrioritySection.setupRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_room_view, false, this, null, this);
        mLowPriorityAdapter = mLowPrioritySection.getAdapter();

        //TODO solution to calculate the number of low priority rooms that can be displayed when we arrive on the screen + load rest while scrolling
    }

    @Override
    public void onSummariesUpdate() {
        if (!mActivity.isWaitingViewVisible()) {
            initData();
        }
    }

    /*
     * *********************************************************************************************
     * Data management
     * *********************************************************************************************
     */

    /**
     * Init the rooms data
     */
    private void initData() {
        final List<Room> favourites = new ArrayList<>();
        final List<Room> directChats = new ArrayList<>();
        final List<Room> lowPriorities = new ArrayList<>();
        final List<Room> otherRooms = new ArrayList<>();

        final Collection<Room> roomCollection = mSession.getDataHandler().getStore().getRooms();
        final List<String> directChatIds = mSession.getDirectChatRoomIdsList();

        for (Room room : roomCollection) {
            if (!room.isConferenceUserRoom()) {
                final RoomAccountData accountData = room.getAccountData();
                final Set<String> tags = new HashSet<>();

                if (accountData != null && accountData.hasTags()) {
                    tags.addAll(accountData.getKeys());
                }

                if (tags.contains(RoomTag.ROOM_TAG_FAVOURITE)) {
                    favourites.add(room);
                } else if (tags.contains(RoomTag.ROOM_TAG_LOW_PRIORITY)) {
                    lowPriorities.add(room);
                } else if (directChatIds.contains(room.getRoomId())) {
                    directChats.add(room);
                } else {
                    otherRooms.add(room);
                }
            }
        }

        Comparator<Room> favComparator = RoomUtils.getTaggedRoomComparator(mSession.roomIdsWithTag(RoomTag.ROOM_TAG_FAVOURITE));
        Comparator<Room> lowPriorityComparator = RoomUtils.getTaggedRoomComparator(mSession.roomIdsWithTag(RoomTag.ROOM_TAG_LOW_PRIORITY));
        Comparator<Room> dateComparator = RoomUtils.getRoomsDateComparator(mSession, false);

        sortAndDisplay(favourites, favComparator, mFavouritesAdapter);
        sortAndDisplay(directChats, dateComparator, mPeopleAdapter);
        sortAndDisplay(lowPriorities, lowPriorityComparator, mLowPriorityAdapter);
        sortAndDisplay(otherRooms, dateComparator, mRoomsAdapter);

        mActivity.stopWaitingView();

        mInvitationsAdapter.setRooms(mActivity.getRoomInvitations());
    }

    /**
     * Sort the given room list with the given comparator then attach it to the given adapter
     *
     * @param rooms
     * @param comparator
     * @param adapter
     */
    public void sortAndDisplay(final List<Room> rooms, final Comparator comparator, final HomeRoomAdapter adapter) {
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                if (!isCancelled()) {
                    Collections.sort(rooms, comparator);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void args) {
                adapter.setRooms(rooms);
                mSortingAsyncTasks.remove(this);
            }
        };
        mSortingAsyncTasks.add(task);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /*
     * *********************************************************************************************
     * Listeners
     * *********************************************************************************************
     */

    @Override
    public void onSelectRoom(Room room, int position) {
        openRoom(room);
    }

}
