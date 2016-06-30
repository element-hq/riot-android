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

package im.vector.view;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import im.vector.R;
import im.vector.activity.CallViewActivity;
import im.vector.util.VectorUtils;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.data.Room;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Pending call cell
 */
public class VectorPendingCallView extends RelativeLayout {
    //
    private MXSession mSession;

    private IMXCall mCall;
    private String mCallId;

    private TextView mCallDescriptionTextView;
    private TextView mCallStatusTextView;

    private Timer mCallRefreshTimer;
    private TimerTask mCallRefreshTimerTask;

    private IMXCall.MXCallListener mCallListener = new IMXCall.MXCallListener() {
        @Override
        public void onStateDidChange(String state) {
        }

        @Override
        public void onCallError(String error) {

        }

        @Override
        public void onViewLoading(View callview) {

        }

        @Override
        public void onViewReady() {

        }

        @Override
        public void onCallAnsweredElsewhere() {
            onCallTerminated();
        }

        @Override
        public void onCallEnd() {
            onCallTerminated();
        }
    };


    public VectorPendingCallView(Context context) {
        super(context);
        initView(context);
    }

    public VectorPendingCallView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public VectorPendingCallView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context);
    }

    private void initView(Context context) {
        View.inflate(context, R.layout.vector_pending_call_cell, this);

        mCallDescriptionTextView = (TextView) findViewById(R.id.pending_call_room_name_textview);
        mCallDescriptionTextView.setVisibility(View.GONE);

        mCallStatusTextView = (TextView) findViewById(R.id.pending_call_status_textview);
        mCallStatusTextView.setVisibility(View.GONE);
    }

    /**
     * Start the call monitoring
     * @param session the session
     */
    public void start(MXSession session) {
        mSession = session;

        IMXCall call = CallViewActivity.getActiveCall();

        // check if is a new call
        if (mCall != call) {

            // replace the previous one
            mCall = call;

            if (null != mCallRefreshTimer) {
                mCallRefreshTimer.cancel();
                mCallRefreshTimer = null;
            }

            if (null != mCallRefreshTimerTask) {
                mCallRefreshTimerTask.cancel();
                mCallRefreshTimerTask = null;
            }

            if (null != call) {
                call.addListener(mCallListener);

                mCallRefreshTimer = new Timer();
                mCallRefreshTimerTask = new TimerTask() {
                    public void run() {
                        refreshCallStatus();
                    }
                };

                refreshCallDescription();
                refreshCallDescription();
                mCallRefreshTimer.schedule(mCallRefreshTimerTask, 1000);
            }
        } else if (null != mCall) {
            refreshCallDescription();
            refreshCallDescription();
        }
    }

    /**
     * The call is ended.
     * Terminates the refresh processes.
     */
    private void onCallTerminated() {
        if (null != mCallRefreshTimer) {
            mCallRefreshTimer.cancel();
            mCallRefreshTimer = null;
        }

        if (null != mCallRefreshTimerTask) {
            mCallRefreshTimerTask.cancel();
            mCallRefreshTimerTask = null;
        }

        mCall = null;
        // should hide from parent view
    }

    /**
     * refresh the call description
     */
    private void refreshCallDescription() {
        if (null != mCall) {
            mCallDescriptionTextView.setVisibility(View.VISIBLE);

            Room room =  mCall.getRoom();

            if (null != room) {
                mCallDescriptionTextView.setText(VectorUtils.getRoomDisplayname(getContext(), mSession, room));
            } else {
                mCallDescriptionTextView.setText(mCall.getCallId());
            }
        } else {
            mCallDescriptionTextView.setVisibility(View.GONE);
        }
    }

    /**
     * Refresh the call status
     */
    private void refreshCallStatus() {
        String callStatus = getCallStatus(getContext(), mCall);

        mCallStatusTextView.setText(callStatus);
        mCallStatusTextView.setVisibility(TextUtils.isEmpty(callStatus) ? View.GONE : View.VISIBLE);
    }

    //================================================================================
    // ToolBox
    //================================================================================

    // formatters
    private static SimpleDateFormat mHourMinSecFormat = null;
    private static SimpleDateFormat mMinSecFormat =  null;

    /**
     * Format a time in seconds to a HH:MM:SS string.
     * @param seconds the time in seconds
     * @return the formatted time
     */
    private static String formatSecondsToHMS(long seconds) {
        if (null == mHourMinSecFormat) {
            mHourMinSecFormat =  new SimpleDateFormat("HH:mm:ss");
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
     * @param call the dedicated call
     * @return the call status.
     */
    private static String getCallStatus(Context context, IMXCall call) {
        // sanity check
        if (null == call) {
            return null;
        }

        String callState = call.getCallState();

        if (callState.equals(IMXCall.CALL_STATE_CONNECTING) || callState.equals(IMXCall.CALL_STATE_CREATE_ANSWER)
                || callState.equals(IMXCall.CALL_STATE_WAIT_LOCAL_MEDIA) || callState.equals(IMXCall.CALL_STATE_WAIT_CREATE_OFFER)
                ) {
            return context.getResources().getString(R.string.call_connecting);
        } else if (callState.equals(IMXCall.CALL_STATE_CONNECTED)) {
            long elapsedTime = call.getCallElapsedTime();

            if (elapsedTime < 0) {
                return context.getResources().getString(R.string.call_connected);
            } else {
                return formatSecondsToHMS(elapsedTime);
            }
        } else if (callState.equals(IMXCall.CALL_STATE_ENDED)) {
            return context.getResources().getString(R.string.call_ended);
        } else if (callState.equals(IMXCall.CALL_STATE_RINGING)) {
            if (call.isIncoming()) {
                if (call.isVideo()) {
                    return context.getResources().getString(R.string.incoming_video_call);
                } else {
                    return context.getResources().getString(R.string.incoming_voice_call);
                }
            } else {
                return context.getResources().getString(R.string.call_ring);
            }
        }

        return null;
    }
}
