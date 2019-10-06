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

package im.vector.util;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.IntDef;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.MXPatterns;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.model.Event;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import im.vector.R;
import im.vector.activity.MXCActionBarActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.VectorMessagesAdapter;
import im.vector.fragments.VectorMessageListFragment;

/**
 * Class handling the read marker for a given room
 */
public class ReadMarkerManager implements VectorMessagesAdapter.ReadMarkerListener {

    private static final String LOG_TAG = ReadMarkerManager.class.getSimpleName();

    // number of messages from the store that we allow to load for the "jump to first unread message"
    private static final int UNREAD_BACK_PAGINATE_EVENT_COUNT = 100;

    @IntDef({LIVE_MODE, PREVIEW_MODE})
    @Retention(RetentionPolicy.SOURCE)
    @interface UpdateMode {
    }

    public static final int LIVE_MODE = 0;
    public static final int PREVIEW_MODE = 1;

    private int mUpdateMode = -1;

    // To track when a scroll finished
    private int mScrollState = -1;

    // Views of the "Jump to..." banner
    private View mJumpToUnreadView;
    private View mCloseJumpToUnreadView;
    private View mJumpToUnreadViewSpinner;

    private VectorRoomActivity mActivity;
    private VectorMessageListFragment mVectorMessageListFragment;
    private MXSession mSession;
    private Room mRoom;
    private RoomSummary mRoomSummary;

    private String mReadMarkerEventId;

    // Visible events from the listview
    private Event mFirstVisibleEvent;
    private Event mLastVisibleEvent;

    // Set to true when user jumped to first unread message, false otherwise
    // Used to know whether we need to update the read marker while scrolling up or down
    private boolean mHasJumpedToFirstUnread;
    // Set to true after used jumped to bottom, false otherwise
    // Used to make sure we check if the read marker has to be updated after reaching the bottom
    private boolean mHasJumpedToBottom;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public ReadMarkerManager(final VectorRoomActivity activity, final VectorMessageListFragment messageListFragment,
                             final MXSession session, final Room room, @UpdateMode final int updateMode,
                             final View jumpToFirstUnreadView) {
        if (room == null) {
            return;
        }

        mActivity = activity;
        mVectorMessageListFragment = messageListFragment;
        mSession = session;

        mRoom = room;
        mRoomSummary = mRoom.getDataHandler().getStore().getSummary(mRoom.getRoomId());

        mReadMarkerEventId = mRoomSummary.getReadMarkerEventId();
        Log.d(LOG_TAG, "Create ReadMarkerManager instance id:" + mReadMarkerEventId + " for room:" + mRoom.getRoomId());

        mUpdateMode = updateMode;

        if (jumpToFirstUnreadView != null) {
            mJumpToUnreadView = jumpToFirstUnreadView;
            TextView jumpToUnreadLabel = jumpToFirstUnreadView.findViewById(R.id.jump_to_first_unread_label);
            jumpToUnreadLabel.setPaintFlags(jumpToUnreadLabel.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            // Actions views
            mCloseJumpToUnreadView = jumpToFirstUnreadView.findViewById(R.id.close_jump_to_first_unread);
            mJumpToUnreadViewSpinner = jumpToFirstUnreadView.findViewById(R.id.jump_to_read_spinner);

            if (isLiveMode()) {
                jumpToUnreadLabel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // force to dismiss the keyboard
                        // on some devices, it is not closed
                        activity.dismissKeyboard();

                        // Make sure read marker didn't change
                        updateReadMarkerValue();

                        if (!TextUtils.isEmpty(mReadMarkerEventId)) {
                            final Event lastReadEvent = mRoom.getDataHandler().getStore().getEvent(mReadMarkerEventId, mRoom.getRoomId());
                            if (lastReadEvent == null) {
                                // Event is not in store, open preview
                                openPreviewToGivenEvent(mReadMarkerEventId);
                            } else {
                                // Event is in memory, scroll up to it
                                scrollUpToGivenEvent(lastReadEvent);
                            }
                        }
                    }
                });
                mCloseJumpToUnreadView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        forgetReadMarker();
                    }
                });
            }
        }
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    /**
     * Called after the activity/fragment resumed
     */
    public void onResume() {
        ((VectorMessagesAdapter) mVectorMessageListFragment.getMessageAdapter()).setReadMarkerListener(this);
        updateJumpToBanner();
    }

    /**
     * Called after the activity/fragment paused
     */
    public void onPause() {
        if (!isLiveMode() || mHasJumpedToFirstUnread) {
            setReadMarkerToLastVisibleRow();
        }
    }

    /**
     * Called during scroll on the listview
     *
     * @param firstVisibleItem
     * @param visibleItemCount
     * @param totalItemCount
     * @param eventAtTop
     * @param eventAtBottom
     */
    public void onScroll(final int firstVisibleItem, final int visibleItemCount, final int totalItemCount,
                         final Event eventAtTop, final Event eventAtBottom) {
        mFirstVisibleEvent = eventAtTop;
        mLastVisibleEvent = eventAtBottom;

        if (isLiveMode()) {
            updateJumpToBanner();
        } else if (mVectorMessageListFragment.getEventTimeLine().hasReachedHomeServerForwardsPaginationEnd()) {
            // Display "You've caught up" message if necessary
            final ListView messageListView = mVectorMessageListFragment.getMessageListView();
            if (messageListView != null && firstVisibleItem + visibleItemCount == totalItemCount
                    && messageListView.getChildAt(messageListView.getChildCount() - 1).getBottom() == messageListView.getBottom()) {
                mActivity.setResult(Activity.RESULT_OK);
                mActivity.finish();
            }
        }
    }

    /**
     * Called at the end of a scroll action
     *
     * @param scrollState the scroll state
     */
    public void onScrollStateChanged(final int scrollState) {
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE
                && (mScrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING
                || mScrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)) {
            checkUnreadMessage();
        }

        mScrollState = scrollState;
    }

    /**
     * Called when we received a new read marker for the room we are monitoring
     * I.e. when read marker has been changed from another client
     *
     * @param roomId
     */
    public void onReadMarkerChanged(String roomId) {
        if (TextUtils.equals(mRoom.getRoomId(), roomId)) {
            final String newReadMarkerEventId = mRoomSummary.getReadMarkerEventId();
            if (!TextUtils.equals(newReadMarkerEventId, mReadMarkerEventId)) {
                Log.d(LOG_TAG, "onReadMarkerChanged" + newReadMarkerEventId);
                refresh();
            }
        }
    }

    /**
     * Handle jump to bottom action
     */
    public void handleJumpToBottom() {
        // Set flag to be sure we check unread messages after updating the "Jump to" view
        // since on onScrollStateChanged will not be triggered
        mHasJumpedToBottom = true;

        if (isLiveMode() && mHasJumpedToFirstUnread) {
            // Update read marker to the last visible event before jumping down
            setReadMarkerToLastVisibleRow();
            mHasJumpedToFirstUnread = false;
        }
        mVectorMessageListFragment.getMessageAdapter().updateReadMarker(mReadMarkerEventId, mRoomSummary.getReadReceiptEventId());
        mVectorMessageListFragment.scrollToBottom(0);
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Check if the read marker value has to be updated
     */
    private void checkUnreadMessage() {
        Log.d(LOG_TAG, "checkUnreadMessage");
        if (mJumpToUnreadView.getVisibility() != View.VISIBLE) {
            final String readReceiptEventId = mRoomSummary.getReadReceiptEventId();
            if (mReadMarkerEventId != null && !mReadMarkerEventId.equals(readReceiptEventId)) {
                if (isLiveMode() && !mHasJumpedToFirstUnread) {
                    // We are catching up as scrolling up
                    // Check if the first unread has been reached by scrolling up
                    MessageRow unreadRow = mVectorMessageListFragment.getMessageAdapter().getMessageRow(mReadMarkerEventId);
                    if (unreadRow != null && unreadRow.getEvent() != null && mFirstVisibleEvent != null
                            && unreadRow.getEvent().getOriginServerTs() >= mFirstVisibleEvent.getOriginServerTs()) {
                        Log.d(LOG_TAG, "checkUnreadMessage: first unread has been reached by scrolling up");
                        forgetReadMarker();
                    }
                } else if (mLastVisibleEvent != null) {
                    // We are catching up as scrolling down
                    // Check if the last received event has been reached by scrolling down
                    if (mLastVisibleEvent.eventId.equals(mRoomSummary.getLatestReceivedEvent().eventId)) {
                        Log.d(LOG_TAG, "checkUnreadMessage: last received event has been reached by scrolling down");
                        markAllAsRead();
                    } else if (!isLiveMode()) {
                        Log.d(LOG_TAG, "checkUnreadMessage: preview mode, set read marker to last visible row");
                        setReadMarkerToLastVisibleRow();
                    }
                }
            }
        }
    }

    /**
     * Make sure we have the correct read marker event id
     */
    private void updateReadMarkerValue() {
        mReadMarkerEventId = mRoomSummary.getReadMarkerEventId();
        mVectorMessageListFragment.getMessageAdapter().updateReadMarker(mReadMarkerEventId, mRoomSummary.getReadReceiptEventId());
    }

    /**
     * Refresh the current read marker event id and make all the checks again
     */
    private void refresh() {
        Log.d(LOG_TAG, "refresh");
        updateReadMarkerValue();
        updateJumpToBanner();
        checkUnreadMessage();
    }

    /**
     * Check if we display "Jump to" banner
     */
    private synchronized void updateJumpToBanner() {
        //Log.d(LOG_TAG, "updateJumpToBanner");
        boolean showJumpToView = false;

        mReadMarkerEventId = mRoomSummary.getReadMarkerEventId();
        if (mRoomSummary != null && mReadMarkerEventId != null && !mHasJumpedToFirstUnread) {
            final String readReceiptEventId = mRoomSummary.getReadReceiptEventId();

            if (!mReadMarkerEventId.equals(readReceiptEventId)) {
                if (!MXPatterns.isEventId(mReadMarkerEventId)) {
                    // Read marker is invalid, ignore it as it should not occur
                    Log.e(LOG_TAG, "updateJumpToBanner: Read marker event id is invalid, ignore it as it should not occur");
                } else {
                    final Event readMarkerEvent = getEvent(mReadMarkerEventId);
                    if (readMarkerEvent == null) {
                        // Event is not in store so we assume it is further in the past
                        // Note: preview will be opened to the last read since we have no way to
                        // determine the event id of the first unread
                        showJumpToView = true;
                    } else {
                        // Last read event is in the store
                        Collection<Event> roomMessagesCol = mRoom.getDataHandler().getStore().getRoomMessages(mRoom.getRoomId());
                        if (roomMessagesCol == null) {
                            Log.e(LOG_TAG, "updateJumpToBanner getRoomMessages returned null instead of collection with event " + readMarkerEvent.eventId);
                        } else {
                            List<Event> roomMessages = new ArrayList<>(roomMessagesCol);
                            final int lastReadEventIndex = roomMessages.indexOf(readMarkerEvent);
                            final int firstUnreadEventIndex = lastReadEventIndex != -1 ? lastReadEventIndex + 1 : -1;
                            if (firstUnreadEventIndex != -1 && firstUnreadEventIndex < roomMessages.size()) {
                                final Event firstUnreadEvent = roomMessages.get(firstUnreadEventIndex);
                                if (mFirstVisibleEvent != null && firstUnreadEvent != null) {
                                    if (firstUnreadEvent.getOriginServerTs() > mFirstVisibleEvent.getOriginServerTs()) {
                                        // Beginning of first unread message is visible
                                        showJumpToView = false;
                                    } else if (firstUnreadEvent.getOriginServerTs() == mFirstVisibleEvent.getOriginServerTs()) {
                                        // Check if beginning of first unread message is visible
                                        final ListView listView = mVectorMessageListFragment.getMessageListView();
                                        final View firstUnreadView = listView != null ? listView.getChildAt(0) : null;
                                        showJumpToView = firstUnreadView != null && firstUnreadView.getTop() < 0;
                                        if (mHasJumpedToFirstUnread && !showJumpToView) {
                                            forgetReadMarker();
                                        }
                                    } else {
                                        // Beginning of first unread message is hidden
                                        showJumpToView = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (mVectorMessageListFragment.getMessageAdapter() != null) {
                mVectorMessageListFragment.getMessageAdapter().updateReadMarker(mReadMarkerEventId, readReceiptEventId);
            }
        }

        // Update "jump to" view's visibility
        if (isLiveMode() && showJumpToView) {
            mJumpToUnreadViewSpinner.setVisibility(View.GONE);
            mCloseJumpToUnreadView.setVisibility(View.VISIBLE);
            mJumpToUnreadView.setVisibility(View.VISIBLE);
        } else {
            mJumpToUnreadView.setVisibility(View.GONE);
        }

        if (mHasJumpedToBottom) {
            mHasJumpedToBottom = false;
            checkUnreadMessage();
        }
    }

    /**
     * Try to retrieve an event from its id
     *
     * @param eventId
     * @return event if found
     */
    private Event getEvent(final String eventId) {
        MessageRow readMarkerRow = mVectorMessageListFragment.getMessageAdapter().getMessageRow(eventId);
        Event readMarkerEvent = readMarkerRow != null ? readMarkerRow.getEvent() : null;
        if (readMarkerEvent == null) {
            readMarkerEvent = mVectorMessageListFragment.getEventTimeLine().getStore().getEvent(mReadMarkerEventId, mRoom.getRoomId());
        }
        return readMarkerEvent;
    }

    /**
     * Get whether we are in live mode or not
     *
     * @return
     */
    private boolean isLiveMode() {
        return mUpdateMode == LIVE_MODE;
    }

    /**
     * Open the room in preview mode to the given event id
     *
     * @param eventId
     */
    private void openPreviewToGivenEvent(final String eventId) {
        if (!TextUtils.isEmpty(eventId)) {
            Intent intent = new Intent(mActivity, VectorRoomActivity.class);
            intent.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
            intent.putExtra(MXCActionBarActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
            intent.putExtra(VectorRoomActivity.EXTRA_EVENT_ID, eventId);
            intent.putExtra(VectorRoomActivity.EXTRA_IS_UNREAD_PREVIEW_MODE, true);
            mActivity.startActivityForResult(intent, VectorRoomActivity.UNREAD_PREVIEW_REQUEST_CODE);
        }
    }

    /**
     * Scroll up to the given event id or open preview as a last resort
     *
     * @param event event we want to scroll up to
     */
    private void scrollUpToGivenEvent(final Event event) {
        if (event != null) {
            mCloseJumpToUnreadView.setVisibility(View.GONE);
            mJumpToUnreadViewSpinner.setVisibility(View.VISIBLE);
            Log.d(LOG_TAG, "scrollUpToGivenEvent " + event.eventId);
            if (!scrollToAdapterEvent(event)) {
                // use the cached events list
                mRoom.getTimeline().backPaginate(UNREAD_BACK_PAGINATE_EVENT_COUNT, true, new ApiCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer info) {
                        if (!mActivity.isFinishing()) {
                            mVectorMessageListFragment.getMessageAdapter().notifyDataSetChanged();
                            if (!scrollToAdapterEvent(event)) {
                                openPreviewToGivenEvent(event.eventId);
                            }
                        }
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        openPreviewToGivenEvent(event.eventId);
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        openPreviewToGivenEvent(event.eventId);
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        openPreviewToGivenEvent(event.eventId);
                    }
                });
            }
        }
    }

    /**
     * Try to scroll to the given event
     *
     * @param event
     * @return true if event was in adapter and have been scrolled to
     */
    private boolean scrollToAdapterEvent(final Event event) {
        final MessageRow lastReadRow = mVectorMessageListFragment.getMessageAdapter() != null
                ? mVectorMessageListFragment.getMessageAdapter().getMessageRow(event.eventId)
                : null;
        if (lastReadRow != null) {
            scrollToRow(lastReadRow, true);
            return true;
        } else {
            Log.d(LOG_TAG, "scrollToAdapterEvent: need to load more events in adapter or eventId is not displayed");

            if (mVectorMessageListFragment.getMessageAdapter().getCount() > 0) {
                final MessageRow firstRow = mVectorMessageListFragment.getMessageAdapter()
                        .getItem(0);
                final Event firstEvent = firstRow != null ? firstRow.getEvent() : null;
                final MessageRow lastRow = mVectorMessageListFragment.getMessageAdapter()
                        .getItem(mVectorMessageListFragment.getMessageAdapter().getCount() - 1);
                final Event lastEvent = lastRow != null ? lastRow.getEvent() : null;
                if (firstEvent != null && lastEvent != null && event.getOriginServerTs() > firstEvent.getOriginServerTs()
                        && event.getOriginServerTs() < lastEvent.getOriginServerTs()) {
                    // Event should be in adapter
                    final MessageRow closestRowFromEvent = mVectorMessageListFragment.getMessageAdapter().getClosestRow(event);
                    if (closestRowFromEvent != null) {
                        scrollToRow(closestRowFromEvent, closestRowFromEvent.getEvent().eventId.equals(event.eventId));
                        return true;
                    }
                    return false;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    /**
     * Scroll to the given message row or the row right after the given one if it is the last read
     *
     * @param messageRow
     * @param isLastRead
     */
    private void scrollToRow(final MessageRow messageRow, final boolean isLastRead) {
        mVectorMessageListFragment.getMessageListView().post(new Runnable() {
            @Override
            public void run() {
                mVectorMessageListFragment.scrollToRow(messageRow, isLastRead);
                mHasJumpedToFirstUnread = true;
            }
        });
    }

    /**
     * Update the read marker position to put it on the last visible row
     */
    private void setReadMarkerToLastVisibleRow() {
        Log.d(LOG_TAG, "setReadMarkerToLastVisibleRow");
        // Update the read marker to the last message completely displayed
        final ListView messageListView = mVectorMessageListFragment.getMessageListView();
        if (messageListView != null && messageListView.getChildCount() != 0 && mVectorMessageListFragment.getMessageAdapter() != null) {
            Event newReadMarkerEvent;
            final int lastVisiblePos = messageListView.getLastVisiblePosition();
            final View lastVisibleRowView = messageListView.getChildAt(messageListView.getChildCount() - 1);
            if (lastVisibleRowView.getBottom() <= messageListView.getBottom()) {
                // Last visible message is entirely displayed, move read marker to that message
                newReadMarkerEvent = mVectorMessageListFragment.getEvent(lastVisiblePos);
            } else {
                // Move read marker to the message before the last visible one
                newReadMarkerEvent = mVectorMessageListFragment.getEvent(lastVisiblePos - 1);
            }

            // Update read marker
            // In preview mode, check events from adapter and only update if new read marker is more recent
            final Event currentReadMarkerEvent = getEvent(mReadMarkerEventId);
            if (currentReadMarkerEvent != null) {
                final long currentReadMarkerTs = currentReadMarkerEvent.getOriginServerTs();
                final MessageRow closestRow = mVectorMessageListFragment.getMessageAdapter().getClosestRow(newReadMarkerEvent);

                if (null != closestRow) {
                    final Event closestEvent = closestRow.getEvent();
                    final long newReadMarkerTs = closestEvent.getOriginServerTs();
                    Log.v(LOG_TAG, "setReadMarkerToLastVisibleRow currentReadMarkerEvent:" + currentReadMarkerEvent.eventId
                            + " TS:" + currentReadMarkerTs + " closestEvent:" + closestEvent.eventId + " TS:" + closestEvent.getOriginServerTs());
                    if (newReadMarkerTs > currentReadMarkerTs) {
                        Log.d(LOG_TAG, "setReadMarkerToLastVisibleRow update read marker to:" + newReadMarkerEvent.eventId
                                + " isEventId:" + MXPatterns.isEventId(newReadMarkerEvent.eventId));
                        mRoom.setReadMakerEventId(newReadMarkerEvent.eventId);
                        onReadMarkerChanged(mRoom.getRoomId());
                    }
                }
            }
        }
    }

    /**
     * Mark all as read
     */
    private void markAllAsRead() {
        Log.d(LOG_TAG, "markAllAsRead");
        mRoom.markAllAsRead(null);
    }

    /**
     * Forget the current read marker (read marker event will be same as read receipt event)
     */
    private void forgetReadMarker() {
        Log.d(LOG_TAG, "forgetReadMarker");
        mRoom.forgetReadMarker(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                updateJumpToBanner();
            }

            @Override
            public void onNetworkError(Exception e) {
                updateJumpToBanner();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                updateJumpToBanner();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                updateJumpToBanner();
            }
        });
    }

    /*
     * *********************************************************************************************
     * Listener
     * *********************************************************************************************
     */

    @Override
    public void onReadMarkerDisplayed(Event event, View view) {
        Log.d(LOG_TAG, "onReadMarkerDisplayed for " + event.eventId);
        if (!mActivity.isFinishing()) {
            if (mLastVisibleEvent == null) {
                // In case it is triggered before any onScroll callback
                // crash reported by rage shake
                try {
                    mLastVisibleEvent = mVectorMessageListFragment.getEvent(mVectorMessageListFragment.getMessageListView().getLastVisiblePosition());
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## onReadMarkerDisplayed() : crash while retrieving mLastVisibleEvent " + e.getMessage(), e);
                }
            }

            if (mFirstVisibleEvent == null) {
                // In case it is triggered before any onScroll callback
                // crash reported by rage shake
                try {
                    mFirstVisibleEvent = mVectorMessageListFragment.getEvent(mVectorMessageListFragment.getMessageListView().getFirstVisiblePosition());
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## onReadMarkerDisplayed() : crash while retrieving mFirstVisibleEvent " + e.getMessage(), e);
                }
            }

            checkUnreadMessage();
        }
    }

}
