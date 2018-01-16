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
package im.vector.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;

import im.vector.R;
/**
 *
 */
public class PillView extends LinearLayout {
    private static final String LOG_TAG = PillView.class.getSimpleName();

    private TextView mTextView;
    private View mPillLayout;

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
        mPillLayout = findViewById(R.id.pill_layout);
    }

    /**
     * Tells if a pill can be displayed for this url.
     *
     * @param url the url
     * @return true if a pill can be made.
     */
    public static boolean isPillable(String url) {
        boolean isSupported = (null != url) && url.startsWith("https://matrix.to/#/");

        if (isSupported) {
            String linkedItem = url.substring("https://matrix.to/#/".length());
            isSupported = MXSession.isRoomAlias(linkedItem) || MXSession.isUserId(linkedItem);
        }

        return isSupported;
    }

    /**
     * Update the pills text.
     *
     * @param text the pills
     * @param url  the URL
     */
    public void setText(CharSequence text, String url) {
        mTextView.setText(text.toString());

        TypedArray a = getContext().getTheme().obtainStyledAttributes(new int[]{MXSession.isRoomAlias(text.toString()) ? R.attr.pill_background_room_alias : R.attr.pill_background_user_id});
        int attributeResourceId = a.getResourceId(0, 0);
        a.recycle();

        mPillLayout.setBackground(ContextCompat.getDrawable(getContext(), attributeResourceId));

        a = getContext().getTheme().obtainStyledAttributes(new int[]{MXSession.isRoomAlias(text.toString()) ? R.attr.pill_text_color_room_alias : R.attr.pill_text_color_user_id});
        attributeResourceId = a.getResourceId(0, 0);
        a.recycle();
        mTextView.setTextColor(ContextCompat.getColor(getContext(), attributeResourceId));
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
     * @return a snapshot of the view
     */
    public Drawable getDrawable() {
        try {
            if (null == getDrawingCache()) {
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
            Log.e(LOG_TAG, "## getDrawable() : failed " + e.getMessage());
        }

        return null;
    }
}
