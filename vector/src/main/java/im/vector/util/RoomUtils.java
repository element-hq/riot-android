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

    /**
     * Return comparator to sort rooms by date
     *
     * @param session
     * @return comparator
     */
    public static Comparator<Room> getRoomsDateComparator(final MXSession session) {
        return new Comparator<Room>() {
            public int compare(Room aLeftObj, Room aRightObj) {
                final RoomSummary leftRoomSummary = session.getDataHandler().getStore().getSummary(aLeftObj.getRoomId());
                final RoomSummary rightRoomSummary = session.getDataHandler().getStore().getSummary(aRightObj.getRoomId());

                return getRoomSummaryComparator().compare(leftRoomSummary, rightRoomSummary);
            }
        };
    }

    /**
     * Return a comparator to sort summaries by lastest event
     *
     * @return
     */
    public static Comparator<RoomSummary> getRoomSummaryComparator() {
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
    public static CharSequence getRoomMessageToDisplay(final Context context, final MXSession session,
                                                       final RoomSummary roomSummary) {
        CharSequence messageToDisplay = null;
        EventDisplay eventDisplay;

        if (null != roomSummary) {
            if (roomSummary.getLatestReceivedEvent() != null) {
                eventDisplay = new EventDisplay(context, roomSummary.getLatestReceivedEvent(), roomSummary.getLatestRoomState());
                eventDisplay.setPrependMessagesWithAuthor(true);
                messageToDisplay = eventDisplay.getTextualDisplay(ContextCompat.getColor(context, R.color.vector_text_gray_color));
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
     * Display the room action popup.
     *
     * @param context
     * @param session
     * @param childRoom  the room in which the actions should be triggered in.
     * @param actionView the anchor view.
     * @param isFavorite true if it is a favorite room
     * @param isLowPrior true it it is a low priority room
     * @param listener
     */
    @SuppressLint("NewApi")
    public static void displayPopupMenu(final Context context, final MXSession session, final Room childRoom,
                                        final View actionView, final boolean isFavorite, final boolean isLowPrior,
                                        final MoreActionListener listener) {
        // sanity check
        if (null == childRoom) {
            return;
        }

        final PopupMenu popup;

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            popup = new PopupMenu(context, actionView, Gravity.END);
        } else {
            popup = new PopupMenu(context, actionView);
        }
        popup.getMenuInflater().inflate(R.menu.vector_home_room_settings, popup.getMenu());

        MenuItem item;

        final BingRulesManager bingRulesManager = session.getDataHandler().getBingRulesManager();

        if (bingRulesManager.isRoomNotificationsDisabled(childRoom)) {
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

        if (session.getDirectChatRoomIdsList().indexOf(childRoom.getRoomId()) < 0) {
            item = popup.getMenu().getItem(3);
            item.setIcon(null);
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

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                if (listener != null) {
                    switch (item.getItemId()) {
                        case R.id.ic_action_select_notifications: {
                            listener.onToggleRoomNotifications(session, childRoom.getRoomId());
                            break;
                        }
                        case R.id.ic_action_select_fav: {
                            if (isFavorite) {
                                listener.moveToConversations(session, childRoom.getRoomId());
                            } else {
                                listener.moveToFavorites(session, childRoom.getRoomId());
                            }
                            break;
                        }
                        case R.id.ic_action_select_deprioritize: {
                            if (isLowPrior) {
                                listener.moveToConversations(session, childRoom.getRoomId());
                            } else {
                                listener.moveToLowPriority(session, childRoom.getRoomId());
                            }
                            break;
                        }
                        case R.id.ic_action_select_remove: {
                            listener.onLeaveRoom(session, childRoom.getRoomId());
                            break;
                        }
                        case R.id.ic_action_select_direct_chat: {
                            listener.onToggleDirectChat(session, childRoom.getRoomId());
                            break;
                        }
                    }
                }
                return false;
            }
        });

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
     * Update the room tag.
     *
     * @param session the session
     * @param roomId  the room id
     * @param newtag  the new tag
     */
    public static void updateRoomTag(final MXSession session, final String roomId, final String newtag, final ApiCallback<Void> apiCallback) {
        Room room = session.getDataHandler().getRoom(roomId);

        if (null != room) {
            String oldTag = null;

            // retrieve the tag from the room info
            RoomAccountData accountData = room.getAccountData();

            if ((null != accountData) && accountData.hasTags()) {
                oldTag = accountData.getKeys().iterator().next();
            }

            // and work
            room.replaceTag(oldTag, newtag, null, apiCallback);
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
        Room room = session.getDataHandler().getRoom(roomId);

        if (null != room) {
            BingRulesManager bingRulesManager = session.getDataHandler().getBingRulesManager();
            bingRulesManager.muteRoomNotifications(room, !bingRulesManager.isRoomNotificationsDisabled(room), listener);
        }
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
}
