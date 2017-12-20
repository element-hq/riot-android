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
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.gson.JsonElement;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.MXMediaDownloadListener;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;

import java.io.File;
import java.util.List;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.adapters.VectorMediasViewerAdapter;
import im.vector.db.VectorContentProvider;
import im.vector.util.SlidableMediaInfo;
import im.vector.util.ThemeUtils;

/**
 * Display a medias list.
 */
public class VectorMediasViewerActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = VectorMediasViewerActivity.class.getSimpleName();

    public static final String KEY_INFO_LIST = "ImageSliderActivity.KEY_INFO_LIST";
    public static final String KEY_INFO_LIST_INDEX = "ImageSliderActivity.KEY_INFO_LIST_INDEX";

    public static final String KEY_THUMBNAIL_WIDTH = "ImageSliderActivity.KEY_THUMBNAIL_WIDTH";
    public static final String KEY_THUMBNAIL_HEIGHT = "ImageSliderActivity.KEY_THUMBNAIL_HEIGHT";

    public static final String EXTRA_MATRIX_ID = "ImageSliderActivity.EXTRA_MATRIX_ID";

    // session
    private MXSession mSession;

    // the pager
    private ViewPager mViewPager;

    // the pager adapter
    private VectorMediasViewerAdapter mAdapter;

    // the medias list
    private List<SlidableMediaInfo> mMediasList;

    private MenuItem mShareMenuItem;

    // the slide effect
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

        if ((null == mSession) || !mSession.isAlive()) {
            finish();
            Log.d(LOG_TAG, "onCreate : invalid session");
            return;
        }

        mMediasList = (List<SlidableMediaInfo>) intent.getSerializableExtra(KEY_INFO_LIST);

        if ((null == mMediasList) || (0 == mMediasList.size())) {
            finish();
            return;
        }

        setContentView(R.layout.activity_vector_medias_viewer);
        mViewPager = findViewById(R.id.view_pager);

        int position = Math.min(intent.getIntExtra(KEY_INFO_LIST_INDEX, 0), mMediasList.size() - 1);
        int maxImageWidth = intent.getIntExtra(KEY_THUMBNAIL_WIDTH, 0);
        int maxImageHeight = intent.getIntExtra(VectorMediasViewerActivity.KEY_THUMBNAIL_HEIGHT, 0);

        mAdapter = new VectorMediasViewerAdapter(this, mSession, mSession.getMediasCache(), mMediasList, maxImageWidth, maxImageHeight);
        mViewPager.setAdapter(mAdapter);
        mViewPager.setPageTransformer(true, new DepthPageTransformer());
        mAdapter.autoPlayItemAt(position);
        mViewPager.setCurrentItem(position);

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

                // disable shared for encrypted files as they are saved in a tmp folder
                if (null != mShareMenuItem) {
                    mShareMenuItem.setVisible(null == mMediasList.get(position).mEncryptedFileInfo);
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
        CommonActivityUtils.tintMenuIcons(menu, ThemeUtils.getColor(this, R.attr.icon_tint_on_dark_action_bar_color));

        mShareMenuItem = menu.findItem(R.id.ic_action_share);
        if (null != mShareMenuItem) {
            mShareMenuItem.setVisible(null == mMediasList.get(mViewPager.getCurrentItem()).mEncryptedFileInfo);
        }

        return true;
    }

    /**
     * Download the current video file
     */
    private void onAction(final int position, final int action) {
        MXMediasCache mediasCache = Matrix.getInstance(this).getMediasCache();
        final SlidableMediaInfo mediaInfo = mMediasList.get(position);

        // check if the media has already been downloaded
        if (mediasCache.isMediaCached(mediaInfo.mMediaUrl, mediaInfo.mMimeType)) {
            mediasCache.createTmpMediaFile(mediaInfo.mMediaUrl, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo, new SimpleApiCallback<File>() {
                @Override
                public void onSuccess(File file) {
                    // sanity check
                    if (null == file) {
                        return;
                    }

                    if (action == R.id.ic_action_download) {
                        CommonActivityUtils.saveMediaIntoDownloads(VectorMediasViewerActivity.this, file, mediaInfo.mFileName, mediaInfo.mMimeType, new SimpleApiCallback<String>() {
                            @Override
                            public void onSuccess(String savedMediaPath) {
                                Toast.makeText(VectorApp.getInstance(), getText(R.string.media_slider_saved), Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        if (null != mediaInfo.mFileName) {
                            File dstFile = new File(file.getParent(), mediaInfo.mFileName);

                            if (dstFile.exists()) {
                                dstFile.delete();
                            }

                            file.renameTo(dstFile);
                            file = dstFile;
                        }

                        // shared / forward
                        Uri mediaUri = null;
                        try {
                            mediaUri = VectorContentProvider.absolutePathToUri(VectorMediasViewerActivity.this, file.getAbsolutePath());
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "onMediaAction onAction.absolutePathToUri: " + e.getMessage());
                        }

                        if (null != mediaUri) {
                            try {
                                final Intent sendIntent = new Intent();
                                sendIntent.setAction(Intent.ACTION_SEND);
                                sendIntent.setType(mediaInfo.mMimeType);
                                sendIntent.putExtra(Intent.EXTRA_STREAM, mediaUri);
                                startActivity(sendIntent);
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## onAction : cannot display the media " + mediaUri + " mimeType " + mediaInfo.mMimeType);
                                CommonActivityUtils.displayToast(VectorMediasViewerActivity.this, e.getLocalizedMessage());
                            }
                        }
                    }
                }
            });
        } else {
            // else download it
            final String downloadId = mediasCache.downloadMedia(this, mSession.getHomeServerConfig(), mediaInfo.mMediaUrl, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo);

            if (null != downloadId) {
                mediasCache.addDownloadListener(downloadId, new MXMediaDownloadListener() {
                    @Override
                    public void onDownloadError(String downloadId, JsonElement jsonElement) {
                        MatrixError error = JsonUtils.toMatrixError(jsonElement);

                        if ((null != error) && error.isSupportedErrorCode()) {
                            Toast.makeText(VectorMediasViewerActivity.this, error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        }
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

        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.ic_action_share:
            case R.id.ic_action_download:
                onAction(mViewPager.getCurrentItem(), id);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
