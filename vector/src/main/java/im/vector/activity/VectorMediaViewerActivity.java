/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2018 New Vector Ltd
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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.viewpager.widget.ViewPager;

import com.google.gson.JsonElement;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.db.MXMediaCache;
import org.matrix.androidsdk.listeners.MXMediaDownloadListener;

import java.io.File;
import java.util.List;

import im.vector.BuildConfig;
import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.adapters.VectorMediaViewerAdapter;
import im.vector.util.PermissionsToolsKt;
import im.vector.util.SlidableMediaInfo;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Display a medias list.
 */
public class VectorMediaViewerActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = VectorMediaViewerActivity.class.getSimpleName();

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
    private VectorMediaViewerAdapter mAdapter;

    // the medias list
    private List<SlidableMediaInfo> mMediasList;

    // Pending data during permission request
    private int mPendingPosition;
    private int mPendingAction;

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
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_vector_media_viewer;
    }

    @Override
    public void initUiAndData() {
        configureToolbar();

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

        mViewPager = findViewById(R.id.view_pager);

        int position = Math.min(intent.getIntExtra(KEY_INFO_LIST_INDEX, 0), mMediasList.size() - 1);
        int maxImageWidth = intent.getIntExtra(KEY_THUMBNAIL_WIDTH, 0);
        int maxImageHeight = intent.getIntExtra(VectorMediaViewerActivity.KEY_THUMBNAIL_HEIGHT, 0);

        mAdapter = new VectorMediaViewerAdapter(this, mSession, mSession.getMediaCache(), mMediasList, maxImageWidth, maxImageHeight);
        mViewPager.setAdapter(mAdapter);
        mViewPager.setPageTransformer(true, new DepthPageTransformer());
        mAdapter.autoPlayItemAt(position);
        mViewPager.setCurrentItem(position);

        if (null != getSupportActionBar()) {
            getSupportActionBar().setTitle(mMediasList.get(position).mFileName);
        }
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (null != getSupportActionBar()) {
                    getSupportActionBar().setTitle(mMediasList.get(position).mFileName);
                }

                // disable shared for encrypted files as they are saved in a tmp folder
                supportInvalidateOptionsMenu();
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
    public int getMenuRes() {
        return R.menu.vector_medias_viewer;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // the application is in a weird state
        if (CommonActivityUtils.shouldRestartApp(this)) {
            return false;
        }

        MenuItem shareMenuItem = menu.findItem(R.id.ic_action_share);
        if (null != shareMenuItem) {
            // disable shared for encrypted files as they are saved in a tmp folder
            shareMenuItem.setVisible(null == mMediasList.get(mViewPager.getCurrentItem()).mEncryptedFileInfo);
        }

        return true;
    }

    /**
     * Download the current video file
     */
    private void onAction(final int position, final int action) {
        final MXMediaCache mediasCache = Matrix.getInstance(this).getMediaCache();
        final SlidableMediaInfo mediaInfo = mMediasList.get(position);

        // check if the media has already been downloaded
        if (mediasCache.isMediaCached(mediaInfo.mMediaUrl, mediaInfo.mMimeType)) {
            mediasCache.createTmpDecryptedMediaFile(mediaInfo.mMediaUrl, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo, new SimpleApiCallback<File>() {
                @Override
                public void onSuccess(File file) {
                    // sanity check
                    if (null == file) {
                        return;
                    }

                    if (action == R.id.ic_action_download) {
                        if (checkWritePermission(PermissionsToolsKt.PERMISSION_REQUEST_CODE)) {
                            CommonActivityUtils.saveMediaIntoDownloads(VectorMediaViewerActivity.this,
                                    file, mediaInfo.mFileName, mediaInfo.mMimeType, new SimpleApiCallback<String>() {
                                        @Override
                                        public void onSuccess(String savedMediaPath) {
                                            Toast.makeText(VectorApp.getInstance(), getText(R.string.media_slider_saved), Toast.LENGTH_LONG).show();
                                        }
                                    });
                        } else {
                            mPendingPosition = position;
                            mPendingAction = action;
                        }
                    } else {
                        // Move the file to the Share folder, to avoid it to be deleted because the Activity will be paused while the
                        // user select an application to share the file
                        if (null != mediaInfo.mFileName) {
                            file = mediasCache.moveToShareFolder(file, mediaInfo.mFileName);
                        } else {
                            file = mediasCache.moveToShareFolder(file, file.getName());
                        }

                        // shared / forward
                        Uri mediaUri = null;
                        try {
                            mediaUri = FileProvider.getUriForFile(VectorMediaViewerActivity.this, BuildConfig.APPLICATION_ID + ".fileProvider", file);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "onMediaAction Selected File cannot be shared " + e.getMessage(), e);
                        }

                        if (null != mediaUri) {
                            try {
                                final Intent sendIntent = new Intent();
                                // Grant temporary read permission to the content URI
                                sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                sendIntent.setAction(Intent.ACTION_SEND);
                                sendIntent.setType(mediaInfo.mMimeType);
                                sendIntent.putExtra(Intent.EXTRA_STREAM, mediaUri);
                                startActivity(sendIntent);
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## onAction : cannot display the media " + mediaUri + " mimeType " + mediaInfo.mMimeType, e);
                                Toast.makeText(VectorMediaViewerActivity.this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            });
        } else {
            // else download it
            final String downloadId = mediasCache.downloadMedia(this,
                    mSession.getHomeServerConfig(),
                    mediaInfo.mMediaUrl,
                    mediaInfo.mMimeType,
                    mediaInfo.mEncryptedFileInfo);

            if (null != downloadId) {
                mediasCache.addDownloadListener(downloadId, new MXMediaDownloadListener() {
                    @Override
                    public void onDownloadError(String downloadId, JsonElement jsonElement) {
                        MatrixError error = JsonUtils.toMatrixError(jsonElement);

                        if ((null != error) && error.isSupportedErrorCode()) {
                            Toast.makeText(VectorMediaViewerActivity.this, error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
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
        switch (item.getItemId()) {
            case R.id.ic_action_share:
            case R.id.ic_action_download:
                onAction(mViewPager.getCurrentItem(), item.getItemId());
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public boolean checkWritePermission(int requestCode) {
        return PermissionsToolsKt.checkPermissions(PermissionsToolsKt.PERMISSIONS_FOR_WRITING_FILES, this, requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (PermissionsToolsKt.allGranted(grantResults)) {
            if (requestCode == PermissionsToolsKt.PERMISSION_REQUEST_CODE) {
                // Request comes from here
                onAction(mPendingPosition, mPendingAction);
            }
        }
    }
}
