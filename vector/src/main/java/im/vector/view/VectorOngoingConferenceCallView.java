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
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import im.vector.R;
import im.vector.activity.VectorCallViewActivity;
import im.vector.util.CallUtilities;
import im.vector.util.VectorUtils;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.MXCall;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.w3c.dom.Text;

/**
 * This class displays the pending call information.
 */
public class VectorOngoingConferenceCallView extends RelativeLayout {
    // call information
    private MXSession mSession;
    private Room mRoom;

    private final MXCallsManager.MXCallsManagerListener mCallsListener = new MXCallsManager.MXCallsManagerListener() {
        @Override
        public void onIncomingCall(IMXCall call) {

        }

        @Override
        public void onCallHangUp(IMXCall call) {

        }

        @Override
        public void onVoipConferenceStarted(String roomId) {
            if ((null != mRoom) && TextUtils.equals(roomId, mRoom.getRoomId())) {
                refresh();
            }
        }

        @Override
        public void onVoipConferenceFinished(String roomId) {
            if ((null != mRoom) && TextUtils.equals(roomId, mRoom.getRoomId())) {
                refresh();
            }
        }
    };

    /**
     * constructors
     **/
    public VectorOngoingConferenceCallView(Context context) {
        super(context);
        initView();
    }

    public VectorOngoingConferenceCallView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public VectorOngoingConferenceCallView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    /**
     * Common initialisation method.
     */
    private void initView() {
        View.inflate(getContext(), R.layout.vector_ongoing_conference_call, this);
    }

    /**
     * Define the room and the session.
     * @param session the session
     * @param room the room
     */
    public void initRoomInfo(MXSession session, Room room) {
        mSession = session;
        mRoom = room;
    }

    /**
     * Refresh the view visibility
     */
    public void refresh() {
        if ((null != mRoom) && (null != mSession)) {
            IMXCall call = mSession.mCallsManager.callWithRoomId(mRoom.getRoomId());
            setVisibility((!MXCallsManager.isCallInProgress(call) && mRoom.isOngoingConferenceCall()) ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * The parent activity is resumed
     */
    public void onActivityResume() {
        refresh();

        if (null != mSession) {
            mSession.mCallsManager.addListener(mCallsListener);
        }
    }

    /**
     * The parent activity is suspended
     */
    public void onActivityPause() {
        if (null != mSession) {
            mSession.mCallsManager.addListener(mCallsListener);
        }
    }
}
