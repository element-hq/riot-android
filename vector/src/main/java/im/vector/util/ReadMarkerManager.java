package im.vector.util;

import android.content.Intent;
import android.graphics.Paint;
import android.support.annotation.IntDef;
import android.text.TextUtils;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import im.vector.R;
import im.vector.activity.MXCActionBarActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.fragments.VectorMessageListFragment;

public class ReadMarkerManager implements MessagesAdapter.ReadMarkerListener {

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

    // Views allowing to open preview starting at the first unread message
    private View mJumpToUnreadView;
    private View mCloseJumpToUnreadView;
    private View mJumpToUnreadViewSpinner;

    private VectorRoomActivity mActivity;
    private VectorMessageListFragment mVectorMessageListFragment;
    private MXSession mSession;
    private Room mRoom;
    private RoomSummary mRoomSummary;

    private String mReadMarkerEventId;

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
     * Public methods
     * *********************************************************************************************
     */

    public ReadMarkerManager(final VectorRoomActivity activity, final VectorMessageListFragment messageListFragment,
                             final MXSession session, final Room room,
                             @UpdateMode final int updateMode, final View jumpToFirstUnreadView) {
        Log.d(LOG_TAG, "Create ReadMarkerManager instance ");
        if (room == null) {
            return;
        }

        mActivity = activity;
        mVectorMessageListFragment = messageListFragment;
        mSession = session;

        mRoom = room;
        mRoomSummary = mRoom.getDataHandler().getStore().getSummary(mRoom.getRoomId());

        mReadMarkerEventId = mRoomSummary.getReadMarkerEventId();

        mUpdateMode = updateMode;

        if (jumpToFirstUnreadView != null) {
            mJumpToUnreadView = jumpToFirstUnreadView;
            TextView jumpToUnreadLabel = (TextView) jumpToFirstUnreadView.findViewById(R.id.jump_to_first_unread_label);
            jumpToUnreadLabel.setPaintFlags(jumpToUnreadLabel.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            // Actions views
            mCloseJumpToUnreadView = jumpToFirstUnreadView.findViewById(R.id.close_jump_to_first_unread);
            mJumpToUnreadViewSpinner = jumpToFirstUnreadView.findViewById(R.id.jump_to_read_spinner);

            if (isLiveMode()) {
                jumpToUnreadLabel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
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

    public void onResume() {
        mVectorMessageListFragment.getMessageAdapter().setReadMarkerListener(this);
        updateJumpToBanner();
    }

    public void onPause() {
        if (!isLiveMode() || mHasJumpedToFirstUnread) {
            setReadMarkerToLastVisibleRow();
        }
    }

    public void onScroll(final int firstVisibleItem, final int visibleItemCount, final int totalItemCount,
                         final Event eventAtTop, final Event eventAtBottom) {
        mFirstVisibleEvent = eventAtTop;
        mLastVisibleEvent = eventAtBottom;

        if (isLiveMode()) {
            updateJumpToBanner();
        } else if (mVectorMessageListFragment.getEventTimeLine().hasReachedHomeServerForwardsPaginationEnd()) {
            // Display "You've caught up" message if necessary
            final ListView messageListView = mVectorMessageListFragment.getMessageListView();
            if (messageListView != null && firstVisibleItem + visibleItemCount == totalItemCount &&
                    messageListView.getChildAt(messageListView.getChildCount() - 1).getBottom() == messageListView.getBottom()) {
                Toast.makeText(mActivity, org.matrix.androidsdk.R.string.unread_messages_footer, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void onScrollStateChanged(final int scrollState) {
        switch (scrollState) {
            case 0:
                Log.e(LOG_TAG, "onScrollStateChanged SCROLL_STATE_IDLE");
                break;
            case 1:
                Log.e(LOG_TAG, "onScrollStateChanged SCROLL_STATE_TOUCH_SCROLL");
                break;
            case 2:
                Log.e(LOG_TAG, "onScrollStateChanged SCROLL_STATE_FLING");
                break;
        }

        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE
                && (mScrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING || mScrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)) {
            Log.e(LOG_TAG, "User scrolled");
            checkUnreadMessage();
        }

        mScrollState = scrollState;
    }

    private void checkUnreadMessage() {
        Log.e(LOG_TAG, "############ checkUnreadMessage START");
        if (mJumpToUnreadView.getVisibility() != View.VISIBLE) {
            /*
             * - Check if we must display the read marker line
             * - Update the read marker if necessary
             */
            int showReadMarkerLine = -1;
            boolean updateReadMarker = false;
            final String readReceiptEventId = mRoomSummary.getReadReceiptEventId();
            if (!mReadMarkerEventId.equals(readReceiptEventId)) {
                if (isLiveMode() && !mHasJumpedToFirstUnread) {
                    // Catching up as scrolling up
                    // Check if the first unread has been reached by scrolling up
                    Log.e(LOG_TAG, "## checkUnreadMessage : Check if the first unread has been reached by scrolling up");
                    MessageRow unreadRow = mVectorMessageListFragment.getMessageAdapter().getMessageRow(mReadMarkerEventId);
                    if (unreadRow != null && unreadRow.getEvent().getOriginServerTs() >= mFirstVisibleEvent.getOriginServerTs()) {
                        Log.e(LOG_TAG, "## checkUnreadMessage : first unread has been reached by scrolling up");
                        forgetReadMarker();
                    }
                } else {
                    // Catching up as scrolling down
                    // Check if the last received event has been reached by scrolling down
                    if (mLastVisibleEvent.eventId.equals(mRoomSummary.getLatestReceivedEvent().eventId)) {
                        Log.e(LOG_TAG, "## checkUnreadMessage : last received event has been reached by scrolling down");
                        markAllAsRead();
                    }
                }
            }
        }
    }

    /**
     * Handle jump to bottom action
     */
    public void handleJumpToBottom() {
        Log.e(LOG_TAG, "handleJumpToBottom " + mReadMarkerEventId);
        // Set flag to be sure we check unread messages after updating the "Jump to" view
        // since on onScrollStateChanged will not be triggered
        mVectorMessageListFragment.getMessageAdapter().updateReadMarker(mReadMarkerEventId, mRoomSummary.getReadReceiptEventId());
        mHasJumpedToBottom = true;
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Check if we display "Jump to" banner
     */
    private synchronized void updateJumpToBanner() {
        Log.e(LOG_TAG, "############ updateJumpToBanner START");
        boolean showJumpToView = false;

        if (mRoomSummary != null && mReadMarkerEventId != null && !mHasJumpedToFirstUnread) {
            mReadMarkerEventId = mRoomSummary.getReadMarkerEventId();
            final String readReceiptEventId = mRoomSummary.getReadReceiptEventId();

            Log.d(LOG_TAG, "## updateJumpToBanner readMarkerEventId " + mReadMarkerEventId + " readReceiptEventId " + readReceiptEventId);

            if (!mReadMarkerEventId.equals(readReceiptEventId)) {
                if (!MXSession.isMessageId(mReadMarkerEventId)) {
                    // Read marker is invalid, ignore it as it should not occur
                    //TODO call
                    //getClosestEvent()
                    Log.e(LOG_TAG, "## updateJumpToBanner: Read marker is invalid, ignore it as it should not occur");
                } else {
                    List<Event> roomMessages = new ArrayList<>(mRoom.getDataHandler().getStore().getRoomMessages(mRoom.getRoomId()));
                    final Event readMarkerEvent = getEvent(mReadMarkerEventId);
                    if (readMarkerEvent == null) {
                        // Event is not in store so we assume it is further in the past
                        // Note: preview will be opened to the last read since we have no way to
                        // determine the event id of the first unread
                        showJumpToView = true;
                    } else {
                        // Last read event is in the store
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
                                    Log.e(LOG_TAG, "updateJumpToBanner Check if beginning of first unread message is visible. showJumpToView:" + showJumpToView);
                                    if (mHasJumpedToFirstUnread && !showJumpToView) {
                                        forgetReadMarker();
                                    }
                                } else {
                                    // Beginning of first unread message is hidden
                                    showJumpToView = true;
                                }
                                Log.e(LOG_TAG, "updateJumpToBanner showJumpToView :" + showJumpToView);
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

        Log.d(LOG_TAG, "############ updateJumpToBanner END");
    }

    /**
     * Try to retrieve an event from its id
     *
     * @param eventId
     * @return event if found
     */
    private Event getEvent(final String eventId){
        MessageRow readMarkerRow = mVectorMessageListFragment.getMessageAdapter().getMessageRow(eventId);
        Event readMarkerEvent = readMarkerRow != null ? readMarkerRow.getEvent() : null;
        if (readMarkerEvent == null) {
            readMarkerEvent = mVectorMessageListFragment.getEventTimeLine().getStore().getEvent(mReadMarkerEventId, mRoom.getRoomId());
        }
        return readMarkerEvent;
    }

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
            mActivity.startActivity(intent);
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
            Log.e(LOG_TAG, "scrollUpToGivenEvent " + event.eventId);
            if (!scrollToAdapterEvent(event)) {
                Log.e(LOG_TAG, "scrollUpToGivenEvent load more events in adapter" + event.eventId);
                mRoom.getLiveTimeLine().backPaginate(UNREAD_BACK_PAGINATE_EVENT_COUNT, new ApiCallback<Integer>() {
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
        Log.e(LOG_TAG, "scrollToAdapterEvent row " + lastReadRow);
        if (lastReadRow != null) {
            scrollToRow(lastReadRow);
            return true;
        } else {
            Log.e(LOG_TAG, "scrollToAdapterEvent need to load more events in adapter or eventId is not displayed");
            final MessageRow firstRow = mVectorMessageListFragment.getMessageAdapter().getItem(0);
            final Event firstEvent = firstRow != null ? firstRow.getEvent() : null;
            final MessageRow lastRow = mVectorMessageListFragment.getMessageAdapter().getItem(mVectorMessageListFragment.getMessageAdapter().getCount() - 1);
            final Event lastEvent = lastRow != null ? lastRow.getEvent() : null;
            if (firstEvent != null && lastEvent != null && event.getOriginServerTs() > firstEvent.getOriginServerTs()
                    && event.getOriginServerTs() < lastEvent.getOriginServerTs()) {
                // Event should be in adapter
                final MessageRow closestRowFromEvent = mVectorMessageListFragment.getMessageAdapter().getClosestRow(event);
                if (closestRowFromEvent != null) {
                    scrollToRow(closestRowFromEvent);
                    return true;
                }
                return false;
            } else {
                return false;
            }
        }
    }

    /**
     * Scroll to the given message row
     *
     * @param messageRow
     */
    private void scrollToRow(final MessageRow messageRow) {
        mVectorMessageListFragment.scrollToRow(messageRow);
        mHasJumpedToFirstUnread = true;
    }

    /**
     * Update the read marker position to put it on the last visible row
     */
    private void setReadMarkerToLastVisibleRow() {
        Log.e(LOG_TAG, "setReadMarkerToLastVisibleRow row ");
        // Update the read marker to the last message completely displayed
        final ListView messageListView = mVectorMessageListFragment.getMessageListView();
        if (messageListView != null && mVectorMessageListFragment.getMessageAdapter() != null) {
            Event newReadMarkerEvent;
            final int lastVisiblePos = messageListView.getLastVisiblePosition();
            //final MessageRow lastVisibleRow = mVectorMessageListFragment.getMessageAdapter().getItem(lastVisiblePos);
            final View lastVisibleRowView = messageListView.getChildAt(messageListView.getChildCount() - 1);
            if (lastVisibleRowView.getBottom() <= messageListView.getBottom()) {
                // Last visible message is entirely displayed, move read marker to that message
                newReadMarkerEvent = mVectorMessageListFragment.getEvent(lastVisiblePos);
                Log.e(LOG_TAG, "setReadMarkerToLastVisibleRow A " + newReadMarkerEvent.eventId);
            } else {
                // Move read marker to the message before the last visible one
                newReadMarkerEvent = mVectorMessageListFragment.getEvent(lastVisiblePos - 1);
                Log.e(LOG_TAG, "setReadMarkerToLastVisibleRow B " + newReadMarkerEvent.eventId);
            }

            // Update read marker
            if (!isLiveMode()) {
                // In preview mode, check events from adapter and only update if new read marker is more recent
                final long currentReadMarkerTs = mVectorMessageListFragment.getMessageAdapter().getMessageRow(mReadMarkerEventId).getEvent().getOriginServerTs();
                final long newReadMarkerTs = mVectorMessageListFragment.getMessageAdapter().getClosestRow(newReadMarkerEvent).getEvent().getOriginServerTs();
                if (newReadMarkerTs > currentReadMarkerTs) {
                    mRoom.setReadMakerEventId(newReadMarkerEvent.eventId);
                }
            } else {
                mRoom.setReadMakerEventId(newReadMarkerEvent.eventId);
            }
        }
    }

    /**
     * Mark all as read
     */
    private void markAllAsRead() {
        Log.e(LOG_TAG, "markAllAsRead");
        mRoom.markAllAsRead(null);
    }

    /**
     * Forget the current read marker (read marker event will be same as read receipt event)
     */
    private void forgetReadMarker() {
        Log.e(LOG_TAG, "forgetReadMarker");
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
        Log.e(LOG_TAG, "onReadMarkerDisplayed for " + event.eventId);
        checkUnreadMessage();
    }

}
