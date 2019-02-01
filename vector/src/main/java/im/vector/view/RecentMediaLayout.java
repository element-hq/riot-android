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
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import im.vector.R;

public class RecentMediaLayout extends RelativeLayout {

    private ImageView mThumbnailView;
    private ImageView mTypeView;
    private View mSelectedItemView;

    public RecentMediaLayout(Context context) {
        super(context);
        init();
    }

    public RecentMediaLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RecentMediaLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.layout_media_thumbnail, this);

        mThumbnailView = findViewById(R.id.media_thumbnail_view);
        mTypeView = findViewById(R.id.media_type_view);
        mSelectedItemView = findViewById(R.id.media_selected_mask_view);
    }

    /**
     * @return true when the layout is displayed as selected
     */
    public boolean isSelected() {
        return mSelectedItemView.getVisibility() == View.VISIBLE;
    }

    /**
     * Update the layout thumbnail.
     *
     * @param thumbnail the thumbnail
     */
    public void setThumbnail(Bitmap thumbnail) {
        mThumbnailView.setImageBitmap(thumbnail);
    }

    public void setThumbnailByUri(Uri aThumbnailUri) {
        mThumbnailView.setImageURI(aThumbnailUri);
    }

    public void setThumbnailByResource(int aThumbnailResource) {
        mThumbnailView.setImageResource(aThumbnailResource);
    }

    /**
     * Enable the display of the video thumbnail
     *
     * @param isVideo true to display video thumbnail
     */
    public void setVideoType(boolean isVideo) {
        mTypeView.setImageResource(isVideo ? R.drawable.ic_material_movie : R.drawable.ic_material_photo);
    }

    /**
     * Enable the display of the gif thumbnail
     *
     * @param isGif true to display gif thumbnail
     */
    public void setGifType(boolean isGif) {
        mTypeView.setImageResource(isGif ? R.drawable.filetype_gif : R.drawable.ic_material_photo);
    }
}
