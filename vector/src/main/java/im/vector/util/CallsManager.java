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
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.CallSoundsManager;
import org.matrix.androidsdk.call.HeadsetConnectionReceiver;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.IMXCallListener;
import org.matrix.androidsdk.call.IMXCallsManagerListener;
import org.matrix.androidsdk.call.MXCallListener;
import org.matrix.androidsdk.call.MXCallsManagerListener;
import org.matrix.androidsdk.call.VideoLayoutConfiguration;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.util.Log;

import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.VectorCallViewActivity;
import im.vector.activity.VectorHomeActivity;
import im.vector.services.EventStreamService;

/**
 * This class contains the call toolbox.
 */
public class CallsManager {
    private static final String LOG_TAG = CallsManager.class.getSimpleName();

    public static final String HANGUP_MSG_USER_CANCEL = "user hangup";

    // ring tones resource names
    private static final String RING_TONE_START_RINGING = "ring.ogg";

    private static CallsManager mSharedInstance = null;

    // application context
    private final Context mContext;

    // the active call
    private IMXCall mActiveCall;
    private CallSoundsManager mCallSoundsManager;

    private View mCallView = null;
    private VideoLayoutConfiguration mLocalVideoLayoutConfig = null;

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    private Activity mCallActivity;

    private String mPrevCallState;
    private boolean mIsStoppedByUser;

    private final HeadsetConnectionReceiver.OnHeadsetStatusUpdateListener mOnHeadsetStatusUpdateListener = new HeadsetConnectionReceiver.OnHeadsetStatusUpdateListener() {

        private void onHeadsetUpdate(boolean isBTHeadsetUpdate) {
            if (null != mActiveCall) {
                boolean isHeadsetPlugged = HeadsetConnectionReceiver.isHeadsetPlugged(mContext);

                // the user plugs a headset while the device is on loud speaker
                if (mCallSoundsManager.isSpeakerphoneOn() && isHeadsetPlugged) {
                    Log.d(LOG_TAG, "toggle the call speaker because the call was on loudspeaker.");
                    // change the audio path to the headset
                    mCallSoundsManager.toggleSpeaker();
                }
                // the user unplugs the headset during video call
                else if (!isHeadsetPlugged && mActiveCall.isVideo()) {
                    Log.d(LOG_TAG, "toggle the call speaker because the headset was unplugged during a video call.");
                    mCallSoundsManager.toggleSpeaker();
                } else if (isBTHeadsetUpdate) {
                    AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

                    if (HeadsetConnectionReceiver.isBTHeadsetPlugged()) {
                        audioManager.startBluetoothSco();
                        audioManager.setBluetoothScoOn(true);
                    } else if (audioManager.isBluetoothScoOn()) {
                        audioManager.stopBluetoothSco();
                        audioManager.setBluetoothScoOn(false);
                    }
                }

                if (mCallActivity instanceof VectorCallViewActivity) {
                    ((VectorCallViewActivity) mCallActivity).refreshSpeakerButton();
                }
            }
        }

        @Override
        public void onWiredHeadsetUpdate(boolean isPlugged) {
            onHeadsetUpdate(false);
        }

        @Override
        public void onBluetoothHeadsetUpdate(boolean isConnected) {
            onHeadsetUpdate(true);
        }
    };


    /**
     * Constructor
     *
     * @param context the context
     */
    public CallsManager(Context context) {
        mContext = context.getApplicationContext();
        mCallSoundsManager = mCallSoundsManager.getSharedInstance(mContext);
        HeadsetConnectionReceiver.getSharedInstance(mContext).addListener(mOnHeadsetStatusUpdateListener);
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

    /**
     * @return the active call
     */
    public IMXCall getActiveCall() {
        if ((null != mActiveCall) && TextUtils.equals(mActiveCall.getCallState(), IMXCall.CALL_STATE_ENDED)) {
            return null;
        }
        return mActiveCall;
    }

    /**
     * Save the callview.
     *
     * @param callView the callview
     */
    public void setCallView(View callView) {
        mCallView = callView;
    }

    /**
     * @return the callview
     */
    public View getCallView() {
        return mCallView;
    }

    /**
     * Save the layout conffig
     *
     * @param aLocalVideoLayoutConfig the video config
     */
    public void setVideoLayoutConfiguration(VideoLayoutConfiguration aLocalVideoLayoutConfig) {
        mLocalVideoLayoutConfig = aLocalVideoLayoutConfig;
    }

    /**
     * @return the layout config
     */
    public VideoLayoutConfiguration getVideoLayoutConfiguration() {
        return mLocalVideoLayoutConfig;
    }

    /**
     * Set the call activity.
     * This activity will be dismissed when the call ends.
     *
     * @param activity the activity
     */
    public void setCallActivity(Activity activity) {
        mCallActivity = activity;
    }

    /**
     * Display the error messages
     *
     * @param toast the message
     */
    private void showToast(final String toast) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, toast, Toast.LENGTH_LONG).show();
            }
        });
    }

    private final IMXCallListener mCallListener = new MXCallListener() {
        @Override
        public void onStateDidChange(final String state) {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (null == mActiveCall) {
                        Log.d(LOG_TAG, "## onStateDidChange() : no more active call");
                        return;
                    }

                    Log.d(LOG_TAG, "dispatchOnStateDidChange " + mActiveCall.getCallId() + " : " + state);

                    switch (state) {
                        case IMXCall.CALL_STATE_CREATED:
                            if (mActiveCall.isIncoming()) {
                                EventStreamService.getInstance().displayIncomingCallNotification(mActiveCall.getSession(), mActiveCall.getRoom(), null, mActiveCall.getCallId(), null);
                                startRinging();
                            }
                            break;
                        case IMXCall.CALL_STATE_CREATING_CALL_VIEW:
                        case IMXCall.CALL_STATE_CONNECTING:
                        case IMXCall.CALL_STATE_CREATE_ANSWER:
                        case IMXCall.CALL_STATE_WAIT_LOCAL_MEDIA:
                        case IMXCall.CALL_STATE_WAIT_CREATE_OFFER:
                            if (mActiveCall.isIncoming()) {
                                mCallSoundsManager.stopSounds();
                            } // else ringback
                            break;

                        case IMXCall.CALL_STATE_CONNECTED:
                            EventStreamService.getInstance().displayCallInProgressNotification(mActiveCall.getSession(), mActiveCall.getRoom(), mActiveCall.getCallId());
                            mCallSoundsManager.stopSounds();
                            requestAudioFocus();

                            mUiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (null != mActiveCall) {
                                        setCallSpeakerphoneOn(mActiveCall.isVideo() && !HeadsetConnectionReceiver.isHeadsetPlugged(mContext));
                                        mCallSoundsManager.setMicrophoneMute(false);
                                    } else {
                                        Log.e(LOG_TAG, "## onStateDidChange() : no more active call");
                                    }
                                }
                            });

                            break;

                        case IMXCall.CALL_STATE_RINGING:
                            if (!mActiveCall.isIncoming()) {
                                startRingBackSound();
                            }
                            break;

                        case IMXCall.CALL_STATE_ENDED: {
                            if (((TextUtils.equals(IMXCall.CALL_STATE_RINGING, mPrevCallState) && !mActiveCall.isIncoming()) ||
                                    TextUtils.equals(IMXCall.CALL_STATE_INVITE_SENT, mPrevCallState))) {
                                if (!mIsStoppedByUser) {
                                    // display message only if the caller originated the hang up
                                    showToast(mContext.getString(R.string.call_error_user_not_responding));
                                }

                                endCall(true);
                            } else {
                                endCall(false);
                            }

                            break;
                        }
                    }

                    mPrevCallState = state;
                }
            });
        }

        @Override
        public void onCallError(final String error) {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (null == mActiveCall) {
                        Log.d(LOG_TAG, "## onCallError() : no more active call");
                        return;
                    }

                    Log.d(LOG_TAG, "## onCallError(): error=" + error);
                    if (IMXCall.CALL_ERROR_USER_NOT_RESPONDING.equals(error)) {
                        showToast(mContext.getString(R.string.call_error_user_not_responding));
                    } else if (IMXCall.CALL_ERROR_ICE_FAILED.equals(error)) {
                        showToast(mContext.getString(R.string.call_error_ice_failed));
                    } else if (IMXCall.CALL_ERROR_CAMERA_INIT_FAILED.equals(error)) {
                        showToast(mContext.getString(R.string.call_error_camera_init_failed));
                    } else {
                        showToast(error);
                    }

                    endCall(IMXCall.CALL_ERROR_USER_NOT_RESPONDING.equals(error));
                }
            });
        }

        @Override
        public void onCallAnsweredElsewhere() {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (null == mActiveCall) {
                        Log.d(LOG_TAG, "## onCallError() : no more active call");
                        return;
                    }

                    Log.d(LOG_TAG, "onCallAnsweredElsewhere " + mActiveCall.getCallId());
                    showToast(mContext.getString(R.string.call_error_answered_elsewhere));
                    releaseCall();
                }
            });
        }

        @Override
        public void onCallEnd(final int aReasonId) {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (null == mActiveCall) {
                        Log.d(LOG_TAG, "## onCallEnd() : no more active call");
                        return;
                    }
                    endCall(false);
                }
            });
        }
    };

    /**
     * Calls events listener.
     */
    private final IMXCallsManagerListener mCallsManagerListener = new MXCallsManagerListener() {
        @Override
        public void onIncomingCall(final IMXCall aCall, final MXUsersDevicesMap<MXDeviceInfo> unknownDevices) {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    Context context = VectorApp.getInstance();
                    Log.d(LOG_TAG, "## onIncomingCall () :" + aCall.getCallId());
                    int currentCallState = TelephonyManager.CALL_STATE_IDLE;

                    TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

                    if (null != telephonyManager && telephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY) {
                        currentCallState = telephonyManager.getCallState();
                    }

                    Log.d(LOG_TAG, "## onIncomingCall () : currentCallState(GSM) = " + currentCallState);

                    if (currentCallState == TelephonyManager.CALL_STATE_OFFHOOK || currentCallState == TelephonyManager.CALL_STATE_RINGING) {
                        Log.d(LOG_TAG, "## onIncomingCall () : rejected because GSM Call is in progress");
                        aCall.hangup("busy");
                    } else if (null != mActiveCall) {
                        Log.d(LOG_TAG, "## onIncomingCall () : rejected because " + mActiveCall + " is in progress");
                        aCall.hangup("busy");
                    } else {
                        mPrevCallState = null;
                        mIsStoppedByUser = false;

                        mActiveCall = aCall;
                        VectorHomeActivity homeActivity = VectorHomeActivity.getInstance();

                        // if the home activity does not exist : the application has been woken up by a notification)
                        if (null == homeActivity) {
                            Log.d(LOG_TAG, "onIncomingCall : the home activity does not exist -> launch it");

                            // clear the activity stack to home activity
                            Intent intent = new Intent(context, VectorHomeActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.putExtra(VectorHomeActivity.EXTRA_CALL_SESSION_ID, mActiveCall.getSession().getMyUserId());
                            intent.putExtra(VectorHomeActivity.EXTRA_CALL_ID, mActiveCall.getCallId());
                            if (null != unknownDevices) {
                                intent.putExtra(VectorHomeActivity.EXTRA_CALL_UNKNOWN_DEVICES, unknownDevices);
                            }
                            context.startActivity(intent);
                        } else {
                            Log.d(LOG_TAG, "onIncomingCall : the home activity exists : but permissions have to be checked before");
                            // check incoming call required permissions, before allowing the call..
                            homeActivity.startCall(mActiveCall.getSession().getMyUserId(), mActiveCall.getCallId(), unknownDevices);
                        }

                        startRinging();
                        mActiveCall.addListener(mCallListener);
                    }
                }
            });
        }

        @Override
        public void onOutgoingCall(final IMXCall call) {
            Log.d(LOG_TAG, "## onOutgoingCall () :" + call.getCallId());

            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mPrevCallState = null;
                    mIsStoppedByUser = false;
                    mActiveCall = call;
                    mActiveCall.addListener(mCallListener);
                    startRingBackSound();
                }
            });
        }

        @Override
        public void onCallHangUp(final IMXCall call) {
            Log.d(LOG_TAG, "onCallHangUp " + call.getCallId());
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (null == mActiveCall) {
                        Log.d(LOG_TAG, "## onCallEnd() : no more active call");
                        return;
                    }
                    endCall(false);
                }
            });
        }
    };

    /**
     * Listen the call events on the provided session
     *
     * @param session the session.
     */
    public void addSession(MXSession session) {
        session.getDataHandler().getCallsManager().addListener(mCallsManagerListener);
    }

    /**
     * Remove the call events listener on the provided session
     *
     * @param session the session
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

        for (MXSession session : sessions) {
            hasActiveCall |= session.getDataHandler().getCallsManager().hasActiveCalls();
        }

        // detect if an infinite ringing has been triggered
        if ((null != mActiveCall) && !hasActiveCall) {
            Log.e(LOG_TAG, "## checkDeadCalls() : fix an infinite ringing");

            if (null != EventStreamService.getInstance()) {
                EventStreamService.getInstance().hideCallNotifications();
            }

            releaseCall();
        }
    }

    /**
     * Reject the incoming call
     */
    public void rejectCall() {
        if (null != mActiveCall) {
            mActiveCall.hangup("Reject");
            releaseCall();
        }
    }

    /**
     * Toggle the speaker
     */
    public void toggleSpeaker() {
        if (null != mActiveCall) {
            mCallSoundsManager.toggleSpeaker();
        } else {
            Log.w(LOG_TAG, "## toggleSpeaker(): no active call");
        }
    }

    /**
     * Update the speaker status
     *
     * @param isSpeakerPhoneOn true to turn on the loud speaker.
     */
    private void setCallSpeakerphoneOn(boolean isSpeakerPhoneOn) {
        if (null != mActiveCall) {
            mCallSoundsManager.setCallSpeakerphoneOn(isSpeakerPhoneOn);
        } else {
            Log.w(LOG_TAG, "## toggleSpeaker(): no active call");
        }
    }

    /**
     * hangup the call.
     */
    public void onHangUp(String hangUpMsg) {
        if (null != mActiveCall) {
            mIsStoppedByUser = true;
            mActiveCall.hangup(hangUpMsg);
            endCall(false);
        }
    }

    /**
     * Start the ringing tone
     */
    private void startRinging() {
        requestAudioFocus();
        mCallSoundsManager.startRinging(R.raw.ring, RING_TONE_START_RINGING);
    }

    /**
     * @return true if it's ringing
     */
    public boolean isRinging() {
        return mCallSoundsManager.isRinging();
    }

    /**
     * @return true if the speaker is turned on.
     */
    public boolean isSpeakerphoneOn() {
        return mCallSoundsManager.isSpeakerphoneOn();
    }

    /**
     * Request the audio focus
     */
    private void requestAudioFocus() {
        mCallSoundsManager.requestAudioFocus();
    }

    /**
     * Play the ringback sound
     */
    private void startRingBackSound() {
        mCallSoundsManager.startSound(R.raw.ringback, true, new CallSoundsManager.OnMediaListener() {
            @Override
            public void onMediaReadyToPlay() {
                if (null != mActiveCall) {
                    requestAudioFocus();
                    mCallSoundsManager.setSpeakerphoneOn(true, mActiveCall.isVideo() && !HeadsetConnectionReceiver.isHeadsetPlugged(mContext));
                } else {
                    Log.e(LOG_TAG, "## startSound() : null mActiveCall");
                }
            }

            @Override
            public void onMediaPlay() {

            }

            @Override
            public void onMediaCompleted() {
            }
        });
    }

    /**
     * Manage end of call
     */
    private void endCall(boolean isBusy) {
        if (null != mActiveCall) {
            final IMXCall call = mActiveCall;
            mActiveCall = null;

            if (mCallSoundsManager.isRinging()) {
                releaseCall(call);
            } else {
                mCallSoundsManager.startSound(isBusy ? R.raw.busy : R.raw.callend, false, new CallSoundsManager.OnMediaListener() {
                    @Override
                    public void onMediaReadyToPlay() {
                        if (null != mCallActivity) {
                            mCallActivity.finish();
                            mCallActivity = null;
                        }
                    }

                    @Override
                    public void onMediaPlay() {
                    }

                    @Override
                    public void onMediaCompleted() {
                        releaseCall(call);
                    }
                });
            }
        }
    }

    /**
     * Release the active call.
     */
    private void releaseCall() {
        if (null != mActiveCall) {
            releaseCall(mActiveCall);
            mActiveCall = null;
        }
    }

    /**
     * Release a call.
     */
    private void releaseCall(IMXCall call) {
        if (null != call) {
            call.removeListener(mCallListener);

            mCallSoundsManager.stopSounds();
            mCallSoundsManager.releaseAudioFocus();

            if (null != mCallActivity) {
                mCallActivity.finish();
                mCallActivity = null;
            }
            mCallView = null;
            mLocalVideoLayoutConfig = null;

            EventStreamService.getInstance().hideCallNotifications();
        }
    }
}
