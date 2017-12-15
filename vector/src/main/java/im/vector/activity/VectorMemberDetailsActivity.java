/*
 * Copyright 2014 OpenMarket Ltd
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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.ExifInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.VectorMemberDetailsAdapter;
import im.vector.adapters.VectorMemberDetailsDevicesAdapter;
import im.vector.fragments.VectorUnknownDevicesFragment;
import im.vector.util.CallsManager;
import im.vector.util.VectorUtils;

/**
 * VectorMemberDetailsActivity displays the member information and allows to perform some dedicated actions.
 */
public class VectorMemberDetailsActivity extends MXCActionBarActivity implements VectorMemberDetailsAdapter.IEnablingActions, VectorMemberDetailsDevicesAdapter.IDevicesAdapterListener {
    private static final String LOG_TAG = VectorMemberDetailsActivity.class.getSimpleName();

    public static final String EXTRA_ROOM_ID = "EXTRA_ROOM_ID";
    public static final String EXTRA_MEMBER_ID = "EXTRA_MEMBER_ID";
    public static final String EXTRA_MEMBER_DISPLAY_NAME = "EXTRA_MEMBER_DISPLAY_NAME";
    public static final String EXTRA_MEMBER_AVATAR_URL = "EXTRA_MEMBER_AVATAR_URL";

    public static final String EXTRA_STORE_ID = "EXTRA_STORE_ID";

    public static final String RESULT_MENTION_ID = "RESULT_MENTION_ID";

    private static final String AVATAR_FULLSCREEN_MODE = "AVATAR_FULLSCREEN_MODE";

    // list view items associated actions
    private static final int ITEM_ACTION_INVITE = 0;
    private static final int ITEM_ACTION_LEAVE = 1;
    public static final int ITEM_ACTION_KICK = 2;
    private static final int ITEM_ACTION_BAN = 3;
    private static final int ITEM_ACTION_UNBAN = 4;
    private static final int ITEM_ACTION_IGNORE = 5;
    private static final int ITEM_ACTION_UNIGNORE = 6;
    private static final int ITEM_ACTION_SET_DEFAULT_POWER_LEVEL = 7;
    private static final int ITEM_ACTION_SET_MODERATOR = 8;
    private static final int ITEM_ACTION_SET_ADMIN = 9;
    //public static final int ITEM_ACTION_SET_CUSTOM_POWER_LEVEL = 10;
    private static final int ITEM_ACTION_START_CHAT = 11;
    private static final int ITEM_ACTION_START_VOICE_CALL = 12;
    private static final int ITEM_ACTION_START_VIDEO_CALL = 13;
    private static final int ITEM_ACTION_MENTION = 14;
    private static final int ITEM_ACTION_DEVICES = 15;

    private static final int VECTOR_ROOM_MODERATOR_LEVEL = 50;
    private static final int VECTOR_ROOM_ADMIN_LEVEL = 100;

    // internal info
    private Room mRoom;
    private String mMemberId;       // member whose details area displayed (provided in EXTRAS)
    private RoomMember mRoomMember; // room member corresponding to mMemberId
    private MXSession mSession;
    private User mUser;
    //private ArrayList<MemberDetailsAdapter.AdapterMemberActionItems> mActionItemsArrayList;
    private VectorMemberDetailsAdapter mListViewAdapter;
    private VectorMemberDetailsDevicesAdapter mDevicesListViewAdapter;

    // UI widgets
    private ImageView mMemberAvatarImageView;
    private ImageView mMemberAvatarBadgeImageView;
    private TextView mMemberNameTextView;
    private TextView mPresenceTextView;
    private View mProgressBarView;

    // full screen avatar
    private View mFullMemberAvatarLayout;
    private ImageView mFullMemberAvatarImageView;

    // listview
    private ExpandableListView mExpandableListView;
    private ListView mDevicesListView;
    private View mDevicesListHeaderView;

    // direct message
    /**
     * callback for the creation of the direct message room
     **/
    private final ApiCallback<String> mCreateDirectMessageCallBack = new ApiCallback<String>() {
        @Override
        public void onSuccess(String roomId) {
            HashMap<String, Object> params = new HashMap<>();
            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
            params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
            params.put(VectorRoomActivity.EXTRA_EXPAND_ROOM_HEADER, true);

            Log.d(LOG_TAG, "## mCreateDirectMessageCallBack: onSuccess - start goToRoomPage");
            CommonActivityUtils.goToRoomPage(VectorMemberDetailsActivity.this, mSession, params);
        }

        @Override
        public void onMatrixError(MatrixError e) {
            Log.d(LOG_TAG, "## mCreateDirectMessageCallBack: onMatrixError Msg=" + e.getLocalizedMessage());
            mRoomActionsListener.onMatrixError(e);
        }

        @Override
        public void onNetworkError(Exception e) {
            Log.d(LOG_TAG, "## mCreateDirectMessageCallBack: onNetworkError Msg=" + e.getLocalizedMessage());
            mRoomActionsListener.onNetworkError(e);
        }

        @Override
        public void onUnexpectedError(Exception e) {
            Log.d(LOG_TAG, "## mCreateDirectMessageCallBack: onUnexpectedError Msg=" + e.getLocalizedMessage());
            mRoomActionsListener.onUnexpectedError(e);
        }
    };

    // MX event listener
    private final MXEventListener mLiveEventsListener = new MXEventListener() {
        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            VectorMemberDetailsActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String eventType = event.getType();

                    // check if the event is received for the current room
                    // check if there is a member update
                    if ((Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType)) || (Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS.equals(eventType))) {
                        // update only if it is the current user
                        VectorMemberDetailsActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (checkRoomMemberStatus()) {
                                    updateUi();
                                } else if (null != mRoom) {
                                    // exit from the activity
                                    finish();
                                }
                            }
                        });
                    }
                }
            });
        }

        @Override
        public void onLeaveRoom(String roomId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // pop to the home activity
                    Intent intent = new Intent(VectorMemberDetailsActivity.this, VectorHomeActivity.class);
                    intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    VectorMemberDetailsActivity.this.startActivity(intent);
                }
            });
        }
    };

    private final MXEventListener mPresenceEventsListener = new MXEventListener() {
        @Override
        public void onPresenceUpdate(Event event, final User user) {
            if (mMemberId.equals(user.user_id)) {
                // Someone's presence has changed, reprocess the whole list
                VectorMemberDetailsActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // display an avatar it it was not used
                        updateMemberAvatarUi();
                        // refresh the presence
                        updatePresenceInfoUi();
                    }
                });
            }
        }
    };

    // Room action listeners. Every time an action is detected the UI must be updated.
    private final ApiCallback<Void> mRoomActionsListener = new SimpleApiCallback<Void>(this) {
        @Override
        public void onMatrixError(MatrixError e) {
            Toast.makeText(VectorMemberDetailsActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            updateUi();
        }

        @Override
        public void onSuccess(Void info) {
            updateUi();
        }

        @Override
        public void onNetworkError(Exception e) {
            Toast.makeText(VectorMemberDetailsActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            updateUi();
        }

        @Override
        public void onUnexpectedError(Exception e) {
            Toast.makeText(VectorMemberDetailsActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            updateUi();
        }
    };

    /**
     * Start a call in a dedicated room
     *
     * @param isVideo true if the call is a video call
     */
    private void startCall(final boolean isVideo) {
        if (!mSession.isAlive()) {
            Log.e(LOG_TAG, "startCall : the session is not anymore valid");
            return;
        }

        // create the call object
        mSession.mCallsManager.createCallInRoom(mRoom.getRoomId(), isVideo, new ApiCallback<IMXCall>() {
            @Override
            public void onSuccess(final IMXCall call) {
                VectorMemberDetailsActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final Intent intent = new Intent(VectorMemberDetailsActivity.this, VectorCallViewActivity.class);

                        intent.putExtra(VectorCallViewActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                        intent.putExtra(VectorCallViewActivity.EXTRA_CALL_ID, call.getCallId());

                        VectorMemberDetailsActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                VectorMemberDetailsActivity.this.startActivity(intent);
                            }
                        });
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                CommonActivityUtils.displayToast(VectorMemberDetailsActivity.this, e.getLocalizedMessage());
                Log.e(LOG_TAG, "## startCall() failed " + e.getMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (e instanceof MXCryptoError) {
                    MXCryptoError cryptoError = (MXCryptoError) e;

                    if (MXCryptoError.UNKNOWN_DEVICES_CODE.equals(cryptoError.errcode)) {
                        CommonActivityUtils.displayUnknownDevicesDialog(mSession, VectorMemberDetailsActivity.this, (MXUsersDevicesMap<MXDeviceInfo>) cryptoError.mExceptionData, new VectorUnknownDevicesFragment.IUnknownDevicesSendAnywayListener() {
                            @Override
                            public void onSendAnyway() {
                                startCall(isVideo);
                            }
                        });

                        return;
                    }
                }

                CommonActivityUtils.displayToast(VectorMemberDetailsActivity.this, e.getLocalizedMessage());
                Log.e(LOG_TAG, "## startCall() failed " + e.getMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                CommonActivityUtils.displayToast(VectorMemberDetailsActivity.this, e.getLocalizedMessage());
                Log.e(LOG_TAG, "## startCall() failed " + e.getMessage());
            }
        });
    }

    /**
     * Check the permissions to establish an audio/video call.
     * If permissions are already granted, the call is established, otherwise
     * the permissions are checked against the system. Final result is provided in
     * {@link #onRequestPermissionsResult(int, String[], int[])}.
     *
     * @param aIsVideoCall true if video call, false if audio call
     */
    private void startCheckCallPermissions(boolean aIsVideoCall) {
        int requestCode = CommonActivityUtils.REQUEST_CODE_PERMISSION_AUDIO_IP_CALL;

        if (aIsVideoCall) {
            requestCode = CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL;
        }

        if (CommonActivityUtils.checkPermissions(requestCode, this)) {
            startCall(aIsVideoCall);
        }
    }

    @Override
    public void onRequestPermissionsResult(int aRequestCode, @NonNull String[] aPermissions, @NonNull int[] aGrantResults) {
        if (0 == aPermissions.length) {
            Log.e(LOG_TAG, "## onRequestPermissionsResult(): cancelled " + aRequestCode);
        } else if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_AUDIO_IP_CALL) {
            if (CommonActivityUtils.onPermissionResultAudioIpCall(this, aPermissions, aGrantResults)) {
                startCall(false);
            }
        } else if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL) {
            if (CommonActivityUtils.onPermissionResultVideoIpCall(this, aPermissions, aGrantResults)) {
                startCall(true);
            }
        }
    }

    @Override
    public void selectRoom(final Room aRoom) {
        VectorMemberDetailsActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> params = new HashMap<>();
                params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                params.put(VectorRoomActivity.EXTRA_ROOM_ID, aRoom.getRoomId());

                Log.d(LOG_TAG, "## selectRoom(): open the room " + aRoom.getRoomId());
                CommonActivityUtils.goToRoomPage(VectorMemberDetailsActivity.this, mSession, params);
            }
        });
    }

    @Override
    public void performItemAction(final int aActionType) {
        if (!mSession.isAlive()) {
            Log.e(LOG_TAG, "performItemAction : the session is not anymore valid");
            return;
        }

        final ArrayList<String> idsList = new ArrayList<>();

        switch (aActionType) {
            case ITEM_ACTION_DEVICES:
                refreshDevicesListView();
                break;

            case ITEM_ACTION_START_CHAT:
                Log.d(LOG_TAG, "## performItemAction(): Start new room - start chat");

                VectorMemberDetailsActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        enableProgressBarView(CommonActivityUtils.UTILS_DISPLAY_PROGRESS_BAR);
                        mSession.createDirectMessageRoom(mMemberId, mCreateDirectMessageCallBack);
                    }
                });
                break;

            case ITEM_ACTION_START_VIDEO_CALL:
            case ITEM_ACTION_START_VOICE_CALL:
                Log.d(LOG_TAG, "## performItemAction(): Start call");
                startCheckCallPermissions(ITEM_ACTION_START_VIDEO_CALL == aActionType);
                break;

            case ITEM_ACTION_INVITE:
                Log.d(LOG_TAG, "## performItemAction(): Invite");
                if (null != mRoom) {
                    enableProgressBarView(CommonActivityUtils.UTILS_DISPLAY_PROGRESS_BAR);
                    mRoom.invite(mRoomMember.getUserId(), mRoomActionsListener);
                }
                break;

            case ITEM_ACTION_LEAVE:
                Log.d(LOG_TAG, "## performItemAction(): Leave the room");
                if (null != mRoom) {
                    enableProgressBarView(CommonActivityUtils.UTILS_DISPLAY_PROGRESS_BAR);
                    mRoom.leave(mRoomActionsListener);
                }
                break;

            case ITEM_ACTION_SET_ADMIN:
                if (null != mRoom) {
                    updateUserPowerLevels(mMemberId, VECTOR_ROOM_ADMIN_LEVEL, mRoomActionsListener);
                    Log.d(LOG_TAG, "## performItemAction(): Make Admin");
                }
                break;

            case ITEM_ACTION_SET_MODERATOR:
                if (null != mRoom) {
                    updateUserPowerLevels(mMemberId, VECTOR_ROOM_MODERATOR_LEVEL, mRoomActionsListener);
                    Log.d(LOG_TAG, "## performItemAction(): Make moderator");
                }
                break;

            case ITEM_ACTION_SET_DEFAULT_POWER_LEVEL:
                if (null != mRoom) {
                    int defaultPowerLevel = 0;
                    PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

                    if (null != powerLevels) {
                        defaultPowerLevel = powerLevels.users_default;
                    }

                    updateUserPowerLevels(mMemberId, defaultPowerLevel, mRoomActionsListener);
                    Log.d(LOG_TAG, "## performItemAction(): default power level");
                }
                break;

            case ITEM_ACTION_BAN:
                if (null != mRoom) {
                    enableProgressBarView(CommonActivityUtils.UTILS_DISPLAY_PROGRESS_BAR);
                    mRoom.ban(mRoomMember.getUserId(), null, mRoomActionsListener);
                    Log.d(LOG_TAG, "## performItemAction(): Block (Ban)");
                }
                break;

            case ITEM_ACTION_UNBAN:
                if (null != mRoom) {
                    enableProgressBarView(CommonActivityUtils.UTILS_DISPLAY_PROGRESS_BAR);
                    mRoom.unban(mRoomMember.getUserId(), mRoomActionsListener);
                    Log.d(LOG_TAG, "## performItemAction(): Block (unban)");
                }
                break;

            case ITEM_ACTION_KICK:
                if (null != mRoom) {
                    enableProgressBarView(CommonActivityUtils.UTILS_DISPLAY_PROGRESS_BAR);
                    mRoom.kick(mRoomMember.getUserId(), mRoomActionsListener);
                    Log.d(LOG_TAG, "## performItemAction(): Kick");
                }
                break;

            case ITEM_ACTION_IGNORE: {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setMessage(getString(R.string.room_participants_action_ignore) + " ?");

                // set dialog message
                alertDialogBuilder
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        enableProgressBarView(CommonActivityUtils.UTILS_DISPLAY_PROGRESS_BAR);

                                        if (null != mRoomMember) {
                                            idsList.add(mRoomMember.getUserId());
                                        } else if (null != mMemberId) {
                                            idsList.add(mMemberId);
                                        }

                                        if (0 != idsList.size()) {
                                            enableProgressBarView(CommonActivityUtils.UTILS_DISPLAY_PROGRESS_BAR);
                                            mSession.ignoreUsers(idsList, new ApiCallback<Void>() {
                                                @Override
                                                public void onSuccess(Void info) {
                                                    // do not hide the progress bar to warn the user that something is pending
                                                    // an initial sync should be triggered
                                                }

                                                @Override
                                                public void onNetworkError(Exception e) {
                                                    mRoomActionsListener.onNetworkError(e);
                                                }

                                                @Override
                                                public void onMatrixError(MatrixError e) {
                                                    mRoomActionsListener.onMatrixError(e);
                                                }

                                                @Override
                                                public void onUnexpectedError(Exception e) {
                                                    mRoomActionsListener.onUnexpectedError(e);
                                                }
                                            });

                                            Log.d(LOG_TAG, "## performItemAction(): ignoreUsers");
                                        }
                                    }
                                })
                        .setNegativeButton(R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                    }
                                });

                // create alert dialog
                AlertDialog alertDialog = alertDialogBuilder.create();
                // show it
                alertDialog.show();

                break;
            }

            case ITEM_ACTION_UNIGNORE: {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setMessage(getString(R.string.room_participants_action_unignore) + " ?");

                // set dialog message
                alertDialogBuilder
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        enableProgressBarView(CommonActivityUtils.UTILS_DISPLAY_PROGRESS_BAR);

                                        if (null != mRoomMember) {
                                            idsList.add(mRoomMember.getUserId());
                                        } else if (null != mMemberId) {
                                            idsList.add(mMemberId);
                                        }

                                        if (0 != idsList.size()) {
                                            enableProgressBarView(CommonActivityUtils.UTILS_DISPLAY_PROGRESS_BAR);
                                            mSession.unIgnoreUsers(idsList, new ApiCallback<Void>() {
                                                @Override
                                                public void onSuccess(Void info) {
                                                    // do not hide the progress bar to warn the user that something is pending
                                                    // an initial sync should be triggered
                                                }

                                                @Override
                                                public void onNetworkError(Exception e) {
                                                    mRoomActionsListener.onNetworkError(e);
                                                }

                                                @Override
                                                public void onMatrixError(MatrixError e) {
                                                    mRoomActionsListener.onMatrixError(e);
                                                }

                                                @Override
                                                public void onUnexpectedError(Exception e) {
                                                    mRoomActionsListener.onUnexpectedError(e);
                                                }
                                            });

                                            Log.d(LOG_TAG, "## performItemAction(): unIgnoreUsers");
                                        }
                                    }
                                })
                        .setNegativeButton(R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                    }
                                });

                // create alert dialog
                AlertDialog alertDialog = alertDialogBuilder.create();
                // show it
                alertDialog.show();
                break;
            }
            case ITEM_ACTION_MENTION:
                String displayName = TextUtils.isEmpty(mRoomMember.displayname) ? mRoomMember.getUserId() : mRoomMember.displayname;

                // provide the mention name
                Intent intent = new Intent();
                intent.putExtra(RESULT_MENTION_ID, displayName);
                setResult(RESULT_OK, intent);
                finish();

                break;

            default:
                // unknown action
                Log.w(LOG_TAG, "## performItemAction(): unknown action type = " + aActionType);
                break;
        }
    }


    /**
     * Set the visibility of the devices list view. When the devices list is showing, the expandable
     * view will be hidden and vice-versa.
     *
     * @param aVisibilityToSet View.GONE to hide the view, View.VISIBLE to show
     */
    private void setScreenDevicesListVisibility(int aVisibilityToSet) {
        mDevicesListHeaderView.setVisibility(aVisibilityToSet);
        mDevicesListView.setVisibility(aVisibilityToSet);

        if (View.VISIBLE == aVisibilityToSet) {
            mExpandableListView.setVisibility(View.GONE);
        } else {
            mExpandableListView.setVisibility(View.VISIBLE);
        }
    }


    /**
     * Refresh the user devices list.
     */
    private void refreshDevicesListView() {
        // sanity check
        if ((null != mSession) && (null != mSession.getCrypto())) {

            // enable progress bar
            enableProgressBarView(CommonActivityUtils.UTILS_DISPLAY_PROGRESS_BAR);

            // force the refresh to ensure that the devices list is up-to-date
            mSession.getCrypto().getDeviceList().downloadKeys(Collections.singletonList(mMemberId), true, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
                // common default error handler
                private void onError(String aErrorMsg) {
                    Toast.makeText(VectorMemberDetailsActivity.this, aErrorMsg, Toast.LENGTH_LONG).show();
                    updateUi();
                }

                @Override
                public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                    final boolean isDevicesListPopulated = populateDevicesListAdapter(info);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            enableProgressBarView(CommonActivityUtils.UTILS_HIDE_PROGRESS_BAR);
                            if (isDevicesListPopulated) {
                                setScreenDevicesListVisibility(View.VISIBLE);
                            }
                        }
                    });
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (android.R.id.home == item.getItemId()) {
            if (View.VISIBLE == mDevicesListView.getVisibility()) {
                setScreenDevicesListVisibility(View.GONE);
            } else {
                // don't use the default parent activity defined in the manifest file.
                // close this activity when the home button is pressed
                onBackPressed();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Populate the devices list view adapter with the result of the downloadKeysForUsers().
     *
     * @param aDevicesInfoMap devices info response
     * @return true if the adapter data model is not empty, false otherwise
     */
    private boolean populateDevicesListAdapter(MXUsersDevicesMap<MXDeviceInfo> aDevicesInfoMap) {
        boolean isAdapterPopulated = false;
        if ((null != aDevicesInfoMap) && (null != mDevicesListViewAdapter)) {
            // reset the adapter list
            mDevicesListViewAdapter.clear();

            // check the member whose details are displayed, is present in the query response
            if (aDevicesInfoMap.getMap().containsKey(mMemberId)) {
                HashMap<String, MXDeviceInfo> memberDevicesInfo = new HashMap<>(aDevicesInfoMap.getMap().get(mMemberId));
                ArrayList<MXDeviceInfo> deviceInfoList = new ArrayList<>(memberDevicesInfo.values());

                mDevicesListViewAdapter.addAll(deviceInfoList);
            } else {
                Log.w(LOG_TAG, "## populateDevicesListAdapter(): invalid response - entry for " + mMemberId + " is missing");
            }
        }

        if ((null != mDevicesListViewAdapter) && (0 != mDevicesListViewAdapter.getCount())) {
            isAdapterPopulated = true;
        }

        return isAdapterPopulated;
    }

    /**
     * Update the power level of the user userId.
     * A confirmation dialog is displayed the new user level is the same as the self one.
     *
     * @param userId        the user id
     * @param newPowerLevel the new power level
     * @param callback      the callback with the created event
     */
    private void updateUserPowerLevels(final String userId, final int newPowerLevel, final ApiCallback<Void> callback) {
        PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();
        int currentSelfPowerLevel = 0;

        if (null != powerLevels) {
            currentSelfPowerLevel = powerLevels.getUserPowerLevel(mSession.getMyUserId());
        }

        if (currentSelfPowerLevel == newPowerLevel) {
            // ask to the user to confirmation thu upgrade.
            new AlertDialog.Builder(VectorMemberDetailsActivity.this)
                    .setMessage(R.string.room_participants_power_level_prompt)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            mRoom.updateUserPowerLevels(userId, newPowerLevel, callback);
                        }
                    })
                    .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .create()
                    .show();
        } else {
            mRoom.updateUserPowerLevels(userId, newPowerLevel, callback);
        }
    }

    /**
     * Search the first callable room with this member
     *
     * @return a valid Room instance, null if no room found
     */
    private Room searchCallableRoom() {
        if (!mSession.isAlive()) {
            Log.e(LOG_TAG, "searchCallableRoom : the session is not anymore valid");
            return null;
        }

        Collection<Room> rooms = mSession.getDataHandler().getStore().getRooms();

        for (Room room : rooms) {
            Collection<RoomMember> members = room.getMembers();

            if (members.size() == 2) {
                for (RoomMember member : members) {
                    if (member.getUserId().equals(mMemberId) && room.canPerformCall()) {
                        return room;
                    }
                }
            }
        }

        return null;
    }

    // *********************************************************************************************

    /**
     * @return the list of supported actions (ITEM_ACTION_XX)
     */
    private ArrayList<Integer> supportedActionsList() {
        ArrayList<Integer> supportedActions = new ArrayList<>();

        if (!mSession.isAlive()) {
            Log.e(LOG_TAG, "supportedActionsList : the session is not anymore valid");
            return supportedActions;
        }

        String selfUserId = mSession.getMyUserId();

        // Check user's power level before allowing an action (kick, ban, ...)
        PowerLevels powerLevels = null;
        int memberPowerLevel = 50;
        int selfPowerLevel = 50;
        int adminCount = 0;

        if (null != mRoom) {
            powerLevels = mRoom.getLiveState().getPowerLevels();
        }

        mMemberAvatarBadgeImageView.setVisibility(View.GONE);

        if (null != powerLevels) {
            // get power levels from myself and from the member of the room
            memberPowerLevel = powerLevels.getUserPowerLevel(mMemberId);
            selfPowerLevel = powerLevels.getUserPowerLevel(selfUserId);

            if (memberPowerLevel >= CommonActivityUtils.UTILS_POWER_LEVEL_ADMIN) {
                mMemberAvatarBadgeImageView.setVisibility(View.VISIBLE);
                mMemberAvatarBadgeImageView.setImageResource(R.drawable.admin_icon);
            } else if (memberPowerLevel >= CommonActivityUtils.UTILS_POWER_LEVEL_MODERATOR) {
                mMemberAvatarBadgeImageView.setVisibility(View.VISIBLE);
                mMemberAvatarBadgeImageView.setImageResource(R.drawable.mod_icon);
            }

            // compute the number of administrators
            for (Integer powerLevel : powerLevels.users.values()) {
                if ((null != powerLevel) && (powerLevel >= CommonActivityUtils.UTILS_POWER_LEVEL_ADMIN)) {
                    adminCount++;
                }
            }
        }

        // Check user's power level before allowing an action (kick, ban, ...)
        if (TextUtils.equals(mMemberId, selfUserId)) {
            if (null != mRoom) {
                supportedActions.add(ITEM_ACTION_LEAVE);
            }

            // Check whether the user is admin (in this case he may reduce his power level to become moderator or less, EXCEPT if he is the only admin).
            if ((adminCount > 1) && (null != powerLevels) && (selfPowerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS))) {
                // Check whether the user is admin (in this case he may reduce his power level to become moderator).
                if (selfPowerLevel >= VECTOR_ROOM_ADMIN_LEVEL) {
                    supportedActions.add(ITEM_ACTION_SET_MODERATOR);
                }

                // Check whether the user is moderator (in this case he may reduce his power level to become normal user).
                if (selfPowerLevel >= VECTOR_ROOM_MODERATOR_LEVEL) {
                    supportedActions.add(ITEM_ACTION_SET_DEFAULT_POWER_LEVEL);
                }
            }
        } else if (null != mRoomMember) {
            // 1:1 call
            if ((null != searchCallableRoom()) && mSession.isVoipCallSupported() && (null == CallsManager.getSharedInstance().getActiveCall())) {
                // Offer voip call options
                supportedActions.add(ITEM_ACTION_START_VOICE_CALL);
                supportedActions.add(ITEM_ACTION_START_VIDEO_CALL);
            }

            String membership = mRoomMember.membership;

            if (null != powerLevels) {
                // Consider membership of the selected member
                if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_INVITE) || TextUtils.equals(membership, RoomMember.MEMBERSHIP_JOIN)) {

                    // update power level
                    if ((selfPowerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS)) && (selfPowerLevel > memberPowerLevel)) {

                        // Check whether user is admin
                        if (selfPowerLevel >= VECTOR_ROOM_ADMIN_LEVEL) {
                            supportedActions.add(ITEM_ACTION_SET_ADMIN);
                        }

                        // Check whether the member may become moderator
                        if ((selfPowerLevel >= VECTOR_ROOM_MODERATOR_LEVEL) && (memberPowerLevel < VECTOR_ROOM_MODERATOR_LEVEL)) {
                            supportedActions.add(ITEM_ACTION_SET_MODERATOR);
                        }

                        if (memberPowerLevel >= ITEM_ACTION_SET_MODERATOR) {
                            supportedActions.add(ITEM_ACTION_SET_DEFAULT_POWER_LEVEL);
                        }
                    }

                    // Check conditions to be able to kick someone
                    if ((selfPowerLevel >= powerLevels.kick) && (selfPowerLevel > memberPowerLevel)) {
                        supportedActions.add(ITEM_ACTION_KICK);
                    }

                    // Check conditions to be able to ban someone
                    if ((selfPowerLevel >= powerLevels.ban) && (selfPowerLevel > memberPowerLevel)) {
                        supportedActions.add(ITEM_ACTION_BAN);
                    }

                    if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_JOIN)) {
                        // if the member is already admin, then disable the action
                        int maxPowerLevel = CommonActivityUtils.getRoomMaxPowerLevel(mRoom);

                        if ((selfPowerLevel == maxPowerLevel) && (memberPowerLevel != maxPowerLevel)) {
                            supportedActions.add(ITEM_ACTION_SET_ADMIN);
                        }

                        if (!mSession.isUserIgnored(mRoomMember.getUserId())) {
                            supportedActions.add(ITEM_ACTION_IGNORE);
                        } else {
                            supportedActions.add(ITEM_ACTION_UNIGNORE);
                        }
                    }
                } else if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_LEAVE)) {
                    // Check conditions to be able to invite someone
                    if (selfPowerLevel >= powerLevels.invite) {
                        supportedActions.add(ITEM_ACTION_INVITE);
                    }
                    // Check conditions to be able to ban someone
                    if ((selfPowerLevel >= powerLevels.ban) && (selfPowerLevel > memberPowerLevel)) {
                        supportedActions.add(ITEM_ACTION_BAN);
                    }
                } else if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_BAN)) {
                    // Check conditions to be able to ban someone
                    if ((selfPowerLevel >= powerLevels.ban) && (selfPowerLevel > memberPowerLevel)) {
                        supportedActions.add(ITEM_ACTION_UNBAN);
                    }
                }
            }

            // copy the user displayname in the VectorRoomActivity EditText
            // do not show "mention" fro
            if (!TextUtils.equals(mRoomMember.membership, mSession.getMyUserId())) {
                supportedActions.add(ITEM_ACTION_MENTION);
            }
        } else if (mUser != null) {
            if (!mSession.isUserIgnored(mMemberId)) {
                supportedActions.add(ITEM_ACTION_IGNORE);
            } else {
                supportedActions.add(ITEM_ACTION_UNIGNORE);
            }
        }

        return supportedActions;
    }

    /**
     * Update items actions list view.
     * The item actions present in the list view are updated dynamically
     * according to the power levels.
     */
    private void updateAdapterListViewItems() {
        int imageResource;
        String actionText;

        // sanity check
        if ((null == mListViewAdapter)) {
            Log.w(LOG_TAG, "## updateListViewItemsContent(): list view adapter not initialized");
        } else {
            // reset action lists & allocate items list
            List<VectorMemberDetailsAdapter.AdapterMemberActionItems> uncategorizedActions = new ArrayList<>();
            List<VectorMemberDetailsAdapter.AdapterMemberActionItems> adminActions = new ArrayList<>();
            List<VectorMemberDetailsAdapter.AdapterMemberActionItems> callActions = new ArrayList<>();
            List<VectorMemberDetailsAdapter.AdapterMemberActionItems> directMessagesActions = new ArrayList<>();
            List<VectorMemberDetailsAdapter.AdapterMemberActionItems> devicesActions = new ArrayList<>();

            ArrayList<Integer> supportedActionsList = supportedActionsList();

            if (supportedActionsList.indexOf(ITEM_ACTION_START_VOICE_CALL) >= 0) {
                imageResource = R.drawable.voice_call_black;
                actionText = getResources().getString(R.string.start_voice_call);
                callActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_START_VOICE_CALL));
            }

            if (supportedActionsList.indexOf(ITEM_ACTION_START_VIDEO_CALL) >= 0) {
                imageResource = R.drawable.video_call_black;
                actionText = getResources().getString(R.string.start_video_call);
                callActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_START_VIDEO_CALL));
            }

            if (supportedActionsList.indexOf(ITEM_ACTION_INVITE) >= 0) {
                imageResource = R.drawable.ic_person_add_black;
                actionText = getResources().getString(R.string.room_participants_action_invite);
                adminActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_INVITE));
            }

            // build the leave item
            if (supportedActionsList.indexOf(ITEM_ACTION_LEAVE) >= 0) {
                imageResource = R.drawable.vector_leave_room_black;
                actionText = getResources().getString(R.string.room_participants_action_leave);
                uncategorizedActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_LEAVE));
            }

            // build the "make admin" item
            if (supportedActionsList.indexOf(ITEM_ACTION_SET_ADMIN) >= 0) {
                imageResource = R.drawable.ic_verified_user_black;
                actionText = getResources().getString(R.string.room_participants_action_set_admin);
                adminActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_SET_ADMIN));
            }

            // build the "moderator" item
            if (supportedActionsList.indexOf(ITEM_ACTION_SET_MODERATOR) >= 0) {
                imageResource = R.drawable.ic_verified_user_black;
                actionText = getResources().getString(R.string.room_participants_action_set_moderator);
                adminActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_SET_MODERATOR));
            }

            // build the "default" item
            if (supportedActionsList.indexOf(ITEM_ACTION_SET_DEFAULT_POWER_LEVEL) >= 0) {
                imageResource = R.drawable.ic_verified_user_black;
                actionText = getResources().getString(R.string.room_participants_action_set_default_power_level);
                adminActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_SET_DEFAULT_POWER_LEVEL));
            }

            // build the "remove from" item (ban)
            if (supportedActionsList.indexOf(ITEM_ACTION_KICK) >= 0) {
                imageResource = R.drawable.ic_remove_circle_outline_red;
                actionText = getResources().getString(R.string.room_participants_action_remove);
                adminActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_KICK));
            }

            // build the "block" item (block)
            if (supportedActionsList.indexOf(ITEM_ACTION_BAN) >= 0) {
                imageResource = R.drawable.ic_block_black;
                actionText = getResources().getString(R.string.room_participants_action_ban);
                adminActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_BAN));
            }

            // build the "unblock" item (unblock)
            if (supportedActionsList.indexOf(ITEM_ACTION_UNBAN) >= 0) {
                imageResource = R.drawable.ic_block_black;
                actionText = getResources().getString(R.string.room_participants_action_unban);
                adminActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_UNBAN));
            }

            // build the "ignore" item
            if (supportedActionsList.indexOf(ITEM_ACTION_IGNORE) >= 0) {
                imageResource = R.drawable.ic_person_outline_black;
                actionText = getResources().getString(R.string.room_participants_action_ignore);
                uncategorizedActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_IGNORE));
            }

            // build the "unignore" item
            if (supportedActionsList.indexOf(ITEM_ACTION_UNIGNORE) >= 0) {
                imageResource = R.drawable.ic_person_black;
                actionText = getResources().getString(R.string.room_participants_action_unignore);
                uncategorizedActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_UNIGNORE));
            }

            // build the "mention" item
            if (supportedActionsList.indexOf(ITEM_ACTION_MENTION) >= 0) {
                imageResource = R.drawable.ic_comment_black;
                actionText = getResources().getString(R.string.room_participants_action_mention);
                uncategorizedActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_MENTION));
            }

            mListViewAdapter.setUncategorizedActionsList(uncategorizedActions);
            mListViewAdapter.setAdminActionsList(adminActions);
            mListViewAdapter.setCallActionsList(callActions);

            // devices
            if (mUser != null) {
                imageResource = R.drawable.ic_devices_info;
                actionText = getResources().getString(R.string.room_participants_action_devices_list);
                devicesActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_DEVICES));
                mListViewAdapter.setDevicesActionsList(devicesActions);
            }

            // direct chats management

            // list other direct rooms
            List<String> roomIds = mSession.getDirectChatRoomIdsList(mMemberId);
            for (String roomId : roomIds) {
                Room room = mSession.getDataHandler().getRoom(roomId);
                if (null != room) {
                    directMessagesActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(room));
                }
            }

            imageResource = R.drawable.ic_add_black;
            actionText = getResources().getString(R.string.start_new_chat);
            directMessagesActions.add(new VectorMemberDetailsAdapter.AdapterMemberActionItems(imageResource, actionText, ITEM_ACTION_START_CHAT));

            mListViewAdapter.setDirectCallsActionsList(directMessagesActions);
            mListViewAdapter.notifyDataSetChanged();

            mExpandableListView.post(new Runnable() {
                @Override
                public void run() {
                    int count = mListViewAdapter.getGroupCount();

                    for (int pos = 0; pos < count; pos++) {
                        mExpandableListView.expandGroup(pos);
                    }
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "Restart the application");
            CommonActivityUtils.restartApp(this);
            return;
        }

        if (CommonActivityUtils.isGoingToSplash(this)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen");
            return;
        }

        // retrieve the parameters contained extras and setup other
        // internal state values such as the; session, room..
        if (!initContextStateValues()) {
            // init failed, just return
            Log.e(LOG_TAG, "## onCreate(): Parameters init failure");
            finish();
        } else {
            // check if the user is a member of the room
            checkRoomMemberStatus();

            // setup UI view and bind the widgets
            setContentView(R.layout.activity_member_details);

            // use a toolbar instead of the actionbar
            // to be able to display a large header
            android.support.v7.widget.Toolbar toolbar = findViewById(R.id.member_details_toolbar);
            this.setSupportActionBar(toolbar);

            if (null != getSupportActionBar()) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }

            mMemberAvatarImageView = findViewById(R.id.avatar_img);
            mMemberAvatarBadgeImageView = findViewById(R.id.member_avatar_badge);

            mFullMemberAvatarImageView = findViewById(R.id.member_details_fullscreen_avatar_image_view);
            mFullMemberAvatarLayout = findViewById(R.id.member_details_fullscreen_avatar_layout);

            mMemberNameTextView = findViewById(R.id.member_details_name);
            mPresenceTextView = findViewById(R.id.member_details_presence);
            mProgressBarView = findViewById(R.id.member_details_list_view_progress_bar);

            // setup the devices list view
            mDevicesListView = findViewById(R.id.member_details_devices_list_view);
            mDevicesListViewAdapter = new VectorMemberDetailsDevicesAdapter(this, R.layout.adapter_item_member_details_devices, mSession);
            mDevicesListViewAdapter.setDevicesAdapterListener(this);
            mDevicesListView.setAdapter(mDevicesListViewAdapter);
            // devices header row
            mDevicesListHeaderView = findViewById(R.id.devices_header_view);
            TextView devicesHeaderTitleTxtView = mDevicesListHeaderView.findViewById(R.id.heading);
            if (null != devicesHeaderTitleTxtView) {
                devicesHeaderTitleTxtView.setText(R.string.room_participants_header_devices);
            }

            // setup the expandable list view
            mListViewAdapter = new VectorMemberDetailsAdapter(this, mSession, R.layout.vector_adapter_member_details_items, R.layout.adapter_item_vector_recent_header);
            mListViewAdapter.setActionListener(this);

            mExpandableListView = findViewById(R.id.member_details_actions_list_view);
            // the chevron is managed in the header view
            mExpandableListView.setGroupIndicator(null);
            mExpandableListView.setAdapter(mListViewAdapter);

            mExpandableListView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
                @Override
                public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
                    // the groups are always expanded
                    return true;
                }
            });


            // when clicking on the username
            // switch member name <-> member id
            mMemberNameTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    User user = mSession.getDataHandler().getUser(mMemberId);

                    if (TextUtils.equals(mMemberNameTextView.getText(), mMemberId)) {
                        if ((null != user) && !TextUtils.isEmpty(user.displayname)) {
                            mMemberNameTextView.setText(user.displayname);
                        }
                    } else {
                        mMemberNameTextView.setText(mMemberId);
                    }
                }
            });

            // long tap : copy to the clipboard
            mMemberNameTextView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    VectorUtils.copyToClipboard(VectorMemberDetailsActivity.this, mMemberNameTextView.getText());
                    return true;
                }
            });

            mMemberAvatarImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    displayFullScreenAvatar();
                }
            });

            mFullMemberAvatarImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideFullScreenAvatar();
                }
            });

            mFullMemberAvatarLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideFullScreenAvatar();
                }
            });

            // update the UI
            updateUi();

            if ((null != savedInstanceState) && savedInstanceState.getBoolean(AVATAR_FULLSCREEN_MODE, false)) {
                displayFullScreenAvatar();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((KeyEvent.KEYCODE_BACK == keyCode)) {
            if (View.VISIBLE == mFullMemberAvatarLayout.getVisibility()) {
                hideFullScreenAvatar();
                return true;
            } else if ((View.VISIBLE == mDevicesListView.getVisibility())) {
                setScreenDevicesListVisibility(View.GONE);
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(AVATAR_FULLSCREEN_MODE, View.VISIBLE == mFullMemberAvatarLayout.getVisibility());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if (savedInstanceState.getBoolean(AVATAR_FULLSCREEN_MODE, false)) {
            displayFullScreenAvatar();
        }
    }

    /**
     * Hide the fullscreen avatar.
     */
    private void hideFullScreenAvatar() {
        mFullMemberAvatarLayout.setVisibility(View.GONE);
    }

    /**
     * Display the user/member avatar in fullscreen.
     */
    private void displayFullScreenAvatar() {
        String avatarUrl = null;
        String userId = mMemberId;

        if (null != mRoomMember) {
            avatarUrl = mRoomMember.getAvatarUrl();

            if (TextUtils.isEmpty(avatarUrl)) {
                userId = mRoomMember.getUserId();
            }
        }

        if (TextUtils.isEmpty(avatarUrl) && !TextUtils.isEmpty(userId)) {
            if (null != mUser) {
                avatarUrl = mUser.getAvatarUrl();
            }
        }

        if (!TextUtils.isEmpty(avatarUrl)) {
            mFullMemberAvatarLayout.setVisibility(View.VISIBLE);
            mSession.getMediasCache().loadBitmap(mSession.getHomeServerConfig(), mFullMemberAvatarImageView, avatarUrl, 0, ExifInterface.ORIENTATION_UNDEFINED, null, null);
        }
    }

    /**
     * Retrieve all the state values required to run the activity.
     * If values are not provided in the intent or are some are
     * null, then the activity can not continue to run and must be finished
     *
     * @return true if init succeed, false otherwise
     */
    private boolean initContextStateValues() {
        Intent intent = getIntent();
        boolean isParamInitSucceed = false;

        if (null != intent) {
            if (null == (mMemberId = intent.getStringExtra(EXTRA_MEMBER_ID))) {
                Log.e(LOG_TAG, "member ID missing in extra");
                return false;
            } else if (null == (mSession = getSession(this, intent))) {
                Log.e(LOG_TAG, "Invalid session");
                return false;
            }

            int storeIndex = intent.getIntExtra(EXTRA_STORE_ID, -1);
            IMXStore store;

            if (storeIndex >= 0) {
                store = Matrix.getInstance(this).getTmpStore(storeIndex);
            } else {
                store = mSession.getDataHandler().getStore();

                if (refreshUser()) {
                    intent.removeExtra(EXTRA_ROOM_ID);
                }
            }

            String roomId = intent.getStringExtra(EXTRA_ROOM_ID);

            if ((null != roomId) && (null == (mRoom = store.getRoom(roomId)))) {
                Log.e(LOG_TAG, "The room is not found");
            } else {
                // Everything is OK
                Log.d(LOG_TAG, "Parameters init succeed");
                isParamInitSucceed = true;
            }
        }

        return isParamInitSucceed;
    }

    /**
     * Search if the member is present in the list of the members of
     * the room
     *
     * @return true if member was found in the room , false otherwise
     */
    private boolean checkRoomMemberStatus() {
        mRoomMember = null;

        if (null != mRoom) {
            // find out the room member
            Collection<RoomMember> members = mRoom.getMembers();
            for (RoomMember member : members) {
                if (member.getUserId().equals(mMemberId)) {
                    mRoomMember = member;
                    break;
                }
            }
        }

        return (null == mRoom) || (null != mRoomMember);
    }

    /**
     * Refresh the user information
     *
     * @return true if the user is not a known one
     */
    private boolean refreshUser() {
        mUser = mSession.getDataHandler().getStore().getUser(mMemberId);

        // build a tmp user from the data provided as parameters
        if (null == mUser) {
            mUser = new User();
            mUser.user_id = mMemberId;
            mUser.displayname = getIntent().getStringExtra(EXTRA_MEMBER_DISPLAY_NAME);

            if (TextUtils.isEmpty(mUser.displayname)) {
                mUser.displayname = mMemberId;
            }

            mUser.avatar_url = getIntent().getStringExtra(EXTRA_MEMBER_AVATAR_URL);

            return true;
        }

        return false;
    }

    /**
     * Update the UI
     */
    private void updateUi() {
        if (!mSession.isAlive()) {
            Log.e(LOG_TAG, "updateUi : the session is not anymore valid");
            return;
        }

        if (null != mMemberNameTextView) {
            if ((null != mRoomMember) && !TextUtils.isEmpty(mRoomMember.displayname)) {
                mMemberNameTextView.setText(mRoomMember.displayname);
            } else {
                refreshUser();
                mMemberNameTextView.setText(mUser.displayname);
            }

            // do not display the activity name in the action bar
            setTitle("");
        }

        // disable the progress bar
        enableProgressBarView(CommonActivityUtils.UTILS_HIDE_PROGRESS_BAR);

        // UI updates
        updateMemberAvatarUi();
        updatePresenceInfoUi();

        // Update adapter list view:
        // notify the list view to update the items that could
        // have changed according to the new rights of the member
        updateAdapterListViewItems();
        if (null != mListViewAdapter) {
            mListViewAdapter.notifyDataSetChanged();
        }
    }

    private void updatePresenceInfoUi() {
        // sanity check
        if (null != mPresenceTextView) {
            mPresenceTextView.setText(VectorUtils.getUserOnlineStatus(this, mSession, mMemberId, new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    mPresenceTextView.setText(VectorUtils.getUserOnlineStatus(VectorMemberDetailsActivity.this, mSession, mMemberId, null));
                }
            }));
        }
    }

    /**
     * update the profile avatar
     */
    private void updateMemberAvatarUi() {
        if (null != mMemberAvatarImageView) {
            // use the room member if it exists
            if (null != mRoomMember) {
                String displayname = mRoomMember.displayname;
                String avatarUrl = mRoomMember.getAvatarUrl();

                // if there is no avatar or displayname , try to find one from the known user
                // it is always better than the vector avatar or the matrid id.
                if (TextUtils.isEmpty(avatarUrl) || TextUtils.isEmpty(displayname)) {
                    if (null != mUser) {
                        if (TextUtils.isEmpty(avatarUrl)) {
                            avatarUrl = mUser.avatar_url;
                        }

                        if (TextUtils.isEmpty(displayname)) {
                            displayname = mUser.displayname;
                        }
                    }
                }

                VectorUtils.loadUserAvatar(this, mSession, mMemberAvatarImageView, avatarUrl, mRoomMember.getUserId(), displayname);
            } else {
                // use the user if it is defined
                if (null != mUser) {
                    VectorUtils.loadUserAvatar(this, mSession, mMemberAvatarImageView, mUser);
                } else {
                    // default avatar
                    VectorUtils.loadUserAvatar(this, mSession, mMemberAvatarImageView, null, mMemberId, mMemberId);
                }
            }
        }
    }

    /**
     * Helper method to enable/disable the progress bar view used when a
     * remote server action is on progress.
     *
     * @param aIsProgressBarDisplayed true to show the progress bar screen, false to hide it
     */
    private void enableProgressBarView(boolean aIsProgressBarDisplayed) {
        if (null != mProgressBarView) {
            mProgressBarView.setVisibility(aIsProgressBarDisplayed ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (null != mSession) {
            if (null != mRoom) {
                mRoom.removeEventListener(mLiveEventsListener);
            }

            mSession.getDataHandler().removeListener(mPresenceEventsListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (null != mSession) {
            if (null != mRoom) {
                mRoom.addEventListener(mLiveEventsListener);
            }
            mSession.getDataHandler().addListener(mPresenceEventsListener);

            updateAdapterListViewItems();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (null != mRoom) {
            mRoom.removeEventListener(mLiveEventsListener);
        }

        if (null != mSession) {
            mSession.getDataHandler().removeListener(mPresenceEventsListener);
        }
    }

    // ********* IDevicesAdapterListener implementation *********

    private final ApiCallback<Void> mDevicesVerificationCallback = new ApiCallback<Void>() {
        @Override
        public void onSuccess(Void info) {
            mDevicesListViewAdapter.notifyDataSetChanged();
        }

        @Override
        public void onNetworkError(Exception e) {
            mDevicesListViewAdapter.notifyDataSetChanged();
        }

        @Override
        public void onMatrixError(MatrixError e) {
            mDevicesListViewAdapter.notifyDataSetChanged();
        }

        @Override
        public void onUnexpectedError(Exception e) {
            mDevicesListViewAdapter.notifyDataSetChanged();
        }
    };

    @Override
    public void OnVerifyDeviceClick(MXDeviceInfo aDeviceInfo) {
        switch (aDeviceInfo.mVerified) {
            case MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED:
                mSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED, aDeviceInfo.deviceId, mMemberId, mDevicesVerificationCallback);
                break;

            case MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED:
            default: // Blocked
                CommonActivityUtils.displayDeviceVerificationDialog(aDeviceInfo, mMemberId, mSession, this, mDevicesVerificationCallback);
                break;
        }
    }

    @Override
    public void OnBlockDeviceClick(MXDeviceInfo aDeviceInfo) {
        if (aDeviceInfo.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED) {
            mSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED, aDeviceInfo.deviceId, aDeviceInfo.userId, mDevicesVerificationCallback);
        } else {
            mSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, aDeviceInfo.deviceId, aDeviceInfo.userId, mDevicesVerificationCallback);
        }

        mDevicesListViewAdapter.notifyDataSetChanged();
    }
    // ***********************************************************
}
