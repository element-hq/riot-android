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
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import im.vector.R;
import im.vector.VectorApp;

/**
 * This class manages the sound for v
 */
public class VectorCallSoundManager {

    private static final String LOG_TAG = "CallRingManager";

    // ring tones resource names
    private static final String RING_TONE_BUSY = "busy.ogg";
    private static final String RING_TONE_CALL_END = "callend.ogg";
    private static final String RING_TONE_START_RINGING = "ring.ogg";
    private static final String RING_TONE_RING_BACK = "ringback.ogg";

    // the ring tones
    private static Ringtone mRingTone = null;
    private static Ringtone mRingBackTone = null;
    private static Ringtone mCallEndTone = null;
    private static Ringtone mBusyTone = null;

    // the audio manager
    private static AudioManager mAudioManager = null;

    private static HashMap<String, Uri> mRingtoneUrlByFileName = new HashMap<>();

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
                values.put(MediaStore.Audio.Media.ARTIST, R.string.app_name);
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
     * Stop any playing ring tones.
     */
    private static void stopRingTones() {
        if (null != mRingTone) {
            mRingTone.stop();
            mRingTone = null;
        }

        if (null != mRingBackTone) {
            mRingBackTone.stop();
            mRingBackTone = null;
        }

        if (null != mCallEndTone) {
            mCallEndTone.stop();
            mCallEndTone = null;
        }

        if (null != mBusyTone) {
            mBusyTone.stop();
            mBusyTone = null;
        }
    }

    /**
     * Stop the ringing sound
     */
    public static void stopRinging() {
        Log.d(LOG_TAG, "stopRinging");
        stopRingTones();
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
    }

    /**
     * Start the ring back sound
     */
    public static void startRingBackSound() {
        Log.d(LOG_TAG, "startRingBackSound");

        if (null != mRingBackTone) {
            Log.d(LOG_TAG, "ringtone already ringing");
            return;
        }

        stopRinging();

        // use the ringTone to manage sound volume properly
        mRingBackTone = getRingTone(R.raw.ringback, RING_TONE_RING_BACK);

        if (null != mRingBackTone) {
            initRingToneSpeaker();
            mRingBackTone.play();
        } else {
            Log.e(LOG_TAG, "startRingBackSound : fail to retrieve RING_TONE_RING_BACK");
        }
    }

    /**
     * Start the end call sound
     */
    public static void startEndCallSound() {
        Log.d(LOG_TAG, "startEndCallSound");

        if (null != mCallEndTone) {
            Log.d(LOG_TAG, "ringtone already ringing");
            return;
        }

        stopRingTones();

        // use the ringTone to manage sound volume properly
        mCallEndTone = getRingTone(R.raw.callend, RING_TONE_CALL_END);

        if (null != mCallEndTone) {
            initRingToneSpeaker();
            mCallEndTone.play();
            restoreAudioConfigAfter(2000);
        } else {
            Log.e(LOG_TAG, "startEndCallSound : fail to retrieve RING_TONE_RING_BACK");
        }
    }

    /**
     * Start the busy call sound
     */
    public static void startBusyCallSound() {
        Log.d(LOG_TAG, "startBusyCallSound");

        if (null != mBusyTone) {
            Log.d(LOG_TAG, "ringtone already ringing");
            return;
        }

        stopRingTones();

        // use the ringTone to manage sound volume properly
        mBusyTone = getRingTone(R.raw.busy, RING_TONE_BUSY);

        if (null != mBusyTone) {
            initRingToneSpeaker();
            mBusyTone.play();
            restoreAudioConfigAfter(4000);
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
     * Restore the audio config after a specified delay
     * @param delayMs the delay in ms
     */
    private static void restoreAudioConfigAfter(int delayMs) {
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

                        mUIHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                restoreAudioConfig();
                            }
                        });
                    }
                });
            }
        };

        mRestoreAudioConfigTimer.schedule(mRestoreAudioConfigTimerMask, delayMs);
    }

    /**
     * Turn the speaker on to be ready to ring
     */
    private static void initRingToneSpeaker() {
        setSpeakerphoneOn(false, true);
    }

    /**
     * Sets the speakerphone on or off.
     * @param isOn true to turn on the speaker (false to turn it off)
     */
    public static void setCallSpeakerphoneOn(boolean isOn) {
        setSpeakerphoneOn(true, isOn);
    }

    /**
     * Tells if there is a plugged headset.
     * @return
     */
    public static boolean isHeadsetPlugged() {
        AudioManager audioManager = getAudioManager();

        return audioManager.isBluetoothA2dpOn() || audioManager.isWiredHeadsetOn();
    }

    /**
     * Update the speaker status
     *
     * @param isInCall true when the speaker is updated during call.
     * @param isOn true to turn on the speaker (false to turn it off)
     */
    private static void setSpeakerphoneOn(boolean isInCall, boolean isOn) {
        Log.d(LOG_TAG, "setCallSpeakerphoneOn " + isOn);

        backupAudioConfig();

        AudioManager audioManager = getAudioManager();

        // ignore speaker button if a headset is connected
        if (!isHeadsetPlugged()) {
            int audioMode = isInCall ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_RINGTONE;

            if (audioManager.getMode() != audioMode) {
                audioManager.setMode(audioMode);
            }

            if (isOn != audioManager.isSpeakerphoneOn()) {
                audioManager.setSpeakerphoneOn(isOn);
            }
        }
    }

    /**
     * Toggle the speaker
     */
    public static void toggleSpeaker() {
        if (!isHeadsetPlugged()) {
            AudioManager audioManager = getAudioManager();
            audioManager.setSpeakerphoneOn(!audioManager.isSpeakerphoneOn());
        }
    }
}
