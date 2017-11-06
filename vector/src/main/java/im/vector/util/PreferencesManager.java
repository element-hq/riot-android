/*
 * Copyright 2016 OpenMarket Ltd
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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.TextUtils;
import org.matrix.androidsdk.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import im.vector.R;
import im.vector.activity.LoginActivity;
import im.vector.ga.GAHelper;

public class PreferencesManager {

    private static final String LOG_TAG = "PreferencesManager";

    public static final String SETTINGS_MESSAGES_SENT_BY_BOT_PREFERENCE_KEY = "SETTINGS_MESSAGES_SENT_BY_BOT_PREFERENCE_KEY";
    public static final String SETTINGS_CHANGE_PASSWORD_PREFERENCE_KEY = "SETTINGS_CHANGE_PASSWORD_PREFERENCE_KEY";
    public static final String SETTINGS_VERSION_PREFERENCE_KEY = "SETTINGS_VERSION_PREFERENCE_KEY";
    public static final String SETTINGS_OLM_VERSION_PREFERENCE_KEY = "SETTINGS_OLM_VERSION_PREFERENCE_KEY";
    public static final String SETTINGS_LOGGED_IN_PREFERENCE_KEY = "SETTINGS_LOGGED_IN_PREFERENCE_KEY";
    public static final String SETTINGS_HOME_SERVER_PREFERENCE_KEY = "SETTINGS_HOME_SERVER_PREFERENCE_KEY";
    public static final String SETTINGS_IDENTITY_SERVER_PREFERENCE_KEY = "SETTINGS_IDENTITY_SERVER_PREFERENCE_KEY";
    public static final String SETTINGS_APP_TERM_CONDITIONS_PREFERENCE_KEY = "SETTINGS_APP_TERM_CONDITIONS_PREFERENCE_KEY";
    public static final String SETTINGS_PRIVACY_POLICY_PREFERENCE_KEY = "SETTINGS_PRIVACY_POLICY_PREFERENCE_KEY";
    public static final String SETTINGS_THIRD_PARTY_NOTICES_PREFERENCE_KEY = "SETTINGS_THIRD_PARTY_NOTICES_PREFERENCE_KEY";
    public static final String SETTINGS_COPYRIGHT_PREFERENCE_KEY = "SETTINGS_COPYRIGHT_PREFERENCE_KEY";
    public static final String SETTINGS_CLEAR_CACHE_PREFERENCE_KEY = "SETTINGS_CLEAR_CACHE_PREFERENCE_KEY";
    public static final String SETTINGS_CLEAR_MEDIA_CACHE_PREFERENCE_KEY = "SETTINGS_CLEAR_MEDIA_CACHE_PREFERENCE_KEY";
    public static final String SETTINGS_ENABLE_BACKGROUND_SYNC_PREFERENCE_KEY = "SETTINGS_ENABLE_BACKGROUND_SYNC_PREFERENCE_KEY";
    public static final String SETTINGS_OTHERS_PREFERENCE_KEY = "SETTINGS_OTHERS_PREFERENCE_KEY";
    public static final String SETTINGS_USER_SETTINGS_PREFERENCE_KEY = "SETTINGS_USER_SETTINGS_PREFERENCE_KEY";
    public static final String SETTINGS_CONTACT_PREFERENCE_KEYS = "SETTINGS_CONTACT_PREFERENCE_KEYS";
    public static final String SETTINGS_NOTIFICATIONS_TARGETS_PREFERENCE_KEY = "SETTINGS_NOTIFICATIONS_TARGETS_PREFERENCE_KEY";
    public static final String SETTINGS_NOTIFICATIONS_TARGET_DIVIDER_PREFERENCE_KEY = "SETTINGS_NOTIFICATIONS_TARGET_DIVIDER_PREFERENCE_KEY";
    public static final String SETTINGS_IGNORED_USERS_PREFERENCE_KEY = "SETTINGS_IGNORED_USERS_PREFERENCE_KEY";
    public static final String SETTINGS_IGNORE_USERS_DIVIDER_PREFERENCE_KEY = "SETTINGS_IGNORE_USERS_DIVIDER_PREFERENCE_KEY";
    public static final String SETTINGS_DEVICES_LIST_PREFERENCE_KEY = "SETTINGS_DEVICES_LIST_PREFERENCE_KEY";
    public static final String SETTINGS_DEVICES_DIVIDER_PREFERENCE_KEY = "SETTINGS_DEVICES_DIVIDER_PREFERENCE_KEY";
    public static final String SETTINGS_CRYPTOGRAPHY_PREFERENCE_KEY = "SETTINGS_CRYPTOGRAPHY_PREFERENCE_KEY";
    public static final String SETTINGS_CRYPTOGRAPHY_DIVIDER_PREFERENCE_KEY = "SETTINGS_CRYPTOGRAPHY_DIVIDER_PREFERENCE_KEY";
    public static final String SETTINGS_LABS_PREFERENCE_KEY = "SETTINGS_LABS_PREFERENCE_KEY";
    public static final String SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY = "SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY";
    public static final String SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY = "SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY";
    public static final String SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_PREFERENCE_KEY = "SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_PREFERENCE_KEY";
    public static final String SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_IS_ACTIVE_PREFERENCE_KEY = "SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_IS_ACTIVE_PREFERENCE_KEY";
    public static final String SETTINGS_PROFILE_PICTURE_PREFERENCE_KEY = "SETTINGS_PROFILE_PICTURE_PREFERENCE_KEY";
    public static final String SETTINGS_CONTACTS_PHONEBOOK_COUNTRY_PREFERENCE_KEY = "SETTINGS_CONTACTS_PHONEBOOK_COUNTRY_PREFERENCE_KEY";
    public static final String SETTINGS_INTERFACE_LANGUAGE_PREFERENCE_KEY = "SETTINGS_INTERFACE_LANGUAGE_PREFERENCE_KEY";
    public static final String SETTINGS_BACKGROUND_SYNC_PREFERENCE_KEY = "SETTINGS_BACKGROUND_SYNC_PREFERENCE_KEY";
    public static final String SETTINGS_ENCRYPTION_INFORMATION_DEVICE_NAME_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_INFORMATION_DEVICE_NAME_PREFERENCE_KEY";
    public static final String SETTINGS_ENCRYPTION_INFORMATION_DEVICE_ID_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_INFORMATION_DEVICE_ID_PREFERENCE_KEY";
    public static final String SETTINGS_ENCRYPTION_EXPORT_E2E_ROOM_KEYS_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_EXPORT_E2E_ROOM_KEYS_PREFERENCE_KEY";
    public static final String SETTINGS_ENCRYPTION_IMPORT_E2E_ROOM_KEYS_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_IMPORT_E2E_ROOM_KEYS_PREFERENCE_KEY";
    public static final String SETTINGS_ENCRYPTION_NEVER_SENT_TO_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_NEVER_SENT_TO_PREFERENCE_KEY";
    public static final String SETTINGS_ENCRYPTION_INFORMATION_DEVICE_KEY_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_INFORMATION_DEVICE_KEY_PREFERENCE_KEY";

    public static final String SETTINGS_DISPLAY_NAME_PREFERENCE_KEY = "SETTINGS_DISPLAY_NAME_PREFERENCE_KEY";
    public static final String SETTINGS_ENABLE_ALL_NOTIF_PREFERENCE_KEY = "SETTINGS_ENABLE_ALL_NOTIF_PREFERENCE_KEY";
    public static final String SETTINGS_ENABLE_THIS_DEVICE_PREFERENCE_KEY = "SETTINGS_ENABLE_THIS_DEVICE_PREFERENCE_KEY";
    public static final String SETTINGS_TURN_SCREEN_ON_PREFERENCE_KEY = "SETTINGS_TURN_SCREEN_ON_PREFERENCE_KEY";
    public static final String SETTINGS_CONTAINING_MY_DISPLAY_NAME_PREFERENCE_KEY = "SETTINGS_CONTAINING_MY_DISPLAY_NAME_PREFERENCE_KEY";
    public static final String SETTINGS_CONTAINING_MY_USER_NAME_PREFERENCE_KEY = "SETTINGS_CONTAINING_MY_USER_NAME_PREFERENCE_KEY";
    public static final String SETTINGS_MESSAGES_IN_ONE_TO_ONE_PREFERENCE_KEY = "SETTINGS_MESSAGES_IN_ONE_TO_ONE_PREFERENCE_KEY";
    public static final String SETTINGS_MESSAGES_IN_GROUP_CHAT_PREFERENCE_KEY = "SETTINGS_MESSAGES_IN_GROUP_CHAT_PREFERENCE_KEY";
    public static final String SETTINGS_INVITED_TO_ROOM_PREFERENCE_KEY = "SETTINGS_INVITED_TO_ROOM_PREFERENCE_KEY";
    public static final String SETTINGS_CALL_INVITATIONS_PREFERENCE_KEY = "SETTINGS_CALL_INVITATIONS_PREFERENCE_KEY";

    public static final String SETTINGS_HIDE_READ_RECEIPTS_KEY = "SETTINGS_HIDE_READ_RECEIPTS_KEY";
    public static final String SETTINGS_ALWAYS_SHOW_TIMESTAMPS_KEY = "SETTINGS_ALWAYS_SHOW_TIMESTAMPS_KEY";
    public static final String SETTINGS_12_24_TIMESTAMPS_KEY = "SETTINGS_12_24_TIMESTAMPS_KEY";
    public static final String SETTINGS_DISABLE_MARKDOWN_KEY = "SETTINGS_DISABLE_MARKDOWN_KEY";
    public static final String SETTINGS_DONT_SEND_TYPING_NOTIF_KEY = "SETTINGS_DONT_SEND_TYPING_NOTIF_KEY";
    public static final String SETTINGS_HIDE_JOIN_LEAVE_MESSAGES_KEY = "SETTINGS_HIDE_JOIN_LEAVE_MESSAGES_KEY";
    public static final String SETTINGS_HIDE_AVATAR_DISPLAY_NAME_CHANGES_MESSAGES_KEY = "SETTINGS_HIDE_AVATAR_DISPLAY_NAME_CHANGES_MESSAGES_KEY";

    public static final String SETTINGS_MEDIA_SAVING_PERIOD_KEY = "SETTINGS_MEDIA_SAVING_PERIOD_KEY";
    public static final String SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY = "SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY";

    public static final String SETTINGS_PIN_UNREAD_MESSAGES_PREFERENCE_KEY = "SETTINGS_PIN_UNREAD_MESSAGES_PREFERENCE_KEY";
    public static final String SETTINGS_PIN_MISSED_NOTIFICATIONS_PREFERENCE_KEY = "SETTINGS_PIN_MISSED_NOTIFICATIONS_PREFERENCE_KEY";
    public static final String SETTINGS_GA_USE_SETTINGS_PREFERENCE_KEY = "SETTINGS_GA_USE_SETTINGS_PREFERENCE_KEY";

    public static final String SETTINGS_DATA_SAVE_MODE_PREFERENCE_KEY = "SETTINGS_DATA_SAVE_MODE_PREFERENCE_KEY";
    public static final String SETTINGS_START_ON_BOOT_PREFERENCE_KEY = "SETTINGS_START_ON_BOOT_PREFERENCE_KEY";
    public static final String SETTINGS_INTERFACE_TEXT_SIZE_KEY = "SETTINGS_INTERFACE_TEXT_SIZE_KEY";

    public static final String SETTINGS_USE_JITSI_CONF_PREFERENCE_KEY = "SETTINGS_USE_JITSI_CONF_PREFERENCE_KEY";
    public static final String SETTINGS_USE_MATRIX_APPS_PREFERENCE_KEY = "SETTINGS_USE_MATRIX_APPS_PREFERENCE_KEY";

    public static final String SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY = "SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY";
    public static final String SETTINGS_NOTIFICATION_RINGTONE_SELECTION_PREFERENCE_KEY = "SETTINGS_NOTIFICATION_RINGTONE_SELECTION_PREFERENCE_KEY";

    private static final int MEDIA_SAVING_3_DAYS = 0;
    private static final int MEDIA_SAVING_1_WEEK = 1;
    private static final int MEDIA_SAVING_1_MONTH = 2;
    private static final int MEDIA_SAVING_FOREVER = 3;

    // some preferences keys must be kept after a logout
    private static final List<String> mKeysToKeepAfterLogout = Arrays.asList(
            SETTINGS_HIDE_READ_RECEIPTS_KEY,
            SETTINGS_ALWAYS_SHOW_TIMESTAMPS_KEY,
            SETTINGS_12_24_TIMESTAMPS_KEY,
            SETTINGS_DONT_SEND_TYPING_NOTIF_KEY,
            SETTINGS_HIDE_JOIN_LEAVE_MESSAGES_KEY,
            SETTINGS_HIDE_AVATAR_DISPLAY_NAME_CHANGES_MESSAGES_KEY,
            SETTINGS_MEDIA_SAVING_PERIOD_KEY,
            SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY,

            SETTINGS_PIN_UNREAD_MESSAGES_PREFERENCE_KEY,
            SETTINGS_PIN_MISSED_NOTIFICATIONS_PREFERENCE_KEY,
            SETTINGS_GA_USE_SETTINGS_PREFERENCE_KEY,
            SETTINGS_DATA_SAVE_MODE_PREFERENCE_KEY,
            SETTINGS_START_ON_BOOT_PREFERENCE_KEY,
            SETTINGS_INTERFACE_TEXT_SIZE_KEY,
            SETTINGS_USE_JITSI_CONF_PREFERENCE_KEY,
            SETTINGS_USE_MATRIX_APPS_PREFERENCE_KEY,
            SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY,
            SETTINGS_NOTIFICATION_RINGTONE_SELECTION_PREFERENCE_KEY,

            SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY,
            SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY,
            SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_PREFERENCE_KEY,
            SETTINGS_CONTACTS_PHONEBOOK_COUNTRY_PREFERENCE_KEY,
            SETTINGS_INTERFACE_LANGUAGE_PREFERENCE_KEY,
            SETTINGS_BACKGROUND_SYNC_PREFERENCE_KEY,
            SETTINGS_ENABLE_BACKGROUND_SYNC_PREFERENCE_KEY,
            SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY,
            SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY
    );

    /**
     * Clear the preferences.
     *
     * @param context the context
     */
    public static void clearPreferences(Context context) {
        Set<String> keysToKeep = new HashSet<>(mKeysToKeepAfterLogout);

        // home server url
        keysToKeep.add(LoginActivity.HOME_SERVER_URL_PREF);
        keysToKeep.add(LoginActivity.IDENTITY_SERVER_URL_PREF);

        // theme
        keysToKeep.add(ThemeUtils.APPLICATION_THEME_KEY);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();

        // get all the existing keys
        Set<String> keys = preferences.getAll().keySet();
        // remove the one to keep

        keys.removeAll(keysToKeep);

        for(String key : keys) {
            editor.remove(key);
        }

        editor.commit();
    }

    /**
     * Tells if the timestamp must be displayed in 12h format
     *
     * @param context the context
     * @return true if the time must be displayed in 12h format
     */
    public static boolean displayTimeIn12hFormat(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_12_24_TIMESTAMPS_KEY, false);
    }

    /**
     * Tells if the join / leave membership events must be hidden in the messages list.
     *
     * @param context the context
     * @return true if the join / leave membership events must be hidden in the messages list
     */
    public static boolean hideJoinLeaveMessages(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_HIDE_JOIN_LEAVE_MESSAGES_KEY, false);
    }

    /**
     * Tells if the avatar / display name events must be hidden in the messages list.
     *
     * @param context the context
     * @return true true if the avatar / display name events must be hidden in the messages list.
     */
    public static boolean hideAvatarDisplayNameChangeMessages(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_HIDE_AVATAR_DISPLAY_NAME_CHANGES_MESSAGES_KEY, false);
    }

    /**
     * Update the notification ringtone
     * @param context the context
     * @param uri the new notification ringtone
     */
    public static void setNotificationRingTone(Context context, Uri uri) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();

        String value = "";

        if (null != uri) {
            value = uri.toString();

            if (value.startsWith("file://")) {
                // it should never happen
                // else android.os.FileUriExposedException will be triggered.
                // see https://github.com/vector-im/riot-android/issues/1725
                return;
            }
        }

        editor.putString(SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY, value);
        editor.commit();
    }

    /**
     * Provides the selected notification ring tone
     * @param context the context
     * @return the selected ring tone
     */
    public static Uri getNotificationRingTone(Context context) {
        String url = PreferenceManager.getDefaultSharedPreferences(context).getString(SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY, null);

        // the user selects "None"
        if (TextUtils.equals(url, "")) {
            return null;
        }

        Uri uri = null;

        // https://github.com/vector-im/riot-android/issues/1725
        if ((null != url) && !url.startsWith("file://")) {
            try {
                uri = Uri.parse(url);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getNotificationRingTone() : Uri.parse failed");
            }
        }

        if (null == uri) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        Log.d(LOG_TAG, "## getNotificationRingTone() returns " + uri);
        return uri;
    }

    /**
     * Provide the notification ringtone filename
     * @param context the context
     * @return the filename
     */
    public static String getNotificationRingToneName(Context context) {
        Uri toneUri = getNotificationRingTone(context);

        if (null == toneUri) {
            return null;
        }

        String name = null;

        Cursor cursor = null;

        try {
            String[] proj = {MediaStore.Audio.Media.DATA};
            cursor = context.getContentResolver().query(toneUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            cursor.moveToFirst();

            File file = new File(cursor.getString(column_index));
            name = file.getName();

            if (name.contains(".")) {
                name = name.substring(0, name.lastIndexOf("."));
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getNotificationRingToneName() failed() : " + e.getMessage());
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return name;
    }

    /**
     * Tells if the data save mode is enabled
     *
     * @param context the context
     * @return true if the data save mode is enabled
     */
    public static boolean useDataSaveMode(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_DATA_SAVE_MODE_PREFERENCE_KEY, false);
    }

    /**
     * Tells if the conf calls must be done with Jitsi.
     *
     * @param context the context
     * @return true if the conference call must be done with jitsi.
     */
    public static boolean useJitsiConfCall(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_USE_JITSI_CONF_PREFERENCE_KEY, true);
    }
    
    /**
     * Tells if the matrix apps are supported.
     *
     * @param context the context
     * @return true if the matrix apps are supported.
     */
    public static boolean useMatrixApps(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_USE_MATRIX_APPS_PREFERENCE_KEY, false);
    }

    /**
     * Tells if the application is started on boot
     *
     * @param context the context
     * @return true if the application must be started on boot
     */
    public static boolean autoStartOnBoot(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_START_ON_BOOT_PREFERENCE_KEY, false);
    }

    /**
     * Tells if the application is started on boot
     *
     * @param context the context
     * @param value true to start the application on boot
     */
    public static void setAutoStartOnBoot(Context context, boolean value) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(SETTINGS_START_ON_BOOT_PREFERENCE_KEY, value);
        editor.commit();
    }

    /**
     * Provides the medias saving choice list.
     *
     * @param context the context
     * @return the list
     */
    public static CharSequence[] getMediasSavingItemsChoicesList(Context context) {
        return new CharSequence[]{
                context.getString(R.string.media_saving_period_3_days),
                context.getString(R.string.media_saving_period_1_week),
                context.getString(R.string.media_saving_period_1_month),
                context.getString(R.string.media_saving_period_forever)};
    }

    /**
     * Provides the selected saving period.
     *
     * @param context the context
     * @return the selected period
     */
    public static int getSelectedMediasSavingPeriod(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY, MEDIA_SAVING_1_WEEK);
    }

    /**
     * Updates the selected saving period.
     *
     * @param context the context
     * @param index the selected period index
     */
    public static void setSelectedMediasSavingPeriod(Context context, int index) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY, index);
        editor.commit();
    }

    /**
     * Provides the minimum last access time to keep a media file.
     *
     * @param context the context
     * @return the min last access time (in seconds)
     */
    public static long getMinMediasLastAccessTime(Context context) {
        int selection = getSelectedMediasSavingPeriod(context);

        switch (selection) {
            case MEDIA_SAVING_3_DAYS:
                return (System.currentTimeMillis()/1000) - (3 * 24 * 60 * 60);
            case MEDIA_SAVING_1_WEEK:
                return (System.currentTimeMillis()/1000) - (7 * 24 * 60 * 60);
            case MEDIA_SAVING_1_MONTH:
                return (System.currentTimeMillis()/1000) - (30 * 24 * 60 * 60);
            case MEDIA_SAVING_FOREVER:
                return 0;
        }

        return 0;
    }

    /**
     * Provides the selected saving period.
     *
     * @param context the context
     * @return the selected period
     */
    public static String getSelectedMediasSavingPeriodString(Context context) {
        int selection = getSelectedMediasSavingPeriod(context);

        switch (selection) {
            case MEDIA_SAVING_3_DAYS:
                return context.getString(R.string.media_saving_period_3_days);
            case MEDIA_SAVING_1_WEEK:
                return context.getString(R.string.media_saving_period_1_week);
            case MEDIA_SAVING_1_MONTH:
                return context.getString(R.string.media_saving_period_1_month);
            case MEDIA_SAVING_FOREVER:
                return context.getString(R.string.media_saving_period_forever);
        }
        return "?";
    }

    /**
     * Fix some migration issues
     */
    public static void fixMigrationIssues(Context context) {
        // some key names have been updated to supported language switch
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (preferences.contains(context.getString(R.string.ga_use_settings))) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(SETTINGS_GA_USE_SETTINGS_PREFERENCE_KEY, preferences.getBoolean(context.getString(R.string.ga_use_settings), false));
            editor.remove(context.getString(R.string.ga_use_settings));
            editor.commit();
        }

        if (preferences.contains(context.getString(R.string.settings_pin_missed_notifications))) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(SETTINGS_PIN_MISSED_NOTIFICATIONS_PREFERENCE_KEY, preferences.getBoolean(context.getString(R.string.settings_pin_missed_notifications), false));
            editor.remove(context.getString(R.string.settings_pin_missed_notifications));
            editor.commit();
        }

        if (preferences.contains(context.getString(R.string.settings_pin_unread_messages))) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(SETTINGS_PIN_UNREAD_MESSAGES_PREFERENCE_KEY, preferences.getBoolean(context.getString(R.string.settings_pin_unread_messages), false));
            editor.remove(context.getString(R.string.settings_pin_unread_messages));
            editor.commit();
        }

        if (preferences.contains("MARKDOWN_PREFERENCE_KEY")) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(SETTINGS_DISABLE_MARKDOWN_KEY, !preferences.getBoolean("MARKDOWN_PREFERENCE_KEY", false));
            editor.remove("MARKDOWN_PREFERENCE_KEY");
            editor.commit();
        }

        if (!preferences.contains(SETTINGS_USE_JITSI_CONF_PREFERENCE_KEY)) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(SETTINGS_USE_JITSI_CONF_PREFERENCE_KEY, true);
            editor.commit();
        }

        if (!preferences.contains(SETTINGS_PIN_MISSED_NOTIFICATIONS_PREFERENCE_KEY)) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(SETTINGS_PIN_MISSED_NOTIFICATIONS_PREFERENCE_KEY, true);
            editor.commit();
        }

        if (!preferences.contains(SETTINGS_PIN_UNREAD_MESSAGES_PREFERENCE_KEY)) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(SETTINGS_PIN_UNREAD_MESSAGES_PREFERENCE_KEY, true);
            editor.commit();
        }
    }

    /**
     * Tells if the markdown is enabled
     *
     * @param context the context
     * @return true if the markdown is enabled
     */
    public static boolean isMarkdownEnabled(Context context) {
        return !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_DISABLE_MARKDOWN_KEY, false);
    }

    /**
     * Update the markdown enable status.
     *
     * @param context the context
     * @param isEnabled true to enable the markdown
     */
    public static void setMarkdownEnabled(Context context, boolean isEnabled) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(SETTINGS_DISABLE_MARKDOWN_KEY, !isEnabled);
        editor.commit();
    }

    /**
     * Tells if the read receipts must be hidden
     *
     * @param context the context
     * @return true if the read receipts must be hidden
     */
    public static boolean hideReadReceipts(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_HIDE_READ_RECEIPTS_KEY, false);
    }

    /**
     * Tells if the message timestamps must be always shown.
     *
     * @param context the context
     * @return true if the message timestamps must be always shown.
     */
    public static boolean alwaysShowTimeStamps(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_ALWAYS_SHOW_TIMESTAMPS_KEY, false);
    }

    /**
     * Tells if the typing notifications must NOT be sent
     *
     * @param context the context
     * @return true to do NOT send the typing notifs
     */
    public static boolean dontSendTypingNotifs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_DONT_SEND_TYPING_NOTIF_KEY, false);
    }

    /**
     * Tells of the missing notifications rooms must be displayed at left (home screen)
     *
     * @param context the context
     * @return true to move the missed notifications to the left side
     */
    public static boolean pinMissedNotifications(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_PIN_MISSED_NOTIFICATIONS_PREFERENCE_KEY, false);
    }

    /**
     * Tells of the unread rooms must be displayed at left (home screen)
     *
     * @param context the context
     * @return true to move the unread room to the left side
     */
    public static boolean pinUnreadMessages(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_PIN_UNREAD_MESSAGES_PREFERENCE_KEY, false);
    }

    /**
     * Update the GA use.
     *
     * @param context the context
     * @param value   the new value
     */
    public static void setUseGA(Context context, boolean value) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(SETTINGS_GA_USE_SETTINGS_PREFERENCE_KEY, value);
        editor.commit();

        GAHelper.initGoogleAnalytics(context);
    }

    /**
     * Tells if GA can be used
     *
     * @param context the context
     * @return null if not defined, true / false when defined
     */
    public static Boolean useGA(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (preferences.contains(SETTINGS_GA_USE_SETTINGS_PREFERENCE_KEY)) {
            return preferences.getBoolean(SETTINGS_GA_USE_SETTINGS_PREFERENCE_KEY, false);
        } else {
            try {
                // test if the client should not use GA
                boolean allowGA = TextUtils.equals(context.getResources().getString(R.string.allow_ga_use), "true");

                if (!allowGA) {
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean(SETTINGS_GA_USE_SETTINGS_PREFERENCE_KEY, false);
                    editor.commit();

                    return false;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "useGA " + e.getLocalizedMessage());
            }

            return null;
        }
    }
}
