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
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.gson.JsonElement;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.util.JsonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.VectorMediasViewerAdapter;
import im.vector.db.VectorContentProvider;
import im.vector.util.SlidableMediaInfo;

public class VectorMediasViewerActivity extends MXCActionBarActivity {

    public static final String LOG_TAG = "VectorMediasViewerActivity";

    public static final String KEY_INFO_LIST = "ImageSliderActivity.KEY_INFO_LIST";
    public static final String KEY_INFO_LIST_INDEX = "ImageSliderActivity.KEY_INFO_LIST_INDEX";

    public static final String KEY_THUMBNAIL_WIDTH = "ImageSliderActivity.KEY_THUMBNAIL_WIDTH";
    public static final String KEY_THUMBNAIL_HEIGHT = "ImageSliderActivity.KEY_THUMBNAIL_HEIGHT";

    public static final String EXTRA_MATRIX_ID = "ImageSliderActivity.EXTRA_MATRIX_ID";

    private MXSession mSession;
    private MXMediasCache mxMediasCache;
    private ViewPager mViewPager;
    private VectorMediasViewerAdapter mAdapter;

    private List<SlidableMediaInfo> mMediasList;

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

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.d(LOG_TAG, "onCreate : restart the application");
            CommonActivityUtils.restartApp(this);
            return;
        }

        if (CommonActivityUtils.isGoingToSplash(this)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen");
            return;
        }

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

        mMediasList = (List<SlidableMediaInfo>)intent.getSerializableExtra(KEY_INFO_LIST);

        setContentView(R.layout.activity_vector_medias_viewer);
        mViewPager =(ViewPager) findViewById(R.id.view_pager);

        int position = intent.getIntExtra(KEY_INFO_LIST_INDEX, 0);
        int maxImageWidth = intent.getIntExtra(KEY_THUMBNAIL_WIDTH, 0);
        int maxImageHeight = intent.getIntExtra(VectorMediasViewerActivity.KEY_THUMBNAIL_HEIGHT, 0);

        mAdapter = new VectorMediasViewerAdapter(this, mSession, mxMediasCache, mMediasList, maxImageWidth, maxImageHeight);
        mAdapter.autoPlayItemAt(position);
        mViewPager.setAdapter(mAdapter);
        mViewPager.setCurrentItem(position);
        mViewPager.setPageTransformer(true, new DepthPageTransformer());

        if (null != VectorMediasViewerActivity.this.getSupportActionBar()) {
            VectorMediasViewerActivity.this.getSupportActionBar().setTitle(mMediasList.get(position).mFileName);
        }
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (null != VectorMediasViewerActivity.this.getSupportActionBar()) {
                    VectorMediasViewerActivity.this.getSupportActionBar().setTitle(mMediasList.get(position).mFileName);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        // stop any playing video
        mAdapter.stopPlayingVideo();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // the application is in a weird state
        if (CommonActivityUtils.shouldRestartApp(this)) {
            return false;
        }

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.vector_medias_viewer, menu);
        return true;
    }

    /**
     * Download the current video file
     */
    private void onAction(final int position, final int action) {
        MXMediasCache mediasCache = Matrix.getInstance(this).getMediasCache();
        SlidableMediaInfo mediaInfo = mMediasList.get(position);

        File file = mediasCache.mediaCacheFile(mediaInfo.mMediaUrl, mediaInfo.mMimeType);

        // check if the media has already been downloaded
        if (null != file) {
            // download
            if (action == R.id.ic_action_download) {
                if (null != CommonActivityUtils.saveMediaIntoDownloads(this, file, mediaInfo.mFileName, mediaInfo.mMimeType)) {
                    Toast.makeText(this, getText(R.string.media_slider_saved), Toast.LENGTH_LONG).show();
                }
            } else {
                // shared
                Uri mediaUri = null;

                File renamedFile = file;

                if (!TextUtils.isEmpty(mediaInfo.mFileName))
                    try {
                        InputStream fin = new FileInputStream(file);
                        String tmpUrl = mediasCache.saveMedia(fin, mediaInfo.mFileName, mediaInfo.mMimeType);

                        if (null != tmpUrl) {
                            renamedFile = mediasCache.mediaCacheFile(tmpUrl, mediaInfo.mMimeType);
                        }
                    } catch (Exception e) {
                    }


                if (null != renamedFile) {
                    try {
                        mediaUri = VectorContentProvider.absolutePathToUri(this, renamedFile.getAbsolutePath());
                    } catch (Exception e) {
                    }
                }

                if (null != mediaUri) {
                    final Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.setType(mediaInfo.mMimeType);
                    sendIntent.putExtra(Intent.EXTRA_STREAM, mediaUri);
                    startActivity(sendIntent);
                }
            }
        } else {
            // else download it
            final String downloadId = mediasCache.downloadMedia(this, mSession.getHomeserverConfig(), mediaInfo.mMediaUrl, mediaInfo.mMimeType);

            if (null != downloadId) {
                mediasCache.addDownloadListener(downloadId, new MXMediasCache.DownloadCallback() {
                    @Override
                    public void onDownloadStart(String downloadId) {
                    }

                    @Override
                    public void onError(String downloadId, JsonElement jsonElement) {
                        MatrixError error = JsonUtils.toMatrixError(jsonElement);

                        if ((null != error) && error.isSupportedErrorCode()) {
                            Toast.makeText(VectorMediasViewerActivity.this, error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onDownloadProgress(String aDownloadId, int percentageProgress) {
                    }

                    @Override
                    public void onDownloadComplete(String aDownloadId) {
                        if (aDownloadId.equals(downloadId)) {
                            onAction(position, action);
                        }
                    }
                });
            }
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.ic_action_share) {
            onAction(mViewPager.getCurrentItem(), id);
        } else if (id ==  R.id.ic_action_download) {
            onAction(mViewPager.getCurrentItem(), id);
        }

        return super.onOptionsItemSelected(item);
    }
}
