/*
 * Copyright 2014 OpenMarket Ltd
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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorCallViewActivity;
import im.vector.activity.VectorHomeActivity;
import im.vector.services.EventStreamService;

/**
 * This class contains the call toolbox.
 */
public class CallsManager {
    private static final String LOG_TAG = CallsManager.class.getSimpleName();

    public static final String HANGUP_MSG_USER_CANCEL = "user hangup";
    public static final String HANGUP_MSG_NOT_DEFINED = "not defined";


    // application context
    private Context mContext;

    // the active call
    private IMXCall mActiveCall;

    private static CallsManager mSharedInstance = null;

    private View mCallView = null;
    private IMXCall.VideoLayoutConfiguration mLocalVideoLayoutConfig = null;

    public void setCallView(View callView) {
        mCallView = callView;
    }

    public View getCallView() {
        return mCallView;
    }

    public void setVideoLayoutConfiguration(IMXCall.VideoLayoutConfiguration aLocalVideoLayoutConfig) {
        mLocalVideoLayoutConfig = aLocalVideoLayoutConfig;
    }

    public IMXCall.VideoLayoutConfiguration getVideoLayoutConfiguration() {
        return mLocalVideoLayoutConfig;
    }

    public void toggleSpeaker() {
        /*if(null != mCall) {
            VectorCallSoundManager.toggleSpeaker();
        } else {
            Log.w(LOG_TAG, "## toggleSpeaker(): Failed");
        }*/
    }

    public void setCallSpeakerphoneOn(boolean isSpeakerPhoneOn) {
        //VectorCallSoundManager.setCallSpeakerphoneOn(isSpeakerPhoneOn);
    }



    /**
     * hangup the call.
     */
    public void onHangUp(String hangUpMsg) {
        /*mSavedCallView = null;
        mSavedLocalVideoLayoutConfig = null;
        mHangUpReason = hangUpMsg;

        if (null != mCall) {
            mCall.hangup(hangUpMsg);
        }*/
    }

    /**
     * Constructor
     * @param context the context
     */
    public CallsManager(Context context) {
        mContext = context.getApplicationContext();
        mSharedInstance = this;
    }

    /**
     * @return the shared instance
     */
    public static CallsManager getSharedInstance() {
        if (null == mSharedInstance) {
            mSharedInstance = new CallsManager(VectorApp.getInstance());
        }

        return mSharedInstance;
    }


    public IMXCall getActiveCall() {
        return mActiveCall;
    }

    /**
     * Tells if the call Id is the active one.
     * @param callId the call id to test
     * @return true if it matches to the current active call.
     */
    private boolean isActiveCall(String callId) {
        return (null != mActiveCall) && TextUtils.equals(callId, mActiveCall.getCallId());
    }

    /**
     * Calls events listener.
     */
    private final MXCallsManager.MXCallsManagerListener mCallsManagerListener = new MXCallsManager.MXCallsManagerListener() {
        /**
         * Manage hangup event.
         * The ringing sound is disabled and pending incoming call is dismissed.
         * @param callId the callId
         */
        private void manageHangUpEvent(String callId) {
            if (isActiveCall(callId)) {
                if (null != callId) {
                    Log.d(LOG_TAG, "manageHangUpEvent : hide call notification and stopRinging for call " + callId);
                    EventStreamService.getInstance().hideCallNotifications();
                } else {
                    Log.d(LOG_TAG, "manageHangUpEvent : stopRinging");
                }
                //VectorCallSoundManager.stopRinging();
                mActiveCall = null;
            }
        }


        /**
         * Display the error messages
         * @param toast the message
         */
        private void showToast(final String toast)  {
            /*if (null != getInstance()) {
                getInstance().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (null != getInstance()) {
                            Toast.makeText(getInstance(), toast, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }*/
        }

        @Override
        public void onIncomingCall(final IMXCall call, MXUsersDevicesMap<MXDeviceInfo> unknownDevices) {
            Log.d(LOG_TAG, "## onIncomingCall () :" + call.getCallId());

            if (null != mActiveCall) {
                Log.d(LOG_TAG, "## onIncomingCall () : rejected because " + mActiveCall + " is in progress");
                call.hangup("busy");
            } else {

                mActiveCall = call;
                VectorHomeActivity homeActivity = VectorHomeActivity.getInstance();

                // if the home activity does not exist : the application has been woken up by a notification)
                if (null == homeActivity) {
                    Log.d(LOG_TAG, "onIncomingCall : the home activity does not exist -> launch it");

                    Context context = VectorApp.getInstance();

                    // clear the activity stack to home activity
                    Intent intent = new Intent(context, VectorHomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra(VectorHomeActivity.EXTRA_CALL_SESSION_ID, call.getSession().getMyUserId());
                    intent.putExtra(VectorHomeActivity.EXTRA_CALL_ID, call.getCallId());
                    if (null != unknownDevices) {
                        intent.putExtra(VectorHomeActivity.EXTRA_CALL_UNKNOWN_DEVICES, unknownDevices);
                    }
                    context.startActivity(intent);
                } else {
                    Log.d(LOG_TAG, "onIncomingCall : the home activity exists : but permissions have to be checked before");
                    // check incoming call required permissions, before allowing the call..
                    homeActivity.startCall(call.getSession().getMyUserId(), call.getCallId(), unknownDevices);
                }


                IMXCall.MXCallListener callListener = new IMXCall.MXCallListener() {
                    @Override
                    public void onStateDidChange(String state) {
                        Log.d(LOG_TAG, "dispatchOnStateDidChange " + call.getCallId() + " : " + state);


                        // manage audio focus
                        //VectorCallSoundManager.manageCallStateFocus(state);

                        switch (state) {
                            case IMXCall.CALL_STATE_CREATED:
                            case IMXCall.CALL_STATE_CREATING_CALL_VIEW: {

                                if (mActiveCall.isIncoming()) {
                                    EventStreamService.getInstance().displayIncomingCallNotification(call.getSession(), call.getRoom(), null, call.getCallId(), null);
                                    //VectorCallSoundManager.startRinging();
                                }

                                break;
                            }
                            case IMXCall.CALL_STATE_CONNECTING:
                            case IMXCall.CALL_STATE_CREATE_ANSWER:
                            case IMXCall.CALL_STATE_WAIT_LOCAL_MEDIA:
                            case IMXCall.CALL_STATE_WAIT_CREATE_OFFER:
                                //VectorCallSoundManager.stopRinging();
                                break;

                            case IMXCall.CALL_STATE_CONNECTED:
                                //VectorCallSoundManager.stopRinging();
                                break;

                            case IMXCall.CALL_STATE_RINGING:
                                if (mActiveCall.isIncoming()) {
                                    // TODO IncomingCallActivity disables the ringing when the user accepts the call.
                                    // when IncomingCallActivity will be removed, it should be enabled again
                                    //VectorCallSoundManager.startRinging();
                                } else {
                                    //VectorCallSoundManager.startRingBackSound(mActiveCall.isVideo());
                                }
                                break;
                        }

                        /*if (TextUtils.equals(IMXCall.CALL_STATE_ENDED, fState) &&
                                ((TextUtils.equals(IMXCall.CALL_STATE_RINGING, mLastCallState) && (null!=mCall) && !mCall.isIncoming())||
                                        TextUtils.equals(IMXCall.CALL_STATE_INVITE_SENT, mLastCallState))) {

                            if (!TextUtils.equals(HANGUP_MSG_USER_CANCEL, mHangUpReason)) {
                                // display message only if the caller originated the hang up
                                showToast(VectorCallViewActivity.this.getString(R.string.call_error_user_not_responding));
                            }

                            mIsCalleeBusy = true;
                            Log.d(LOG_TAG, "## onStateDidChange(): the callee is busy");
                        }
                        mLastCallState = fState;*/


                    }

                    @Override
                    public void onCallError(String error) {
                        Log.d(LOG_TAG, "## onCallError(): error=" + error);

                        /*if (IMXCall.CALL_ERROR_USER_NOT_RESPONDING.equals(error)) {
                            showToast(context.getString(R.string.call_error_user_not_responding));
                            mIsCalleeBusy = true;
                        } else if (IMXCall.CALL_ERROR_ICE_FAILED.equals(error)) {
                            showToast(context.getString(R.string.call_error_ice_failed));
                        } else if (IMXCall.CALL_ERROR_CAMERA_INIT_FAILED.equals(error)) {
                            showToast(context.getString(R.string.call_error_camera_init_failed));
                        } else {

                        }*/

                        manageHangUpEvent(call.getCallId());
                    }


                    @Override
                    public void onViewLoading(View callView) {
                    }

                    @Override
                    public void onViewReady() {
                    }

                    @Override
                    public void onCallAnsweredElsewhere() {
                        Log.d(LOG_TAG, "onCallAnsweredElsewhere " + call.getCallId());
                        manageHangUpEvent(call.getCallId());

                        /*VectorCallViewActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(LOG_TAG, "## onCallAnsweredElsewhere(): ");
                                showToast(VectorCallViewActivity.this.getString(R.string.call_error_answered_elsewhere));
                                clearCallData();
                                VectorCallViewActivity.this.finish();
                            }
                        });*/
                    }

                    @Override
                    public void onCallEnd(final int aReasonId) {
                       /* VectorCallViewActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(LOG_TAG, "## onCallEnd(): ");

                                clearCallData();
                                mIsCallEnded = true;
                                CommonActivityUtils.processEndCallInfo(VectorCallViewActivity.this, aReasonId);
                                VectorCallViewActivity.this.finish();
                            }
                        });
                        Log.d(LOG_TAG, "dispatchOnCallEnd " + call.getCallId());
                        manageHangUpEvent(call.getCallId());*/
                    }

                    @Override
                    public void onPreviewSizeChanged(int width, int height) {
                    }
                };

                call.addListener(callListener);
            }
        }

        @Override
        public void onCallHangUp(final IMXCall call) {
            Log.d(LOG_TAG, "onCallHangUp " + call.getCallId());
            manageHangUpEvent(call.getCallId());
        }

        @Override
        public void onVoipConferenceStarted(String roomId) {
        }

        @Override
        public void onVoipConferenceFinished(String roomId) {
        }
    };

    /**
     * Listen the call events on the provided session
     * @param session the session.
     */
    public void addSession(MXSession session) {
        session.getDataHandler().getCallsManager().addListener(mCallsManagerListener);
    }

    /**
     * Remove the call events listener on the provided session
     * @param session
     */
    public void removeSession(MXSession session) {
        session.getDataHandler().getCallsManager().removeListener(mCallsManagerListener);
    }

    /**
     * Check if there is no more active calls.
     * Stop any ringing if there is no more one
     */
    public void checkDeadCalls() {
        List<MXSession> sessions = Matrix.getMXSessions(mContext);
        boolean hasActiveCall = false;

        for(MXSession session : sessions) {
            hasActiveCall |= session.getDataHandler().getCallsManager().hasActiveCalls();
        }

        // detect if an infinite ringing has been triggered
        if ((null != mActiveCall) && !hasActiveCall) {
            Log.e(LOG_TAG, "## checkDeadCalls() : fix an infinite ringing");

            if (null != EventStreamService.getInstance()) {
                EventStreamService.getInstance().hideCallNotifications();
            }

            mActiveCall = null;

            /*if (VectorCallSoundManager.isRinging()) {
                VectorCallSoundManager.stopRinging();
            }*/
        }
    }

    public void rejectCall() {
        if (null != mActiveCall) {
            // stop the ringing when the user presses on reject
           // VectorCallSoundManager.stopRinging();
           // VectorCallSoundManager.releaseAudioFocus();
            mActiveCall.hangup("");

            mActiveCall = null;
        }
    }


    public void acceptCall(Activity fromActivity, Intent aSourceIntent) {
        // stop the ringing when the user presses on accept
        //VectorCallSoundManager.stopRinging();

        Intent intent = new Intent(fromActivity, VectorCallViewActivity.class);
        Bundle receivedData = aSourceIntent.getExtras();
        intent.putExtras(receivedData);
        intent.putExtra(VectorCallViewActivity.EXTRA_AUTO_ACCEPT, true);
        fromActivity.startActivity(intent);
    }
}
