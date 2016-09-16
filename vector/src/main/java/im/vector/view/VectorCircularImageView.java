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
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Display a circular image.
 */
public class VectorCircularImageView extends ImageView {

    public VectorCircularImageView(Context context) {
        super(context);
    }

    public VectorCircularImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VectorCircularImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Update the bitmap.
     * The bitmap is first squared before adding corners
     * @param bm the new bitmap
     */
    public void setImageBitmap(Bitmap bm) {
        if (null != bm) {
            // convert the bitmap to a square bitmap
            int width = bm.getWidth();
            int height = bm.getHeight();

            if (width == height) {
                // nothing to do
            }
            // larger than high
            else if (width > height){

                bm = Bitmap.createBitmap(
                        bm,
                        (width - height) / 2,
                        0,
                        height,
                        height
                );

            }
            // higher than large
            else {
                bm = Bitmap.createBitmap(
                        bm,
                        0,
                        (height - width) / 2,
                        width,
                        width
                );
            }

            // create a rounded bitmap
            RoundedBitmapDrawable img = RoundedBitmapDrawableFactory.create(getResources(), bm);
            img.setAntiAlias(true);
            img.setCornerRadius(height / 2.0f);

            // apply it to the image
            this.setImageDrawable(img);

        } else {
            super.setImageBitmap(null);
        }
    }
}
