/*
 * Copyright 2015 OpenMarket Ltd
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

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomAccountData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.EventUtils;
import im.vector.Matrix;
import im.vector.R;
import im.vector.ViewedRoomTracker;
import im.vector.activity.CommonActivityUtils;
import im.vector.adapters.VectorRoomSummaryAdapter;

import java.util.List;

public class VectorRecentsListFragment extends Fragment implements VectorRoomSummaryAdapter.RoomEventListener {
    private static final String LOG_TAG = "VectorRecentsListFrg";

    public static final String ARG_LAYOUT_ID = "VectorRecentsListFragment.ARG_LAYOUT_ID";
    public static final String ARG_MATRIX_ID = "VectorRecentsListFragment.ARG_MATRIX_ID";

    public static VectorRecentsListFragment newInstance(String matrixId, int layoutResId) {
        VectorRecentsListFragment f = new VectorRecentsListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, matrixId);
        f.setArguments(args);
        return f;
    }

    protected String mMatrixId;
    protected MXSession mSession;
    protected MXEventListener mEventsListener;
    protected ExpandableListView mRecentsListView;
    protected VectorRoomSummaryAdapter mAdapter;
    protected View mWaitingView = null;

    protected boolean mIsPaused = false;

    // set to true to force refresh when an events chunk has been processed.
    protected boolean refreshOnChunkEnd = false;

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
        mRecentsListView = (ExpandableListView)v.findViewById(R.id.fragment_recents_list);
        // the chevron is managed in the header view
        mRecentsListView.setGroupIndicator(null);
        // create the adapter
        mAdapter = new VectorRoomSummaryAdapter(getActivity(), mSession, false, R.layout.adapter_item_vector_recent_room, R.layout.adapter_item_vector_recent_header, this);

        mRecentsListView.setAdapter(mAdapter);

        // Set rooms click listener:
        // - reset the unread count
        // - start the corresponding room activity
        mRecentsListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v,
                                        int groupPosition, int childPosition, long id) {
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
                    CommonActivityUtils.goToRoomPage(session, roomId, getActivity(), null);
                }

                // click is handled
                return true;
            }
        });

        return v;
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsPaused = true;
        removeSessionListener();
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsPaused = false;
        addSessionListener();

        // some unsent messages could have been added
        // it does not trigger any live event.
        // So, it is safer to sort the messages when debackgrounding
        //mAdapter.sortSummaries();
        mAdapter.notifyDataSetChanged();
    }

    // RoomEventListener

    private void findWaitingView() {
        if (null == mWaitingView) {
            mWaitingView = getActivity().findViewById(R.id.listView_spinner_views);
        }
    }

    private void showWaitingView() {
        findWaitingView();

        if (null != mWaitingView) {
            mWaitingView.setVisibility(View.VISIBLE);
        }
    }

    private void hideWaitingView() {
        findWaitingView();

        if (null != mWaitingView) {
            mWaitingView.setVisibility(View.GONE);
        }
    }

    /**
     * Add a MXEventListener to the session listeners.
     */
    private void addSessionListener() {
        mEventsListener = new MXEventListener() {
            private boolean mInitialSyncComplete = false;

            @Override
            public void onInitialSyncComplete() {
                Log.d(LOG_TAG, "## onInitialSyncComplete()");
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mInitialSyncComplete = true;
                        mAdapter.notifyDataSetChanged();

                        mRecentsListView.post(new Runnable() {
                            @Override
                            public void run() {
                                // expand all
                                int groupCount = mRecentsListView.getExpandableListAdapter().getGroupCount();
                                for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
                                    mRecentsListView.expandGroup(groupIndex);
                                }
                            }
                        });
                    }
                });
            }

            @Override
            public void onLiveEventsChunkProcessed() {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(LOG_TAG, "onLiveEventsChunkProcessed");
                        if (!mIsPaused && refreshOnChunkEnd) {
                            mAdapter.notifyDataSetChanged();
                        }

                        refreshOnChunkEnd = false;
                    }
                });
            }

            @Override
            public void onLiveEvent(final Event event, final RoomState roomState) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        // refresh the UI at the end of the next events chunk
                        refreshOnChunkEnd |= ((event.roomId != null) && RoomSummary.isSupportedEvent(event)) ||
                                Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) ||
                                Event.EVENT_TYPE_TAGS.equals(event.type) ||
                                Event.EVENT_TYPE_REDACTION.equals(event.type) ||
                                Event.EVENT_TYPE_RECEIPT.equals(event.type) ||
                                Event.EVENT_TYPE_STATE_ROOM_AVATAR.equals(event.type);

                        // highlight notified messages
                        // the SDK only highlighted invitation messages
                        // it lets the application chooses the behaviour.
                        ViewedRoomTracker rTracker = ViewedRoomTracker.getInstance();
                        String viewedRoomId = rTracker.getViewedRoomId();
                        String fromMatrixId = rTracker.getMatrixId();
                        MXSession session = VectorRecentsListFragment.this.mSession;
                        String matrixId = session.getCredentials().userId;

                        // If we're not currently viewing this room or not sent by myself, increment the unread count
                        if ((!TextUtils.equals(event.roomId, viewedRoomId) || !TextUtils.equals(matrixId, fromMatrixId)) && !TextUtils.equals(event.getSender(), matrixId)) {
                            RoomSummary summary = session.getDataHandler().getStore().getSummary(event.roomId);
                            if (null != summary) {
                                summary.setHighlighted(summary.isHighlighted() || EventUtils.shouldHighlight(session, event));
                            }
                        }
                    }
                });
            }

            @Override
            public void onReceiptEvent(String roomId, List<String> senderIds) {
                // refresh only if the current user read some messages (to update the unread messages counters)
                refreshOnChunkEnd |= (senderIds.indexOf(VectorRecentsListFragment.this.mSession.getCredentials().userId) >= 0);
            }

            @Override
            public void onRoomTagEvent(String roomId) {
                refreshOnChunkEnd = true;
            }

            /**
             * These methods trigger an UI refresh asap because the user could have created / joined / left a room
             * but the server events echos are not yet received.
             *
             */
            private void onForceRefresh() {
                if (mInitialSyncComplete) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }

            @Override
            public void onStoreReady() {
                onForceRefresh();
            }

            @Override
            public void onLeaveRoom(final String roomId) {
                onForceRefresh();
            }

            @Override
            public void onNewRoom(String roomId) {
                onForceRefresh();
            }

            @Override
            public void onJoinRoom(String roomId) {
                onForceRefresh();
            }
        };

        mSession.getDataHandler().addListener(mEventsListener);
    }

    /**
     * Remove the MXEventListener to the session listeners.
     */
    private void removeSessionListener() {
        if (mSession.isActive()) {
            mSession.getDataHandler().removeListener(mEventsListener);
        }
    }

    @Override
    public void onJoinRoom(MXSession session, String roomId) {
        CommonActivityUtils.goToRoomPage(session, roomId, getActivity(), null);
    }

    @Override
    public void onRejectInvitation(MXSession session, String roomId) {
        Room room = session.getDataHandler().getRoom(roomId);

        if (null != room) {
            showWaitingView();

            room.leave(new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    hideWaitingView();
                }

                private void onError() {
                    // TODO display a message ?
                    hideWaitingView();
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

    @Override
    public void onLeaveRoom(MXSession session, String roomId) {
        onRejectInvitation(session, roomId);
    }

    @Override
    public void onToggleRoomNotifications(MXSession session, String roomId) {
        Room room = session.getDataHandler().getRoom(roomId);

        if (null != room) {
            BingRulesManager bingRulesManager = session.getDataHandler().getBingRulesManager();

            showWaitingView();

            bingRulesManager.muteRoomNotifications(room, !bingRulesManager.isRoomNotificationsDisabled(room), new BingRulesManager.onBingRuleUpdateListener() {
                @Override
                public void onBingRuleUpdateSuccess() {
                    hideWaitingView();
                }

                @Override
                public void onBingRuleUpdateFailure(String errorMessage) {
                    // TODO display a message ?
                    hideWaitingView();
                }
            });
        }
    }

    private void updateRoomTag(MXSession session, String roomId, String newtag) {
        Room room = session.getDataHandler().getRoom(roomId);

        if (null != room) {
            String oldTag = null;

            RoomAccountData accountData = room.getAccountData();

            if ((null != accountData) && accountData.hasTags()) {
                oldTag = accountData.getKeys().iterator().next();
            }

            Double tagOrder = 0.0;

            if (null != newtag) {
                tagOrder = session.tagOrderToBeAtIndex(0, Integer.MAX_VALUE, newtag);
            }

            showWaitingView();

            room.replaceTag(oldTag, newtag, tagOrder, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    hideWaitingView();
                }

                @Override
                public void onNetworkError(Exception e) {
                    // TODO display a message ?
                    hideWaitingView();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    // TODO display a message ?
                    hideWaitingView();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    // TODO display a message ?
                    hideWaitingView();
                }
            });
        }
    }

    @Override
    public void moveToConversations(MXSession session, String roomId) {
        updateRoomTag(session, roomId, null);
    }

    @Override
    public void moveToFavorites(MXSession session, String roomId) {
        updateRoomTag(session, roomId, RoomTag.ROOM_TAG_FAVOURITE);
    }

    @Override
    public void moveToLowPriority(MXSession session, String roomId) {
        updateRoomTag(session, roomId, RoomTag.ROOM_TAG_LOW_PRIORITY);
    }
}
