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

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import im.vector.R;
import im.vector.view.RecentMediaLayout;

import android.hardware.Camera;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class VectorMediasPickerActivity extends MXCActionBarActivity implements SurfaceHolder.Callback {
    // medias folder
    private static final int REQUEST_MEDIAS = 0;
    private static final String LOG_TAG = "VectorMedPicker";

    public static final String EXTRA_SINGLE_IMAGE_MODE = "im.vector.activity.VectorMediasPickerActivity.EXTRA_SINGLE_IMAGE_MODE";

    /**
     * define a recent media
     */
    private class RecentMedia {
        public Uri mFileUri;
        public long mCreationTime;
        public Bitmap mThumbnail = null;
        public Boolean mIsvideo = false;
        public RecentMediaLayout mRecentMediaLayout = null;
    }

    // recents medias list
    private ArrayList<RecentMedia> mRecentsMedias = new ArrayList<RecentMedia>();
    private ArrayList<RecentMedia> mSelectedRecents = new ArrayList<RecentMedia>();

    // camera object
    private Camera mCamera = null;
    // start with back camera
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

    // graphical items
    ImageView mSwitchCameraImageView = null;
    ImageView mRecordModeImageView = null;

    TextView mCaptureTitleTextView = null;
    ImageView mTakePhotoImageView = null;
    SurfaceHolder mCameraSurfaceHolder = null;
    SurfaceView mCameraSurfaceView = null;
    ImageView mCameraDefaultView = null;
    Button mRetakeButton = null;
    Button mAttachImageButton = null;
    Button mOpenLibraryButton = null;
    Button mUseImagesListButton = null;
    LinearLayout mRecentsListView = null;
    HorizontalScrollView mRecentsScrollView = null;
    VideoView mVideoView = null;

    //
    MediaRecorder mMediaRecorder = null;

    private String mShootedPicturePath = null;
    private String mRecordedVideoPath = null;
    private Boolean mIsPreviewStarted = false;

    static private Boolean mIsSingleImageMode = false;
    static private Boolean mIsPhotoMode = true;

    int mCameraOrientation = 0;

    /**
     * The recent requests are performed in a dedicated thread
     */
    private HandlerThread mHandlerThread = null;
    private android.os.Handler mFileHandler = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_medias_picker);

        // retrieving item from UI
        mSwitchCameraImageView = (ImageView) findViewById(R.id.medias_picker_switch_camera);
        mCameraSurfaceView = (SurfaceView) findViewById(R.id.medias_picker_surface_view);
        mCameraDefaultView = (ImageView) findViewById(R.id.medias_picker_preview);
        mRetakeButton = (Button) findViewById(R.id.medias_picker_retake_button);
        mTakePhotoImageView = (ImageView) findViewById(R.id.medias_picker_camera_button);
        mAttachImageButton = (Button) findViewById(R.id.medias_picker_attach1_button);
        mOpenLibraryButton = (Button) findViewById(R.id.medias_picker_library_button);
        mUseImagesListButton = (Button) findViewById(R.id.medias_picker_attach2_button);
        mRecentsListView = (LinearLayout) findViewById(R.id.medias_picker_recents_listview);
        mRecentsScrollView = (HorizontalScrollView) findViewById(R.id.medias_picker_recents_scrollview);
        mRecordModeImageView = (ImageView) findViewById(R.id.medias_picker_recording_mode);
        mVideoView = (VideoView) findViewById(R.id.medias_picker_video_view);
        mCaptureTitleTextView = (TextView) findViewById(R.id.medias_picker_camera_title);

        Intent intent = getIntent();
        mIsSingleImageMode = intent.hasExtra(EXTRA_SINGLE_IMAGE_MODE);

        if (mIsSingleImageMode) {
            mRecordModeImageView.setVisibility(View.INVISIBLE);
        }

        // click action
        mSwitchCameraImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.switchCamera();
            }
        });

        mTakePhotoImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.takePicture();
            }
        });

        mRetakeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.retake();
            }
        });

        mAttachImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.attachImage();
            }
        });

        mOpenLibraryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.openFileExplorer();
            }
        });

        mUseImagesListButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.attachCarouselMedias();
            }
        });

        mRecordModeImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.mIsPhotoMode = !mIsPhotoMode;
                VectorMediasPickerActivity.this.manageButtons();
            }
        });

        mHandlerThread = new HandlerThread("VectorMediasPickerActivityThread");
        mHandlerThread.start();
        mFileHandler = new android.os.Handler(mHandlerThread.getLooper());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopVideoPlayer();
        stopVideoRecord();

        if (null != mHandlerThread) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
    }

    /**
     * Enable user interactivity
     * @param view the view
     */
    private void enableView(View view) {
        view.setEnabled(true);
        view.setAlpha(1.0f);
    }

    /**
     * Disable user interactivity
     * @param view the view
     */
    private void disableView(View view) {
        view.setEnabled(false);
        view.setAlpha(0.5f);
    }

    /**
     * Manage the buttons status
     */
    private void manageButtons() {

        // avoid having an empty area
        if ((null == mCamera) && (null == mRecordedVideoPath)) {
            mCameraDefaultView.setVisibility(View.VISIBLE);
            mCameraSurfaceView.setVisibility(View.GONE);
            mVideoView.setVisibility(View.GONE);
        } else if (null != mRecordedVideoPath) {
            mCameraSurfaceView.setVisibility(View.GONE);
            mVideoView.setVisibility(View.VISIBLE);
        } else {
            mCameraDefaultView.setVisibility(View.GONE);
            mCameraSurfaceView.setVisibility(View.VISIBLE);
            mVideoView.setVisibility(View.GONE);
        }

        // no camera
        if ((null == mCamera) && (null == mRecordedVideoPath)) {
            disableView(mRecordModeImageView);
            disableView(mTakePhotoImageView);
            disableView(mRetakeButton);
            disableView(mAttachImageButton);
            disableView(mSwitchCameraImageView);
        } else if (null != mMediaRecorder)  {
            enableView(mTakePhotoImageView);
            disableView(mRetakeButton);
            disableView(mAttachImageButton);
            disableView(mSwitchCameraImageView);
            disableView(mRecordModeImageView);
        } else if (null != mRecordedVideoPath) {
            enableView(mTakePhotoImageView);
            enableView(mRetakeButton);
            enableView(mAttachImageButton);
            enableView(mSwitchCameraImageView);
            enableView(mRecordModeImageView);
        } else if (null == mShootedPicturePath) {
            enableView(mTakePhotoImageView);
            disableView(mRetakeButton);
            disableView(mAttachImageButton);
            enableView(mSwitchCameraImageView);
            enableView(mRecordModeImageView);
        } else {
            disableView(mTakePhotoImageView);
            enableView(mRetakeButton);
            enableView(mAttachImageButton);
            disableView(mSwitchCameraImageView);
            disableView(mSwitchCameraImageView);
        }

        // must have more than 2 cameras
        if (2 > Camera.getNumberOfCameras()) {
            disableView(mSwitchCameraImageView);
        }

        // selection from the media list
        if (mSelectedRecents.size() > 0)  {
            enableView(mUseImagesListButton);
        } else {
            disableView(mUseImagesListButton);
        }

        // manage the take picture button
        if (mIsPhotoMode) {
            mRecordModeImageView.setImageResource(R.drawable.ic_material_camera);
            mTakePhotoImageView.setImageResource(R.drawable.ic_material_camera);
            mCaptureTitleTextView.setText(R.string.media_picker_picture_capture_title);
        } else {
            mCaptureTitleTextView.setText(R.string.media_picker_video_capture_title);
            mRecordModeImageView.setImageResource(R.drawable.ic_material_videocam);
            // playing mode
            if (mVideoView.getVisibility() == View.VISIBLE) {
                if (mVideoView.isPlaying()) {
                    mTakePhotoImageView.setImageResource(R.drawable.ic_material_stop);
                } else {
                    mTakePhotoImageView.setImageResource(R.drawable.ic_material_play_circle);
                }
            } else if (null != mMediaRecorder) {
                // stop the record
                mTakePhotoImageView.setImageResource(R.drawable.ic_material_stop);
            } else {
                // wait that the user start the video recording
                mTakePhotoImageView.setImageResource(R.drawable.ic_material_videocam);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopVideoPlayer();

        // cancel the camera use
        // to avoid locking it
        if (null != mCamera) {
            mCamera.stopPreview();
            mIsPreviewStarted = false;
            manageButtons();
        }
    }

    private int getCarouselItemWidth() {
        return mRecentsScrollView.getLayoutParams().height;
    }

    private void startCameraPreview() {
        // should always be true
        if (null == mCamera) {
            // check if the device has at least camera
            if (Camera.getNumberOfCameras() > 0) {
                mVideoView.setVisibility(View.GONE);
                mCameraDefaultView.setVisibility(View.GONE);
                mCameraSurfaceView.setVisibility(View.VISIBLE);

                if (null == mCameraSurfaceHolder) {
                    mCameraSurfaceHolder = mCameraSurfaceView.getHolder();
                    mCameraSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                    mCameraSurfaceHolder.setSizeFromLayout();
                    mCameraSurfaceHolder.addCallback(VectorMediasPickerActivity.this);
                }
            }
        } else {
            mCamera.startPreview();
            manageButtons();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (0 == mRecentsMedias.size()) {
            refreshRecentsMediasList();
        }

        // should always be true
        if (null == mCamera) {
            startCameraPreview();
        } else {
            if ((null == mShootedPicturePath) && (null == mRecordedVideoPath)) {
                try {
                    mCamera.startPreview();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "mCamera.startPreview failed " + e.getLocalizedMessage());

                    // the preview cannot be resumed close this activity
                    VectorMediasPickerActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            VectorMediasPickerActivity.this.finish();
                        }
                    });
                }
            }
            manageButtons();
        }
    }

    @SuppressLint("NewApi")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_MEDIAS) {
                // provide the Uri
                Bundle conData = new Bundle();
                Intent intent = new Intent();
                intent.setData(data.getData());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    intent.setClipData(data.getClipData());
                }
                intent.putExtras(conData);
                setResult(RESULT_OK, intent);
                finish();
            }
        }
    }

    /**
     * List the existing images thumbnails
     * @param maxLifetime the max image lifetime
     */
    private void addImagesThumbnails(long maxLifetime) {
        final String[] projection = {MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.DATE_TAKEN};
        Cursor thumbnailsCursor = null;

        try {
            thumbnailsCursor = this.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, // Which columns to return
                    null,       // Return all rows
                    null,
                    MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC");
        } catch (Exception e) {
            Log.e(LOG_TAG, "addImagesThumbnails" + e.getLocalizedMessage());
        }

        if (null != thumbnailsCursor) {
            int timeIndex = thumbnailsCursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN);
            int idIndex = thumbnailsCursor.getColumnIndex(MediaStore.Images.ImageColumns._ID);

            if (thumbnailsCursor.moveToFirst()) {
                do {
                    try {
                        RecentMedia recentMedia = new RecentMedia();
                        recentMedia.mIsvideo = false;

                        String id = thumbnailsCursor.getString(idIndex);
                        String dateAsString = thumbnailsCursor.getString(timeIndex);
                        recentMedia.mCreationTime = Long.parseLong(dateAsString);

                        if ((maxLifetime > 0) && ((System.currentTimeMillis() - recentMedia.mCreationTime) > maxLifetime)) {
                            break;
                        }

                        recentMedia.mThumbnail = MediaStore.Images.Thumbnails.getThumbnail(this.getContentResolver(), Long.parseLong(id), MediaStore.Images.Thumbnails.MICRO_KIND, null);
                        recentMedia.mFileUri = Uri.parse(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString() + "/" + id);

                        if (null != recentMedia.mThumbnail) {
                            mRecentsMedias.add(recentMedia);
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "addImagesThumbnails 2" + e.getLocalizedMessage());
                    }
                } while (thumbnailsCursor.moveToNext());
            }
            thumbnailsCursor.close();
        }
    }

    /**
     * List the existing video thumbnails
     * @param maxLifetime the max image lifetime
     */
    private void addVideoThumbnails(long maxLifetime) {
        final String[] projection = {MediaStore.Video.VideoColumns._ID, MediaStore.Video.VideoColumns.DATE_TAKEN};
        Cursor thumbnailsCursor = null;


        try {
            thumbnailsCursor = this.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection, // Which columns to return
                    null,       // Return all rows
                    null,
                    MediaStore.Video.VideoColumns.DATE_TAKEN + " DESC");
        } catch (Exception e) {
            Log.e(LOG_TAG, "addVideoThumbnails" + e.getLocalizedMessage());
        }

        if (null != thumbnailsCursor) {
            int timeIndex = thumbnailsCursor.getColumnIndex(MediaStore.Video.VideoColumns.DATE_TAKEN);
            int idIndex = thumbnailsCursor.getColumnIndex(MediaStore.Video.VideoColumns._ID);

            if (thumbnailsCursor.moveToFirst()) {
                do {
                    try {
                        RecentMedia recentMedia = new RecentMedia();
                        recentMedia.mIsvideo = true;

                        String id = thumbnailsCursor.getString(idIndex);
                        String dateAsString = thumbnailsCursor.getString(timeIndex);
                        recentMedia.mCreationTime = Long.parseLong(dateAsString);

                        if ((maxLifetime > 0) && ((System.currentTimeMillis() - recentMedia.mCreationTime) > maxLifetime)) {
                            break;
                        }
                        recentMedia.mThumbnail = MediaStore.Video.Thumbnails.getThumbnail(this.getContentResolver(), Long.parseLong(id), MediaStore.Video.Thumbnails.MICRO_KIND, null);
                        recentMedia.mFileUri = Uri.parse(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString() + "/" + id);

                        if (null != recentMedia.mThumbnail) {
                            mRecentsMedias.add(recentMedia);
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "addVideoThumbnails 2" + e.getLocalizedMessage());
                    }
                } while (thumbnailsCursor.moveToNext());
            }
            thumbnailsCursor.close();
        }
    }

    /**
     * Refresh the recent medias
     */
    private void refreshRecentsMediasList() {
        // the last 30 days
        final long maxLifetime = 1000L * 60L * 60L * 24L * 30L;

        mRecentsListView.removeAllViews();
        mRecentsMedias.clear();

        mFileHandler.post(new Runnable() {
            @Override
            public void run() {
                addImagesThumbnails(maxLifetime);

                if (!mIsSingleImageMode) {
                    addVideoThumbnails(maxLifetime);

                    Collections.sort(mRecentsMedias, new Comparator<RecentMedia>() {
                        @Override
                        public int compare(RecentMedia r1, RecentMedia r2) {
                            long t1 = r1.mCreationTime;
                            long t2 = r2.mCreationTime;

                            // sort from the most recent
                            return -(t1 < t2 ? -1 : (t1 == t2 ? 0 : 1));
                        }
                    });
                }

                VectorMediasPickerActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int itemWidth = getCarouselItemWidth();

                        for (RecentMedia recentMedia : mRecentsMedias) {
                            final RecentMediaLayout recentMediaLayout = new RecentMediaLayout(VectorMediasPickerActivity.this);

                            recentMediaLayout.setThumbnail(recentMedia.mThumbnail);
                            recentMediaLayout.setIsVideo(recentMedia.mIsvideo);

                            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(itemWidth, ViewGroup.LayoutParams.MATCH_PARENT);
                            recentMediaLayout.setLayoutParams(params);

                            recentMedia.mRecentMediaLayout = recentMediaLayout;
                            mRecentsListView.addView(recentMediaLayout, params);

                            final RecentMedia frecentMedia = recentMedia;

                            recentMediaLayout.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    // unselect it ?
                                    if (recentMediaLayout.isSelected()) {
                                        mSelectedRecents.remove(frecentMedia);
                                    } else {
                                        // single image mode : disable any previously selected image
                                        if ((mIsSingleImageMode || (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)) && (mSelectedRecents.size() > 0)) {
                                            mSelectedRecents.get(0).mRecentMediaLayout.setIsSelected(false);
                                            mSelectedRecents.clear();
                                        }

                                        mSelectedRecents.add(frecentMedia);
                                    }

                                    recentMediaLayout.setIsSelected(!recentMediaLayout.isSelected());
                                    VectorMediasPickerActivity.this.manageButtons();
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    /**
     * Stop the video player
     */
    private void stopVideoPlayer() {
        if ((null != mVideoView) && mVideoView.isPlaying()) {
            mVideoView.stopPlayback();
        }
    }

    /**
     * Stop the video recorder
     */
    private void stopVideoRecord() {
        if (null != mMediaRecorder) {
            try {
                mMediaRecorder.stop();
                mMediaRecorder.reset();
            } catch (Exception e) {
            }
        }

        mMediaRecorder = null;
    }

    /**
     * Take a picture of the current preview
     */
    void takePicture() {
        // a video is recorded
        if (null != mRecordedVideoPath) {
            // play
            if (mVideoView.isPlaying()) {
                mVideoView.stopPlayback();
                refreshVideoThumbnail(true);
            } else {
                refreshVideoThumbnail(false);
                mVideoView.start();

                mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        if (null != mRecordedVideoPath) {
                            manageButtons();
                            refreshVideoThumbnail(true);
                        }
                    }
                });
            }
            manageButtons();
        } else if (null != mCamera) {
            if (mIsPhotoMode) {
                mCamera.takePicture(null, null, new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                        File dstFile = new File(getCacheDir().getAbsolutePath(), "edited.jpg");

                        // remove any previously saved image
                        if (dstFile.exists()) {
                            dstFile.delete();
                        }

                        // Copy source file to destination
                        FileOutputStream outputStream = null;
                        try {
                            // create only the
                            if (!dstFile.exists()) {
                                dstFile.createNewFile();

                                outputStream = new FileOutputStream(dstFile);

                                byte[] buffer = new byte[1024 * 10];
                                int len;
                                while ((len = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, len);
                                }
                            }
                            mShootedPicturePath = dstFile.getAbsolutePath();
                            manageButtons();
                        } catch (Exception e) {
                            Toast.makeText(VectorMediasPickerActivity.this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                        } finally {
                            // Close resources
                            try {
                                if (inputStream != null) inputStream.close();
                                if (outputStream != null) outputStream.close();
                            } catch (Exception e) {
                            }
                        }
                    }
                });
            } else {
                // video mode
                File videoFile = new File(getCacheDir().getAbsolutePath(), "EditedVideo.mp4");

                // not yet started
                if (null == mMediaRecorder) {
                    if (videoFile.exists()) {
                        videoFile.delete();
                    }

                    try {
                        mCamera.lock();
                        mCamera.unlock();

                        mMediaRecorder = new MediaRecorder();
                        mMediaRecorder.setCamera(mCamera);
                        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                        CamcorderProfile cpHigh = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
                        mMediaRecorder.setProfile(cpHigh);
                        mMediaRecorder.setOrientationHint(mCameraOrientation);
                        mMediaRecorder.setPreviewDisplay(mCameraSurfaceHolder.getSurface());
                        mMediaRecorder.setOutputFile(videoFile.getAbsolutePath());
                    } catch (Exception e) {
                        stopVideoRecord();
                        Toast.makeText(VectorMediasPickerActivity.this, "Cannot start the record" + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                        Log.e(LOG_TAG, "Cannot start the record" + e.getLocalizedMessage());
                    }

                    // the media recorder has been created
                    if (null != mMediaRecorder) {
                        try {
                            mMediaRecorder.prepare();

                            VectorMediasPickerActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        mMediaRecorder.start();
                                        manageButtons();
                                    } catch (Exception e) {
                                        stopVideoRecord();
                                        Toast.makeText(VectorMediasPickerActivity.this, "Cannot start the record" + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                                        Log.e(LOG_TAG, "Cannot start the record" + e.getLocalizedMessage());
                                    }
                                }
                            });
                        } catch (Exception e) {
                            stopVideoRecord();
                            Toast.makeText(VectorMediasPickerActivity.this, "Cannot start the record" + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                            Log.e(LOG_TAG, "Cannot start the record" + e.getLocalizedMessage());
                        }
                    }
                } else {
                    stopVideoRecord();

                    if (videoFile.exists()) {
                        mRecordedVideoPath = videoFile.getAbsolutePath();
                        mVideoView.setVideoPath(mRecordedVideoPath);
                        manageButtons();
                        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                            public void onPrepared(MediaPlayer mp) {
                                refreshVideoThumbnail(true);
                            }
                        });
                    }
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private void refreshVideoThumbnail(boolean show) {
        BitmapDrawable bitmapDrawable = null;

        if (show && (null != mRecordedVideoPath)) {
            Bitmap thumb = ThumbnailUtils.createVideoThumbnail(mRecordedVideoPath, MediaStore.Images.Thumbnails.MINI_KIND);
            bitmapDrawable = new BitmapDrawable(VectorMediasPickerActivity.this.getResources(), thumb);
        }
        // display the video thumbnail
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)) {
            mVideoView.setBackground(bitmapDrawable);
        } else {
            mVideoView.setBackgroundDrawable(bitmapDrawable);
        }
    }

    /**
     * Cancel the taken image
     */
    void retake() {
        stopVideoPlayer();
        mShootedPicturePath = null;
        mRecordedVideoPath = null;
        manageButtons();

        startCameraPreview();
    }

    /**
     * The taken image is accepted
     */
    void attachImage() {
        stopVideoPlayer();

        try {
            String uriString;

            if (null != mShootedPicturePath) {
                uriString = CommonActivityUtils.saveImageIntoGallery(this, new File(mShootedPicturePath));
            } else {
                uriString = CommonActivityUtils.saveIntoMovies(this, new File(mRecordedVideoPath));
            }

            // sanity check
            if (null != uriString) {
                Uri uri = Uri.fromFile(new File(uriString));

                // provide the Uri
                Bundle conData = new Bundle();
                Intent intent = new Intent();
                intent.setData(uri);
                intent.putExtras(conData);
                setResult(RESULT_OK, intent);
                finish();
            }
        } catch (Exception e) {
            setResult(RESULT_CANCELED, null);
            finish();
        }
    }

    void openFileExplorer() {
        stopVideoPlayer();

        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        // did not find a way to filter image and video files
        fileIntent.setType(mIsSingleImageMode ? "image/*" : "*/*");
        startActivityForResult(fileIntent, REQUEST_MEDIAS);
    }

    @SuppressLint("NewApi")
    void attachCarouselMedias() {

        Bundle conData = new Bundle();
        Intent intent = new Intent();

        if ((mSelectedRecents.size() == 1) || (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)) {
            // provide the Uri
            intent.setData(mSelectedRecents.get(0).mFileUri);
        } else if (mSelectedRecents.size() > 0) {
            ClipData.Item firstUri = new ClipData.Item(null, null, null, mSelectedRecents.get(0).mFileUri);
            String[] mimeType = { "*/*" };
            ClipData clipData = new ClipData("", mimeType, firstUri);

            for(int index = 1; index < mSelectedRecents.size(); index++) {
                ClipData.Item item = new ClipData.Item(null, null, null, mSelectedRecents.get(index).mFileUri);
                clipData.addItem(item);
            }
            intent.setClipData(clipData);
        }

        intent.putExtras(conData);
        setResult(RESULT_OK, intent);
        finish();
    }

    /**
     * Switch camera (front <-> back)
     */
    void switchCamera() {
        if (null != mCameraSurfaceHolder) {
            mCamera.stopPreview();
        }
        mCamera.release();

        if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else {
            mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        }

        mCamera = Camera.open(mCameraId);

        setCameraDisplayOrientation();

        try {
            mCamera.setPreviewDisplay(mCameraSurfaceHolder);
        } catch (IOException e) {
        }
        mCamera.startPreview();
    }

    /**
     * Define the camera rotation (preview and recording).
     */
    private void setCameraDisplayOrientation() {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(mCameraId, info);

        int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int previewRotation;
        int imageRotation;

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            imageRotation = previewRotation = (info.orientation + degrees) % 360;
            previewRotation = (360 - previewRotation) % 360;  // compensate the mirror
        } else {  // back-facing
            imageRotation = previewRotation = (info.orientation - degrees + 360) % 360;
        }

        mCameraOrientation = previewRotation;
        mCamera.setDisplayOrientation(previewRotation);

        Camera.Parameters params = mCamera.getParameters();
        params.setRotation(imageRotation);
        mCamera.setParameters(params);
    }


    // SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
        mCamera = Camera.open(mCameraId);

        // the camera initialisation failed
        if (null == mCamera) {
            mCamera = Camera.open((
                    Camera.CameraInfo.CAMERA_FACING_BACK == mCameraId) ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK);
        }

        // cannot start the cam
        if (null == mCamera) {
            manageButtons();
        }
    }

    /**
     * This is called immediately after any structural changes (format or
     * size) have been made to the surface.  You should at this point update
     * the imagery in the surface.  This method is always called at least
     * once, after {@link #surfaceCreated}.
     *
     * @param holder The SurfaceHolder whose surface has changed.
     * @param format The new PixelFormat of the surface.
     * @param width The new width of the surface.
     * @param height The new height of the surface.
     */
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if ((null != mCamera) && !mIsPreviewStarted) {
            try {
                mCameraSurfaceHolder = holder;
                mCamera.setPreviewDisplay(mCameraSurfaceHolder);
                setCameraDisplayOrientation();

                Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
                android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();

                if ((mCameraOrientation == 90) || (mCameraOrientation == 270)) {
                    int tmp = previewSize.width;
                    previewSize.width = previewSize.height;
                    previewSize.height = tmp;
                }

                // check that the aspect ratio is kept
                int sourceRatio = previewSize.height * 100 / previewSize.width;
                int dstRatio = height * 100 / width;

                if (sourceRatio != dstRatio) {
                    int newWidth;
                    int newHeight;

                    newHeight = height;
                    newWidth = (int) (((float) newHeight) * previewSize.width / previewSize.height);

                    if (newWidth > width) {
                        newWidth = width;
                        newHeight = (int) (((float) newWidth) * previewSize.height / previewSize.width);
                    }

                    ViewGroup.LayoutParams layout = mCameraSurfaceView.getLayoutParams();
                    layout.width = newWidth;
                    layout.height = newHeight;
                    mCameraSurfaceView.setLayoutParams(layout);
                }
                mCamera.startPreview();
                mIsPreviewStarted = true;

            } catch (Exception e) {
                if (null != mCamera) {
                    mCamera.stopPreview();
                    mCamera.release();
                    mCamera = null;
                }
            }

            manageButtons();
        }
    }

    /**
     * This is called immediately before a surface is being destroyed. After
     * returning from this call, you should no longer try to access this
     * surface.  If you have a rendering thread that directly accesses
     * the surface, you must ensure that thread is no longer touching the
     * Surface before returning from this function.
     *
     * @param holder The SurfaceHolder whose surface is being destroyed.
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
        mIsPreviewStarted = false;
        stopVideoRecord();
        mCameraSurfaceHolder = null;
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }
}
