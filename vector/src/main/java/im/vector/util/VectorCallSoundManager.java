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
import android.provider.MediaStore;
import android.util.Log;

import org.matrix.androidsdk.call.MXCallsManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import im.vector.R;
import im.vector.VectorApp;

/**
 * This class manages the sound for v
 */
public class VectorCallSoundManager {

    private static final String LOG_TAG = "CallRingManager";

    // ring tones
    private static final String RING_TONE_BUSY = "busy.ogg";
    private static final String RING_TONE_CALL_END = "callend.ogg";
    private static final String RING_TONE_START_RINGING = "ring.ogg";
    private static final String RING_TONE_RING_BACK = "ringback.ogg";

    // sounds management
    private static MediaPlayer mRingingPlayer = null;
    private static MediaPlayer mRingBackPlayer = null;
    private static MediaPlayer mCallEndPlayer = null;
    private static MediaPlayer mBusyPlayer = null;

    private static Ringtone mRingTone = null;
    private static Ringtone mRingBackTone = null;
    private static Ringtone mCallEndTone = null;
    private static Ringtone mBusyTone = null;

    private static final int DEFAULT_PERCENT_VOLUME = 10;
    private static final int FIRST_PERCENT_VOLUME = 10;
    private static boolean mIsFirstCallAlert = true;
    private static int mCallVolume = 0;

    private static AudioManager mAudioManager = null;

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
     * @param resid The audio resource.
     * @param filename the audio filename
     * @return a RingTone, null if the operation fails.
     */
    static private Ringtone getRingTone(Context context, int resid, String filename) {
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

                if (null != cursor) {
                    cursor.close();
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
                        Log.e(LOG_TAG, "## getRingTone():  Exception1 Msg=" + e.getLocalizedMessage());
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
            Log.e(LOG_TAG, "## getRingTone():  Exception2 Msg=" + e.getLocalizedMessage());
        }

        return ringtone;
    }

    /**
     * Initialize the audio volume.
     */
    public static void initMediaPlayerVolume() {
        /*AudioManager audioManager = getAudioManager();

        // use the ringing volume to initialize the playing volume
        // it does not make sense to ring louder
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int minValue = mIsFirstCallAlert ? FIRST_PERCENT_VOLUME : DEFAULT_PERCENT_VOLUME;
        int ratio = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100) / maxVol;

        mIsFirstCallAlert = false;

        // ensure there is a minimum audio level
        // some users could complain they did not hear their device was ringing.
        if (ratio < minValue) {
            setMediaPlayerVolume(minValue);
        }
        else {
            setMediaPlayerVolume(ratio);
        }*/
    }

    /**
     *
     * @param percent
     */
    private static void setMediaPlayerVolume(int percent) {
       /* if(percent < 0 || percent > 100) {
            Log.e(LOG_TAG,"setMediaPlayerVolume percent is invalid: "+percent);
            return;
        }

        AudioManager audioManager = getAudioManager();

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
        */
    }

    /**
     * Stop any playing ring tones.
     */
    private static boolean stopRingTones() {
        boolean found = false;

        if (null != mRingTone) {
            mRingTone.stop();
            mRingTone = null;
            found = true;
        }

        if (null != mRingBackTone) {
            mRingBackTone.stop();
            mRingBackTone = null;
            found = true;
        }

        if (null != mCallEndTone) {
            mCallEndTone.stop();
            mCallEndTone = null;
            found = true;
        }

        if (null != mBusyTone) {
            mBusyTone.stop();
            mBusyTone = null;
            found = true;
        }

        return found;
    }

    /**
     * Stop any running ring media player
     */
    private static boolean stopRingPlayers() {
        boolean found = false;

        if ((null != mRingingPlayer) && mRingingPlayer.isPlaying()) {
            try {
                mRingingPlayer.pause();
            } catch (Exception e) {
                Log.e(LOG_TAG, "stopRingPlayers : mRingingPlayer.pause failed " + e.getMessage());
            }

            found = true;
        }

        if ((null != mRingBackPlayer) && mRingBackPlayer.isPlaying()) {
            try {
                mRingBackPlayer.pause();
            } catch (Exception e) {
                Log.e(LOG_TAG, "stopRingPlayers : mRingbackPlayer.pause failed " + e.getMessage());
            }

            found = true;
        }

        if ((null != mCallEndPlayer) && mCallEndPlayer.isPlaying()) {
            mCallEndPlayer.stop();

            found = true;
        }

        if ((null != mBusyPlayer) && mBusyPlayer.isPlaying()) {
            mBusyPlayer.stop();
            found = true;
        }

        return found;
    }

    /**
     * Stop the ringing sound
     */
    public static void stopRinging(Context context) {
        Log.d(LOG_TAG, "stopRinging");

        if (!stopRingTones() && !stopRingPlayers()) {
            MXCallsManager.prepareCallAudio(context);
        }
    }

    /**
     * Start the ringing sound
     */
    public static void startRinging(Context context) {
        Log.d(LOG_TAG, "startRinging");

        if (null != mRingTone) {
            Log.d(LOG_TAG, "ring tone already ringing");
            return;
        }

        if ((null != mRingingPlayer) && mRingingPlayer.isPlaying()) {
            Log.d(LOG_TAG, "player is already ringing");
            return;
        }

        stopRinging(context);

        // use the ringTone to manage sound volume properly
        mRingTone = getRingTone(context, R.raw.ring, RING_TONE_START_RINGING);

        if (null != mRingTone) {
            MXCallsManager.setCallSpeakerphoneOn(context, true);
            mRingTone.play();

            return;
        }

        // if the ring tone cannot be used
        // use the media player
        if (null == mRingingPlayer) {
            mRingingPlayer = MediaPlayer.create(context.getApplicationContext(), R.raw.ring);
        }

        if (null != mRingingPlayer) {
            stopRingPlayers();
            mRingingPlayer.setLooping(true);

            MXCallsManager.setCallSpeakerphoneOn(context, true);
            mRingingPlayer.start();
        }
    }

    /**
     * Start the ring back sound
     */
    public static void startRingBackSound(Context context) {
        Log.d(LOG_TAG, "startRingBackSound");

        if (null != mRingBackTone) {
            Log.d(LOG_TAG, "ringtone already ringing");
            return;
        }

        if ((null != mRingBackPlayer) && mRingBackPlayer.isPlaying()) {
            Log.d(LOG_TAG, "player already ringing");
            return;
        }

        stopRinging(context);

        // use the ringTone to manage sound volume properly
        mRingBackTone = getRingTone(context, R.raw.ringback, RING_TONE_RING_BACK);

        if (null != mRingBackTone) {
            MXCallsManager.setCallSpeakerphoneOn(context, true);
            mRingBackTone.play();
            return;
        }

        if (null == mRingBackPlayer) {
            mRingBackPlayer = MediaPlayer.create(context.getApplicationContext(), R.raw.ringback);
        }

        if ((null != mRingBackPlayer) && !mRingBackPlayer.isPlaying()) {
            mRingBackPlayer.setLooping(true);

            stopRingPlayers();

            MXCallsManager.setCallSpeakerphoneOn(context, true);
            mRingBackPlayer.start();
        }
    }

    /**
     * Start the end call sound
     */
    public static void startEndCallSound(Context context) {
        Log.d(LOG_TAG, "startEndCallSound");

        if (null != mCallEndTone) {
            Log.d(LOG_TAG, "ringtone already ringing");
            return;
        }

        if ((null != mCallEndPlayer) && mCallEndPlayer.isPlaying()) {
            Log.d(LOG_TAG, "player already ringing");
            return;
        }

        stopRingTones();

        // use the ringTone to manage sound volume properly
        mCallEndTone = getRingTone(context, R.raw.callend, RING_TONE_CALL_END);

        if (null != mCallEndTone) {
            MXCallsManager.setCallSpeakerphoneOn(context, true);
            mCallEndTone.play();
            restoreAudioConfigAfter(context, 5000);
            return;
        }

        if (null == mCallEndPlayer) {
            mCallEndPlayer = MediaPlayer.create(context.getApplicationContext(), R.raw.callend);
        }

        // sanity checks
        if (null != mCallEndPlayer) {
            mCallEndPlayer.setLooping(false);
            MXCallsManager.setCallSpeakerphoneOn(context, true);
            mCallEndPlayer.start();
            restoreAudioConfigAfter(context, 5000);
        }
    }

    /**
     * Start the busy call sound
     */
    public static void startBusyCallSound(Context context) {
        Log.d(LOG_TAG, "startBusyCallSound");

        if (null != mBusyTone) {
            Log.d(LOG_TAG, "ringtone already ringing");
            return;
        }

        if ((null != mBusyPlayer) && mBusyPlayer.isPlaying()) {
            Log.d(LOG_TAG, "player already ringing");
            return;
        }

        stopRingTones();

        // use the ringTone to manage sound volume properly
        mBusyTone = getRingTone(context, R.raw.callend, RING_TONE_BUSY);

        if (null != mBusyTone) {
            MXCallsManager.setCallSpeakerphoneOn(context, true);
            mBusyTone.play();
            restoreAudioConfigAfter(context, 5000);
            return;
        }

        if (null == mBusyPlayer) {
            mBusyPlayer = MediaPlayer.create(context.getApplicationContext(), R.raw.busy);
        }

        // sanity checks
        if (null != mBusyPlayer) {
            mBusyPlayer.setLooping(false);
            MXCallsManager.setCallSpeakerphoneOn(context, true);
            mBusyPlayer.start();
            restoreAudioConfigAfter(context, 5000);
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

    private static void prepareCallAudio(Context context) {
        AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);

        mAudioMode = audioManager.getMode();
        mIsSpeakerOn = audioManager.isSpeakerphoneOn();
    }



    private static void restoreAudioConfigAfter(final Context context, int delayMs) {
        if (null == mUIHandler) {
            mUIHandler = new Handler(Looper.getMainLooper());
        }

        mRestoreAudioConfigTimer = new Timer();
        mRestoreAudioConfigTimerMask = new TimerTask() {
            public void run() {
                mUIHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != mRestoreAudioConfigTimer) {
                            mRestoreAudioConfigTimer.cancel();
                        }
                        mRestoreAudioConfigTimer = null;
                        mRestoreAudioConfigTimerMask = null;

                        MXCallsManager.restoreCallAudio(context);
                    }
                });
            }
        };

        mRestoreAudioConfigTimer.schedule(mRestoreAudioConfigTimerMask, delayMs);
    }


    private static void restoreCallAudio(Context context) {
        if ((null != mAudioMode) && (null != mIsSpeakerOn)) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

            // ignore speaker button if a headset is connected
            if (!audioManager.isBluetoothA2dpOn() && !audioManager.isWiredHeadsetOn()) {
                audioManager.setMode(mAudioMode);
                audioManager.setSpeakerphoneOn(mIsSpeakerOn);
            }

            mAudioMode = null;
            mIsSpeakerOn = null;
        }
    }

    /**
     * Sets the speakerphone on or off.
     *
     * @param isOn true to turn on speakerphone;
     *           false to turn it off
     */
    public static void setSpeakerphoneOn(Context context, boolean isOn) {
        Log.d(LOG_TAG, "setCallSpeakerphoneOn " + isOn);

        if ((null == mAudioMode) || (null == mIsSpeakerOn)) {
            prepareCallAudio(context);
        }

        AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);

        // ignore speaker button if a headset is connected
        if (!audioManager.isBluetoothA2dpOn() && !audioManager.isWiredHeadsetOn()) {
            int audioMode = AudioManager.MODE_IN_COMMUNICATION;

            if (audioManager.getMode() != audioMode) {
                audioManager.setMode(audioMode);
            }

            if (isOn != audioManager.isSpeakerphoneOn()) {
                audioManager.setSpeakerphoneOn(isOn);
            }
        }
    }


    /**
     * Toogle the speaker
     */
    public void toggleSpeaker() {
        AudioManager audioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);

        if (null != audioManager) {
            MXCallsManager.setCallSpeakerphoneOn(mContext, !audioManager.isSpeakerphoneOn());
        }
    }
}
