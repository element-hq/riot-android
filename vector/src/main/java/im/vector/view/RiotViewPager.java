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
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;

import org.matrix.androidsdk.util.Log;

/**
 * Patch the issue "https://code.google.com/p/android/issues/detail?id=66620"
 */
public class RiotViewPager extends android.support.v4.view.ViewPager {
    private static final String LOG_TAG = RiotViewPager.class.getSimpleName();

    public RiotViewPager(Context context) {
        super(context);
    }

    public RiotViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (getAdapter() == null || getAdapter().getCount() == 0) {
            return false;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (getAdapter() == null || getAdapter().getCount() == 0) {
            return false;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        try {
            return super.getChildDrawingOrder(childCount, i);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getChildDrawingOrder() failed " + e.getMessage());
        }

        return 0;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        try {
            super.dispatchDraw(canvas);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## dispatchDraw() failed " + e.getMessage());
        }
    }
}
