/* 
 * Copyright 2017 Vector Creations Ltd
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
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.w3c.dom.Text;

import im.vector.R;

public class UnreadCounterBadgeView extends RelativeLayout {

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
        mCounterTextView = (TextView) findViewById(R.id.unread_counter_badge_text_view);
    }

    /**
     * Update the badge value
     *
     * @param value the new value
     */
    public void updateCounter(int value) {
        if (value > 0) {
            if (value > 999) {
                mCounterTextView.setText("999+");
            } else {
                mCounterTextView.setText(value + "");
            }
            setVisibility(View.VISIBLE);
        } else {
            setVisibility(View.INVISIBLE);
        }
    }
}
