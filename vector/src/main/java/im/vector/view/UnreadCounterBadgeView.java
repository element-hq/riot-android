/*
 * Copyright 2017 Vector Creations Ltd
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
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.core.content.ContextCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import im.vector.R;
import im.vector.ui.themes.ThemeUtils;
import im.vector.util.RoomUtils;
import im.vector.util.ViewUtilKt;

/**
 * Badge for the bottom navigation bar of the Home Activity
 */
public class UnreadCounterBadgeView extends FrameLayout {
    // the background settings
    public static final int HIGHLIGHTED = 0;
    public static final int NOTIFIED = 1;
    public static final int DEFAULT = 2;

    @IntDef({HIGHLIGHTED, NOTIFIED, DEFAULT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
    }

    private TextView mCounterTextView;

    public UnreadCounterBadgeView(Context context) {
        super(context);
        init();
    }

    public UnreadCounterBadgeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public UnreadCounterBadgeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.unread_counter_badge, this);
        mCounterTextView = findViewById(R.id.unread_counter_badge_text_view);
    }

    /**
     * Update the badge value and its status
     *
     * @param value  the new value
     * @param status the new status
     */
    public void updateCounter(int value, @Status int status) {
        updateText(RoomUtils.formatUnreadMessagesCounter(value), status);
    }

    /**
     * Update the badge value and its status
     *
     * @param text   the new text value
     * @param status the new status
     */
    public void updateText(String text, @Status int status) {
        if (!TextUtils.isEmpty(text)) {
            mCounterTextView.setText(text);

            setVisibility(View.VISIBLE);

            int color;

            if (status == HIGHLIGHTED) {
                color = ContextCompat.getColor(getContext(), R.color.vector_fuchsia_color);
            } else if (status == NOTIFIED) {
                color = ThemeUtils.INSTANCE.getColor(getContext(), R.attr.vctr_notice_secondary);
            } else { //if (status == DEFAULT)
                color = ThemeUtils.INSTANCE.getColor(getContext(), R.attr.vctr_unread_room_indent_color);
            }

            ViewUtilKt.setRoundBackground(mCounterTextView, color);
        } else {
            setVisibility(View.GONE);
        }
    }
}
