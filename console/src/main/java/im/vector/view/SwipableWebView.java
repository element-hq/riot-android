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

import android.content.ClipData;
import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;

public class SwipableWebView extends WebView {

    static int THRESHOLD = 100;


    /**
     * Construct a new WebView with layout parameters and a default style.
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     */
    public SwipableWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }


    @Override
    protected void onOverScrolled (int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        if (scrollX > (getWidth() + THRESHOLD) && clampedX) {
            int left = 0;
            left++;
            //onSwipeLeft();
        }
        if (scrollX < - THRESHOLD && clampedX) {
            int right = 0;
            right++;
        }
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
    }

}
