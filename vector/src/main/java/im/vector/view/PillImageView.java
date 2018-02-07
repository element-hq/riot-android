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
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.util.AttributeSet;

import java.lang.ref.WeakReference;

/**
 * Avatar image view used in PillView
 */
public class PillImageView extends VectorCircularImageView {
    // listener
    private WeakReference<PillView.OnUpdateListener> mOnUpdateListener = null;

    public PillImageView(Context context) {
        super(context);
    }

    public PillImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PillImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void setCircularImageDrawable(final RoundedBitmapDrawable cachedDrawable) {
        super.setCircularImageDrawable(cachedDrawable);

        if ((null != mOnUpdateListener) && (null != mOnUpdateListener.get())) {
            mOnUpdateListener.get().onAvatarUpdate();
        }
    }

    /**
     * Update the update listener
     *
     * @param listener the new update listener
     */
    public void setOnUpdateListener(PillView.OnUpdateListener listener) {
        mOnUpdateListener = new WeakReference<>(listener);
    }
}
