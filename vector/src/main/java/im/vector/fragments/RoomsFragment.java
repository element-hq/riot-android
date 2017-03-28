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
import android.support.v4.widget.NestedScrollView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Toast;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import im.vector.PublicRoomsManager;
import im.vector.R;
import im.vector.adapters.AbsListAdapter;
import im.vector.adapters.PublicRoomAdapter;

public class RoomsFragment extends AbsHomeFragment {

    private static final String LOG_TAG = PeopleFragment.class.getSimpleName();

    @BindView(R.id.room_directory_recycler_view)
    RecyclerView mRoomDirectoryRecyclerView;

    @BindView(R.id.nested_scroll_view)
    NestedScrollView mNestedScrollView;

    private PublicRoomAdapter mPublicRoomAdapter;
    private List<PublicRoom> mPublicRooms = new ArrayList<>();

    /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static RoomsFragment newInstance() {
        return new RoomsFragment();
    }

    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rooms, container, false);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initViews();

        if (savedInstanceState != null) {
            // Restore adapter items
        }
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
    protected void onFilter(String pattern, OnFilterListener listener) {
        Toast.makeText(getActivity(), "room onFilter "+pattern, Toast.LENGTH_SHORT).show();
        //TODO adapter getFilter().filter(pattern, listener)
        //TODO call listener.onFilterDone(); when complete
    }

    @Override
    protected void onResetFilter() {

    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {

        mRoomDirectoryRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mRoomDirectoryRecyclerView.setHasFixedSize(true);

        //mRoomDirectoryRecyclerView.addItemDecoration(dividerItemDecoration);
        mPublicRoomAdapter = new PublicRoomAdapter(getActivity(), new AbsListAdapter.OnSelectItemListener<PublicRoom>() {
            @Override
            public void onSelectItem(PublicRoom room, int position) {
                //onRoomSelected(room, position);
            }
        });

        mPublicRoomAdapter.setItems(mPublicRooms);
        mRoomDirectoryRecyclerView.setAdapter(mPublicRoomAdapter);
        initPublicRooms();
    }


    /*
     * *********************************************************************************************
     * Public rooms management
     * *********************************************************************************************
     */

    private boolean mIsForwardPaginating = false;
    private int mItemHeight = -1;

    private final RecyclerView.OnScrollListener mPublicRoomScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);

            /*LinearLayoutManager layoutManager = (LinearLayoutManager)recyclerView.getLayoutManager();
            int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();


            if (-1 == mItemHeight) {
                mItemHeight = (mRoomDirectoryRecyclerView.getMeasuredHeight() + mRoomDirectoryRecyclerView.getChildCount() - 1) / recyclerView.getLayoutManager().getChildCount();
            }

            int[] location = {0,0};
            mRoomDirectoryRecyclerView.getLocationOnScreen(location);

            int firstPosition = 0;

            int offsetY = location[1] - mNestedScrollView.getScrollY();

            if (offsetY < 0) {
                firstPosition = (-offsetY) / mItemHeight;
            }

            int count = (mNestedScrollView.getHeight() + mItemHeight - 1) / mItemHeight;

            int lastVisibleItemPosition = firstPosition + count;

            Log.e(LOG_TAG, "DTC :  " + firstPosition);*/

            // detect if the last visible item is going to be displayed
            /*if (!mIsForwardPaginating && (lastVisibleItemPosition != RecyclerView.NO_POSITION) && (lastVisibleItemPosition >= (recyclerView.getAdapter().getItemCount() - 10)))  {
                forwardPaginate();
            }*/
        }
    };

    private void initPublicRooms() {
        PublicRoomsManager.getInstance().startPublicRoomsSearch(null, null, false, "", new ApiCallback<List<PublicRoom>>() {
            @Override
            public void onSuccess(List<PublicRoom> publicRooms) {
                if (null != getActivity()) {
                    mPublicRoomAdapter.setItems(publicRooms);
                    addPublicRoomsListener();
                }
            }

            private void onError(String message) {
                if (null != getActivity()) {
                    Log.e(LOG_TAG, "## startPublicRoomsSearch() failed " + message);
                    Toast.makeText(getActivity(),message, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError(e.getLocalizedMessage());
            }
        });
    }


    private void addPublicRoomsListener() {
        mRoomDirectoryRecyclerView.addOnScrollListener(mPublicRoomScrollListener);
    }

    private void removePublicRoomsListener() {
        mRoomDirectoryRecyclerView.removeOnScrollListener(mPublicRoomScrollListener);
    }

    /**
     * Trigger a forward room pagination
     */
    private void forwardPaginate() {
        mIsForwardPaginating = PublicRoomsManager.getInstance().forwardPaginate(new ApiCallback<List<PublicRoom>>() {
            @Override
            public void onSuccess(List<PublicRoom> publicRooms) {
                if (null != getActivity()) {
                    List items = mPublicRoomAdapter.getItems();
                    items.addAll(publicRooms);

                    mPublicRoomAdapter.setItems(items);
                    // unplug the scroll listener if there is no more data to find
                    if (!PublicRoomsManager.getInstance().hasMoreResults()) {
                        removePublicRoomsListener();
                    }

                    mIsForwardPaginating = false;
                }
            }

            private void onError(String message) {
                if (null != getActivity()) {
                    Log.e(LOG_TAG, "## forwardPaginate() failed " + message);
                    Toast.makeText(getActivity(),message, Toast.LENGTH_SHORT).show();
                }

                mIsForwardPaginating = false;
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError(e.getLocalizedMessage());
            }
        });
    }
}
