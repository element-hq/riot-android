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

import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.widget.LinearLayoutManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.listeners.MXEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import butterknife.BindView;
import im.vector.R;
import im.vector.adapters.HomeRoomAdapter;
import im.vector.ui.themes.ThemeUtils;
import im.vector.util.HomeRoomsViewModel;
import im.vector.util.PreferencesManager;
import im.vector.util.RoomUtils;
import im.vector.view.HomeSectionView;

public class HomeFragment extends AbsHomeFragment implements HomeRoomAdapter.OnSelectRoomListener {
    private static final String LOG_TAG = HomeFragment.class.getSimpleName();

    @BindView(R.id.nested_scrollview)
    NestedScrollView mNestedScrollView;

    @BindView(R.id.invitations_section)
    HomeSectionView mInvitationsSection;

    @BindView(R.id.favourites_section)
    HomeSectionView mFavouritesSection;

    @BindView(R.id.direct_chats_section)
    HomeSectionView mDirectChatsSection;

    @BindView(R.id.server_notices_section)
    HomeSectionView mServerNoticesSection;

    @BindView(R.id.rooms_section)
    HomeSectionView mRoomsSection;

    @BindView(R.id.low_priority_section)
    HomeSectionView mLowPrioritySection;

    private List<HomeSectionView> mHomeSectionViews;

    private final MXEventListener mEventsListener = new MXEventListener() {
        //TODO
    };

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
    public int getLayoutResId() {
        return R.layout.fragment_home;
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPrimaryColor = ThemeUtils.INSTANCE.getColor(getActivity(), R.attr.vctr_tab_home);
        mSecondaryColor = ThemeUtils.INSTANCE.getColor(getActivity(), R.attr.vctr_tab_home_secondary);
        mFabColor = ContextCompat.getColor(getActivity(), R.color.tab_rooms);
        mFabPressedColor = ContextCompat.getColor(getActivity(), R.color.tab_rooms_secondary);

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
        if (null != mHomeSectionViews) {
            for (HomeSectionView homeSectionView : mHomeSectionViews) {
                homeSectionView.scrollToPosition(0);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mSession.getDataHandler().removeListener(mEventsListener);
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected List<Room> getRooms() {
        return new ArrayList<>(mSession.getDataHandler().getStore().getRooms());
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
        mInvitationsSection.setupRoomRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false),
                R.layout.adapter_item_room_invite, false, this, this, null);

        // Favourites
        mFavouritesSection.setTitle(R.string.bottom_action_favourites);
        mFavouritesSection.setHideIfEmpty(true);
        mFavouritesSection.setPlaceholders(null, getString(R.string.no_result_placeholder));
        mFavouritesSection.setupRoomRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_room_view, true, this, null, null);

        // People
        mDirectChatsSection.setTitle(R.string.bottom_action_people);
        mDirectChatsSection.setPlaceholders(getString(R.string.no_conversation_placeholder), getString(R.string.no_result_placeholder));
        mDirectChatsSection.setupRoomRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_room_view, true, this, null, null);

        // Rooms
        mRoomsSection.setTitle(R.string.bottom_action_rooms);
        mRoomsSection.setPlaceholders(getString(R.string.no_room_placeholder), getString(R.string.no_result_placeholder));
        mRoomsSection.setupRoomRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_room_view, true, this, null, null);

        // Low priority
        mLowPrioritySection.setTitle(R.string.low_priority_header);
        mLowPrioritySection.setHideIfEmpty(true);
        mLowPrioritySection.setPlaceholders(null, getString(R.string.no_result_placeholder));
        mLowPrioritySection.setupRoomRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_room_view, true, this, null, null);

        // Server notice
        mServerNoticesSection.setTitle(R.string.system_alerts_header);
        mServerNoticesSection.setHideIfEmpty(true);
        mServerNoticesSection.setPlaceholders(null, getString(R.string.no_result_placeholder));
        mServerNoticesSection.setupRoomRecyclerView(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false),
                R.layout.adapter_item_circular_room_view, true, this, null, null);

        mHomeSectionViews = Arrays.asList(mInvitationsSection,
                mFavouritesSection,
                mDirectChatsSection,
                mRoomsSection,
                mLowPrioritySection,
                mServerNoticesSection);

        // Add listeners to hide the floating button when needed
        final GestureDetectorCompat gestureDetector = new GestureDetectorCompat(mActivity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent event1, MotionEvent event2,
                                   float velocityX, float velocityY) {
                if (mActivity.getFloatingActionButton() != null
                        && mNestedScrollView.getBottom() > mActivity.getFloatingActionButton().getTop()) {
                    mActivity.hideFloatingActionButton(getTag());
                }
                return true;
            }
        });

        mNestedScrollView.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (null != mNestedScrollView) {
                    gestureDetector.onTouchEvent(event);
                    return mNestedScrollView.onTouchEvent(event);
                } else {
                    return false;
                }
            }
        });
        mNestedScrollView.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
            @Override
            public void onScrollChange(NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                mActivity.hideFloatingActionButton(getTag());
            }
        });
    }

    @Override
    public void onRoomResultUpdated(final HomeRoomsViewModel.Result result) {
        if (isResumed()) {
            refreshData(result);
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
    private void refreshData(final HomeRoomsViewModel.Result result) {
        final boolean pinMissedNotifications = PreferencesManager.pinMissedNotifications(getActivity());
        final boolean pinUnreadMessages = PreferencesManager.pinUnreadMessages(getActivity());
        final Comparator<Room> notificationComparator = RoomUtils.getNotifCountRoomsComparator(mSession, pinMissedNotifications, pinUnreadMessages);
        sortAndDisplay(result.getFavourites(), notificationComparator, mFavouritesSection);
        sortAndDisplay(result.getDirectChats(), notificationComparator, mDirectChatsSection);
        sortAndDisplay(result.getLowPriorities(), notificationComparator, mLowPrioritySection);
        sortAndDisplay(result.getOtherRooms(), notificationComparator, mRoomsSection);
        sortAndDisplay(result.getServerNotices(), notificationComparator, mServerNoticesSection);
        mActivity.hideWaitingView();
        mInvitationsSection.setRooms(mActivity.getRoomInvitations());
    }

    /**
     * Sort the given room list with the given comparator then attach it to the given adapter
     *
     * @param rooms
     * @param comparator
     * @param section
     */
    private void sortAndDisplay(final List<Room> rooms, final Comparator comparator, final HomeSectionView section) {
        try {
            Collections.sort(rooms, comparator);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## sortAndDisplay() failed " + e.getMessage(), e);
        }
        section.setRooms(rooms);
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
