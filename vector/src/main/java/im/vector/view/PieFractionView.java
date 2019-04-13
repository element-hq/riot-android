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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import im.vector.R;
import im.vector.ui.themes.ThemeUtils;

/**
 * View that displays a disc representing a percentage.
 */
public class PieFractionView extends View {

    private static final int START_ANGLE = -90;

    // fraction between 0 and 100
    private int fraction = 0;

    private final RectF rectF;
    private final Paint paint;

    private int fillColor;
    private int restColor;

    public int getFillColor() {
        return ThemeUtils.INSTANCE.getColor(getContext(), R.attr.colorAccent);
    }

    public int getRestColor() {
        return getResources().getColor(R.color.vector_dark_grey_color);
    }

    @SuppressWarnings("ResourceType")
    public PieFractionView(Context context, AttributeSet attrs) {
        super(context, attrs);

        int[] attrArray = new int[]{android.R.attr.layout_width, android.R.attr.layout_height};
        TypedArray typedArray = context.obtainStyledAttributes(attrs, attrArray);
        int width = typedArray.getDimensionPixelSize(0, 0);
        int height = typedArray.getDimensionPixelSize(1, 0);
        typedArray.recycle();

        rectF = new RectF(0, 0, width, height);
        paint = new Paint();

        fillColor = getFillColor();
        restColor = getRestColor();
    }

    public void setFraction(int fraction) {
        // check bounds and avoid useless refresh
        if ((0 <= fraction) && (fraction <= 100) && (this.fraction != fraction)) {
            this.fraction = fraction;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int angle = fraction * 360 / 100;

        // draw the fill section
        paint.setColor(fillColor);
        canvas.drawArc(rectF, START_ANGLE, angle, true, paint);

        // draw the rest of the circle
        paint.setColor(restColor);
        canvas.drawArc(rectF, START_ANGLE + angle, 360 - angle, true, paint);
    }
}
