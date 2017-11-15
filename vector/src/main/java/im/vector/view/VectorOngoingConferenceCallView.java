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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;

import org.matrix.androidsdk.call.IMXCallsManagerListener;
import org.matrix.androidsdk.call.MXCallsManagerListener;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import im.vector.R;
import im.vector.widgets.Widget;
import im.vector.widgets.WidgetsManager;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.data.Room;

import java.util.List;

/**
 * This class displays if there is an ongoing conference call.
 */
public class VectorOngoingConferenceCallView extends RelativeLayout {
    private static final String LOG_TAG = "OngConferenceCallView";

    // video / voice text click listener.
    public interface ICallClickListener {
        /**
         * The user clicks on the voice text.
         * @param widget the active widget (if any)
         */
        void onVoiceCallClick(Widget widget);

        /**
         * The user clicks on the video text.
         * @param widget the active widget (if any)
         */
        void onVideoCallClick(Widget widget);

        /**
         * The user clicks on the close widget button.
         * @param widget the widget
         */
        void onCloseWidgetClick(Widget widget);

        /**
         * Warn that the current active widget has been updated
         */
        void onActiveWidgetUpdate();
    }

    // call information
    private MXSession mSession;
    private Room mRoom;

    // the linked widget
    private Widget mActiveWidget;

    // close widget icon
    private View mCloseWidgetIcon;

    private ICallClickListener mCallClickListener;

    private final IMXCallsManagerListener mCallsListener = new MXCallsManagerListener() {
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
     * Jitsi calls management
     */
    private final WidgetsManager.onWidgetUpdateListener mWidgetListener = new WidgetsManager.onWidgetUpdateListener() {
        @Override
        public void onWidgetUpdate(Widget widget) {
            refresh();
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

        TextView textView = (TextView) findViewById(R.id.ongoing_conference_call_text_view);
        ClickableSpan voiceClickableSpan = new ClickableSpan() {
            @Override
            public void onClick(View textView) {
                if (null != mCallClickListener) {
                    try {
                        mCallClickListener.onVoiceCallClick(mActiveWidget);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## initView() : onVoiceCallClick failed " + e.getMessage());
                    }
                }
            }
        };

        ClickableSpan videoClickableSpan = new ClickableSpan() {
            @Override
            public void onClick(View textView) {
                if (null != mCallClickListener) {
                    try {
                        mCallClickListener.onVideoCallClick(mActiveWidget);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## initView() : onVideoCallClick failed " + e.getMessage());
                    }
                }
            }
        };

        SpannableString ss = new SpannableString(textView.getText());
        
        // "voice" and "video" texts are underlined
        // and clickable
        String voiceString = getContext().getString(R.string.ongoing_conference_call_voice);
        int pos = ss.toString().indexOf(voiceString);

        if (pos >= 0) {
            ss.setSpan(voiceClickableSpan, pos, pos + voiceString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new UnderlineSpan(), pos, pos + voiceString.length(), 0);
        } else {
            Log.e(LOG_TAG, "## initView() : cannot find " + voiceString + " in " +  ss.toString());
        }

        String videoString = getContext().getString(R.string.ongoing_conference_call_video);
        pos = ss.toString().indexOf(videoString);

        if (pos >= 0) {
            ss.setSpan(videoClickableSpan, pos, pos + videoString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new UnderlineSpan(), pos, pos + videoString.length(), 0);
        } else {
            Log.e(LOG_TAG, "## initView() : cannot find " + videoString + " in " +  ss.toString());
        }
        
        textView.setText(ss);
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        mCloseWidgetIcon = findViewById(R.id.close_widget_icon_container);
        mCloseWidgetIcon.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mCallClickListener) {
                    try {
                        mCallClickListener.onCloseWidgetClick(mActiveWidget);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## initView() : onCloseWidgetClick failed " + e.getMessage());
                    }
                }
            }
        });
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
     * Set a call click listener
     * @param callClickListener the new call listener
     */
    public void setCallClickListener(ICallClickListener callClickListener) {
        mCallClickListener = callClickListener;
    }

    /**
     * Refresh the view visibility
     */
    public void refresh() {
        if ((null != mRoom) && (null != mSession)) {
            List<Widget> mActiveWidgets = WidgetsManager.getSharedInstance().getActiveJitsiWidgets(mSession, mRoom);
            Widget widget = mActiveWidgets.isEmpty() ? null : mActiveWidgets.get(0);

            if (mActiveWidget != widget) {
                mActiveWidget = widget;
                if (null != mCallClickListener) {
                    try {
                        mCallClickListener.onActiveWidgetUpdate();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## refresh() : onActiveWidgetUpdate failed " + e.getMessage());
                    }
                }
            }

            IMXCall call = mSession.mCallsManager.getCallWithRoomId(mRoom.getRoomId());
            setVisibility(((!MXCallsManager.isCallInProgress(call) && mRoom.isOngoingConferenceCall()) || (null != mActiveWidget)) ? View.VISIBLE : View.GONE);

            // show the close widget button if the user is allowed to do it
            mCloseWidgetIcon.setVisibility(((null != mActiveWidget) && (null == WidgetsManager.getSharedInstance().checkWidgetPermission(mSession, mRoom))) ? View.VISIBLE : View.GONE);
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

        WidgetsManager.addListener(mWidgetListener);
    }

    /**
     * The parent activity is suspended
     */
    public void onActivityPause() {
        if (null != mSession) {
            mSession.mCallsManager.removeListener(mCallsListener);
        }

        WidgetsManager.removeListener(mWidgetListener);
    }

    /**
     * @return the current active widget
     */
    public Widget getActiveWidget() {
        return mActiveWidget;
    }
}
