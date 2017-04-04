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

package im.vector.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.call.IMXCall;

import im.vector.VectorApp;
import im.vector.activity.VectorCallViewActivity;
import im.vector.util.VectorCallSoundManager;

// this class detect if the headset is plugged / unplugged
public class HeadsetConnectionReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "HeadsetReceiver";

    private static Boolean mIsHeadsetPlugged = null;

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


    public HeadsetConnectionReceiver() {
    }

    @Override
    public void onReceive(final Context aContext, final Intent aIntent) {
        Log.d(LOG_TAG, "## onReceive() : " + aIntent.getExtras());

        if (aIntent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
            Boolean newState = null;

            int state = aIntent.getIntExtra("state", -1);

            switch (state) {
                case 0:
                    Log.d(LOG_TAG, "Headset is unplugged");
                    newState = false;
                    break;
                case 1:
                    Log.d(LOG_TAG, "Headset is plugged");
                    newState = true;
                    break;
                default:
                    Log.d(LOG_TAG, "undefined state");
            }

            if (newState != mIsHeadsetPlugged) {
                mIsHeadsetPlugged = newState;

                // toggle the call speaker when the headset is plugged/unplugged
                // TODO : all this stuff should be done in a vector calls manager
                // via a dedicated listener.
                // do it here until this manager is not implemented.
                IMXCall call = VectorCallViewActivity.getActiveCall();
                if (null != call) {
                    boolean isSpeakerOn = getAudioManager().isSpeakerphoneOn();

                    // the user plugs a headset while the device is on loud speaker
                    if (isSpeakerOn && mIsHeadsetPlugged) {
                        Log.d(LOG_TAG, "toggle the call speaker because the call was on loudspeaker.");
                        // change the audio path to the headset
                        VectorCallSoundManager.toggleSpeaker();
                    }
                    // the user unplugs the headset during video call
                    else if (!mIsHeadsetPlugged && call.isVideo()) {
                        VectorCallSoundManager.toggleSpeaker();
                        Log.d(LOG_TAG, "toggle the call speaker because the headset was unplugged during a video call.");
                    }
                }

                // detect if there is an active VectorCallViewActivity instance
                if (null != VectorCallViewActivity.getInstance()) {
                    VectorCallViewActivity.getInstance().refreshSpeakerButton();
                }
            }
        }
    }

    /**
     * @return true if the headset is plugged
     */
    public static boolean isHeadsetPlugged() {
        if (null == mIsHeadsetPlugged) {
            AudioManager audioManager =  getAudioManager();
            mIsHeadsetPlugged = audioManager.isBluetoothA2dpOn() || audioManager.isWiredHeadsetOn();
        }

        return mIsHeadsetPlugged;
    }
}

