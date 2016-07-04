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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.rest.model.RoomMember;

import im.vector.Matrix;
import im.vector.R;
import im.vector.util.VectorUtils;

/**
 * InComingCallActivity is Dialog Activity, displayed when an incoming call (audio or a video) over IP
 * is received by the user. The user is asked to accept or ignore.
 */
public class InComingCallActivity extends Activity { // do NOT extend from UC*Activity, we do not want to login on this screen!
    private static final String LOG_TAG = "InComingCallActivity";

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

        /**
         * Display the error messages
         * @param aMsgToDisplay the toast message
         */
        private void showToast(final String aMsgToDisplay)  {
            InComingCallActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    CommonActivityUtils.displayToast(InComingCallActivity.this.getApplicationContext(),aMsgToDisplay);
                }
            });
        }

        @Override
        public void onCallError(String aErrorMsg) {
            Log.d(LOG_TAG, "## onCallError(): error=" + aErrorMsg);

            if (IMXCall.CALL_ERROR_USER_NOT_RESPONDING.equals(aErrorMsg)) {
                showToast(InComingCallActivity.this.getString(R.string.call_error_user_not_responding));
            } else if (IMXCall.CALL_ERROR_ICE_FAILED.equals(aErrorMsg)) {
                showToast(InComingCallActivity.this.getString(R.string.call_error_ice_failed));
            } else if (IMXCall.CALL_ERROR_CAMERA_INIT_FAILED.equals(aErrorMsg)) {
                showToast(InComingCallActivity.this.getString(R.string.call_error_camera_init_failed));
            } else {
                showToast(aErrorMsg);
            }
        }

        @Override
        public void onViewLoading(View callview) {
            Log.d(LOG_TAG, "## onViewLoading():");
        }

        @Override
        public void onViewReady() {
            Log.d(LOG_TAG, "## onViewReady(): ");

            if(null != mMxCall) {
                if (mMxCall.isIncoming()) {
                    mMxCall.launchIncomingCall();
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
                    InComingCallActivity.this.finish();
                }
            });
        }

        @Override
        public void onCallEnd() {
            InComingCallActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "## onCallEnd(): finish activity");
                    InComingCallActivity.this.finish();
                }
            });
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        // this will turn the screen on whilst honouring the screen timeout setting, so it will
        // dim/turn off depending on user configured values.
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);*/
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.vector_incoming_call_dialog);

        // retrieve intent extras
        final Intent intent = getIntent();
        if (intent == null) {
            Log.e(LOG_TAG, "## onCreate(): intent missing");
            finish();
        } else {
            mMatrixId = intent.getStringExtra(CallViewActivity.EXTRA_MATRIX_ID);
            mCallId = intent.getStringExtra(CallViewActivity.EXTRA_CALL_ID);

            if(null == mMatrixId){
                Log.e(LOG_TAG, "## onCreate(): matrix ID is missing in extras");
                finish();
            } else if(null == mCallId){
                Log.e(LOG_TAG, "## onCreate(): call ID is missing in extras");
                finish();
            } else if(null == (mSession = Matrix.getInstance(getApplicationContext()).getSession(mMatrixId))){
                Log.e(LOG_TAG, "## onCreate(): invalid session (null)");
                finish();
            } else if(null == (mMxCall = mSession.mCallsManager.callWithCallId(mCallId))){
                Log.e(LOG_TAG, "## onCreate(): invalid call ID (null)");
                finish();
            } else {
                // UI widgets binding
                mCallingUserAvatarView = (ImageView)findViewById(R.id.avatar_img);
                mRoomNameTextView = (TextView)findViewById(R.id.room_name);
                mAcceptCallButton = (Button) findViewById(R.id.button_incoming_call_accept);
                mIgnoreCallButton = (Button) findViewById(R.id.button_incoming_call_ignore);

                // set the avatar of the calling member
                // assume that it is a 1:1 call.
                RoomMember callingMember = mMxCall.getRoom().callees().get(0);
                if(null != callingMember) {
                    VectorUtils.loadUserAvatar(this, mSession, mCallingUserAvatarView, callingMember.avatarUrl, callingMember.getUserId(), callingMember.displayname);
                } else {
                    mCallingUserAvatarView.setImageResource(R.drawable.ic_contact_picture_holo_light);
                }

                // set the room display name
                String roomDisplayName = VectorUtils.getRoomDisplayname(this, mSession, mMxCall.getRoom());
                if(null != roomDisplayName) {
                    mRoomNameTextView.setText(roomDisplayName);
                } else {
                    mRoomNameTextView.setText("");
                }

                // set button handlers
                mIgnoreCallButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onHangUp();
                        finish();
                    }
                });

                mAcceptCallButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startCallViewActivity(intent);
                        finish();
                    }
                });

                // set listener
                //mMxCall.addListener(mMxCallListener);

                // create the call view asap
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMxCall.createCallView();
                    }
                });
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (null != mMxCall) {
            mMxCall.onResume();
            mMxCall.addListener(mMxCallListener);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (null != mMxCall) {
            mMxCall.removeListener(mMxCallListener);
        }
    }

    /**
     * Helper method: starts the CallViewActivity in auto accept mode.
     * The extras provided in  are copied to
     * the CallViewActivity and {@link CallViewActivity#EXTRA_AUTO_ACCEPT} is set to true.
     * @param aSourceIntent the intent whose extras are transmitted
     */
    private void startCallViewActivity(final Intent aSourceIntent) {
        Intent intent = new Intent(this, CallViewActivity.class);
        Bundle receivedData = aSourceIntent.getExtras();
        intent.putExtras(receivedData);
        intent.putExtra(CallViewActivity.EXTRA_AUTO_ACCEPT, true);
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
