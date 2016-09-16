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

package im.vector.receiver;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;

import com.squareup.okhttp.internal.Platform;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import im.vector.Matrix;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.LoginActivity;
import im.vector.activity.SplashActivity;
import im.vector.activity.VectorHomeActivity;
import im.vector.activity.VectorRoomActivity;

@SuppressLint("LongLogTag")
/**
 * An universal link receiver.
 */
public class VectorUniversalLinkReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "VectorUniversalLinkReceiver";

    public static final String BROADCAST_ACTION_UNIVERSAL_LINK = "im.vector.receiver.UNIVERSAL_LINK";
    public static final String BROADCAST_ACTION_UNIVERSAL_LINK_RESUME = "im.vector.receiver.UNIVERSAL_LINK_RESUME";

    // the universal link
    public static final String EXTRA_UNIVERSAL_LINK_URI = "EXTRA_UNIVERSAL_LINK_URI";
    // the flow id
    public static final String EXTRA_UNIVERSAL_LINK_FLOW_ID = "EXTRA_UNIVERSAL_LINK_FLOW_ID";
    // the sender identifier (XXX_SENDER_ID)
    public static final String EXTRA_UNIVERSAL_LINK_SENDER_ID = "EXTRA_UNIVERSAL_LINK_SENDER_ID";

    // sender activities
    public static final String HOME_SENDER_ID = VectorHomeActivity.class.getSimpleName();
    public static final String LOGIN_SENDER_ID = LoginActivity.class.getSimpleName();
    public static final String SPLASH_SENDER_ID = SplashActivity.class.getSimpleName();

    // the supported paths
    private static final String SUPPORTED_PATH_BETA = "/beta/";
    private static final String SUPPORTED_PATH_DEVELOP = "/develop/";
    private static final String SUPPORTED_PATH_APP = "/app/";
    private static final String SUPPORTED_PATH_STAGING = "/staging/";

    // index of each item in path
    public static final String ULINK_ROOM_ID_KEY = "ULINK_ROOM_ID_KEY";
    public static final String ULINK_EVENT_ID_KEY = "ULINK_EVENT_ID_KEY";
    public static final String ULINK_EMAIL_ID_KEY = "email";
    public static final String ULINK_SIGN_URL_KEY = "signurl";
    public static final String ULINK_ROOM_NAME_KEY = "room_name";
    public static final String ULINK_ROOM_AVATAR_URL_KEY = "room_avatar_url";
    public static final String ULINK_INVITER_NAME_KEY = "inviter_name";
    public static final String ULINK_GUEST_ACCESS_TOKEN_KEY = "guest_access_token";
    public static final String ULINK_GUEST_USER_ID_KEY = "guest_user_id";

    // supported paths list
    private static final List<String> mSupportedVectorLinkPaths = Arrays.asList(SUPPORTED_PATH_BETA, SUPPORTED_PATH_DEVELOP, SUPPORTED_PATH_APP, SUPPORTED_PATH_STAGING);

    // the session
    private MXSession mSession;

    // the universal link parameters
    private HashMap<String, String>  mParameters;

    public VectorUniversalLinkReceiver() {
    }

    @Override
    public void onReceive(final Context aContext, final Intent aIntent) {
        String action,uriString;
        Uri intentUri;

        Log.d(LOG_TAG, "## onReceive() IN");

        // get session
        mSession = Matrix.getInstance(aContext).getDefaultSession();

        // user is not yet logged in
        if (null == mSession) {
            Log.e(LOG_TAG, "## onReceive() Warning - Unable to proceed URL link: Session is null");

            // No user is logged => no session. Just forward request to the login activity
            Intent intent = new Intent(aContext, LoginActivity.class);
            intent.putExtra(EXTRA_UNIVERSAL_LINK_URI, aIntent.getData());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            aContext.startActivity(intent);
            return;
        }

        // sanity check
        if (null != aIntent) {

            action = aIntent.getAction();
            uriString = aIntent.getDataString();
            boolean isSessionActive = mSession.isAlive();
            boolean isLoginStepDone = mSession.getDataHandler().isInitialSyncComplete();

            Log.d(LOG_TAG, "## onReceive() uri getDataString=" + uriString+"isSessionActive=" + isSessionActive + " isLoginStepDone=" + isLoginStepDone);

            if (TextUtils.equals(action, BROADCAST_ACTION_UNIVERSAL_LINK)){
                Log.d(LOG_TAG, "## onReceive() action = BROADCAST_ACTION_UNIVERSAL_LINK");
                intentUri = aIntent.getData();

            } else if(TextUtils.equals(action, BROADCAST_ACTION_UNIVERSAL_LINK_RESUME)){
                Log.d(LOG_TAG, "## onReceive() action = BROADCAST_ACTION_UNIVERSAL_LINK_RESUME");

                // A first BROADCAST_ACTION_UNIVERSAL_LINK has been received with a room alias that could not be translated to a room ID.
                // Translation has been asked to server, and the response is processed here.
                // ......................
                intentUri = aIntent.getParcelableExtra(EXTRA_UNIVERSAL_LINK_URI);
                // aIntent.getParcelableExtra(EXTRA_UNIVERSAL_LINK_SENDER_ID);
            } else {
                // unknown action (very unlikely)
                Log.e(LOG_TAG, "## onReceive() Unknown action received ("+action+") - unable to proceed URL link");
                return;
            }

            if (null != intentUri) {
                Log.d(LOG_TAG, "## onCreate() intentUri - host=" + intentUri.getHost() + " path=" + intentUri.getPath() + " queryParams=" + intentUri.getQuery());
                //intentUri.getEncodedSchemeSpecificPart() = //vector.im/beta/  intentUri.getSchemeSpecificPart() = //vector.im/beta/

                HashMap<String, String> params = parseUniversalLink(intentUri);

                if (null != params) {

                    if(!isSessionActive) {
                        Log.w(LOG_TAG, "## onReceive() Warning: Session is not alive");
                    }

                    if(!isLoginStepDone){
                        Log.w(LOG_TAG, "## onReceive() Warning: Session is not complete - start Login Activity");

                        // Start the login activity and wait for BROADCAST_ACTION_UNIVERSAL_LINK_RESUME.
                        // Once the login process flow is complete, BROADCAST_ACTION_UNIVERSAL_LINK_RESUME is
                        // sent back to resume the URL link processing.
                        Intent intent = new Intent(aContext, LoginActivity.class);
                        intent.putExtra(EXTRA_UNIVERSAL_LINK_URI, aIntent.getData());
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        aContext.startActivity(intent);
                    } else {
                        mParameters = params;
                        manageRoomOnActivity(aContext);
                    }
                } else {
                    Log.e(LOG_TAG, "## onReceive() Path not supported: " + intentUri.getPath());
                }
            }
        }
    }

    /**
     * Start the universal link management when the login process is done.
     * If there is no active activity, launch the home activity
     * @param aContext
     */
    private void manageRoomOnActivity(final Context aContext) {
        final Activity currentActivity = VectorApp.getCurrentActivity();

        if (null != currentActivity) {
            currentActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    manageRoom(aContext);
                }
            });
        } else {
            // clear the activity stack to home activity
            Intent intent = new Intent(aContext, VectorHomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(VectorHomeActivity.EXTRA_WAITING_VIEW_STATUS, VectorHomeActivity.WAITING_VIEW_START);
            aContext.startActivity(intent);

            final Timer wakeup = new Timer();

            wakeup.schedule(new TimerTask() {
                @Override
                public void run() {
                    wakeup.cancel();
                    manageRoomOnActivity(aContext);
                }
            }, 200);
        }
    }

    /**
     * Manage the room presence.
     * Check the URL room ID format: if room ID is provided as an alias, we translate it
     * into its corresponding room ID.
     * @param aContext the context
     */
    private void manageRoom(final Context aContext) {
        manageRoom(aContext, null);
    }

    /**
     * Manage the room presence.
     * Check the URL room ID format: if room ID is provided as an alias, we translate it
     * into its corresponding room ID.
     * @param aContext the context
     */
    private void manageRoom(final Context aContext, final String roomAlias) {
        final String roomIdOrAlias = mParameters.get(ULINK_ROOM_ID_KEY);

        Log.d(LOG_TAG, "manageRoom roomIdOrAlias");

        if (roomIdOrAlias.startsWith("!"))  { // usual room Id format (not alias)
            final RoomPreviewData roomPreviewData = new RoomPreviewData(mSession, roomIdOrAlias, mParameters.get(ULINK_EVENT_ID_KEY), roomAlias, mParameters);
            Room room = mSession.getDataHandler().getRoom(roomIdOrAlias, false);

            // if the room exists
            if ((null != room) && !room.isInvited()) {
                openRoomActivity(aContext);
            } else {

                CommonActivityUtils.previewRoom(VectorApp.getCurrentActivity(), mSession, roomIdOrAlias, roomPreviewData, null);
            }
        } else { // room ID is provided as a room alias: get corresponding room ID

            Log.d(LOG_TAG, "manageRoom : it is a room Alias");

            // Start the home activity with the waiting view enabled, while the URL link
            // is processed in the receiver. The receiver, once the URL was parsed, will stop the waiting view.
            Intent intent = new Intent(aContext, VectorHomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(VectorHomeActivity.EXTRA_WAITING_VIEW_STATUS, VectorHomeActivity.WAITING_VIEW_START);
            aContext.startActivity(intent);

            mSession.getDataHandler().roomIdByAlias(roomIdOrAlias, new ApiCallback<String>() {
                @Override
                public void onSuccess(final String roomId) {
                    Log.d(LOG_TAG, "manageRoom : retrieve the room ID " + roomId);
                    if (!TextUtils.isEmpty(roomId)) {
                        mParameters.put(ULINK_ROOM_ID_KEY, roomId);
                        manageRoom(aContext, roomIdOrAlias);
                    }
                }

                private void onError(String errorMessage) {
                    CommonActivityUtils.displayToast(aContext, errorMessage);
                    stopHomeActivitySpinner(aContext);
                }

                @Override
                public void onNetworkError(Exception e) {
                    onError(e.getLocalizedMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onError(e.getLocalizedMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onError(e.getLocalizedMessage());
                }
            });
        }
    }

    /**
     * Open the room activity with the dedicated parameters
     * @param context
     */
    private void openRoomActivity(Context context) {
        HashMap<String, Object> params = new HashMap<String, Object>();

        params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
        params.put(VectorRoomActivity.EXTRA_ROOM_ID, mParameters.get(ULINK_ROOM_ID_KEY));

        if (mParameters.containsKey(ULINK_EVENT_ID_KEY)) {
            params.put(VectorRoomActivity.EXTRA_EVENT_ID, mParameters.get(ULINK_EVENT_ID_KEY));
        }

        // clear the activity stack to home activity
        Intent intent = new Intent(context, VectorHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        intent.putExtra(VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS, params);
        context.startActivity(intent);
    }
    
    /***
     * Tries to parse an universal link.
     * @param uri the uri to parse
     * @return the universal link items, null if the universal link is invalid
     */
    public static HashMap<String, String> parseUniversalLink(Uri uri) {
        HashMap<String, String> map = null;

        try {
            // sanity check
            if ((null == uri) || TextUtils.isEmpty(uri.getPath())) {
                Log.e(LOG_TAG, "## parseUniversalLink : null");
                return null;
            }

            if (!mSupportedVectorLinkPaths.contains(uri.getPath())) {
                Log.e(LOG_TAG, "## parseUniversalLink : not supported");
                return null;
            }

            String uriFragment;

            if (!TextUtils.equals(uri.getHost(), "vector.im")) {
                Log.e(LOG_TAG, "## parseUniversalLink : unsupported host");
                return null;
            }

            // remove the server part
            if (null != (uriFragment = uri.getFragment())) {
                uriFragment = uriFragment.substring(1); // get rid of first "/"
            } else {
                Log.e(LOG_TAG, "## parseUniversalLink : cannot extract path");
                return null;
            }

            String temp[] = uriFragment.split("/", 3); // limit to 3 for security concerns (stack overflow injection)

            if (temp.length < 2) {
                Log.e(LOG_TAG, "## parseUniversalLink : too short");
                return null;
            }

            if (!TextUtils.equals(temp[0], "room")) {
                Log.e(LOG_TAG, "## parseUniversalLink : not supported " + temp[0]);
                return null;
            }

            map = new HashMap<>();

            // room id only ?
            if (temp.length == 2) {
                map.put(ULINK_ROOM_ID_KEY, temp[1]);
            } else {
                String eventId = temp[2];

                if (eventId.startsWith("$")) {
                    map.put(ULINK_ROOM_ID_KEY, temp[1]);
                    map.put(ULINK_EVENT_ID_KEY, temp[2]);
                } else {
                    uri = Uri.parse(uri.toString().replace("#/room/", "room/"));

                    map.put(ULINK_ROOM_ID_KEY, uri.getLastPathSegment());

                    Set<String> names = uri.getQueryParameterNames();

                    for(String name : names) {
                        String value = uri.getQueryParameter(name);

                        try {
                            value = URLDecoder.decode(value, "UTF-8");
                        } catch (Exception e) {

                        }

                        map.put(name, value);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## parseUniversalLink : crashes " + e.getLocalizedMessage());
        }

        return map;
    }

    /**
     * Stop the spinner on the home activity
     * @param aContext the context.
     */
    private void stopHomeActivitySpinner(Context aContext){
        Intent myBroadcastIntent = new Intent(VectorHomeActivity.BROADCAST_ACTION_STOP_WAITING_VIEW);
        aContext.sendBroadcast(myBroadcastIntent);
    }
}

