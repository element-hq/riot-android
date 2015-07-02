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

import org.matrix.console.R;
import org.matrix.console.adapters.ImagesSliderAdapter;
import org.matrix.console.util.SlidableImageInfo;

import java.util.List;

public class ImageSliderActivity extends FragmentActivity {

    public static final String KEY_INFO_LIST = "org.matrix.console.activity.ImageSliderActivity.KEY_INFO_LIST";
    public static final String KEY_INFO_LIST_INDEX = "org.matrix.console.activity.ImageSliderActivity.KEY_INFO_LIST_INDEX";

    public static final String KEY_THUMBNAIL_WIDTH = "org.matrix.console.activity.ImageSliderActivity.KEY_THUMBNAIL_WIDTH";
    public static final String KEY_THUMBNAIL_HEIGHT = "org.matrix.console.activity.ImageSliderActivity.KEY_THUMBNAIL_HEIGHT";

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
    }
}
