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
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Filter;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.data.store.IMXStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import butterknife.BindView;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.AbsListAdapter;
import im.vector.adapters.RoomAdapter;
import im.vector.view.SimpleDividerItemDecoration;

public class FavouritesFragment extends AbsHomeFragment {

    @BindView(R.id.favorites_recycler_view)
    RecyclerView mFavoritesRecyclerView;

    @BindView(R.id.favorites_placeholder)
    TextView mFavoritesPlaceHolder;

    // rooms management
    private RoomAdapter mFavoritesAdapter;

    // the searched pattern
    private String mSearchedPattern;

    /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static FavouritesFragment newInstance() {
        return new FavouritesFragment();
    }

    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favourites, container, false);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initViews();

        if (savedInstanceState != null) {
            // Restore adapter items
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshFavorites();
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected void onMarkAllAsRead() {

    }

    @Override
    protected void onFilter(String pattern, final OnFilterListener listener) {
        if (!TextUtils.equals(mSearchedPattern, pattern)) {
            mSearchedPattern = pattern;

            mFavoritesAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
                @Override
                public void onFilterComplete(int count) {
                    updateRoomsDisplay(count);
                    listener.onFilterDone(count);
                }
            });
        }
    }

    @Override
    protected void onResetFilter() {
        mFavoritesAdapter.getFilter().filter("");
        updateRoomsDisplay(mFavoritesAdapter.getItemCount());
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {
        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);
        SimpleDividerItemDecoration dividerItemDecoration = new SimpleDividerItemDecoration(getActivity(), DividerItemDecoration.HORIZONTAL, margin);

        // favorites
        mFavoritesRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mFavoritesRecyclerView.setHasFixedSize(true);
        mFavoritesRecyclerView.setNestedScrollingEnabled(false);

        mFavoritesAdapter = new RoomAdapter(getActivity(), new AbsListAdapter.OnSelectItemListener<Room>() {
            @Override
            public void onSelectItem(Room room, int position) {
                onFavoriteSelected(room, position);
            }
        });
        mFavoritesRecyclerView.addItemDecoration(dividerItemDecoration);
        mFavoritesRecyclerView.setAdapter(mFavoritesAdapter);
    }

    /*
     * *********************************************************************************************
     * public rooms management
     * *********************************************************************************************
     */

    /**
     * Update the rooms display
     *
     * @param count the matched rooms count
     */
    private void updateRoomsDisplay(int count) {
        mFavoritesPlaceHolder.setVisibility((0 == count) && !TextUtils.isEmpty(mSearchedPattern) ? View.VISIBLE : View.GONE);
        mFavoritesRecyclerView.setVisibility((0 != count) ? View.VISIBLE : View.GONE);
    }

    /**
     * Init the rooms display
     */
    private void refreshFavorites() {
        final List<String> favouriteRoomIdList = mSession.roomIdsWithTag(RoomTag.ROOM_TAG_FAVOURITE);
        final List<Room> favRooms = new ArrayList<>(favouriteRoomIdList.size());

        if (0 != favouriteRoomIdList.size()) {
            IMXStore store = mSession.getDataHandler().getStore();
            List<RoomSummary> roomSummaries = new ArrayList<>(store.getSummaries());

            for (RoomSummary summary : roomSummaries) {
                if (favouriteRoomIdList.contains(summary.getRoomId())) {
                    Room room = store.getRoom(summary.getRoomId());

                    if (null != room) {
                        favRooms.add(room);
                    }
                }
            }

            Comparator<Room> favComparator = new Comparator<Room>() {
                public int compare(Room r1, Room r2) {
                    return favouriteRoomIdList.indexOf(r1.getRoomId()) - favouriteRoomIdList.indexOf(r2.getRoomId());
                }
            };

            Collections.sort(favRooms, favComparator);
        }

        mFavoritesAdapter.setItems(favRooms);
        updateRoomsDisplay(favRooms.size());
    }

    /**
     * Handle a room selection
     *
     * @param room     the room
     * @param position the room index in the list
     */
    private void onFavoriteSelected(Room room, int position) {
        final String roomId;
        // cannot join a leaving room
        if (room == null || room.isLeaving()) {
            roomId = null;
        } else {
            roomId = room.getRoomId();
        }

        if (roomId != null) {
            final RoomSummary roomSummary = mSession.getDataHandler().getStore().getSummary(roomId);

            if (null != roomSummary) {
                room.sendReadReceipt(null);

                // Reset the highlight
                if (roomSummary.setHighlighted(false)) {
                    mSession.getDataHandler().getStore().flushSummary(roomSummary);
                }
            }

            // Update badge unread count in case device is offline
            CommonActivityUtils.specificUpdateBadgeUnreadCount(mSession, getContext());

            // Launch corresponding room activity
            HashMap<String, Object> params = new HashMap<>();
            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
            params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);

            CommonActivityUtils.goToRoomPage(getActivity(), mSession, params);
        }

        // Refresh the adapter item
        mFavoritesAdapter.notifyItemChanged(position);
    }
}
