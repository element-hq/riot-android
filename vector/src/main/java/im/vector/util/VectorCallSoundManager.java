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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.MediaStore;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.call.IMXCall;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.receiver.HeadsetConnectionReceiver;

/**
 * This class manages the sound for vector.
 * It is in charge of playing ringtones and managing the audio focus.
 */
public class VectorCallSoundManager {
    /** Observer pattern class to notify sound events.
     *  Clients listen to events by calling {@link #addSoundListener(IVectorCallSoundListener)}**/
    public interface IVectorCallSoundListener {
        /**
         * Call back indicating new focus events (ex: {@link AudioManager#AUDIOFOCUS_GAIN},
         * {@link AudioManager#AUDIOFOCUS_LOSS}..).
         * @param aFocusEvent the focus event (see {@link AudioManager.OnAudioFocusChangeListener})
         */
        void onFocusChanged(int aFocusEvent);
    }

    private static final String LOG_TAG = "CallSoundManager";

    // ring tones resource names
    private static final String RING_TONE_START_RINGING = "ring.ogg";

    // audio focus
    private static boolean mIsFocusGranted = false;

    // the ringtones are played on loudspeaker
    private static Ringtone mRingTone = null;

    // the media players are played on loudspeaker / earpiece according to setSpeakerOn
    private static MediaPlayer mRingBackPlayer = null;
    private static MediaPlayer mCallEndPlayer = null;
    private static MediaPlayer mBusyPlayer = null;

    private static final int VIBRATE_DURATION = 500; // milliseconds
    private static final int VIBRATE_SLEEP = 1000;  // milliseconds
    private static final long[] VIBRATE_PATTERN = {0, VIBRATE_DURATION, VIBRATE_SLEEP};

    // audio focus management
    private final static ArrayList<IVectorCallSoundListener> mCallSoundListenersList = new ArrayList<>();

    private final static AudioManager.OnAudioFocusChangeListener mFocusListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int aFocusEvent) {
            switch (aFocusEvent) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_GAIN");
                    // TODO resume voip call (ex: ending GSM call)
                    break;

                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_LOSS");
                    // TODO pause voip call (ex: incoming GSM call)
                    break;

                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_GAIN_TRANSIENT");
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_LOSS_TRANSIENT");
                    // TODO pause voip call (ex: incoming GSM call)
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    // TODO : continue playing at an attenuated level
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                    break;

                case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_REQUEST_FAILED");
                    break;

                default:
                    break;
            }

            // notify listeners
            for (IVectorCallSoundListener listener : mCallSoundListenersList) {
                listener.onFocusChanged(aFocusEvent);
            }
        }
    };

    // the audio manager
    private static AudioManager mAudioManager = null;

    private static final HashMap<String, Uri> mRingtoneUrlByFileName = new HashMap<>();

    /**
     * @return the audio manager
     */
    private static AudioManager getAudioManager() {
        if (null == mAudioManager) {
            mAudioManager =  (AudioManager)VectorApp.getInstance().getSystemService(Context.AUDIO_SERVICE);
        }

        return mAudioManager;
    }

    /**
     * Provide a ringtone from a resource and a filename.
     * The audio file must have a ANDROID_LOOP metatada set to true to loop the sound.
     * @param resId The audio resource.
     * @param filename the audio filename
     * @return a RingTone, null if the operation fails.
     */
    static private Ringtone getRingTone(int resId, String filename) {
        Context context = VectorApp.getInstance();
        Uri ringToneUri = mRingtoneUrlByFileName.get(filename);
        Ringtone ringtone = null;

        // test if the ring tone has been cached
        if (null != ringToneUri) {
            ringtone = RingtoneManager.getRingtone(context, ringToneUri);

            // provide it
            return ringtone;
        }

        try {
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

                if (null != cursor) {
                    cursor.close();
                }
            }

            // the Uri has been retrieved
            if (null == ringToneUri) {
                // create the file
                if (!file.exists()) {
                    try {
                        byte[] readData = new byte[1024];
                        InputStream fis = context.getResources().openRawResource(resId);
                        FileOutputStream fos = new FileOutputStream(file);
                        int i = fis.read(readData);

                        while (i != -1) {
                            fos.write(readData, 0, i);
                            i = fis.read(readData);
                        }

                        fos.close();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## getRingTone():  Exception1 Msg=" + e.getLocalizedMessage());
                    }
                }

                // and the resource Uri
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DATA, file.getAbsolutePath());
                values.put(MediaStore.MediaColumns.TITLE, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/ogg");
                values.put(MediaStore.MediaColumns.SIZE, file.length());
                values.put(MediaStore.Audio.Media.ARTIST, R.string.riot_app_name);
                values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
                values.put(MediaStore.Audio.Media.IS_NOTIFICATION, true);
                values.put(MediaStore.Audio.Media.IS_ALARM, true);
                values.put(MediaStore.Audio.Media.IS_MUSIC, true);

                ringToneUri = context.getContentResolver() .insert(MediaStore.Audio.Media.getContentUriForPath(file.getAbsolutePath()), values);
            }

            if (null != ringToneUri) {
                mRingtoneUrlByFileName.put(filename, ringToneUri);
                ringtone = RingtoneManager.getRingtone(context, ringToneUri);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getRingTone():  Exception2 Msg=" + e.getLocalizedMessage());
        }

        return ringtone;
    }

    /**
     * Tells that the device is ringing.
     * @return true if the device is ringing
     */
    public static boolean isRinging() {
        return (null != mRingTone);
    }

    /**
     * Stop any playing ring tones.
     */
    private static void stopRingTones() {
        if (null != mRingTone) {
            mRingTone.stop();
            mRingTone = null;
        }

        if (null != mRingBackPlayer) {
            if (mRingBackPlayer.isPlaying()) {
                mRingBackPlayer.stop();
            }
            mRingBackPlayer = null;
        }

        if (null != mCallEndPlayer) {
            if (mCallEndPlayer.isPlaying()) {
                mCallEndPlayer.stop();
            }
            mCallEndPlayer = null;
        }

        if (null != mBusyPlayer) {
            if (mBusyPlayer.isPlaying()) {
                mBusyPlayer.stop();
            }
            mBusyPlayer = null;
        }
    }

    /**
     * Stop the ringing sound
     */
    public static void stopRinging() {
        Log.d(LOG_TAG, "stopRinging");
        stopRingTones();

        // stop vibrate
        enableVibrating(false);
    }

    /**
     * Getter method.
     * @return true is focus is granted, false otherwise.
     */
    public static boolean isFocusGranted(){
        return mIsFocusGranted;
    }

    /**
     * Request a permanent audio focus if the focus was not yet granted.
     */
    private static void requestAudioFocus() {
        if(! mIsFocusGranted) {
            int focusResult;
            AudioManager audioMgr;

            if ((null != (audioMgr = getAudioManager()))) {
                // Request permanent audio focus for voice call
                focusResult = audioMgr.requestAudioFocus(mFocusListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);

                if (AudioManager.AUDIOFOCUS_REQUEST_GRANTED == focusResult) {
                    mIsFocusGranted = true;
                    Log.d(LOG_TAG, "## getAudioFocus(): granted");
                } else {
                    mIsFocusGranted = false;
                    Log.w(LOG_TAG, "## getAudioFocus(): refused - focusResult=" + focusResult);
                }
            }
        } else {
            Log.d(LOG_TAG, "## getAudioFocus(): already granted");
        }
    }

    /**
     * Release the audio focus if it was granted.
     */
    public static void releaseAudioFocus() {
        if(mIsFocusGranted) {
            Handler handler = new Handler(Looper.getMainLooper());

            // the audio focus is abandoned with delay
            // to let the call to finish properly
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    AudioManager audioMgr;

                    if ((null != (audioMgr = getAudioManager()))) {
                        // release focus
                        int abandonResult = audioMgr.abandonAudioFocus(mFocusListener);

                        if (AudioManager.AUDIOFOCUS_REQUEST_GRANTED == abandonResult) {
                            mIsFocusGranted = false;
                            Log.d(LOG_TAG, "## releaseAudioFocus(): abandonAudioFocus = AUDIOFOCUS_REQUEST_GRANTED");
                        }

                        if (AudioManager.AUDIOFOCUS_REQUEST_FAILED == abandonResult) {
                            Log.d(LOG_TAG, "## releaseAudioFocus(): abandonAudioFocus = AUDIOFOCUS_REQUEST_FAILED");
                        }
                    } else {
                        Log.d(LOG_TAG, "## releaseAudioFocus(): failure - invalid AudioManager");
                    }
                }
            }, 300);
        }
    }

    /**
     * Start the ringing sound
     */
    public static void startRinging() {
        Log.d(LOG_TAG, "startRinging");

        if (null != mRingTone) {
            Log.d(LOG_TAG, "ring tone already ringing");
            return;
        }

        stopRinging();

        // use the ringTone to manage sound volume properly
        mRingTone = getRingTone(R.raw.ring, RING_TONE_START_RINGING);

        if (null != mRingTone) {
            setSpeakerphoneOn(false, true);
            mRingTone.play();
        } else {
            Log.e(LOG_TAG, "startRinging : fail to retrieve RING_TONE_START_RINGING");
        }

        // start vibrate
        enableVibrating(true);
    }

    /**
     * Enable the vibrate mode.
     * @param aIsVibrateEnabled true to force vibrate, false to stop vibrate.
     */
    private static void enableVibrating(boolean aIsVibrateEnabled) {
        Vibrator vibrator = (Vibrator)VectorApp.getInstance().getSystemService(Context.VIBRATOR_SERVICE);

        if((null != vibrator) && vibrator.hasVibrator()) {
            if(aIsVibrateEnabled) {
                vibrator.vibrate(VIBRATE_PATTERN, 0 /*repeat till stop*/);
                Log.d(LOG_TAG, "## startVibrating(): Vibrate started");
            } else {
                vibrator.cancel();
                Log.d(LOG_TAG, "## startVibrating(): Vibrate canceled");
            }
        } else {
            Log.w(LOG_TAG, "## startVibrating(): vibrator access failed");
        }
    }

    /**
     * Perform the audio focus management according to the VoIP call
     * states.
     * @param aCallState voip state
     */
    public static void manageCallStateFocus(String aCallState){
        switch (aCallState) {
            case IMXCall.CALL_STATE_CONNECTED:
                requestAudioFocus();
                break;

            case IMXCall.CALL_STATE_ENDED:
                releaseAudioFocus();
                break;

            default:
                break;
        }
    }

    /**
     * Start the ring back sound
     */
    public static void startRingBackSound(boolean isVideo) {
        Log.d(LOG_TAG, "startRingBackSound");

        if (null != mRingBackPlayer) {
            Log.d(LOG_TAG, "ringtone already ringing");
            return;
        }

        stopRinging();

        mRingBackPlayer = MediaPlayer.create(VectorApp.getInstance(), R.raw.ringback);
        mRingBackPlayer.setLooping(true);

        if (null != mRingBackPlayer) {
            setSpeakerphoneOn(true, isVideo && !HeadsetConnectionReceiver.isHeadsetPlugged());
            mRingBackPlayer.start();
        } else {
            Log.e(LOG_TAG, "startRingBackSound : fail to retrieve RING_TONE_RING_BACK");
        }
    }

    /**
     * Start the end call sound
     */
    public static void startEndCallSound() {
        Log.d(LOG_TAG, "startEndCallSound");

        if (null != mCallEndPlayer) {
            Log.d(LOG_TAG, "ringtone already ringing");
            return;
        }

        stopRingTones();

        mCallEndPlayer = MediaPlayer.create(VectorApp.getInstance(), R.raw.callend);
        mCallEndPlayer.setLooping(false);

        if (null != mCallEndPlayer) {
            // do not update the audio path

            mCallEndPlayer.start();

            mCallEndPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    restoreAudioConfig();
                }
            });
        } else {
            Log.e(LOG_TAG, "startEndCallSound : fail to retrieve RING_TONE_RING_BACK");
        }
    }

    /**
     * Start the busy call sound
     */
    public static void startBusyCallSound() {
        Log.d(LOG_TAG, "startBusyCallSound");

        if (null != mBusyPlayer) {
            Log.d(LOG_TAG, "ringtone already ringing");
            return;
        }

        stopRingTones();

        // use the ringTone to manage sound volume properly
        mBusyPlayer = MediaPlayer.create(VectorApp.getInstance(), R.raw.busy);

        if (null != mBusyPlayer) {
            // do not update the audio path

            mBusyPlayer.start();

            mBusyPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    restoreAudioConfig();
                }
            });
        }
    }

    //==============================================================================================================
    // speakers management
    //==============================================================================================================

    // save the audio statuses
    private static Integer mAudioMode = null;
    private static Boolean mIsSpeakerOn = null;

    private static Timer mRestoreAudioConfigTimer = null;
    private static TimerTask mRestoreAudioConfigTimerMask = null;
    private static Handler mUIHandler = null;

    /**
     * Back up the current audio config.
     */
    private static void backupAudioConfig() {
        // there is a pending timer to restore the audio config
        if (null != mRestoreAudioConfigTimer) {
            // cancel the timer and don't get the audio config
            mRestoreAudioConfigTimer.cancel();
            mRestoreAudioConfigTimer = null;
            mRestoreAudioConfigTimerMask = null;
        } else if (null == mAudioMode) { // not yet backuped
            AudioManager audioManager = getAudioManager();

            mAudioMode = audioManager.getMode();
            mIsSpeakerOn = audioManager.isSpeakerphoneOn();
        }
    }

    /**
     * Restore the audio config.
     */
    private static void restoreAudioConfig() {
        // ensure that something has been saved
        if ((null != mAudioMode) && (null != mIsSpeakerOn)) {
            AudioManager audioManager = getAudioManager();

            // ignore speaker button if a headset is connected
            if (!audioManager.isBluetoothA2dpOn() && !audioManager.isWiredHeadsetOn()) {
                if (mAudioMode!= audioManager.getMode()) {
                    audioManager.setMode(mAudioMode);
                }

                if (mIsSpeakerOn != audioManager.isSpeakerphoneOn()) {
                    audioManager.setSpeakerphoneOn(mIsSpeakerOn);
                }
            }

            mAudioMode = null;
            mIsSpeakerOn = null;
        }
    }

    /**
     * Set the speakerphone ON or OFF.
     * @param isOn true to enable the speaker (ON), false to disable it (OFF)
     */
    public static void setCallSpeakerphoneOn(boolean isOn) {
        setSpeakerphoneOn(true, isOn);
    }

    /**
     * Save the current speaker status and the audio mode, before updating those
     * values.
     * The audio mode depends on if there is a call in progress.
     * If audio mode set to {@link AudioManager#MODE_IN_COMMUNICATION} and
     * a media player is in ON, the media player will reduce its audio level.
     *
     * @param isInCall true when the speaker is updated during call.
     * @param isSpeakerOn true to turn on the speaker (false to turn it off)
     */
    private static void setSpeakerphoneOn(boolean isInCall, boolean isSpeakerOn) {
        Log.d(LOG_TAG, "setCallSpeakerphoneOn " + isSpeakerOn);

        backupAudioConfig();

        AudioManager audioManager = getAudioManager();

        int audioMode = isInCall ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_RINGTONE;

        if (audioManager.getMode() != audioMode) {
            audioManager.setMode(audioMode);
        }

        if (isSpeakerOn != audioManager.isSpeakerphoneOn()) {
            audioManager.setSpeakerphoneOn(isSpeakerOn);
        }
    }

    /**
     * Toggle the speaker
     */
    public static void toggleSpeaker() {
        AudioManager audioManager = getAudioManager();
        audioManager.setSpeakerphoneOn(!audioManager.isSpeakerphoneOn());
    }
}
