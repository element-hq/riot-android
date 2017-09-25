/* 
 * Copyright 2016 OpenMarket Ltd
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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v4.util.LruCache;
import android.util.AttributeSet;

import org.matrix.androidsdk.util.Log;

/**
 * Display a circular image.
 */
public class VectorCircularImageView extends android.support.v7.widget.AppCompatImageView {
    private static final String LOG_TAG = "VCirImageView";

    public VectorCircularImageView(Context context) {
        super(context);
    }

    public VectorCircularImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VectorCircularImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);

        if ((null != drawable) && (drawable instanceof BitmapDrawable)) {
            final Bitmap b = ((BitmapDrawable)drawable).getBitmap();

            if (null != b) {
                this.post(new Runnable() {
                    @Override
                    public void run() {
                        setImageBitmap(b);
                    }
                });
            }
        }
    }

    // We use a lru cache to reduce the screen loading time.
    // Create a RoundedBitmapDrawable might be slow
    static final LruCache<String, RoundedBitmapDrawable> mCache = new LruCache<String, RoundedBitmapDrawable>(4 * 1024 * 1024) {
        @Override
        protected int sizeOf (String key, RoundedBitmapDrawable drawable) {
            return drawable.getBitmap().getRowBytes() * drawable.getBitmap().getHeight(); // size in bytes
        }
    };

    // the background thread
    private static HandlerThread mConversionImagesThread = null;
    private static android.os.Handler mConversionImagesThreadHandler = null;
    private static Handler mUIHandler = null;

    /**
     * Update the bitmap.
     * The bitmap is first squared before adding corners
     * @param bm the new bitmap
     */
    public void setImageBitmap(final Bitmap bm) {
        if (null != bm) {
            // convert the bitmap to a square bitmap
            final int width = bm.getWidth();
            final int height = bm.getHeight();

            final String key = bm.toString() + width + "-" + height;

            // We use a lru cache to reduce the screen loading time.
            // Create a RoundedBitmapDrawable might be slow
            RoundedBitmapDrawable cachedDrawable = mCache.get(key);
            if (null != cachedDrawable) {
                super.setImageDrawable(cachedDrawable);
                return;
            }

            if (null == mConversionImagesThread) {
                mConversionImagesThread = new HandlerThread("VectorCircularImageViewThread", Thread.MIN_PRIORITY);
                mConversionImagesThread.start();
                mConversionImagesThreadHandler = new android.os.Handler(mConversionImagesThread.getLooper());
                mUIHandler = new Handler(Looper.getMainLooper());
            }
            
            mConversionImagesThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    Bitmap squareBitmap = bm;

                    if (width == height) {
                        // nothing to do
                    }
                    // larger than high
                    else if (width > height){
                        try {
                            squareBitmap = Bitmap.createBitmap(
                                    squareBitmap,
                                    (width - height) / 2,
                                    0,
                                    height,
                                    height
                            );
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## setImageBitmap - createBitmap " + e.getMessage());
                        }
                    }
                    // higher than large
                    else {
                        try {
                            squareBitmap = Bitmap.createBitmap(
                                    squareBitmap,
                                    0,
                                    (height - width) / 2,
                                    width,
                                    width
                            );
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## setImageBitmap - createBitmap " + e.getMessage());
                        }
                    }
                    try {
                        // create a rounded bitmap
                        final RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(getResources(), squareBitmap);
                        drawable.setAntiAlias(true);
                        drawable.setCornerRadius(height / 2.0f);

                        mUIHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                // save it in a cache
                                mCache.put(key, drawable);
                                VectorCircularImageView.this.setImageDrawable(drawable);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## setImageBitmap - RoundedBitmapDrawableFactory.create " + e.getMessage());
                        mUIHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                VectorCircularImageView.this.setImageBitmap(null);
                            }
                        });
                    }
                }
            });
        } else {
            super.setImageBitmap(null);
        }
    }
}
