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
import android.graphics.Movie;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import im.vector.R;

public class ThreeDotsImageView extends ImageView {
    long mAnimationStartTime = -1;

    //
    static byte[] mDotBytesArray = null;
    Movie mMovie = null;
    static long mMovieDuration = 0;

    public ThreeDotsImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ThreeDotsImageView(Context context) {
        super(context);
    }

    /**
     * Construct a 3 dots animation from a gif files.
     * @param context the application context
     * @param attrs the attribute set
     */
    public ThreeDotsImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);

        if (null == mDotBytesArray) {
            try {
                InputStream inputStream = context.getResources().openRawResource(R.raw.dotdotdot);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);

                int len;
                byte[] buffer = new byte[1024];

                while ((len = inputStream.read(buffer)) >= 0) {
                    byteArrayOutputStream.write(buffer, 0, len);
                }

                mDotBytesArray = byteArrayOutputStream.toByteArray();
            } catch (Exception e) {
            }
        }

        // succeed to decode the movie
        if (null != mDotBytesArray) {
            mMovie = Movie.decodeByteArray(mDotBytesArray, 0, mDotBytesArray.length);

            if (null != mMovie) {
                mMovieDuration = mMovie.duration();

                if (0 == mMovieDuration) {
                    mMovieDuration = 3000; // ms
                }
            }

            this.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        mAnimationStartTime = System.currentTimeMillis();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.currentTimeMillis();

        if (mAnimationStartTime < 0) {
            mAnimationStartTime = now;
        }
        if (null != mMovie) {
            int relTime = (int) ((now - mAnimationStartTime) % mMovieDuration);
            mMovie.setTime(relTime);
            mMovie.draw(canvas, 0 ,0);
            // refresh asap
            invalidate();
        }
    }
}
