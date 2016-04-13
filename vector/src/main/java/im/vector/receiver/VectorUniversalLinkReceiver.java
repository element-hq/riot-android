package im.vector.receiver;


import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import im.vector.Matrix;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.LoginActivity;
import im.vector.activity.SplashActivity;
import im.vector.activity.VectorHomeActivity;
import im.vector.activity.VectorRoomActivity;

// adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d "http://vector.im"
// adb shell am start -a android.intent.action.MAIN -n com.civolution.instantdetector.example/com.civolution.instantdetector.example.NonRegressionTest -e appli_type 3g -e filename snap-input-24000

// adb shell am broadcast -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d "http://vector.im"
// adb shell am broadcast -n im.vector/im.vector.broadcast.VectorUniversalLinkReceiver
// OK adb shell am broadcast -a im.vector.receiver.UNIVERSAL_LINK adb shell am broadcast -a im.vector.receiver.UNIVERSAL_LINK -d "http://vector.im/beta/"
// https://vector.im/beta/#/room/!WdMmNiklMyEcvSuKZv:matrix.org?email=mmmmanolo101%40yopmail.com&signurl=https%3A%2F%2Fvector.im%2F_matrix%2Fidentity%2Fapi%2Fv1%2Fsign-ed25519%3Ftoken%3DFdQlQjXJhXBFmspRaDxwudamtEqhSbqtzvnUCcnvlrMwALKWRBhertUiaYfLhDQogDjKtBjPzyIJthrtJBAmudvumuuQGJsVDjKWJARIdmJjAKtFpcuRVIoRvQzpSbIL%26private_key%3DxXfHpTue3rK1PnxoJaLQvdidMPyL8g4wWI60Bi9q0qQ&room_name=&room_avatar_url=&inviter_name=Manu&guest_access_token=&guest_user_id=

@SuppressLint("LongLogTag")
public class VectorUniversalLinkReceiver extends BroadcastReceiver {
    public static final String BROADCAST_ACTION_UNIVERSAL_LINK = "im.vector.receiver.UNIVERSAL_LINK";
    public static final String BROADCAST_ACTION_UNIVERSAL_LINK_RESUME = "im.vector.receiver.UNIVERSAL_LINK_RESUME";
    private static final String LOG_TAG = "VectorUniversalLinkReceiver";
    public static final String EXTRA_UNIVERSAL_LINK_URI = "EXTRA_UNIVERSAL_LINK_URI";
    public static final String EXTRA_UNIVERSAL_LINK_FLOW_ID = "EXTRA_UNIVERSAL_LINK_FLOW_ID";
    public static final String EXTRA_UNIVERSAL_LINK_SENDER_ID = "EXTRA_UNIVERSAL_LINK_SENDER_ID";

    public static final String HOME_SENDER_ID = VectorHomeActivity.class.getSimpleName();
    public static final String LOGIN_SENDER_ID = LoginActivity.class.getSimpleName();
    public static final String SPLASH_SENDER_ID = SplashActivity.class.getSimpleName();

    public static final String SUPPORTED_PATH_BETA = "/beta/";
    public static final String SUPPORTED_PATH_DEVELOP = "/develop/";
    public static final String SUPPORTED_PATH_APP = "/app/";
    public static final String SUPPORTED_PATH_STAGING = "/staging/";

    private static final int OFFSET_FRAGMENT_ROOM_ID = 1;
    private static final int OFFSET_FRAGMENT_EVENT_ID = 2;
    private static final int FRAGMENT_MAX_SPLIT_SECTIONS = 3;

    public static final List<String> mSupportedVectorLinkPaths = Arrays.asList(SUPPORTED_PATH_BETA, SUPPORTED_PATH_DEVELOP, SUPPORTED_PATH_APP, SUPPORTED_PATH_STAGING);

    private String mRoomId;
    private String mEventId;
    private MXSession mSession;


    public VectorUniversalLinkReceiver() {
    }

    @Override
    public void onReceive(final Context aContext, Intent aIntent) {
        String action,uriString, path;
        Uri intentUri;
        final List<String> uriSegments;
        Room targetRoom = null;

        Log.d(LOG_TAG, "## onReceive() IN");

        // get session
        mSession = Matrix.getInstance(aContext).getDefaultSession();
        if(null == mSession){
            Log.e(LOG_TAG, "## onReceive() Warning - Unable to proceed URL link: Session is null");

            // No user is logged => no session. Just forward request to the login activity
            Intent intent = new Intent(aContext, LoginActivity.class);
            intent.putExtra(EXTRA_UNIVERSAL_LINK_URI, aIntent.getData());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            aContext.startActivity(intent);

            return;
        }

        /*Thread trun = new Thread(new Runnable() {
            @Override
            public void run() {
                Intent myBroadcastIntent = new Intent(VectorUniversalLinkReceiver.BROADCAST_ACTION_UNIVERSAL_LINK);

                aContext.sendBroadcast(myBroadcastIntent);
            }
        });
        trun.start();*/

        if (null != aIntent) {
            action = aIntent.getAction();
            uriString = aIntent.getDataString();

            Log.d(LOG_TAG, "## onReceive()  uri getDataString=" + uriString);

            boolean isSessionActive = mSession.isAlive();
            boolean isLoginStepDone = mSession.getDataHandler().isInitialSyncComplete();
            Log.d(LOG_TAG, "## onReceive() isSessionActive=" + isSessionActive + " isLoginStepDone=" + isLoginStepDone);


            if(TextUtils.equals(action, BROADCAST_ACTION_UNIVERSAL_LINK)){
                Log.d(LOG_TAG, "## onReceive() action = BROADCAST_ACTION_UNIVERSAL_LINK");
                intentUri = aIntent.getData();

            } else if(TextUtils.equals(action, BROADCAST_ACTION_UNIVERSAL_LINK_RESUME)){
                Log.d(LOG_TAG, "## onReceive() action = BROADCAST_ACTION_UNIVERSAL_LINK_RESUME");

                // A first BROADCAST_ACTION_UNIVERSAL_LINK has been received with a room alias that could not be translated to a room ID.
                // Translation has been asked to server, and the response is processed here.
                // ......................
                intentUri = aIntent.getParcelableExtra(EXTRA_UNIVERSAL_LINK_URI);
                String senderId = aIntent.getParcelableExtra(EXTRA_UNIVERSAL_LINK_SENDER_ID);
            } else {
                // unknown action (very unlikely)
                Log.e(LOG_TAG, "## onReceive() Unknown action received ("+action+") - unable to proceed URL link");
                return;
            }

            if (null != intentUri) {
                path = intentUri.getPath();
                Log.d(LOG_TAG, "## onCreate() intentUri - host=" + intentUri.getHost() + " path=" + path + " queryParams=" + intentUri.getQuery());
                //intentUri.getEncodedSchemeSpecificPart() = //vector.im/beta/  intentUri.getSchemeSpecificPart() = //vector.im/beta/

                if (isUrlPathSupported(path)) {
                    String uriFragment = null;

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
                        // retrieve room ID and event ID (if any)
                        getParamsFromUri(intentUri, mSession);

                        if (null == mRoomId) {
                            // room could not be found:
                            // - Vector app not started?
                            // - Vector app has not finished sync complete?
                            // - user has not already joined this room?
                            Log.w(LOG_TAG, "## onReceive() URL Link error: room ID cannot be found");
                            CommonActivityUtils.displayToast(aContext, "URL Link error: room ID cannot be found");
                        } else if ((null != (targetRoom = mSession.getDataHandler().getRoom(mRoomId))) && (targetRoom.isLeaving())) {
                            Log.w(LOG_TAG, "## onReceive() URL Link error: target room \"" + mRoomId + "\"is about to be left by the user..");
                            CommonActivityUtils.displayToast(aContext, "URL Link error: target room \"" + mRoomId + "\"is about to be left by the user..");
                        } else {
                            HashMap<String, Object> params = new HashMap<String, Object>();

                            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                            params.put(VectorRoomActivity.EXTRA_ROOM_ID, mRoomId);
                            if (null != mEventId) {
                                params.put(VectorRoomActivity.EXTRA_EVENT_ID, mEventId);
                            }

                            // clear the activity stack to home activity
                            Intent intent = new Intent(aContext, VectorHomeActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

                            intent.putExtra(VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS, (Serializable) params);
                            aContext.startActivity(intent);
                        }
                    }
                } else {
                    Log.e(LOG_TAG, "## onReceive() Path not supported: " + path);
                    CommonActivityUtils.displayToast(aContext, "URL Link error - URL path ("+path+") is not supported");
                }
            }
        }
    }

    /**
     * Extract room ID and event ID from the intent Uri.
     * @param aIntentUri
     * @param aSession
     */
    private void getParamsFromUri(Uri aIntentUri, MXSession aSession) {
        // sanity check
        if((null == aIntentUri) || (null == aSession)){
            mRoomId = null;
            mEventId = null;
            return;
        } else {
            List<String> params = parseUniversalLink(aIntentUri);

            if (null != params) {
                Log.d(LOG_TAG,"## getParamsFromUri(): URI room ID=" + params.get(OFFSET_FRAGMENT_ROOM_ID));

                if (params.get(OFFSET_FRAGMENT_ROOM_ID).startsWith("!")) {
                    // room ID in classical format (ex: !quwbwtvMMHXdqvHZvv:matrix.org)
                    mRoomId = params.get(OFFSET_FRAGMENT_ROOM_ID);
                } else {
                    // room ID in alias format (ex: #Giom_and_Pedro:matrix.org)
                    // search the corresponding room and get its classical room ID
                    mRoomId = getRoomIdFromAlias(params.get(OFFSET_FRAGMENT_ROOM_ID), aSession);
                }

                // Is there any event ID?
                if (params.size() > 2) {
                    mEventId = params.get(OFFSET_FRAGMENT_EVENT_ID);
                }
            }
        }
    }

    private static String getRoomIdFromAlias(String aRoomAlias, MXSession aSession) {
        String roomIdRetValue = null;
        List<String> aliasList;
        String mainAlias = null;

        if ((null != aRoomAlias) && (null != aSession)) {
            Collection<Room> roomsList = aSession.getDataHandler().getStore().getRooms();
            Collection<RoomSummary> roomSummariesList = aSession.getDataHandler().getStore().getSummaries();

            Log.d(LOG_TAG, "## getRoomIdFromAlias(): Rooms Size=" + roomsList.size() + " Summary list size=" + roomSummariesList.size());

            // loop on all the rooms and find a room with the corresponding alias..
            for (Room room : roomsList) {
                aliasList = room.getState().aliases;
                mainAlias = room.getState().alias;

                if (null != mainAlias) {
                    Log.d(LOG_TAG, "## 1 getRoomIdFromAlias(): Main alias="+mainAlias);
                    if(aRoomAlias.equals(mainAlias)) {
                        Log.d(LOG_TAG, "## 1 getRoomIdFromAlias(): Main alias match1!");
                        roomIdRetValue = room.getRoomId();
                        return roomIdRetValue;
                    /*} else if(aRoomAlias.endsWith(mainAlias)){
                        Log.d(LOG_TAG, "## 1 getRoomIdFromAlias(): Main alias match2!");*/
                    } else {
                        Log.d(LOG_TAG, "## 1 getRoomIdFromAlias(): Main alias unmatch!");
                    }
                } else {
                    Log.d(LOG_TAG, "## 1 getRoomIdFromAlias(): Main alias is null");
                }

                if (null != aliasList) {
                    for (String alias : aliasList) {
                        Log.d(LOG_TAG, "## 2 getRoomIdFromAlias(): Alias list candidate=" + alias);
                        if (aRoomAlias.equals(alias)) {
                            Log.d(LOG_TAG, "## 2.1 getRoomIdFromAlias(): Alias list match!");
                            roomIdRetValue = room.getRoomId();
                            return roomIdRetValue;
                        } else {
                            Log.d(LOG_TAG, "## 2.1 getRoomIdFromAlias(): Alias list unmatch!");
                        }
                    }
                } else {
                    Log.d(LOG_TAG, "## 2 getRoomIdFromAlias(): Main alias list is null!");
                }
            }
        }

        return roomIdRetValue;
    }

    private static boolean isUrlPathSupported(String aPath) {
        return (null != aPath) ? mSupportedVectorLinkPaths.contains(aPath) : false;
    }

    /***
     * Tries to parse an universal link.
     * @param uri the uri to parse
     * @return the universal link items, null if the universal link is invalid
     */
    public static List<String> parseUniversalLink(Uri uri) {
        List<String> res = null;

        try {
            // sanity check
            if (null == uri) {
                Log.e(LOG_TAG, "## parseUniversalLink : null");
                return null;
            }

            if (!isUrlPathSupported(uri.getPath())) {
                Log.e(LOG_TAG, "## parseUniversalLink : not supported");
                return null;
            }

            String uriFragment;

            // remove the server part
            if (null != (uriFragment = uri.getFragment())) {
                uriFragment = uriFragment.substring(1); // get rid of first "/"
            } else {
                Log.e(LOG_TAG, "## parseUniversalLink : cannot extract path");
                return null;
            }

            String temp[] = uriFragment.split("/", FRAGMENT_MAX_SPLIT_SECTIONS); // limit to 3 for security concerns (stack overflow injection)

            if (temp.length < 2) {
                Log.e(LOG_TAG, "## parseUniversalLink : too short");
                return null;
            }

            if (!TextUtils.equals(temp[0], "room")) {
                Log.e(LOG_TAG, "## parseUniversalLink : not supported " + temp[0]);
                return null;
            }

            res = Arrays.asList(temp);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## parseUniversalLink : crashes " + e.getLocalizedMessage());
        }

        return res;
    }
}

