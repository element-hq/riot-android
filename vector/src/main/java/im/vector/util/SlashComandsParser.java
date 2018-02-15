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

import android.app.AlertDialog;
import android.text.TextUtils;

import org.matrix.androidsdk.util.Log;

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

import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorRoomActivity;

public class SlashComandsParser {

    private static final String LOG_TAG = SlashComandsParser.class.getSimpleName();

    // defines the command line operations
    // the user can write theses messages to perform some room events
    public static final String CMD_EMOTE = "/me";

    // <user-id> [reason]
    private static final String CMD_BAN_USER = "/ban";

    // <user-id>'
    private static final String CMD_UNBAN_USER = "/unban";

    // <user-id> [<power-level>]
    private static final String CMD_SET_USER_POWER_LEVEL = "/op";

    // <user-id>
    private static final String CMD_RESET_USER_POWER_LEVEL = "/deop";

    // <user-id>
    private static final String CMD_INVITE = "/invite";

    // <room-alias>
    private static final String CMD_JOIN_ROOM = "/join";

    // <room-alias>
    private static final String CMD_PART = "/part";

    // <topic>
    private static final String CMD_TOPIC = "/topic";

    // <user-id> [reason]
    private static final String CMD_KICK_USER = "/kick";

    // <display-name>
    private static final String CMD_CHANGE_DISPLAY_NAME = "/nick";

    // <query>
    private static final String CMD_DDG = "/ddg";

    // <color1> [<color2>]
    private static final String CMD_TINT = "/tint";

    // <user-id> <device-id> <device-signing-key>
    private static final String CMD_VERIFY = "/verify";

    // <<user-id>
    private static final String CMD_IGNORE = "/ignore";

    // <<user-id>
    private static final String CMD_UNIGNORE = "/unignore";

    // on / off
    private static final String CMD_MARKDOWN = "/markdown";

    /**
     * check if the text message is an IRC command.
     * If it is an IRC command, it is executed
     *
     * @param activity      the room activity
     * @param session       the session
     * @param room          the room
     * @param textMessage   the text message
     * @param formattedBody the formatted message
     * @param format        the message format
     * @return true if it is a splash command
     */
    public static boolean manageSplashCommand(final VectorRoomActivity activity, final MXSession session, final Room room, final String textMessage, final String formattedBody, final String format) {
        boolean isIRCCmd = false;

        // sanity checks
        if ((null == activity) || (null == session) || (null == room)) {
            Log.e(LOG_TAG, "manageSplashCommand : invalid parameters");
            return false;
        }

        // check if it has the IRC marker
        if ((null != textMessage) && (textMessage.startsWith("/"))) {
            Log.d(LOG_TAG, "manageSplashCommand : " + textMessage);

            if (textMessage.length() == 1) {
                return false;
            }

            if ("/".equals(textMessage.substring(1, 2))) {
                return false;
            }

            final ApiCallback callback = new SimpleApiCallback<Void>(activity) {
                @Override
                public void onSuccess(Void info) {
                    Log.d(LOG_TAG, "manageSplashCommand : " + textMessage + " : the operation succeeded.");
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    if (MatrixError.FORBIDDEN.equals(e.errcode)) {
                        Toast.makeText(activity, e.error, Toast.LENGTH_LONG).show();
                    }
                }
            };

            String[] messageParts = null;

            try {
                messageParts = textMessage.split("\\s+");
            } catch (Exception e) {
                Log.e(LOG_TAG, "## manageSplashCommand() : split failed " + e.getMessage());
            }

            // test if the string cut fails
            if ((null == messageParts) || (0 == messageParts.length)) {
                return false;
            }

            String firstPart = messageParts[0];

            if (TextUtils.equals(firstPart, CMD_CHANGE_DISPLAY_NAME)) {
                isIRCCmd = true;

                String newDisplayname = textMessage.substring(CMD_CHANGE_DISPLAY_NAME.length()).trim();

                if (newDisplayname.length() > 0) {
                    MyUser myUser = session.getMyUser();

                    myUser.updateDisplayName(newDisplayname, callback);
                }
            } else if (TextUtils.equals(firstPart, CMD_TOPIC)) {
                isIRCCmd = true;

                String newTopîc = textMessage.substring(CMD_TOPIC.length()).trim();

                if (newTopîc.length() > 0) {
                    room.updateTopic(newTopîc, callback);
                }
            } else if (TextUtils.equals(firstPart, CMD_EMOTE)) {
                isIRCCmd = true;

                String newMessage = textMessage.substring(CMD_EMOTE.length()).trim();

                if (textMessage.length() > 0) {
                    if ((null != formattedBody) && formattedBody.length() > CMD_EMOTE.length()) {
                        activity.sendEmote(newMessage, formattedBody.substring(CMD_EMOTE.length()), format);
                    } else {
                        activity.sendEmote(newMessage, formattedBody, format);
                    }
                }
            } else if (TextUtils.equals(firstPart, CMD_JOIN_ROOM)) {
                isIRCCmd = true;
                String roomAlias = textMessage.substring(CMD_JOIN_ROOM.length()).trim();

                if (roomAlias.length() > 0) {
                    session.joinRoom(roomAlias, new SimpleApiCallback<String>(activity) {

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
            } else if (TextUtils.equals(firstPart, CMD_PART)) {
                isIRCCmd = true;
                String roomAlias = textMessage.substring(CMD_PART.length()).trim();

                if (roomAlias.length() > 0) {
                    Room theRoom = null;
                    Collection<Room> rooms = session.getDataHandler().getStore().getRooms();

                    for (Room r : rooms) {
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
            } else if (TextUtils.equals(firstPart, CMD_INVITE)) {
                isIRCCmd = true;

                if (messageParts.length >= 2) {
                    room.invite(messageParts[1], callback);
                }
            } else if (TextUtils.equals(firstPart, CMD_KICK_USER)) {
                isIRCCmd = true;

                if (messageParts.length >= 2) {
                    room.kick(messageParts[1], callback);
                }
            } else if (TextUtils.equals(firstPart, CMD_BAN_USER)) {
                isIRCCmd = true;

                String params = textMessage.substring(CMD_BAN_USER.length()).trim();
                String[] paramsList = params.split(" ");

                String bannedUserID = paramsList[0];
                String reason = params.substring(bannedUserID.length()).trim();

                if (bannedUserID.length() > 0) {
                    room.ban(bannedUserID, reason, callback);
                }
            } else if (TextUtils.equals(firstPart, CMD_UNBAN_USER)) {
                isIRCCmd = true;

                if (messageParts.length >= 2) {
                    room.unban(messageParts[1], callback);
                }

            } else if (TextUtils.equals(firstPart, CMD_SET_USER_POWER_LEVEL)) {
                isIRCCmd = true;

                if (messageParts.length >= 3) {
                    String userID = messageParts[1];
                    String powerLevelsAsString = messageParts[2];

                    try {
                        if ((userID.length() > 0) && (powerLevelsAsString.length() > 0)) {
                            room.updateUserPowerLevels(userID, Integer.parseInt(powerLevelsAsString), callback);
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "mRoom.updateUserPowerLevels " + e.getMessage());
                    }
                }
            } else if (TextUtils.equals(firstPart, CMD_RESET_USER_POWER_LEVEL)) {
                isIRCCmd = true;

                if (messageParts.length >= 2) {
                    room.updateUserPowerLevels(messageParts[1], 0, callback);
                }
            } else if (TextUtils.equals(firstPart, CMD_MARKDOWN)) {
                isIRCCmd = true;

                if (messageParts.length >= 2) {
                    if (TextUtils.equals(messageParts[1], "on")) {
                        PreferencesManager.setMarkdownEnabled(VectorApp.getInstance(), true);
                    } else if (TextUtils.equals(messageParts[1], "off")) {
                        PreferencesManager.setMarkdownEnabled(VectorApp.getInstance(), false);
                    }
                }
            }

            if (!isIRCCmd) {
                AlertDialog.Builder dialog = new AlertDialog.Builder(activity);
                dialog.setTitle(R.string.command_error);
                dialog.setMessage(activity.getString(R.string.unrecognized_command, firstPart));
                dialog.setPositiveButton(R.string.ok, null);
                dialog.show();
                // do not send the command as a message
                isIRCCmd = true;
            }
        }

        return isIRCCmd;
    }
}
