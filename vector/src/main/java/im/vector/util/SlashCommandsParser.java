/*
 * Copyright 2016 OpenMarket Ltd
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

import android.content.res.Resources;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.RiotAppCompatActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.widgets.WidgetsManager;

public class SlashCommandsParser {

    private static final String LOG_TAG = SlashCommandsParser.class.getSimpleName();

    // defines the command line operations
    // the user can write theses messages to perform some room events
    public static final String CMD_EMOTE = "/me";
    public static final String PARAM_EMOTE ="<message>";
    public static final String DESC_EMOTE ="Displays action";

    // <user-id> [reason]
    public static final String CMD_BAN_USER = "/ban";
    public static final String PARAM_BAN_USER ="<user-id>";
    public static final String DESC_BAN_USE ="Bans user with given id";

    // <user-id>'
    public static final String CMD_UNBAN_USER = "/unban";
    public static final String PARAM_UNBAN_USER = "<user-id>";
    public static final String DESC_UNBAN_USER = "Unbans user with given id";

    // <user-id> [<power-level>]
    public static final String CMD_SET_USER_POWER_LEVEL = "/op";
    public static final String PARAM_SET_USER_POWER_LEVEL = "<user-id> [<power-level>]";
    public static final String DESC_SET_USER_POWER_LEVEL = "Define the power level of a user";

    // <user-id>
    public static final String CMD_RESET_USER_POWER_LEVEL = "/deop";
    public static final String PARAM_RESET_USER_POWER_LEVEL = "<user-id>";
    public static final String DESC_RESET_USER_POWER_LEVEL = "Deops user with given id";

    // <user-id>
    public static final String CMD_INVITE = "/invite";
    public static final String PARAM_INVITE = "<user-id>";
    public static final String DESC_INVITE = "Invites user with given id to this room";

    // <room-alias>
    public static final String CMD_JOIN_ROOM = "/join";
    public static final String PARAM_JOIN_ROOM = "<room-alias>";
    public static final String DESC_JOIN_ROOM = "Joins room with given alias";

    // <room-alias>
    public static final String CMD_PART = "/part";
    public static final String PARAM_PART = "<room-alias>";
    public static final String DESC_PART = "Leave room";

    // <topic>
    public static final String CMD_TOPIC = "/topic";
    public static final String PARAM_TOPIC = "<topic>";
    public static final String DESC_TOPIC = "Set the room topic";

    // <user-id> [reason]
    public static final String CMD_KICK_USER = "/kick";
    public static final String PARAM_KICK_USER = "<user-id>";
    public static final String DESC_KICK_USER = "Kicks user with given id";

    // <display-name>
    public static final String CMD_CHANGE_DISPLAY_NAME = "/nick";
    public static final String PARAM_CHANGE_DISPLAY_NAME = "<display-name>";
    public static final String DESC_CHANGE_DISPLAY_NAME = "Changes your display nickname";

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
    public static final String CMD_MARKDOWN = "/markdown";
    public static final String PARAM_MARKDOWN = "";
    public static final String DESC_MARKDOWN = "On/Off markdown";

    // clear scalar token (waiting for correct 403 management)
    public static final String CMD_CLEAR_SCALAR_TOKEN = "/clear_scalar_token";
    public static final String PARAM_CLEAR_SCALAR_TOKEN = "";
    public static final String DESC_CLEAR_SCALAR_TOKEN = "To fix Matrix Apps management";

    public String parameter;
    public String description;
    public static List<String> mSlashCommandList;
    public static HashMap<String, String> mSlashCommandParamMap;
    public static HashMap<String, String> mSlashCommandDescMap;

    public static List<String> getSlashCommandList() {
        createSlashCommandList();
        setSlashCommandParameter();
        setSlashCommandDescription();
        return mSlashCommandList;
    }

    public static String getSlashCommandParam(String command) {

        if (mSlashCommandParamMap.containsKey(command)) {
            return mSlashCommandParamMap.get(command);
        }

        return null;
    }

    public static String getSlashCommandDescription(String command) {

        if (mSlashCommandDescMap.containsKey(command)) {
            return mSlashCommandDescMap.get(command);
        }

        return null;
    }

    private static void createSlashCommandList() {
        mSlashCommandList = new ArrayList<String>();

        mSlashCommandList.add(CMD_EMOTE);
        mSlashCommandList.add(CMD_CHANGE_DISPLAY_NAME);
        mSlashCommandList.add(CMD_TOPIC);
        mSlashCommandList.add(CMD_INVITE);
        mSlashCommandList.add(CMD_JOIN_ROOM);
        mSlashCommandList.add(CMD_PART);
        mSlashCommandList.add(CMD_SET_USER_POWER_LEVEL);
        mSlashCommandList.add(CMD_RESET_USER_POWER_LEVEL);
        mSlashCommandList.add(CMD_KICK_USER);
        mSlashCommandList.add(CMD_BAN_USER);
        mSlashCommandList.add(CMD_UNBAN_USER);
        mSlashCommandList.add(CMD_MARKDOWN);
        mSlashCommandList.add(CMD_CLEAR_SCALAR_TOKEN);
    }

    private static void setSlashCommandParameter(){
        mSlashCommandParamMap = new HashMap<>();

        mSlashCommandParamMap.put(CMD_EMOTE, PARAM_EMOTE);
        mSlashCommandParamMap.put(CMD_CHANGE_DISPLAY_NAME, PARAM_CHANGE_DISPLAY_NAME);
        mSlashCommandParamMap.put(CMD_TOPIC, PARAM_TOPIC);
        mSlashCommandParamMap.put(CMD_INVITE, PARAM_INVITE);
        mSlashCommandParamMap.put(CMD_JOIN_ROOM, PARAM_JOIN_ROOM);
        mSlashCommandParamMap.put(CMD_PART, PARAM_PART);
        mSlashCommandParamMap.put(CMD_SET_USER_POWER_LEVEL, PARAM_SET_USER_POWER_LEVEL);
        mSlashCommandParamMap.put(CMD_RESET_USER_POWER_LEVEL, PARAM_RESET_USER_POWER_LEVEL);
        mSlashCommandParamMap.put(CMD_KICK_USER, PARAM_KICK_USER);
        mSlashCommandParamMap.put(CMD_BAN_USER, PARAM_BAN_USER);
        mSlashCommandParamMap.put(CMD_UNBAN_USER, PARAM_UNBAN_USER);
        mSlashCommandParamMap.put(CMD_MARKDOWN, PARAM_MARKDOWN);
        mSlashCommandParamMap.put(CMD_CLEAR_SCALAR_TOKEN, PARAM_CLEAR_SCALAR_TOKEN);

    }

    private static void setSlashCommandDescription() {
        mSlashCommandDescMap = new HashMap<>();

        mSlashCommandDescMap.put(CMD_EMOTE, DESC_EMOTE);
        mSlashCommandDescMap.put(CMD_CHANGE_DISPLAY_NAME, DESC_CHANGE_DISPLAY_NAME);
        mSlashCommandDescMap.put(CMD_TOPIC, DESC_TOPIC);
        mSlashCommandDescMap.put(CMD_INVITE, DESC_INVITE);
        mSlashCommandDescMap.put(CMD_JOIN_ROOM, DESC_JOIN_ROOM);
        mSlashCommandDescMap.put(CMD_PART, DESC_PART);
        mSlashCommandDescMap.put(CMD_SET_USER_POWER_LEVEL, DESC_SET_USER_POWER_LEVEL);
        mSlashCommandDescMap.put(CMD_RESET_USER_POWER_LEVEL, DESC_RESET_USER_POWER_LEVEL);
        mSlashCommandDescMap.put(CMD_KICK_USER, DESC_KICK_USER);
        mSlashCommandDescMap.put(CMD_BAN_USER, DESC_BAN_USE);
        mSlashCommandDescMap.put(CMD_UNBAN_USER, DESC_UNBAN_USER);
        mSlashCommandDescMap.put(CMD_MARKDOWN, DESC_MARKDOWN);
        mSlashCommandDescMap.put(CMD_CLEAR_SCALAR_TOKEN, DESC_CLEAR_SCALAR_TOKEN);
    }

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
    public static boolean manageSplashCommand(final VectorRoomActivity activity,
                                              final MXSession session,
                                              final Room room,
                                              final String textMessage,
                                              final String formattedBody,
                                              final String format) {
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
                    } else if (MatrixError.M_CONSENT_NOT_GIVEN.equals(e.errcode)) {
                        activity.getConsentNotGivenHelper().displayDialog(e);
                    }
                }
            };

            String[] messageParts = null;

            try {
                messageParts = textMessage.split("\\s+");
            } catch (Exception e) {
                Log.e(LOG_TAG, "## manageSplashCommand() : split failed " + e.getMessage(), e);
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
                                Map<String, Object> params = new HashMap<>();
                                params.put(VectorRoomActivity.EXTRA_MATRIX_ID, session.getMyUserId());
                                params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);

                                CommonActivityUtils.goToRoomPage(activity, session, params);
                            }
                        }

                        @Override
                        public void onMatrixError(final MatrixError e) {
                            if (MatrixError.M_CONSENT_NOT_GIVEN.equals(e.errcode)) {
                                activity.getConsentNotGivenHelper().displayDialog(e);
                            } else {
                                Toast.makeText(activity, e.error, Toast.LENGTH_LONG).show();
                            }
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
                        RoomState state = r.getState();

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
                        Log.e(LOG_TAG, "mRoom.updateUserPowerLevels " + e.getMessage(), e);
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
            } else if (TextUtils.equals(firstPart, CMD_CLEAR_SCALAR_TOKEN)) {
                isIRCCmd = true;

                WidgetsManager.clearScalarToken(activity, session);

                Toast.makeText(activity, "Scalar token cleared", Toast.LENGTH_SHORT).show();
            }

            if (!isIRCCmd) {
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.command_error)
                        .setMessage(activity.getString(R.string.unrecognized_command, firstPart))
                        .setPositiveButton(R.string.ok, null)
                        .show();
                // do not send the command as a message
                isIRCCmd = true;
            }
        }

        return isIRCCmd;
    }
}
