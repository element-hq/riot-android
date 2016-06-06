/*
 * Copyright 2016 OpenMarket Ltd
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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ExpandableListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomAccountData;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.EventUtils;
import im.vector.Matrix;
import im.vector.PublicRoomsManager;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.ViewedRoomTracker;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorPublicRoomsActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.VectorRoomSummaryAdapter;
import im.vector.services.EventStreamService;
import im.vector.view.RecentsExpandableListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class VectorRecentsListFragment extends Fragment implements VectorRoomSummaryAdapter.RoomEventListener, RecentsExpandableListView.DragAndDropEventsListener {

    private static final String KEY_EXPAND_STATE_INVITES_GROUP = "KEY_EXPAND_STATE_INVITES_GROUP";
    private static final String KEY_EXPAND_STATE_ROOMS_GROUP = "KEY_EXPAND_STATE_ROOMS_GROUP";
    private static final String KEY_EXPAND_STATE_LOW_PRIORITY_GROUP = "KEY_EXPAND_STATE_LOW_PRIORITY_GROUP";
    private static final String KEY_EXPAND_STATE_FAVOURITE_GROUP = "KEY_EXPAND_STATE_FAVOURITE_GROUP";

    /**
     * warns the activity when there is a scroll in the recents
     */
    public interface IVectorRecentsScrollEventListener {
        // warn the user scrolls up
        void onRecentsListScrollUp();
        // warn when the user scrolls downs
        void onRecentsListScrollDown();
        // warn when the list content can be fully displayed without scrolling
        void onRecentsListFitsScreen();
    }

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
    protected RecentsExpandableListView mRecentsListView;
    protected VectorRoomSummaryAdapter mAdapter;
    protected View mWaitingView = null;

    // drag and drop management
    protected RelativeLayout mSelectedCellLayout;
    protected View mDraggedView;
    protected boolean mIgnoreScrollEvent;
    protected int mOriginGroupPosition = -1;
    protected int mOriginChildPosition = -1;
    protected int mDestGroupPosition = -1;
    protected int mDestChildPosition = -1;
    protected boolean mIsWaitingTagOrderEcho;

    protected int mFirstVisibleIndex = 0;

    protected boolean mIsPaused = false;

    // set to true to force refresh when an events chunk has been processed.
    protected boolean refreshOnChunkEnd = false;

    // public room management
    private List<PublicRoom> mPublicRoomsList = null;
    private boolean mIsLoadingPublicRooms = false;
    private long mLatestPublicRoomsRefresh = System.currentTimeMillis();

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
        mRecentsListView = (RecentsExpandableListView)v.findViewById(R.id.fragment_recents_list);
        // the chevron is managed in the header view
        mRecentsListView.setGroupIndicator(null);
        // create the adapter
        mAdapter = new VectorRoomSummaryAdapter(getActivity(), mSession, false, true, R.layout.adapter_item_vector_recent_room, R.layout.adapter_item_vector_recent_header, this);

        mRecentsListView.setAdapter(mAdapter);

        mSelectedCellLayout = (RelativeLayout)v.findViewById(R.id.fragment_recents_selected_cell_layout);
        mRecentsListView.mDragAndDropEventsListener = this;

        // Set rooms click listener:
        // - reset the unread count
        // - start the corresponding room activity
        mRecentsListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v,
                                        int groupPosition, int childPosition, long id) {

                if (mAdapter.isDirectoryGroupPosition(groupPosition)) {
                    List<PublicRoom> matchedPublicRooms = mAdapter.getMatchedPublicRooms();

                    if ((null != matchedPublicRooms) && (matchedPublicRooms.size() > 0)) {
                        Intent intent = new Intent(getActivity(), VectorPublicRoomsActivity.class);
                        intent.putExtra(VectorPublicRoomsActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                        // cannot send the public rooms list in parameters because it might trigger a stackoverflow
                        VectorPublicRoomsActivity.mPublicRooms = new ArrayList<PublicRoom>(matchedPublicRooms);
                        getActivity().startActivity(intent);
                    }
                } else {
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
                        HashMap<String, Object> params = new HashMap<String, Object>();
                        params.put(VectorRoomActivity.EXTRA_MATRIX_ID, session.getMyUserId());
                        params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);

                        CommonActivityUtils.goToRoomPage(getActivity(), session, params);
                    }
                }

                // click is handled
                return true;
            }
        });

        mRecentsListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                startDragAndDrop();
                return true;
            }
        });

        mRecentsListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            IVectorRecentsScrollEventListener mScrollEventListener = null;

            private IVectorRecentsScrollEventListener getListener() {
                if (null == mScrollEventListener) {
                    if (getActivity() instanceof IVectorRecentsScrollEventListener) {
                        mScrollEventListener = (IVectorRecentsScrollEventListener) getActivity();
                    }
                }

                return mScrollEventListener;
            }

            private void onScrollUp() {
                if (null != getListener()) {
                    mScrollEventListener.onRecentsListScrollUp();
                }
            }

            private void onScrollDown() {
                if (null != getListener()) {
                    mScrollEventListener.onRecentsListScrollDown();
                }
            }

            private void onFitScreen() {
                if (null != getListener()) {
                    mScrollEventListener.onRecentsListFitsScreen();
                }
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            // latest cell offset Y
            private int mPrevOffset = 0;

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if ((0 == firstVisibleItem) && ((totalItemCount+1) < visibleItemCount)) {
                    onFitScreen();
                } else if (firstVisibleItem < mFirstVisibleIndex) {
                    mFirstVisibleIndex = firstVisibleItem;
                    mPrevOffset = 0;
                    onScrollUp();
                } else if (firstVisibleItem > mFirstVisibleIndex) {
                    mFirstVisibleIndex = firstVisibleItem;
                    mPrevOffset = 0;
                    onScrollDown();
                } else {
                    // detect the cell has moved
                    View visibleCell = mRecentsListView.getChildAt(firstVisibleItem);

                    if (null != visibleCell) {
                        int off = visibleCell.getTop();

                        if (off > mPrevOffset) {
                            onScrollDown();
                        } else if (off < mPrevOffset){
                            onScrollUp();
                        }

                        mPrevOffset = off;
                    }
                }
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

        mAdapter.setPublicRoomsList(PublicRoomsManager.getPublicRooms());

        // some unsent messages could have been added
        // it does not trigger any live event.
        // So, it is safer to sort the messages when debackgrounding
        //mAdapter.sortSummaries();
        notifyDataSetChanged();

        mRecentsListView.post(new Runnable() {
            @Override
            public void run() {

                // trigger a public room refresh if the list was not initialized or too old (5 mins)
                if (((null == mPublicRoomsList) || ((System.currentTimeMillis() - mLatestPublicRoomsRefresh) < (5 * 60000))) && (!mIsLoadingPublicRooms)) {
                    PublicRoomsManager.refresh(new PublicRoomsManager.PublicRoomsManagerListener() {
                        @Override
                        public void onRefresh() {
                            if (null != getActivity()) {
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        // statuses
                                        mLatestPublicRoomsRefresh = System.currentTimeMillis();
                                        mIsLoadingPublicRooms = false;

                                        // save the public rooms list
                                        mPublicRoomsList = PublicRoomsManager.getPublicRooms();
                                        mAdapter.setPublicRoomsList(mPublicRoomsList);

                                        // refreshed
                                        mAdapter.notifyDataSetChanged();
                                    }
                                });
                            }
                        }
                    });

                }
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle aOutState) {
        super.onSaveInstanceState(aOutState);
    }

    private void findWaitingView() {
        if (null == mWaitingView) {
            mWaitingView = getActivity().findViewById(R.id.listView_spinner_views);
        }
    }

    protected void showWaitingView() {
        findWaitingView();

        if (null != mWaitingView) {
            mWaitingView.setVisibility(View.VISIBLE);
        }
    }

    protected void hideWaitingView() {
        findWaitingView();

        if (null != mWaitingView) {
            mWaitingView.setVisibility(View.GONE);
        }
    }

    /**
     * Refresh the summaries list.
     * It also expands or collapses the section according to the latest known user preferences.
     */
    protected void notifyDataSetChanged(){
        mAdapter.notifyDataSetChanged();

        mRecentsListView.post(new Runnable() {
            @Override
            public void run() {
                if (null != getActivity()) {
                    int groupCount = mRecentsListView.getExpandableListAdapter().getGroupCount();
                    boolean isExpanded;
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

                    for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {

                        if (mAdapter.isInvitedRoomPosition(groupIndex)) {
                            isExpanded = preferences.getBoolean(KEY_EXPAND_STATE_INVITES_GROUP, CommonActivityUtils.GROUP_IS_EXPANDED);
                        } else if (mAdapter.isFavouriteRoomPosition(groupIndex)) {
                            isExpanded = preferences.getBoolean(KEY_EXPAND_STATE_FAVOURITE_GROUP, CommonActivityUtils.GROUP_IS_EXPANDED);
                        } else if (mAdapter.isNoTagRoomPosition(groupIndex)) { // "Rooms" group
                            isExpanded = preferences.getBoolean(KEY_EXPAND_STATE_ROOMS_GROUP, CommonActivityUtils.GROUP_IS_EXPANDED);
                        } else if (mAdapter.isLowPriorityRoomPosition(groupIndex)) {
                            isExpanded = preferences.getBoolean(KEY_EXPAND_STATE_LOW_PRIORITY_GROUP, CommonActivityUtils.GROUP_IS_EXPANDED);
                        } else if (mAdapter.isDirectoryGroupPosition(groupIndex)) { // public rooms (search mode)
                            isExpanded = preferences.getBoolean(KEY_EXPAND_STATE_LOW_PRIORITY_GROUP, CommonActivityUtils.GROUP_IS_EXPANDED);
                        } else {
                            // unknown group index, just skipp
                            break;
                        }

                        if (CommonActivityUtils.GROUP_IS_EXPANDED == isExpanded) {
                            mRecentsListView.expandGroup(groupIndex);
                        } else {
                            mRecentsListView.collapseGroup(groupIndex);
                        }
                    }
                }
            }
        });
    }

    /**
     * Update the group visibility preference.
     * @param aGroupPosition the group position
     * @param aValue the new value.
     */
    protected void updateGroupExpandStatus(int aGroupPosition, boolean aValue) {
        if (null != getActivity()) {
            Context context;
            String groupKey;

            if(mAdapter.isInvitedRoomPosition(aGroupPosition)){
                groupKey = KEY_EXPAND_STATE_INVITES_GROUP;
            } else if(mAdapter.isFavouriteRoomPosition(aGroupPosition)){
                groupKey = KEY_EXPAND_STATE_FAVOURITE_GROUP;
            } else if(mAdapter.isNoTagRoomPosition(aGroupPosition)){ // "Rooms" group
                groupKey = KEY_EXPAND_STATE_ROOMS_GROUP;
            } else if(mAdapter.isLowPriorityRoomPosition(aGroupPosition)){
                groupKey = KEY_EXPAND_STATE_LOW_PRIORITY_GROUP;
            } else if(mAdapter.isDirectoryGroupPosition(aGroupPosition)) { // public rooms (search mode)
                groupKey = KEY_EXPAND_STATE_LOW_PRIORITY_GROUP;
            } else {
                // unknown group position, just skipp
                Log.w(LOG_TAG, "## updateGroupExpandStatus(): Failure - Unknown group: "+aGroupPosition);
                return;
            }

            if (null != (context = getActivity().getApplicationContext())) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(groupKey, aValue);
                editor.commit();
            }
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
                        notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onLiveEventsChunkProcessed() {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(LOG_TAG, "onLiveEventsChunkProcessed");
                        if (!mIsPaused && refreshOnChunkEnd && !mIsWaitingTagOrderEcho) {
                            notifyDataSetChanged();
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
                                Event.EVENT_TYPE_STATE_ROOM_AVATAR.equals(event.type) ||
                                Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(event.type);

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
                mIsWaitingTagOrderEcho = false;
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
                            notifyDataSetChanged();
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
                // clear any pending notification for this room
                EventStreamService.cancelNotificationsForRoomId(mSession.getMyUserId(), roomId);
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
        if (mSession.isAlive()) {
            mSession.getDataHandler().removeListener(mEventsListener);
        }
    }

    @Override
    public void onGroupCollapsedNotif(int aGroupPosition){
        updateGroupExpandStatus(aGroupPosition, CommonActivityUtils.GROUP_IS_COLLAPSED);
    }

    @Override
    public void onGroupExpandedNotif(int aGroupPosition){
        updateGroupExpandStatus(aGroupPosition, CommonActivityUtils.GROUP_IS_EXPANDED);
    }

    @Override
    public void onPreviewRoom(MXSession session, String roomId) {
        String roomAlias = null;

        Room room = session.getDataHandler().getRoom(roomId);
        if ((null != room) && (null != room.getLiveState())) {
            roomAlias = room.getLiveState().getAlias();
        }

        final RoomPreviewData roomPreviewData = new RoomPreviewData(mSession, roomId, null, roomAlias, null);
        CommonActivityUtils.previewRoom(getActivity(), roomPreviewData);
    }

    @Override
    public void onRejectInvitation(final MXSession session, final String roomId) {
        Room room = session.getDataHandler().getRoom(roomId);

        if (null != room) {
            showWaitingView();

            room.leave(new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    if (null != getActivity()) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // clear any pending notification for this room
                                EventStreamService.cancelNotificationsForRoomId(mSession.getMyUserId(), roomId);
                                hideWaitingView();
                            }
                        });
                    }
                }

                private void onError(final String message) {
                    if (null != getActivity()) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                hideWaitingView();
                                Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                            }
                        });
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
                    if (null != getActivity()) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                hideWaitingView();
                            }
                        });
                    }
                }

                @Override
                public void onBingRuleUpdateFailure(final String errorMessage) {
                    if (null != getActivity()) {
                        getActivity().runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
                                        hideWaitingView();
                                    }
                                }
                        );
                    }

                }
            });
        }
    }


    protected boolean isDragAndDropSupported() {
        return true;
    }

    /**
     * Start the drag and drop mode
     */
    private void startDragAndDrop() {
        mIsWaitingTagOrderEcho = false;

        if (isDragAndDropSupported() && groupIsMovable(mRecentsListView.getTouchedGroupPosition())) {
            // enable the drag and drop mode
            mAdapter.setIsDragAndDropMode(true);
            mSession.getDataHandler().removeListener(mEventsListener);

            int groupPos = mRecentsListView.getTouchedGroupPosition();
            int childPos = mRecentsListView.getTouchedChildPosition();

            mDraggedView = mAdapter.getChildView(groupPos, childPos, false, null, null);
            mDraggedView.setBackgroundColor(getResources().getColor(R.color.vector_silver_color));
            mDraggedView.setAlpha(0.3f);

            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
            mSelectedCellLayout.addView(mDraggedView, params);

            mDestGroupPosition = mOriginGroupPosition = groupPos;
            mDestChildPosition = mOriginChildPosition = childPos;

            onTouchMove(mRecentsListView.getTouchedY(), groupPos, childPos);
        }
    }

    /**
     * Drag and drop managemnt
     * @param y the touch Y position
     * @param groupPosition the touched group position
     * @param childPosition the touched child position
     */
    public void onTouchMove(int y, int groupPosition, int childPosition) {
        // check if the recents list is drag & drop mode
        if ((null != mDraggedView) && (!mIgnoreScrollEvent)){

            // display the cell if it is not yet visible
            if (mSelectedCellLayout.getVisibility() != View.VISIBLE) {
                mSelectedCellLayout.setVisibility(View.VISIBLE);
            }

            // compute the next first cell postion
            int nextFirstVisiblePosition = -1;

            // scroll over the screen top
            if (y < 0) {
                // scroll up
                if ((mRecentsListView.getFirstVisiblePosition() > 0)) {
                    nextFirstVisiblePosition = mRecentsListView.getFirstVisiblePosition() - 1;
                }

                y = 0;
            }

            // scroll over the screen bottom
            if ((y + mSelectedCellLayout.getHeight()) > mRecentsListView.getHeight()) {

                // scroll down
                if (mRecentsListView.getLastVisiblePosition() < mRecentsListView.getCount()) {
                    nextFirstVisiblePosition = mRecentsListView.getFirstVisiblePosition() + 2;
                }

                y = mRecentsListView.getHeight() - mSelectedCellLayout.getHeight();
            }

            // move the overlay child view with the y position
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(mSelectedCellLayout.getLayoutParams());
            layoutParams.topMargin = y;
            mSelectedCellLayout.setLayoutParams(layoutParams);

            // virtually insert the moving cell in the recents list
            if ((groupPosition != mDestGroupPosition) || (childPosition != mDestChildPosition)) {

                // move cell
                mAdapter.moveChildView(mDestGroupPosition, mDestChildPosition, groupPosition, childPosition);
                // refresh
                notifyDataSetChanged();

                // backup
                mDestGroupPosition = groupPosition;
                mDestChildPosition = childPosition;
            }

            // the first selected position has been updated
            if (-1 != nextFirstVisiblePosition) {
                mIgnoreScrollEvent = true;

                mRecentsListView.setSelection(nextFirstVisiblePosition);

                // avoid moving to quickly i.e moving only each 100ms
                mRecentsListView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mIgnoreScrollEvent = false;
                    }
                }, 100);
            }
        }
    }

    /**
     * Retrieves the RoomTag.ROOM_TAG.XX value from the group position
     * @param groupPosition
     * @return
     */
    private String roomTagAt(int groupPosition) {
        if (mAdapter.isFavouriteRoomPosition(groupPosition)) {
            return RoomTag.ROOM_TAG_FAVOURITE;
        } else if (mAdapter.isLowPriorityRoomPosition(groupPosition)) {
            return RoomTag.ROOM_TAG_LOW_PRIORITY;
        }

        return null;
    }

    /**
     * Check if a group is movable.
     * @param groupPosition the group position
     * @return true if the group is movable.
     */
    private boolean groupIsMovable(int groupPosition) {
        return mAdapter.isNoTagRoomPosition(groupPosition) ||
                mAdapter.isFavouriteRoomPosition(groupPosition) ||
                mAdapter.isLowPriorityRoomPosition(groupPosition);
    }

    /**
     * The drag ends.
     */
    public void onDrop() {
        // check if the list wad in drag & drop mode
        if (null != mDraggedView) {

            // remove the overlay child view
            ViewGroup viewParent = (ViewGroup) mDraggedView.getParent();
            viewParent.removeView(mDraggedView);
            mDraggedView = null;

            // hide the overlay layout
            mSelectedCellLayout.setVisibility(View.GONE);

            // same place, nothing to do
            if ((mOriginGroupPosition == mDestGroupPosition) && (mOriginChildPosition == mDestChildPosition)) {
                stopDragAndDropMode();
            }
            // move in no tag sections
            else if (mAdapter.isNoTagRoomPosition(mOriginGroupPosition) && mAdapter.isNoTagRoomPosition(mDestGroupPosition)) {
                // nothing to do, there is no other
                stopDragAndDropMode();
            } else if (!groupIsMovable(mDestGroupPosition)) {
                // cannot move in the expected group
                stopDragAndDropMode();
            } else {
                // retrieve the moved summary
                RoomSummary roomSummary = mAdapter.getRoomSummaryAt(mDestGroupPosition, mDestChildPosition);
                // its tag
                String dstRoomTag = roomTagAt(mDestGroupPosition);

                // compute the new tag order
                int oldPos = (mOriginGroupPosition == mDestGroupPosition) ? mOriginChildPosition : Integer.MAX_VALUE;
                Double tagOrder = mSession.tagOrderToBeAtIndex(mDestChildPosition, oldPos, dstRoomTag);

                updateRoomTag(mSession, roomSummary.getRoomId(), tagOrder, dstRoomTag);
            }
        }
    }

    /**
     * Stop the drag and drop mode.
     */
    private void stopDragAndDropMode() {
        // in drag and drop mode
        // the events listener is unplugged while playing with the cell
        if (mAdapter.isInDragAndDropMode()) {
            mSession.getDataHandler().addListener(mEventsListener);
            mAdapter.setIsDragAndDropMode(false);
            if (!mIsWaitingTagOrderEcho) {
                notifyDataSetChanged();
            }
        }
    }

    /**
     * Update the room tag.
     * @param session the session
     * @param roomId the room id.
     * @param tagOrder the tag order.
     * @param newtag the new tag.
     */
    private void updateRoomTag(MXSession session, String roomId, Double tagOrder, String newtag) {
        Room room = session.getDataHandler().getRoom(roomId);

        if (null != room) {
            String oldTag = null;

            // retrieve the tag from the room info
            RoomAccountData accountData = room.getAccountData();

            if ((null != accountData) && accountData.hasTags()) {
                oldTag = accountData.getKeys().iterator().next();
            }

            // if the tag order is not provided, compute it
            if (null == tagOrder) {
                tagOrder = 0.0;

                if (null != newtag) {
                    tagOrder = session.tagOrderToBeAtIndex(0, Integer.MAX_VALUE, newtag);
                }
            }

            // show a spinner
            showWaitingView();

            // restore the listener because the room tag event could be sent before getting the replaceTag response.
            mIsWaitingTagOrderEcho = true;
            mSession.getDataHandler().addListener(mEventsListener);

            // and work
            room.replaceTag(oldTag, newtag, tagOrder, new ApiCallback<Void>() {

                @Override
                public void onSuccess(Void info) {
                    if (null != getActivity()) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                hideWaitingView();
                                stopDragAndDropMode();
                            }
                        });
                    }
                }

                private void onReplaceFails(final String errorMessage) {
                    if (null != getActivity()) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mIsWaitingTagOrderEcho = false;
                                hideWaitingView();
                                stopDragAndDropMode();

                                if (!TextUtils.isEmpty(errorMessage)) {
                                    Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    onReplaceFails(e.getLocalizedMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onReplaceFails(e.getLocalizedMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onReplaceFails(e.getLocalizedMessage());
                }
            });
        }
    }

    @Override
    public void moveToConversations(MXSession session, String roomId) {
        updateRoomTag(session, roomId, null, null);
    }

    @Override
    public void moveToFavorites(MXSession session, String roomId) {
        updateRoomTag(session, roomId, null, RoomTag.ROOM_TAG_FAVOURITE);
    }

    @Override
    public void moveToLowPriority(MXSession session, String roomId) {
        updateRoomTag(session, roomId, null, RoomTag.ROOM_TAG_LOW_PRIORITY);
    }
}
