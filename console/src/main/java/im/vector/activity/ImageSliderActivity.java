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

package im.vector.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.view.View;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.db.MXMediasCache;

import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.ImagesSliderAdapter;
import im.vector.util.SlidableMediaInfo;

public class ImageSliderActivity extends FragmentActivity {

    public static final String KEY_INFO_LIST = "ImageSliderActivity.KEY_INFO_LIST";
    public static final String KEY_INFO_LIST_INDEX = "ImageSliderActivity.KEY_INFO_LIST_INDEX";

    public static final String KEY_THUMBNAIL_WIDTH = "ImageSliderActivity.KEY_THUMBNAIL_WIDTH";
    public static final String KEY_THUMBNAIL_HEIGHT = "ImageSliderActivity.KEY_THUMBNAIL_HEIGHT";

    public static final String EXTRA_MATRIX_ID = "ImageSliderActivity.EXTRA_MATRIX_ID";

    private MXSession mSession;
    private MXMediasCache mxMediasCache;
    private ViewPager mViewPager;
    private ImagesSliderAdapter mAdapter;

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
        if (CommonActivityUtils.shouldRestartApp()) {
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);

        String matrixId = null;
        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_MATRIX_ID)) {
            matrixId = intent.getStringExtra(EXTRA_MATRIX_ID);
        }

        mSession = Matrix.getInstance(getApplicationContext()).getSession(matrixId);

        if (mSession == null) {
            finish();
            return;
        }

        mxMediasCache = mSession.getMediasCache();

        final List<SlidableMediaInfo> mediasList = (List<SlidableMediaInfo>)intent.getSerializableExtra(KEY_INFO_LIST);

        setContentView(R.layout.activity_images_slider);
        mViewPager=(ViewPager) findViewById(R.id.view_pager);

        int position = intent.getIntExtra(KEY_INFO_LIST_INDEX, 0);
        int maxImageWidth = intent.getIntExtra(KEY_THUMBNAIL_WIDTH, 0);
        int maxImageHeight = intent.getIntExtra(ImageSliderActivity.KEY_THUMBNAIL_HEIGHT, 0);

        mAdapter = new ImagesSliderAdapter(this,mxMediasCache, mediasList, maxImageWidth, maxImageHeight);

        mAdapter.autPlayItemAt(position);
        mViewPager.setAdapter(mAdapter);
        mViewPager.setCurrentItem(position);
        mViewPager.setPageTransformer(true,new DepthPageTransformer());
    }

    @Override
    protected void onPause() {
        super.onPause();

        // stop any playing video
        mAdapter.stopPlayingVideo();
    }
}
