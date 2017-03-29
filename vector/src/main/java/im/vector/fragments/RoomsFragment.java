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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
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
import im.vector.activity.RoomDirectoryPickerActivity;
import im.vector.adapters.AbsListAdapter;
import im.vector.adapters.PublicRoomAdapter;
import im.vector.util.RoomDirectoryData;
import im.vector.view.SimpleDividerItemDecoration;

public class RoomsFragment extends AbsHomeFragment {
    private static final String LOG_TAG = PeopleFragment.class.getSimpleName();

    // activity result codes
    private static final int DIRECTORY_SOURCE_ACTIVITY_REQUEST_CODE = 314;

    //
    private static final String SELECTED_ROOM_DIRECTORY = "SELECTED_ROOM_DIRECTORY";

    @BindView(R.id.rooms_scroll_view)
    ScrollView mScrollView;

    @BindView(R.id.room_directory_recycler_view)
    RecyclerView mRoomDirectoryRecyclerView;

    @BindView(R.id.room_directory_loading_view)
    View mLoadingMoreDirectoryRooms;

    @BindView(R.id.public_rooms_selector)
    Spinner mPublicRoomsSelector;

    // public rooms management
    private PublicRoomAdapter mPublicRoomAdapter;
    private List<PublicRoom> mPublicRoomsList = new ArrayList<>();

    //
    private String mSearchedPattern;

    // the selected room directory
    private RoomDirectoryData mSelectedRoomDirectory;

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

        mSearchedPattern = null;
        initViews();

        if (savedInstanceState != null) {
            mSelectedRoomDirectory = (RoomDirectoryData)savedInstanceState.getSerializable(SELECTED_ROOM_DIRECTORY);
        }
        initPublicRooms();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // save the selected room directory
        outState.putSerializable(SELECTED_ROOM_DIRECTORY, mSelectedRoomDirectory);
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
        // check if there is an update
        if (!TextUtils.equals(mSearchedPattern, pattern)) {
            mSearchedPattern = pattern;
            initPublicRooms();
        }
    }

    @Override
    protected void onResetFilter() {
        mSearchedPattern = null;
        initPublicRooms();
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {
        // public rooms
        mRoomDirectoryRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mRoomDirectoryRecyclerView.setHasFixedSize(true);
        mRoomDirectoryRecyclerView.setNestedScrollingEnabled(false);

        //mRoomDirectoryRecyclerView.addItemDecoration(dividerItemDecoration);
        mPublicRoomAdapter = new PublicRoomAdapter(getActivity(), new AbsListAdapter.OnSelectItemListener<PublicRoom>() {
            @Override
            public void onSelectItem(PublicRoom room, int position) {
                //onRoomSelected(room, position);
            }
        });

        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);
        SimpleDividerItemDecoration dividerItemDecoration =
                new SimpleDividerItemDecoration(mRoomDirectoryRecyclerView.getContext(), DividerItemDecoration.HORIZONTAL, margin);
        mRoomDirectoryRecyclerView.addItemDecoration(dividerItemDecoration);

        mPublicRoomAdapter.setItems(mPublicRoomsList);
        mRoomDirectoryRecyclerView.setAdapter(mPublicRoomAdapter);
    }

    /*
     * *********************************************************************************************
     * Public rooms management
     * *********************************************************************************************
     */

    private ArrayAdapter<CharSequence> mRoomDirectoryAdapter;

    // Cell height
    private int mPublicCellHeight = -1;

    // tells if a forward pagination is in progress
    private boolean isForwardPaginating() {
        return View.VISIBLE == mLoadingMoreDirectoryRooms.getVisibility();
    }

    /**
     * Scroll events listener to forward paginate when it is required.
     */
    private final ScrollView.OnScrollChangeListener mPublicRoomScrollListener = new ScrollView.OnScrollChangeListener() {
        @Override
        public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
            LinearLayoutManager layoutManager = (LinearLayoutManager)mRoomDirectoryRecyclerView.getLayoutManager();

            int lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();

            // as a nestedScrollView is used, findLastVisibleItemPosition is not valid (always the last list item)
            if (0 != mRoomDirectoryRecyclerView.getChildCount()) {

                // compute the item height in pixels
                if (-1 == mPublicCellHeight) {
                    mPublicCellHeight = (mRoomDirectoryRecyclerView.getMeasuredHeight() + mRoomDirectoryRecyclerView.getChildCount() - 1) / mRoomDirectoryRecyclerView.getLayoutManager().getChildCount();
                }

                // compute the first item position
                int[] recyclerPosition = {0, 0};
                mRoomDirectoryRecyclerView.getLocationOnScreen(recyclerPosition);

                int firstPosition = 0;
                int offsetY = recyclerPosition[1];

                if (offsetY < 0) {
                    firstPosition = (-offsetY) / mPublicCellHeight;
                }

                // compute the last visible item position
                lastVisibleItemPosition = firstPosition + (mScrollView.getHeight() + mPublicCellHeight - 1) / mPublicCellHeight;
            }

            // detect if the last visible item is going to be displayed
            if (!isForwardPaginating() && (lastVisibleItemPosition != RecyclerView.NO_POSITION) && (lastVisibleItemPosition >= (mRoomDirectoryRecyclerView.getAdapter().getItemCount() - 10)))  {
                forwardPaginate();
            }
        }
    };

    /**
     * Refresh the directory source spinner
     */
    private void refreshDirectorySourceSpinner() {
        // no directory source, use the default one
        if (null == mSelectedRoomDirectory) {
            mSelectedRoomDirectory = RoomDirectoryData.getDefault();
        }

        if (null == mRoomDirectoryAdapter) {
            mRoomDirectoryAdapter = new ArrayAdapter<>(getActivity(), R.layout.public_room_spinner_item);
        } else {
            mRoomDirectoryAdapter.clear();
        }

        mPublicRoomsSelector.setAdapter(mRoomDirectoryAdapter);
        mPublicRoomsSelector.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startActivityForResult(RoomDirectoryPickerActivity.getIntent(getActivity(), mSession.getMyUserId()), DIRECTORY_SOURCE_ACTIVITY_REQUEST_CODE);
                }
                return true;
            }
        });

        mRoomDirectoryAdapter.add(mSelectedRoomDirectory.getDisplayName());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Activity.RESULT_OK == resultCode) {
            if (requestCode == DIRECTORY_SOURCE_ACTIVITY_REQUEST_CODE) {
                mSelectedRoomDirectory = (RoomDirectoryData)data.getSerializableExtra(RoomDirectoryPickerActivity.EXTRA_OUT_ROOM_DIRECTORY_DATA);

                initPublicRooms();
            }
        }
    }

    /**
     * Init the public rooms.
     */
    private void initPublicRooms() {
        refreshDirectorySourceSpinner();

        mLoadingMoreDirectoryRooms.setVisibility(View.VISIBLE);
        mRoomDirectoryRecyclerView.setVisibility(View.GONE);

        PublicRoomsManager.getInstance().startPublicRoomsSearch(mSelectedRoomDirectory.getServerUrl(),
                mSelectedRoomDirectory.getThirdPartyInstanceId(),
                mSelectedRoomDirectory.isIncludedAllNetworks(),
                mSearchedPattern, new ApiCallback<List<PublicRoom>>() {
            @Override
            public void onSuccess(List<PublicRoom> publicRooms) {
                if (null != getActivity()) {
                    mPublicRoomAdapter.setItems(publicRooms);
                    addPublicRoomsListener();

                    mLoadingMoreDirectoryRooms.setVisibility(View.GONE);
                    mRoomDirectoryRecyclerView.setVisibility(View.VISIBLE);
                }
            }

            private void onError(String message) {
                if (null != getActivity()) {
                    Log.e(LOG_TAG, "## startPublicRoomsSearch() failed " + message);
                    Toast.makeText(getActivity(),message, Toast.LENGTH_SHORT).show();

                    mLoadingMoreDirectoryRooms.setVisibility(View.GONE);
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

    /**
     * Trigger a forward room pagination
     */
    private void forwardPaginate() {
        boolean isForwarding = PublicRoomsManager.getInstance().forwardPaginate(new ApiCallback<List<PublicRoom>>() {
            @Override
            public void onSuccess(final List<PublicRoom> publicRooms) {
                if (null != getActivity()) {
                    mPublicRoomAdapter.addItems(publicRooms);

                    // unplug the scroll listener if there is no more data to find
                    if (!PublicRoomsManager.getInstance().hasMoreResults()) {
                        removePublicRoomsListener();
                    }

                    mLoadingMoreDirectoryRooms.setVisibility(View.GONE);
                }
            }

            private void onError(String message) {
                if (null != getActivity()) {
                    Log.e(LOG_TAG, "## forwardPaginate() failed " + message);
                    Toast.makeText(getActivity(),message, Toast.LENGTH_SHORT).show();
                }

                mLoadingMoreDirectoryRooms.setVisibility(View.GONE);
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

        mLoadingMoreDirectoryRooms.setVisibility(isForwarding ? View.VISIBLE : View.GONE);
    }

    /**
     * Add the public rooms listener
     */
    private void addPublicRoomsListener() {
        mScrollView.setOnScrollChangeListener(mPublicRoomScrollListener);
    }

    /**
     * Remove the public rooms listener
     */
    private void removePublicRoomsListener() {
    }
}
