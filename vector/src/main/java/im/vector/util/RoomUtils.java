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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.BingRulesManager;
import org.matrix.androidsdk.core.EventDisplay;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomAccountData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;

import java.io.File;
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
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.AdapterUtils;
import im.vector.ui.themes.ThemeUtils;

public class RoomUtils {

    private static final String LOG_TAG = RoomUtils.class.getSimpleName();

    public interface MoreActionListener {
        void onUpdateRoomNotificationsState(MXSession session, String roomId, BingRulesManager.RoomNotificationState state);

        void onToggleDirectChat(MXSession session, String roomId);

        void moveToFavorites(MXSession session, String roomId);

        void moveToConversations(MXSession session, String roomId);

        void moveToLowPriority(MXSession session, String roomId);

        void onLeaveRoom(MXSession session, String roomId);

        void onForgetRoom(MXSession session, String roomId);

        void addHomeScreenShortcut(MXSession session, String roomId);
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
            private final Map<String, RoomSummary> mSummaryByRoomIdMap = new HashMap<>();

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
    public static Comparator<Room> getNotifCountRoomsComparator(final MXSession session,
                                                                final boolean pinMissedNotifications,
                                                                final boolean pinUnreadMessages) {
        return new Comparator<Room>() {
            private Comparator<RoomSummary> mRoomSummaryComparator;
            private final Map<String, RoomSummary> mSummaryByRoomIdMap = new HashMap<>();

            /**
             * Retrieve the room summary comparator
             * @return comparator
             */
            private Comparator<RoomSummary> getSummaryComparator() {
                if (null == mRoomSummaryComparator) {
                    mRoomSummaryComparator
                            = getNotifCountRoomSummaryComparator(session.getDataHandler().getBingRulesManager(), pinMissedNotifications, pinUnreadMessages);
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
    private static Comparator<RoomSummary> getRoomSummaryComparator(final boolean reverseOrder) {
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
    private static Comparator<RoomSummary> getNotifCountRoomSummaryComparator(final BingRulesManager bingRulesManager,
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
                } else if (pinUnreadMessages && (rightUnreadCount > 0) && (leftUnreadCount == 0)) {
                    retValue = 1;
                } else if (pinUnreadMessages && (rightUnreadCount == 0) && (leftUnreadCount > 0)) {
                    retValue = -1;
                } else if ((deltaTimestamp = rightRoomSummary.getLatestReceivedEvent().getOriginServerTs()
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
    public static CharSequence getRoomMessageToDisplay(final Context context,
                                                       final MXSession session,
                                                       final RoomSummary roomSummary) {
        CharSequence messageToDisplay = null;
        EventDisplay eventDisplay;

        if (null != roomSummary) {
            if (roomSummary.getLatestReceivedEvent() != null) {
                eventDisplay = new EventDisplay(context);
                eventDisplay.setPrependMessagesWithAuthor(true);
                messageToDisplay = eventDisplay.getTextualDisplay(ThemeUtils.INSTANCE.getColor(context, R.attr.vctr_room_notification_text_color),
                        roomSummary.getLatestReceivedEvent(),
                        roomSummary.getLatestRoomState());
            }

            // check if this is an invite
            if (roomSummary.isInvited() && (null != roomSummary.getInviterUserId())) {
                // TODO Re-write this algorithm, it's so complicated to understand for nothing...
                RoomState latestRoomState = roomSummary.getLatestRoomState();
                String inviterUserId = roomSummary.getInviterUserId();
                String myName = roomSummary.getUserId();

                if (null != latestRoomState) {
                    inviterUserId = latestRoomState.getMemberName(inviterUserId);
                    myName = latestRoomState.getMemberName(myName);
                } else {
                    inviterUserId = getMemberDisplayNameFromUserId(context, roomSummary.getUserId(), inviterUserId);
                    myName = getMemberDisplayNameFromUserId(context, roomSummary.getUserId(), myName);
                }

                if (TextUtils.equals(session.getMyUserId(), roomSummary.getUserId())) {
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
    private static String getMemberDisplayNameFromUserId(final Context context,
                                                         final String matrixId,
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
    public static void displayPopupMenu(final Context context,
                                        final MXSession session,
                                        final Room room,
                                        final View actionView,
                                        final boolean isFavorite,
                                        final boolean isLowPrior,
                                        @NonNull final MoreActionListener listener) {
        if (listener != null) {
            displayPopupMenu(context, session, room, actionView, isFavorite, isLowPrior, listener, null);
        }
    }


    /**
     * See {@link #displayPopupMenu(Context, MXSession, Room, View, boolean, boolean, MoreActionListener, HistoricalRoomActionListener)}
     */
    public static void displayHistoricalRoomMenu(final Context context,
                                                 final MXSession session,
                                                 final Room room,
                                                 final View actionView,
                                                 @NonNull final HistoricalRoomActionListener listener) {
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
    private static void displayPopupMenu(final Context context,
                                         final MXSession session,
                                         final Room room,
                                         final View actionView,
                                         final boolean isFavorite,
                                         final boolean isLowPrior,
                                         final MoreActionListener moreActionListener,
                                         final HistoricalRoomActionListener historicalRoomActionListener) {
        // sanity check
        if (null == room) {
            return;
        }

        Context popmenuContext = new ContextThemeWrapper(context, R.style.PopMenuStyle);

        final PopupMenu popup;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            popup = new PopupMenu(popmenuContext, actionView, Gravity.END);
        } else {
            popup = new PopupMenu(popmenuContext, actionView);
        }
        popup.getMenuInflater().inflate(R.menu.vector_home_room_settings, popup.getMenu());
        ThemeUtils.INSTANCE.tintMenuIcons(popup.getMenu(), ThemeUtils.INSTANCE.getColor(context, R.attr.vctr_settings_icon_tint_color));

        if (room.isLeft()) {
            popup.getMenu().setGroupVisible(R.id.active_room_actions, false);
            popup.getMenu().setGroupVisible(R.id.add_shortcut_actions, false);
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

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                popup.getMenu().setGroupVisible(R.id.add_shortcut_actions, false);
            } else {
                ShortcutManager manager = context.getSystemService(ShortcutManager.class);

                if (!manager.isRequestPinShortcutSupported()) {
                    popup.getMenu().setGroupVisible(R.id.add_shortcut_actions, false);
                } else {
                    popup.getMenu().setGroupVisible(R.id.add_shortcut_actions, true);
                }
            }

            popup.getMenu().setGroupVisible(R.id.historical_room_actions, false);

            MenuItem item;

            BingRulesManager.RoomNotificationState state = session.getDataHandler().getBingRulesManager().getRoomNotificationState(room.getRoomId());

            if (BingRulesManager.RoomNotificationState.ALL_MESSAGES_NOISY != state) {
                item = popup.getMenu().findItem(R.id.ic_action_notifications_noisy);
                item.setIcon(R.drawable.ic_material_transparent);
            }

            if (BingRulesManager.RoomNotificationState.ALL_MESSAGES != state) {
                item = popup.getMenu().findItem(R.id.ic_action_notifications_all_message);
                item.setIcon(R.drawable.ic_material_transparent);
            }

            if (BingRulesManager.RoomNotificationState.MENTIONS_ONLY != state) {
                item = popup.getMenu().findItem(R.id.ic_action_notifications_mention_only);
                item.setIcon(R.drawable.ic_material_transparent);
            }

            if (BingRulesManager.RoomNotificationState.MUTE != state) {
                item = popup.getMenu().findItem(R.id.ic_action_notifications_mute);
                item.setIcon(R.drawable.ic_material_transparent);
            }

            if (!isFavorite) {
                item = popup.getMenu().findItem(R.id.ic_action_select_fav);
                item.setIcon(R.drawable.ic_material_transparent);
            }

            if (!isLowPrior) {
                item = popup.getMenu().findItem(R.id.ic_action_select_deprioritize);
                item.setIcon(R.drawable.ic_material_transparent);
            }

            if (!room.isDirect()) {
                item = popup.getMenu().findItem(R.id.ic_action_select_direct_chat);
                item.setIcon(R.drawable.ic_material_transparent);
            }

            // TODO LazyLoading, current user may be null
            RoomMember member = room.getMember(session.getMyUserId());
            final boolean isBannedKickedRoom = (null != member) && member.kickedOrBanned();

            if (isBannedKickedRoom) {
                item = popup.getMenu().findItem(R.id.ic_action_select_remove);

                if (null != item) {
                    item.setTitle(R.string.forget_room);
                }
            }

            if (moreActionListener != null) {
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(final MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.ic_action_notifications_noisy:
                                moreActionListener.onUpdateRoomNotificationsState(session,
                                        room.getRoomId(), BingRulesManager.RoomNotificationState.ALL_MESSAGES_NOISY);
                                break;

                            case R.id.ic_action_notifications_all_message:
                                moreActionListener.onUpdateRoomNotificationsState(session,
                                        room.getRoomId(), BingRulesManager.RoomNotificationState.ALL_MESSAGES);
                                break;

                            case R.id.ic_action_notifications_mention_only:
                                moreActionListener.onUpdateRoomNotificationsState(session,
                                        room.getRoomId(), BingRulesManager.RoomNotificationState.MENTIONS_ONLY);
                                break;

                            case R.id.ic_action_notifications_mute:
                                moreActionListener.onUpdateRoomNotificationsState(session,
                                        room.getRoomId(), BingRulesManager.RoomNotificationState.MUTE);
                                break;

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
                                if (isBannedKickedRoom) {
                                    moreActionListener.onForgetRoom(session, room.getRoomId());
                                } else {
                                    moreActionListener.onLeaveRoom(session, room.getRoomId());
                                }
                                break;
                            }
                            case R.id.ic_action_select_direct_chat: {
                                moreActionListener.onToggleDirectChat(session, room.getRoomId());
                                break;
                            }
                            case R.id.ic_action_add_homescreen_shortcut: {
                                moreActionListener.addHomeScreenShortcut(session, room.getRoomId());
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
            Log.e(LOG_TAG, "## displayPopupMenu() : failed " + e.getMessage(), e);
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
                        onClickListener.onClick(dialog, which);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Add a room shortcut to the home screen (Android >= O).
     *
     * @param context the context
     * @param session the session
     * @param roomId  the room Id
     */
    @SuppressLint("NewApi")
    public static void addHomeScreenShortcut(final Context context, final MXSession session, final String roomId) {
        // android >=  O only
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        ShortcutManager manager = context.getSystemService(ShortcutManager.class);

        if (!manager.isRequestPinShortcutSupported()) {
            return;
        }

        Room room = session.getDataHandler().getRoom(roomId);

        if (null == room) {
            return;
        }

        String roomName = room.getRoomDisplayName(context);

        Bitmap bitmap = null;

        // try to retrieve the avatar from the medias cache
        if (!TextUtils.isEmpty(room.getAvatarUrl())) {
            int size = context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size);

            // check if the thumbnail is already downloaded
            File f = session.getMediaCache().thumbnailCacheFile(room.getAvatarUrl(), size);

            if (null != f) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                try {
                    bitmap = BitmapFactory.decodeFile(f.getPath(), options);
                } catch (OutOfMemoryError oom) {
                    Log.e(LOG_TAG, "decodeFile failed with an oom", oom);
                }
            }
        }

        if (null == bitmap) {
            bitmap = VectorUtils.getAvatar(context, VectorUtils.getAvatarColor(roomId), roomName, true);
        }

        Icon icon = Icon.createWithBitmap(bitmap);

        Intent intent = new Intent(context, VectorRoomActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, roomId);

        ShortcutInfo info = new ShortcutInfo.Builder(context, roomId)
                .setShortLabel(roomName)
                .setIcon(icon)
                .setIntent(intent)
                .build();


        manager.requestPinShortcut(info, null);
    }

    /**
     * Update a room Tag
     *
     * @param session     the session
     * @param roomId      the room Id
     * @param aTagOrder   the new tag order
     * @param newTag      the new Tag
     * @param apiCallback the asynchronous callback
     */
    public static void updateRoomTag(final MXSession session,
                                     final String roomId,
                                     final Double aTagOrder,
                                     final String newTag,
                                     final ApiCallback<Void> apiCallback) {
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
     * Get whether the room of the given is a direct chat
     *
     * @param roomId
     * @return true if direct chat
     */
    public static boolean isDirectChat(final MXSession session, final String roomId) {
        return (null != roomId) && session.getDataHandler().getDirectChatRoomIdsList().contains(roomId);
    }

    /**
     * Create a list of rooms by filtering the given list with the given pattern
     *
     * @param roomsToFilter
     * @param constraint
     * @return filtered rooms
     */
    public static List<Room> getFilteredRooms(final Context context,
                                              final List<Room> roomsToFilter,
                                              final CharSequence constraint) {
        final String filterPattern = constraint != null ? constraint.toString().trim() : null;
        if (!TextUtils.isEmpty(filterPattern)) {
            List<Room> filteredRoom = new ArrayList<>();
            Pattern pattern = Pattern.compile(Pattern.quote(filterPattern), Pattern.CASE_INSENSITIVE);
            for (final Room room : roomsToFilter) {
                final String roomName = room.getRoomDisplayName(context);
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
