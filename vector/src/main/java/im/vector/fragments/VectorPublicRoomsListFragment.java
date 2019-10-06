/*
 * Copyright 2015 OpenMarket Ltd
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

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.rest.model.publicroom.PublicRoom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import im.vector.Matrix;
import im.vector.PublicRoomsManager;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.VectorPublicRoomsAdapter;

public class VectorPublicRoomsListFragment extends VectorBaseFragment {
    private static final String LOG_TAG = VectorPublicRoomsListFragment.class.getSimpleName();

    private static final String ARG_LAYOUT_ID = "VectorPublicRoomsListFragment.ARG_LAYOUT_ID";
    private static final String ARG_MATRIX_ID = "VectorPublicRoomsListFragment.ARG_MATRIX_ID";
    private static final String ARG_SEARCHED_PATTERN = "VectorPublicRoomsListFragment.ARG_SEARCHED_PATTERN";

    public static VectorPublicRoomsListFragment newInstance(String matrixId, int layoutResId, String pattern) {
        VectorPublicRoomsListFragment f = new VectorPublicRoomsListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, matrixId);

        if (!TextUtils.isEmpty(pattern)) {
            args.putString(ARG_SEARCHED_PATTERN, pattern);
        }
        f.setArguments(args);
        return f;
    }

    private MXSession mSession;
    @BindView(R.id.fragment_public_rooms_list)
    ListView mRecentsListView;
    private VectorPublicRoomsAdapter mAdapter;
    private String mPattern;

    @BindView(R.id.listView_global_spinner_views)
    View mInitializationSpinnerView;

    @BindView(R.id.listView_forward_spinner_view)
    View mForwardPaginationView;

    /**
     * Customize the scrolls behaviour.
     * -> scroll over the top triggers a back pagination
     * -> scroll over the bottom triggers a forward pagination
     */
    private final AbsListView.OnScrollListener mScrollListener = new AbsListView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            //check only when the user scrolls the content
            if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
                int lastVisibleRow = mRecentsListView.getLastVisiblePosition();
                int count = mRecentsListView.getCount();

                if ((lastVisibleRow + 10) >= count) {
                    forwardPaginate();
                }
            }
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

            if ((firstVisibleItem + visibleItemCount + 10) >= totalItemCount) {
                forwardPaginate();
            }
        }
    };

    @Override
    public int getLayoutResId() {
        Bundle args = getArguments();
        return args.getInt(ARG_LAYOUT_ID);
    }

    @Override
    public void onViewCreated(@NotNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();

        String matrixId = args.getString(ARG_MATRIX_ID);
        mSession = Matrix.getInstance(getActivity()).getSession(matrixId);

        if (null == mSession) {
            throw new RuntimeException("Must have valid default MXSession.");
        }

        mPattern = args.getString(ARG_SEARCHED_PATTERN, null);

        // create the adapter
        mAdapter = new VectorPublicRoomsAdapter(getActivity(), R.layout.adapter_item_vector_recent_room, mSession);
        mRecentsListView.setAdapter(mAdapter);

        // Set rooms click listener:
        // - reset the unread count
        // - start the corresponding room activity
        mRecentsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final PublicRoom publicRoom = mAdapter.getItem(position);

                // launch corresponding room activity
                if (null != publicRoom.roomId) {
                    final RoomPreviewData roomPreviewData = new RoomPreviewData(mSession, publicRoom.roomId, null, publicRoom.canonicalAlias, null);

                    // Check whether the room exists to handled the cases where the user is invited or he has joined.
                    // CAUTION: the room may exist whereas the user membership is neither invited nor joined.
                    final Room room = mSession.getDataHandler().getRoom(publicRoom.roomId, false);
                    if (null != room && room.isInvited()) {
                        Log.d(LOG_TAG, "manageRoom : the user is invited -> display the preview " + VectorApp.getCurrentActivity());
                        CommonActivityUtils.previewRoom(getActivity(), roomPreviewData);
                    } else if (null != room && room.isJoined()) {
                        Log.d(LOG_TAG, "manageRoom : the user joined the room -> open the room");
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
                        Log.d(LOG_TAG, "manageRoom : display the preview");
                        mInitializationSpinnerView.setVisibility(View.VISIBLE);

                        roomPreviewData.fetchPreviewData(new ApiCallback<Void>() {
                            private void onDone() {
                                if (!isAdded()) {
                                    return;
                                }

                                mInitializationSpinnerView.setVisibility(View.GONE);
                                CommonActivityUtils.previewRoom(getActivity(), roomPreviewData);
                            }

                            @Override
                            public void onSuccess(Void info) {
                                onDone();
                            }

                            private void onError() {
                                if (!isAdded()) {
                                    return;
                                }

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
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        if (0 == mAdapter.getCount()) {
            mInitializationSpinnerView.setVisibility(View.VISIBLE);

            PublicRoomsManager.getInstance().startPublicRoomsSearch(null, null, false, mPattern, new ApiCallback<List<PublicRoom>>() {
                @Override
                public void onSuccess(List<PublicRoom> publicRooms) {
                    if (null != getActivity()) {
                        mAdapter.addAll(publicRooms);
                        mRecentsListView.setOnScrollListener(mScrollListener);
                        mInitializationSpinnerView.setVisibility(View.GONE);
                    }
                }

                private void onError(String message) {
                    if (null != getActivity()) {
                        Log.e(LOG_TAG, "## startPublicRoomsSearch() failed " + message);
                        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                        mInitializationSpinnerView.setVisibility(View.GONE);
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
    }

    /**
     * Trigger a forward room pagination
     */
    private void forwardPaginate() {
        boolean hasStarted = PublicRoomsManager.getInstance().forwardPaginate(new ApiCallback<List<PublicRoom>>() {
            @Override
            public void onSuccess(List<PublicRoom> publicRooms) {
                if (null != getActivity()) {
                    mForwardPaginationView.setVisibility(View.GONE);
                    mAdapter.addAll(publicRooms);

                    // unplug the scroll listener if there is no more data to find
                    if (!PublicRoomsManager.getInstance().hasMoreResults()) {
                        mRecentsListView.setOnScrollListener(null);
                    }
                }
            }

            private void onError(String message) {
                if (null != getActivity()) {
                    Log.e(LOG_TAG, "## forwardPaginate() failed " + message);
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                    mForwardPaginationView.setVisibility(View.GONE);
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

        if (hasStarted) {
            mForwardPaginationView.setVisibility(View.VISIBLE);
        }
    }
}
