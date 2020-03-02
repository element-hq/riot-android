/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2018 New Vector Ltd
 * Copyright 2019 New Vector Ltd
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
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import org.jetbrains.annotations.Nullable;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.features.identityserver.IdentityServerNotConfiguredException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorRoomActivity;
import im.vector.widgets.WidgetManagerProvider;
import im.vector.widgets.WidgetsManager;

public class SlashCommandsParser {

    private static final String LOG_TAG = SlashCommandsParser.class.getSimpleName();

    public enum SlashCommand {

        // defines the command line operations
        // the user can write theses messages to perform some actions
        // the list will be displayed in this order
        EMOTE("/me", "<message>", R.string.command_description_emote),
        BAN_USER("/ban", "<user-id> [reason]", R.string.command_description_ban_user),
        UNBAN_USER("/unban", "<user-id>", R.string.command_description_unban_user),
        SET_USER_POWER_LEVEL("/op", "<user-id> [<power-level>]", R.string.command_description_op_user),
        RESET_USER_POWER_LEVEL("/deop", "<user-id>", R.string.command_description_deop_user),
        INVITE("/invite", "<user-id>", R.string.command_description_invite_user),
        JOIN_ROOM("/join", "<room-alias>", R.string.command_description_join_room),
        PART("/part", "<room-alias>", R.string.command_description_part_room),
        TOPIC("/topic", "<topic>", R.string.command_description_topic),
        KICK_USER("/kick", "<user-id> [reason]", R.string.command_description_kick_user),
        CHANGE_DISPLAY_NAME("/nick", "<display-name>", R.string.command_description_nick),
        MARKDOWN("/markdown", "<on|off>", R.string.command_description_markdown),
        CLEAR_SCALAR_TOKEN("/clear_scalar_token", "", R.string.command_description_clear_scalar_token);

        private final String command;
        private String parameter;

        @StringRes
        private int description;

        private static final Map<String, SlashCommand> lookup = new HashMap<>();

        static {
            for (SlashCommand slashCommand : SlashCommand.values()) {
                lookup.put(slashCommand.getCommand(), slashCommand);
            }
        }

        SlashCommand(String command, String parameter, @StringRes int description) {
            this.command = command;
            this.parameter = parameter;
            this.description = description;
        }

        public static SlashCommand get(String command) {
            return lookup.get(command);
        }

        public String getCommand() {
            return command;
        }

        public String getParam() {
            return parameter;
        }

        public int getDescription() {
            return description;
        }
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
        boolean isIRCCmdValid = false;

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

                @Override
                public void onUnexpectedError(Exception e) {
                    if (e instanceof IdentityServerNotConfiguredException) {
                        Toast.makeText(activity, activity.getString(R.string.invite_no_identity_server_error), Toast.LENGTH_LONG).show();
                    } else {
                        super.onUnexpectedError(e);
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

            if (TextUtils.equals(firstPart, SlashCommand.CHANGE_DISPLAY_NAME.getCommand())) {
                isIRCCmd = true;

                String newDisplayname = textMessage.substring(SlashCommand.CHANGE_DISPLAY_NAME.getCommand().length()).trim();

                if (newDisplayname.length() > 0) {
                    isIRCCmdValid = true;
                    MyUser myUser = session.getMyUser();

                    myUser.updateDisplayName(newDisplayname, callback);
                }
            } else if (TextUtils.equals(firstPart, SlashCommand.TOPIC.getCommand())) {
                isIRCCmd = true;

                String newTopic = textMessage.substring(SlashCommand.TOPIC.getCommand().length()).trim();

                if (newTopic.length() > 0) {
                    isIRCCmdValid = true;
                    room.updateTopic(newTopic, callback);
                }
            } else if (TextUtils.equals(firstPart, SlashCommand.EMOTE.getCommand())) {
                isIRCCmd = true;
                isIRCCmdValid = true;

                String newMessage = textMessage.substring(SlashCommand.EMOTE.getCommand().length()).trim();

                if ((null != formattedBody) && formattedBody.length() > SlashCommand.EMOTE.getCommand().length()) {
                    activity.sendEmote(newMessage, formattedBody.substring(SlashCommand.EMOTE.getCommand().length()), format);
                } else {
                    activity.sendEmote(newMessage, formattedBody, format);
                }
            } else if (TextUtils.equals(firstPart, SlashCommand.JOIN_ROOM.getCommand())) {
                isIRCCmd = true;
                String roomAlias = textMessage.substring(SlashCommand.JOIN_ROOM.getCommand().length()).trim();

                if (roomAlias.length() > 0) {
                    isIRCCmdValid = true;
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
            } else if (TextUtils.equals(firstPart, SlashCommand.PART.getCommand())) {
                isIRCCmd = true;
                String roomAlias = textMessage.substring(SlashCommand.PART.getCommand().length()).trim();

                if (roomAlias.length() > 0) {
                    isIRCCmdValid = true;
                    Room theRoom = null;
                    Collection<Room> rooms = session.getDataHandler().getStore().getRooms();

                    for (Room r : rooms) {
                        RoomState state = r.getState();

                        if (null != state) {
                            if (TextUtils.equals(state.getCanonicalAlias(), roomAlias)) {
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
            } else if (TextUtils.equals(firstPart, SlashCommand.INVITE.getCommand())) {
                isIRCCmd = true;

                if (messageParts.length >= 2) {
                    isIRCCmdValid = true;
                    room.invite(session, messageParts[1], callback);
                }
            } else if (TextUtils.equals(firstPart, SlashCommand.KICK_USER.getCommand())) {
                isIRCCmd = true;

                if (messageParts.length >= 2) {
                    isIRCCmdValid = true;

                    String user = messageParts[1];
                    String reason = textMessage.substring(SlashCommand.KICK_USER.getCommand().length()
                            + 1
                            + user.length()).trim();

                    room.kick(user, reason, callback);
                }
            } else if (TextUtils.equals(firstPart, SlashCommand.BAN_USER.getCommand())) {
                isIRCCmd = true;

                String params = textMessage.substring(SlashCommand.BAN_USER.getCommand().length()).trim();
                String[] paramsList = params.split(" ");

                String bannedUserID = paramsList[0];
                String reason = params.substring(bannedUserID.length()).trim();

                if (bannedUserID.length() > 0) {
                    isIRCCmdValid = true;
                    room.ban(bannedUserID, reason, callback);
                }
            } else if (TextUtils.equals(firstPart, SlashCommand.UNBAN_USER.getCommand())) {
                isIRCCmd = true;

                if (messageParts.length >= 2) {
                    isIRCCmdValid = true;
                    room.unban(messageParts[1], callback);
                }

            } else if (TextUtils.equals(firstPart, SlashCommand.SET_USER_POWER_LEVEL.getCommand())) {
                isIRCCmd = true;

                if (messageParts.length >= 3) {
                    isIRCCmdValid = true;
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
            } else if (TextUtils.equals(firstPart, SlashCommand.RESET_USER_POWER_LEVEL.getCommand())) {
                isIRCCmd = true;

                if (messageParts.length >= 2) {
                    isIRCCmdValid = true;
                    room.updateUserPowerLevels(messageParts[1], 0, callback);
                }
            } else if (TextUtils.equals(firstPart, SlashCommand.MARKDOWN.getCommand())) {
                isIRCCmd = true;

                if (messageParts.length >= 2) {
                    if (TextUtils.equals(messageParts[1], "on")) {
                        isIRCCmdValid = true;
                        PreferencesManager.setMarkdownEnabled(VectorApp.getInstance(), true);
                        Toast.makeText(activity, R.string.markdown_has_been_enabled, Toast.LENGTH_SHORT).show();
                    } else if (TextUtils.equals(messageParts[1], "off")) {
                        isIRCCmdValid = true;
                        PreferencesManager.setMarkdownEnabled(VectorApp.getInstance(), false);
                        Toast.makeText(activity, R.string.markdown_has_been_disabled, Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (TextUtils.equals(firstPart, SlashCommand.CLEAR_SCALAR_TOKEN.getCommand())) {
                isIRCCmd = true;
                isIRCCmdValid = true;

                WidgetsManager wm = getWidgetManager(activity);
                if (wm != null) {
                    wm.clearScalarToken(activity, session);
                    Toast.makeText(activity, "Scalar token cleared", Toast.LENGTH_SHORT).show();
                }

            }

            if (!isIRCCmd) {
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.command_error)
                        .setMessage(activity.getString(R.string.unrecognized_command, firstPart))
                        .setPositiveButton(R.string.ok, null)
                        .show();

                // do not send the command as a message
                isIRCCmd = true;
            } else if (!isIRCCmdValid) {
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.command_error)
                        .setMessage(activity.getString(R.string.command_problem_with_parameters, firstPart))
                        .setPositiveButton(R.string.ok, null)
                        .show();

                // do not send the command as a message
                isIRCCmd = true;
            }
        }

        return isIRCCmd;
    }

    @Nullable
    private static WidgetsManager getWidgetManager(Activity activity) {
        if (Matrix.getInstance(activity) == null) return null;
        MXSession session = Matrix.getInstance(activity).getDefaultSession();
        if (session == null) return null;
        WidgetManagerProvider widgetManagerProvider = Matrix.getInstance(activity).getWidgetManagerProvider(session);
        if (widgetManagerProvider == null) return null;
        return widgetManagerProvider.getWidgetManager(activity);
    }
}
