/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2018 New Vector Ltd
 * Copyright 2019 New Vector Ltd
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
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.jetbrains.annotations.Nullable;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.IMXCallsManagerListener;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.call.MXCallsManagerListener;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.data.Room;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import im.vector.Matrix;
import im.vector.R;
import im.vector.widgets.Widget;
import im.vector.widgets.WidgetManagerProvider;
import im.vector.widgets.WidgetsManager;

/**
 * This class displays if there is an ongoing conference call.
 */
public class VectorOngoingConferenceCallView extends RelativeLayout {
    private static final String LOG_TAG = VectorOngoingConferenceCallView.class.getSimpleName();

    // video / voice text click listener.
    public interface ICallClickListener {
        /**
         * The user clicks on the voice text.
         *
         * @param widget the active widget (if any)
         */
        void onVoiceCallClick(Widget widget);

        /**
         * The user clicks on the video text.
         *
         * @param widget the active widget (if any)
         */
        void onVideoCallClick(Widget widget);

        /**
         * The user clicks on the close widget button.
         *
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

    @BindView(R.id.ongoing_conference_call_text_view)
    TextView mConferenceCallTextView;

    // close widget icon
    @BindView(R.id.close_widget_icon)
    View mCloseWidgetIcon;

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
        inflate(getContext(), R.layout.vector_ongoing_conference_call, this);
        ButterKnife.bind(this);

        // "voice" and "video" texts are underlined and clickable
        String voiceString = getContext().getString(R.string.ongoing_conference_call_voice);
        String videoString = getContext().getString(R.string.ongoing_conference_call_video);

        String fullMessage = getContext().getString(R.string.ongoing_conference_call, voiceString, videoString);

        SpannableString ss = new SpannableString(fullMessage);

        int pos = ss.toString().indexOf(voiceString);

        ss.setSpan(new ClickableSpan() {
                       @Override
                       public void onClick(View textView1) {
                           if (null != mCallClickListener) {
                               try {
                                   mCallClickListener.onVoiceCallClick(mActiveWidget);
                               } catch (Exception e) {
                                   Log.e(LOG_TAG, "## initView() : onVoiceCallClick failed " + e.getMessage(), e);
                               }
                           }
                       }
                   },
                pos,
                pos + voiceString.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(Typeface.BOLD),
                pos,
                pos + voiceString.length(),
                0);

        pos = ss.toString().indexOf(videoString);

        ss.setSpan(new ClickableSpan() {
                       @Override
                       public void onClick(View textView1) {
                           if (null != mCallClickListener) {
                               try {
                                   mCallClickListener.onVideoCallClick(mActiveWidget);
                               } catch (Exception e) {
                                   Log.e(LOG_TAG, "## initView() : onVideoCallClick failed " + e.getMessage(), e);
                               }
                           }
                       }
                   }, pos,
                pos + videoString.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(Typeface.BOLD),
                pos,
                pos + videoString.length(),
                0);

        mConferenceCallTextView.setText(ss);
        mConferenceCallTextView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @OnClick(R.id.close_widget_icon)
    void onClose() {
        if (null != mCallClickListener) {
            try {
                mCallClickListener.onCloseWidgetClick(mActiveWidget);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## initView() : onRemoveWidgetClick failed " + e.getMessage(), e);
            }
        }
    }

    /**
     * Define the room and the session.
     *
     * @param session the session
     * @param room    the room
     */
    public void initRoomInfo(MXSession session, Room room) {
        mSession = session;
        mRoom = room;
    }

    /**
     * Set a call click listener
     *
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
            List<Widget> mActiveWidgets = WidgetsManager.getActiveJitsiWidgets(mSession, mRoom);
            Widget widget = mActiveWidgets.isEmpty() ? null : mActiveWidgets.get(0);

            if (mActiveWidget != widget) {
                mActiveWidget = widget;
                if (null != mCallClickListener) {
                    try {
                        mCallClickListener.onActiveWidgetUpdate();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## refresh() : onActiveWidgetUpdate failed " + e.getMessage(), e);
                    }
                }
            }

            IMXCall call = mSession.mCallsManager.getCallWithRoomId(mRoom.getRoomId());
            setVisibility(((!MXCallsManager.isCallInProgress(call) && mRoom.isOngoingConferenceCall()) || (null != mActiveWidget)) ? View.VISIBLE : View.GONE);

            // show the close widget button if the user is allowed to do it
            mCloseWidgetIcon.setVisibility(((null != mActiveWidget) && (null == WidgetsManager.checkWidgetPermission(mSession, mRoom))) ?
                    View.VISIBLE : View.GONE);
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
        WidgetsManager wm = Matrix.getWidgetManager(getContext());
        if (wm != null) {
            wm.addListener(mWidgetListener);
        }
    }

    /**
     * The parent activity is suspended
     */
    public void onActivityPause() {
        if (null != mSession) {
            mSession.mCallsManager.removeListener(mCallsListener);
        }
        WidgetsManager wm = Matrix.getWidgetManager(getContext());
        if (wm != null) {
            wm.removeListener(mWidgetListener);
        }
    }

    /**
     * @return the current active widget
     */
    public Widget getActiveWidget() {
        return mActiveWidget;
    }

}
