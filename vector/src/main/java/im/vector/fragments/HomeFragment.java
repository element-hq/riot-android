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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomAccountData;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
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

    @BindView(R.id.historical_section)
    HomeSectionView mHistoricalSection;

    private List<HomeSectionView> mHomeSectionViews;

    private final MXEventListener mEventsListener = new MXEventListener() {
        //TODO
    };

    private List<AsyncTask> mSortingAsyncTasks = new ArrayList<>();

    private AlertDialog mFabDialog;

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
        for (HomeSectionView homeSectionView : mHomeSectionViews) {
            homeSectionView.setCurrentFilter(mCurrentFilter);
        }

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
        if (mFabDialog != null) {
            // Prevent leak after orientation changed
            mFabDialog.dismiss();
            mFabDialog = null;
        }
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
        // ignore any action if there is a pending one
        if (!mActivity.isWaitingViewVisible()) {
            CharSequence items[] = new CharSequence[]{getString(R.string.room_recents_start_chat), getString(R.string.room_recents_create_room)};
            mFabDialog = new AlertDialog.Builder(mActivity)
                    .setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int n) {
                            d.cancel();
                            if (0 == n) {
                                mActivity.invitePeopleToNewRoom();
                            } else {
                                mActivity.createRoom();
                            }
                        }
                    })
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mActivity.invitePeopleToNewRoom();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    @Override
    protected List<Room> getRooms() {
        return new ArrayList<>();
    }

    @Override
    protected void onFilter(String pattern, final OnFilterListener listener) {
        for (HomeSectionView homeSectionView : mHomeSectionViews) {
            homeSectionView.onFilter(pattern, listener);
        }
    }

    @Override
    protected void onResetFilter() {
        for (HomeSectionView homeSectionView : mHomeSectionViews) {
            homeSectionView.onFilter("", null);
        }
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
                R.layout.adapter_item_room_invite, false, this, this, null);

        // Favourites
        mFavouritesSection.setTitle(R.string.bottom_action_favourites);
        mFavouritesSection.setHideIfEmpty(true);
        mFavouritesSection.setPlaceholders(null, getString(R.string.no_result_placeholder));
        mFavouritesSection.setupRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_room_view, true, this, null, null);

        // People
        mDirectChatsSection.setTitle(R.string.bottom_action_people);
        mDirectChatsSection.setPlaceholders(getString(R.string.no_conversation_placeholder), getString(R.string.no_result_placeholder));
        mDirectChatsSection.setupRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_room_view, true, this, null, null);

        // Rooms
        mRoomsSection.setTitle(R.string.bottom_action_rooms);
        mRoomsSection.setPlaceholders(getString(R.string.no_room_placeholder), getString(R.string.no_result_placeholder));
        mRoomsSection.setupRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_room_view, true, this, null, null);

        // Low priority
        mLowPrioritySection.setTitle(R.string.low_priority_header);
        mLowPrioritySection.setHideIfEmpty(true);
        mLowPrioritySection.setPlaceholders(null, getString(R.string.no_result_placeholder));
        mLowPrioritySection.setupRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_room_view, true, this, null, null);

        // Historical
        mHistoricalSection.setTitle(R.string.historical_header);
        mHistoricalSection.setHideIfEmpty(false);
        mHistoricalSection.setupRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_room_view, true, this, null, null);

        if (!mSession.getDataHandler().areLeftRoomsSynced() && !mSession.getDataHandler().isRetrievingLeftRooms()) {
            mHistoricalSection.setPlaceholders(getString(R.string.load_history_placeholder), getString(R.string.load_history_placeholder));
            mHistoricalSection.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    retrieveRoomHistory();
                }
            });
        } else {
            mHistoricalSection.setPlaceholders(getString(R.string.no_history_placeholder), getString(R.string.no_result_placeholder));
        }

        mHomeSectionViews = Arrays.asList(mInvitationsSection, mFavouritesSection, mDirectChatsSection,
                mRoomsSection, mLowPrioritySection, mHistoricalSection);
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
        // Main sections
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

        sortAndDisplay(favourites, favComparator, mFavouritesSection);
        sortAndDisplay(directChats, dateComparator, mDirectChatsSection);
        sortAndDisplay(lowPriorities, lowPriorityComparator, mLowPrioritySection);
        sortAndDisplay(otherRooms, dateComparator, mRoomsSection);

        mInvitationsSection.setRooms(mActivity.getRoomInvitations());

        // History
        initHistoryData(RoomUtils.getRoomsDateComparator(mSession, false));

        mActivity.stopWaitingView();
    }

    /**
     * Init history rooms data
     *
     * @param roomsComparator
     */
    private void initHistoryData(final Comparator<Room> roomsComparator) {
        final List<Room> historicalRooms = new ArrayList<>(mSession.getDataHandler().getLeftRooms());
        for (Iterator<Room> iterator = historicalRooms.iterator(); iterator.hasNext(); ) {
            final Room room = iterator.next();
            if (room.isConferenceUserRoom()) {
                iterator.remove();
            }
        }
        if (roomsComparator != null) {
            sortAndDisplay(historicalRooms, roomsComparator, mHistoricalSection);
        } else {
            mHistoricalSection.setRooms(historicalRooms);
        }
    }

    /**
     * Sort the given room list with the given comparator then attach it to the given adapter
     *
     * @param rooms
     * @param comparator
     * @param section
     */
    public void sortAndDisplay(final List<Room> rooms, final Comparator comparator, final HomeSectionView section) {
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
                section.setRooms(rooms);
                mSortingAsyncTasks.remove(this);
            }
        };
        mSortingAsyncTasks.add(task);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Start history retrieval
     */
    private void retrieveRoomHistory() {
        mHistoricalSection.setPlaceholders(getString(R.string.loading_placeholder), getString(R.string.load_history_placeholder));
        mSession.getDataHandler().retrieveLeftRooms(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onHistoryRetrieved();
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {

            }

            @Override
            public void onMatrixError(MatrixError e) {

            }

            @Override
            public void onUnexpectedError(Exception e) {

            }
        });
    }

    /**
     * Update UI after history has been retrieved
     */
    private void onHistoryRetrieved() {
        initHistoryData(RoomUtils.getRoomsDateComparator(mSession, false));
        mHistoricalSection.setPlaceholders(getString(R.string.no_history_placeholder), getString(R.string.no_result_placeholder));
        mHistoricalSection.setOnClickListener(null);
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

    @Override
    public void onLongClickRoom(View v, Room room, int position) {
        // User clicked on the "more actions" area
        final Set<String> tags = room.getAccountData().getKeys();
        final boolean isFavorite = tags != null && tags.contains(RoomTag.ROOM_TAG_FAVOURITE);
        final boolean isLowPriority = tags != null && tags.contains(RoomTag.ROOM_TAG_LOW_PRIORITY);
        RoomUtils.displayPopupMenu(getActivity(), mSession, room, v, isFavorite, isLowPriority, this);
    }

}
