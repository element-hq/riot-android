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
import android.util.AttributeSet;

import org.matrix.androidsdk.util.Log;

import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import im.vector.R;
import im.vector.widgets.Widget;
import im.vector.widgets.WidgetsManager;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;

import java.util.ArrayList;
import java.util.List;

/**
 * This class displays the active widgets
 */
public class ActiveWidgetsBanner extends RelativeLayout {
    private static final String LOG_TAG = ActiveWidgetsBanner.class.getSimpleName();

    public interface onUpdateListener {
        /**
         * The user clicks on the close widget button.
         *
         * @param widget the widget
         */
        void onCloseWidgetClick(Widget widget);

        /**
         * Warn that the current active widget has been updated
         */
        void onActiveWidgetsListUpdate();

        /**
         * Click on the banner
         */
        void onClick(List<Widget> widgets);
    }

    private Context mContext;

    //
    private MXSession mSession;
    private Room mRoom;

    // the active widgets list
    private List<Widget> mActiveWidgets = new ArrayList<>();

    // close widget icon
    private View mCloseWidgetIcon;

    //
    private TextView mWidgetTypeTextView;

    // listener
    private onUpdateListener mUpdateListener;

    /**
     * widget management
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
    public ActiveWidgetsBanner(Context context) {
        super(context);
        initView(context);
    }

    public ActiveWidgetsBanner(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public ActiveWidgetsBanner(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context);
    }

    /**
     * Common initialisation method.
     */
    private void initView(Context context) {
        mContext = context;

        View.inflate(getContext(), R.layout.active_widget_banner, this);
        mWidgetTypeTextView = findViewById(R.id.widget_type_text_view);

        mCloseWidgetIcon = findViewById(R.id.close_widget_icon_container);
        mCloseWidgetIcon.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mUpdateListener) {
                    try {
                        mUpdateListener.onCloseWidgetClick(mActiveWidgets.get(0));
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## initView() : onCloseWidgetClick failed " + e.getMessage());
                    }
                }
            }
        });

        this.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mUpdateListener) {
                    try {
                        mUpdateListener.onClick(mActiveWidgets);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## initView() : onClick failed " + e.getMessage());
                    }
                }
            }
        });
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
     * Set a listener
     *
     * @param listener the new listener
     */
    public void setOnUpdateListener(onUpdateListener listener) {
        mUpdateListener = listener;
    }

    /**
     * Refresh the view visibility
     */
    private void refresh() {
        if ((null != mRoom) && (null != mSession)) {
            List<Widget> activeWidgets = WidgetsManager.getSharedInstance().getActiveWebviewWidgets(mSession, mRoom);
            Widget firstWidget = null;

            if ((activeWidgets.size() != mActiveWidgets.size()) || !mActiveWidgets.containsAll(activeWidgets)) {
                mActiveWidgets = activeWidgets;

                if (1 == mActiveWidgets.size()) {
                    firstWidget = mActiveWidgets.get(0);
                    mWidgetTypeTextView.setText(firstWidget.getHumanName());
                } else if (mActiveWidgets.size() > 1) {
                    mWidgetTypeTextView.setText(mContext.getResources().getQuantityString(R.plurals.active_widgets, mActiveWidgets.size(), mActiveWidgets.size()));
                }

                if (null != mUpdateListener) {
                    try {
                        mUpdateListener.onActiveWidgetsListUpdate();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## refresh() : onActiveWidgetUpdate failed " + e.getMessage());
                    }
                }
            }

            setVisibility((mActiveWidgets.size() > 0) ? View.VISIBLE : View.GONE);

            // show the close widget button if the user is allowed to do it
            mCloseWidgetIcon.setVisibility(((null != firstWidget) && (null == WidgetsManager.getSharedInstance().checkWidgetPermission(mSession, mRoom))) ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * The parent activity is resumed
     */
    public void onActivityResume() {
        refresh();
        WidgetsManager.getSharedInstance().addListener(mWidgetListener);
    }

    /**
     * The parent activity is suspended
     */
    public void onActivityPause() {
        WidgetsManager.getSharedInstance().removeListener(mWidgetListener);
    }
}
