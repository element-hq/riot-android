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

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.BingRulesManager;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.RoomTag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorHomeActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.AbsAdapter;
import im.vector.ui.badge.BadgeProxy;
import im.vector.util.HomeRoomsViewModel;
import im.vector.util.RoomUtils;

/**
 * Abstract fragment providing the universal search
 */
public abstract class AbsHomeFragment extends VectorBaseFragment implements
        AbsAdapter.RoomInvitationListener,
        AbsAdapter.MoreRoomActionListener,
        RoomUtils.MoreActionListener {

    private static final String LOG_TAG = AbsHomeFragment.class.getSimpleName();
    private static final String CURRENT_FILTER = "CURRENT_FILTER";

    VectorHomeActivity mActivity;

    String mCurrentFilter;

    MXSession mSession;
    OnRoomChangedListener mOnRoomChangedListener;

    final RecyclerView.OnScrollListener mScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            // warn only if there is dy i.e the list has been really scrolled not refreshed
            if ((null != mActivity) && (0 != dy)) {
                mActivity.hideFloatingActionButton(getTag());
            }
        }
    };

    int mPrimaryColor = -1;
    int mSecondaryColor = -1;
    int mFabColor = -1;
    int mFabPressedColor = -1;


    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    @CallSuper
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    @CallSuper
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (getActivity() instanceof VectorHomeActivity) {
            mActivity = (VectorHomeActivity) getActivity();
        }
        mSession = Matrix.getInstance(getActivity()).getDefaultSession();
        if (savedInstanceState != null && savedInstanceState.containsKey(CURRENT_FILTER)) {
            mCurrentFilter = savedInstanceState.getString(CURRENT_FILTER);
        }
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        if (mActivity != null) {
            mActivity.updateTabStyle(mPrimaryColor, mSecondaryColor, mFabColor, mFabPressedColor);

            final HomeRoomsViewModel.Result result = mActivity.getRoomsViewModel().getResult();
            onRoomResultUpdated(result);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ic_action_mark_all_as_read:
                Log.d(LOG_TAG, "onOptionsItemSelected mark all as read");
                onMarkAllAsRead();
                return true;
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(CURRENT_FILTER, mCurrentFilter);
    }

    @Override
    @CallSuper
    public void onDestroyView() {
        super.onDestroyView();
        mCurrentFilter = null;
    }

    @Override
    @CallSuper
    public void onDetach() {
        super.onDetach();
        mActivity = null;
    }

    /*
     * *********************************************************************************************
     * Listeners
     * *********************************************************************************************
     */

    @Override
    public void onPreviewRoom(MXSession session, String roomId) {
        Log.i(LOG_TAG, "onPreviewRoom " + roomId);
        mActivity.onPreviewRoom(session, roomId);
    }

    @Override
    public void onRejectInvitation(MXSession session, String roomId) {
        Log.i(LOG_TAG, "onRejectInvitation " + roomId);
        mActivity.onRejectInvitation(roomId, null);
    }

    @Override
    public void onMoreActionClick(View itemView, Room room) {
        // User clicked on the "more actions" area
        final Set<String> tags = room.getAccountData().getKeys();
        final boolean isFavorite = tags != null && tags.contains(RoomTag.ROOM_TAG_FAVOURITE);
        final boolean isLowPriority = tags != null && tags.contains(RoomTag.ROOM_TAG_LOW_PRIORITY);
        RoomUtils.displayPopupMenu(mActivity, mSession, room, itemView, isFavorite, isLowPriority, this);
    }

    @Override
    public void onUpdateRoomNotificationsState(MXSession session, String roomId, BingRulesManager.RoomNotificationState state) {
        mActivity.showWaitingView();
        session.getDataHandler().getBingRulesManager().updateRoomNotificationState(roomId, state, new BingRulesManager.onBingRuleUpdateListener() {
            @Override
            public void onBingRuleUpdateSuccess() {
                onRequestDone(null);
            }

            @Override
            public void onBingRuleUpdateFailure(final String errorMessage) {
                onRequestDone(errorMessage);
            }
        });
    }

    @Override
    public void onToggleDirectChat(MXSession session, final String roomId) {
        mActivity.showWaitingView();
        RoomUtils.toggleDirectChat(session, roomId, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                onRequestDone(null);
                if (mOnRoomChangedListener != null) {
                    mOnRoomChangedListener.onToggleDirectChat(roomId, RoomUtils.isDirectChat(mSession, roomId));
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onRequestDone(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onRequestDone(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onRequestDone(e.getLocalizedMessage());
            }
        });
    }

    @Override
    public void moveToFavorites(MXSession session, String roomId) {
        updateTag(roomId, null, RoomTag.ROOM_TAG_FAVOURITE);
    }

    @Override
    public void moveToConversations(MXSession session, String roomId) {
        updateTag(roomId, null, null);
    }

    @Override
    public void moveToLowPriority(MXSession session, String roomId) {
        updateTag(roomId, null, RoomTag.ROOM_TAG_LOW_PRIORITY);
    }

    @Override
    public void onLeaveRoom(final MXSession session, final String roomId) {
        RoomUtils.showLeaveRoomDialog(getActivity(), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mActivity != null && !mActivity.isFinishing()) {
                    mActivity.onRejectInvitation(roomId, new SimpleApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            if (mOnRoomChangedListener != null) {
                                mOnRoomChangedListener.onRoomLeft(roomId);
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onForgetRoom(final MXSession session, final String roomId) {
        mActivity.onForgetRoom(roomId, new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                if (mOnRoomChangedListener != null) {
                    mOnRoomChangedListener.onRoomForgot(roomId);
                }
            }
        });
    }

    @Override
    public void addHomeScreenShortcut(MXSession session, String roomId) {
        RoomUtils.addHomeScreenShortcut(getActivity(), session, roomId);
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    /**
     * Apply the filter
     *
     * @param pattern
     */
    public void applyFilter(final String pattern) {
        if (TextUtils.isEmpty(pattern)) {
            if (mCurrentFilter != null) {
                onResetFilter();
                mCurrentFilter = null;
            }
        } else if (!TextUtils.equals(mCurrentFilter, pattern)) {
            onFilter(pattern, new OnFilterListener() {
                @Override
                public void onFilterDone(int nbItems) {
                    mCurrentFilter = pattern;
                }
            });
        }
    }

    /**
     * A room summary has been updated
     *
     * @param result
     */
    public void onRoomResultUpdated(final HomeRoomsViewModel.Result result) {
        //no-op
    }

    /**
     * Open the selected room
     *
     * @param room
     */
    protected void openRoom(final Room room) {
        // sanity checks
        // reported by GA
        if ((null == mSession.getDataHandler()) || (null == mSession.getDataHandler().getStore())) {
            return;
        }

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
                room.sendReadReceipt();
            }

            // Update badge unread count in case device is offline
            BadgeProxy.INSTANCE.specificUpdateBadgeUnreadCount(mSession, getContext());

            // Launch corresponding room activity
            Map<String, Object> params = new HashMap<>();
            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
            params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);

            CommonActivityUtils.goToRoomPage(getActivity(), mSession, params);
        }
    }

    /**
     * Manage the fab actions
     *
     * @return true if the fragment has a dedicated action.
     */
    public boolean onFabClick() {
        return false;
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Change the tag of the given room with the provided one
     *
     * @param roomId
     * @param newTagOrder
     * @param newTag
     */
    private void updateTag(final String roomId, Double newTagOrder, final String newTag) {
        mActivity.showWaitingView();
        RoomUtils.updateRoomTag(mSession, roomId, newTagOrder, newTag, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                onRequestDone(null);
            }

            @Override
            public void onNetworkError(Exception e) {
                onRequestDone(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onRequestDone(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onRequestDone(e.getLocalizedMessage());
            }
        });
    }

    /**
     * Handle the end of any request : hide loading wheel and display error message if there is any
     *
     * @param errorMessage
     */
    private void onRequestDone(final String errorMessage) {
        if (mActivity != null && !mActivity.isFinishing()) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mActivity.hideWaitingView();
                    if (!TextUtils.isEmpty(errorMessage)) {
                        Toast.makeText(mActivity, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    /**
     * Mark all the fragment rooms as read
     */
    private void onMarkAllAsRead() {
        mActivity.showWaitingView();

        mSession.markRoomsAsRead(getRooms(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // check if the activity is still attached
                if ((null != mActivity) && !mActivity.isFinishing()) {
                    mActivity.hideWaitingView();
                    mActivity.refreshUnreadBadges();
                    mActivity.onRoomDataUpdated();
                }
            }

            private void onError(String errorMessage) {
                Log.e(LOG_TAG, "## markAllMessagesAsRead() failed " + errorMessage);
                onSuccess(null);
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError(e.getMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError(e.getMessage());
            }
        });
    }

    /*
     * *********************************************************************************************
     * Abstract methods
     * *********************************************************************************************
     */

    protected abstract List<Room> getRooms();

    protected abstract void onFilter(final String pattern, final OnFilterListener listener);

    protected abstract void onResetFilter();

    /*
     * *********************************************************************************************
     * Listener
     * *********************************************************************************************
     */

    public interface OnFilterListener {
        void onFilterDone(final int nbItems);
    }

    public interface OnRoomChangedListener {

        void onToggleDirectChat(final String roomId, final boolean isDirectChat);

        void onRoomLeft(final String roomId);

        void onRoomForgot(final String roomId);
    }
}
