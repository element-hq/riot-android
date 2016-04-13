package im.vector.receiver;


import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import im.vector.Matrix;
import im.vector.activity.CommonActivityUtils;
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
    private static final String LOG_TAG = "VectorUniversalLinkReceiver";

    public static final String SUPPORTED_PATH_BETA = "/beta/";
    public static final String SUPPORTED_PATH_DEVELOP = "/develop/";
    public static final String SUPPORTED_PATH_APP = "/app/";
    public static final String SUPPORTED_PATH_STAGING = "/staging/";
    private static final int OFFSET_FRAGMENT_ROOM_ID = 1;
    private static final int OFFSET_FRAGMENT_EVENT_ID = 2;
    private static final int FRAGMENT_MAX_SPLIT_SECTIONS = 3;
    public static final HashSet<String> mSupportedVectorLinkPaths = new HashSet<String>();

    private String mRoomId;
    private String mEventId;
    private MXSession mSession;


    public VectorUniversalLinkReceiver() {
        mSupportedVectorLinkPaths.add(SUPPORTED_PATH_BETA);
        mSupportedVectorLinkPaths.add(SUPPORTED_PATH_DEVELOP);
        mSupportedVectorLinkPaths.add(SUPPORTED_PATH_APP);
        mSupportedVectorLinkPaths.add(SUPPORTED_PATH_STAGING);
    }

    @Override
    public void onReceive(Context aContext, Intent aIntent) {
        String scheme, mimeType, action, packageName, uriString, host, path;
        Uri intentUri;
        final List<String> uriSegments;
        Room targetRoom = null;

        Log.d(LOG_TAG, "## onReceive() IN");

        if (null != aIntent) {
            scheme = aIntent.getScheme();
            action = aIntent.getAction();
            uriString = aIntent.getDataString();
            packageName = aIntent.getPackage();
            mimeType = aIntent.getType();

            Log.d(LOG_TAG, "## onCreate() scheme=" + scheme + " action=" + action + " uri getDataString=" + uriString);
            Log.d(LOG_TAG, "## onCreate() packageName=" + packageName + " mimeType=" + mimeType);

            if (null != (intentUri = aIntent.getData())) {
                host = intentUri.getHost();
                int port = intentUri.getPort();
                path = intentUri.getPath();
                Log.d(LOG_TAG, "## onCreate() intentUri - intentUri.toString()=" + intentUri.toString());
                Log.d(LOG_TAG, "## onCreate() intentUri - host=" + host + " port=" + port + " path=" + path + " queryParams=" + intentUri.getQuery());
                Log.d(LOG_TAG, "## onCreate() intentUri - EncodedFragment=" + intentUri.getEncodedFragment() + " DecodedFragment=" + intentUri.getFragment());
                Log.d(LOG_TAG, "## onCreate() intentUri - EncodedSchemeSpecificPart=" + intentUri.getEncodedSchemeSpecificPart() + " SchemeSpecificPart=" + intentUri.getSchemeSpecificPart());
                Log.d(LOG_TAG, "## onCreate() intentUri - LastPathSegment=" + intentUri.getLastPathSegment());

                if (null != (uriSegments = intentUri.getPathSegments())) {
                    for (String segment : uriSegments) {
                        Log.d(LOG_TAG, "## onCreate() intentUri - uriSeg=" + segment);
                    }
                }

                if (isUrlPathSupported(path)) {
                    String uriFragment = null;
                    mSession = Matrix.getInstance(aContext).getDefaultSession();
                    boolean isSessionActive = mSession.isAlive();
                    boolean isLoginStepDone = mSession.getDataHandler().isInitialSyncComplete();

                    // retrieve room ID and event ID (if any)
                    getParamsFromUri(intentUri, mSession);

                    if (null == mRoomId) {
                        // room could not be found:
                        // - Vector app not started?
                        // - Vector app has not finished sync complete?
                        // - user has not already joined this room?
                        Log.w(LOG_TAG, "## URL Link error: room ID cannot be found");
                        CommonActivityUtils.displayToast(aContext, "URL Link error: room ID cannot be found");
                    } else if ((null != (targetRoom = mSession.getDataHandler().getRoom(mRoomId))) && (targetRoom.isLeaving())) {
                        Log.w(LOG_TAG, "## URL Link error: target room \""+mRoomId+"\"is about to be left by the user..");
                        CommonActivityUtils.displayToast(aContext, "URL Link error: target room \""+mRoomId+"\"is about to be left by the user..");
                    } else {
                        HashMap<String, Object> params = new HashMap<String, Object>();

                        params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                        params.put(VectorRoomActivity.EXTRA_ROOM_ID, mRoomId);
                        if (null != mEventId) {
                            params.put(VectorRoomActivity.EXTRA_EVENT_ID, mEventId);
                        }

                        // clear the activity stack to home activity
                        Intent intent = new Intent(aContext, VectorHomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP/*Intent.FLAG_ACTIVITY_REORDER_TO_FRONT*/| Intent.FLAG_ACTIVITY_NEW_TASK);

                        intent.putExtra(VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS, (Serializable) params);
                        aContext.startActivity(intent);
                    }
                } else {
                    Log.e(LOG_TAG, "## Path not supported: " + path);
                    CommonActivityUtils.displayToast(aContext, "URL Link error: Path not supported: " + path);
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
        String uriFragment;

        // sanity check
        if((null == aIntentUri) || (null == aSession)){
            mRoomId = null;
            mEventId = null;
            return;
        } else {
            try {
                // Extract room ID from URL
                if (null != (uriFragment = aIntentUri.getFragment())) {
                    // intentUri.getFragment() ex: "/room/#and:matrix.org/$1460098601502078OoSMt:matrix.org"
                    // "/room/!quwbwtvMMHXdqvHZvv:matrix.org" or "/room/#Giom_and_Pedro:matrix.org"
                    uriFragment = uriFragment.substring(1); // get rid of first "/"
                }

                String temp[] = uriFragment.split("/", FRAGMENT_MAX_SPLIT_SECTIONS); // limit to 3 for security concerns (stack overflow injection)

                Log.d(LOG_TAG,"## getParamsFromUri(): extracted room ID="+temp[OFFSET_FRAGMENT_ROOM_ID]);
                if (temp[OFFSET_FRAGMENT_ROOM_ID].startsWith("!")) {
                    // room ID in classical format (ex: !quwbwtvMMHXdqvHZvv:matrix.org)
                    mRoomId = temp[OFFSET_FRAGMENT_ROOM_ID];
                } else {
                    // room ID in alias format (ex: #Giom_and_Pedro:matrix.org)
                    // search the corresponding room and get its classical room ID
                    mRoomId = getRoomIdFromAlias(temp[OFFSET_FRAGMENT_ROOM_ID], aSession);
                }

                // Is there any event ID?
                if (temp.length > 2) {
                    mEventId = temp[OFFSET_FRAGMENT_EVENT_ID];
                }
            } catch (Exception ex) {
                Log.w(LOG_TAG, "## getParamsFromUri(): Exception Msg=" + ex.getMessage());
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

}

