/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2018 New Vector Ltd
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
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.MXPatterns;
import org.matrix.androidsdk.core.PermalinkUtils;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.rest.model.User;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.util.VectorUtils;

/**
 * A PillView displays a user avatar and a user name in a message
 */
public class PillView extends LinearLayout {
    private static final String LOG_TAG = PillView.class.getSimpleName();

    // pill item views
    private TextView mTextView;
    private PillImageView mAvatarView;
    private View mPillLayout;

    // update listener
    public interface OnUpdateListener {
        void onAvatarUpdate();
    }

    private OnUpdateListener mOnUpdateListener = null;

    /**
     * constructors
     **/
    public PillView(Context context) {
        super(context);
        initView();
    }

    public PillView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public PillView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    /**
     * Common initialisation method.
     */
    private void initView() {
        View.inflate(getContext(), R.layout.pill_view, this);
        mTextView = findViewById(R.id.pill_text_view);
        mAvatarView = findViewById(R.id.pill_avatar_view);
        mPillLayout = findViewById(R.id.pill_layout);
    }

    /**
     * Tells if a pill can be displayed for this url.
     *
     * @param url the url
     * @return true if a pill can be made.
     */
    public static boolean isPillable(String url) {
        String linkedUrl = PermalinkUtils.getLinkedId(url);

        return (null != linkedUrl) && (MXPatterns.isRoomAlias(linkedUrl) || MXPatterns.isUserId(linkedUrl));
    }

    /**
     * Update the pills data
     *
     * @param text the pills
     * @param url  the URL
     */
    public void initData(final CharSequence text, final String url, final MXSession session, OnUpdateListener listener) {
        final String str = text.toString();

        mOnUpdateListener = listener;
        mAvatarView.setOnUpdateListener(listener);
        mTextView.setText(str);

        TypedArray a = getContext().getTheme()
                .obtainStyledAttributes(new int[]{MXPatterns.isRoomAlias(str) ? R.attr.vctr_pill_background_room_alias : R.attr.vctr_pill_background_user_id});
        int attributeResourceId = a.getResourceId(0, 0);
        a.recycle();

        mPillLayout.setBackground(ContextCompat.getDrawable(getContext(), attributeResourceId));

        a = getContext().getTheme()
                .obtainStyledAttributes(new int[]{MXPatterns.isRoomAlias(str) ? R.attr.vctr_pill_text_color_room_alias : R.attr.vctr_pill_text_color_user_id});
        attributeResourceId = a.getResourceId(0, 0);
        a.recycle();
        mTextView.setTextColor(ContextCompat.getColor(getContext(), attributeResourceId));

        final String linkedUrl = PermalinkUtils.getLinkedId(url);

        if (MXPatterns.isUserId(linkedUrl)) {
            User user = session.getDataHandler().getUser(linkedUrl);

            if (null == user) {
                user = new User();
                user.user_id = linkedUrl;
            }

            VectorUtils.loadUserAvatar(VectorApp.getInstance(), session, mAvatarView, user);
        } else {
            session.getDataHandler().roomIdByAlias(linkedUrl, new ApiCallback<String>() {
                @Override
                public void onSuccess(String roomId) {
                    if (null != mOnUpdateListener) {
                        // Check whether the room is available
                        Room room = session.getDataHandler().getRoom(roomId, false);
                        if (null != room) {
                            VectorUtils.loadRoomAvatar(VectorApp.getInstance(), session, mAvatarView, room);
                        } else {
                            // Here the room is not joined by the user yet.
                            // Display the default avatar based on the room alias.
                            final Bitmap bitmap = VectorUtils.getAvatar(VectorApp.getInstance(), VectorUtils.getAvatarColor(roomId), linkedUrl, true);
                            mAvatarView.setImageBitmap(bitmap);

                            // Fetch the preview data to display the room avatar if any.
                            final RoomPreviewData roomPreviewData = new RoomPreviewData(session, roomId, null, linkedUrl, null);
                            roomPreviewData.fetchPreviewData(new ApiCallback<Void>() {
                                @Override
                                public void onSuccess(Void info) {
                                    if (null != mOnUpdateListener) {
                                        VectorUtils.loadRoomAvatar(VectorApp.getInstance(), session, mAvatarView, roomPreviewData);
                                    }
                                }

                                @Override
                                public void onNetworkError(Exception e) {
                                    Log.e(LOG_TAG, "## initData() : fetchPreviewData failed " + e.getMessage(), e);
                                }

                                @Override
                                public void onMatrixError(MatrixError e) {
                                    Log.e(LOG_TAG, "## initData() : fetchPreviewData failed " + e.getMessage());
                                }

                                @Override
                                public void onUnexpectedError(Exception e) {
                                    Log.e(LOG_TAG, "## initData() : fetchPreviewData failed " + e.getMessage(), e);
                                }
                            });
                        }
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.e(LOG_TAG, "## initData() : roomIdByAlias failed " + e.getMessage(), e);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.e(LOG_TAG, "## initData() : roomIdByAlias failed " + e.getMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.e(LOG_TAG, "## initData() : roomIdByAlias failed " + e.getMessage(), e);
                }
            });
        }
    }

    /**
     * Set the highlight status
     *
     * @param isHighlighted
     */
    public void setHighlighted(boolean isHighlighted) {
        if (isHighlighted) {
            mPillLayout.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.pill_background_bing));
            mTextView.setTextColor(ContextCompat.getColor(getContext(), android.R.color.white));
        }
    }

    /**
     * Return a snapshot of the view
     *
     * @param forceUpdate tell whether the cached data must be ignored or not.
     * @return a snapshot of the view
     */
    public Drawable getDrawable(boolean forceUpdate) {
        try {
            if (forceUpdate || null == getDrawingCache()) {
                destroyDrawingCache();
                setDrawingCacheEnabled(true);
                measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                layout(0, 0, getMeasuredWidth(), getMeasuredHeight());
                buildDrawingCache(true);
            }

            if (null != getDrawingCache()) {
                Bitmap bitmap = Bitmap.createBitmap(getDrawingCache());
                return new BitmapDrawable(getContext().getResources(), bitmap);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getDrawable() : failed " + e.getMessage(), e);
        }

        return null;
    }
}
