/*
 * Copyright 2016 OpenMarket Ltd
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import org.matrix.androidsdk.call.CallSoundsManager;
import org.matrix.androidsdk.call.IMXCallListener;
import org.matrix.androidsdk.call.MXCallListener;
import org.matrix.androidsdk.call.VideoLayoutConfiguration;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.util.Log;

import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.services.EventStreamService;
import im.vector.util.CallsManager;
import im.vector.util.VectorUtils;
import im.vector.view.VectorPendingCallView;

/**
 * VectorCallViewActivity is the call activity.
 */
public class VectorCallViewActivity extends RiotAppCompatActivity implements SensorEventListener {
    private static final String LOG_TAG = VectorCallViewActivity.class.getSimpleName();

    public static final String EXTRA_MATRIX_ID = "CallViewActivity.EXTRA_MATRIX_ID";
    public static final String EXTRA_CALL_ID = "CallViewActivity.EXTRA_CALL_ID";
    public static final String EXTRA_UNKNOWN_DEVICES = "CallViewActivity.EXTRA_UNKNOWN_DEVICES";

    private static final String EXTRA_LOCAL_FRAME_LAYOUT = "EXTRA_LOCAL_FRAME_LAYOUT";

    private View mCallView;

    // account info
    private String mMatrixId = null;
    private MXSession mSession = null;

    // graphical items
    private ImageView mHangUpImageView;
    private ImageView mSpeakerSelectionView;
    private ImageView mAvatarView;
    private ImageView mMuteMicImageView;
    private ImageView mSwitchRearFrontCameraImageView;
    private ImageView mMuteLocalCameraView;
    private VectorPendingCallView mHeaderPendingCallView;
    private View mButtonsContainerView;

    private View mIncomingCallTabbar;
    private View mAcceptIncomingCallButton;

    // video screen management
    private Timer mVideoFadingEdgesTimer;
    private TimerTask mVideoFadingEdgesTimerTask;
    private static final short FADE_IN_DURATION = 250;
    private static final short FADE_OUT_DURATION = 2000;
    private static final short VIDEO_FADING_TIMER = 5000;

    // video display size
    private VideoLayoutConfiguration mLocalVideoLayoutConfig;

    // true when the user moves the preview
    private boolean mIsCustomLocalVideoLayoutConfig;

    // hard coded values are taken from specs:
    // - 585 as screen height reference
    // - 18 as space between the local video and the container view containing the setting buttons
    //private static final float RATIO_TOP_MARGIN_LOCAL_USER_VIDEO = (float)(462.0/585.0);
    private static final float VIDEO_TO_BUTTONS_VERTICAL_SPACE = (float) (18.0 / 585.0);
    /**
     * local user video height is set as percent of the total screen height
     **/
    private static final int PERCENT_LOCAL_USER_VIDEO_SIZE = 25;

    private int mSourceVideoWidth = 0;
    private int mSourceVideoHeight = 0;

    // sensor
    private SensorManager mSensorMgr;
    private Sensor mProximitySensor;

    private IMXCall mCall;
    private CallsManager mCallsManager;
    private int mPermissionCode;

    // on Samsung devices, the application is suspended when the screen is turned off
    // so the call must not be suspended
    private boolean mIsScreenOff = false;
    private final IMXCallListener mListener = new MXCallListener() {
        @Override
        public void onStateDidChange(String state) {
            final String fState = state;
            VectorCallViewActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "## onStateDidChange(): new state=" + fState);

                    manageSubViews();

                    if ((null != mCall) && mCall.isVideo() && mCall.getCallState().equals(IMXCall.CALL_STATE_CONNECTED)) {
                        mCall.updateLocalVideoRendererPosition(mLocalVideoLayoutConfig);
                    }
                }
            });
        }

        @Override
        public void onCallViewCreated(View callView) {
            Log.d(LOG_TAG, "## onViewLoading():");
            mCallView = callView;
            insertCallView();
        }

        @Override
        public void onReady() {
            // update UI before displaying the video
            computeVideoUiLayout();
            if (!mCall.isIncoming()) {
                Log.d(LOG_TAG, "## onReady(): placeCall()");
                mCall.placeCall(mLocalVideoLayoutConfig);
            } else {
                Log.d(LOG_TAG, "## onReady(): launchIncomingCall()");
                mCall.launchIncomingCall(mLocalVideoLayoutConfig);
            }
        }

        @Override
        public void onPreviewSizeChanged(int width, int height) {
            Log.d(LOG_TAG, "## onPreviewSizeChanged : " + width + " * " + height);

            mSourceVideoWidth = width;
            mSourceVideoHeight = height;

            if ((null != mCall) && mCall.isVideo() && mCall.getCallState().equals(IMXCall.CALL_STATE_CONNECTED)) {
                computeVideoUiLayout();
                mCall.updateLocalVideoRendererPosition(mLocalVideoLayoutConfig);
            }
        }
    };

    // track the audio config updates
    private final CallSoundsManager.OnAudioConfigurationUpdateListener mAudioConfigListener = new CallSoundsManager.OnAudioConfigurationUpdateListener() {
        @Override
        public void onAudioConfigurationUpdate() {
            refreshSpeakerButton();
            refreshMuteMicButton();
        }
    };

    // to drag the local video preview
    private final View.OnTouchListener mMainViewTouchListener = new View.OnTouchListener() {

        // fields
        private Rect mPreviewRect = null;
        private int mStartX = 0;
        private int mStartY = 0;

        /**
         * @return the local preview rect in pixels
         */
        private Rect computePreviewRect() {
            // get the height of the screen
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int screenHeight = metrics.heightPixels;
            int screenWidth = metrics.widthPixels;

            int left = mLocalVideoLayoutConfig.mX * screenWidth / 100;
            int right = (mLocalVideoLayoutConfig.mX + mLocalVideoLayoutConfig.mWidth) * screenWidth / 100;
            int top = mLocalVideoLayoutConfig.mY * screenHeight / 100;
            int bottom = (mLocalVideoLayoutConfig.mY + mLocalVideoLayoutConfig.mHeight) * screenHeight / 100;

            return new Rect(left, top, right, bottom);
        }

        private void updatePreviewFrame(int deltaX, int deltaY) {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int screenHeight = metrics.heightPixels;
            int screenWidth = metrics.widthPixels;
            int width = mPreviewRect.width();
            int height = mPreviewRect.height();

            // top left
            mPreviewRect.left = Math.max(0, mPreviewRect.left + deltaX);
            mPreviewRect.right = mPreviewRect.left + width;
            mPreviewRect.top = Math.max(0, mPreviewRect.top + deltaY);
            mPreviewRect.bottom = mPreviewRect.top + height;

            // right margin
            if (mPreviewRect.right > screenWidth) {
                mPreviewRect.right = screenWidth;
                mPreviewRect.left = mPreviewRect.right - width;
            }

            if (mPreviewRect.bottom > screenHeight) {
                mPreviewRect.bottom = screenHeight;
                mPreviewRect.top = screenHeight - height;
            }

            mLocalVideoLayoutConfig.mX = mPreviewRect.left * 100 / screenWidth;
            mLocalVideoLayoutConfig.mY = mPreviewRect.top * 100 / screenHeight;
            mLocalVideoLayoutConfig.mDisplayWidth = screenWidth;
            mLocalVideoLayoutConfig.mDisplayHeight = screenHeight;

            mIsCustomLocalVideoLayoutConfig = true;
            mCall.updateLocalVideoRendererPosition(mLocalVideoLayoutConfig);
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // call management
            if ((null != mCall) && mCall.isVideo() && TextUtils.equals(IMXCall.CALL_STATE_CONNECTED, mCall.getCallState())) {
                final int action = event.getAction();
                final int x = (int) event.getX();
                final int y = (int) event.getY();

                if (action == MotionEvent.ACTION_DOWN) {
                    Rect rect = computePreviewRect();

                    if (rect.contains(x, y)) {
                        mPreviewRect = rect;
                        mStartX = x;
                        mStartY = y;
                        return true;
                    }
                } else if ((null != mPreviewRect) && (action == MotionEvent.ACTION_MOVE)) {
                    updatePreviewFrame(x - mStartX, y - mStartY);
                    mStartX = x;
                    mStartY = y;
                    return true;
                } else {
                    mPreviewRect = null;
                }
            }
            return false;
        }
    };

    /**
     * Insert the callView in the activity (above the other room member).
     * The callView is setup in the SDK, and provided via dispatchOnViewLoading() in {@link #mListener}.
     */
    private void insertCallView() {
        if (null != mCallView) {
            // insert the call view above the avatar
            RelativeLayout layout = findViewById(R.id.call_layout);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
            params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            layout.removeView(mCallView);
            layout.setVisibility(View.VISIBLE);

            // add the call view only is the call is a video one
            if (mCall.isVideo()) {
                // reported by a rageshake
                if (null != mCallView.getParent()) {
                    ((ViewGroup) mCallView.getParent()).removeView(mCallView);
                }
                layout.addView(mCallView, 1, params);
            }
            // init as GONE, will be displayed according to call states..
            mCall.setVisibility(View.GONE);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "## onCreate(): IN");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_callview);

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

        String callId = intent.getStringExtra(EXTRA_CALL_ID);
        mMatrixId = intent.getStringExtra(EXTRA_MATRIX_ID);

        mSession = Matrix.getInstance(getApplicationContext()).getSession(mMatrixId);
        if ((null == mSession) || !mSession.isAlive()) {
            Log.e(LOG_TAG, "invalid session");
            finish();
            return;
        }

        mCall = CallsManager.getSharedInstance().getActiveCall();

        if ((null == mCall) || !TextUtils.equals(mCall.getCallId(), callId)) {
            Log.e(LOG_TAG, "invalid call");
            finish();
            return;
        }


        mCallsManager = CallsManager.getSharedInstance();

        // UI binding
        mHangUpImageView = findViewById(R.id.hang_up_button);
        mSpeakerSelectionView = findViewById(R.id.call_speaker_view);
        mAvatarView = VectorCallViewActivity.this.findViewById(R.id.call_other_member);
        mMuteMicImageView = VectorCallViewActivity.this.findViewById(R.id.mute_audio);
        mHeaderPendingCallView = findViewById(R.id.header_pending_callview);
        mSwitchRearFrontCameraImageView = findViewById(R.id.call_switch_camera_view);
        mMuteLocalCameraView = findViewById(R.id.mute_local_camera);
        mButtonsContainerView = findViewById(R.id.call_menu_buttons_layout_container);
        mIncomingCallTabbar = findViewById(R.id.incoming_call_menu_buttons_layout_container);
        mAcceptIncomingCallButton = findViewById(R.id.accept_incoming_call);
        View rejectIncomingCallButton = findViewById(R.id.reject_incoming_call);

        View mainContainerLayoutView = findViewById(R.id.call_layout);

        // when video is in full screen, touching the screen restore the edges (fade in)
        mainContainerLayoutView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fadeInVideoEdge();
                startVideoFadingEdgesScreenTimer();
            }
        });

        mainContainerLayoutView.setOnTouchListener(mMainViewTouchListener);

        ImageView roomLinkImageView = VectorCallViewActivity.this.findViewById(R.id.room_chat_link);
        roomLinkImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // simulate a back button press
                VectorCallViewActivity.this.finish();
                startRoomActivity();
            }
        });

        mSwitchRearFrontCameraImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRearFrontCamera();
                refreshSwitchRearFrontCameraButton();
                startVideoFadingEdgesScreenTimer();
            }
        });

        mMuteLocalCameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleVideoMute();
                refreshMuteVideoButton();
                startVideoFadingEdgesScreenTimer();
            }
        });

        mMuteMicImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMicMute();
                startVideoFadingEdgesScreenTimer();
            }
        });

        mHangUpImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallsManager.onHangUp(CallsManager.HANGUP_MSG_USER_CANCEL);
            }
        });

        mSpeakerSelectionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSpeaker();
                startVideoFadingEdgesScreenTimer();
            }
        });

        if (null != savedInstanceState) {
            mLocalVideoLayoutConfig = (VideoLayoutConfiguration) savedInstanceState.getSerializable(EXTRA_LOCAL_FRAME_LAYOUT);

            // check if the layout is not out of bounds
            if (null != mLocalVideoLayoutConfig) {
                boolean isPortrait = (Configuration.ORIENTATION_LANDSCAPE != getResources().getConfiguration().orientation);

                // do not keep the custom layout if the device orientation has been updated
                if (mLocalVideoLayoutConfig.mIsPortrait != isPortrait) {
                    mLocalVideoLayoutConfig = null;
                }
            }

            mIsCustomLocalVideoLayoutConfig = (null != mLocalVideoLayoutConfig);
        }

        // init call UI setting buttons
        manageSubViews();

        // the webview has been saved after a screen rotation
        // getParent() != null : the static value have been reused whereas it should not
        if ((null != mCallsManager.getCallView()) && (null == mCallsManager.getCallView().getParent())) {
            mCallView = mCallsManager.getCallView();
            insertCallView();

            if (null != mCallsManager.getVideoLayoutConfiguration()) {
                boolean isPortrait = (Configuration.ORIENTATION_LANDSCAPE != getResources().getConfiguration().orientation);

                // do not keep the custom layout if the device orientation has been updated
                if (mCallsManager.getVideoLayoutConfiguration().mIsPortrait == isPortrait) {
                    mLocalVideoLayoutConfig = mCallsManager.getVideoLayoutConfiguration();
                    mIsCustomLocalVideoLayoutConfig = true;
                }
            }
        } else {
            Log.d(LOG_TAG, "## onCreate(): Hide the call notifications");
            if (null != EventStreamService.getInstance()) {
                EventStreamService.getInstance().hideCallNotifications();
            }

            // create the callview asap
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (null != mCall.getCallView()) {
                        mCallView = mCall.getCallView();
                        insertCallView();
                        computeVideoUiLayout();
                        mCall.updateLocalVideoRendererPosition(mLocalVideoLayoutConfig);

                        // if the view is ready, launch the incoming call
                        if (TextUtils.equals(mCall.getCallState(), IMXCall.CALL_STATE_READY) && mCall.isIncoming()) {
                            mCall.launchIncomingCall(mLocalVideoLayoutConfig);
                        }
                    } else if (!mCall.isIncoming() && (TextUtils.equals(IMXCall.CALL_STATE_CREATED, mCall.getCallState()))) {
                        mCall.createCallView();
                    }
                }
            });
        }

        ImageView avatarView = findViewById(R.id.call_other_member);

        // the avatar side must be the half of the min screen side
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        int side = Math.min(size.x, size.y) / 2;

        RelativeLayout.LayoutParams avatarLayoutParams = (RelativeLayout.LayoutParams) avatarView.getLayoutParams();
        avatarLayoutParams.height = side;
        avatarLayoutParams.width = side;

        avatarView.setLayoutParams(avatarLayoutParams);

        VectorUtils.loadCallAvatar(this, mSession, avatarView, mCall.getRoom());

        mIncomingCallTabbar.setVisibility(CallsManager.getSharedInstance().isRinging() && mCall.isIncoming() ? View.VISIBLE : View.GONE);
        mPermissionCode = mCall.isVideo() ? CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL : CommonActivityUtils.REQUEST_CODE_PERMISSION_AUDIO_IP_CALL;

        // the user can only accept if the dedicated permissions are granted
        mAcceptIncomingCallButton.setVisibility(CommonActivityUtils.checkPermissions(mPermissionCode, this) ? View.VISIBLE : View.GONE);
        mAcceptIncomingCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(LOG_TAG, "Accept the incoming call");
                mAcceptIncomingCallButton.setVisibility(View.GONE);
                mCall.createCallView();
            }
        });

        rejectIncomingCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(LOG_TAG, "Reject the incoming call");
                mCallsManager.rejectCall();
            }
        });

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CommonActivityUtils.displayUnknownDevicesDialog(mSession, VectorCallViewActivity.this, (MXUsersDevicesMap<MXDeviceInfo>) intent.getSerializableExtra(VectorCallViewActivity.EXTRA_UNKNOWN_DEVICES), null);
            }
        });

        setupHeaderPendingCallView();
        Log.d(LOG_TAG, "## onCreate(): OUT");
    }

    /**
     * Customize the header pending call view to match the video/audio call UI.
     */
    private void setupHeaderPendingCallView() {
        if (null != mHeaderPendingCallView) {
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
                    VectorCallViewActivity.this.onBackPressed();
                }
            });

            // center the text horizontally and remove any padding
            LinearLayout textInfoContainerView = mHeaderPendingCallView.findViewById(R.id.call_info_container);
            textInfoContainerView.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);
            textInfoContainerView.setPadding(0, 0, 0, 0);

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
        if (null != mCall) {
            if (mCall.isVideo()) {
                // video call: set the backlight on
                Log.d(LOG_TAG, "## initBackLightManagement(): backlight is ON");
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // same as android:keepScreenOn="true" in layout
            } else {
                if ((null == mSensorMgr) && (null != mCall) && TextUtils.equals(mCall.getCallState(), IMXCall.CALL_STATE_CONNECTED)) {

                    // voice call: use the proximity sensor
                    mSensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);

                    // listener the proximity update
                    if (null == (mProximitySensor = mSensorMgr.getDefaultSensor(Sensor.TYPE_PROXIMITY))) {
                        Log.w(LOG_TAG, "## initBackLightManagement(): Warning - proximity sensor not supported");
                    } else {
                        // define the
                        mSensorMgr.registerListener(this, mProximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
                    }
                }
            }
        }

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
    public void finish() {
        super.finish();
        stopProximitySensor();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // called when the application is put in background
        if (!mIsScreenOff) {
            stopProximitySensor();
        }
    }

    @Override
    public void onRequestPermissionsResult(int aRequestCode, @NonNull String[] aPermissions, @NonNull int[] aGrantResults) {
        if (aRequestCode == mPermissionCode) {

            if (CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL == aRequestCode) {
                // the user can only accept if the dedicated permissions are granted
                mAcceptIncomingCallButton.setVisibility(CommonActivityUtils.onPermissionResultVideoIpCall(this, aPermissions, aGrantResults) ? View.VISIBLE : View.GONE);
            } else {
                // the user can only accept if the dedicated permissions are granted
                mAcceptIncomingCallButton.setVisibility(CommonActivityUtils.onPermissionResultAudioIpCall(this, aPermissions, aGrantResults) ? View.VISIBLE : View.GONE);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // on Samsung devices, the application is suspended when the screen is turned off
        // so the call must not be suspended
        if (!mIsScreenOff) {
            if (null != mCall) {
                mCall.onPause();
            }
        }

        if (null != mCall) {
            mCall.removeListener(mListener);
        }
        saveCallView();
        CallsManager.getSharedInstance().setCallActivity(null);
        CallSoundsManager.getSharedInstance(this).removeAudioConfigurationListener(mAudioConfigListener);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (null == mCallsManager.getActiveCall()) {
            Log.d(LOG_TAG, "## onResume() : the call does not exist anymore");
            finish();
            return;
        }

        mHeaderPendingCallView.checkPendingCall();

        EventStreamService.getInstance().displayCallInProgressNotification(mCall.getSession(), mCall.getRoom(), mCall.getCallId());
        // compute video UI layout position after rotation & apply new position
        computeVideoUiLayout();
        if ((null != mCall) && mCall.isVideo() && mCall.getCallState().equals(IMXCall.CALL_STATE_CONNECTED)) {
            mCall.updateLocalVideoRendererPosition(mLocalVideoLayoutConfig);
        }

        if (null != mCall) {
            mCall.addListener(mListener);

            if (!mIsScreenOff) {
                mCall.onResume();
            }

            mIsScreenOff = false;

            final String fState = mCall.getCallState();

            Log.d(LOG_TAG, "## onResume(): call state=" + fState);

            // restore video layout after rotation
            mCallView = mCallsManager.getCallView();
            insertCallView();

            // init the call button
            manageSubViews();

            // restore the backlight management
            initBackLightManagement();
            CallsManager.getSharedInstance().setCallActivity(this);
            CallSoundsManager.getSharedInstance(this).addAudioConfigurationListener(mAudioConfigListener);
        } else {
            this.finish();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        computeVideoUiLayout();
        if ((null != mCall) && mCall.isVideo() && mCall.getCallState().equals(IMXCall.CALL_STATE_CONNECTED)) {
            mCall.updateLocalVideoRendererPosition(mLocalVideoLayoutConfig);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);

        if (mIsCustomLocalVideoLayoutConfig) {
            savedInstanceState.putSerializable(EXTRA_LOCAL_FRAME_LAYOUT, mLocalVideoLayoutConfig);
        }
    }

    //==============================================================================================================
    // user interaction
    //==============================================================================================================

    /**
     * Toggle the mute feature of the mic.
     */
    private void toggleMicMute() {
        CallSoundsManager callSoundsManager = CallSoundsManager.getSharedInstance(this);
        callSoundsManager.setMicrophoneMute(!callSoundsManager.isMicrophoneMute());
    }

    /**
     * Toggle the mute feature of the local camera.
     */
    private void toggleVideoMute() {
        if (null != mCall) {
            if (mCall.isVideo()) {
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
        mCallsManager.toggleSpeaker();
    }

    /**
     * Toggle the cameras.
     */
    private void toggleRearFrontCamera() {
        boolean wasCameraSwitched = false;

        if ((null != mCall) && mCall.isVideo()) {
            wasCameraSwitched = mCall.switchRearFrontCamera();
        } else {
            Log.w(LOG_TAG, "## toggleRearFrontCamera(): Skipped");
        }
        Log.w(LOG_TAG, "## toggleRearFrontCamera(): done? " + wasCameraSwitched);
    }

    /**
     * Helper method to start the room activity.
     */
    private void startRoomActivity() {
        if (null != mCall) {
            String roomId = mCall.getRoom().getRoomId();

            if (null != VectorApp.getCurrentActivity()) {
                HashMap<String, Object> params = new HashMap<>();
                params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mMatrixId);
                params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
                CommonActivityUtils.goToRoomPage(VectorApp.getCurrentActivity(), mSession, params);
            } else {
                Intent intent = new Intent(getApplicationContext(), VectorRoomActivity.class);
                intent.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
                intent.putExtra(VectorRoomActivity.EXTRA_MATRIX_ID, mMatrixId);
                startActivity(intent);
            }
        }
    }

    //==============================================================================================================
    // fade management
    //==============================================================================================================

    /**
     * Stop the video fading timer.
     */
    private void stopVideoFadingEdgesScreenTimer() {
        if (null != mVideoFadingEdgesTimer) {
            mVideoFadingEdgesTimer.cancel();
            mVideoFadingEdgesTimer = null;
            mVideoFadingEdgesTimerTask = null;
        }
    }

    /**
     * Start the video fading timer.
     */
    private void startVideoFadingEdgesScreenTimer() {
        // do not hide the overlay during a voice call
        if ((null == mCall) || !mCall.isVideo()) {
            return;
        }

        // stop current timer in progress
        stopVideoFadingEdgesScreenTimer();

        try {
            mVideoFadingEdgesTimer = new Timer();
            mVideoFadingEdgesTimerTask = new TimerTask() {
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopVideoFadingEdgesScreenTimer();
                            fadeOutVideoEdge();
                        }
                    });
                }
            };

            mVideoFadingEdgesTimer.schedule(mVideoFadingEdgesTimerTask, VIDEO_FADING_TIMER);
        } catch (Throwable throwable) {
            Log.e(LOG_TAG, "## startVideoFadingEdgesScreenTimer() " + throwable.getMessage());

            stopVideoFadingEdgesScreenTimer();
            fadeOutVideoEdge();
        }
    }

    /**
     * Set the fading effect on the view above the UI video.
     *
     * @param aOpacity      UTILS_OPACITY_FULL to fade out, UTILS_OPACITY_NONE to fade in
     * @param aAnimDuration animation duration in milliseconds
     */
    private void fadeVideoEdge(final float aOpacity, int aAnimDuration) {
        if (null != mHeaderPendingCallView) {
            if (aOpacity != mHeaderPendingCallView.getAlpha()) {
                mHeaderPendingCallView.animate().alpha(aOpacity).setDuration(aAnimDuration).setInterpolator(new AccelerateInterpolator());
            }
        }

        if (null != mButtonsContainerView) {
            if (aOpacity != mButtonsContainerView.getAlpha()) {
                mButtonsContainerView.animate().alpha(aOpacity).setDuration(aAnimDuration).setInterpolator(new AccelerateInterpolator()).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);

                        // set to GONE after the fade out, so buttons can not not be accessed by the user
                        if (CommonActivityUtils.UTILS_OPACITY_FULL == aOpacity) {
                            mButtonsContainerView.setVisibility(View.GONE);
                        } else {
                            // restore visibility after fade in
                            mButtonsContainerView.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        }
    }

    /**
     * Remove the views (buttons settings + pending call view) above the video call with a fade out animation.
     */
    private void fadeOutVideoEdge() {
        fadeVideoEdge(CommonActivityUtils.UTILS_OPACITY_FULL, FADE_OUT_DURATION);
    }

    /**
     * Restore the views (buttons settings + pending call view) above the video call with a fade in animation.
     */
    private void fadeInVideoEdge() {
        fadeVideoEdge(CommonActivityUtils.UTILS_OPACITY_NONE, FADE_IN_DURATION);
    }

    //==============================================================================================================
    // UI items refresh
    //==============================================================================================================

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
        if (null == mLocalVideoLayoutConfig) {
            mLocalVideoLayoutConfig = new VideoLayoutConfiguration();
        }

        // get the height of the screen
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenHeight = metrics.heightPixels;
        int screenWidth = metrics.widthPixels;

        // compute action bar size: the video Y component starts below the action bar
        int actionBarHeight;
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(typedValue.data, getResources().getDisplayMetrics());
            screenHeight -= actionBarHeight;
        }

        View mMenuButtonsContainerView = VectorCallViewActivity.this.findViewById(R.id.hang_up_button);
        ViewGroup.LayoutParams layout = mMenuButtonsContainerView.getLayoutParams();

        if (0 == mLocalVideoLayoutConfig.mWidth) {
            mLocalVideoLayoutConfig.mWidth = PERCENT_LOCAL_USER_VIDEO_SIZE;
        }

        if (0 == mLocalVideoLayoutConfig.mHeight) {
            mLocalVideoLayoutConfig.mHeight = PERCENT_LOCAL_USER_VIDEO_SIZE;
        }

        if ((0 != mSourceVideoWidth) && (0 != mSourceVideoHeight)) {
            int previewWidth = screenWidth * mLocalVideoLayoutConfig.mWidth / 100;
            int previewHeight = screenHeight * mLocalVideoLayoutConfig.mHeight / 100;

            int sourceRatio = mSourceVideoWidth * 100 / mSourceVideoHeight;
            int previewRatio = previewWidth * 100 / previewHeight;

            // there is an aspect ratio update
            if (sourceRatio != previewRatio) {
                int maxPreviewWidth = screenWidth * PERCENT_LOCAL_USER_VIDEO_SIZE / 100;
                int maxPreviewHeight = screenHeight * PERCENT_LOCAL_USER_VIDEO_SIZE / 100;

                if ((maxPreviewHeight * sourceRatio / 100) > maxPreviewWidth) {
                    mLocalVideoLayoutConfig.mHeight = maxPreviewWidth * 100 * 100 / sourceRatio / screenHeight;
                    mLocalVideoLayoutConfig.mWidth = PERCENT_LOCAL_USER_VIDEO_SIZE;
                } else {
                    mLocalVideoLayoutConfig.mWidth = maxPreviewHeight * sourceRatio / screenWidth;
                    mLocalVideoLayoutConfig.mHeight = PERCENT_LOCAL_USER_VIDEO_SIZE;
                }
            }
        } else {
            mLocalVideoLayoutConfig.mWidth = PERCENT_LOCAL_USER_VIDEO_SIZE;
            mLocalVideoLayoutConfig.mHeight = PERCENT_LOCAL_USER_VIDEO_SIZE;
        }

        if (!mIsCustomLocalVideoLayoutConfig) {
            int buttonsContainerHeight = (mButtonsContainerView.getVisibility() == View.VISIBLE) ? layout.height * 100 / screenHeight : 0;
            int bottomLeftMargin = (int) (VIDEO_TO_BUTTONS_VERTICAL_SPACE * screenHeight * 100 / screenHeight);

            mLocalVideoLayoutConfig.mX = bottomLeftMargin * screenHeight / screenWidth;
            mLocalVideoLayoutConfig.mY = 100 - bottomLeftMargin - buttonsContainerHeight - mLocalVideoLayoutConfig.mHeight;
        }

        mLocalVideoLayoutConfig.mIsPortrait = (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE);
        mLocalVideoLayoutConfig.mDisplayWidth = screenWidth;
        mLocalVideoLayoutConfig.mDisplayHeight = screenHeight;

        Log.d(LOG_TAG, "## computeVideoUiLayout() : x " + mLocalVideoLayoutConfig.mX + " y " + mLocalVideoLayoutConfig.mY);
        Log.d(LOG_TAG, "## computeVideoUiLayout() : mWidth " + mLocalVideoLayoutConfig.mWidth + " mHeight " + mLocalVideoLayoutConfig.mHeight);
    }


    /**
     * Update the mute mic icon according to mute mic status.
     */
    private void refreshMuteMicButton() {
        // update icon
        int iconId = CallSoundsManager.getSharedInstance(this).isMicrophoneMute() ? R.drawable.ic_material_mic_off_pink_red : R.drawable.ic_material_mic_off_grey;
        mMuteMicImageView.setImageResource(iconId);
    }

    /**
     * Update the mute speaker icon according to speaker status.
     */
    public void refreshSpeakerButton() {
        // update icon
        int iconId = CallSoundsManager.getSharedInstance(this).isSpeakerphoneOn() ? R.drawable.ic_material_speaker_phone_pink_red : R.drawable.ic_material_speaker_phone_grey;
        mSpeakerSelectionView.setImageResource(iconId);
    }

    /**
     * Update the mute video icon.
     */
    private void refreshMuteVideoButton() {
        if ((null != mCall) && mCall.isVideo()) {
            mMuteLocalCameraView.setVisibility(View.VISIBLE);

            boolean isMuted = mCall.isVideoRecordingMuted();
            Log.d(LOG_TAG, "## refreshMuteVideoButton(): isMuted=" + isMuted);

            // update icon
            int iconId = isMuted ? R.drawable.ic_material_videocam_off_pink_red : R.drawable.ic_material_videocam_off_grey;
            mMuteLocalCameraView.setImageResource(iconId);
        } else {
            Log.d(LOG_TAG, "## refreshMuteVideoButton(): View.INVISIBLE");
            mMuteLocalCameraView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Update the switch camera icon.
     * Note that, this icon is only active if the device supports
     * camera switching (See {@link IMXCall#isSwitchCameraSupported()})
     */
    private void refreshSwitchRearFrontCameraButton() {
        if ((null != mCall) && mCall.isVideo() && mCall.isSwitchCameraSupported()) {
            mSwitchRearFrontCameraImageView.setVisibility(View.VISIBLE);

            boolean isSwitched = mCall.isCameraSwitched();
            Log.d(LOG_TAG, "## refreshSwitchRearFrontCameraButton(): isSwitched=" + isSwitched);

            // update icon
            int iconId = isSwitched ? R.drawable.ic_material_switch_video_pink_red : R.drawable.ic_material_switch_video_grey;
            mSwitchRearFrontCameraImageView.setImageResource(iconId);
        } else {
            Log.d(LOG_TAG, "## refreshSwitchRearFrontCameraButton(): View.INVISIBLE");
            mSwitchRearFrontCameraImageView.setVisibility(View.INVISIBLE);
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
                initBackLightManagement();
                mHangUpImageView.setVisibility(View.VISIBLE);
                break;
            default:
                mHangUpImageView.setVisibility(View.VISIBLE);
                break;
        }

        if (mCall.isVideo()) {
            switch (callState) {
                case IMXCall.CALL_STATE_CONNECTED:
                    startVideoFadingEdgesScreenTimer();
                    break;

                default:
                    stopVideoFadingEdgesScreenTimer();
                    break;
            }
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

        // other management
        switch (callState) {
            case IMXCall.CALL_STATE_RINGING:
                // the call view is created when the user accepts the call.
                if (mCall.isIncoming()) {
                    mCall.answer();
                    mIncomingCallTabbar.setVisibility(View.GONE);
                }
                break;
            default:
                // nothing to do..
                break;
        }
        Log.d(LOG_TAG, "## manageSubViews(): OUT");
    }

    /**
     * Save the call view before leaving the activity.
     */
    private void saveCallView() {
        if ((null != mCall) && !mCall.getCallState().equals(IMXCall.CALL_STATE_ENDED) && (null != mCallView) && (null != mCallView.getParent())) {

            // warn the call that the activity is going to be paused.
            // as the rendering is DSA, it saves time to close the activity while removing mCallView
            mCall.onPause();

            ViewGroup parent = (ViewGroup) mCallView.getParent();
            parent.removeView(mCallView);
            mCallsManager.setCallView(mCallView);

            mCallsManager.setVideoLayoutConfiguration(mLocalVideoLayoutConfig);

            // remove the call layout to avoid having a black screen
            RelativeLayout layout = findViewById(R.id.call_layout);
            layout.setVisibility(View.GONE);
            mCallView = null;
        }
    }

    //==============================================================================================================
    // Proximity sensor management
    //==============================================================================================================

    private static final float PROXIMITY_THRESHOLD = 3.0f; // centimeters
    private PowerManager.WakeLock mWakeLock;
    private int mField = 0x00000020;

    /**
     * Init the screen management to be able to turn the screen on/off
     */
    private void initScreenManagement() {
        try {
            try {
                mField = PowerManager.class.getClass().getField("PROXIMITY_SCREEN_OFF_WAKE_LOCK").getInt(null);
            } catch (Throwable ignored) {
                Log.e(LOG_TAG, "## initScreenManagement " + ignored.getMessage());
            }

            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(mField, getLocalClassName());
        } catch (Exception e) {
            Log.e(LOG_TAG, "## initScreenManagement() : failed " + e.getMessage());
        }
    }

    /**
     * Turn the screen off
     */
    private void turnScreenOff() {
        if (null == mWakeLock) {
            initScreenManagement();
        }

        try {
            if ((null != mWakeLock) && !mWakeLock.isHeld()) {
                mWakeLock.acquire();
                mIsScreenOff = true;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## turnScreenOff() failed");
        }

        // set the back light level to the minimum
        // fallback if the previous trick does not work
        if (null != getWindow() && (null != getWindow().getAttributes())) {
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.screenBrightness = 0;
            getWindow().setAttributes(layoutParams);
        }
    }

    /**
     * Turn the screen on
     */
    private void turnScreenOn() {
        try {
            if (null != mWakeLock) {
                mWakeLock.release();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## turnScreenOn() failed");
        }

        mIsScreenOff = false;
        mWakeLock = null;

        // restore previous brightness (whatever it was)
        if (null != getWindow() && (null != getWindow().getAttributes())) {
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.screenBrightness = -1;
            getWindow().setAttributes(layoutParams);
        }
    }

    /**
     * Stop the proximity sensor.
     */
    private void stopProximitySensor() {
        // do not release the proximity sensor while pausing the activity
        // when the screen is turned off, the activity is paused.
        if ((null != mProximitySensor) && (null != mSensorMgr)) {
            mSensorMgr.unregisterListener(this);
            mProximitySensor = null;
            mSensorMgr = null;
        }

        turnScreenOn();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (null != event) {
            float distanceCentimeters = event.values[0];

            Log.d(LOG_TAG, "## onSensorChanged(): " + String.format(VectorApp.getApplicationLocale(), "distance=%.3f", distanceCentimeters));

            if (CallsManager.getSharedInstance().isSpeakerphoneOn()) {
                Log.d(LOG_TAG, "## onSensorChanged(): Skipped due speaker ON");
            } else {
                if (distanceCentimeters <= PROXIMITY_THRESHOLD) {
                    turnScreenOff();
                    Log.d(LOG_TAG, "## onSensorChanged(): force screen OFF");
                } else {
                    turnScreenOn();
                    Log.d(LOG_TAG, "## onSensorChanged(): force screen ON");
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(LOG_TAG, "## onAccuracyChanged(): accuracy=" + accuracy);
    }
}
