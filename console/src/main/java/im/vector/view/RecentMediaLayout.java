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
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import im.vector.R;

public class RecentMediaLayout extends RelativeLayout {

    ImageView mThumbnailView;
    ImageView mTypeView;
    View mSelectedItemView;

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
        inflate(getContext(), R.layout.recent_media, this);

        mThumbnailView = (ImageView)findViewById(R.id.media_thumbnail_view);
        mTypeView = (ImageView)findViewById(R.id.media_type_view);
        mSelectedItemView = findViewById(R.id.media_selected_mask_view);
        mSelectedItemView.setVisibility(View.GONE);
    }

    /**
     * @return true when the layout is displayed as selected
     */
    public boolean isSelected() {
        return mSelectedItemView.getVisibility() == View.VISIBLE;
    }

    /**
     * Mark the item as selected
     * @param isSelected true if the layout must be displayed as selected.
     */
    public void setIsSelected(Boolean isSelected) {
        mSelectedItemView.setVisibility(isSelected ? View.VISIBLE : View.GONE);
    }

    /**
     * Update the layout thumbnail.
     * @param thumnail the thumbnail
     */
    public void setThumbnail(Bitmap thumnail) {
        mThumbnailView.setImageBitmap(thumnail);
    }

    /**
     * Update the media type view
     * @param isVideo true to display the view type thumbnail
     */
    public void setIsVideo(Boolean isVideo) {
        mTypeView.setImageResource(isVideo ? R.drawable.ic_material_movie  : R.drawable.ic_material_photo);
    }
}
