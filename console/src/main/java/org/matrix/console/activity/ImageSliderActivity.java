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

package org.matrix.console.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.view.View;

import org.matrix.console.R;
import org.matrix.console.adapters.ImagesSliderAdapter;
import org.matrix.console.util.SlidableImageInfo;

import java.util.List;

public class ImageSliderActivity extends FragmentActivity {

    public static final String KEY_INFO_LIST = "org.matrix.console.activity.ImageSliderActivity.KEY_INFO_LIST";
    public static final String KEY_INFO_LIST_INDEX = "org.matrix.console.activity.ImageSliderActivity.KEY_INFO_LIST_INDEX";

    public static final String KEY_THUMBNAIL_WIDTH = "org.matrix.console.activity.ImageSliderActivity.KEY_THUMBNAIL_WIDTH";
    public static final String KEY_THUMBNAIL_HEIGHT = "org.matrix.console.activity.ImageSliderActivity.KEY_THUMBNAIL_HEIGHT";

    public class DepthPageTransformer implements ViewPager.PageTransformer {
        private static final float MIN_SCALE = 0.75f;

        public void transformPage(View view, float position) {
            int pageWidth = view.getWidth();

            if (position < -1) { // [-Infinity,-1)
                // This page is way off-screen to the left.
                view.setAlpha(0);

            } else if (position <= 0) { // [-1,0]
                // Use the default slide transition when moving to the left page
                view.setAlpha(1);
                view.setTranslationX(0);
                view.setScaleX(1);
                view.setScaleY(1);

            } else if (position <= 1) { // (0,1]
                // Fade the page out.
                view.setAlpha(1 - position);

                // Counteract the default slide transition
                view.setTranslationX(pageWidth * -position);

                // Scale the page down (between MIN_SCALE and 1)
                float scaleFactor = MIN_SCALE
                        + (1 - MIN_SCALE) * (1 - Math.abs(position));
                view.setScaleX(scaleFactor);
                view.setScaleY(scaleFactor);

            } else { // (1,+Infinity]
                // This page is way off-screen to the right.
                view.setAlpha(0);
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_images_slider);

        ViewPager viewPager = (ViewPager)findViewById(R.id.view_pager);

        final Intent intent = getIntent();

        List<SlidableImageInfo> listImageMessages = (List<SlidableImageInfo>)intent.getSerializableExtra(KEY_INFO_LIST);
        int position = intent.getIntExtra(KEY_INFO_LIST_INDEX, 0);
        int maxImageWidth = intent.getIntExtra(KEY_THUMBNAIL_WIDTH, 0);
        int maxImageHeight = intent.getIntExtra(ImageSliderActivity.KEY_THUMBNAIL_HEIGHT, 0);

        ImagesSliderAdapter adapter = new ImagesSliderAdapter(this, listImageMessages, maxImageWidth, maxImageHeight);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(position);
        viewPager.setPageTransformer(true, new DepthPageTransformer());
    }
}
