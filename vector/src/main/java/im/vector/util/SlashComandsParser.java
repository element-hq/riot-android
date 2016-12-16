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

package im.vector.util;

import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.util.Collection;
import java.util.HashMap;

import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorRoomActivity;

public class SlashComandsParser {

    private static final String LOG_TAG = "SlashComandsParser";

    // defines the command line operations
    // the user can write theses messages to perform some room events

    private static final String CMD_CHANGE_DISPLAY_NAME = "/nick";
    private static final String CMD_EMOTE = "/me";
    private static final String CMD_JOIN_ROOM = "/join";
    private static final String CMD_KICK_USER = "/kick";
    private static final String CMD_BAN_USER = "/ban";
    private static final String CMD_UNBAN_USER = "/unban";
    private static final String CMD_SET_USER_POWER_LEVEL = "/op";
    private static final String CMD_RESET_USER_POWER_LEVEL = "/deop";
    private static final String CMD_TOPIC = "/topic";
    private static final String CMD_INVITE = "/invite";
    private static final String CMD_PART = "/part";
    private static final String CMD_MARKDOWN = "/markdown";

    /**
     * check if the text message is an IRC command.
     * If it is an IRC command, it is executed
     * @param activity the room activity
     * @param session the session
     * @param room the room
     * @param message the text message
     * @param formattedBody the formatted message
     * @param format the message format
     * @return true if it is a splash command
     */
    public static boolean manageSplashCommand(final VectorRoomActivity activity, final MXSession session, final Room room, final String message, final String formattedBody, final String format) {
        boolean isIRCCmd = false;

        // sanity checks
        if ((null == activity) || (null == session) || (null == room)) {
            Log.e(LOG_TAG, "manageSplashCommand : invalid parameters");
            return false;
        }

        // check if it has the IRC marker
        if ((null != message) && (message.startsWith("/"))) {

            Log.d(LOG_TAG, "manageSplashCommand : " + message);

            final ApiCallback callback = new SimpleApiCallback<Void>(activity) {
                @Override
                public void onSuccess(Void info) {
                    Log.d(LOG_TAG, "manageSplashCommand : " + message + " : the operation succeeded.");
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    if (MatrixError.FORBIDDEN.equals(e.errcode)) {
                        Toast.makeText(activity, e.error, Toast.LENGTH_LONG).show();
                    }
                }
            };

            if (message.startsWith(CMD_CHANGE_DISPLAY_NAME)) {
                isIRCCmd = true;

                String newDisplayname = message.substring(CMD_CHANGE_DISPLAY_NAME.length()).trim();

                if (newDisplayname.length() > 0) {
                    MyUser myUser = session.getMyUser();

                    myUser.updateDisplayName(newDisplayname, callback);
                }
            } else if (message.startsWith(CMD_TOPIC)) {
                isIRCCmd = true;

                String newTopîc = message.substring(CMD_TOPIC.length()).trim();

                if (newTopîc.length() > 0) {
                    room.updateTopic(newTopîc, callback);
                }
            } else if (message.startsWith(CMD_EMOTE)) {
                isIRCCmd = true;

                String newMessage = message.substring(CMD_EMOTE.length()).trim();

                if (message.length() > 0) {
                    if ((null != formattedBody) && formattedBody.length() > CMD_EMOTE.length()) {
                        activity.sendEmote(newMessage, formattedBody.substring(CMD_EMOTE.length()), format);
                    } else {
                        activity.sendEmote(newMessage, formattedBody, format);
                    }
                }
            } else if (message.startsWith(CMD_JOIN_ROOM)) {
                isIRCCmd = true;
                String roomAlias = message.substring(CMD_JOIN_ROOM.length()).trim();

                if (roomAlias.length() > 0) {
                    session.joinRoom(roomAlias,new SimpleApiCallback<String>(activity) {

                        @Override
                        public void onSuccess(String roomId) {
                            if (null != roomId) {
                                HashMap<String, Object> params = new HashMap<>();
                                params.put(VectorRoomActivity.EXTRA_MATRIX_ID, session.getMyUserId());
                                params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);

                                CommonActivityUtils.goToRoomPage(activity, session, params);
                            }
                        }

                        @Override
                        public void onMatrixError(final MatrixError e) {
                            Toast.makeText(activity, e.error, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } else if (message.startsWith(CMD_PART)) {
                isIRCCmd = true;
                String roomAlias = message.substring(CMD_PART.length()).trim();

                if (roomAlias.length() > 0) {
                    Room theRoom = null;
                    Collection<Room> rooms = session.getDataHandler().getStore().getRooms();

                    for(Room r : rooms) {
                        RoomState state = r.getLiveState();

                        if (null != state) {
                            if (TextUtils.equals(state.alias, roomAlias)) {
                                theRoom = r;
                                break;
                            } else if (state.getAliases().indexOf(roomAlias) >= 0) {
                                theRoom = r;
                                break;
                            }
                        }
                    }

                    if (null != theRoom) {
                        theRoom.leave(callback);
                    }
                }
            } else if (message.startsWith(CMD_INVITE)) {
                isIRCCmd = true;

                String params = message.substring(CMD_INVITE.length()).trim();
                String[] paramsList = params.split(" ");

                String kickedUserID = paramsList[0];

                if (kickedUserID.length() > 0) {
                    room.invite(kickedUserID, callback);
                }
            } else if (message.startsWith(CMD_KICK_USER)) {
                isIRCCmd = true;

                String params = message.substring(CMD_KICK_USER.length()).trim();
                String[] paramsList = params.split(" ");

                String kickedUserID = paramsList[0];

                if (kickedUserID.length() > 0) {
                    room.kick(kickedUserID, callback);
                }
            } else if (message.startsWith(CMD_BAN_USER)) {
                isIRCCmd = true;

                String params = message.substring(CMD_BAN_USER.length()).trim();
                String[] paramsList = params.split(" ");

                String bannedUserID = paramsList[0];
                String reason = params.substring(bannedUserID.length()).trim();

                if (bannedUserID.length() > 0) {
                    room.ban(bannedUserID, reason, callback);
                }
            } else if (message.startsWith(CMD_UNBAN_USER)) {
                isIRCCmd = true;

                String params = message.substring(CMD_UNBAN_USER.length()).trim();
                String[] paramsList = params.split(" ");

                String unbannedUserID = paramsList[0];

                if (unbannedUserID.length() > 0) {
                    room.unban(unbannedUserID, callback);
                }
            } else if (message.startsWith(CMD_SET_USER_POWER_LEVEL)) {
                isIRCCmd = true;

                String params = message.substring(CMD_SET_USER_POWER_LEVEL.length()).trim();
                String[] paramsList = params.split(" ");

                String userID = paramsList[0];
                String powerLevelsAsString  = params.substring(userID.length()).trim();

                try {
                    if ((userID.length() > 0) && (powerLevelsAsString.length() > 0)) {
                        room.updateUserPowerLevels(userID, Integer.parseInt(powerLevelsAsString), callback);
                    }
                } catch(Exception e){
                    Log.e(LOG_TAG, "mRoom.updateUserPowerLevels " + e.getMessage());
                }
            } else if (message.startsWith(CMD_RESET_USER_POWER_LEVEL)) {
                isIRCCmd = true;

                String params = message.substring(CMD_RESET_USER_POWER_LEVEL.length()).trim();
                String[] paramsList = params.split(" ");

                String userID = paramsList[0];

                if (userID.length() > 0) {
                    room.updateUserPowerLevels(userID, 0, callback);
                }
            } else if (message.startsWith(CMD_MARKDOWN)) {
                isIRCCmd = true;

                String params = message.substring(CMD_MARKDOWN.length()).trim();
                String[] paramsList = params.split(" ");

                if (paramsList.length > 0) {
                    if (TextUtils.equals(paramsList[0], "on")) {
                        VectorApp.getInstance().mMarkdownParser.setEnable(true);
                    } else if (TextUtils.equals(paramsList[0], "off")) {
                        VectorApp.getInstance().mMarkdownParser.setEnable(false);
                    }
                }
            }
        }

        return isIRCCmd;
    }


}
