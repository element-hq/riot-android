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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.Log;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.rest.model.RoomMember;
import im.vector.VectorApp;
import im.vector.Matrix;
import im.vector.R;
import im.vector.services.EventStreamService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * CallViewActivity is the call activity.
 */
public class CallViewActivity extends FragmentActivity {
    private static final String LOG_TAG = "CallViewActivity";

    public static final String EXTRA_MATRIX_ID = "CallViewActivity.EXTRA_MATRIX_ID";
    public static final String EXTRA_CALL_ID = "CallViewActivity.EXTRA_CALL_ID";
    public static final String EXTRA_AUTO_ACCEPT = "CallViewActivity.EXTRA_AUTO_ACCEPT";

    private static CallViewActivity instance = null;

    private static View mSavedCallview = null;
    private static IMXCall mCall = null;

    private View mCallView;

    // account info
    private String mMatrixId = null;
    private MXSession mSession = null;
    private String mCallId = null;

    // call info
    private RoomMember mOtherMember = null;
    private boolean mAutoAccept = false;
    private boolean mIsCallEnded = false;

    // graphical items
    private View mAcceptRejectLayout;
    private Button mCancelButton;
    private Button mAcceptButton;
    private Button mRejectButton;
    private Button mStopButton;
    private ImageView mSpeakerSelectionView;
    private TextView mCallStateTextView;

    // sounds management
    private static MediaPlayer mRingingPlayer = null;
    private static MediaPlayer mRingbackPlayer = null;
    private static MediaPlayer mCallEndPlayer = null;

    private static Ringtone mRingTone = null;
    private static Ringtone mRingbackTone = null;
    private static Ringtone mCallEndTone = null;

    private static final int DEFAULT_PERCENT_VOLUME = 10;
    private static final int FIRST_PERCENT_VOLUME = 10;
    private static boolean firstCallAlert = true;
    private static int mCallVolume = 0;

    private IMXCall.MXCallListener mListener = new IMXCall.MXCallListener() {
        @Override
        public void onStateDidChange(String state) {
            if (null != getInstance()) {
                final String fState = state;
                CallViewActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(LOG_TAG, "onStateDidChange " + fState);
                        manageSubViews();
                    }
                });
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

            Log.d(LOG_TAG, "onCallError " + error);

            if (null != context) {
                if (IMXCall.CALL_ERROR_USER_NOT_RESPONDING.equals(error)) {
                    showToast(context.getString(R.string.call_error_user_not_responding));
                } else if (IMXCall.CALL_ERROR_ICE_FAILED.equals(error)) {
                    showToast(context.getString(R.string.call_error_ice_failed));
                } else if (IMXCall.CALL_ERROR_CAMERA_INIT_FAILED.equals(error)) {
                    showToast(context.getString(R.string.call_error_camera_init_failed));
                }
            }
        }

        @Override
        public void onViewLoading(View callview) {
            Log.d(LOG_TAG, "onViewLoading");

            mCallView = callview;
            insertCallView(mOtherMember.avatarUrl);
        }

        @Override
        public void onViewReady() {
            Log.d(LOG_TAG, "onViewReady");

            if (!mCall.isIncoming()) {
                mCall.placeCall();
            } else {
                mCall.launchIncomingCall();
            }
        }

        /**
         * The call was answered on another device
         */
        @Override
        public void onCallAnsweredElsewhere() {
            CallViewActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "onCallAnsweredElsewhere");
                    clearCallData();
                    CallViewActivity.this.finish();
                }
            });
        }

        @Override
        public void onCallEnd() {
            CallViewActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "onCallEnd");

                    clearCallData();
                    mIsCallEnded = true;
                    CallViewActivity.this.finish();
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
        Boolean res = false;

        if ((null != mCall) && (null == instance)) {
            res = mCall.getCallId().equals(callId);
            // clear unexpected call.
            getActiveCall();
        }

        return res;
    }

    /**
     * @return the active call
     */
    public static IMXCall getActiveCall() {
        // not currently displayed
        if ((instance == null) && (null != mCall)) {
            // check if the call can be resume
            // or it's still valid
            if (!canCallBeResumed() || (null == mCall.getSession().mCallsManager.callWithCallId(mCall.getCallId()))) {
                EventStreamService.getInstance().hidePendingCallNotification(mCall.getCallId());
                mCall = null;
                mSavedCallview = null;
            }
        }

        return mCall;
    }

    /**
     * @return the callViewActivity instance
     */
    public static CallViewActivity getInstance() {
        return instance;
    }

    /**
     * release the call info
     */
    private void clearCallData() {
        if (null != mCall) {
            mCall.removeListener(mListener);
        }

        mCall = null;
        mCallView = null;
        mSavedCallview = null;
    }

    /**
     * Insert the callView in the activity (above the other room member)
     * @param avatarUrl the other member avatar
     */
    private void insertCallView(String avatarUrl) {
        ImageView avatarView = (ImageView)CallViewActivity.this.findViewById(R.id.call_other_member);
        avatarView.setImageResource(R.drawable.ic_contact_picture_holo_light);

        if (!TextUtils.isEmpty(avatarUrl)) {
            int size = CallViewActivity.this.getResources().getDimensionPixelSize(R.dimen.member_list_avatar_size);
            mSession.getMediasCache().loadAvatarThumbnail(mSession.getHomeserverConfig(), avatarView, avatarUrl, size);
        }

        RelativeLayout layout = (RelativeLayout)CallViewActivity.this.findViewById(R.id.call_layout);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        layout.addView(mCallView, 1, params);

        mCall.setVisibility(View.GONE);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
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

        mCall = mSession.mCallsManager.callWithCallId(mCallId);

        if (null == mCall) {
            Log.e(LOG_TAG, "invalid callId");
            finish();
            return;
        }

        mAutoAccept = intent.hasExtra(EXTRA_AUTO_ACCEPT);

        initMediaPlayerVolume();

        // assume that it is a 1:1 call.
        mOtherMember = mCall.getRoom().callees().get(0);

        // the webview has been saved after a screen rotation
        // getParent() != null : the static value have been reused whereas it should not
        if ((null != mSavedCallview) && (null == mSavedCallview.getParent())) {
            mCallView = mSavedCallview;
            insertCallView(mOtherMember.avatarUrl);
            manageSubViews();
        } else {
            EventStreamService.getInstance().hidePendingCallNotification(mCall.getCallId());
            mSavedCallview = null;
            // init the call button
            manageSubViews();

            // create the callview asap
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mCall.createCallView();
                }
            });
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // assume that the user cancels the call if it is ringing
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!canCallBeResumed()) {
                if (null != mCall) {
                    mCall.hangup("");
                }
            } else {
                saveCallView();
            }
        } else if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) || (keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            // this is a trick to reduce the ring volume :
            // when the call is ringing, the AudioManager.Mode switch to MODE_IN_COMMUNICATION
            // so the volume is the next call one whereas the user expects to reduce the ring volume.
            if ((null != mCall) && mCall.getCallState().equals(IMXCall.CALL_STATE_RINGING)) {
                AudioManager audioManager = (AudioManager) CallViewActivity.this.getSystemService(Context.AUDIO_SERVICE);
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
        stopRinging();
        instance = null;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (null != mCall) {
            mCall.onPause();
            mCall.removeListener(mListener);
        }
        VectorApp.setCurrentActivity(null);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (null != mCall) {
            mCall.onResume();

            if (null != mListener) {
                mCall.addListener(mListener);
            }

            final String fState = mCall.getCallState();
            CallViewActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "onResume " + fState);
                    manageSubViews();
                }
            });

        } else {
            this.finish();
        }

        VectorApp.setCurrentActivity(this);
    }

    private void refreshSpeakerButton() {
        if ((null != mCall) && mCall.getCallState().equals(IMXCall.CALL_STATE_CONNECTED)) {
            mSpeakerSelectionView.setVisibility(View.VISIBLE);

            AudioManager audioManager = (AudioManager) CallViewActivity.this.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager.isSpeakerphoneOn()) {
                mSpeakerSelectionView.setImageResource(R.drawable.ic_material_call);
            } else {
                mSpeakerSelectionView.setImageResource(R.drawable.ic_material_volume);
            }
            CallViewActivity.this.setVolumeControlStream(audioManager.getMode());

        } else {
            mSpeakerSelectionView.setVisibility(View.GONE);
        }
    }

    /**
     * hangup the call.
     */
    private void onHangUp() {
        mSavedCallview = null;

        if (null != mCall) {
            mCall.hangup("");
        }
    }

    /**
     * Update the text value
     * @param stateText
     */
    private void updateStateTextView(String stateText, boolean displayState) {
        String text = mOtherMember.getName();

        if (!TextUtils.isEmpty(stateText) && displayState) {
            text += " (" + stateText + ")";
        }

        mCallStateTextView.setText(text);
    }

    /**
     * Init the buttons layer
     */
    private void manageSubViews() {
        // sanity check
        // the call could have been destroyed between call.
        if (null == mCall) {
            Log.d(LOG_TAG, "manageSubViews nothing to do");
            return;
        }

        // initialize buttons
        if (null == mAcceptRejectLayout) {
            mAcceptRejectLayout = findViewById(R.id.layout_accept_reject);
            mAcceptButton = (Button) findViewById(R.id.accept_button);
            mRejectButton = (Button) findViewById(R.id.reject_button);
            mCancelButton = (Button) findViewById(R.id.cancel_button);
            mStopButton = (Button) findViewById(R.id.stop_button);
            mSpeakerSelectionView = (ImageView) findViewById(R.id.call_speaker_view);

            mCallStateTextView = (TextView) findViewById(R.id.call_state_text);

            mAcceptButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mCall) {
                        mCall.answer();
                    }
                }
            });

            mRejectButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onHangUp();
                }
            });

            mCancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onHangUp();
                }
            });

            mStopButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onHangUp();
                }
            });

            mSpeakerSelectionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mCall) {
                        mCall.toggleSpeaker();
                        refreshSpeakerButton();
                    }
                }
            });

            updateStateTextView("", false);
        }

        String callState = mCall.getCallState();

        Log.d(LOG_TAG, "manageSubViews callState : " + callState);

        // hide / show avatar
        ImageView avatarView = (ImageView)CallViewActivity.this.findViewById(R.id.call_other_member);
        if (null != avatarView) {
            avatarView.setVisibility((callState.equals(IMXCall.CALL_STATE_CONNECTED) && mCall.isVideo()) ? View.GONE : View.VISIBLE);
        }

        refreshSpeakerButton();

        // display the button according to the call state
        if (callState.equals(IMXCall.CALL_STATE_ENDED)) {
            mAcceptRejectLayout.setVisibility(View.GONE);
            mCancelButton.setVisibility(View.GONE);
            mStopButton.setVisibility(View.GONE);
        } else if (callState.equals(IMXCall.CALL_STATE_CONNECTED)) {
            mAcceptRejectLayout.setVisibility(View.GONE);
            mCancelButton.setVisibility(View.GONE);
            mStopButton.setVisibility(View.VISIBLE);
        } else {
            mAcceptRejectLayout.setVisibility(mCall.isIncoming() ? View.VISIBLE : View.GONE);
            mCancelButton.setVisibility(mCall.isIncoming() ?  View.GONE : View.VISIBLE);
            mStopButton.setVisibility(View.GONE);
        }

        // display the callview only when the preview is displayed
        if (mCall.isVideo() && !callState.equals(IMXCall.CALL_STATE_ENDED)) {
            int visibility;

            if (callState.equals(IMXCall.CALL_STATE_WAIT_CREATE_OFFER) ||
                    callState.equals(IMXCall.CALL_STATE_INVITE_SENT) ||
                    callState.equals(IMXCall.CALL_STATE_RINGING) ||
                    callState.equals(IMXCall.CALL_STATE_CREATE_ANSWER) ||
                    callState.equals(IMXCall.CALL_STATE_CONNECTING) ||
                    callState.equals(IMXCall.CALL_STATE_CONNECTED)) {
                visibility = View.VISIBLE;
            } else {
                visibility = View.GONE;
            }

            if ((null != mCall) && (visibility != mCall.getVisibility())) {
                mCall.setVisibility(visibility);
            }
        }

        // display the callstate
        if (callState.equals(IMXCall.CALL_STATE_CONNECTING) || callState.equals(IMXCall.CALL_STATE_CREATE_ANSWER)
                || callState.equals(IMXCall.CALL_STATE_WAIT_LOCAL_MEDIA) || callState.equals(IMXCall.CALL_STATE_WAIT_CREATE_OFFER)
                ) {
            mAcceptButton.setAlpha(0.5f);
            mAcceptButton.setEnabled(false);
            updateStateTextView(getResources().getString(R.string.call_connecting), true);
            mCallStateTextView.setVisibility(View.VISIBLE);
            stopRinging();
        } else if (callState.equals(IMXCall.CALL_STATE_CONNECTED)) {
            stopRinging();

            CallViewActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AudioManager audioManager = (AudioManager) CallViewActivity.this.getSystemService(Context.AUDIO_SERVICE);
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, mCallVolume, 0);
                    MXCallsManager.setSpeakerphoneOn(CallViewActivity.this, mCall.isVideo());
                    refreshSpeakerButton();
                }
            });

            updateStateTextView(getResources().getString(R.string.call_connected), false);
            mCallStateTextView.setVisibility(mCall.isVideo() ? View.GONE : View.VISIBLE);
        } else if (callState.equals(IMXCall.CALL_STATE_ENDED)) {
            updateStateTextView(getResources().getString(R.string.call_ended), true);
            mCallStateTextView.setVisibility(View.VISIBLE);
        } else if (callState.equals(IMXCall.CALL_STATE_RINGING)) {
            if (mCall.isIncoming()) {
                if (mCall.isVideo()) {
                    updateStateTextView(getResources().getString(R.string.incoming_video_call), true);
                } else {
                    updateStateTextView(getResources().getString(R.string.incoming_voice_call), true);
                }
            } else {
                updateStateTextView(getResources().getString(R.string.call_ring), true);
            }
            mCallStateTextView.setVisibility(View.VISIBLE);

            if (mAutoAccept) {
                mAutoAccept = false;
                mAcceptButton.setAlpha(0.5f);
                mAcceptButton.setEnabled(false);
                mCallStateTextView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mCall.answer();
                    }
                }, 100);
            } else {
                mAcceptButton.setAlpha(1.0f);
                mAcceptButton.setEnabled(true);

                if (mCall.isIncoming()) {
                    startRinging(CallViewActivity.this);
                } else {
                    startRingbackSound(CallViewActivity.this);
                }
            }
        }
    }

    private void saveCallView() {
        if ((null != mCall) && !mCall.getCallState().equals(IMXCall.CALL_STATE_ENDED) && (null != mCallView) && (null != mCallView.getParent())) {
            ViewGroup parent = (ViewGroup) mCallView.getParent();
            parent.removeView(mCallView);
            mSavedCallview = mCallView;

            EventStreamService.getInstance().displayPendingCallNotification(mSession, mCall.getRoom(), mCall.getCallId());
            mCallView = null;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
        saveCallView();
        instance = null;
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

        if (mIsCallEnded) {
            EventStreamService.getInstance().hidePendingCallNotification(mCallId);
            startEndCallSound(this);
        }

        super.onDestroy();
    }

    /**
     * Provices a ringtone from a resource and a filename.
     * The audio file must have a ANDROID_LOOP metatada set to true to loop the sound.
     * @param resid The audio resource.
     * @param filename the audio filename
     * @return a RingTone, null if the operation fails.
     */
    static Ringtone getRingTone(Context context, int resid, String filename) {
        Ringtone ringtone = null;

        try {
            Uri ringToneUri = null;
            File directory = new File(Environment.getExternalStorageDirectory(), "/" + context.getApplicationContext().getPackageName().hashCode() + "/Audio/");

            // create the directory if it does not exist
            if (!directory.exists()) {
                directory.mkdirs();
            }

            File file = new File(directory + "/", filename);

            // if the file exists, check if the resource has been created
            if (file.exists()) {
                Cursor cursor = context.getContentResolver().query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        new String[] { MediaStore.Audio.Media._ID },
                        MediaStore.Audio.Media.DATA + "=? ",
                        new String[] {file.getAbsolutePath()}, null);

                if ((null != cursor) && cursor.moveToFirst()) {
                    int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
                    ringToneUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/media"), "" + id);
                }
            }

            // the Uri has been received
            if (null == ringToneUri) {
                // create the file
                if (!file.exists()) {
                    try {
                        byte[] readData = new byte[1024];
                        InputStream fis = context.getResources().openRawResource(resid);
                        FileOutputStream fos = new FileOutputStream(file);
                        int i = fis.read(readData);

                        while (i != -1) {
                            fos.write(readData, 0, i);
                            i = fis.read(readData);
                        }

                        fos.close();
                    } catch (Exception e) {
                    }
                }

                // and the resource Uri
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DATA, file.getAbsolutePath());
                values.put(MediaStore.MediaColumns.TITLE, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/ogg");
                values.put(MediaStore.MediaColumns.SIZE, file.length());
                values.put(MediaStore.Audio.Media.ARTIST, R.string.app_name);
                values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
                values.put(MediaStore.Audio.Media.IS_NOTIFICATION, true);
                values.put(MediaStore.Audio.Media.IS_ALARM, true);
                values.put(MediaStore.Audio.Media.IS_MUSIC, true);

                ringToneUri = context.getContentResolver() .insert(MediaStore.Audio.Media.getContentUriForPath(file.getAbsolutePath()), values);
            }

            if (null != ringToneUri) {
                ringtone = RingtoneManager.getRingtone(context, ringToneUri);
            }
        } catch (Exception e) {

        }

        return ringtone;
    }

    /**
     * Initialize the audio volume.
     */
    private void initMediaPlayerVolume() {
        AudioManager audioManager = (AudioManager)this.getSystemService(Context.AUDIO_SERVICE);

        // use the ringing volume to initialize the playing volume
        // it does not make sense to ring louder
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int minValue = firstCallAlert ? FIRST_PERCENT_VOLUME : DEFAULT_PERCENT_VOLUME;
        int ratio = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100) / maxVol;

        firstCallAlert = false;

        // ensure there is a minimum audio level
        // some users could complain they did not hear their device was ringing.
        if (ratio < minValue) {
            setMediaPlayerVolume(minValue);
        }
        else {
            setMediaPlayerVolume(ratio);
        }
    }

    private void setMediaPlayerVolume(int percent) {
        if(percent < 0 || percent > 100) {
            Log.e(LOG_TAG,"setMediaPlayerVolume percent is invalid: "+percent);
            return;
        }

        AudioManager audioManager = (AudioManager)this.getSystemService(Context.AUDIO_SERVICE);

        mCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);

        int maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int maxVoiceVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);

        if(maxMusicVol > 0) {
            int volume = (int) ((float) percent / 100f * maxMusicVol);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);

            volume = (int) ((float) percent / 100f * maxVoiceVol);
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, volume, 0);
        }
        Log.i(LOG_TAG, "Set media volume (ringback) to: " + audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
    }

    /**
     * @return true if the ringing sound is played
     */
    public static Boolean isRinging() {
        if (null != mRingingPlayer) {
            return mRingingPlayer.isPlaying();
        }

        if (null != mRingTone) {
            return mRingTone.isPlaying();
        }
        return false;
    }

    /**
     * Start the ringing sound
     */
    public static void startRinging(Context context) {
        Log.d(LOG_TAG, "startRinging");

        if (null != mRingTone) {
            Log.d(LOG_TAG, "already ringing");
            return;
        }

        // use the ringTone to manage sound volume properly
        // else the ringing volume is linked to the media volume.
        mRingTone = getRingTone(context, R.raw.ring, "ring.ogg");
        if (null != mRingTone) {

            if (null != mRingbackTone) {
                mRingbackTone.stop();
                mRingbackTone = null;
            }

            if (null != mCallEndTone) {
                mCallEndTone.stop();
                mCallEndTone = null;
            }

            MXCallsManager.setSpeakerphoneOn(context, true);
            mRingTone.play();
            return;
        }


        if (null == mRingingPlayer) {
            mRingingPlayer = MediaPlayer.create(context.getApplicationContext(), R.raw.ring);

            if (null != mRingingPlayer) {
                mRingingPlayer.setLooping(true);
                mRingingPlayer.setVolume(1.0f, 1.0f);
            }
        }

        if (null != mRingingPlayer) {
            // check if it is not yet playing
            if (!mRingingPlayer.isPlaying()) {
                // stop pending
                if ((null != mCallEndPlayer) && mCallEndPlayer.isPlaying()) {
                    mCallEndPlayer.stop();
                }

                if ((null != mRingbackPlayer) && mRingbackPlayer.isPlaying()) {
                    mRingbackPlayer.stop();
                }

                MXCallsManager.setSpeakerphoneOn(context, true);
                mRingingPlayer.start();
            }
        }
    }

    /**
     * Start the ringback sound
     */
    public static void startRingbackSound(Context context) {
        Log.d(LOG_TAG, "startRingbackSound");

        if (null != mRingTone) {
            Log.d(LOG_TAG, "already ringing");
            return;
        }

        // use the ringTone to manage sound volume properly
        // else the ringing volume is linked to the media volume.
        mRingbackTone = getRingTone(context, R.raw.ringback, "ringback.ogg");

        if (null != mRingbackTone) {
            if (null != mRingTone) {
                mRingTone.stop();
                mRingTone = null;
            }

            if (null != mCallEndTone) {
                mCallEndTone.stop();
                mCallEndTone = null;
            }

            MXCallsManager.setSpeakerphoneOn(context, true);
            mRingbackTone.play();
            return;
        }

        if (null == mRingbackPlayer) {
            mRingbackPlayer = MediaPlayer.create(context.getApplicationContext(), R.raw.ringback);

            if (null != mRingbackPlayer) {
                mRingbackPlayer.setLooping(true);
                mRingbackPlayer.setVolume(1.0f, 1.0f);
            }
        }

        if (null != mRingbackPlayer) {
            // check if it is not yet playing
            if (!mRingbackPlayer.isPlaying()) {
                // stop pending
                if ((null != mCallEndPlayer) && mCallEndPlayer.isPlaying()) {
                    mCallEndPlayer.stop();
                }

                if ((null != mRingingPlayer) && mRingingPlayer.isPlaying()) {
                    mRingingPlayer.stop();
                }

                MXCallsManager.setSpeakerphoneOn(context, true);
                mRingbackPlayer.start();
            }
        }
    }
    /**
     * Stop the ringing sound
     */
    public static void stopRinging() {
        Log.d(LOG_TAG, "stopRinging");

        if (null != mRingTone) {
            mRingTone.stop();
            mRingTone = null;
        }

        if (null != mRingbackTone) {
            mRingbackTone.stop();
            mRingbackTone = null;
        }

        // sanity checks
        if ((null != mRingingPlayer) && mRingingPlayer.isPlaying()) {
            Log.d(LOG_TAG, "stop mRingingPLayer");
            try {
                mRingingPlayer.pause();
            } catch (Exception e) {
                Log.e(LOG_TAG, "stop mRingingPLayer failed " + e.getLocalizedMessage());
            }
        }
        if ((null != mRingbackPlayer) && mRingbackPlayer.isPlaying()) {
            Log.d(LOG_TAG, "stop mRingbackPlayer");

            try {
                mRingbackPlayer.pause();
            } catch (Exception e) {
                Log.e(LOG_TAG, "stop mRingbackPlayer failed " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Start the end call sound
     */
    public static void startEndCallSound(Context context) {
        Log.d(LOG_TAG, "startEndCallSound");

        // use the ringTone to manage sound volume properly
        // else the ringing volume is linked to the media volume.
        mCallEndTone = getRingTone(context, R.raw.callend, "callend.ogg");

        if (null != mCallEndTone) {
            if (null != mRingTone) {
                mRingTone.stop();
                mRingTone = null;
            }

            if (null != mRingbackTone) {
                mRingbackTone.stop();
                mRingbackTone = null;
            }

            MXCallsManager.setSpeakerphoneOn(context, true);
            mCallEndTone.play();
            return;
        }

        if (null == mCallEndPlayer) {
            mCallEndPlayer = MediaPlayer.create(context.getApplicationContext(), R.raw.callend);
            mCallEndPlayer.setLooping(false);
            mCallEndPlayer.setVolume(1.0f, 1.0f);
        }

        // sanity checks
        if ((null != mCallEndPlayer) && !mCallEndPlayer.isPlaying()) {
            MXCallsManager.setSpeakerphoneOn(context, true);
            mCallEndPlayer.start();
        }
        stopRinging();
    }
}
