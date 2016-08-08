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
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;

/**
 * This class displays the pending call information.
 */
public class VectorOngoingConferenceCallView extends RelativeLayout {
    // call information
    private MXSession mSession;
    private Room mRoom;

    public final MXEventListener mEventsListener = new MXEventListener()  {
        @Override
        public void onLiveEvent(Event event, RoomState roomState) {
            if (TextUtils.equals(event.type, Event.EVENT_TYPE_STATE_ROOM_MEMBER)) {
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
    private void refresh() {
        if ((null != mRoom) && (null != mSession)) {
            if (null != mSession.mCallsManager.callWithRoomId(mRoom.getRoomId())) {
                setVisibility(View.GONE);
            } else {
                setVisibility(mRoom.isOngoingConferenceCall() ? View.VISIBLE : View.GONE);
            }
        }
    }

    /**
     * The parent activity is resumed
     */
    public void onActivityResume() {
        refresh();

        if (null != mRoom) {
            mRoom.addEventListener(mEventsListener);
        }
    }

    /**
     * The parent activity is suspended
     */
    public void onActivityPause() {
        if (null != mRoom) {
            mRoom.removeEventListener(mEventsListener);
        }
    }
}
