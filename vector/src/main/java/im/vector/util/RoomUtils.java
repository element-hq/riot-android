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

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.EventDisplay;

import java.util.Comparator;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.AdapterUtils;

public class RoomUtils {

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
     * @return
     */
    public static Comparator<RoomSummary> getRoomSummaryComparator(){
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
}
