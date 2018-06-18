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

import android.content.Context;

import org.matrix.androidsdk.call.IMXCall;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import im.vector.R;

/**
 * This class contains the call toolbox.
 */
public class CallUtilities {
    //
    private static SimpleDateFormat mHourMinSecFormat = null;
    private static SimpleDateFormat mMinSecFormat = null;

    /**
     * Format a time in seconds to a HH:MM:SS string.
     *
     * @param seconds the time in seconds
     * @return the formatted time
     */
    private static String formatSecondsToHMS(long seconds) {
        if (null == mHourMinSecFormat) {
            mHourMinSecFormat = new SimpleDateFormat("HH:mm:ss");
            mHourMinSecFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            mMinSecFormat = new SimpleDateFormat("mm:ss");
            mMinSecFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        if (seconds < 3600) {
            return mMinSecFormat.format(new Date(seconds * 1000));
        } else {
            return mHourMinSecFormat.format(new Date(seconds * 1000));
        }
    }

    /**
     * Return the call status.
     *
     * @param call the dedicated call
     * @return the call status.
     */
    public static String getCallStatus(Context context, IMXCall call) {
        if (null == call) {
            return null;
        }

        String callState = call.getCallState();

        switch (callState) {
            case IMXCall.CALL_STATE_CREATED:
            case IMXCall.CALL_STATE_CREATING_CALL_VIEW:
            case IMXCall.CALL_STATE_READY:
            case IMXCall.CALL_STATE_WAIT_LOCAL_MEDIA:
                if (call.isIncoming()) {
                    if (call.isVideo()) {
                        return context.getString(R.string.incoming_video_call);
                    } else {
                        return context.getString(R.string.incoming_voice_call);
                    }
                }
            case IMXCall.CALL_STATE_INVITE_SENT:
            case IMXCall.CALL_STATE_CONNECTING:
            case IMXCall.CALL_STATE_CREATE_ANSWER:
            case IMXCall.CALL_STATE_WAIT_CREATE_OFFER: {
                return context.getString(R.string.call_connecting);
            }
            case IMXCall.CALL_STATE_RINGING:
                if (call.isIncoming()) {
                    if (call.isVideo()) {
                        return context.getString(R.string.incoming_video_call);
                    } else {
                        return context.getString(R.string.incoming_voice_call);
                    }
                } else {
                    return context.getString(R.string.call_ring);
                }
            case IMXCall.CALL_STATE_CONNECTED:
                long elapsedTime = call.getCallElapsedTime();

                if (elapsedTime < 0) {
                    return context.getString(R.string.call_connected);
                } else {
                    return formatSecondsToHMS(elapsedTime);
                }
            case IMXCall.CALL_STATE_ENDED:
                return context.getString(R.string.call_ended);
        }

        return null;
    }
}
