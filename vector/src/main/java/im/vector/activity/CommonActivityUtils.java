/*
 * Copyright 2015 OpenMarket Ltd
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
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import im.vector.VectorApp;
import im.vector.Matrix;
import im.vector.MyPresenceManager;
import im.vector.R;
import im.vector.adapters.VectorRoomsSelectionAdapter;
import im.vector.contacts.ContactsManager;
import im.vector.contacts.PIDsRetriever;
import im.vector.fragments.AccountsSelectionDialogFragment;
import im.vector.ga.GAHelper;
import im.vector.receiver.VectorUniversalLinkReceiver;
import im.vector.services.EventStreamService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import im.vector.util.VectorUtils;
import me.leolin.shortcutbadger.ShortcutBadger;

/**
 * Contains useful functions which are called in multiple activities.
 */
public class CommonActivityUtils {
    private static final String LOG_TAG = "CommonActivityUtils";

    /**
     * Mime types
     **/
    public static final String MIME_TYPE_IMAGE_ALL = "image/*";
    public static final String MIME_TYPE_ALL_CONTENT = "*/*";

    /**
     * Schemes
     */
    public static final String HTTP_SCHEME = "http://";
    public static final String HTTPS_SCHEME = "https://";

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
    public static final Boolean GROUP_IS_EXPANDED = true;
    public static final Boolean GROUP_IS_COLLAPSED = false;

    // power levels
    public static final float UTILS_POWER_LEVEL_ADMIN = 100;
    public static final float UTILS_POWER_LEVEL_MODERATOR = 50;
    public static final int ROOM_SIZE_ONE_TO_ONE = 2;

    // Android M permission request code management
    public static final boolean PERMISSIONS_GRANTED = true;
    public static final boolean PERMISSIONS_DENIED = !PERMISSIONS_GRANTED;
    public static final int PERMISSION_CAMERA = 0x1;
    public static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 0x1<<1;
    public static final int PERMISSION_RECORD_AUDIO = 0x1<<2;
    public static final int PERMISSION_READ_CONTACTS = 0x1<<3;
    public static final int REQUEST_CODE_PERMISSION_AUDIO_IP_CALL = PERMISSION_RECORD_AUDIO;
    public static final int REQUEST_CODE_PERMISSION_VIDEO_IP_CALL = PERMISSION_CAMERA | PERMISSION_RECORD_AUDIO;
    public static final int REQUEST_CODE_PERMISSION_TAKE_PHOTO = PERMISSION_CAMERA | PERMISSION_WRITE_EXTERNAL_STORAGE;
    public static final int REQUEST_CODE_PERMISSION_SEARCH_ROOM = PERMISSION_READ_CONTACTS;
    public static final int REQUEST_CODE_PERMISSION_MEMBER_DETAILS = PERMISSION_READ_CONTACTS;
    public static final int REQUEST_CODE_PERMISSION_HOME_ACTIVITY = PERMISSION_WRITE_EXTERNAL_STORAGE;

    // start activity intent parameters
    public static final String KEY_PERMISSIONS_READ_CONTACTS = "KEY_PERMISSIONS_READ_CONTACTS";

    public static void logout(Activity activity, MXSession session, Boolean clearCredentials) {
        if (session.isAlive()) {
            // stop the service
            EventStreamService eventStreamService = EventStreamService.getInstance();
            ArrayList<String> matrixIds = new ArrayList<String>();
            matrixIds.add(session.getMyUserId());
            eventStreamService.stopAccounts(matrixIds);

            // Publish to the server that we're now offline
            MyPresenceManager.getInstance(activity, session).advertiseOffline();
            MyPresenceManager.remove(session);

            // clear notification
            EventStreamService.removeNotification();

            // unregister from the GCM.
            Matrix.getInstance(activity).getSharedGcmRegistrationManager().unregister(session, null);

            // clear credentials
            Matrix.getInstance(activity).clearSession(activity, session, clearCredentials);
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
     * @param activity the caller activity
     * @param sessionId the session id
     * @param roomId the room id
     * @return true if go to splash screen
     */
    public static boolean isGoingToSplash(Activity activity, String sessionId, String roomId) {
        if (Matrix.hasValidSessions()) {
            List<MXSession> sessions = Matrix.getInstance(activity).getSessions();

            for(MXSession session : sessions) {
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

    public static final String RESTART_IN_PROGRESS_KEY = "RESTART_IN_PROGRESS_KEY";

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
     * @param activity the caller activity
     * @param goToLoginPage true to jump to the login page
     */
    public static void logout(Activity activity, boolean goToLoginPage) {
        EventStreamService.removeNotification();
        stopEventStream(activity);

        try {
            ShortcutBadger.setBadge(activity, 0);
        } catch (Exception e) {
            Log.d(LOG_TAG,"## logout(): Exception Msg="+e.getMessage());
        }

        // warn that the user logs out
        Collection<MXSession> sessions = Matrix.getMXSessions(activity);
        for (MXSession session : sessions) {
            // Publish to the server that we're now offline
            MyPresenceManager.getInstance(activity, session).advertiseOffline();
            MyPresenceManager.remove(session);
        }

        // clear the preferences
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        String loginVal = preferences.getString(LoginActivity.LOGIN_PREF, "");
        String passwordVal = preferences.getString(LoginActivity.PASSWORD_PREF, "");

        String homeServer = preferences.getString(LoginActivity.HOME_SERVER_URL_PREF, activity.getResources().getString(R.string.default_hs_server_url));
        String identityServer = preferences.getString(LoginActivity.IDENTITY_SERVER_URL_PREF, activity.getResources().getString(R.string.default_identity_server_url));
        Boolean useGa = GAHelper.useGA(activity);

        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.putString(LoginActivity.PASSWORD_PREF, passwordVal);
        editor.putString(LoginActivity.LOGIN_PREF, loginVal);
        editor.putString(LoginActivity.HOME_SERVER_URL_PREF, homeServer);
        editor.putString(LoginActivity.IDENTITY_SERVER_URL_PREF, identityServer);
        editor.commit();

        if (null != useGa) {
            GAHelper.setUseGA(activity, useGa);
        }

        // reset the GCM
        Matrix.getInstance(activity).getSharedGcmRegistrationManager().reset();

        // clear credentials
        Matrix.getInstance(activity).clearSessions(activity, true);

        // ensure that corrupted values are cleared
        Matrix.getInstance(activity).getLoginStorage().clear();

        // clear the tmp store list
        Matrix.getInstance(activity).clearTmpStoresList();

        // reset the contacts
        PIDsRetriever.getIntance().reset();
        ContactsManager.reset();

        MXMediasCache.clearThumbnailsCache(activity);

        if (goToLoginPage) {
            // go to login page
            activity.startActivity(new Intent(activity, LoginActivity.class));
            activity.finish();
        }
    }

    /**
     * Remove the http schemes from the URl passed in parameter
     * @param aUrl URL to be parsed
     * @return the URL with the scheme removed
     */
    public static String removeUrlScheme(String aUrl){
        String urlRetValue = aUrl;

        if(null != aUrl) {
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
     * @param aContext App context
     * @return true if no user is logged in, false otherwise
     */
    private static boolean isUserLogout(Context aContext){
        boolean retCode = false;

        if(null == aContext) {
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
     * @param context the context.
     * @param action the action to send.
     */
    private static void sendEventStreamAction(Context context, EventStreamService.StreamAction action) {
        Context appContext = context.getApplicationContext();

        Log.d(LOG_TAG, "sendEventStreamAction " + action);

        if(!isUserLogout(appContext)) {
            // Fix https://github.com/vector-im/vector-android/issues/230
            // Only start the service if a session is in progress, otherwise
            // starting the service is useless
            Intent killStreamService = new Intent(appContext, EventStreamService.class);
            killStreamService.putExtra(EventStreamService.EXTRA_STREAM_ACTION, action.ordinal());
            appContext.startService(killStreamService);
        } else {
            Log.d(LOG_TAG,"## sendEventStreamAction(): \""+action+"\" action not sent - user logged out");
        }
    }

    /**
     * Stop the event stream.
     * @param context the context.
     */
    public static void stopEventStream(Context context) {
        Log.d(LOG_TAG, "stopEventStream");
        sendEventStreamAction(context, EventStreamService.StreamAction.STOP);
    }

    /**
     * Pause the event stream.
     * @param context the context.
     */
    public static void pauseEventStream(Context context) {
        Log.d(LOG_TAG, "pauseEventStream");
        sendEventStreamAction(context, EventStreamService.StreamAction.PAUSE);
    }

    /**
     * Resume the events stream
     * @param context the context.
     */
    public static void resumeEventStream(Context context) {
        Log.d(LOG_TAG, "resumeEventStream");
        sendEventStreamAction(context, EventStreamService.StreamAction.RESUME);
    }

    /**
     * Trigger a event stream catchup i.e. there is only sync/ call.
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
     * @param context the context.
     */
    public static void onGcmUpdate(Context context) {
        Log.d(LOG_TAG, "onGcmUpdate");
        sendEventStreamAction(context, EventStreamService.StreamAction.GCM_STATUS_UPDATE);
    }

    /**
     * Start the events stream service.
     * @param context the context.
     */
    public static void startEventStreamService(Context context) {
        // the events stream service is launched
        // either the application has never be launched
        // or the service has been killed on low memory
        if (EventStreamService.getInstance() == null) {
            ArrayList<String> matrixIds = new ArrayList<String>();
            Collection<MXSession> sessions = Matrix.getInstance(context.getApplicationContext()).getSessions();

            if ((null != sessions) && (sessions.size() > 0)) {
                Log.d(LOG_TAG, "restart EventStreamService");

                for (MXSession session : sessions) {
                    boolean isSessionReady = session.getDataHandler().getStore().isReady();

                    if (!isSessionReady) {
                        session.getDataHandler().getStore().open();
                    }

                    // session to activate
                    matrixIds.add(session.getCredentials().userId);
                }

                Intent intent = new Intent(context, EventStreamService.class);
                intent.putExtra(EventStreamService.EXTRA_MATRIX_IDS, matrixIds.toArray(new String[matrixIds.size()]));
                intent.putExtra(EventStreamService.EXTRA_STREAM_ACTION, EventStreamService.StreamAction.START.ordinal());
                context.startService(intent);
            }
        }
    }

    /**
     * Check if the permissions provided in the list are granted.
     * This is an asynchronous method if permissions are requested, the final response
     * is provided in onRequestPermissionsResult(). In this case checkPermissions()
     * returns false.
     * <br>If checkPermissions() returns true, the permissions were already granted.
     * The permissions to be granted are given as bit map in aPermissionsToBeGrantedBitMap (ex: {@link #REQUEST_CODE_PERMISSION_TAKE_PHOTO}).
     * <br>aPermissionsToBeGrantedBitMap is passed as the request code in onRequestPermissionsResult().
     *
     * If a permission was already denied by the user, a popup is displayed to
     * explain why vector needs the corresponding permission.
     * @param aPermissionsToBeGrantedBitMap the permissions bit map to be granted
     * @param aCallingActivity the calling Activity that is requesting the permissions
     * @return true if the permissions are granted (synchronous flow), false otherwise (asynchronous flow)
     */
    public static boolean checkPermissions(final int aPermissionsToBeGrantedBitMap, final Activity aCallingActivity) {
        boolean isPermissionGranted = false;

        // sanity check
        if(null == aCallingActivity){
            Log.w(LOG_TAG, "## checkPermissions(): invalid input data");
            isPermissionGranted = false;
        } else if((REQUEST_CODE_PERMISSION_TAKE_PHOTO!=aPermissionsToBeGrantedBitMap)
                && (REQUEST_CODE_PERMISSION_AUDIO_IP_CALL!=aPermissionsToBeGrantedBitMap)
                && (REQUEST_CODE_PERMISSION_VIDEO_IP_CALL!=aPermissionsToBeGrantedBitMap)
                && (REQUEST_CODE_PERMISSION_SEARCH_ROOM !=aPermissionsToBeGrantedBitMap)
                && (REQUEST_CODE_PERMISSION_HOME_ACTIVITY !=aPermissionsToBeGrantedBitMap)
                && (REQUEST_CODE_PERMISSION_MEMBER_DETAILS !=aPermissionsToBeGrantedBitMap)
                ) {
            Log.w(LOG_TAG, "## checkPermissions(): permissions to be granted are not supported");
            isPermissionGranted = false;
        } else {
            List<String> permissionListAlreadyDenied = new ArrayList<String>();
            List<String> permissionsListToBeGranted = new ArrayList<String>();
            final List<String> finalPermissionsListToBeGranted;
            boolean isRequestPermissionRequired = false;
            Resources resource = aCallingActivity.getResources();
            String explanationMessage;
            String permissionType;

            // retrieve the permissions to be granted according to the request code bit map
            if(PERMISSION_CAMERA == (aPermissionsToBeGrantedBitMap & PERMISSION_CAMERA)){
                permissionType = Manifest.permission.CAMERA;
                isRequestPermissionRequired = updatePermissionsToBeGranted(aCallingActivity, permissionListAlreadyDenied, permissionsListToBeGranted, permissionType);
            }

            if(PERMISSION_RECORD_AUDIO == (aPermissionsToBeGrantedBitMap & PERMISSION_RECORD_AUDIO)){
                permissionType = Manifest.permission.RECORD_AUDIO;
                isRequestPermissionRequired = updatePermissionsToBeGranted(aCallingActivity, permissionListAlreadyDenied, permissionsListToBeGranted, permissionType);
            }

            if(PERMISSION_WRITE_EXTERNAL_STORAGE == (aPermissionsToBeGrantedBitMap & PERMISSION_WRITE_EXTERNAL_STORAGE)){
                permissionType = Manifest.permission.WRITE_EXTERNAL_STORAGE;
                isRequestPermissionRequired = updatePermissionsToBeGranted(aCallingActivity, permissionListAlreadyDenied, permissionsListToBeGranted, permissionType);
            }

            if(PERMISSION_READ_CONTACTS == (aPermissionsToBeGrantedBitMap & PERMISSION_READ_CONTACTS)){
                permissionType = Manifest.permission.READ_CONTACTS;
                isRequestPermissionRequired = updatePermissionsToBeGranted(aCallingActivity, permissionListAlreadyDenied, permissionsListToBeGranted, permissionType);
            }

            finalPermissionsListToBeGranted = permissionsListToBeGranted;

            // if some permissions were already denied: display a dialog to the user before asking again..
            if(!permissionListAlreadyDenied.isEmpty()) {
                if(null != resource) {
                    explanationMessage = resource.getString(R.string.permissions_rationale_msg_title);

                    // add the user info text to be displayed to explain why the permission is required by the App
                    for(String permissionAlreadyDenied : permissionListAlreadyDenied) {
                        if (Manifest.permission.CAMERA.equals(permissionAlreadyDenied)) {
                            explanationMessage += "\n\n" + resource.getString(R.string.permissions_rationale_msg_camera);
                        } else if(Manifest.permission.RECORD_AUDIO.equals(permissionAlreadyDenied)){
                            explanationMessage += "\n\n"+resource.getString(R.string.permissions_rationale_msg_record_audio);
                        } else if(Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissionAlreadyDenied)){
                            explanationMessage += "\n\n"+resource.getString(R.string.permissions_rationale_msg_storage);
                        } else if(Manifest.permission.READ_CONTACTS.equals(permissionAlreadyDenied)){
                            explanationMessage += "\n\n"+resource.getString(R.string.permissions_rationale_msg_contacts);
                        } else {
                            Log.d(LOG_TAG, "## checkPermissions(): already denied permission not supported");
                        }

                    }
                } else { // fall back if resource is null.. very unlikely
                    explanationMessage = "You are about to be asked to grant permissions..\n\n";
                }

                // display the dialog with the info text
                AlertDialog.Builder permissionsInfoDialog = new AlertDialog.Builder(aCallingActivity);
                if(null != resource) {
                    permissionsInfoDialog.setTitle(resource.getString(R.string.permissions_rationale_popup_title));
                }

                permissionsInfoDialog.setMessage(explanationMessage);
                permissionsInfoDialog.setIcon(android.R.drawable.ic_dialog_info);
                permissionsInfoDialog.setPositiveButton(aCallingActivity.getString(R.string.yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!finalPermissionsListToBeGranted.isEmpty()) {
                            //ContactsManager.refreshLocalContactsSnapshot(aCallingActivity);
                            ActivityCompat.requestPermissions(aCallingActivity, finalPermissionsListToBeGranted.toArray(new String[finalPermissionsListToBeGranted.size()]), aPermissionsToBeGrantedBitMap);
                        }
                    }
                });
                permissionsInfoDialog.show();
            } else {
                // some permissions are not granted, ask permissions
                if (isRequestPermissionRequired) {
                    ActivityCompat.requestPermissions(aCallingActivity, finalPermissionsListToBeGranted.toArray(new String[finalPermissionsListToBeGranted.size()]), aPermissionsToBeGrantedBitMap);
                } else {
                    // permissions were granted, start now..
                    isPermissionGranted = true;
                }
            }
        }
        return isPermissionGranted;
    }

    /**
     * Helper method used in {@link #checkPermissions(int, Activity)} to populate the list of the
     * permissions to be granted (aPermissionsListToBeGranted_out) and the list of the permissions already denied (aPermissionAlreadyDeniedList_out).
     * @param aCallingActivity calling activity
     * @param aPermissionAlreadyDeniedList_out list to be updated with the permissions already denied by the user
     * @param aPermissionsListToBeGranted_out list to be updated with the permissions to be granted
     * @param permissionType the permission to be checked
     * @return true if the permission requires to be granted, false otherwise
     */
    private static boolean updatePermissionsToBeGranted(final Activity aCallingActivity, List<String> aPermissionAlreadyDeniedList_out, List<String> aPermissionsListToBeGranted_out, final String permissionType) {
        boolean isRequestPermissionRequested = false;

        // add permission to be granted
        aPermissionsListToBeGranted_out.add(permissionType);

        if(PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(aCallingActivity.getApplicationContext(), permissionType)){
            isRequestPermissionRequested = true;

            // add permission to the ones that were already asked to the user
            if(ActivityCompat.shouldShowRequestPermissionRationale(aCallingActivity, permissionType)){
                aPermissionAlreadyDeniedList_out.add(permissionType);
            }
        }
        return isRequestPermissionRequested;
    }

    /**
     * Helper method to process {@link CommonActivityUtils#REQUEST_CODE_PERMISSION_AUDIO_IP_CALL}
     * on onRequestPermissionsResult() methods.
     * @param aContext App context
     * @param aPermissions permissions list
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
                    if(null != aContext)
                        CommonActivityUtils.displayToast(aContext, aContext.getString(R.string.permissions_action_not_performed_missing_permissions));
                }
            }
        } catch (Exception ex){
            Log.d(LOG_TAG, "## onPermissionResultAudioIpCall(): Exception MSg="+ex.getMessage());
        }

        return isPermissionGranted;
    }

    /**
     * Helper method to process {@link CommonActivityUtils#REQUEST_CODE_PERMISSION_VIDEO_IP_CALL}
     * on onRequestPermissionsResult() methods.
     * For video IP calls, record audio and camera permissions are both mandatory.
     * @param aContext App context
     * @param aPermissions permissions list
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
                if(null != aContext)
                    CommonActivityUtils.displayToast(aContext, aContext.getString(R.string.permissions_action_not_performed_missing_permissions));
            }
        } catch (Exception ex){
            Log.d(LOG_TAG, "## onPermissionResultVideoIpCall(): Exception MSg="+ex.getMessage());
        }

        return isPermissionGranted;
    }

    //==============================================================================================================
    // Room preview methods.
    //==============================================================================================================

    /**
     * Start a room activity in preview mode.
     * @param fromActivity the caller activity.
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
     * @param aMatrixId matrix ID of the user
     * @param aRoomId room ID
     * @param aContext application context
     * @param aTargetActivity the activity set in the returned intent
     * @return a valid intent if operation succeed, null otherwise
     */
    public static Intent buildIntentPreviewRoom(String aMatrixId, String aRoomId, Context aContext, Class<?> aTargetActivity) {
        Intent intentRetCode;

        // sanity check
        if ((null == aContext) || (null == aRoomId) || (null == aMatrixId)){
            intentRetCode = null;
        } else {
            MXSession session;

            // get the session
            if(null == (session = Matrix.getInstance(aContext).getSession(aMatrixId))) {
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
     * @param fromActivity the caller activity.
     * @param session the session
     * @param roomId the roomId
     * @param roomAlias the room alias
     * @param callback the operation callback
     */
    public static void previewRoom(final Activity fromActivity, final MXSession session, final String roomId, final String roomAlias, final ApiCallback<Void> callback) {
        previewRoom(fromActivity, session, roomId, new RoomPreviewData(session, roomId, null, roomAlias, null), callback);
    }

    /**
     * Start a room activity in preview mode.
     * If the room is already joined, open it in edition mode.
     * @param fromActivity the caller activity.
     * @param session the session
     * @param roomId the roomId
     * @param roomPreviewData the room preview data
     * @param callback the operation callback
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
                HashMap<String, Object> params = new HashMap<String, Object>();
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
     * @param fromActivity the caller activity.
     * @param params the room activity parameters
     */
    public static void goToRoomPage(final Activity fromActivity, final Map<String, Object> params) {
        goToRoomPage(fromActivity, null, params);
    }

    /**
     * Start a room activity with the dedicated parameters.
     * Pop the activity to the homeActivity before pushing the new activity.
     * @param fromActivity  the caller activity.
     * @param session the session.
     * @param params the room activity parameters.
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
                                                       String displayName = VectorUtils.getRoomDisplayname(fromActivity, finalSession, room);

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
     * Search the first existing room with a dedicated user.
     * @param aSession the session
     * @param otherUserId the other user id
     * @return the room if it exits.
     */
    public static Room findOneToOneRoom(final MXSession aSession, final String otherUserId) {
        Collection<Room> rooms = aSession.getDataHandler().getStore().getRooms();

        for (Room room : rooms) {
            Collection<RoomMember> members = room.getMembers();

            if (members.size() == ROOM_SIZE_ONE_TO_ONE) {
                for (RoomMember member : members) {
                    if (member.getUserId().equals(otherUserId)) {
                        return room;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Return all the 1:1 rooms joined by the searched user and by the current logged in user.
     * This method go through all the rooms, and for each room, tests if the searched user
     * and the logged in user are present.
     * @param aSession session
     * @param aSearchedUserId the searched user ID
     * @return an array containing the found rooms
     */
    public static ArrayList<Room> findOneToOneRoomList(final MXSession aSession, final String aSearchedUserId) {
        ArrayList<Room> listRetValue = new ArrayList<>();
        List<RoomMember> roomMembersList;
        String userId0, userId1;

        if((null!=aSession) && (null!=aSearchedUserId)) {
            Collection<Room> roomsList = aSession.getDataHandler().getStore().getRooms();

            for (Room room : roomsList) {
                roomMembersList = (List<RoomMember>)room.getJoinedMembers();

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
     * Return the 1:1 room with the most recent message, that the searched user and the current
     * logged user have joined.
     * Among the list of the 1:1 rooms, joined by the user, the room with the most recent
     * posted message is chosen to be returned.
     * @param aSession session
     * @param aSearchedUserId the searched user ID
     * @return 1:1 room joined by the user with the most recent message, null otherwise
     */
    public static Room findLatestOneToOneRoom(final MXSession aSession, final String aSearchedUserId) {
        long serverTimeStamp = 0, newServerTimeStamp;
        RoomSummary summary;
        Room mostRecentRoomRetValue = null;
        IMXStore mStore = aSession.getDataHandler().getStore();

        // get all the "one to one" rooms where the user has joined
        ArrayList<Room> roomsFoundList = findOneToOneRoomList(aSession, aSearchedUserId);

        // parse all the 1:1 rooms and take the one with the most recent message.
        if (!roomsFoundList.isEmpty()) {
            for (Room room : roomsFoundList) {

                summary = mStore.getSummary(room.getRoomId());
                try {
                    // test on the most recent time stamp
                    if ((null != summary) && ((newServerTimeStamp = summary.getLatestEvent().getOriginServerTs()) > serverTimeStamp)) {
                        mostRecentRoomRetValue = room;
                        serverTimeStamp = newServerTimeStamp;
                    }
                } catch(Exception ex) {
                    Log.e(LOG_TAG,"## findLatestOneToOneRoom(): Exception Msg="+ex.getMessage());
                }
            }
        }

        return mostRecentRoomRetValue;
    }

    /**
     * Check if the room is a 1:1 room and if the searched user has joined this room.
     * The user ID is searched in the room only if the room is a 1:1 room.
     * This method is useful to check if we can create a new 1:1 room when it is
     * asked from a already existing room (see {@link VectorMemberDetailsActivity#ITEM_ACTION_START_CHAT}).
     * @param aRoom room to be checked
     * @param aSearchedUserId the user ID to be searched in the room
     * @return true if the room is a 1:1 room where the user ID is present, false otherwise
     */
    public static boolean isOneToOneRoomJoinedByUserId(final Room aRoom, final String aSearchedUserId) {
        boolean retVal = false;
        List<RoomMember> memberList;

        if((null != aRoom) && (null != (memberList=(List<RoomMember>)aRoom.getJoinedMembers()))){
            if(CommonActivityUtils.ROOM_SIZE_ONE_TO_ONE == memberList.size()) {
                for (RoomMember member : memberList) {
                    if (member.getUserId().equals(aSearchedUserId)) {
                        retVal = true;
                    }
                }
            }
        }

        return retVal;
    }

    /**
     * Jump to a 1:1 room with a dedicated user.
     * If there is no room with this user, the room is created.
     * @param aSession the session.
     * @param otherUserId the other user id.
     * @param fromActivity the caller activity.
     * @param callback the callback.
     */
    public static void goToOneToOneRoom(final MXSession aSession, final String otherUserId, final Activity fromActivity, final ApiCallback<Void> callback) {
        // sanity check
        if (null == otherUserId) {
            return;
        }

        // check first if the 1:1 room already exists
        MXSession session = (aSession == null) ? Matrix.getMXSession(fromActivity, null) : aSession;

        // no session is provided
        if (null == session) {
            // get the default one.
            session = Matrix.getInstance(fromActivity.getApplicationContext()).getDefaultSession();
        }

        // sanity check
        if ((null == session) || !session.isAlive()) {
            return;
        }

        final MXSession fSession = session;
        Room room = findLatestOneToOneRoom(session, otherUserId);

        // the room already exists -> switch to it
        if (null != room) {
            Log.d(LOG_TAG,"## goToOneToOneRoom(): room already exists");
            HashMap<String, Object> params = new HashMap<String, Object>();

            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, session.getMyUserId());
            params.put(VectorRoomActivity.EXTRA_ROOM_ID, room.getRoomId());

            CommonActivityUtils.goToRoomPage(fromActivity, session, params);

            // everything is ok
            if (null != callback) {
                callback.onSuccess(null);
            }
        } else {
            Log.d(LOG_TAG,"## goToOneToOneRoom(): start createRoom()");
            session.createRoom(new SimpleApiCallback<String>(fromActivity) {
                @Override
                public void onSuccess(String roomId) {
                    final Room room = fSession.getDataHandler().getRoom(roomId);

                    final SimpleApiCallback inviteCallback = new SimpleApiCallback<Void>(this) {
                        @Override
                        public void onSuccess(Void info) {
                            HashMap<String, Object> params = new HashMap<String, Object>();
                            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, fSession.getMyUserId());
                            params.put(VectorRoomActivity.EXTRA_ROOM_ID, room.getRoomId());
                            params.put(VectorRoomActivity.EXTRA_EXPAND_ROOM_HEADER, true);

                            Log.d(LOG_TAG, "## goToOneToOneRoom(): invite() onSuccess - start goToRoomPage");
                            CommonActivityUtils.goToRoomPage(fromActivity, fSession, params);

                            callback.onSuccess(null);
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            Log.d(LOG_TAG, "## goToOneToOneRoom(): invite() onMatrixError Msg="+e.getLocalizedMessage());
                            if (null != callback) {
                                callback.onMatrixError(e);
                            }
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            Log.d(LOG_TAG, "## goToOneToOneRoom(): invite() onNetworkError Msg="+e.getLocalizedMessage());
                            if (null != callback) {
                                callback.onNetworkError(e);
                            }
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            Log.d(LOG_TAG, "## goToOneToOneRoom(): invite() onUnexpectedError Msg="+e.getLocalizedMessage());
                            if (null != callback) {
                                callback.onUnexpectedError(e);
                            }
                        }

                    };

                    // check if the userId defines an email address.
                    if (android.util.Patterns.EMAIL_ADDRESS.matcher(otherUserId).matches()) {
                        Log.d(LOG_TAG, "## goToOneToOneRoom(): createRoom() onSuccess - start invite by mail");
                        room.inviteByEmail(otherUserId, inviteCallback);
                    } else {
                        Log.d(LOG_TAG, "## goToOneToOneRoom(): createRoom() onSuccess - start invite");
                        room.invite(otherUserId, inviteCallback);
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.d(LOG_TAG, "## goToOneToOneRoom(): createRoom() onMatrixError Msg="+e.getLocalizedMessage());
                    if (null != callback) {
                        callback.onMatrixError(e);
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.d(LOG_TAG, "## goToOneToOneRoom(): createRoom() onNetworkError Msg="+e.getLocalizedMessage());
                    if (null != callback) {
                        callback.onNetworkError(e);
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.d(LOG_TAG, "## goToOneToOneRoom(): createRoom() onUnexpectedError Msg="+e.getLocalizedMessage());
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
            FragmentManager fm = ((FragmentActivity) fromActivity).getSupportFragmentManager();

            AccountsSelectionDialogFragment fragment = (AccountsSelectionDialogFragment) fm.findFragmentByTag(MXCActionBarActivity.TAG_FRAGMENT_ACCOUNT_SELECTION_DIALOG);
            if (fragment != null) {
                fragment.dismissAllowingStateLoss();
            }

            fragment = AccountsSelectionDialogFragment.newInstance(Matrix.getMXSessions(fromActivity));

            fragment.setListener(new AccountsSelectionDialogFragment.AccountsListener() {
                @Override
                public void onSelected(final MXSession session) {
                    fromActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sendFilesTo(fromActivity, intent, session);
                        }
                    });
                }
            });

            fragment.show(fm, MXCActionBarActivity.TAG_FRAGMENT_ACCOUNT_SELECTION_DIALOG);
        }
    }

    /**
     * Offer to send some dedicated intent data to an existing room
     *
     * @param fromActivity the caller activity
     * @param intent       the intent param
     * @param session      the session/
     */
    public static void sendFilesTo(final Activity fromActivity, final Intent intent, final MXSession session) {
        // sanity check
        if ((null == session) || !session.isAlive()) {
            return;
        }

        ArrayList<RoomSummary> mergedSummaries = new ArrayList<RoomSummary>(session.getDataHandler().getStore().getSummaries());

        // keep only the joined room
        for (int index = 0; index < mergedSummaries.size(); index++) {
            RoomSummary summary = mergedSummaries.get(index);
            Room room = session.getDataHandler().getRoom(summary.getRoomId());

            if ((null == room) || room.isInvited()) {
                mergedSummaries.remove(index);
                index--;
            }
        }

        Collections.sort(mergedSummaries, new Comparator<RoomSummary>() {
            @Override
            public int compare(RoomSummary lhs, RoomSummary rhs) {
                if (lhs == null || lhs.getLatestEvent() == null) {
                    return 1;
                } else if (rhs == null || rhs.getLatestEvent() == null) {
                    return -1;
                }

                if (lhs.getLatestEvent().getOriginServerTs() > rhs.getLatestEvent().getOriginServerTs()) {
                    return -1;
                } else if (lhs.getLatestEvent().getOriginServerTs() < rhs.getLatestEvent().getOriginServerTs()) {
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

                                HashMap<String, Object> params = new HashMap<String, Object>();
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

    /**
     * Check if the userId format is valid with the matrix standard.
     * It should start with a @ and ends with the home server suffix.
     *
     * @param userId           the userID to check
     * @param homeServerSuffix the home server suffix
     * @return the checked user ID
     */
    public static String checkUserId(String userId, String homeServerSuffix) {
        String res = userId;

        if (res.length() > 0) {
            res = res.trim();
            if (!res.startsWith("@")) {
                res = "@" + res;
            }

            if (res.indexOf(":") < 0) {
                res += homeServerSuffix;
            }
        }

        return res;
    }

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
                        Log.d(LOG_TAG,"## openMedia(): Exception Msg="+e.getMessage());
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
     * @return the downloads file path if the file exists or has been properly saved
     */
    public static String saveFileInto(File sourceFile, String dstDirPath, String outputFilename) {
        // sanity check
        if ((null == sourceFile) || (null == dstDirPath)) {
            return null;
        }

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

        // Copy source file to destination
        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            // create only the
            if (!dstFile.exists()) {
                dstFile.createNewFile();

                inputStream = new FileInputStream(sourceFile);
                outputStream = new FileOutputStream(dstFile);

                byte[] buffer = new byte[1024 * 10];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                }
            }
        } catch (Exception e) {
            dstFile = null;
        } finally {
            // Close resources
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (Exception e) {
                Log.e(LOG_TAG,"## saveFileInto(): Exception Msg="+e.getMessage());
            }
        }

        if (null != dstFile) {
            return dstFile.getAbsolutePath();
        } else {
            return null;
        }
    }

    /**
     * Save a media URI into the download directory
     *
     * @param context  the context
     * @param srcFile  the source file.
     * @param filename the filename (optional)
     * @return the downloads file path
     */
    @SuppressLint("NewApi")
    public static String saveMediaIntoDownloads(Context context, File srcFile, String filename, String mimeType) {
        String fullFilePath = saveFileInto(srcFile, Environment.DIRECTORY_DOWNLOADS, filename);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            if (null != fullFilePath) {
                DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

                try {
                    File file = new File(fullFilePath);
                    downloadManager.addCompletedDownload(file.getName(), file.getName(), true, mimeType, file.getAbsolutePath(), file.length(), true);
                } catch (Exception e) {
                    Log.e(LOG_TAG,"## saveMediaIntoDownloads(): Exception Msg="+e.getMessage());
                }
            }
        }

        return fullFilePath;
    }

    /**
     * Save an image URI into the gallery
     *
     * @param context    the context.
     * @param sourceFile the image path to save.
     */
    public static String saveImageIntoGallery(Context context, File sourceFile) {
        String filePath = saveFileInto(sourceFile, Environment.DIRECTORY_PICTURES, null);

        if (null != filePath) {
            // This broadcasts that there's been a change in the media directory
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(filePath))));
        }

        return filePath;
    }

    //==============================================================================================================
    // toast utils
    //==============================================================================================================

    /**
     * Display a toast
     * @param aContext the context.
     * @param aTextToDisplay the text to display.
     */
    public static void displayToast(Context aContext, CharSequence aTextToDisplay) {
        Toast.makeText(aContext, aTextToDisplay, Toast.LENGTH_SHORT).show();
    }

    /**
     * Display a snack.
     * @param aTargetView the parent view.
     * @param aTextToDisplay the text to display.
     */
    public static void displaySnack(View aTargetView, CharSequence aTextToDisplay) {
        Snackbar.make(aTargetView, aTextToDisplay, Snackbar.LENGTH_SHORT).show();
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
            Log.e(LOG_TAG,"## updateBadgeCount(): Exception Msg="+e.getMessage());
        }
    }

    /**
     * @return the badge value
     */
    public static int getBadgeCount() {
        return mBadgeValue;
    }


    //==============================================================================================================
    // Low memory management
    //==============================================================================================================

    private static final String LOW_MEMORY_LOG_TAG = "Memory usage";

    /**
     * Log the memory status.
     */
    public static void displayMemoryInformation(Activity activity) {
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
        Log.e(LOW_MEMORY_LOG_TAG, "usedSize   " + (usedSize / 1048576L) + " MB");
        Log.e(LOW_MEMORY_LOG_TAG, "freeSize   " + (freeSize / 1048576L) + " MB");
        Log.e(LOW_MEMORY_LOG_TAG, "totalSize  " + (totalSize / 1048576L) + " MB");
        Log.e(LOW_MEMORY_LOG_TAG, "---------------------------------------------------");


        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);

        Log.e(LOW_MEMORY_LOG_TAG, "availMem   " + (mi.availMem / 1048576L) + " MB");
        Log.e(LOW_MEMORY_LOG_TAG, "totalMem   " + (mi.totalMem / 1048576L) + " MB");
        Log.e(LOW_MEMORY_LOG_TAG, "threshold  " + (mi.threshold / 1048576L) + " MB");
        Log.e(LOW_MEMORY_LOG_TAG, "lowMemory  " + (mi.lowMemory));
        Log.e(LOW_MEMORY_LOG_TAG, "---------------------------------------------------");
    }

    /**
     * Manage the low memory case
     *
     * @param activity activity instance
     */
    public static void onLowMemory(Activity activity) {
        if (!VectorApp.isAppInBackground()) {
            String activityName = (null!=activity)?activity.getClass().getSimpleName():"NotAvailable";
            Log.e(LOW_MEMORY_LOG_TAG, "Active application : onLowMemory from " + activityName);

            displayMemoryInformation(activity);

            if (CommonActivityUtils.shouldRestartApp(activity)) {
                Log.e(LOW_MEMORY_LOG_TAG, "restart");
                CommonActivityUtils.restartApp(activity);
            } else {
                Log.e(LOW_MEMORY_LOG_TAG, "clear the application cache");
                Matrix.getInstance(activity).reloadSessions(activity);
            }
        } else {
            Log.e(LOW_MEMORY_LOG_TAG, "background application : onLowMemory ");
        }

        displayMemoryInformation(activity);
    }

    /**
     * Manage the trim memory.
     * @param activity the activity.
     * @param level the memory level
     */
    public static void onTrimMemory(Activity activity, int level) {
        String activityName = (null!=activity)?activity.getClass().getSimpleName():"NotAvailable";
        Log.e(LOW_MEMORY_LOG_TAG, "Active application : onTrimMemory from "+ activityName+" level=" + level);
        // TODO implement things to reduce memory usage

        displayMemoryInformation(activity);
    }
}
