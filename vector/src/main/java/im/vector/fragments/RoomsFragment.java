/*
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
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
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.model.publicroom.PublicRoom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import im.vector.PublicRoomsManager;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.RoomDirectoryPickerActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.AdapterSection;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.RoomAdapter;
import im.vector.ui.themes.ThemeUtils;
import im.vector.util.HomeRoomsViewModel;
import im.vector.util.RoomDirectoryData;
import im.vector.view.EmptyViewItemDecoration;
import im.vector.view.SectionView;
import im.vector.view.SimpleDividerItemDecoration;

public class RoomsFragment extends AbsHomeFragment implements AbsHomeFragment.OnRoomChangedListener {
    private static final String LOG_TAG = RoomsFragment.class.getSimpleName();

    // activity result codes
    private static final int DIRECTORY_SOURCE_ACTIVITY_REQUEST_CODE = 314;

    //
    private static final String SELECTED_ROOM_DIRECTORY = "SELECTED_ROOM_DIRECTORY";

    // dummy spinner to select the public rooms
    private Spinner mPublicRoomsSelector;

    // estimated number of public rooms
    private Integer mEstimatedPublicRoomCount = null;

    @BindView(R.id.recyclerview)
    RecyclerView mRecycler;

    // rooms management
    private RoomAdapter mAdapter;

    // the selected room directory
    private RoomDirectoryData mSelectedRoomDirectory;

    // rooms list
    private List<Room> mRooms = new ArrayList<>();

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
    public int getLayoutResId() {
        return R.layout.fragment_rooms;
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPrimaryColor = ThemeUtils.INSTANCE.getColor(getActivity(), R.attr.vctr_tab_home);
        mSecondaryColor = ThemeUtils.INSTANCE.getColor(getActivity(), R.attr.vctr_tab_home_secondary);

        mFabColor = ContextCompat.getColor(getActivity(), R.color.tab_rooms);
        mFabPressedColor = ContextCompat.getColor(getActivity(), R.color.tab_rooms_secondary);

        initViews();

        mOnRoomChangedListener = this;

        mAdapter.onFilterDone(mCurrentFilter);

        if (savedInstanceState != null) {
            mSelectedRoomDirectory = (RoomDirectoryData) savedInstanceState.getSerializable(SELECTED_ROOM_DIRECTORY);
        }

        initPublicRooms(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        mAdapter.setInvitation(mActivity.getRoomInvitations());
        mRecycler.addOnScrollListener(mScrollListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        mEstimatedPublicRoomCount = null;
        mRecycler.removeOnScrollListener(mScrollListener);
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
    protected List<Room> getRooms() {
        return new ArrayList<>(mRooms);
    }

    @Override
    protected void onFilter(String pattern, final OnFilterListener listener) {
        mAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                Log.i(LOG_TAG, "onFilterComplete " + count);
                if (listener != null) {
                    listener.onFilterDone(count);
                }

                // trigger the public rooms search to avoid unexpected list refresh
                initPublicRooms(false);
            }
        });
    }

    @Override
    protected void onResetFilter() {
        mAdapter.getFilter().filter("", new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                Log.i(LOG_TAG, "onResetFilter " + count);

                // trigger the public rooms search to avoid unexpected list refresh
                initPublicRooms(false);
            }
        });
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    @Override
    public void onRoomResultUpdated(final HomeRoomsViewModel.Result result) {
        if (isResumed()) {
            mRooms = result.getOtherRoomsWithFavorites();
            mAdapter.setRooms(mRooms);
            mAdapter.setInvitation(mActivity.getRoomInvitations());
        }
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {
        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);
        mRecycler.setLayoutManager(new LinearLayoutManager(getActivity(), RecyclerView.VERTICAL, false));
        mRecycler.addItemDecoration(new SimpleDividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, margin));
        mRecycler.addItemDecoration(new EmptyViewItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, 40, 16, 14));

        mAdapter = new RoomAdapter(getActivity(), new RoomAdapter.OnSelectItemListener() {
            @Override
            public void onSelectItem(Room room, int position) {
                openRoom(room);
            }

            @Override
            public void onSelectItem(PublicRoom publicRoom) {
                onPublicRoomSelected(publicRoom);
            }

            @Override
            public void onSelectItem(ParticipantAdapterItem contact, int position) {
            }
        }, this, this);
        mRecycler.setAdapter(mAdapter);

        View spinner = mAdapter.findSectionSubViewById(R.id.public_rooms_selector);
        if (spinner != null && spinner instanceof Spinner) {
            mPublicRoomsSelector = (Spinner) spinner;
        }
    }

    /*
     * *********************************************************************************************
     * Public rooms management
     * *********************************************************************************************
     */

    // spinner text
    private ArrayAdapter<CharSequence> mRoomDirectoryAdapter;

    /**
     * Handle a public room selection
     *
     * @param publicRoom the public room
     */
    private void onPublicRoomSelected(final PublicRoom publicRoom) {
        // sanity check
        if (null != publicRoom.roomId) {
            final RoomPreviewData roomPreviewData = new RoomPreviewData(mSession, publicRoom.roomId, null, publicRoom.canonicalAlias, null);

            // Check whether the room exists to handled the cases where the user is invited or he has joined.
            // CAUTION: the room may exist whereas the user membership is neither invited nor joined.
            final Room room = mSession.getDataHandler().getRoom(publicRoom.roomId, false);
            if (null != room && room.isInvited()) {
                Log.d(LOG_TAG, "onPublicRoomSelected : the user is invited -> display the preview " + getActivity());
                CommonActivityUtils.previewRoom(getActivity(), roomPreviewData);
            } else if (null != room && room.isJoined()) {
                Log.d(LOG_TAG, "onPublicRoomSelected : the user joined the room -> open the room");
                final Map<String, Object> params = new HashMap<>();
                params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                params.put(VectorRoomActivity.EXTRA_ROOM_ID, publicRoom.roomId);

                if (!TextUtils.isEmpty(publicRoom.name)) {
                    params.put(VectorRoomActivity.EXTRA_DEFAULT_NAME, publicRoom.name);
                }

                if (!TextUtils.isEmpty(publicRoom.topic)) {
                    params.put(VectorRoomActivity.EXTRA_DEFAULT_TOPIC, publicRoom.topic);
                }

                CommonActivityUtils.goToRoomPage(getActivity(), mSession, params);
            } else {
                // Display a preview by default.
                Log.d(LOG_TAG, "onPublicRoomSelected : display the preview");
                mActivity.showWaitingView();

                roomPreviewData.fetchPreviewData(new ApiCallback<Void>() {
                    private void onDone() {
                        if (null != mActivity) {
                            mActivity.hideWaitingView();
                            CommonActivityUtils.previewRoom(getActivity(), roomPreviewData);
                        }
                    }

                    @Override
                    public void onSuccess(Void info) {
                        onDone();
                    }

                    private void onError() {
                        roomPreviewData.setPublicRoom(publicRoom);
                        roomPreviewData.setRoomName(publicRoom.name);
                        onDone();
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        onError();
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        onError();
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        onError();
                    }
                });
            }
        }
    }

    /**
     * Scroll events listener to forward paginate when it is required.
     */
    private final RecyclerView.OnScrollListener mPublicRoomScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            LinearLayoutManager layoutManager = (LinearLayoutManager) mRecycler.getLayoutManager();
            int lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition();

            // we load public rooms 20 by 20, when the 10th one becomes visible, starts loading the next 20
            SectionView sectionView = mAdapter.getSectionViewForSectionIndex(mAdapter.getSectionsCount() - 1);
            AdapterSection lastSection = sectionView != null ? sectionView.getSection() : null;

            if (null != lastSection) {
                // detect if the last visible item is inside another section
                for (int i = 0; i < mAdapter.getSectionsCount() - 1; i++) {
                    SectionView prevSectionView = mAdapter.getSectionViewForSectionIndex(i);

                    if ((null != prevSectionView) && (null != prevSectionView.getSection())) {
                        lastVisibleItemPosition -= prevSectionView.getSection().getNbItems();

                        // the item is in a previous section
                        if (lastVisibleItemPosition <= 0) {
                            return;
                        }
                    }
                }

                // trigger a forward paginate when there are only 10 items left
                if ((lastSection.getNbItems() - lastVisibleItemPosition) < 10) {
                    forwardPaginate();
                }
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

        if (mPublicRoomsSelector != null) {
            // reported by GA
            // https://stackoverflow.com/questions/26752974/adapterdatasetobserver-was-not-registered
            if (mRoomDirectoryAdapter != mPublicRoomsSelector.getAdapter()) {
                mPublicRoomsSelector.setAdapter(mRoomDirectoryAdapter);
            } else {
                mRoomDirectoryAdapter.notifyDataSetChanged();
            }

            mPublicRoomsSelector.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        startActivityForResult(RoomDirectoryPickerActivity.getIntent(getActivity(), mSession.getMyUserId()),
                                DIRECTORY_SOURCE_ACTIVITY_REQUEST_CODE);
                    }
                    return true;
                }
            });
        }

        mRoomDirectoryAdapter.add(mSelectedRoomDirectory.getDisplayName());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Activity.RESULT_OK == resultCode) {
            if (requestCode == DIRECTORY_SOURCE_ACTIVITY_REQUEST_CODE) {
                mSelectedRoomDirectory = (RoomDirectoryData) data.getSerializableExtra(RoomDirectoryPickerActivity.EXTRA_OUT_ROOM_DIRECTORY_DATA);
                mAdapter.setPublicRooms(new ArrayList<PublicRoom>());
                initPublicRooms(true);
            }
        }
    }

    /**
     * Display the public rooms loading view
     */
    private void showPublicRoomsLoadingView() {
        mAdapter.getSectionViewForSectionIndex(mAdapter.getSectionsCount() - 1).showLoadingView();
    }

    /**
     * Hide the public rooms loading view
     */
    private void hidePublicRoomsLoadingView() {
        mAdapter.getSectionViewForSectionIndex(mAdapter.getSectionsCount() - 1).hideLoadingView();
    }

    /**
     * Init the public rooms.
     *
     * @param displayOnTop true to display the public rooms in full screen
     */
    private void initPublicRooms(final boolean displayOnTop) {
        refreshDirectorySourceSpinner();

        showPublicRoomsLoadingView();

        mAdapter.setNoMorePublicRooms(false);

        if (null == mEstimatedPublicRoomCount) {
            final EventsRestClient eventsRestClient = mSession != null ? mSession.getEventsApiClient() : null;
            if (eventsRestClient == null) {
                hidePublicRoomsLoadingView();
                return;
            }
            eventsRestClient.getPublicRoomsCount(
                    mSelectedRoomDirectory.getHomeServer(),
                    mSelectedRoomDirectory.getThirdPartyInstanceId(),
                    mSelectedRoomDirectory.isIncludedAllNetworks(),
                    new ApiCallback<Integer>() {
                        private void onDone(int count) {
                            mEstimatedPublicRoomCount = count;
                            mAdapter.setEstimatedPublicRoomsCount(count);

                            // next step
                            initPublicRooms(displayOnTop);
                        }

                        @Override
                        public void onSuccess(Integer count) {
                            if (null != count) {
                                onDone(count);
                            } else {
                                onDone(-1);
                            }
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            Log.e(LOG_TAG, "## startPublicRoomsSearch() : getPublicRoomsCount failed " + e.getMessage(), e);
                            onDone(-1);
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            Log.e(LOG_TAG, "## startPublicRoomsSearch() : getPublicRoomsCount failed " + e.getMessage());
                            onDone(-1);
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            Log.e(LOG_TAG, "## startPublicRoomsSearch() : getPublicRoomsCount failed " + e.getMessage(), e);
                            onDone(-1);
                        }
                    }
            );
            return;
        }

        PublicRoomsManager.getInstance().startPublicRoomsSearch(mSelectedRoomDirectory.getHomeServer(),
                mSelectedRoomDirectory.getThirdPartyInstanceId(),
                mSelectedRoomDirectory.isIncludedAllNetworks(),
                mCurrentFilter, new ApiCallback<List<PublicRoom>>() {
                    @Override
                    public void onSuccess(List<PublicRoom> publicRooms) {
                        if (isAdded()) {
                            mAdapter.setNoMorePublicRooms(publicRooms.size() < PublicRoomsManager.PUBLIC_ROOMS_LIMIT);
                            mAdapter.setPublicRooms(publicRooms);
                            addPublicRoomsListener();

                            // trick to display the full public rooms list
                            if (displayOnTop) {
                                // wait that the list is refreshed
                                mRecycler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        SectionView publicSectionView = mAdapter.getSectionViewForSectionIndex(mAdapter.getSectionsCount() - 1);

                                        // simulate a click on the header is to display the full list
                                        if ((null != publicSectionView) && !publicSectionView.isStickyHeader()) {
                                            publicSectionView.callOnClick();
                                        }
                                    }
                                });
                            }

                            hidePublicRoomsLoadingView();
                        }
                    }

                    private void onError(String message) {
                        if (isAdded()) {
                            Log.e(LOG_TAG, "## startPublicRoomsSearch() failed " + message);
                            Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                            hidePublicRoomsLoadingView();
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
        if (PublicRoomsManager.getInstance().isRequestInProgress()) {
            return;
        }

        boolean isForwarding = PublicRoomsManager.getInstance().forwardPaginate(new ApiCallback<List<PublicRoom>>() {
            @Override
            public void onSuccess(final List<PublicRoom> publicRooms) {
                if (isAdded()) {
                    // unplug the scroll listener if there is no more data to find
                    if (!PublicRoomsManager.getInstance().hasMoreResults()) {
                        mAdapter.setNoMorePublicRooms(true);
                        removePublicRoomsListener();
                    }

                    mAdapter.addPublicRooms(publicRooms);
                    hidePublicRoomsLoadingView();
                }
            }

            private void onError(String message) {
                if (isAdded()) {
                    Log.e(LOG_TAG, "## forwardPaginate() failed " + message);
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();

                    hidePublicRoomsLoadingView();
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

        if (isForwarding) {
            showPublicRoomsLoadingView();
        } else {
            hidePublicRoomsLoadingView();
        }
    }

    /**
     * Add the public rooms listener
     */
    private void addPublicRoomsListener() {
        mRecycler.addOnScrollListener(mPublicRoomScrollListener);
    }

    /**
     * Remove the public rooms listener
     */
    private void removePublicRoomsListener() {
        mRecycler.removeOnScrollListener(mPublicRoomScrollListener);
    }

    /*
     * *********************************************************************************************
     * Listeners
     * *********************************************************************************************
     */

    @Override
    public void onToggleDirectChat(String roomId, boolean isDirectChat) {
    }

    @Override
    public void onRoomLeft(String roomId) {
    }

    @Override
    public void onRoomForgot(String roomId) {
        // there is no sync event when a room is forgotten
    }
}
