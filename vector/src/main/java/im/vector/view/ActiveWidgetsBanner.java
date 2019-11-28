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

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.jetbrains.annotations.Nullable;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.data.Room;

import java.util.ArrayList;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.widgets.Widget;
import im.vector.widgets.WidgetManagerProvider;
import im.vector.widgets.WidgetsManager;

/**
 * This class displays the active widgets
 */
public class ActiveWidgetsBanner extends FrameLayout {
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

        mCloseWidgetIcon = findViewById(R.id.close_widget_icon);
        mCloseWidgetIcon.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mUpdateListener) {
                    try {
                        mUpdateListener.onCloseWidgetClick(mActiveWidgets.get(0));
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## initView() : onCloseWidgetClick failed " + e.getMessage(), e);
                    }
                }
            }
        });

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mUpdateListener) {
                    try {
                        mUpdateListener.onClick(mActiveWidgets);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## initView() : onClick failed " + e.getMessage(), e);
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
            List<Widget> activeWidgets = WidgetsManager.getActiveWebviewWidgets(mSession, mRoom);
            Widget firstWidget = null;

            if ((activeWidgets.size() != mActiveWidgets.size()) || !mActiveWidgets.containsAll(activeWidgets)) {
                mActiveWidgets = activeWidgets;

                if (1 == mActiveWidgets.size()) {
                    firstWidget = mActiveWidgets.get(0);
                    mWidgetTypeTextView.setText(firstWidget.getHumanName());
                } else if (mActiveWidgets.size() > 1) {
                    mWidgetTypeTextView.setText(mContext.getResources().getQuantityString(R.plurals.active_widgets,
                            mActiveWidgets.size(), mActiveWidgets.size()));
                }

                if (null != mUpdateListener) {
                    try {
                        mUpdateListener.onActiveWidgetsListUpdate();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## refresh() : onActiveWidgetUpdate failed " + e.getMessage(), e);
                    }
                }
            }

            setVisibility((mActiveWidgets.size() > 0) ? View.VISIBLE : View.GONE);

            // show the close widget button if the user is allowed to do it
            mCloseWidgetIcon.setVisibility(((null != firstWidget) && (null == WidgetsManager.checkWidgetPermission(mSession, mRoom))) ?
                    View.VISIBLE : View.GONE);
        }
    }

    /**
     * The parent activity is resumed
     */
    public void onActivityResume() {
        refresh();
        WidgetsManager wm = getWidgetManager(getContext());
        if (wm != null) {
            wm.addListener(mWidgetListener);
        }
    }

    /**
     * The parent activity is suspended
     */
    public void onActivityPause() {
        WidgetsManager wm = getWidgetManager(getContext());
        if (wm != null) {
            wm.removeListener(mWidgetListener);
        }
    }

    @Nullable
    private WidgetsManager getWidgetManager(Context activity) {
        if (Matrix.getInstance(activity) == null) return null;
        MXSession session = Matrix.getInstance(activity).getDefaultSession();
        if (session == null) return null;
        WidgetManagerProvider widgetManagerProvider = Matrix.getInstance(activity).getWidgetManagerProvider(session);
        if (widgetManagerProvider == null) return null;
        return widgetManagerProvider.getWidgetManager(activity);
    }
}
