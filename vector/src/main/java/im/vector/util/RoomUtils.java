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

package im.vector.util;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomAccountData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.AdapterUtils;

public class RoomUtils {

    private static final String LOG_TAG = RoomUtils.class.getSimpleName();

    public interface MoreActionListener {
        void onToggleRoomNotifications(MXSession session, String roomId);

        void onToggleDirectChat(MXSession session, String roomId);

        void moveToFavorites(MXSession session, String roomId);

        void moveToConversations(MXSession session, String roomId);

        void moveToLowPriority(MXSession session, String roomId);

        void onLeaveRoom(MXSession session, String roomId);
    }

    public interface HistoricalRoomActionListener {
        void onForgotRoom(Room room);
    }

    /**
     * Return comparator to sort rooms by date
     *
     * @param session
     * @param reverseOrder
     * @return comparator
     */
    public static Comparator<Room> getRoomsDateComparator(final MXSession session, final boolean reverseOrder) {
        return new Comparator<Room>() {
            private Comparator<RoomSummary> mRoomSummaryComparator;
            private HashMap<String, RoomSummary> mSummaryByRoomIdMap = new HashMap<>();

            /**
             * Retrieve the room summary comparator
             * @return comparator
             */
            private Comparator<RoomSummary> getSummaryComparator() {
                if (null == mRoomSummaryComparator) {
                    mRoomSummaryComparator = getRoomSummaryComparator(reverseOrder);
                }
                return mRoomSummaryComparator;
            }

            /**
             * Retrieve a summary from its room id
             * @param roomId
             * @return the summary
             */
            private RoomSummary getSummary(String roomId) {
                if (TextUtils.isEmpty(roomId)) {
                    return null;
                }

                RoomSummary summary = mSummaryByRoomIdMap.get(roomId);

                if (null == summary) {
                    summary = session.getDataHandler().getStore().getSummary(roomId);

                    if (null != summary) {
                        mSummaryByRoomIdMap.put(roomId, summary);
                    }
                }

                return summary;
            }

            public int compare(Room aLeftObj, Room aRightObj) {
                final RoomSummary leftRoomSummary = getSummary(aLeftObj.getRoomId());
                final RoomSummary rightRoomSummary = getSummary(aRightObj.getRoomId());

                return getSummaryComparator().compare(leftRoomSummary, rightRoomSummary);
            }
        };
    }

    /**
     * Return comparator to sort rooms by:
     * 1- the highlighted rooms (sub sorted by date) if pinMissedNotifications is true
     * 2- the notified rooms (sub sorted by date) if pinMissedNotifications is true
     * 3- the unread rooms if pinUnreadMessages is true
     * 4- latest event timestamp
     *
     * @param session                the session
     * @param pinMissedNotifications whether missed notifications should be pinned
     * @param pinUnreadMessages      whether unread messages should be pinned
     * @return comparator
     */
    public static Comparator<Room> getNotifCountRoomsComparator(final MXSession session, final boolean pinMissedNotifications, final boolean pinUnreadMessages) {
        return new Comparator<Room>() {
            private Comparator<RoomSummary> mRoomSummaryComparator;
            private HashMap<String, RoomSummary> mSummaryByRoomIdMap = new HashMap<>();

            /**
             * Retrieve the room summary comparator
             * @return comparator
             */
            private Comparator<RoomSummary> getSummaryComparator() {
                if (null == mRoomSummaryComparator) {
                    mRoomSummaryComparator = getNotifCountRoomSummaryComparator(session.getDataHandler().getBingRulesManager(), pinMissedNotifications, pinUnreadMessages);
                }
                return mRoomSummaryComparator;
            }

            /**
             * Retrieve a summary from its room id
             * @param roomId
             * @return the summary
             */
            private RoomSummary getSummary(String roomId) {
                if (TextUtils.isEmpty(roomId)) {
                    return null;
                }

                RoomSummary summary = mSummaryByRoomIdMap.get(roomId);

                if (null == summary) {
                    summary = session.getDataHandler().getStore().getSummary(roomId);

                    if (null != summary) {
                        mSummaryByRoomIdMap.put(roomId, summary);
                    }
                }

                return summary;
            }


            public int compare(Room aLeftObj, Room aRightObj) {
                final RoomSummary leftRoomSummary = getSummary(aLeftObj.getRoomId());
                final RoomSummary rightRoomSummary = getSummary(aRightObj.getRoomId());

                return getSummaryComparator().compare(leftRoomSummary, rightRoomSummary);
            }
        };
    }

    /**
     * Return comparator to sort historical rooms by date
     *
     * @param session
     * @param reverseOrder
     * @return comparator
     */
    public static Comparator<Room> getHistoricalRoomsComparator(final MXSession session, final boolean reverseOrder) {
        return new Comparator<Room>() {
            public int compare(Room aLeftObj, Room aRightObj) {
                final RoomSummary leftRoomSummary = session.getDataHandler().getStore(aLeftObj.getRoomId()).getSummary(aLeftObj.getRoomId());
                final RoomSummary rightRoomSummary = session.getDataHandler().getStore(aRightObj.getRoomId()).getSummary(aRightObj.getRoomId());

                return getRoomSummaryComparator(reverseOrder).compare(leftRoomSummary, rightRoomSummary);
            }
        };
    }

    /**
     * Return a comparator to sort summaries by latest event
     *
     * @param reverseOrder
     * @return ordered list
     */
    public static Comparator<RoomSummary> getRoomSummaryComparator(final boolean reverseOrder) {
        return new Comparator<RoomSummary>() {
            public int compare(RoomSummary leftRoomSummary, RoomSummary rightRoomSummary) {
                int retValue;
                long deltaTimestamp;

                if ((null == leftRoomSummary) || (null == leftRoomSummary.getLatestReceivedEvent())) {
                    retValue = 1;
                } else if ((null == rightRoomSummary) || (null == rightRoomSummary.getLatestReceivedEvent())) {
                    retValue = -1;
                } else if ((deltaTimestamp = rightRoomSummary.getLatestReceivedEvent().getOriginServerTs()
                        - leftRoomSummary.getLatestReceivedEvent().getOriginServerTs()) > 0) {
                    retValue = 1;
                } else if (deltaTimestamp < 0) {
                    retValue = -1;
                } else {
                    retValue = 0;
                }

                return reverseOrder ? -retValue : retValue;
            }
        };
    }

    /**
     * Return comparator to sort room summaries  by
     * 1- the highlighted rooms (sub sorted by date) if pinMissedNotifications is true
     * 2- the notified rooms (sub sorted by date) if pinMissedNotifications is true
     * 3- the unread rooms if pinUnreadMessages is true
     * 4- latest event timestamp
     *
     * @param bingRulesManager       the bing rules manager
     * @param pinMissedNotifications whether missed notifications should be pinned
     * @param pinUnreadMessages      whether unread messages should be pinned
     * @return comparator
     */
    public static Comparator<RoomSummary> getNotifCountRoomSummaryComparator(final BingRulesManager bingRulesManager,
                                                                             final boolean pinMissedNotifications,
                                                                             final boolean pinUnreadMessages) {
        return new Comparator<RoomSummary>() {
            public int compare(RoomSummary leftRoomSummary, RoomSummary rightRoomSummary) {
                int retValue;
                long deltaTimestamp;
                int leftHighlightCount = 0, rightHighlightCount = 0;
                int leftNotificationCount = 0, rightNotificationCount = 0;
                int leftUnreadCount = 0, rightUnreadCount = 0;

                if (null != leftRoomSummary) {
                    leftHighlightCount = leftRoomSummary.getHighlightCount();
                    leftNotificationCount = leftRoomSummary.getNotificationCount();
                    leftUnreadCount = leftRoomSummary.getUnreadEventsCount();

                    if (bingRulesManager.isRoomMentionOnly(leftRoomSummary.getRoomId())) {
                        leftNotificationCount = leftHighlightCount;
                    }
                }

                if (null != rightRoomSummary) {
                    rightHighlightCount = rightRoomSummary.getHighlightCount();
                    rightNotificationCount = rightRoomSummary.getNotificationCount();
                    rightUnreadCount = rightRoomSummary.getUnreadEventsCount();

                    if (bingRulesManager.isRoomMentionOnly(rightRoomSummary.getRoomId())) {
                        rightNotificationCount = rightHighlightCount;
                    }
                }

                if ((null == leftRoomSummary) || (null == leftRoomSummary.getLatestReceivedEvent())) {
                    retValue = 1;
                } else if ((null == rightRoomSummary) || (null == rightRoomSummary.getLatestReceivedEvent())) {
                    retValue = -1;
                } else if (pinMissedNotifications && (rightHighlightCount > 0) && (leftHighlightCount == 0)) {
                    retValue = 1;
                } else if (pinMissedNotifications && (rightHighlightCount == 0) && (leftHighlightCount > 0)) {
                    retValue = -1;
                } else if (pinMissedNotifications && (rightNotificationCount > 0) && (leftNotificationCount == 0)) {
                    retValue = 1;
                } else if (pinMissedNotifications && (rightNotificationCount == 0) && (leftNotificationCount > 0)) {
                    retValue = -1;
                } else if(pinUnreadMessages && (rightUnreadCount > 0) && (leftUnreadCount == 0)) {
                    retValue = 1;
                } else if(pinUnreadMessages && (rightUnreadCount == 0) && (leftUnreadCount > 0)) {
                    retValue = -1;
                } else if ( (deltaTimestamp = rightRoomSummary.getLatestReceivedEvent().getOriginServerTs()
                        - leftRoomSummary.getLatestReceivedEvent().getOriginServerTs()) > 0) {
                    retValue = 1;
                } else if (deltaTimestamp < 0) {
                    retValue = -1;
                } else {
                    retValue = 0;
                }

                return retValue;
            }
        };
    }

    /**
     * Get a comparator to sort tagged rooms
     *
     * @param taggedRoomsById
     * @return comparator
     */
    public static Comparator<Room> getTaggedRoomComparator(final List<String> taggedRoomsById) {
        return new Comparator<Room>() {
            public int compare(Room r1, Room r2) {
                return taggedRoomsById.indexOf(r1.getRoomId()) - taggedRoomsById.indexOf(r2.getRoomId());
            }
        };
    }

    /**
     * Provides the formatted timestamp for the room
     *
     * @param context
     * @param latestEvent the latest event.
     * @return the formatted timestamp to display.
     */
    public static String getRoomTimestamp(final Context context, final Event latestEvent) {
        String text = AdapterUtils.tsToString(context, latestEvent.getOriginServerTs(), false);

        // don't display the today before the time
        String today = context.getString(R.string.today) + " ";
        if (text.startsWith(today)) {
            text = text.substring(today.length());
        }

        return text;
    }

    /**
     * Retrieve the text to display for a RoomSummary.
     *
     * @param context
     * @param session
     * @param roomSummary the roomSummary.
     * @return the text to display.
     */
    public static CharSequence getRoomMessageToDisplay(final Context context, final MXSession session,
                                                       final RoomSummary roomSummary) {
        CharSequence messageToDisplay = null;
        EventDisplay eventDisplay;

        if (null != roomSummary) {
            if (roomSummary.getLatestReceivedEvent() != null) {
                eventDisplay = new EventDisplay(context, roomSummary.getLatestReceivedEvent(), roomSummary.getLatestRoomState());
                eventDisplay.setPrependMessagesWithAuthor(true);
                messageToDisplay = eventDisplay.getTextualDisplay(ThemeUtils.getColor(context, R.attr.vector_text_gray_color));
            }

            // check if this is an invite
            if (roomSummary.isInvited() && (null != roomSummary.getInviterUserId())) {
                RoomState latestRoomState = roomSummary.getLatestRoomState();
                String inviterUserId = roomSummary.getInviterUserId();
                String myName = roomSummary.getMatrixId();

                if (null != latestRoomState) {
                    inviterUserId = latestRoomState.getMemberName(inviterUserId);
                    myName = latestRoomState.getMemberName(myName);
                } else {
                    inviterUserId = getMemberDisplayNameFromUserId(context, roomSummary.getMatrixId(), inviterUserId);
                    myName = getMemberDisplayNameFromUserId(context, roomSummary.getMatrixId(), myName);
                }

                if (TextUtils.equals(session.getMyUserId(), roomSummary.getMatrixId())) {
                    messageToDisplay = context.getString(org.matrix.androidsdk.R.string.notice_room_invite_you, inviterUserId);
                } else {
                    messageToDisplay = context.getString(org.matrix.androidsdk.R.string.notice_room_invite, inviterUserId, myName);
                }
            }
        }

        return messageToDisplay;
    }

    /**
     * Get the displayable name of the user whose ID is passed in aUserId.
     *
     * @param context
     * @param matrixId matrix ID
     * @param userId   user ID
     * @return the user display name
     */
    private static String getMemberDisplayNameFromUserId(final Context context, final String matrixId,
                                                         final String userId) {
        String displayNameRetValue;
        MXSession session;

        if (null == matrixId || null == userId) {
            displayNameRetValue = null;
        } else if ((null == (session = Matrix.getMXSession(context, matrixId))) || (!session.isAlive())) {
            displayNameRetValue = null;
        } else {
            User user = session.getDataHandler().getStore().getUser(userId);

            if ((null != user) && !TextUtils.isEmpty(user.displayname)) {
                displayNameRetValue = user.displayname;
            } else {
                displayNameRetValue = userId;
            }
        }

        return displayNameRetValue;
    }

    /**
     * See {@link #displayPopupMenu(Context, MXSession, Room, View, boolean, boolean, MoreActionListener, HistoricalRoomActionListener)}
     */
    public static void displayPopupMenu(final Context context, final MXSession session, final Room room,
                                        final View actionView, final boolean isFavorite, final boolean isLowPrior,
                                        @NonNull final MoreActionListener listener) {
        if (listener != null) {
            displayPopupMenu(context, session, room, actionView, isFavorite, isLowPrior, listener, null);
        }
    }


    /**
     * See {@link #displayPopupMenu(Context, MXSession, Room, View, boolean, boolean, MoreActionListener, HistoricalRoomActionListener)}
     */
    public static void displayHistoricalRoomMenu(final Context context, final MXSession session, final Room room,
                                                 final View actionView, @NonNull final HistoricalRoomActionListener listener) {
        if (listener != null) {
            displayPopupMenu(context, session, room, actionView, false, false, null, listener);
        }
    }

    /**
     * Display the room action popup.
     *
     * @param context
     * @param session
     * @param room               the room in which the actions should be triggered in.
     * @param actionView         the anchor view.
     * @param isFavorite         true if it is a favorite room
     * @param isLowPrior         true it it is a low priority room
     * @param moreActionListener
     */
    @SuppressLint("NewApi")
    private static void displayPopupMenu(final Context context, final MXSession session, final Room room,
                                         final View actionView, final boolean isFavorite, final boolean isLowPrior,
                                         final MoreActionListener moreActionListener, final HistoricalRoomActionListener historicalRoomActionListener) {
        // sanity check
        if (null == room) {
            return;
        }

        final PopupMenu popup;

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            popup = new PopupMenu(context, actionView, Gravity.END);
        } else {
            popup = new PopupMenu(context, actionView);
        }
        popup.getMenuInflater().inflate(R.menu.vector_home_room_settings, popup.getMenu());

        if (room.isLeft()) {
            popup.getMenu().setGroupVisible(R.id.active_room_actions, false);
            popup.getMenu().setGroupVisible(R.id.historical_room_actions, true);

            if (historicalRoomActionListener != null) {
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(final MenuItem item) {
                        if (item.getItemId() == R.id.action_forget_room) {
                            historicalRoomActionListener.onForgotRoom(room);
                        }
                        return true;
                    }
                });
            }
        } else {
            popup.getMenu().setGroupVisible(R.id.active_room_actions, true);
            popup.getMenu().setGroupVisible(R.id.historical_room_actions, false);

            MenuItem item;

            final BingRulesManager bingRulesManager = session.getDataHandler().getBingRulesManager();

            if (bingRulesManager.isRoomNotificationsDisabled(room.getRoomId())) {
                item = popup.getMenu().getItem(0);
                item.setIcon(null);
            }

            if (!isFavorite) {
                item = popup.getMenu().getItem(1);
                item.setIcon(null);
            }

            if (!isLowPrior) {
                item = popup.getMenu().getItem(2);
                item.setIcon(null);
            }

            if (session.getDirectChatRoomIdsList().indexOf(room.getRoomId()) < 0) {
                item = popup.getMenu().getItem(3);
                item.setIcon(null);
            }


            if (moreActionListener != null) {
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(final MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.ic_action_select_notifications: {
                                moreActionListener.onToggleRoomNotifications(session, room.getRoomId());
                                break;
                            }
                            case R.id.ic_action_select_fav: {
                                if (isFavorite) {
                                    moreActionListener.moveToConversations(session, room.getRoomId());
                                } else {
                                    moreActionListener.moveToFavorites(session, room.getRoomId());
                                }
                                break;
                            }
                            case R.id.ic_action_select_deprioritize: {
                                if (isLowPrior) {
                                    moreActionListener.moveToConversations(session, room.getRoomId());
                                } else {
                                    moreActionListener.moveToLowPriority(session, room.getRoomId());
                                }
                                break;
                            }
                            case R.id.ic_action_select_remove: {
                                moreActionListener.onLeaveRoom(session, room.getRoomId());
                                break;
                            }
                            case R.id.ic_action_select_direct_chat: {
                                moreActionListener.onToggleDirectChat(session, room.getRoomId());
                                break;
                            }
                        }
                        return false;
                    }
                });
            }
        }

        // force to display the icon
        try {
            Field[] fields = popup.getClass().getDeclaredFields();
            for (Field field : fields) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(popup);
                    Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
                    Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                    setForceIcons.invoke(menuPopupHelper, true);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## displayPopupMenu() : failed " + e.getMessage());
        }

        popup.show();
    }

    /**
     * Display a confirmation dialog when user wants to leave a room
     *
     * @param context
     * @param onClickListener
     */
    public static void showLeaveRoomDialog(final Context context, final DialogInterface.OnClickListener onClickListener) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.room_participants_leave_prompt_title)
                .setMessage(R.string.room_participants_leave_prompt_msg)
                .setPositiveButton(R.string.leave, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        onClickListener.onClick(dialog, which);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create()
                .show();
    }

    /**
     * Update a room Tag
     *
     * @param session the session
     * @param roomId the room Id
     * @param aTagOrder the new tag order
     * @param newTag the new Tag
     * @param apiCallback the asynchronous callback
     */
    public static void updateRoomTag(final MXSession session, final String roomId, final Double aTagOrder, final String newTag, final ApiCallback<Void> apiCallback) {
        Room room = session.getDataHandler().getRoom(roomId);

        if (null != room) {
            String oldTag = null;

            // retrieve the tag from the room info
            RoomAccountData accountData = room.getAccountData();

            if ((null != accountData) && accountData.hasTags()) {
                oldTag = accountData.getKeys().iterator().next();
            }

            Double tagOrder = aTagOrder;

            // if the tag order is not provided, compute it
            if (null == tagOrder) {
                tagOrder = 0.0;

                if (null != newTag) {
                    tagOrder = session.tagOrderToBeAtIndex(0, Integer.MAX_VALUE, newTag);
                }
            }

            // and work
            room.replaceTag(oldTag, newTag, tagOrder, apiCallback);
        }
    }

    /**
     * Add or remove the given room from direct chats
     *
     * @param session
     * @param roomId
     * @param apiCallback
     */
    public static void toggleDirectChat(final MXSession session, String roomId, final ApiCallback<Void> apiCallback) {
        Room room = session.getDataHandler().getRoom(roomId);
        if (null != room) {
            session.toggleDirectChatRoom(roomId, null, apiCallback);
        }
    }

    /**
     * Enable or disable notifications for the given room
     *
     * @param session
     * @param roomId
     * @param listener
     */
    public static void toggleNotifications(final MXSession session, final String roomId, final BingRulesManager.onBingRuleUpdateListener listener) {
        BingRulesManager bingRulesManager = session.getDataHandler().getBingRulesManager();
        bingRulesManager.muteRoomNotifications(roomId, !bingRulesManager.isRoomNotificationsDisabled(roomId), listener);
    }

    /**
     * Get whether the room of the given is a direct chat
     *
     * @param roomId
     * @return true if direct chat
     */
    public static boolean isDirectChat(final MXSession session, final String roomId) {
        final IMXStore store = session.getDataHandler().getStore();
        final Map<String, List<String>> directChatRoomsDict;

        if (store.getDirectChatRoomsDict() != null) {
            directChatRoomsDict = new HashMap<>(store.getDirectChatRoomsDict());

            if (directChatRoomsDict.containsKey(session.getMyUserId())) {
                List<String> roomIdsList = new ArrayList<>(directChatRoomsDict.get(session.getMyUserId()));
                return roomIdsList.contains(roomId);
            }
        }

        return false;
    }

    /**
     * Create a list of rooms by filtering the given list with the given pattern
     *
     * @param roomsToFilter
     * @param constraint
     * @return filtered rooms
     */
    public static List<Room> getFilteredRooms(final Context context, final MXSession session,
                                              final List<Room> roomsToFilter, final CharSequence constraint) {
        final String filterPattern = constraint != null ? constraint.toString().trim() : null;
        if (!TextUtils.isEmpty(filterPattern)) {
            List<Room> filteredRoom = new ArrayList<>();
            Pattern pattern = Pattern.compile(Pattern.quote(filterPattern), Pattern.CASE_INSENSITIVE);
            for (final Room room : roomsToFilter) {
                final String roomName = VectorUtils.getRoomDisplayName(context, session, room);
                if (pattern.matcher(roomName).find()) {
                    filteredRoom.add(room);
                }
            }
            return filteredRoom;
        } else {
            return roomsToFilter;
        }
    }

    /**
     * Format the unread messages counter.
     *
     * @param count the count
     * @return the formatted value
     */
    public static String formatUnreadMessagesCounter(int count) {
        if (count > 0) {
            if (count > 999) {
                return (count / 1000) + "." + ((count % 1000) / 100) + "K";
            } else {
                return String.valueOf(count);
            }
        } else {
            return null;
        }
    }
}
