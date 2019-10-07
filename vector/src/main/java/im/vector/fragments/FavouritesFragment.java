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
import android.text.TextUtils;
import android.view.View;
import android.widget.Filter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.data.comparator.RoomComparatorWithTag;
import org.matrix.androidsdk.listeners.MXEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import butterknife.BindView;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.adapters.HomeRoomAdapter;
import im.vector.ui.themes.ThemeUtils;
import im.vector.util.HomeRoomsViewModel;
import im.vector.util.RoomUtils;
import im.vector.view.EmptyViewItemDecoration;
import im.vector.view.SimpleDividerItemDecoration;

public class FavouritesFragment extends AbsHomeFragment implements HomeRoomAdapter.OnSelectRoomListener {
    private static final String LOG_TAG = FavouritesFragment.class.getSimpleName();

    @BindView(R.id.favorites_recycler_view)
    RecyclerView mFavoritesRecyclerView;

    @BindView(R.id.favorites_placeholder)
    TextView mFavoritesPlaceHolder;

    // rooms management
    private HomeRoomAdapter mFavoritesAdapter;

    // the favorite rooms list
    private List<Room> mFavorites = new ArrayList<>();

    // Touch helper to handle the drag and drop on items
    private ItemTouchHelper mDragAndDropTouchHelper;

    // detect i
    private final MXEventListener mEventsListener = new MXEventListener() {
        @Override
        public void onRoomTagEvent(String roomId) {
            if (mActivity.isWaitingViewVisible()) {
                onRoomTagUpdated(null);
            }
        }
    };

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
    public int getLayoutResId() {
        return R.layout.fragment_favourites;
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mPrimaryColor = ThemeUtils.INSTANCE.getColor(getActivity(), R.attr.vctr_tab_home);
        mSecondaryColor = ThemeUtils.INSTANCE.getColor(getActivity(), R.attr.vctr_tab_home_secondary);
        initViews();
        // Eventually restore the pattern of adapter after orientation change
        mFavoritesAdapter.onFilterDone(mCurrentFilter);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSession.getDataHandler().addListener(mEventsListener);
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
        return new ArrayList<>(mFavorites);
    }

    @Override
    protected void onFilter(String pattern, final OnFilterListener listener) {
        mFavoritesAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                updateRoomsDisplay(count);
                listener.onFilterDone(count);
            }
        });
    }

    @Override
    protected void onResetFilter() {
        mFavoritesAdapter.getFilter().filter("", new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                Log.i(LOG_TAG, "onResetFilter " + count);
                updateRoomsDisplay(mFavoritesAdapter.getItemCount());
            }
        });
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {
        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);

        // favorites
        mFavoritesRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), RecyclerView.VERTICAL, false));
        mFavoritesRecyclerView.setHasFixedSize(true);
        mFavoritesRecyclerView.setNestedScrollingEnabled(false);

        mFavoritesAdapter = new HomeRoomAdapter(getContext(), R.layout.adapter_item_room_view, this, null, this);

        mFavoritesRecyclerView.addItemDecoration(new SimpleDividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, margin));
        mFavoritesRecyclerView.addItemDecoration(new EmptyViewItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, 40, 16, 14));

        mFavoritesRecyclerView.setAdapter(mFavoritesAdapter);
        initFavoritesDragDrop();
    }

    @Override
    public void onRoomResultUpdated(final HomeRoomsViewModel.Result result) {
        if (isResumed() && !VectorApp.isSessionSyncing(mSession)) {
            mFavorites = result.getFavourites();
            Collections.sort(mFavorites, new RoomComparatorWithTag(RoomTag.ROOM_TAG_FAVOURITE));
            mFavoritesAdapter.setRooms(mFavorites);
            updateRoomsDisplay(mFavorites.size());
            mDragAndDropTouchHelper.attachToRecyclerView(mFavorites.size() > 1 ? mFavoritesRecyclerView : null);
        }
    }

    /*
     * *********************************************************************************************
     * favorites rooms management
     * *********************************************************************************************
     */

    /**
     * Update the rooms display
     *
     * @param count the matched rooms count
     */
    private void updateRoomsDisplay(int count) {
        // theses both fields should never be null but a crash was reported by GA.
        if (null != mFavoritesPlaceHolder) {
            mFavoritesPlaceHolder.setVisibility(0 == count ? View.VISIBLE : View.GONE);
        }

        if (null != mFavoritesRecyclerView) {
            mFavoritesRecyclerView.setVisibility(0 != count ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Init the drag and drop management
     */
    private void initFavoritesDragDrop() {
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            private int mFromPosition = -1;
            private String mRoomId;
            private int mToPosition = -1;

            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                // do not allow the drag and drop if there is a pending search
                return makeMovementFlags(!TextUtils.isEmpty(mCurrentFilter) ? 0 : (ItemTouchHelper.UP | ItemTouchHelper.DOWN), 0);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();

                if (-1 == mFromPosition) {
                    mFromPosition = fromPosition;
                    mRoomId = mFavoritesAdapter.getRoom(mFromPosition).getRoomId();
                }

                mToPosition = target.getAdapterPosition();

                mFavoritesAdapter.notifyItemMoved(mFromPosition, mToPosition);
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);

                if ((mFromPosition >= 0) && (mToPosition >= 0)) {
                    Log.d(LOG_TAG, "## initFavoritesDragDrop() : move room id " + mRoomId + " from " + mFromPosition + " to " + mToPosition);

                    // compute the new tag order
                    Double tagOrder = mSession.tagOrderToBeAtIndex(mToPosition, mFromPosition, RoomTag.ROOM_TAG_FAVOURITE);

                    // show a spinner
                    mActivity.showWaitingView();

                    RoomUtils.updateRoomTag(mSession, mRoomId, tagOrder, RoomTag.ROOM_TAG_FAVOURITE, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            // wait the room tag echo
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            onRoomTagUpdated(e.getLocalizedMessage());
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            onRoomTagUpdated(e.getLocalizedMessage());
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            onRoomTagUpdated(e.getLocalizedMessage());
                        }
                    });
                }

                mFromPosition = -1;
                mToPosition = -1;
            }
        };

        mDragAndDropTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
    }

    /**
     * A room tag has been updated
     *
     * @param errorMessage the error message if any.
     */
    private void onRoomTagUpdated(String errorMessage) {
        mActivity.hideWaitingView();
        if (!TextUtils.isEmpty(errorMessage)) {
            Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
        }
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

    }
}
