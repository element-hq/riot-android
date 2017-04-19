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
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Spinner;
import android.widget.Toast;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import butterknife.BindView;
import im.vector.PublicRoomsManager;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.RoomDirectoryPickerActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.AdapterSection;
import im.vector.adapters.RoomAdapter;
import im.vector.contacts.ContactsManager;
import im.vector.util.RoomDirectoryData;
import im.vector.view.EmptyViewItemDecoration;
import im.vector.view.SectionView;
import im.vector.view.SimpleDividerItemDecoration;

public class RoomsFragment extends AbsHomeFragment implements AbsHomeFragment.OnRoomChangedListener {
    private static final String LOG_TAG = PeopleFragment.class.getSimpleName();

    // activity result codes
    private static final int DIRECTORY_SOURCE_ACTIVITY_REQUEST_CODE = 314;

    //
    private static final String SELECTED_ROOM_DIRECTORY = "SELECTED_ROOM_DIRECTORY";

    Spinner mPublicRoomsSelector;

    @BindView(R.id.recyclerview)
    RecyclerView mRecycler;

    @BindView(R.id.room_directory_loading_view)
    View mLoadingMoreDirectoryRooms;

    // rooms management
    private RoomAdapter mAdapter;

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

        initViews();

        mOnRoomChangedListener = this;

        mAdapter.onFilterDone(mCurrentFilter);

        if (savedInstanceState != null) {
            mSelectedRoomDirectory = (RoomDirectoryData) savedInstanceState.getSerializable(SELECTED_ROOM_DIRECTORY);
        }

        initPublicRooms();
    }

    @Override
    public void onResume() {
        super.onResume();

        refreshRooms();

        mAdapter.setInvitation(mActivity.getRoomInvitations());

        mRecycler.addOnScrollListener(mScrollListener);
    }

    @Override
    public void onPause() {
        super.onPause();

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
    protected void onMarkAllAsRead() {

    }

    @Override
    protected void onFloatingButtonClick() {
        mActivity.createRoom();
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
            }
        });
    }

    @Override
    protected void onResetFilter() {
        mAdapter.getFilter().filter("", new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                Log.i(LOG_TAG, "onResetFilter " + count);
            }
        });
    }


    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    @Override
    public void onSummariesUpdate() {
        super.onSummariesUpdate();

        refreshRooms();

        mAdapter.setInvitation(mActivity.getRoomInvitations());
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {
        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);
        mRecycler.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mRecycler.addItemDecoration(new SimpleDividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, margin));
        mRecycler.addItemDecoration(new EmptyViewItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, 40, 16, 14));

        mAdapter = new RoomAdapter(getActivity(), new RoomAdapter.OnSelectItemListener() {
            @Override
            public void onSelectItem(Room room, int position) {
                onRoomSelected(room, position);
            }

            @Override
            public void onSelectItem(PublicRoom publicRoom) {
                onPublicRoomSelected(publicRoom);
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
     * rooms management
     * *********************************************************************************************
     */

    /**
     * Init the rooms display
     */
    private void refreshRooms() {
        IMXStore store = mSession.getDataHandler().getStore();

        // update/retrieve the complete summary list
        List<RoomSummary> roomSummaries = new ArrayList<>(store.getSummaries());
        List<String> directChatRoomIds = mSession.getDirectChatRoomIdsList();

        List<Room> rooms = new ArrayList<>();

        for (RoomSummary summary : roomSummaries) {
            // don't display the invitations
            if (!summary.isInvited()) {
                Room room = store.getRoom(summary.getRoomId());

                if ((null != room) && !directChatRoomIds.contains(room.getRoomId()) && !room.getAccountData().hasTags()) {
                    rooms.add(room);
                }
            }
        }
        
        mAdapter.setRooms(rooms);
    }

    /**
     * Handle a room selection
     *
     * @param room     the room
     * @param position the room index in the list
     */
    private void onRoomSelected(Room room, int position) {
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
        mAdapter.notifyItemChanged(position);
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
            final RoomPreviewData roomPreviewData = new RoomPreviewData(mSession, publicRoom.roomId, null, publicRoom.getAlias(), null);

            Room room = mSession.getDataHandler().getRoom(publicRoom.roomId, false);

            // if the room exists
            if (null != room) {
                // either the user is invited
                if (room.isInvited()) {
                    Log.d(LOG_TAG, "manageRoom : the user is invited -> display the preview " + getActivity());
                    CommonActivityUtils.previewRoom(getActivity(), roomPreviewData);
                } else {
                    Log.d(LOG_TAG, "manageRoom : open the room");
                    HashMap<String, Object> params = new HashMap<>();
                    params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                    params.put(VectorRoomActivity.EXTRA_ROOM_ID, publicRoom.roomId);

                    if (!TextUtils.isEmpty(publicRoom.name)) {
                        params.put(VectorRoomActivity.EXTRA_DEFAULT_NAME, publicRoom.name);
                    }

                    if (!TextUtils.isEmpty(publicRoom.topic)) {
                        params.put(VectorRoomActivity.EXTRA_DEFAULT_TOPIC, publicRoom.topic);
                    }

                    CommonActivityUtils.goToRoomPage(getActivity(), mSession, params);
                }
            } else {
                // TODO add a spinner
                roomPreviewData.fetchPreviewData(new ApiCallback<Void>() {
                    private void onDone() {
                        CommonActivityUtils.previewRoom(getActivity(), roomPreviewData);
                    }

                    @Override
                    public void onSuccess(Void info) {
                        onDone();
                    }

                    private void onError() {
                        roomPreviewData.setRoomState(publicRoom);
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


    // tells if a forward pagination is in progress
    private boolean isForwardPaginating() {
        return View.VISIBLE == mLoadingMoreDirectoryRooms.getVisibility();
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
            SectionView sectionView = mAdapter.getSectionViewForSectionIndex(2);
            AdapterSection section = sectionView != null ? sectionView.getSection() : null;
            if (section != null && !isForwardPaginating() && mAdapter.getItemForPosition(lastVisibleItemPosition) == section.getItems().get(section.getItems().size() - 10)) {
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

        if (mPublicRoomsSelector != null) {
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
        }

        mRoomDirectoryAdapter.add(mSelectedRoomDirectory.getDisplayName());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Activity.RESULT_OK == resultCode) {
            if (requestCode == DIRECTORY_SOURCE_ACTIVITY_REQUEST_CODE) {
                mSelectedRoomDirectory = (RoomDirectoryData) data.getSerializableExtra(RoomDirectoryPickerActivity.EXTRA_OUT_ROOM_DIRECTORY_DATA);

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

        PublicRoomsManager.getInstance().startPublicRoomsSearch(mSelectedRoomDirectory.getServerUrl(),
                mSelectedRoomDirectory.getThirdPartyInstanceId(),
                mSelectedRoomDirectory.isIncludedAllNetworks(),
                mCurrentFilter, new ApiCallback<List<PublicRoom>>() {
                    @Override
                    public void onSuccess(List<PublicRoom> publicRooms) {
                        if (null != getActivity()) {
                            mAdapter.setPublicRooms(publicRooms);
                            addPublicRoomsListener();

                            mLoadingMoreDirectoryRooms.setVisibility(View.GONE);
                        }
                    }

                    private void onError(String message) {
                        if (null != getActivity()) {
                            Log.e(LOG_TAG, "## startPublicRoomsSearch() failed " + message);
                            Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();

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
                    mAdapter.addPublicRooms(publicRooms);

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
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
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
        mRecycler.addOnScrollListener(mPublicRoomScrollListener);
    }

    /**
     * Remove the public rooms listener
     */
    private void removePublicRoomsListener() {
        mRecycler.removeOnScrollListener(null);
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
}
