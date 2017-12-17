/*
 * Copyright 2016 OpenMarket Ltd
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

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.publicroom.PublicRoom;

import java.util.List;

import im.vector.Matrix;
import im.vector.PublicRoomsManager;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorPublicRoomsActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.VectorRoomSummaryAdapter;

public class VectorSearchRoomsListFragment extends VectorRecentsListFragment {
    // the session
    private MXSession mSession;

    /**
     * Static constructor
     *
     * @param matrixId the matrix id
     * @return a VectorRoomsSearchResultsListFragment instance
     */
    public static VectorSearchRoomsListFragment newInstance(String matrixId, int layoutResId) {
        VectorSearchRoomsListFragment f = new VectorSearchRoomsListFragment();
        Bundle args = new Bundle();
        args.putInt(VectorRecentsListFragment.ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, matrixId);
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        Bundle args = getArguments();

        mMatrixId = args.getString(ARG_MATRIX_ID);
        mSession = Matrix.getInstance(getActivity()).getSession(mMatrixId);

        if (null == mSession) {
            throw new RuntimeException("Must have valid default MXSession.");
        }

        View v = inflater.inflate(args.getInt(ARG_LAYOUT_ID), container, false);
        mWaitingView = v.findViewById(R.id.listView_spinner_views);
        mRecentsListView = v.findViewById(R.id.fragment_recents_list);
        // the chevron is managed in the header view
        mRecentsListView.setGroupIndicator(null);
        // create the adapter
        mAdapter = new VectorRoomSummaryAdapter(getActivity(), mSession, true, false, R.layout.adapter_item_vector_recent_room, R.layout.adapter_item_vector_recent_header, this, this);
        mRecentsListView.setAdapter(mAdapter);
        mRecentsListView.setVisibility(View.VISIBLE);

        // Set rooms click listener:
        // - reset the unread count
        // - start the corresponding room activity
        mRecentsListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v,
                                        int groupPosition, int childPosition, long id) {

                if (mAdapter.isRoomByIdGroupPosition(groupPosition)) {
                    final String roomIdOrAlias = mAdapter.getSearchedPattern();

                    // detect if it is a room id
                    if (roomIdOrAlias.startsWith("!")) {
                        previewRoom(roomIdOrAlias, null);
                    } else {
                        showWaitingView();

                        // test if the room Id / alias exists
                        mSession.getDataHandler().roomIdByAlias(roomIdOrAlias, new ApiCallback<String>() {
                            @Override
                            public void onSuccess(String roomId) {
                                previewRoom(roomId, roomIdOrAlias);
                            }

                            private void onError(String errorMessage) {
                                hideWaitingView();
                                Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
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
                // display the public rooms list
                else if (mAdapter.isDirectoryGroupPosition(groupPosition)) {
                    if (TextUtils.isEmpty(mAdapter.getSearchedPattern()) || (mAdapter.getMatchedPublicRoomsCount() > 0)) {
                        Intent intent = new Intent(getActivity(), VectorPublicRoomsActivity.class);
                        intent.putExtra(VectorPublicRoomsActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());

                        if (!TextUtils.isEmpty(mAdapter.getSearchedPattern())) {
                            intent.putExtra(VectorPublicRoomsActivity.EXTRA_SEARCHED_PATTERN, mAdapter.getSearchedPattern());
                        }
                        getActivity().startActivity(intent);
                    }
                } else {
                    // open the dedicated room activity
                    RoomSummary roomSummary = mAdapter.getRoomSummaryAt(groupPosition, childPosition);
                    MXSession session = Matrix.getInstance(getActivity()).getSession(roomSummary.getMatrixId());

                    String roomId = roomSummary.getRoomId();
                    Room room = session.getDataHandler().getRoom(roomId);
                    // cannot join a leaving room
                    if ((null == room) || room.isLeaving()) {
                        roomId = null;
                    }

                    // update the unread messages count
                    if (mAdapter.resetUnreadCount(groupPosition, childPosition)) {
                        session.getDataHandler().getStore().flushSummary(roomSummary);
                    }

                    // launch corresponding room activity
                    if (null != roomId) {
                        Intent intent = new Intent(getActivity(), VectorRoomActivity.class);
                        intent.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
                        intent.putExtra(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                        getActivity().startActivity(intent);
                    }
                }

                // click is handled
                return true;
            }
        });

        // disable the collapse
        mRecentsListView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
                // Doing nothing
                return true;
            }
        });

        return v;
    }

    /**
     * Preview the dedicated room if it was not joined.
     *
     * @param roomId    the roomId
     * @param roomAlias the room alias
     */
    private void previewRoom(final String roomId, final String roomAlias) {
        CommonActivityUtils.previewRoom(getActivity(), mSession, roomId, roomAlias, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                hideWaitingView();
            }

            @Override
            public void onNetworkError(Exception e) {
                hideWaitingView();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                hideWaitingView();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                hideWaitingView();
            }
        });
    }

    /**
     * Search a pattern in the room
     *
     * @param pattern                the pattern to search
     * @param onSearchResultListener the search listener.
     */
    public void searchPattern(final String pattern, final MatrixMessageListFragment.OnSearchResultListener onSearchResultListener) {
        // will be done while resuming
        if (null == mRecentsListView) {
            return;
        }

        super.applyFilter(pattern);

        if (!TextUtils.isEmpty(mAdapter.getSearchedPattern())) {
            PublicRoomsManager.getInstance().startPublicRoomsSearch(null, null, false, mAdapter.getSearchedPattern(), new ApiCallback<List<PublicRoom>>() {

                private void onDone(int size) {
                    mAdapter.setMatchedPublicRoomsCount(size);
                }

                @Override
                public void onSuccess(List<PublicRoom> info) {
                    onDone(info.size());
                }

                @Override
                public void onNetworkError(Exception e) {
                    onDone(0);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onDone(0);
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onDone(0);
                }
            });
        }

        mRecentsListView.post(new Runnable() {
            @Override
            public void run() {
                onSearchResultListener.onSearchSucceed(1);
            }
        });
    }

    protected boolean isDragAndDropSupported() {
        return false;
    }

    /**
     * Update the group visibility preference.
     *
     * @param aGroupPosition the group position
     * @param aValue         the new value.
     */
    protected void updateGroupExpandStatus(int aGroupPosition, boolean aValue) {
        // do nothing
        // the expandable preferences are not updated because all the groups are expanded.
    }

    /**
     * Refresh the summaries list.
     * It also expands or collapses the section according to the latest known user preferences.
     */
    protected void notifyDataSetChanged() {
        // the groups are always expanded.
        mAdapter.notifyDataSetChanged();
    }
}
