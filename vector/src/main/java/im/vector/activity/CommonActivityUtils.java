/*
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.annotation.AttrRes;
import android.support.annotation.ColorInt;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.TextUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import im.vector.Matrix;
import im.vector.MyPresenceManager;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.adapters.VectorRoomsSelectionAdapter;
import im.vector.contacts.ContactsManager;
import im.vector.contacts.PIDsRetriever;
import im.vector.fragments.VectorUnknownDevicesFragment;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.services.EventStreamService;
import im.vector.util.PreferencesManager;
import im.vector.util.ThemeUtils;
import im.vector.util.VectorUtils;
import me.leolin.shortcutbadger.ShortcutBadger;

/**
 * Contains useful functions which are called in multiple activities.
 */
public class CommonActivityUtils {
    private static final String LOG_TAG = CommonActivityUtils.class.getSimpleName();

    /**
     * Schemes
     */
    private static final String HTTP_SCHEME = "http://";
    private static final String HTTPS_SCHEME = "https://";

    // global helper constants:
    /**
     * The view is visible
     **/
    public static final float UTILS_OPACITY_NONE = 1f;
    /**
     * The view is half dimmed
     **/
    public static final float UTILS_OPACITY_HALF = 0.5f;
    /**
     * The view is hidden
     **/
    public static final float UTILS_OPACITY_FULL = 0f;

    public static final boolean UTILS_DISPLAY_PROGRESS_BAR = true;
    public static final boolean UTILS_HIDE_PROGRESS_BAR = false;

    // room details members:
    public static final String KEY_GROUPS_EXPANDED_STATE = "KEY_GROUPS_EXPANDED_STATE";
    public static final String KEY_SEARCH_PATTERN = "KEY_SEARCH_PATTERN";
    public static final boolean GROUP_IS_EXPANDED = true;
    public static final boolean GROUP_IS_COLLAPSED = false;

    // power levels
    public static final float UTILS_POWER_LEVEL_ADMIN = 100;
    public static final float UTILS_POWER_LEVEL_MODERATOR = 50;
    private static final int ROOM_SIZE_ONE_TO_ONE = 2;

    // Android M permission request code management
    private static final boolean PERMISSIONS_GRANTED = true;
    private static final boolean PERMISSIONS_DENIED = !PERMISSIONS_GRANTED;
    private static final int PERMISSION_BYPASSED = 0x0;
    public static final int PERMISSION_CAMERA = 0x1;
    private static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 0x1 << 1;
    private static final int PERMISSION_RECORD_AUDIO = 0x1 << 2;
    private static final int PERMISSION_READ_CONTACTS = 0x1 << 3;
    public static final int REQUEST_CODE_PERMISSION_AUDIO_IP_CALL = PERMISSION_RECORD_AUDIO;
    public static final int REQUEST_CODE_PERMISSION_VIDEO_IP_CALL = PERMISSION_CAMERA | PERMISSION_RECORD_AUDIO;
    public static final int REQUEST_CODE_PERMISSION_TAKE_PHOTO = PERMISSION_CAMERA | PERMISSION_WRITE_EXTERNAL_STORAGE;
    public static final int REQUEST_CODE_PERMISSION_MEMBERS_SEARCH = PERMISSION_READ_CONTACTS;
    public static final int REQUEST_CODE_PERMISSION_MEMBER_DETAILS = PERMISSION_READ_CONTACTS;
    public static final int REQUEST_CODE_PERMISSION_ROOM_DETAILS = PERMISSION_CAMERA;
    public static final int REQUEST_CODE_PERMISSION_VIDEO_RECORDING = PERMISSION_CAMERA | PERMISSION_RECORD_AUDIO;
    public static final int REQUEST_CODE_PERMISSION_HOME_ACTIVITY = PERMISSION_WRITE_EXTERNAL_STORAGE;
    private static final int REQUEST_CODE_PERMISSION_BY_PASS = PERMISSION_BYPASSED;

    /**
     * Logout a sessions list
     *
     * @param context          the context
     * @param sessions         the sessions list
     * @param clearCredentials true to clear the credentials
     * @param callback         the asynchronous callback
     */
    public static void logout(Context context, List<MXSession> sessions, boolean clearCredentials, final SimpleApiCallback<Void> callback) {
        logout(context, sessions.iterator(), clearCredentials, callback);
    }

    /**
     * Internal method to logout a sessions list
     *
     * @param context          the context
     * @param sessions         the sessions iterator
     * @param clearCredentials true to clear the credentials
     * @param callback         the asynchronous callback
     */
    private static void logout(final Context context, final Iterator<MXSession> sessions, final boolean clearCredentials, final SimpleApiCallback<Void> callback) {
        if (!sessions.hasNext()) {
            if (null != callback) {
                callback.onSuccess(null);
            }

            return;
        }

        MXSession session = sessions.next();

        if (session.isAlive()) {
            // stop the service
            EventStreamService eventStreamService = EventStreamService.getInstance();

            // reported by a rageshake
            if (null != eventStreamService) {
                ArrayList<String> matrixIds = new ArrayList<>();
                matrixIds.add(session.getMyUserId());
                eventStreamService.stopAccounts(matrixIds);
            }

            // Publish to the server that we're now offline
            MyPresenceManager.getInstance(context, session).advertiseOffline();
            MyPresenceManager.remove(session);

            // clear notification
            EventStreamService.removeNotification();

            // unregister from the GCM.
            Matrix.getInstance(context).getSharedGCMRegistrationManager().unregister(session, null);

            // clear credentials
            Matrix.getInstance(context).clearSession(context, session, clearCredentials, new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    logout(context, sessions, clearCredentials, callback);
                }
            });
        }
    }

    public static boolean shouldRestartApp(Context context) {
        EventStreamService eventStreamService = EventStreamService.getInstance();

        if (!Matrix.hasValidSessions()) {
            Log.e(LOG_TAG, "shouldRestartApp : the client has no valid session");
        }

        if (null == eventStreamService) {
            Log.e(LOG_TAG, "eventStreamService is null : restart the event stream");
            CommonActivityUtils.startEventStreamService(context);
        }

        return !Matrix.hasValidSessions();
    }

    /**
     * With android M, the permissions kills the backgrounded application
     * and try to restart the last opened activity.
     * But, the sessions are not initialised (i.e the stores are not ready and so on).
     * Thus, the activity could have an invalid behaviour.
     * It seems safer to go to splash screen and to wait for the end of the initialisation.
     *
     * @param activity the caller activity
     * @return true if go to splash screen
     */
    public static boolean isGoingToSplash(Activity activity) {
        return isGoingToSplash(activity, null, null);
    }

    /**
     * With android M, the permissions kills the backgrounded application
     * and try to restart the last opened activity.
     * But, the sessions are not initialised (i.e the stores are not ready and so on).
     * Thus, the activity could have an invalid behaviour.
     * It seems safer to go to splash screen and to wait for the end of the initialisation.
     *
     * @param activity  the caller activity
     * @param sessionId the session id
     * @param roomId    the room id
     * @return true if go to splash screen
     */
    public static boolean isGoingToSplash(Activity activity, String sessionId, String roomId) {
        if (Matrix.hasValidSessions()) {
            List<MXSession> sessions = Matrix.getInstance(activity).getSessions();

            for (MXSession session : sessions) {
                if (session.isAlive() && !session.getDataHandler().getStore().isReady()) {
                    Intent intent = new Intent(activity, SplashActivity.class);

                    if ((null != sessionId) && (null != roomId)) {
                        intent.putExtra(SplashActivity.EXTRA_MATRIX_ID, sessionId);
                        intent.putExtra(SplashActivity.EXTRA_ROOM_ID, roomId);
                    }

                    activity.startActivity(intent);
                    activity.finish();
                    return true;
                }
            }
        }

        return false;
    }

    private static final String RESTART_IN_PROGRESS_KEY = "RESTART_IN_PROGRESS_KEY";

    /**
     * The application has been started
     */
    public static void onApplicationStarted(Activity activity) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putBoolean(RESTART_IN_PROGRESS_KEY, false);
        editor.commit();
    }

    /**
     * Restart the application after 100ms
     *
     * @param activity activity
     */
    public static void restartApp(Activity activity) {
        // clear the preferences
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = preferences.edit();

        // use the preference to avoid infinite relaunch on some devices
        // the culprit activity is restarted when System.exit is called.
        // so called it once to fix it
        if (!preferences.getBoolean(RESTART_IN_PROGRESS_KEY, false)) {
            CommonActivityUtils.displayToast(activity.getApplicationContext(), "Restart the application (low memory)");

            Log.e(LOG_TAG, "Kill the application");
            editor.putBoolean(RESTART_IN_PROGRESS_KEY, true);
            editor.commit();

            PendingIntent mPendingIntent = PendingIntent.getActivity(activity, 314159, new Intent(activity, LoginActivity.class), PendingIntent.FLAG_CANCEL_CURRENT);

            // so restart the application after 100ms
            AlarmManager mgr = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 50, mPendingIntent);

            System.exit(0);
        } else {
            Log.e(LOG_TAG, "The application is restarting, please wait !!");
            activity.finish();
        }
    }

    /**
     * Logout the current user.
     * Jump to the login page when the logout is done.
     *
     * @param activity the caller activity
     */
    public static void logout(Activity activity) {
        logout(activity, true);
    }

    /**
     * Logout the current user.
     *
     * @param activity      the caller activity
     * @param goToLoginPage true to jump to the login page
     */
    public static void logout(final Activity activity, final boolean goToLoginPage) {
        Log.d(LOG_TAG, "## logout() : from " + activity + " goToLoginPage " + goToLoginPage);

        // if no activity is provided, use the application context instead.
        final Context context = (null == activity) ? VectorApp.getInstance().getApplicationContext() : activity;

        EventStreamService.removeNotification();
        stopEventStream(context);

        try {
            ShortcutBadger.setBadge(context, 0);
        } catch (Exception e) {
            Log.d(LOG_TAG, "## logout(): Exception Msg=" + e.getMessage());
        }

        // warn that the user logs out
        Collection<MXSession> sessions = Matrix.getMXSessions(context);
        for (MXSession session : sessions) {
            // Publish to the server that we're now offline
            MyPresenceManager.getInstance(context, session).advertiseOffline();
            MyPresenceManager.remove(session);
        }

        // clear the preferences
        PreferencesManager.clearPreferences(context);

        // reset the GCM
        Matrix.getInstance(context).getSharedGCMRegistrationManager().resetGCMRegistration();
        // clear the preferences when the application goes to the login screen.
        if (goToLoginPage) {
            // display a dummy activity until the logout is done
            Matrix.getInstance(context).getSharedGCMRegistrationManager().clearPreferences();

            if (null != activity) {
                // go to login page
                activity.startActivity(new Intent(activity, LoggingOutActivity.class));
                activity.finish();
            } else {
                Intent intent = new Intent(context, LoggingOutActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
            }
        }

        // clear credentials
        Matrix.getInstance(context).clearSessions(context, true, new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // ensure that corrupted values are cleared
                Matrix.getInstance(context).getLoginStorage().clear();

                // clear the tmp store list
                Matrix.getInstance(context).clearTmpStoresList();

                // reset the contacts
                PIDsRetriever.getInstance().reset();
                ContactsManager.getInstance().reset();

                MXMediasCache.clearThumbnailsCache(context);

                if (goToLoginPage) {
                    Activity activeActivity = VectorApp.getCurrentActivity();
                    if (null != activeActivity) {
                        // go to login page
                        activeActivity.startActivity(new Intent(activeActivity, LoginActivity.class));
                        activeActivity.finish();
                    } else {
                        Intent intent = new Intent(context, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(intent);
                    }
                }
            }
        });
    }

    /**
     * Remove the http schemes from the URl passed in parameter
     *
     * @param aUrl URL to be parsed
     * @return the URL with the scheme removed
     */
    public static String removeUrlScheme(String aUrl) {
        String urlRetValue = aUrl;

        if (null != aUrl) {
            // remove URL scheme
            if (aUrl.startsWith(HTTP_SCHEME)) {
                urlRetValue = aUrl.substring(HTTP_SCHEME.length());
            } else if (aUrl.startsWith(HTTPS_SCHEME)) {
                urlRetValue = aUrl.substring(HTTPS_SCHEME.length());
            }
        }

        return urlRetValue;
    }

    //==============================================================================================================
    // Events stream service
    //==============================================================================================================

    /**
     * Indicate if a user is logged out or not. If no default session is enabled,
     * no user is logged.
     *
     * @param aContext App context
     * @return true if no user is logged in, false otherwise
     */
    private static boolean isUserLogout(Context aContext) {
        boolean retCode = false;

        if (null == aContext) {
            retCode = true;
        } else {
            if (null == Matrix.getInstance(aContext.getApplicationContext()).getDefaultSession()) {
                retCode = true;
            }
        }

        return retCode;
    }

    /**
     * Send an action to the events service.
     *
     * @param context the context.
     * @param action  the action to send.
     */
    private static void sendEventStreamAction(Context context, EventStreamService.StreamAction action) {
        Context appContext = context.getApplicationContext();

        if (!isUserLogout(appContext)) {
            Intent eventStreamService = new Intent(appContext, EventStreamService.class);

            if ((action == EventStreamService.StreamAction.CATCHUP) && (EventStreamService.isStopped())) {
                Log.d(LOG_TAG, "sendEventStreamAction : auto restart");
                eventStreamService.putExtra(EventStreamService.EXTRA_AUTO_RESTART_ACTION, EventStreamService.EXTRA_AUTO_RESTART_ACTION);
            } else {
                Log.d(LOG_TAG, "sendEventStreamAction " + action);
                eventStreamService.putExtra(EventStreamService.EXTRA_STREAM_ACTION, action.ordinal());
            }

            appContext.startService(eventStreamService);
        } else {
            Log.d(LOG_TAG, "## sendEventStreamAction(): \"" + action + "\" action not sent - user logged out");
        }
    }

    /**
     * Stop the event stream.
     *
     * @param context the context.
     */
    private static void stopEventStream(Context context) {
        Log.d(LOG_TAG, "stopEventStream");
        sendEventStreamAction(context, EventStreamService.StreamAction.STOP);
    }

    /**
     * Pause the event stream.
     *
     * @param context the context.
     */
    public static void pauseEventStream(Context context) {
        Log.d(LOG_TAG, "pauseEventStream");
        sendEventStreamAction(context, EventStreamService.StreamAction.PAUSE);
    }

    /**
     * Resume the events stream
     *
     * @param context the context.
     */
    public static void resumeEventStream(Context context) {
        Log.d(LOG_TAG, "resumeEventStream");
        sendEventStreamAction(context, EventStreamService.StreamAction.RESUME);
    }

    /**
     * Trigger a event stream catchup i.e. there is only sync/ call.
     *
     * @param context the context.
     */
    public static void catchupEventStream(Context context) {
        if (VectorApp.isAppInBackground()) {
            Log.d(LOG_TAG, "catchupEventStream");
            sendEventStreamAction(context, EventStreamService.StreamAction.CATCHUP);
        }
    }

    /**
     * Warn the events stream that there was a GCM status update.
     *
     * @param context the context.
     */
    public static void onGcmUpdate(Context context) {
        Log.d(LOG_TAG, "onGcmUpdate");
        sendEventStreamAction(context, EventStreamService.StreamAction.GCM_STATUS_UPDATE);
    }

    /**
     * Start the events stream service.
     *
     * @param context the context.
     */
    public static void startEventStreamService(Context context) {
        // the events stream service is launched
        // either the application has never be launched
        // or the service has been killed on low memory
        if (EventStreamService.isStopped()) {
            ArrayList<String> matrixIds = new ArrayList<>();
            Collection<MXSession> sessions = Matrix.getInstance(context.getApplicationContext()).getSessions();

            if ((null != sessions) && (sessions.size() > 0)) {
                GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(context).getSharedGCMRegistrationManager();
                Log.e(LOG_TAG, "## startEventStreamService() : restart EventStreamService");

                for (MXSession session : sessions) {
                    // reported by GA
                    if ((null != session.getDataHandler()) && (null != session.getDataHandler().getStore())) {
                        boolean isSessionReady = session.getDataHandler().getStore().isReady();

                        if (!isSessionReady) {
                            Log.e(LOG_TAG, "## startEventStreamService() : the session " + session.getMyUserId() + " is not opened");
                            session.getDataHandler().getStore().open();
                        } else {
                            // it seems that the crypto is not always restarted properly after a crash
                            Log.e(LOG_TAG, "## startEventStreamService() : check if the crypto of the session " + session.getMyUserId());
                            session.checkCrypto();
                        }

                        session.setSyncDelay(gcmRegistrationManager.isBackgroundSyncAllowed() ? gcmRegistrationManager.getBackgroundSyncDelay() : 0);
                        session.setSyncTimeout(gcmRegistrationManager.getBackgroundSyncTimeOut());

                        // session to activate
                        matrixIds.add(session.getCredentials().userId);
                    }
                }

                // check size
                if (matrixIds.size() > 0) {
                    Intent intent = new Intent(context, EventStreamService.class);
                    intent.putExtra(EventStreamService.EXTRA_MATRIX_IDS, matrixIds.toArray(new String[matrixIds.size()]));
                    intent.putExtra(EventStreamService.EXTRA_STREAM_ACTION, EventStreamService.StreamAction.START.ordinal());
                    context.startService(intent);
                }
            }

            if (null != EventStreamService.getInstance()) {
                EventStreamService.getInstance().refreshStatusNotification();
            }
        }
    }

    /**
     * Check if the user power level allows to update the room avatar. This is mainly used to
     * determine if camera permission must be checked or not.
     *
     * @param aRoom    the room
     * @param aSession the session
     * @return true if the user power level allows to update the avatar, false otherwise.
     */
    public static boolean isPowerLevelEnoughForAvatarUpdate(Room aRoom, MXSession aSession) {
        boolean canUpdateAvatarWithCamera = false;
        PowerLevels powerLevels;

        if ((null != aRoom) && (null != aSession)) {
            if (null != (powerLevels = aRoom.getLiveState().getPowerLevels())) {
                int powerLevel = powerLevels.getUserPowerLevel(aSession.getMyUserId());

                // check the power level against avatar level
                canUpdateAvatarWithCamera = (powerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_AVATAR));
            }
        }

        return canUpdateAvatarWithCamera;
    }

    /**
     * Check if the permissions provided in the list are granted.
     * This is an asynchronous method if permissions are requested, the final response
     * is provided in onRequestPermissionsResult(). In this case checkPermissions()
     * returns false.
     * <br>If checkPermissions() returns true, the permissions were already granted.
     * The permissions to be granted are given as bit map in aPermissionsToBeGrantedBitMap (ex: {@link #REQUEST_CODE_PERMISSION_TAKE_PHOTO}).
     * <br>aPermissionsToBeGrantedBitMap is passed as the request code in onRequestPermissionsResult().
     * <p>
     * If a permission was already denied by the user, a popup is displayed to
     * explain why vector needs the corresponding permission.
     *
     * @param aPermissionsToBeGrantedBitMap the permissions bit map to be granted
     * @param aCallingActivity              the calling Activity that is requesting the permissions (or fragment parent)
     * @param fragment                      the calling fragment that is requesting the permissions
     * @return true if the permissions are granted (synchronous flow), false otherwise (asynchronous flow)
     */
    private static boolean checkPermissions(final int aPermissionsToBeGrantedBitMap, final Activity aCallingActivity, final Fragment fragment) {
        boolean isPermissionGranted = false;

        // sanity check
        if (null == aCallingActivity) {
            Log.w(LOG_TAG, "## checkPermissions(): invalid input data");
            isPermissionGranted = false;
        } else if (REQUEST_CODE_PERMISSION_BY_PASS == aPermissionsToBeGrantedBitMap) {
            isPermissionGranted = true;
        } else if ((REQUEST_CODE_PERMISSION_TAKE_PHOTO != aPermissionsToBeGrantedBitMap)
                && (REQUEST_CODE_PERMISSION_AUDIO_IP_CALL != aPermissionsToBeGrantedBitMap)
                && (REQUEST_CODE_PERMISSION_VIDEO_IP_CALL != aPermissionsToBeGrantedBitMap)
                && (REQUEST_CODE_PERMISSION_MEMBERS_SEARCH != aPermissionsToBeGrantedBitMap)
                && (REQUEST_CODE_PERMISSION_HOME_ACTIVITY != aPermissionsToBeGrantedBitMap)
                && (REQUEST_CODE_PERMISSION_MEMBER_DETAILS != aPermissionsToBeGrantedBitMap)
                && (REQUEST_CODE_PERMISSION_ROOM_DETAILS != aPermissionsToBeGrantedBitMap)
                ) {
            Log.w(LOG_TAG, "## checkPermissions(): permissions to be granted are not supported");
            isPermissionGranted = false;
        } else {
            List<String> permissionListAlreadyDenied = new ArrayList<>();
            List<String> permissionsListToBeGranted = new ArrayList<>();
            final List<String> finalPermissionsListToBeGranted;
            boolean isRequestPermissionRequired = false;
            Resources resource = aCallingActivity.getResources();
            String explanationMessage = "";
            String permissionType;

            // retrieve the permissions to be granted according to the request code bit map
            if (PERMISSION_CAMERA == (aPermissionsToBeGrantedBitMap & PERMISSION_CAMERA)) {
                permissionType = Manifest.permission.CAMERA;
                isRequestPermissionRequired |= updatePermissionsToBeGranted(aCallingActivity, permissionListAlreadyDenied, permissionsListToBeGranted, permissionType);
            }

            if (PERMISSION_RECORD_AUDIO == (aPermissionsToBeGrantedBitMap & PERMISSION_RECORD_AUDIO)) {
                permissionType = Manifest.permission.RECORD_AUDIO;
                isRequestPermissionRequired |= updatePermissionsToBeGranted(aCallingActivity, permissionListAlreadyDenied, permissionsListToBeGranted, permissionType);
            }

            if (PERMISSION_WRITE_EXTERNAL_STORAGE == (aPermissionsToBeGrantedBitMap & PERMISSION_WRITE_EXTERNAL_STORAGE)) {
                permissionType = Manifest.permission.WRITE_EXTERNAL_STORAGE;
                isRequestPermissionRequired |= updatePermissionsToBeGranted(aCallingActivity, permissionListAlreadyDenied, permissionsListToBeGranted, permissionType);
            }

            // the contact book access is requested for any android platforms
            // for android M, we use the system preferences
            // for android < M, we use a dedicated settings
            if (PERMISSION_READ_CONTACTS == (aPermissionsToBeGrantedBitMap & PERMISSION_READ_CONTACTS)) {
                permissionType = Manifest.permission.READ_CONTACTS;

                if (Build.VERSION.SDK_INT >= 23) {
                    isRequestPermissionRequired |= updatePermissionsToBeGranted(aCallingActivity, permissionListAlreadyDenied, permissionsListToBeGranted, permissionType);
                } else {
                    if (!ContactsManager.getInstance().isContactBookAccessRequested()) {
                        isRequestPermissionRequired = true;
                        permissionsListToBeGranted.add(permissionType);
                    }
                }
            }

            finalPermissionsListToBeGranted = permissionsListToBeGranted;

            // if some permissions were already denied: display a dialog to the user before asking again..
            if (!permissionListAlreadyDenied.isEmpty()) {
                if (null != resource) {
                    // add the user info text to be displayed to explain why the permission is required by the App
                    if (aPermissionsToBeGrantedBitMap == REQUEST_CODE_PERMISSION_VIDEO_IP_CALL || aPermissionsToBeGrantedBitMap == REQUEST_CODE_PERMISSION_AUDIO_IP_CALL) {
                        // Permission request for VOIP call
                        if (permissionListAlreadyDenied.contains(Manifest.permission.CAMERA)
                                && permissionListAlreadyDenied.contains(Manifest.permission.RECORD_AUDIO)) {
                            // Both missing
                            explanationMessage += resource.getString(R.string.permissions_rationale_msg_camera_and_audio);
                        } else if (permissionListAlreadyDenied.contains(Manifest.permission.RECORD_AUDIO)) {
                            // Audio missing
                            explanationMessage += resource.getString(R.string.permissions_rationale_msg_record_audio);
                            explanationMessage += resource.getString(R.string.permissions_rationale_msg_record_audio_explanation);
                        } else if (permissionListAlreadyDenied.contains(Manifest.permission.CAMERA)) {
                            // Camera missing
                            explanationMessage += resource.getString(R.string.permissions_rationale_msg_camera);
                            explanationMessage += resource.getString(R.string.permissions_rationale_msg_camera_explanation);
                        }
                    } else {
                        for (String permissionAlreadyDenied : permissionListAlreadyDenied) {
                            if (Manifest.permission.CAMERA.equals(permissionAlreadyDenied)) {
                                if (!TextUtils.isEmpty(explanationMessage)) {
                                    explanationMessage += "\n\n";
                                }
                                explanationMessage += resource.getString(R.string.permissions_rationale_msg_camera);
                            } else if (Manifest.permission.RECORD_AUDIO.equals(permissionAlreadyDenied)) {
                                if (!TextUtils.isEmpty(explanationMessage)) {
                                    explanationMessage += "\n\n";
                                }
                                explanationMessage += resource.getString(R.string.permissions_rationale_msg_record_audio);
                            } else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissionAlreadyDenied)) {
                                if (!TextUtils.isEmpty(explanationMessage)) {
                                    explanationMessage += "\n\n";
                                }
                                explanationMessage += resource.getString(R.string.permissions_rationale_msg_storage);
                            } else if (Manifest.permission.READ_CONTACTS.equals(permissionAlreadyDenied)) {
                                if (!TextUtils.isEmpty(explanationMessage)) {
                                    explanationMessage += "\n\n";
                                }
                                explanationMessage += resource.getString(R.string.permissions_rationale_msg_contacts);
                            } else {
                                Log.d(LOG_TAG, "## checkPermissions(): already denied permission not supported");
                            }
                        }
                    }
                } else { // fall back if resource is null.. very unlikely
                    explanationMessage = "You are about to be asked to grant permissions..\n\n";
                }

                // display the dialog with the info text
                AlertDialog.Builder permissionsInfoDialog = new AlertDialog.Builder(aCallingActivity);
                if (null != resource) {
                    permissionsInfoDialog.setTitle(R.string.permissions_rationale_popup_title);
                }

                permissionsInfoDialog.setMessage(explanationMessage);
                permissionsInfoDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!finalPermissionsListToBeGranted.isEmpty()) {
                            if (fragment != null) {
                                fragment.requestPermissions(finalPermissionsListToBeGranted.toArray(new String[finalPermissionsListToBeGranted.size()]), aPermissionsToBeGrantedBitMap);
                            } else {
                                ActivityCompat.requestPermissions(aCallingActivity,
                                        finalPermissionsListToBeGranted.toArray(new String[finalPermissionsListToBeGranted.size()]), aPermissionsToBeGrantedBitMap);
                            }
                        }
                    }
                });

                Dialog dialog = permissionsInfoDialog.show();

                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        CommonActivityUtils.displayToast(aCallingActivity, aCallingActivity.getString(R.string.missing_permissions_warning));
                    }
                });

            } else {
                // some permissions are not granted, ask permissions
                if (isRequestPermissionRequired) {
                    final String[] fPermissionsArrayToBeGranted = finalPermissionsListToBeGranted.toArray(new String[finalPermissionsListToBeGranted.size()]);

                    // for android < M, we use a custom dialog to request the contacts book access.
                    if (permissionsListToBeGranted.contains(Manifest.permission.READ_CONTACTS) && (Build.VERSION.SDK_INT < 23)) {
                        AlertDialog.Builder permissionsInfoDialog = new AlertDialog.Builder(aCallingActivity);
                        permissionsInfoDialog.setIcon(android.R.drawable.ic_dialog_info);

                        if (null != resource) {
                            permissionsInfoDialog.setTitle(resource.getString(R.string.permissions_rationale_popup_title));
                        }

                        permissionsInfoDialog.setMessage(resource.getString(R.string.permissions_msg_contacts_warning_other_androids));

                        // gives the contacts book access
                        permissionsInfoDialog.setPositiveButton(aCallingActivity.getString(R.string.yes), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ContactsManager.getInstance().setIsContactBookAccessAllowed(true);
                                if (fragment != null) {
                                    fragment.requestPermissions(fPermissionsArrayToBeGranted, aPermissionsToBeGrantedBitMap);
                                } else {
                                    ActivityCompat.requestPermissions(aCallingActivity, fPermissionsArrayToBeGranted, aPermissionsToBeGrantedBitMap);
                                }
                            }
                        });

                        // or reject it
                        permissionsInfoDialog.setNegativeButton(aCallingActivity.getString(R.string.no), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ContactsManager.getInstance().setIsContactBookAccessAllowed(false);
                                if (fragment != null) {
                                    fragment.requestPermissions(fPermissionsArrayToBeGranted, aPermissionsToBeGrantedBitMap);
                                } else {
                                    ActivityCompat.requestPermissions(aCallingActivity, fPermissionsArrayToBeGranted, aPermissionsToBeGrantedBitMap);
                                }
                            }
                        });

                        permissionsInfoDialog.show();

                    } else {
                        if (fragment != null) {
                            fragment.requestPermissions(fPermissionsArrayToBeGranted, aPermissionsToBeGrantedBitMap);
                        } else {
                            ActivityCompat.requestPermissions(aCallingActivity, fPermissionsArrayToBeGranted, aPermissionsToBeGrantedBitMap);
                        }
                    }
                } else {
                    // permissions were granted, start now..
                    isPermissionGranted = true;
                }
            }
        }
        return isPermissionGranted;
    }

    /**
     * See {@link #checkPermissions(int, Activity, Fragment)}
     *
     * @param aPermissionsToBeGrantedBitMap
     * @param aCallingActivity
     * @return true if the permissions are granted (synchronous flow), false otherwise (asynchronous flow)
     */
    public static boolean checkPermissions(final int aPermissionsToBeGrantedBitMap, final Activity aCallingActivity) {
        return checkPermissions(aPermissionsToBeGrantedBitMap, aCallingActivity, null);
    }

    /**
     * See {@link #checkPermissions(int, Activity, Fragment)}
     *
     * @param aPermissionsToBeGrantedBitMap
     * @param fragment
     */
    public static void checkPermissions(final int aPermissionsToBeGrantedBitMap, final Fragment fragment) {
        checkPermissions(aPermissionsToBeGrantedBitMap, fragment.getActivity(), fragment);
    }

    /**
     * Helper method used in {@link #checkPermissions(int, Activity)} to populate the list of the
     * permissions to be granted (aPermissionsListToBeGranted_out) and the list of the permissions already denied (aPermissionAlreadyDeniedList_out).
     *
     * @param aCallingActivity                 calling activity
     * @param aPermissionAlreadyDeniedList_out list to be updated with the permissions already denied by the user
     * @param aPermissionsListToBeGranted_out  list to be updated with the permissions to be granted
     * @param permissionType                   the permission to be checked
     * @return true if the permission requires to be granted, false otherwise
     */
    private static boolean updatePermissionsToBeGranted(final Activity aCallingActivity, List<String> aPermissionAlreadyDeniedList_out, List<String> aPermissionsListToBeGranted_out, final String permissionType) {
        boolean isRequestPermissionRequested = false;

        // add permission to be granted
        aPermissionsListToBeGranted_out.add(permissionType);

        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(aCallingActivity.getApplicationContext(), permissionType)) {
            isRequestPermissionRequested = true;

            // add permission to the ones that were already asked to the user
            if (ActivityCompat.shouldShowRequestPermissionRationale(aCallingActivity, permissionType)) {
                aPermissionAlreadyDeniedList_out.add(permissionType);
            }
        }
        return isRequestPermissionRequested;
    }

    /**
     * Helper method to process {@link CommonActivityUtils#REQUEST_CODE_PERMISSION_AUDIO_IP_CALL}
     * on onRequestPermissionsResult() methods.
     *
     * @param aContext      App context
     * @param aPermissions  permissions list
     * @param aGrantResults permissions granted results
     * @return true if audio IP call is permitted, false otherwise
     */
    public static boolean onPermissionResultAudioIpCall(Context aContext, String[] aPermissions, int[] aGrantResults) {
        boolean isPermissionGranted = false;

        try {
            if (Manifest.permission.RECORD_AUDIO.equals(aPermissions[0])) {
                if (PackageManager.PERMISSION_GRANTED == aGrantResults[0]) {
                    Log.d(LOG_TAG, "## onPermissionResultAudioIpCall(): RECORD_AUDIO permission granted");
                    isPermissionGranted = true;
                } else {
                    Log.d(LOG_TAG, "## onPermissionResultAudioIpCall(): RECORD_AUDIO permission not granted");
                    if (null != aContext)
                        CommonActivityUtils.displayToast(aContext, aContext.getString(R.string.permissions_action_not_performed_missing_permissions));
                }
            }
        } catch (Exception ex) {
            Log.d(LOG_TAG, "## onPermissionResultAudioIpCall(): Exception MSg=" + ex.getMessage());
        }

        return isPermissionGranted;
    }

    /**
     * Helper method to process {@link CommonActivityUtils#REQUEST_CODE_PERMISSION_VIDEO_IP_CALL}
     * on onRequestPermissionsResult() methods.
     * For video IP calls, record audio and camera permissions are both mandatory.
     *
     * @param aContext      App context
     * @param aPermissions  permissions list
     * @param aGrantResults permissions granted results
     * @return true if video IP call is permitted, false otherwise
     */
    public static boolean onPermissionResultVideoIpCall(Context aContext, String[] aPermissions, int[] aGrantResults) {
        boolean isPermissionGranted = false;
        int result = 0;

        try {
            for (int i = 0; i < aPermissions.length; i++) {
                Log.d(LOG_TAG, "## onPermissionResultVideoIpCall(): " + aPermissions[i] + "=" + aGrantResults[i]);

                if (Manifest.permission.CAMERA.equals(aPermissions[i])) {
                    if (PackageManager.PERMISSION_GRANTED == aGrantResults[i]) {
                        Log.d(LOG_TAG, "## onPermissionResultVideoIpCall(): CAMERA permission granted");
                        result++;
                    } else {
                        Log.w(LOG_TAG, "## onPermissionResultVideoIpCall(): CAMERA permission not granted");
                    }
                }

                if (Manifest.permission.RECORD_AUDIO.equals(aPermissions[i])) {
                    if (PackageManager.PERMISSION_GRANTED == aGrantResults[i]) {
                        Log.d(LOG_TAG, "## onPermissionResultVideoIpCall(): WRITE_EXTERNAL_STORAGE permission granted");
                        result++;
                    } else {
                        Log.w(LOG_TAG, "## onPermissionResultVideoIpCall(): RECORD_AUDIO permission not granted");
                    }
                }
            }

            // Video over IP requires, both Audio & Video !
            if (2 == result) {
                isPermissionGranted = true;
            } else {
                Log.w(LOG_TAG, "## onPermissionResultVideoIpCall(): No permissions granted to IP call (video or audio)");
                if (null != aContext)
                    CommonActivityUtils.displayToast(aContext, aContext.getString(R.string.permissions_action_not_performed_missing_permissions));
            }
        } catch (Exception ex) {
            Log.d(LOG_TAG, "## onPermissionResultVideoIpCall(): Exception MSg=" + ex.getMessage());
        }

        return isPermissionGranted;
    }

    //==============================================================================================================
    // Room preview methods.
    //==============================================================================================================

    /**
     * Start a room activity in preview mode.
     *
     * @param fromActivity    the caller activity.
     * @param roomPreviewData the room preview information
     */
    public static void previewRoom(final Activity fromActivity, RoomPreviewData roomPreviewData) {
        if ((null != fromActivity) && (null != roomPreviewData)) {
            VectorRoomActivity.sRoomPreviewData = roomPreviewData;
            Intent intent = new Intent(fromActivity, VectorRoomActivity.class);
            intent.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, roomPreviewData.getRoomId());
            intent.putExtra(VectorRoomActivity.EXTRA_ROOM_PREVIEW_ID, roomPreviewData.getRoomId());
            intent.putExtra(VectorRoomActivity.EXTRA_EXPAND_ROOM_HEADER, true);
            fromActivity.startActivity(intent);
        }
    }

    /**
     * Helper method used to build an intent to trigger a room preview.
     *
     * @param aMatrixId       matrix ID of the user
     * @param aRoomId         room ID
     * @param aContext        application context
     * @param aTargetActivity the activity set in the returned intent
     * @return a valid intent if operation succeed, null otherwise
     */
    public static Intent buildIntentPreviewRoom(String aMatrixId, String aRoomId, Context aContext, Class<?> aTargetActivity) {
        Intent intentRetCode;

        // sanity check
        if ((null == aContext) || (null == aRoomId) || (null == aMatrixId)) {
            intentRetCode = null;
        } else {
            MXSession session;

            // get the session
            if (null == (session = Matrix.getInstance(aContext).getSession(aMatrixId))) {
                session = Matrix.getInstance(aContext).getDefaultSession();
            }

            // check session validity
            if ((null == session) || !session.isAlive()) {
                intentRetCode = null;
            } else {
                String roomAlias = null;
                Room room = session.getDataHandler().getRoom(aRoomId);

                // get the room alias (if any) for the preview data
                if ((null != room) && (null != room.getLiveState())) {
                    roomAlias = room.getLiveState().getAlias();
                }

                intentRetCode = new Intent(aContext, aTargetActivity);
                // extra required by VectorRoomActivity
                intentRetCode.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, aRoomId);
                intentRetCode.putExtra(VectorRoomActivity.EXTRA_ROOM_PREVIEW_ID, aRoomId);
                intentRetCode.putExtra(VectorRoomActivity.EXTRA_MATRIX_ID, aMatrixId);
                intentRetCode.putExtra(VectorRoomActivity.EXTRA_EXPAND_ROOM_HEADER, true);
                // extra only required by VectorFakeRoomPreviewActivity
                intentRetCode.putExtra(VectorRoomActivity.EXTRA_ROOM_PREVIEW_ROOM_ALIAS, roomAlias);
            }
        }
        return intentRetCode;
    }

    /**
     * Start a room activity in preview mode.
     * If the room is already joined, open it in edition mode.
     *
     * @param fromActivity the caller activity.
     * @param session      the session
     * @param roomId       the roomId
     * @param roomAlias    the room alias
     * @param callback     the operation callback
     */
    public static void previewRoom(final Activity fromActivity, final MXSession session, final String roomId, final String roomAlias, final ApiCallback<Void> callback) {
        previewRoom(fromActivity, session, roomId, new RoomPreviewData(session, roomId, null, roomAlias, null), callback);
    }

    /**
     * Start a room activity in preview mode.
     * If the room is already joined, open it in edition mode.
     *
     * @param fromActivity    the caller activity.
     * @param session         the session
     * @param roomId          the roomId
     * @param roomPreviewData the room preview data
     * @param callback        the operation callback
     */
    public static void previewRoom(final Activity fromActivity, final MXSession session, final String roomId, final RoomPreviewData roomPreviewData, final ApiCallback<Void> callback) {
        Room room = session.getDataHandler().getRoom(roomId, false);

        // if the room exists
        if (null != room) {
            // either the user is invited
            if (room.isInvited()) {
                Log.d(LOG_TAG, "previewRoom : the user is invited -> display the preview " + VectorApp.getCurrentActivity());
                previewRoom(fromActivity, roomPreviewData);
            } else {
                Log.d(LOG_TAG, "previewRoom : open the room");
                HashMap<String, Object> params = new HashMap<>();
                params.put(VectorRoomActivity.EXTRA_MATRIX_ID, session.getMyUserId());
                params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
                CommonActivityUtils.goToRoomPage(fromActivity, session, params);
            }

            if (null != callback) {
                callback.onSuccess(null);
            }
        } else {
            roomPreviewData.fetchPreviewData(new ApiCallback<Void>() {
                private void onDone() {
                    if (null != callback) {
                        callback.onSuccess(null);
                    }
                    previewRoom(fromActivity, roomPreviewData);
                }

                @Override
                public void onSuccess(Void info) {
                    onDone();
                }

                @Override
                public void onNetworkError(Exception e) {
                    onDone();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onDone();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onDone();
                }
            });
        }


    }
    //==============================================================================================================
    // Room jump methods.
    //==============================================================================================================

    /**
     * Start a room activity with the dedicated parameters.
     * Pop the activity to the homeActivity before pushing the new activity.
     *
     * @param fromActivity the caller activity.
     * @param params       the room activity parameters
     */
    public static void goToRoomPage(final Activity fromActivity, final Map<String, Object> params) {
        goToRoomPage(fromActivity, null, params);
    }

    /**
     * Start a room activity with the dedicated parameters.
     * Pop the activity to the homeActivity before pushing the new activity.
     *
     * @param fromActivity the caller activity.
     * @param session      the session.
     * @param params       the room activity parameters.
     */
    public static void goToRoomPage(final Activity fromActivity, final MXSession session, final Map<String, Object> params) {
        final MXSession finalSession = (session == null) ? Matrix.getMXSession(fromActivity, (String) params.get(VectorRoomActivity.EXTRA_MATRIX_ID)) : session;

        // sanity check
        if ((null == finalSession) || !finalSession.isAlive()) {
            return;
        }

        String roomId = (String) params.get(VectorRoomActivity.EXTRA_ROOM_ID);

        Room room = finalSession.getDataHandler().getRoom(roomId);

        // do not open a leaving room.
        // it does not make.
        if ((null != room) && (room.isLeaving())) {
            return;
        }

        fromActivity.runOnUiThread(new Runnable() {
                                       @Override
                                       public void run() {
                                           // if the activity is not the home activity
                                           if (!(fromActivity instanceof VectorHomeActivity)) {
                                               // pop to the home activity
                                               Log.d(LOG_TAG, "## goToRoomPage(): start VectorHomeActivity..");
                                               Intent intent = new Intent(fromActivity, VectorHomeActivity.class);
                                               intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);

                                               intent.putExtra(VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS, (Serializable) params);
                                               fromActivity.startActivity(intent);
                                           } else {
                                               // already to the home activity
                                               // so just need to open the room activity
                                               Log.d(LOG_TAG, "## goToRoomPage(): already in VectorHomeActivity..");
                                               Intent intent = new Intent(fromActivity, VectorRoomActivity.class);

                                               for (String key : params.keySet()) {
                                                   Object value = params.get(key);

                                                   if (value instanceof String) {
                                                       intent.putExtra(key, (String) value);
                                                   } else if (value instanceof Boolean) {
                                                       intent.putExtra(key, (Boolean) value);
                                                   } else if (value instanceof Parcelable) {
                                                       intent.putExtra(key, (Parcelable) value);
                                                   }
                                               }

                                               // try to find a displayed room name
                                               if (null == params.get(VectorRoomActivity.EXTRA_DEFAULT_NAME)) {

                                                   Room room = finalSession.getDataHandler().getRoom((String) params.get(VectorRoomActivity.EXTRA_ROOM_ID));

                                                   if ((null != room) && room.isInvited()) {
                                                       String displayName = VectorUtils.getRoomDisplayName(fromActivity, finalSession, room);

                                                       if (null != displayName) {
                                                           intent.putExtra(VectorRoomActivity.EXTRA_DEFAULT_NAME, displayName);
                                                       }
                                                   }
                                               }

                                               fromActivity.startActivity(intent);
                                           }
                                       }
                                   }
        );
    }

    //==============================================================================================================
    // 1:1 Room  methods.
    //==============================================================================================================

    /**
     * Return all the 1:1 rooms joined by the searched user and by the current logged in user.
     * This method go through all the rooms, and for each room, tests if the searched user
     * and the logged in user are present.
     *
     * @param aSession        session
     * @param aSearchedUserId the searched user ID
     * @return an array containing the found rooms
     */
    private static ArrayList<Room> findOneToOneRoomList(final MXSession aSession, final String aSearchedUserId) {
        ArrayList<Room> listRetValue = new ArrayList<>();
        List<RoomMember> roomMembersList;
        String userId0, userId1;

        if ((null != aSession) && (null != aSearchedUserId)) {
            Collection<Room> roomsList = aSession.getDataHandler().getStore().getRooms();

            for (Room room : roomsList) {
                roomMembersList = (List<RoomMember>) room.getJoinedMembers();

                if ((null != roomMembersList) && (ROOM_SIZE_ONE_TO_ONE == roomMembersList.size())) {
                    userId0 = roomMembersList.get(0).getUserId();
                    userId1 = roomMembersList.get(1).getUserId();

                    // add the room where the second member is the searched one
                    if (userId0.equals(aSearchedUserId) || userId1.equals(aSearchedUserId)) {
                        listRetValue.add(room);
                    }
                }
            }
        }

        return listRetValue;
    }

    /**
     * Set a room as a direct chat room.<br>
     * In case of success the corresponding room is displayed.
     *
     * @param aSession           session
     * @param aRoomId            room ID
     * @param aParticipantUserId the direct chat invitee user ID
     * @param fromActivity       calling activity
     * @param callback           async response handler
     */
    public static void setToggleDirectMessageRoom(final MXSession aSession, final String aRoomId, String aParticipantUserId, final Activity fromActivity, final ApiCallback<Void> callback) {

        if ((null == aSession) || (null == fromActivity) || TextUtils.isEmpty(aRoomId)) {
            Log.d(LOG_TAG, "## setToggleDirectMessageRoom(): failure - invalid input parameters");
        } else {
            aSession.toggleDirectChatRoom(aRoomId, aParticipantUserId, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    callback.onSuccess(null);
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.d(LOG_TAG, "## setToggleDirectMessageRoom(): invite() onNetworkError Msg=" + e.getLocalizedMessage());
                    if (null != callback) {
                        callback.onNetworkError(e);
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.d(LOG_TAG, "## setToggleDirectMessageRoom(): invite() onMatrixError Msg=" + e.getLocalizedMessage());
                    if (null != callback) {
                        callback.onMatrixError(e);
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.d(LOG_TAG, "## setToggleDirectMessageRoom(): invite() onUnexpectedError Msg=" + e.getLocalizedMessage());
                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }
                }
            });
        }
    }

    /**
     * Offer to send some dedicated intent data to an existing room
     *
     * @param fromActivity the caller activity
     * @param intent       the intent param
     */
    public static void sendFilesTo(final Activity fromActivity, final Intent intent) {
        if (Matrix.getMXSessions(fromActivity).size() == 1) {
            sendFilesTo(fromActivity, intent, Matrix.getMXSession(fromActivity, null));
        } else if (fromActivity instanceof FragmentActivity) {
            // TBD
        }
    }

    /**
     * Offer to send some dedicated intent data to an existing room
     *
     * @param fromActivity the caller activity
     * @param intent       the intent param
     * @param session      the session/
     */
    private static void sendFilesTo(final Activity fromActivity, final Intent intent, final MXSession session) {
        // sanity check
        if ((null == session) || !session.isAlive() || fromActivity.isFinishing()) {
            return;
        }

        ArrayList<RoomSummary> mergedSummaries = new ArrayList<>(session.getDataHandler().getStore().getSummaries());

        // keep only the joined room
        for (int index = 0; index < mergedSummaries.size(); index++) {
            RoomSummary summary = mergedSummaries.get(index);
            Room room = session.getDataHandler().getRoom(summary.getRoomId());

            if ((null == room) || room.isInvited() || room.isConferenceUserRoom()) {
                mergedSummaries.remove(index);
                index--;
            }
        }

        Collections.sort(mergedSummaries, new Comparator<RoomSummary>() {
            @Override
            public int compare(RoomSummary lhs, RoomSummary rhs) {
                if (lhs == null || lhs.getLatestReceivedEvent() == null) {
                    return 1;
                } else if (rhs == null || rhs.getLatestReceivedEvent() == null) {
                    return -1;
                }

                if (lhs.getLatestReceivedEvent().getOriginServerTs() > rhs.getLatestReceivedEvent().getOriginServerTs()) {
                    return -1;
                } else if (lhs.getLatestReceivedEvent().getOriginServerTs() < rhs.getLatestReceivedEvent().getOriginServerTs()) {
                    return 1;
                }
                return 0;
            }
        });

        AlertDialog.Builder builderSingle = new AlertDialog.Builder(fromActivity);
        builderSingle.setTitle(fromActivity.getText(R.string.send_files_in));

        VectorRoomsSelectionAdapter adapter = new VectorRoomsSelectionAdapter(fromActivity, R.layout.adapter_item_vector_recent_room, session);
        adapter.addAll(mergedSummaries);

        builderSingle.setNegativeButton(fromActivity.getText(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        final ArrayList<RoomSummary> fMergedSummaries = mergedSummaries;

        builderSingle.setAdapter(adapter,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, final int which) {
                        dialog.dismiss();
                        fromActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                RoomSummary summary = fMergedSummaries.get(which);

                                HashMap<String, Object> params = new HashMap<>();
                                params.put(VectorRoomActivity.EXTRA_MATRIX_ID, session.getMyUserId());
                                params.put(VectorRoomActivity.EXTRA_ROOM_ID, summary.getRoomId());
                                params.put(VectorRoomActivity.EXTRA_ROOM_INTENT, intent);

                                CommonActivityUtils.goToRoomPage(fromActivity, session, params);
                            }
                        });
                    }
                });
        builderSingle.show();
    }

    //==============================================================================================================
    // Parameters checkers.
    //==============================================================================================================


    //==============================================================================================================
    // Media utils
    //==============================================================================================================

    /**
     * Save a media in the downloads directory and offer to open it with a third party application.
     *
     * @param activity       the activity
     * @param savedMediaPath the media path
     * @param mimeType       the media mime type.
     */
    public static void openMedia(final Activity activity, final String savedMediaPath, final String mimeType) {
        if ((null != activity) && (null != savedMediaPath)) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File file = new File(savedMediaPath);
                        Intent intent = new Intent();
                        intent.setAction(android.content.Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.fromFile(file), mimeType);
                        activity.startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(activity, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.d(LOG_TAG, "## openMedia(): Exception Msg=" + e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * Copy a file into a dstPath directory.
     * The output filename can be provided.
     * The output file is not overridden if it is already exist.
     *
     * @param sourceFile     the file source path
     * @param dstDirPath     the dst path
     * @param outputFilename optional the output filename
     * @param callback       the asynchronous callback
     */
    private static void saveFileInto(final File sourceFile, final String dstDirPath, final String outputFilename, final ApiCallback<String> callback) {
        // sanity check
        if ((null == sourceFile) || (null == dstDirPath)) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (null != callback) {
                        callback.onNetworkError(new Exception("Null parameters"));
                    }
                }
            });
            return;
        }

        AsyncTask<Void, Void, Pair<String, Exception>> task = new AsyncTask<Void, Void, Pair<String, Exception>>() {
            @Override
            protected Pair<String, Exception> doInBackground(Void... params) {
                Pair<String, Exception> result;

                // defines another name for the external media
                String dstFileName;

                // build a filename is not provided
                if (null == outputFilename) {
                    // extract the file extension from the uri
                    int dotPos = sourceFile.getName().lastIndexOf(".");

                    String fileExt = "";
                    if (dotPos > 0) {
                        fileExt = sourceFile.getName().substring(dotPos);
                    }

                    dstFileName = "vector_" + System.currentTimeMillis() + fileExt;
                } else {
                    dstFileName = outputFilename;
                }

                File dstDir = Environment.getExternalStoragePublicDirectory(dstDirPath);
                if (dstDir != null) {
                    dstDir.mkdirs();
                }

                File dstFile = new File(dstDir, dstFileName);

                // if the file already exists, append a marker
                if (dstFile.exists()) {
                    String baseFileName = dstFileName;
                    String fileExt = "";

                    int lastDotPos = dstFileName.lastIndexOf(".");

                    if (lastDotPos > 0) {
                        baseFileName = dstFileName.substring(0, lastDotPos);
                        fileExt = dstFileName.substring(lastDotPos);
                    }

                    int counter = 1;

                    while (dstFile.exists()) {
                        dstFile = new File(dstDir, baseFileName + "(" + counter + ")" + fileExt);
                        counter++;
                    }
                }

                // Copy source file to destination
                FileInputStream inputStream = null;
                FileOutputStream outputStream = null;
                try {
                    dstFile.createNewFile();

                    inputStream = new FileInputStream(sourceFile);
                    outputStream = new FileOutputStream(dstFile);

                    byte[] buffer = new byte[1024 * 10];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, len);
                    }
                    result = new Pair<>(dstFile.getAbsolutePath(), null);
                } catch (Exception e) {
                    result = new Pair<>(null, e);
                } finally {
                    // Close resources
                    try {
                        if (inputStream != null) inputStream.close();
                        if (outputStream != null) outputStream.close();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## saveFileInto(): Exception Msg=" + e.getMessage());
                        result = new Pair<>(null, e);
                    }
                }

                return result;
            }

            @Override
            protected void onPostExecute(Pair<String, Exception> result) {
                if (null != callback) {
                    if (null == result) {
                        callback.onNetworkError(new Exception("Null parameters"));
                    } else if (null != result.first) {
                        callback.onSuccess(result.first);
                    } else {
                        callback.onNetworkError(result.second);
                    }
                }
            }
        };

        try {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } catch (final Exception e) {
            Log.e(LOG_TAG, "## saveFileInto() failed " + e.getMessage());
            task.cancel(true);

            (new android.os.Handler(Looper.getMainLooper())).post(new Runnable() {
                @Override
                public void run() {
                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }
                }
            });
        }
    }

    /**
     * Save a media URI into the download directory
     *
     * @param context  the context
     * @param srcFile  the source file.
     * @param filename the filename (optional)
     * @param callback the asynchronous callback
     */
    @SuppressLint("NewApi")
    public static void saveMediaIntoDownloads(final Context context, final File srcFile, final String filename, final String mimeType, final SimpleApiCallback<String> callback) {
        saveFileInto(srcFile, Environment.DIRECTORY_DOWNLOADS, filename, new ApiCallback<String>() {
            @Override
            public void onSuccess(String fullFilePath) {
                if (null != fullFilePath) {
                    DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

                    try {
                        File file = new File(fullFilePath);
                        downloadManager.addCompletedDownload(file.getName(), file.getName(), true, mimeType, file.getAbsolutePath(), file.length(), true);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## saveMediaIntoDownloads(): Exception Msg=" + e.getMessage());
                    }
                }

                if (null != callback) {
                    callback.onSuccess(fullFilePath);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    //==============================================================================================================
    // toast utils
    //==============================================================================================================

    /**
     * Helper method to display a toast message.
     *
     * @param aCallingActivity calling Activity instance
     * @param aMsgToDisplay    message to display
     */
    public static void displayToastOnUiThread(final Activity aCallingActivity, final String aMsgToDisplay) {
        if (null != aCallingActivity) {
            aCallingActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    CommonActivityUtils.displayToast(aCallingActivity.getApplicationContext(), aMsgToDisplay);
                }
            });
        }
    }

    /**
     * Display a toast
     *
     * @param aContext       the context.
     * @param aTextToDisplay the text to display.
     */
    public static void displayToast(Context aContext, CharSequence aTextToDisplay) {
        Toast.makeText(aContext, aTextToDisplay, Toast.LENGTH_SHORT).show();
    }

    //==============================================================================================================
    // room utils
    //==============================================================================================================

    /**
     * Helper method to retrieve the max power level contained in the room.
     * This value is used to indicate what is the power level value required
     * to be admin of the room.
     *
     * @return max power level of the current room
     */
    public static int getRoomMaxPowerLevel(Room aRoom) {
        int maxPowerLevel = 0;

        if (null != aRoom) {
            PowerLevels powerLevels = aRoom.getLiveState().getPowerLevels();

            if (null != powerLevels) {
                int tempPowerLevel;

                // find out the room member
                Collection<RoomMember> members = aRoom.getMembers();
                for (RoomMember member : members) {
                    tempPowerLevel = powerLevels.getUserPowerLevel(member.getUserId());
                    if (tempPowerLevel > maxPowerLevel) {
                        maxPowerLevel = tempPowerLevel;
                    }
                }
            }
        }
        return maxPowerLevel;
    }

    //==============================================================================================================
    // Application badge (displayed in the launcher)
    //==============================================================================================================

    private static int mBadgeValue = 0;

    /**
     * Update the application badge value.
     *
     * @param context    the context
     * @param badgeValue the new badge value
     */
    public static void updateBadgeCount(Context context, int badgeValue) {
        try {
            mBadgeValue = badgeValue;
            ShortcutBadger.setBadge(context, badgeValue);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## updateBadgeCount(): Exception Msg=" + e.getMessage());
        }
    }

    /**
     * Refresh the badge count for specific configurations.<br>
     * The refresh is only effective if the device is:
     * <ul><li>offline</li><li>does not support GCM</li>
     * <li>GCM registration failed</li>
     * <br>Notifications rooms are parsed to track the notification count value.
     *
     * @param aSession session value
     * @param aContext App context
     */
    public static void specificUpdateBadgeUnreadCount(MXSession aSession, Context aContext) {
        MXDataHandler dataHandler;

        // sanity check
        if ((null == aContext) || (null == aSession)) {
            Log.w(LOG_TAG, "## specificUpdateBadgeUnreadCount(): invalid input null values");
        } else if ((null == (dataHandler = aSession.getDataHandler()))) {
            Log.w(LOG_TAG, "## specificUpdateBadgeUnreadCount(): invalid DataHandler instance");
        } else {
            if (aSession.isAlive()) {
                boolean isRefreshRequired;
                GcmRegistrationManager gcmMgr = Matrix.getInstance(aContext).getSharedGCMRegistrationManager();

                // update the badge count if the device is offline, GCM is not supported or GCM registration failed
                isRefreshRequired = !Matrix.getInstance(aContext).isConnected();
                isRefreshRequired |= (null != gcmMgr) && (!gcmMgr.useGCM() || !gcmMgr.hasRegistrationToken());

                if (isRefreshRequired) {
                    updateBadgeCount(aContext, dataHandler);
                }
            }
        }
    }

    /**
     * Update the badge count value according to the rooms content.
     *
     * @param aContext     App context
     * @param aDataHandler data handler instance
     */
    private static void updateBadgeCount(Context aContext, MXDataHandler aDataHandler) {
        //sanity check
        if ((null == aContext) || (null == aDataHandler)) {
            Log.w(LOG_TAG, "## updateBadgeCount(): invalid input null values");
        } else if (null == aDataHandler.getStore()) {
            Log.w(LOG_TAG, "## updateBadgeCount(): invalid store instance");
        } else {
            ArrayList<Room> roomCompleteList = new ArrayList<>(aDataHandler.getStore().getRooms());
            int unreadRoomsCount = 0;

            for (Room room : roomCompleteList) {
                if (room.getNotificationCount() > 0) {
                    unreadRoomsCount++;
                }
            }

            // update the badge counter
            Log.d(LOG_TAG, "## updateBadgeCount(): badge update count=" + unreadRoomsCount);
            CommonActivityUtils.updateBadgeCount(aContext, unreadRoomsCount);
        }
    }

    //==============================================================================================================
    // Low memory management
    //==============================================================================================================

    private static final String LOW_MEMORY_LOG_TAG = "Memory usage";

    /**
     * Log the memory statuses.
     *
     * @param activity the calling activity
     * @return if the device is running on low memory.
     */
    public static boolean displayMemoryInformation(Activity activity, String title) {
        long freeSize = 0L;
        long totalSize = 0L;
        long usedSize = -1L;
        try {
            Runtime info = Runtime.getRuntime();
            freeSize = info.freeMemory();
            totalSize = info.totalMemory();
            usedSize = totalSize - freeSize;
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.e(LOW_MEMORY_LOG_TAG, "---------------------------------------------------");
        Log.e(LOW_MEMORY_LOG_TAG, "----------- " + title + " -----------------");
        Log.e(LOW_MEMORY_LOG_TAG, "---------------------------------------------------");
        Log.e(LOW_MEMORY_LOG_TAG, "usedSize   " + (usedSize / 1048576L) + " MB");
        Log.e(LOW_MEMORY_LOG_TAG, "freeSize   " + (freeSize / 1048576L) + " MB");
        Log.e(LOW_MEMORY_LOG_TAG, "totalSize  " + (totalSize / 1048576L) + " MB");
        Log.e(LOW_MEMORY_LOG_TAG, "---------------------------------------------------");


        if (null != activity) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ActivityManager activityManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            activityManager.getMemoryInfo(mi);

            Log.e(LOW_MEMORY_LOG_TAG, "availMem   " + (mi.availMem / 1048576L) + " MB");
            Log.e(LOW_MEMORY_LOG_TAG, "totalMem   " + (mi.totalMem / 1048576L) + " MB");
            Log.e(LOW_MEMORY_LOG_TAG, "threshold  " + (mi.threshold / 1048576L) + " MB");
            Log.e(LOW_MEMORY_LOG_TAG, "lowMemory  " + (mi.lowMemory));
            Log.e(LOW_MEMORY_LOG_TAG, "---------------------------------------------------");
            return mi.lowMemory;
        } else {
            return false;
        }
    }

    /**
     * Manage the low memory case
     *
     * @param activity activity instance
     */
    public static void onLowMemory(Activity activity) {
        if (!VectorApp.isAppInBackground()) {
            String activityName = (null != activity) ? activity.getClass().getSimpleName() : "NotAvailable";
            Log.e(LOW_MEMORY_LOG_TAG, "Active application : onLowMemory from " + activityName);

            // it seems that onLowMemory is called whereas the device is seen on low memory condition
            // so, test if the both conditions
            if (displayMemoryInformation(activity, "onLowMemory test")) {
                if (CommonActivityUtils.shouldRestartApp(activity)) {
                    Log.e(LOW_MEMORY_LOG_TAG, "restart");
                    CommonActivityUtils.restartApp(activity);
                } else {
                    Log.e(LOW_MEMORY_LOG_TAG, "clear the application cache");
                    Matrix.getInstance(activity).reloadSessions(activity);
                }
            } else {
                Log.e(LOW_MEMORY_LOG_TAG, "Wait to be concerned");
            }
        } else {
            Log.e(LOW_MEMORY_LOG_TAG, "background application : onLowMemory ");
        }

        displayMemoryInformation(activity, "onLowMemory global");
    }

    /**
     * Manage the trim memory.
     *
     * @param activity the activity.
     * @param level    the memory level
     */
    public static void onTrimMemory(Activity activity, int level) {
        String activityName = (null != activity) ? activity.getClass().getSimpleName() : "NotAvailable";
        Log.e(LOW_MEMORY_LOG_TAG, "Active application : onTrimMemory from " + activityName + " level=" + level);
        // TODO implement things to reduce memory usage

        displayMemoryInformation(activity, "onTrimMemory");
    }

    //==============================================================================================================
    // e2e devices management
    //==============================================================================================================

    /**
     * Display the device verification warning
     *
     * @param deviceInfo the device info
     */
    static public <T> void displayDeviceVerificationDialog(final MXDeviceInfo deviceInfo, final String sender, final MXSession session, Activity activiy, final ApiCallback<Void> callback) {

        // sanity check
        if ((null == deviceInfo) || (null == sender) || (null == session)) {
            Log.e(LOG_TAG, "## displayDeviceVerificationDialog(): invalid imput parameters");
            return;
        }

        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(activiy);
        LayoutInflater inflater = activiy.getLayoutInflater();

        View layout = inflater.inflate(R.layout.encrypted_verify_device, null);

        TextView textView;

        textView = layout.findViewById(R.id.encrypted_device_info_device_name);
        textView.setText(deviceInfo.displayName());

        textView = layout.findViewById(R.id.encrypted_device_info_device_id);
        textView.setText(deviceInfo.deviceId);

        textView = layout.findViewById(R.id.encrypted_device_info_device_key);
        textView.setText(deviceInfo.fingerprint());

        builder.setView(layout);
        builder.setTitle(R.string.encryption_information_verify_device);

        builder.setPositiveButton(R.string.encryption_information_verify_key_match, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                session.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED, deviceInfo.deviceId, sender, callback);
            }
        });

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (null != callback) {
                    callback.onSuccess(null);
                }
            }
        });

        builder.create().show();
    }

    /**
     * Export the e2e keys for a dedicated session.
     *
     * @param session  the session
     * @param password the password
     * @param callback the asynchronous callback.
     */
    public static void exportKeys(final MXSession session, final String password, final ApiCallback<String> callback) {
        final Context appContext = VectorApp.getInstance();

        if (null == session.getCrypto()) {
            if (null != callback) {
                callback.onMatrixError(new MatrixError("EMPTY", "No crypto"));
            }

            return;
        }

        session.getCrypto().exportRoomKeys(password, new ApiCallback<byte[]>() {
            @Override
            public void onSuccess(byte[] bytesArray) {
                try {
                    ByteArrayInputStream stream = new ByteArrayInputStream(bytesArray);
                    String url = session.getMediasCache().saveMedia(stream, "riot-" + System.currentTimeMillis() + ".txt", "text/plain");
                    stream.close();

                    CommonActivityUtils.saveMediaIntoDownloads(appContext, new File(Uri.parse(url).getPath()), "riot-keys.txt", "text/plain", new SimpleApiCallback<String>() {
                        @Override
                        public void onSuccess(String path) {
                            if (null != callback) {
                                callback.onSuccess(path);
                            }
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            if (null != callback) {
                                callback.onNetworkError(e);
                            }
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            if (null != callback) {
                                callback.onMatrixError(e);
                            }
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            if (null != callback) {
                                callback.onUnexpectedError(e);
                            }
                        }
                    });
                } catch (Exception e) {
                    if (null != callback) {
                        callback.onMatrixError(new MatrixError(null, e.getLocalizedMessage()));
                    }
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    private static final String TAG_FRAGMENT_UNKNOWN_DEVICES_DIALOG_DIALOG = "ActionBarActivity.TAG_FRAGMENT_UNKNOWN_DEVICES_DIALOG_DIALOG";

    /**
     * Display the unknown e2e devices
     *
     * @param session        the session
     * @param activity       the calling activity
     * @param unknownDevices the unknown devices list
     * @param listener       optional listener to add an optional "Send anyway" button
     */
    public static void displayUnknownDevicesDialog(MXSession session, FragmentActivity activity, MXUsersDevicesMap<MXDeviceInfo> unknownDevices, VectorUnknownDevicesFragment.IUnknownDevicesSendAnywayListener listener) {
        // sanity checks
        if (activity.isFinishing() || (null == unknownDevices) || (0 == unknownDevices.getMap().size())) {
            return;
        }

        FragmentManager fm = activity.getSupportFragmentManager();

        VectorUnknownDevicesFragment fragment = (VectorUnknownDevicesFragment) fm.findFragmentByTag(TAG_FRAGMENT_UNKNOWN_DEVICES_DIALOG_DIALOG);
        if (fragment != null) {
            fragment.dismissAllowingStateLoss();
        }

        fragment = VectorUnknownDevicesFragment.newInstance(session.getMyUserId(), unknownDevices, listener);
        try {
            fragment.show(fm, TAG_FRAGMENT_UNKNOWN_DEVICES_DIALOG_DIALOG);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## displayUnknownDevicesDialog() failed : " + e.getMessage());
        }
    }

    /**
     * Update the menu icons colors
     *
     * @param menu  the menu
     * @param color the color
     */
    public static void tintMenuIcons(Menu menu, int color) {
        for (int i = 0; i < menu.size(); ++i) {
            MenuItem item = menu.getItem(i);
            Drawable drawable = item.getIcon();
            if (drawable != null) {
                Drawable wrapped = DrawableCompat.wrap(drawable);
                drawable.mutate();
                DrawableCompat.setTint(wrapped, color);
                item.setIcon(drawable);
            }
        }
    }

    /**
     * Tint the drawable with a theme attribute
     *
     * @param context   the context
     * @param drawable  the drawable to tint
     * @param attribute the theme color
     * @return the tinted drawable
     */
    public static Drawable tintDrawable(Context context, Drawable drawable, @AttrRes int attribute) {
        return tintDrawableWithColor(drawable, ThemeUtils.getColor(context, attribute));
    }

    /**
     * Tint the drawable with a color integer
     *
     * @param drawable the drawable to tint
     * @param color    the color
     * @return the tinted drawable
     */
    public static Drawable tintDrawableWithColor(Drawable drawable, @ColorInt int color) {
        Drawable tinted = DrawableCompat.wrap(drawable);
        drawable.mutate();
        DrawableCompat.setTint(tinted, color);
        return tinted;
    }
}
