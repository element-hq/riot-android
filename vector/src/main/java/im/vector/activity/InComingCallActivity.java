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

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;

import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;

import im.vector.Matrix;
import im.vector.R;
import im.vector.util.ThemeUtils;
import im.vector.util.VectorCallSoundManager;
import im.vector.util.VectorUtils;

/**
 * InComingCallActivity is Dialog Activity, displayed when an incoming call (audio or a video) over IP
 * is received by the user. The user is asked to accept or ignore.
 */
public class InComingCallActivity extends VectorAppCompatActivity {
    private static final String LOG_TAG = "InComingCallActivity";

    // only one instance of this class should be displayed
    // TODO find how to avoid multiple creations
    private static InComingCallActivity sharedInstance = null;

    private ImageView mCallingUserAvatarView;
    private TextView mRoomNameTextView;
    private Button mIgnoreCallButton;
    private Button mAcceptCallButton;
    private String mCallId;
    private String mMatrixId;
    private MXSession mSession;
    private IMXCall mMxCall;

    private final IMXCall.MXCallListener mMxCallListener = new IMXCall.MXCallListener() {
        @Override
        public void onStateDidChange(String state) {
            Log.d(LOG_TAG,"## onStateDidChange(): state="+state);
        }

        @Override
        public void onCallError(String aErrorMsg) {
            Log.d(LOG_TAG, "## dispatchOnCallError(): error=" + aErrorMsg);

            if (IMXCall.CALL_ERROR_USER_NOT_RESPONDING.equals(aErrorMsg)) {
                CommonActivityUtils.displayToastOnUiThread(InComingCallActivity.this, InComingCallActivity.this.getString(R.string.call_error_user_not_responding));
            } else if (IMXCall.CALL_ERROR_ICE_FAILED.equals(aErrorMsg)) {
                CommonActivityUtils.displayToastOnUiThread(InComingCallActivity.this, InComingCallActivity.this.getString(R.string.call_error_ice_failed));
            } else if (IMXCall.CALL_ERROR_CAMERA_INIT_FAILED.equals(aErrorMsg)) {
                CommonActivityUtils.displayToastOnUiThread(InComingCallActivity.this, InComingCallActivity.this.getString(R.string.call_error_camera_init_failed));
            } else {
                CommonActivityUtils.displayToastOnUiThread(InComingCallActivity.this, aErrorMsg);
            }
        }

        @Override
        public void onViewLoading(View callView) {
            Log.d(LOG_TAG, "## onViewLoading():");
        }

        @Override
        public void onViewReady() {
            Log.d(LOG_TAG, "## onViewReady(): ");

            if(null != mMxCall) {
                if (mMxCall.isIncoming()) {
                    mMxCall.launchIncomingCall(null);
                } else {
                    Log.d(LOG_TAG, "## onViewReady(): not incoming call");
                }
            }
        }

        /**
         * The call was answered on another device
         */
        @Override
        public void onCallAnsweredElsewhere() {
            InComingCallActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "## onCallAnsweredElsewhere(): finish activity");
                    CommonActivityUtils.displayToastOnUiThread(InComingCallActivity.this, InComingCallActivity.this.getString(R.string.call_error_answered_elsewhere));
                    InComingCallActivity.this.finish();
                }
            });
        }

        @Override
        public void onCallEnd(final int aReasonId) {
            InComingCallActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "## onCallEnd(): finish activity");
                    CommonActivityUtils.processEndCallInfo(InComingCallActivity.this, aReasonId);
                    InComingCallActivity.this.finish();
                }
            });
        }

        @Override
        public void onPreviewSizeChanged(int width, int height) {
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setFinishOnTouchOutside(false);

        setContentView(R.layout.vector_incoming_call_dialog);

        // retrieve intent extras
        final Intent intent = getIntent();
        if (intent == null) {
            Log.e(LOG_TAG, "## onCreate(): intent missing");
            finish();
        } else {
            mMatrixId = intent.getStringExtra(VectorCallViewActivity.EXTRA_MATRIX_ID);
            mCallId = intent.getStringExtra(VectorCallViewActivity.EXTRA_CALL_ID);

            if(null == mMatrixId){
                Log.e(LOG_TAG, "## onCreate(): matrix ID is missing in extras");
                finish();
            } else if(null == mCallId){
                Log.e(LOG_TAG, "## onCreate(): call ID is missing in extras");
                finish();
            } else if(null == (mSession = Matrix.getInstance(getApplicationContext()).getSession(mMatrixId))){
                Log.e(LOG_TAG, "## onCreate(): invalid session (null)");
                finish();
            } else if(null == (mMxCall = mSession.mCallsManager.getCallWithCallId(mCallId))){
                Log.e(LOG_TAG, "## onCreate(): invalid call ID (null)");
                // assume that the user tap on a staled notification
                if (VectorCallSoundManager.isRinging()) {
                    Log.e(LOG_TAG, "## onCreate(): the device was ringing so assume that the call " + mCallId + " does not exist anymore");
                    VectorCallSoundManager.stopRinging();
                }
                finish();
            } else {
                synchronized (LOG_TAG) {
                    if (null != sharedInstance) {
                        sharedInstance.finish();
                        Log.e(LOG_TAG, "## onCreate(): kill previous instance");
                    }

                    sharedInstance = this;
                }

                // UI widgets binding
                mCallingUserAvatarView = (ImageView) findViewById(R.id.avatar_img);
                mRoomNameTextView = (TextView) findViewById(R.id.room_name);
                mAcceptCallButton = (Button) findViewById(R.id.button_incoming_call_accept);
                mIgnoreCallButton = (Button) findViewById(R.id.button_incoming_call_ignore);

                mCallingUserAvatarView.post(new Runnable() {
                    @Override
                    public void run() {
                        // set the avatar
                        VectorUtils.loadCallAvatar(InComingCallActivity.this, mSession, mCallingUserAvatarView, mMxCall.getRoom());
                    }
                });

                // set the room display name
                mRoomNameTextView.setText(VectorUtils.getCallingRoomDisplayName(this, mSession, mMxCall.getRoom()));

                // set button handlers
                mIgnoreCallButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onHangUp();
                        finish();
                    }
                });

                // the user can only accept if the dedicated permissions are granted
                mAcceptCallButton.setVisibility(CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL, InComingCallActivity.this) ? View.VISIBLE : View.INVISIBLE);

                mAcceptCallButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startCallViewActivity(intent);
                        finish();
                    }
                });

                // create the call view to enable mMxCallListener being used,
                // otherwise call API is not enabled
                mMxCall.createCallView();

                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        CommonActivityUtils.displayUnknownDevicesDialog(mSession, InComingCallActivity.this, (MXUsersDevicesMap<MXDeviceInfo>)intent.getSerializableExtra(VectorCallViewActivity.EXTRA_UNKNOWN_DEVICES), null);
                    }
                });
            }
        }
    }

    @Override
    public  void finish() {
        super.finish();
        synchronized (LOG_TAG) {
            if (this == sharedInstance) {
                sharedInstance = null;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mMxCall = mSession.mCallsManager.getCallWithCallId(mCallId);

        if (null != mMxCall) {
            String callState = mMxCall.getCallState();
            boolean isWaitingUserResponse =
                    TextUtils.equals(callState, IMXCall.CALL_STATE_CREATING_CALL_VIEW) ||
                            TextUtils.equals(callState, IMXCall.CALL_STATE_FLEDGLING) ||
                            TextUtils.equals(callState, IMXCall.CALL_STATE_WAIT_LOCAL_MEDIA) ||
                            TextUtils.equals(callState, IMXCall.CALL_STATE_WAIT_CREATE_OFFER) ||
                            TextUtils.equals(callState, IMXCall.CALL_STATE_RINGING);


            if (isWaitingUserResponse) {
                mMxCall.onResume();
                mMxCall.addListener(mMxCallListener);
            } else {
                Log.d(LOG_TAG, "## onResume : the call has already been managed.");
                finish();
            }
        } else {
            Log.d(LOG_TAG, "## onResume : the call does not exist anymore");
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int aRequestCode, @NonNull String[] aPermissions, @NonNull int[] aGrantResults) {
        if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL) {
            // the user can only accept if the dedicated permissions are granted
            mAcceptCallButton.setVisibility(CommonActivityUtils.onPermissionResultVideoIpCall(this, aPermissions, aGrantResults) ? View.VISIBLE : View.INVISIBLE);

            mMxCall = mSession.mCallsManager.getCallWithCallId(mCallId);
            if (null == mMxCall) {
                finish();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (null != mMxCall) {
            mMxCall.onPause();
            mMxCall.removeListener(mMxCallListener);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (null != mMxCall) {
            mMxCall.removeListener(mMxCallListener);
        }
    }

    @Override
    public void onBackPressed() {
        onHangUp();
    }

    /**
     * Helper method: starts the CallViewActivity in auto accept mode.
     * The extras provided in  are copied to
     * the CallViewActivity and {@link VectorCallViewActivity#EXTRA_AUTO_ACCEPT} is set to true.
     * @param aSourceIntent the intent whose extras are transmitted
     */
    private void startCallViewActivity(final Intent aSourceIntent) {
        Intent intent = new Intent(this, VectorCallViewActivity.class);
        Bundle receivedData = aSourceIntent.getExtras();
        intent.putExtras(receivedData);
        intent.putExtra(VectorCallViewActivity.EXTRA_AUTO_ACCEPT, true);
        startActivity(intent);
    }

    /**
     * Hangup the call.
     */
    private void onHangUp() {
        if (null != mMxCall) {
            mMxCall.hangup("");
        }
    }

}
