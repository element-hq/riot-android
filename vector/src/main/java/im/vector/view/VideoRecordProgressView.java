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
import android.os.Handler;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;
import android.view.View;

import org.matrix.androidsdk.R;

import java.util.Timer;
import java.util.TimerTask;

import im.vector.util.ThemeUtils;

/**
 * View that displays a disc representing a percentage.
 */
public class VideoRecordProgressView extends View {

    private static final int PROGRESS_STEP = 360 / 30;
    private static final int START_ANGLE = -90;

    // The fraction between 0 and 100
    private int mAngle;

    private int mRoundCount = 0;

    private RectF mRectF;
    private Paint mPaint;

    private int mPowerColor;
    private int mRestColor;

    private Handler mUIHandler = new Handler();
    private Runnable mProgressHandler = new Runnable() {
        public void run() {
            mAngle += PROGRESS_STEP;

            if (mAngle >= 360) {
                mAngle = 0;
                mRoundCount++;
                refreshColor();
            }

            VideoRecordProgressView.this.invalidate();

            // call me 1 second later
            mUIHandler.postDelayed(this, 1000);
        }
    };

    @SuppressWarnings("ResourceType")
    public VideoRecordProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);

        int[] attrArray = new int[] {android.R.attr.layout_width, android.R.attr.layout_height};
        TypedArray typedArray = context.obtainStyledAttributes(attrs, attrArray);
        int width = typedArray.getDimensionPixelSize(0, 0);
        int height = typedArray.getDimensionPixelSize(1, 0);
		if (typedArray != null) {
			typedArray.recycle();
		}
        mRectF = new RectF(0, 0, width, height);
        mPaint = new Paint();
    }

    /**
     * Refresh the progress colors
     */
    private void refreshColor() {
        if (0 == mRoundCount) {
            mPowerColor = getResources().getColor(android.R.color.white);
            mRestColor = getResources().getColor(android.R.color.transparent);
        } else {
            int mod = (mRoundCount - 1) % 2;


            @ColorInt final int silver = ThemeUtils.getColor(getContext(), im.vector.R.attr.vector_silver_color);
            @ColorInt final int white = getResources().getColor(android.R.color.white);
            mPowerColor = ((0 == mod) ? silver : white);
            mRestColor = ((0 != mod) ? silver : white);
        }
    }

    /**
     * Start the video recording animation
     */
    public void startAnimation() {
        stopAnimation();

        mAngle = 0;
        mRoundCount = 0;
        refreshColor();
        mProgressHandler.run();

        this.invalidate();
    }

    /**
     * Stop the animation
     */
    public void stopAnimation() {
        mUIHandler.removeCallbacks(mProgressHandler);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        // if the view is hidden
        if ((View.GONE == visibility) || (View.INVISIBLE == visibility)) {
            stopAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw the power part
        mPaint.setColor(mPowerColor);
        canvas.drawArc(mRectF, START_ANGLE, mAngle, true, mPaint);

        // Draw the rest
        mPaint.setColor(mRestColor);
        canvas.drawArc(mRectF, START_ANGLE + mAngle, 360 - mAngle, true, mPaint);
    }
}
