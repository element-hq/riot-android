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

package im.vector.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;

import im.vector.Matrix;
import im.vector.R;
import im.vector.services.EventStreamService;
import im.vector.util.VectorCallSoundManager;
import im.vector.util.VectorUtils;
import im.vector.view.VectorPendingCallView;


/**
 * VectorCallViewActivity is the call activity.
 */
public class VectorCallViewActivity extends Activity implements SensorEventListener {
    private static final String LOG_TAG = "VCallViewActivity";
    private static final String HANGUP_MSG_HEADER_UI_CALL = "user hangup from header back arrow";
    private static final String HANGUP_MSG_BACK_KEY = "user hangup from back key";
    /** threshold used to manage the backlight during the call **/
    private static final float PROXIMITY_THRESHOLD = 3.0f; // centimeters
    private static final String HANGUP_MSG_USER_CANCEL = "user hangup";
    private static final String HANGUP_MSG_NOT_DEFINED = "not defined";

    public static final String EXTRA_MATRIX_ID = "CallViewActivity.EXTRA_MATRIX_ID";
    public static final String EXTRA_CALL_ID = "CallViewActivity.EXTRA_CALL_ID";
    public static final String EXTRA_AUTO_ACCEPT = "CallViewActivity.EXTRA_AUTO_ACCEPT";
    private static final String KEY_MIC_MUTE_STATUS = "KEY_MIC_MUTE_STATUS";
    private static final String KEY_SPEAKER_STATUS = "KEY_SPEAKER_STATUS";

    private static VectorCallViewActivity instance = null;

    private static View mSavedCallview = null;
    private static IMXCall mCall = null;

    private View mCallView;

    // account info
    private String mMatrixId = null;
    private MXSession mSession = null;
    private String mCallId = null;

    // call info
    private boolean mAutoAccept = false;
    private boolean mIsCallEnded = false;
    private boolean mIsCalleeBusy = false;
    private String mHangUpReason = HANGUP_MSG_NOT_DEFINED;

    // graphical items
    private ImageView mHangUpImageView;
    private ImageView mSpeakerSelectionView;
    private ImageView mAvatarView;
    private ImageView mMuteMicImageView;
    private ImageView mSwichRearFrontCameraImageView;
    private ImageView mMuteLocalCameraView;
    private ImageView mRoomLinkImageView;
    private VectorPendingCallView mHeaderPendingCallView;

    // video diplay size
    private IMXCall.VideoLayoutConfiguration mLocalVideoLayoutConfig;
    // hard coded values are taken from specs:
    // - 585 as screen height reference
    // - 18 as space between the local video and the container view containing the setting buttons
    private static final float RATIO_TOP_MARGIN_LOCAL_USER_VIDEO = (float)(462.0/585.0);
    private static final float VIDEO_TO_BUTTONS_VERTICAL_SPACE = (float) (18.0/585.0);
    /**  local user video height is set as percent of the total screen height **/
    private static final int PERCENT_LOCAL_USER_VIDEO_SIZE = 25;
    private static final float RATIO_LOCAL_USER_VIDEO_HEIGHT = ((float)(PERCENT_LOCAL_USER_VIDEO_SIZE))/100;
    private static final float RATIO_LOCAL_USER_VIDEO_WIDTH = ((float)(PERCENT_LOCAL_USER_VIDEO_SIZE))/100;
    private static final float RATIO_LOCAL_USER_VIDEO_ASPECT = 0.65f;

    // sensor
    SensorManager mSensorMgr;
    Sensor mProximitySensor;

    // activity life cycle management
    private boolean mSavedSpeakerValue;
    private boolean mIsSpeakerForcedFromLifeCycle;

    private final IMXCall.MXCallListener mListener = new IMXCall.MXCallListener() {
        private String mLastCallState = null;

        @Override
        public void onStateDidChange(String state) {
            if (null != getInstance()) {
                final String fState = state;
                VectorCallViewActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(LOG_TAG, "## onStateDidChange(): new state=" + fState);

                        if (TextUtils.equals(IMXCall.CALL_STATE_ENDED, fState) &&
                                ((TextUtils.equals(IMXCall.CALL_STATE_RINGING, mLastCallState) && (null!=mCall) && !mCall.isIncoming())||
                                        TextUtils.equals(IMXCall.CALL_STATE_INVITE_SENT, mLastCallState))) {

                            if (!TextUtils.equals(HANGUP_MSG_USER_CANCEL, mHangUpReason)) {
                                // display message only if the caller originated the hang up
                                showToast(VectorCallViewActivity.this.getString(R.string.call_error_user_not_responding));
                            }

                            mIsCalleeBusy = true;
                            Log.d(LOG_TAG, "## onStateDidChange(): the callee is busy");
                        }
                        mLastCallState = fState;

                        manageSubViews();
                    }
                });

                // manage audio focus
                VectorCallSoundManager.manageCallStateFocus(state);
            }
        }

        /**
         * Display the error messages
         * @param toast the message
         */
        private void showToast(final String toast)  {
            if (null != getInstance()) {
                getInstance().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (null != getInstance()) {
                            Toast.makeText(getInstance(), toast, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }

        @Override
        public void onCallError(String error) {
            Context context = getInstance();

            Log.d(LOG_TAG, "## onCallError(): error=" + error);

            if (null != context) {
                if (IMXCall.CALL_ERROR_USER_NOT_RESPONDING.equals(error)) {
                    showToast(context.getString(R.string.call_error_user_not_responding));
                    mIsCalleeBusy = true;
                } else if (IMXCall.CALL_ERROR_ICE_FAILED.equals(error)) {
                    showToast(context.getString(R.string.call_error_ice_failed));
                } else if (IMXCall.CALL_ERROR_CAMERA_INIT_FAILED.equals(error)) {
                    showToast(context.getString(R.string.call_error_camera_init_failed));
                }
            }
        }

        @Override
        public void onViewLoading(View callView) {
            Log.d(LOG_TAG, "## onViewLoading():");

            mCallView = callView;
            insertCallView();
        }

        @Override
        public void onViewReady() {
            // update UI before displaying the video
            computeVideoUiLayout();
            if (!mCall.isIncoming()) {
                Log.d(LOG_TAG, "## onViewReady(): placeCall()");
                mCall.placeCall(mLocalVideoLayoutConfig);
            } else {
                Log.d(LOG_TAG, "## onViewReady(): launchIncomingCall()");
                mCall.launchIncomingCall(mLocalVideoLayoutConfig);
            }
        }

        /**
         * The call was answered on another device
         */
        @Override
        public void onCallAnsweredElsewhere() {
            VectorCallViewActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "## onCallAnsweredElsewhere(): ");
                    showToast(VectorCallViewActivity.this.getString(R.string.call_error_answered_elsewhere));
                    clearCallData();
                    VectorCallViewActivity.this.finish();
                }
            });
        }

        @Override
        public void onCallEnd(final int aReasonId) {
            VectorCallViewActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "## onCallEnd(): ");

                    clearCallData();
                    mIsCallEnded = true;
                    CommonActivityUtils.processEndCallInfo(VectorCallViewActivity.this, aReasonId);
                    VectorCallViewActivity.this.finish();
                }
            });
        }
    };

    /**
     * @return true if the call can be resumed.
     * i.e this callView can be closed to be re opened later.
     */
    private static boolean canCallBeResumed() {
        if (null != mCall) {
            String state = mCall.getCallState();

            // active call must be
            return
                    (state.equals(IMXCall.CALL_STATE_RINGING) && !mCall.isIncoming()) ||
                            state.equals(IMXCall.CALL_STATE_CONNECTING) ||
                            state.equals(IMXCall.CALL_STATE_CONNECTED) ||
                            state.equals(IMXCall.CALL_STATE_CREATE_ANSWER);
        }

        return false;
    }


    /**
     * @param callId the call Id
     * @return true if the call is the active callId
     */
    public static boolean isBackgroundedCallId(String callId) {
        boolean res = false;

        if ((null != mCall) && (null == instance)) {
            res = mCall.getCallId().equals(callId);
            // clear unexpected call.
            getActiveCall();
        }

        return res;
    }

    /**
     * Provides the active call.
     * The current call is tested to check if it is still valid.
     * It if it is no more valid, any call UIs are dismissed.
     * @return the active call
     */
    public static IMXCall getActiveCall() {
        // not currently displayed
        if ((instance == null) && (null != mCall)) {
            // check if the call can be resume
            // or it's still valid
            if (!canCallBeResumed() || (null == mCall.getSession().mCallsManager.getCallWithCallId(mCall.getCallId()))) {
                Log.d(LOG_TAG, "Hide the call notifications because the current one cannot be resumed");
                EventStreamService.getInstance().hideCallNotifications();
                mCall = null;
                mSavedCallview = null;
            }
        }

        return mCall;
    }

    /**
     * @return the callViewActivity instance
     */
    private static VectorCallViewActivity getInstance() {
        return instance;
    }

    /**
     * release the call info
     */
    private void clearCallData() {
        if (null != mCall) {
            mCall.removeListener(mListener);
        }

        // remove header call view
        mHeaderPendingCallView.checkPendingCall();

        // release audio focus
        VectorCallSoundManager.releaseAudioFocus();

        mCall = null;
        mCallView = null;
        mSavedCallview = null;
    }

    /**
     * Insert the callView in the activity (above the other room member).
     * The callView is setup in the SDK, and provided via dispatchOnViewLoading() in {@link #mListener}.
     */
    private void insertCallView() {
        if(null != mCallView) {
            // set the avatar
            ImageView avatarView = (ImageView) VectorCallViewActivity.this.findViewById(R.id.call_other_member);
            VectorUtils.loadRoomAvatar(this, mSession, avatarView, mCall.getRoom());

            // insert the
            RelativeLayout layout = (RelativeLayout)findViewById(R.id.call_layout);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
            params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            layout.removeView(mCallView);
            layout.addView(mCallView, 1, params);

            // init as GONE, will be displayed according to call states..
            mCall.setVisibility(View.GONE);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG,"## onCreate(): IN");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_callview);
        instance = this;

        final Intent intent = getIntent();
        if (intent == null) {
            Log.e(LOG_TAG, "Need an intent to view.");
            finish();
            return;
        }

        if (!intent.hasExtra(EXTRA_MATRIX_ID)) {
            Log.e(LOG_TAG, "No matrix ID extra.");
            finish();
            return;
        }

        mCallId = intent.getStringExtra(EXTRA_CALL_ID);
        mMatrixId = intent.getStringExtra(EXTRA_MATRIX_ID);

        mSession = Matrix.getInstance(getApplicationContext()).getSession(mMatrixId);
        if (null == mSession) {
            Log.e(LOG_TAG, "invalid session");
            finish();
            return;
        }

        if(null == (mCall = mSession.mCallsManager.getCallWithCallId(mCallId))) {
            Log.e(LOG_TAG, "invalid callId");
            finish();
            return;
        }

        // UI binding
        mHangUpImageView = (ImageView) findViewById(R.id.hang_up_button);
        mSpeakerSelectionView = (ImageView) findViewById(R.id.call_speaker_view);
        mAvatarView = (ImageView)VectorCallViewActivity.this.findViewById(R.id.call_other_member);
        mMuteMicImageView = (ImageView)VectorCallViewActivity.this.findViewById(R.id.mute_audio);
        mRoomLinkImageView = (ImageView)VectorCallViewActivity.this.findViewById(R.id.room_chat_link);
        mHeaderPendingCallView = (VectorPendingCallView) findViewById(R.id.header_pending_callview);
        mSwichRearFrontCameraImageView = (ImageView) findViewById(R.id.call_switch_camera_view);
        mMuteLocalCameraView = (ImageView) findViewById(R.id.mute_local_camera);

        mRoomLinkImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRoomActivity();
            }
        });

        mSwichRearFrontCameraImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRearFrontCamera();
                refreshSwitchRearFrontCameraButton();
            }
        });

        mMuteLocalCameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleVideoMute();
                refreshMuteVideoButton();
            }
        });

        mMuteMicImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMicMute();
                refreshMuteMicButton();
            }
        });

        mHangUpImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onHangUp(HANGUP_MSG_USER_CANCEL);
            }
        });

        mSpeakerSelectionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsSpeakerForcedFromLifeCycle = false;
                toggleSpeaker();
                refreshSpeakerButton();
            }
        });

        mAutoAccept = intent.hasExtra(EXTRA_AUTO_ACCEPT);

        // life cycle management
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if((null != savedInstanceState) && (null != audioManager)) {
            // restore mic satus
            audioManager.setMicrophoneMute(savedInstanceState.getBoolean(KEY_MIC_MUTE_STATUS, false));
            // restore speaker status (Cf. manageSubViews())
            mIsSpeakerForcedFromLifeCycle = true;
            mSavedSpeakerValue = savedInstanceState.getBoolean(KEY_SPEAKER_STATUS, mCall.isVideo());
        } else {
            // mic default value: enabled
            audioManager.setMicrophoneMute(false);
        }

        // init call UI setting buttons
        manageSubViews();

        // the webview has been saved after a screen rotation
        // getParent() != null : the static value have been reused whereas it should not
        if ((null != mSavedCallview) && (null == mSavedCallview.getParent())) {
            mCallView = mSavedCallview;
            insertCallView();
        } else {
            Log.d(LOG_TAG, "## onCreate(): Hide the call notifications");
            EventStreamService.getInstance().hideCallNotifications();
            mSavedCallview = null;

            // create the callview asap
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mCall.createCallView();
                }
            });
        }

        setupHeaderPendingCallView();
        initBackLightManagement();
        Log.d(LOG_TAG,"## onCreate(): OUT");
    }

    /**
     * Customize the header pending call view to match the video/audio call UI.
     */
    private void setupHeaderPendingCallView(){
        if(null != mHeaderPendingCallView) {
            // set the gradient effect in the background
            View mainContainerView = mHeaderPendingCallView.findViewById(R.id.main_view);
            mainContainerView.setBackgroundResource(R.drawable.call_header_transparent_bg);

            // remove the call icon and display the back arrow icon
            mHeaderPendingCallView.findViewById(R.id.call_icon_container).setVisibility(View.GONE);
            View backButtonView = mHeaderPendingCallView.findViewById(R.id.back_icon);
            backButtonView.setVisibility(View.VISIBLE);
            backButtonView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // simulate a back button press
                    if (!canCallBeResumed()) {
                        if (null != mCall) {
                            mCall.hangup(HANGUP_MSG_HEADER_UI_CALL);
                        }
                    } else {
                        saveCallView();
                    }
                    VectorCallViewActivity.this.onBackPressed();
                }
            });

            // center the text horizontally and remove any padding
            LinearLayout textInfoContainerView = (LinearLayout)mHeaderPendingCallView.findViewById(R.id.call_info_container);
            textInfoContainerView.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);
            textInfoContainerView.setPadding(0,0,0,0);

            // prevent the status call to be displayed
            mHeaderPendingCallView.enableCallStatusDisplay(false);
        }
    }

    /**
     * Perform the required initializations for the backlight management.<p>
     * For video call the backlight must be ON. For voice call the backlight must be
     * OFF when the proximity sensor fires.
     */
    private void initBackLightManagement() {
        if(null != mCall) {
            if(mCall.isVideo()) {
                // video call: set the backlight on
                Log.d(LOG_TAG,"## initBackLightManagement(): backlight is ON");
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // same as android:keepScreenOn="true" in layout
            } else {
                // voice call: use the proximity sensor
                mSensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
                if(null == (mProximitySensor = mSensorMgr.getDefaultSensor(Sensor.TYPE_PROXIMITY))) {
                    Log.w(LOG_TAG,"## initBackLightManagement(): Warning - proximity sensor not supported");
                }
            }
        }

    }

    /**
     * Toggle the mute feature of the mic.
     */
    private void toggleMicMute() {
        AudioManager audioManager = (AudioManager) VectorCallViewActivity.this.getSystemService(Context.AUDIO_SERVICE);
        if(null != audioManager) {
            boolean isMuted = audioManager.isMicrophoneMute();
            Log.d(LOG_TAG,"## toggleMicMute(): current mute val="+isMuted+" new mute val="+!isMuted);
            audioManager.setMicrophoneMute(!isMuted);
        } else {
            Log.w(LOG_TAG,"## toggleMicMute(): Failed due to invalid AudioManager");
        }
    }

    /**
     * Toggle the mute feature of the local camera.
     */
    private void toggleVideoMute() {
        if(null != mCall) {
            if(mCall.isVideo()) {
                boolean isMuted = mCall.isVideoRecordingMuted();
                mCall.muteVideoRecording(!isMuted);
                Log.w(LOG_TAG, "## toggleVideoMute(): camera record turned to " + !isMuted);
            }
        } else {
            Log.w(LOG_TAG, "## toggleVideoMute(): Failed");
        }
    }

    /**
     * Toggle the mute feature of the mic.
     */
    private void toggleSpeaker() {
        if(null != mCall) {
            VectorCallSoundManager.toggleSpeaker();
        } else {
            Log.w(LOG_TAG, "## toggleSpeaker(): Failed");
        }
    }

    /**
     * Toggle the cameras.
     */
    private void toggleRearFrontCamera() {
        boolean wasCameraSwitched = false;

        if ((null != mCall) && mCall.getCallState().equals(IMXCall.CALL_STATE_CONNECTED) && mCall.isVideo()) {
            wasCameraSwitched = mCall.switchRearFrontCamera();
        } else {
            Log.w(LOG_TAG, "## toggleRearFrontCamera(): Skipped");
        }
        Log.w(LOG_TAG, "## toggleRearFrontCamera(): done? " + wasCameraSwitched);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        CommonActivityUtils.onLowMemory(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        CommonActivityUtils.onTrimMemory(this, level);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // assume that the user cancels the call if it is ringing
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!canCallBeResumed()) {
                if (null != mCall) {
                    mCall.hangup(HANGUP_MSG_BACK_KEY);
                }
            } else {
                saveCallView();
            }
        } else if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) || (keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            // this is a trick to reduce the ring volume :
            // when the call is ringing, the AudioManager.Mode switch to MODE_IN_COMMUNICATION
            // so the volume is the next call one whereas the user expects to reduce the ring volume.
            if ((null != mCall) && mCall.getCallState().equals(IMXCall.CALL_STATE_RINGING)) {
                AudioManager audioManager = (AudioManager) VectorCallViewActivity.this.getSystemService(Context.AUDIO_SERVICE);
                // IMXChrome call issue
                if (audioManager.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
                    int musicVol = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) * audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, musicVol, 0);
                }
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void finish() {
        super.finish();
        VectorCallSoundManager.stopRinging();
        instance = null;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (null != mCall) {
            if(null != mProximitySensor) {
                mSensorMgr.unregisterListener(this);
            }
            mCall.onPause();
            mCall.removeListener(mListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mHeaderPendingCallView.checkPendingCall();

        // compute video UI layout position after rotation & apply new position
        computeVideoUiLayout();
        if ((null != mCall) && mCall.isVideo() && mCall.getCallState().equals(IMXCall.CALL_STATE_CONNECTED)) {
            mCall.updateLocalVideoRendererPosition(mLocalVideoLayoutConfig);
        }

        if (null != mCall) {
            if(null != mProximitySensor) {
                // proximity sensor only used for voice call (see initBackLightManagement())
                mSensorMgr.registerListener(this, mProximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
            mCall.onResume();

            mCall.addListener(mListener);

            final String fState = mCall.getCallState();

            Log.d(LOG_TAG, "## onResume(): call state=" + fState);

            // restore video layout after rotation
            mCallView = mSavedCallview;
            insertCallView();

            // init the call button
            manageSubViews();
        } else {
            this.finish();
        }
    }

    /**
     * Compute the top margin of the view that contains the video
     * of the local attendee of the call (the small video, where
     * the user sees himself).<br>
     * Ratios are taken from the UI specifications. The vertical space
     * between the video view and the container (call_menu_buttons_layout_container)
     * containing the buttons of the video menu, is specified as 4.3% of
     * the height screen.
     */
    private void computeVideoUiLayout() {
        String msgDebug="## computeVideoUiLayout():";

        mLocalVideoLayoutConfig = new IMXCall.VideoLayoutConfiguration();
        mLocalVideoLayoutConfig.mWidth = PERCENT_LOCAL_USER_VIDEO_SIZE;
        mLocalVideoLayoutConfig.mHeight = PERCENT_LOCAL_USER_VIDEO_SIZE;

        // get screen orientation:
        int screenOrientation = getResources().getConfiguration().orientation;

        // get the height of the screen
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float screenHeight = (float)(metrics.heightPixels);
        float screenWidth = (float)(metrics.widthPixels);

        // compute action bar size: the video Y component starts below the action bar
        int actionBarHeight=0;
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(typedValue.data, getResources().getDisplayMetrics());
            screenHeight -= actionBarHeight;
        }

        View mMenuButtonsContainerView = VectorCallViewActivity.this.findViewById(R.id.hang_up_button);
        ViewGroup.LayoutParams layout = mMenuButtonsContainerView.getLayoutParams();
        float buttonsContainerHeight = (float)(layout.height);

        // base formula:
        // screenHeight = actionBarHeight + topMarginHeightLocalUserVideo + localVideoHeight + "height between video bottom & buttons" + buttonsContainerHeight
        //float topMarginHeightNormalized = 1 - RATIO_LOCAL_USER_VIDEO_HEIGHT - VIDEO_TO_BUTTONS_VERTICAL_SPACE;

        float topMarginHeightNormalized = 0; // range [0;1]
        float ratioVideoHeightNormalized = 0; // range [0;1]
        float localVideoWidth = Math.min(screenHeight,screenWidth/*portrait is ref*/)*RATIO_LOCAL_USER_VIDEO_HEIGHT; // value effectively applied by the SDK
        float estimatedLocalVideoHeight = (float) ((localVideoWidth)/(RATIO_LOCAL_USER_VIDEO_ASPECT)); // 0.65 => to adapt

        if(Configuration.ORIENTATION_LANDSCAPE == screenOrientation){
            // take the video width as height
            ratioVideoHeightNormalized = (localVideoWidth/screenHeight);
        } else {
            mLocalVideoLayoutConfig.mIsPortrait = true;
            // take the video height as height
            ratioVideoHeightNormalized = estimatedLocalVideoHeight/screenHeight;
        }
        Log.d(LOG_TAG,"## computeVideoUiLayout(): orientation = PORTRAIT");

        // the video is displayed:
        // - X axis: centered horizontally
        mLocalVideoLayoutConfig.mX = (100 - PERCENT_LOCAL_USER_VIDEO_SIZE) / 2;

        // - Y axis: above the video buttons
        topMarginHeightNormalized = 1 - ratioVideoHeightNormalized - VIDEO_TO_BUTTONS_VERTICAL_SPACE - (buttonsContainerHeight/screenHeight);
        if(topMarginHeightNormalized >= 0) {
            mLocalVideoLayoutConfig.mY = (int) (topMarginHeightNormalized * 100);
        }
        else { // set the video at the top of the screen
            mLocalVideoLayoutConfig.mY = 0;
        }

        msgDebug+= " VideoHeightRadio="+ratioVideoHeightNormalized+" screenHeight="+screenHeight+" containerHeight="+(int)buttonsContainerHeight+" TopMarginRatio="+mLocalVideoLayoutConfig.mY;
        Log.d(LOG_TAG,msgDebug);
    }

    /**
     * Helper method to start the room activity.
     */
    private void startRoomActivity() {
        if(null != mCall) {
            String roomId = mCall.getRoom().getRoomId();

            Intent intent = new Intent(getApplicationContext(), VectorRoomActivity.class);
            intent.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
            intent.putExtra(VectorRoomActivity.EXTRA_MATRIX_ID, mMatrixId);
            startActivity(intent);
        }
    }

    /**
     * Update the mute mic icon according to mute mic status.
     */
    private void refreshMuteMicButton() {
        if ((null != mCall) && mCall.getCallState().equals(IMXCall.CALL_STATE_CONNECTED)) {
            AudioManager audioManager = (AudioManager) VectorCallViewActivity.this.getSystemService(Context.AUDIO_SERVICE);
            mMuteMicImageView.setVisibility(View.VISIBLE);

            boolean  isMuted = audioManager.isMicrophoneMute();
            Log.d(LOG_TAG,"## refreshMuteMicButton(): isMuted="+isMuted);

            // update icon
            int iconId = isMuted?R.drawable.ic_material_mic_off_pink_red:R.drawable.ic_material_mic_off_grey;
            mMuteMicImageView.setImageResource(iconId);
        } else {
            Log.d(LOG_TAG,"## refreshMuteMicButton(): View.INVISIBLE");
            mMuteMicImageView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Update the mute speaker icon according to speaker status.
     */
    private void refreshSpeakerButton() {
        if ((null != mCall) && mCall.getCallState().equals(IMXCall.CALL_STATE_CONNECTED)) {
            mSpeakerSelectionView.setVisibility(View.VISIBLE);

            AudioManager audioManager = (AudioManager) VectorCallViewActivity.this.getSystemService(Context.AUDIO_SERVICE);
            boolean isOn = audioManager.isSpeakerphoneOn();
            Log.d(LOG_TAG,"## refreshSpeakerButton(): isOn="+isOn);

            // update icon
            int iconId = isOn?R.drawable.ic_material_speaker_phone_pink_red:R.drawable.ic_material_speaker_phone_grey;
            mSpeakerSelectionView.setImageResource(iconId);

            VectorCallViewActivity.this.setVolumeControlStream(audioManager.getMode());
        } else {
            Log.d(LOG_TAG,"## refreshSpeakerButton(): View.INVISIBLE");
            mSpeakerSelectionView.setVisibility(View.INVISIBLE);
        }
    }


    /**
     * Update the mute video icon.
     */
    private void refreshMuteVideoButton() {
        if ((null != mCall) && mCall.getCallState().equals(IMXCall.CALL_STATE_CONNECTED) && mCall.isVideo()) {
            mMuteLocalCameraView.setVisibility(View.VISIBLE);

            boolean isMuted = mCall.isVideoRecordingMuted();
            Log.d(LOG_TAG,"## refreshMuteVideoButton(): isMuted="+isMuted);

            // update icon
            int iconId = isMuted?R.drawable.ic_material_videocam_off_pink_red:R.drawable.ic_material_videocam_off_grey;
            mMuteLocalCameraView.setImageResource(iconId);
        } else {
            Log.d(LOG_TAG,"## refreshMuteVideoButton(): View.INVISIBLE");
            mMuteLocalCameraView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Update the switch camera icon.
     * Note that, this icon is only active if the device supports
     * camera switching (See {@link IMXCall#isSwitchCameraSupported()})
     */
    private void refreshSwitchRearFrontCameraButton() {
        if ((null != mCall) && mCall.getCallState().equals(IMXCall.CALL_STATE_CONNECTED) && mCall.isVideo() && mCall.isSwitchCameraSupported()) {
            mSwichRearFrontCameraImageView.setVisibility(View.VISIBLE);

            boolean isSwitched= mCall.isCameraSwitched();
            Log.d(LOG_TAG,"## refreshSwitchRearFrontCameraButton(): isSwitched="+isSwitched);

            // update icon
            int iconId = isSwitched?R.drawable.ic_material_switch_video_pink_red:R.drawable.ic_material_switch_video_grey;
            mSwichRearFrontCameraImageView.setImageResource(iconId);
        } else {
            Log.d(LOG_TAG,"## refreshSwitchRearFrontCameraButton(): View.INVISIBLE");
            mSwichRearFrontCameraImageView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * hangup the call.
     */
    private void onHangUp(String hangUpMsg) {
        mSavedCallview = null;
        mHangUpReason = hangUpMsg;

        if (null != mCall) {
            mCall.hangup(hangUpMsg);
        }
    }

    /**
     * Manage the UI according to call state.
     */
    private void manageSubViews() {
        // sanity check
        // the call could have been destroyed between call.
        if (null == mCall) {
            Log.d(LOG_TAG, "## manageSubViews(): call instance = null, just return");
            return;
        }

        String callState = mCall.getCallState();
        Log.d(LOG_TAG, "## manageSubViews() IN callState : " + callState);

        // set speaker status
        boolean isSpeakerPhoneOn;
        if(mIsSpeakerForcedFromLifeCycle) {
            isSpeakerPhoneOn = mSavedSpeakerValue;
        } else {
            // default value: video => speaker ON, voice => speaker OFF
            isSpeakerPhoneOn = mCall.isVideo();
        }

        // avatar visibility: video call => hide avatar, audio call => show avatar
        mAvatarView.setVisibility((callState.equals(IMXCall.CALL_STATE_CONNECTED) && mCall.isVideo()) ? View.GONE : View.VISIBLE);

        // update UI icon settings
        refreshSpeakerButton();
        refreshMuteMicButton();
        refreshMuteVideoButton();
        refreshSwitchRearFrontCameraButton();

        // display the hang up button according to the call state
        switch (callState) {
            case IMXCall.CALL_STATE_ENDED:
                mHangUpImageView.setVisibility(View.INVISIBLE);
                break;
            case IMXCall.CALL_STATE_CONNECTED:
                mHangUpImageView.setVisibility(View.VISIBLE);
                break;
            default:
                mHangUpImageView.setVisibility(View.VISIBLE);
                break;
        }

        // callview visibility management
        if (mCall.isVideo() && !callState.equals(IMXCall.CALL_STATE_ENDED)) {
            int visibility;

            switch (callState) {
                case IMXCall.CALL_STATE_WAIT_CREATE_OFFER:
                case IMXCall.CALL_STATE_INVITE_SENT:
                case IMXCall.CALL_STATE_RINGING:
                case IMXCall.CALL_STATE_CREATE_ANSWER:
                case IMXCall.CALL_STATE_CONNECTING:
                case IMXCall.CALL_STATE_CONNECTED:
                    visibility = View.VISIBLE;
                    break;
                default:
                    visibility = View.GONE;
                    break;
            }

            if ((null != mCall) && (visibility != mCall.getVisibility())) {
                mCall.setVisibility(visibility);
            }
        }

        // ringing management
        switch (callState) {
            case IMXCall.CALL_STATE_CONNECTING:
            case IMXCall.CALL_STATE_CREATE_ANSWER:
            case IMXCall.CALL_STATE_WAIT_LOCAL_MEDIA:
            case IMXCall.CALL_STATE_WAIT_CREATE_OFFER:
                VectorCallSoundManager.stopRinging();
                break;

            case IMXCall.CALL_STATE_CONNECTED:
                VectorCallSoundManager.stopRinging();
                final boolean fIsSpeakerPhoneOn = isSpeakerPhoneOn;
                VectorCallViewActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        VectorCallSoundManager.setCallSpeakerphoneOn(fIsSpeakerPhoneOn);
                        refreshSpeakerButton();
                    }
                });
                break;

            case IMXCall.CALL_STATE_RINGING:
                if (mAutoAccept) {
                    mAutoAccept = false;
                    mCall.answer();
                } else {
                    if (mCall.isIncoming()) {
                        VectorCallSoundManager.startRinging();
                    }
                    else {
                        VectorCallSoundManager.startRingBackSound();
                    }
                }
                break;

            default:
                // nothing to do..
                break;
        }
        Log.d(LOG_TAG, "## manageSubViews(): OUT");
    }

    private void saveCallView() {
        if ((null != mCall) && !mCall.getCallState().equals(IMXCall.CALL_STATE_ENDED) && (null != mCallView) && (null != mCallView.getParent())) {
            ViewGroup parent = (ViewGroup) mCallView.getParent();
            parent.removeView(mCallView);
            mSavedCallview = mCallView;

            EventStreamService.getInstance().displayCallInProgressNotification(mSession, mCall.getRoom(), mCall.getCallId());
            mCallView = null;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
        saveCallView();
        instance = null;

        // save audio settings
        AudioManager audioManager = (AudioManager) VectorCallViewActivity.this.getSystemService(Context.AUDIO_SERVICE);
        if (null != audioManager) {
            savedInstanceState.putBoolean(KEY_MIC_MUTE_STATUS, audioManager.isMicrophoneMute());
            savedInstanceState.putBoolean(KEY_SPEAKER_STATUS, audioManager.isSpeakerphoneOn());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        instance = this;
    }

    @Override
    public void onDestroy() {
        if (null != mCall) {
            mCall.removeListener(mListener);
        }

        if (mIsCallEnded || mIsCalleeBusy) {
            Log.d(LOG_TAG, "onDestroy: Hide the call notifications");
            EventStreamService.getInstance().hideCallNotifications();

            if (mIsCalleeBusy) {
                VectorCallSoundManager.startBusyCallSound();
            } else {
                VectorCallSoundManager.startEndCallSound();
            }
        }

        super.onDestroy();
    }

    // ************* SensorEventListener *************
    @Override
    public void onSensorChanged(SensorEvent event) {
        float distanceCentimeters = event.values[0];
        AudioManager audioManager = (AudioManager) VectorCallViewActivity.this.getSystemService(Context.AUDIO_SERVICE);

        Log.d(LOG_TAG,"## onSensorChanged(): "+String.format("distance=%.3f",distanceCentimeters));

        if(audioManager.isSpeakerphoneOn()) {
            Log.d(LOG_TAG, "## onSensorChanged(): Skipped due speaker ON");
        } else {
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();

            if (distanceCentimeters <= PROXIMITY_THRESHOLD) {
                //layoutParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                layoutParams.screenBrightness = 0;
                getWindow().setAttributes(layoutParams);

                //getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                Log.d(LOG_TAG, "## onSensorChanged(): force screen OFF");
            } else {
                // restore previous brightness (whatever it was)
                layoutParams.screenBrightness = -1;
                getWindow().setAttributes(layoutParams);

                //getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                Log.d(LOG_TAG, "## onSensorChanged(): force screen ON");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(LOG_TAG,"## onAccuracyChanged(): accuracy="+accuracy);
    }
    // ***********************************************
}
