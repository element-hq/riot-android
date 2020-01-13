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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaActionSound;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.jetbrains.annotations.NotNull;
import org.matrix.androidsdk.core.ImageUtils;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.ResourceUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.listeners.ImageViewOnTouchListener;
import im.vector.ui.themes.ActivityOtherThemes;
import im.vector.ui.themes.ThemeUtils;
import im.vector.util.PermissionsToolsKt;
import im.vector.util.PreferencesManager;
import im.vector.util.ViewUtilKt;
import im.vector.view.RecentMediaLayout;
import im.vector.view.VideoRecordView;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * VectorMediasPickerActivity is used to take a photo or to send an old one.
 */
public class VectorMediaPickerActivity extends MXCActionBarActivity implements TextureView.SurfaceTextureListener {
    private static final String LOG_TAG = VectorMediaPickerActivity.class.getSimpleName();

    // public keys
    // boolean, display a mask to show the avatar rendering
    public static final String EXTRA_AVATAR_MODE = "EXTRA_AVATAR_MODE";

    // boolean, tell if the video recording is supported
    public static final String EXTRA_VIDEO_RECORDING_MODE = "EXTRA_VIDEO_RECORDING_MODE";

    // internal keys
    private static final String KEY_EXTRA_IS_TAKEN_IMAGE_DISPLAYED = "IS_TAKEN_IMAGE_DISPLAYED";
    private static final String KEY_EXTRA_TAKEN_IMAGE_ORIGIN = "TAKEN_IMAGE_ORIGIN";
    private static final String KEY_EXTRA_TAKEN_IMAGE_GALLERY_URI = "TAKEN_IMAGE_GALLERY_URI";
    private static final String KEY_EXTRA_TAKEN_IMAGE_CAMERA_URL = "TAKEN_IMAGE_CAMERA_URL";
    private static final String KEY_EXTRA_CAMERA_SIDE = "TAKEN_IMAGE_CAMERA_SIDE";
    private static final String KEY_PREFERENCE_CAMERA_IMAGE_NAME = "KEY_PREFERENCE_CAMERA_IMAGE_NAME";
    private static final String KEY_IS_AVATAR_MODE = "KEY_IS_AVATAR_MODE";
    private static final String KEY_EXTRA_TAKEN_VIDEO_URI = "KEY_EXTRA_TAKEN_VIDEO_URI";

    // activity request
    private static final int REQUEST_MEDIAS = 54;

    // common definitions
    private static final int JPEG_QUALITY_MAX = 100;
    private static final String MIME_TYPE_IMAGE_GIF = "image/gif";
    private static final int AVATAR_COMPRESSION_LEVEL = 50;

    private static final int GALLERY_COLUMN_COUNT = 4;
    private static final int GALLERY_ROW_COUNT = 5;
    private static final double SURFACE_VIEW_HEIGHT_RATIO = 1;
    private static final int GALLERY_TABLE_ITEM_SIZE = (GALLERY_COLUMN_COUNT * GALLERY_ROW_COUNT);

    private static final int IMAGE_ORIGIN_CAMERA = 1;
    private static final int IMAGE_ORIGIN_GALLERY = 2;

    private static final boolean UI_SHOW_TAKEN_IMAGE = true;
    private static final boolean UI_SHOW_CAMERA_PREVIEW = false;

    /**
     * define a recent media
     */
    private class MediaStoreMedia {
        // the media file URI
        public Uri mFileUri;

        // the media creation time
        public long mCreationTime;

        // the media thumbnail
        public Bitmap mThumbnail;

        // tell if the media is a video
        public boolean mIsVideo;

        // mime type
        public String mMimeType = "";
    }

    // recent media list
    private final List<MediaStoreMedia> mMediaStoreMediaList = new ArrayList<>();
    private MediaStoreMedia mSelectedGalleryImage;

    // camera object
    private Camera mCamera;
    private int mCameraId;
    private int mCameraOrientation = 0;

    // graphic items
    private View mSwitchCameraImageView;

    // camera preview and gallery selection layout
    private ImageView mTakeImageView;
    private TableLayout mGalleryTableLayout;
    private RelativeLayout mCameraPreviewLayout;
    private TextureView mCameraTextureView;
    private ImageView mCameraTextureMaskView;
    private SurfaceTexture mSurfaceTexture;

    // preview UI items
    private View mPreviewLayout;

    // image preview
    private View mImagePreviewLayout;
    private ImageView mImagePreviewImageView;
    private ImageView mImagePreviewAvatarModeMaskView;
    private ImageViewOnTouchListener imageViewOnTouchListener = new ImageViewOnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return (mIsAvatarMode) ? super.onTouch(v, event) : true;
        }
    };

    // video preview
    private View mVideoPreviewLayout;
    private VideoView mVideoView;
    private ImageView mVideoButtonView;

    // gallery management
    private RelativeLayout mPreviewAndGalleryLayout;
    private int mGalleryImageCount;
    private int mScreenHeight;
    private int mScreenWidth;

    // display a mask to create a good avatar
    private boolean mIsAvatarMode;

    // manage video recording
    private boolean mIsVideoRecordingSupported;

    // lifecycle management variable
    private boolean mIsTakenImageDisplayed;
    private int mTakenImageOrigin;

    private String mShotPicturePath;
    private int mCameraPreviewLayoutHeight;
    private int mPreviewTextureWidth;
    private int mPreviewTextureHeight;

    /**
     * The recent requests are performed in a dedicated thread
     */
    private HandlerThread mHandlerThread;
    private android.os.Handler mFileHandler;

    private VideoRecordView mRecordAnimationView;

    @NotNull

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public ActivityOtherThemes getOtherThemes() {
        return ActivityOtherThemes.NoActionBarFullscreen.INSTANCE;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_vector_media_picker;
    }

    @Override
    public void initUiAndData() {
        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        if (CommonActivityUtils.isGoingToSplash(this)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen");
            return;
        }

        Intent intent = getIntent();
        mIsAvatarMode = intent.getBooleanExtra(EXTRA_AVATAR_MODE, false);
        mIsVideoRecordingSupported = intent.getBooleanExtra(EXTRA_VIDEO_RECORDING_MODE, false);

        mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

        // camera preview
        mSwitchCameraImageView = findViewById(R.id.media_picker_switch_camera);
        mCameraTextureView = findViewById(R.id.media_picker_texture_view);
        mCameraTextureView.setSurfaceTextureListener(this);
        mCameraTextureMaskView = findViewById(R.id.media_picker_texture_mask_view);
        mRecordAnimationView = findViewById(R.id.media_picker_record_animation);

        // preview
        mPreviewLayout = findViewById(R.id.media_picker_preview_layout);

        // image preview
        mImagePreviewLayout = findViewById(R.id.media_picker_preview_image_layout);
        mImagePreviewImageView = findViewById(R.id.media_picker_preview_image_view);
        mImagePreviewAvatarModeMaskView = findViewById(R.id.media_picker_preview_avatar_mode_mask);
        mImagePreviewImageView.setOnTouchListener(imageViewOnTouchListener);

        // video preview
        mVideoPreviewLayout = findViewById(R.id.media_picker_preview_video_layout);
        mVideoView = findViewById(R.id.media_picker_preview_video_view);
        mVideoButtonView = findViewById(R.id.media_picker_preview_video_button);

        mTakeImageView = findViewById(R.id.media_picker_camera_button);
        mGalleryTableLayout = findViewById(R.id.gallery_table_layout);

        // hide switch camera view if there is only one camera
        mSwitchCameraImageView.setVisibility((Camera.getNumberOfCameras() > 1) ? View.VISIBLE : View.GONE);

        // click action
        mSwitchCameraImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSwitchCamera();
            }
        });

        mTakeImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickTakeImage();
            }
        });

        mTakeImageView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mIsVideoRecordingSupported
                        && PermissionsToolsKt.checkPermissions(PermissionsToolsKt.PERMISSIONS_FOR_VIDEO_RECORDING,
                        VectorMediaPickerActivity.this,
                        PermissionsToolsKt.PERMISSION_REQUEST_CODE)) {
                    mRecordAnimationView.startAnimation();
                    startVideoRecord();
                }

                return mIsVideoRecordingSupported;
            }
        });

        mTakeImageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mIsRecording
                        && ((event.getAction() == MotionEvent.ACTION_UP) || (event.getAction() == MotionEvent.ACTION_CANCEL))) {
                    stopVideoRecord();
                    startVideoPreviewVideo(null);
                    return true;
                }

                return false;
            }
        });

        findViewById(R.id.media_picker_attach_text_view).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mVideoUri) {
                    sendVideoFile();
                } else {
                    attachImageFrom(mTakenImageOrigin);
                }
            }
        });

        findViewById(R.id.media_picker_redo_text_view).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelTakeImage();
            }
        });

        initCameraLayout();

        // setup separate thread for image gallery update
        mHandlerThread = new HandlerThread("VectorMediasPickerActivityThread");
        mHandlerThread.start();
        mFileHandler = new android.os.Handler(mHandlerThread.getLooper());

        if (isFirstCreation()) {
            // default UI: if a taken image is not in preview, then display: live camera preview + "take picture"/switch/exit buttons
            updateUiConfiguration(UI_SHOW_CAMERA_PREVIEW, IMAGE_ORIGIN_CAMERA);
        } else {
            restoreInstanceState(getSavedInstanceState());
        }

        // Force screen orientation be managed by the sensor in case user's setting turned off
        // sensor-based rotation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        //if (aRequestCode == CommonActivityUtils.PERMISSIONS_FOR_VIDEO_RECORDING) {
        // do nothing
        // the user has to long press again on the focus button
        //}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (null != mHandlerThread) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // cancel the camera use to avoid locking it
        if (null != mCamera) {
            mCamera.stopPreview();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // update gallery content
        refreshRecentMediaList();

        // restart the preview
        startCameraPreview();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // save camera UI configuration
        outState.putBoolean(KEY_EXTRA_IS_TAKEN_IMAGE_DISPLAYED, mIsTakenImageDisplayed);
        outState.putBoolean(KEY_IS_AVATAR_MODE, mIsAvatarMode);
        outState.putInt(KEY_EXTRA_TAKEN_IMAGE_ORIGIN, mTakenImageOrigin);
        outState.putInt(KEY_EXTRA_CAMERA_SIDE, mCameraId);

        // save image preview that may be currently displayed:
        // -camera flow
        outState.putString(KEY_EXTRA_TAKEN_IMAGE_CAMERA_URL, mShotPicturePath);
        // -gallery flow
        Uri uriImage = (Uri) mImagePreviewImageView.getTag();
        outState.putParcelable(KEY_EXTRA_TAKEN_IMAGE_GALLERY_URI, uriImage);

        if (null != mVideoUri) {
            outState.putParcelable(KEY_EXTRA_TAKEN_VIDEO_URI, mVideoUri);
        }
    }

    /**
     * Restores the saved instance.
     *
     * @param savedInstanceState the savedInstanceState
     */
    private void restoreInstanceState(@NonNull Bundle savedInstanceState) {
        mIsAvatarMode = savedInstanceState.getBoolean(KEY_IS_AVATAR_MODE);
        mIsTakenImageDisplayed = savedInstanceState.getBoolean(KEY_EXTRA_IS_TAKEN_IMAGE_DISPLAYED);
        mShotPicturePath = savedInstanceState.getString(KEY_EXTRA_TAKEN_IMAGE_CAMERA_URL);
        mTakenImageOrigin = savedInstanceState.getInt(KEY_EXTRA_TAKEN_IMAGE_ORIGIN);

        // restore gallery image preview (the image can be saved from the preview even after rotation)
        Uri uriImage = savedInstanceState.getParcelable(KEY_EXTRA_TAKEN_IMAGE_GALLERY_URI);
        mImagePreviewImageView.setTag(uriImage);

        mVideoUri = savedInstanceState.getParcelable(KEY_EXTRA_TAKEN_VIDEO_URI);

        // display a preview image?
        if (mIsTakenImageDisplayed) {
            Bitmap savedBitmap = VectorApp.getSavedPickerImagePreview();
            if ((null != savedBitmap) && !mIsAvatarMode) {
                // image preview from camera only
                mImagePreviewImageView.setImageBitmap(savedBitmap);
            } else {
                // image preview from gallery or camera (mShotPicturePath)
                displayImagePreview(savedBitmap, mShotPicturePath, uriImage, mTakenImageOrigin);
            }
        }

        // restore UI display
        updateUiConfiguration(mIsTakenImageDisplayed, mTakenImageOrigin);

        // general data to be restored
        mCameraId = savedInstanceState.getInt(KEY_EXTRA_CAMERA_SIDE);

        if (null != mVideoUri) {
            startVideoPreviewVideo(null);
        }
    }

    /**
     * Result handler associated to {@link #openFileExplorer()} request.
     * This method returns the selected image to the calling activity.
     *
     * @param requestCode request ID
     * @param resultCode  operation status
     * @param data        data passed from the called activity
     */
    @SuppressLint("NewApi")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_MEDIAS) {
                // provide the Uri
                Intent intent = new Intent();
                intent.setData(data.getData());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    intent.setClipData(data.getClipData());
                }
                // clean footprint in App
                VectorApp.setSavedCameraImagePreview(null);

                //intent.putExtras(conData);
                setResult(RESULT_OK, intent);
                finish();
            }
        }
    }

    @SuppressLint("NewApi")
    private void openFileExplorer() {
        try {
            Intent fileIntent = new Intent(Intent.ACTION_PICK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
            }
            if (mIsVideoRecordingSupported) {
                fileIntent.setType(ResourceUtils.MIME_TYPE_ALL_CONTENT);
            } else {
                fileIntent.setType(ResourceUtils.MIME_TYPE_IMAGE_ALL);
            }
            startActivityForResult(fileIntent, REQUEST_MEDIAS);
        } catch (Exception e) {
            Toast.makeText(VectorMediaPickerActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    //==============================================================================================================
    // Camera management
    //==============================================================================================================

    /**
     * Switch camera (front <-> back)
     */
    private void onSwitchCamera() {
        // can only switch if the device has more than two camera
        if (Camera.getNumberOfCameras() >= 2) {
            // reported by GA
            if (null != mCamera) {
                // stop camera
                if (null != mCameraTextureView) {
                    mCamera.stopPreview();
                }
                mCamera.release();
            }
            mCamera = null;

            if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
            } else {
                mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
            }

            try {
                mCamera = Camera.open(mCameraId);

                // set the full quality picture, rotation angle
                initCameraSettings();

                try {
                    mCamera.setPreviewTexture(mSurfaceTexture);
                } catch (IOException e) {
                    Log.e(LOG_TAG, "## onSwitchCamera(): setPreviewTexture EXCEPTION Msg=" + e.getMessage(), e);
                }

                startCameraPreview();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## onSwitchCamera(): cannot init the other camera " + e.getMessage(), e);
                // assume that only one camera can be used.
                mSwitchCameraImageView.setVisibility(View.GONE);
                onSwitchCamera();
            }
        }
    }

    /**
     * Define the camera rotation (preview and recording).
     */
    private void initCameraSettings() {
        try {
            android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(mCameraId, info);

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break; // portrait
                case Surface.ROTATION_90:
                    degrees = 90;
                    break; // landscape
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break; // landscape
            }

            int previewRotation;
            int imageRotation;

            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                imageRotation = previewRotation = (info.orientation + degrees) % 360;
                // compensate for the mirror effect
                previewRotation = (360 - previewRotation) % 360;
            } else {
                // back-facing
                imageRotation = previewRotation = (info.orientation - degrees + 360) % 360;
            }

            mCameraOrientation = previewRotation;
            mCamera.setDisplayOrientation(previewRotation);

            Camera.Parameters params = mCamera.getParameters();

            // apply the rotation
            params.setRotation(imageRotation);

            if (!mIsVideoMode) {
                // set the best quality
                List<Camera.Size> supportedSizes = params.getSupportedPictureSizes();
                if (supportedSizes.size() > 0) {

                    // search the highest image quality
                    // they are not always sorted in the same order (sometimes it is asc sort ..)
                    Camera.Size maxSizePicture = supportedSizes.get(0);
                    long mult = maxSizePicture.width * maxSizePicture.height;

                    for (int i = 1; i < supportedSizes.size(); i++) {
                        Camera.Size curSizePicture = supportedSizes.get(i);
                        long curMult = curSizePicture.width * curSizePicture.height;

                        if (curMult > mult) {
                            mult = curMult;
                            maxSizePicture = curSizePicture;
                        }
                    }

                    // and use it.
                    params.setPictureSize(maxSizePicture.width, maxSizePicture.height);
                }

                try {
                    mCamera.setParameters(params);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## initCameraSettings(): set size fails EXCEPTION Msg=" + e.getMessage(), e);
                }
            }

            // set the preview size to have the same aspect ratio than the picture size
            List<Camera.Size> supportedPreviewSizes = params.getSupportedPreviewSizes();

            if (supportedPreviewSizes.size() > 0) {
                int cameraAR;

                if (mIsVideoMode) {
                    mCamcorderProfile = getCamcorderProfile(mCameraId);
                    cameraAR = mCamcorderProfile.videoFrameWidth * 100 / mCamcorderProfile.videoFrameHeight;

                } else {
                    Camera.Size picturesSize = params.getPictureSize();
                    cameraAR = picturesSize.width * 100 / picturesSize.height;
                }

                Camera.Size bestPreviewSize = null;
                int resolution = 0;

                for (Camera.Size previewSize : supportedPreviewSizes) {
                    int previewAR = previewSize.width * 100 / previewSize.height;

                    if (previewAR == cameraAR) {
                        int mult = previewSize.height * previewSize.width;
                        if (mult > resolution) {
                            bestPreviewSize = previewSize;
                            resolution = mult;
                        }
                    }
                }

                if (null != bestPreviewSize) {
                    params.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height);

                    try {
                        mCamera.setParameters(params);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## initCameraSettings(): set preview size fails EXCEPTION Msg=" + e.getMessage(), e);
                    }
                }
            }

            if (!mIsVideoMode) {
                // set jpeg quality
                try {
                    params.setPictureFormat(ImageFormat.JPEG);
                    params.setJpegQuality(JPEG_QUALITY_MAX);
                    mCamera.setParameters(params);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## initCameraSettings(): set jpeg quality fails EXCEPTION Msg=" + e.getMessage(), e);
                }
            }

            resizeCameraPreviewTexture();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## ## initCameraSettings(): failed " + e.getMessage(), e);
        }
    }

    /**
     * Start auto-focus of the camera, using the best available mode
     */
    private void startAutoFocus() {
        Log.d(LOG_TAG, "## startAutoFocus");

        try {
            String focusMode = null;

            Camera.Parameters params = mCamera.getParameters();

            if (mIsVideoMode) {
                // set auto focus for video
                if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
                }
            } else {
                // set auto focus for picture
                if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
                }
            }

            if (focusMode == null
                    && params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                focusMode = Camera.Parameters.FOCUS_MODE_AUTO;
            }

            if (focusMode != null && !focusMode.equals(params.getFocusMode())) {
                try {
                    params.setFocusMode(focusMode);
                    mCamera.setParameters(params);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## startAutoFocus(): set auto focus fails EXCEPTION Msg=" + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## ## startAutoFocus(): failed " + e.getMessage(), e);
        }
    }

    /**
     * Resize the camera preview texture from the camera preview size.
     * The aspect ratio is kept.
     */
    private void resizeCameraPreviewTexture() {
        try {
            Camera.Size previewSize = mCamera.getParameters().getPreviewSize();

            //  Valid values are 0, 90, 180, and 270 (0 = landscape)
            if ((mCameraOrientation == 90) || (mCameraOrientation == 270)) {
                int tmp = previewSize.width;
                previewSize.width = previewSize.height;
                previewSize.height = tmp;
            }

            // check that the aspect ratio is kept
            int sourceRatio = previewSize.height * 100 / previewSize.width;
            int dstRatio = mPreviewTextureHeight * 100 / mPreviewTextureWidth;

            // the camera preview size must fit the size provided by the surface texture
            if (sourceRatio != dstRatio) {
                int newWidth;
                int newHeight;

                // don't update the mCameraPreviewLayout frame when recording the video
                // else medias_picker_camera_button will move and the video recording would stop
                newHeight = mIsVideoMode ? mCameraPreviewLayoutHeight : mPreviewTextureHeight;
                newWidth = (int) (((float) newHeight) * previewSize.width / previewSize.height);

                if (newWidth > mPreviewTextureWidth) {
                    newWidth = mPreviewTextureWidth;
                    newHeight = (int) (((float) newWidth) * previewSize.height / previewSize.width);

                    // max value
                    if (newHeight > (int) (mScreenHeight * SURFACE_VIEW_HEIGHT_RATIO)) {
                        newHeight = (int) (mScreenHeight * SURFACE_VIEW_HEIGHT_RATIO);
                        newWidth = (int) (((float) newHeight) * previewSize.width / previewSize.height);
                    }
                }

                // apply the size provided by the texture to the texture layout
                ViewGroup.LayoutParams layout = mCameraTextureView.getLayoutParams();
                layout.width = newWidth;
                layout.height = newHeight;
                mCameraTextureView.setLayoutParams(layout);

                if (mIsAvatarMode) {
                    mCameraTextureMaskView.setVisibility(View.VISIBLE);
                    final int fWidth = newWidth;
                    final int fHeight = newHeight;

                    mCameraTextureMaskView.post(new Runnable() {
                        @Override
                        public void run() {
                            drawCircleMask(mCameraTextureMaskView, fWidth, fHeight);
                        }
                    });
                } else {
                    mCameraTextureMaskView.setVisibility(View.GONE);
                }

                // don't update the mCameraPreviewLayout frame when recording the video
                // else medias_picker_camera_button will move and the video recording would stop
                if ((layout.height != mCameraPreviewLayoutHeight) && !mIsVideoMode) {
                    mCameraPreviewLayoutHeight = layout.height;
                    // set the height of the relative layout containing the texture view
                    if (null != mCameraPreviewLayout) {
                        RelativeLayout.LayoutParams previewLayoutParams = (RelativeLayout.LayoutParams) mCameraPreviewLayout.getLayoutParams();
                        previewLayoutParams.height = mCameraPreviewLayoutHeight;
                        mCameraPreviewLayout.setLayoutParams(previewLayoutParams);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## ## resizeCameraPreviewTexture(): failed " + e.getMessage(), e);
        }
    }

    //==============================================================================================================
    // Layout management
    //==============================================================================================================

    /**
     * Init the camera layout to make the surface texture + the gallery layout, both
     * enough large to enable scrolling.
     */
    private void initCameraLayout() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenHeight = metrics.heightPixels;
        mScreenWidth = metrics.widthPixels;

        mCameraPreviewLayoutHeight = (int) (mScreenHeight * SURFACE_VIEW_HEIGHT_RATIO);

        // set the height of the relative layout containing the texture view
        mCameraPreviewLayout = findViewById(R.id.medias_picker_camera_preview_layout);
        ViewGroup.LayoutParams previewLayoutParams = mCameraPreviewLayout.getLayoutParams();
        previewLayoutParams.height = mCameraPreviewLayoutHeight;
        mCameraPreviewLayout.setLayoutParams(previewLayoutParams);
        mPreviewAndGalleryLayout = findViewById(R.id.media_picker_preview_gallery_layout);
    }

    /**
     * Exit activity handler.
     *
     * @param aView view
     */
    public void onExitButton(@SuppressWarnings("UnusedParameters") View aView) {
        finish();
    }

    /**
     * Display the image preview.
     *
     * @param bitmap           the bitmap.
     * @param aCameraImageUrl  image from camera
     * @param aGalleryImageUri image ref as an Uri
     * @param aOrigin          CAMERA or GALLERY
     */
    private void displayImagePreview(final Bitmap bitmap, final String aCameraImageUrl, final Uri aGalleryImageUri, final int aOrigin) {
        setWaitingView(findViewById(R.id.media_preview_progress_bar_layout));
        showWaitingView();
        mTakeImageView.setEnabled(false);

        Bitmap newBitmap = bitmap;
        Uri defaultUri = null;

        if (null == newBitmap) {
            if (IMAGE_ORIGIN_CAMERA == aOrigin) {
                newBitmap = null;

                // for the back camera, the texture is used to create a thumbnail.
                // It saves a lot of memory and reduces the processing time.
                // For the front camera, some devices add a flip effect.
                // so it is safer to create a thumbnail from the high res image.
                if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    newBitmap = mCameraTextureView.getBitmap();
                }

                if (null == newBitmap) {
                    newBitmap = createPhotoThumbnail(aCameraImageUrl);
                }
                defaultUri = Uri.fromFile(new File(aCameraImageUrl));
            } else {
                // in gallery
                defaultUri = aGalleryImageUri;
            }
        }

        // save bitmap to speed up UI restore (life cycle)
        VectorApp.setSavedCameraImagePreview(newBitmap);

        mImagePreviewAvatarModeMaskView.setVisibility(View.GONE);

        if (!mIsAvatarMode) {
            mImagePreviewImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            // update the UI part
            if (null != newBitmap) {// from camera
                mImagePreviewImageView.setImageBitmap(newBitmap);
            } else {
                if (null != defaultUri) {
                    mImagePreviewImageView.setImageURI(defaultUri);
                }
            }
        } else {
            mImagePreviewImageView.setScaleType(ImageView.ScaleType.MATRIX);
            // not bitmap but
            if ((null == newBitmap) && (null != defaultUri)) {
                try {
                    ResourceUtils.Resource resource = ResourceUtils.openResource(this, defaultUri, null);

                    if ((null != resource) && (null != resource.mContentStream)) {
                        int rotationAngle = ImageUtils.getRotationAngleForBitmap(VectorMediaPickerActivity.this, defaultUri);
                        newBitmap = createPhotoThumbnail(resource.mContentStream, rotationAngle);
                        resource.mContentStream.close();
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "fails to retrieve the bitmap from uri " + e.getMessage(), e);
                }
            }

            // update the UI part
            if (null != newBitmap) {// from camera
                mImagePreviewImageView.setImageBitmap(newBitmap);

                int screenHeight = getWindow().getDecorView().getHeight();
                int screenWidth = getWindow().getDecorView().getWidth();

                if ((0 == screenHeight) || (0 == screenWidth)) {
                    mImagePreviewImageView.post(new Runnable() {
                        @Override
                        public void run() {
                            displayImagePreview(bitmap, aCameraImageUrl, aGalleryImageUri, aOrigin);
                        }
                    });

                    return;
                } else {
                    mImagePreviewImageView.post(new Runnable() {
                        @Override
                        public void run() {
                            Matrix matrix = mImagePreviewImageView.getMatrix();
                            float widthMatrix = (mImagePreviewImageView.getWidth() - mImagePreviewImageView.getDrawable().getIntrinsicWidth()) / 2;
                            float heightMatrix = (mImagePreviewImageView.getHeight() - mImagePreviewImageView.getDrawable().getIntrinsicHeight()) / 2;

                            Matrix modifiableMatrix = new Matrix(matrix);
                            modifiableMatrix.postTranslate(widthMatrix, heightMatrix);
                            imageViewOnTouchListener.setStartMatrix(modifiableMatrix);
                            mImagePreviewImageView.setImageMatrix(modifiableMatrix);
                        }
                    });
                }
                mImagePreviewAvatarModeMaskView.setVisibility(View.VISIBLE);
                mImagePreviewAvatarModeMaskView.post(new Runnable() {
                    @Override
                    public void run() {
                        drawCircleMask(mImagePreviewAvatarModeMaskView,
                                mImagePreviewAvatarModeMaskView.getWidth(),
                                mImagePreviewAvatarModeMaskView.getHeight());
                    }
                });
            }
        }

        mTakeImageView.setEnabled(true);
        updateUiConfiguration(UI_SHOW_TAKEN_IMAGE, aOrigin);
        hideWaitingView();
    }

    /**
     * Update the UI according to camera action. Two UIs are displayed:
     * the camera real time preview (default configuration) or the taken picture.
     * (the taken picture comes from the camera or from the gallery)
     * <p>
     * When the taken image is displayed, only two buttons are displayed: "attach"
     * the current image or "re take"(cancel) another image with the camera.
     * We also have to distinguish the origin of the taken image: from the camera
     * or from the gallery.
     *
     * @param aIsTakenImageDisplayed true to display the taken image, false to show the camera preview
     * @param aImageOrigin           IMAGE_ORIGIN_CAMERA or IMAGE_ORIGIN_GALLERY
     */
    private void updateUiConfiguration(boolean aIsTakenImageDisplayed, int aImageOrigin) {
        // save current configuration for lifecyle management
        mIsTakenImageDisplayed = aIsTakenImageDisplayed;
        mTakenImageOrigin = aImageOrigin;

        if (!aIsTakenImageDisplayed) {
            // clear the selected image from the gallery (if any)
            mSelectedGalleryImage = null;
        }

        if (aIsTakenImageDisplayed) {
            mPreviewLayout.setVisibility(View.VISIBLE);
            mPreviewAndGalleryLayout.setVisibility(View.GONE);
        } else {
            // the default UI: hide gallery preview, show the surface view
            mPreviewAndGalleryLayout.setVisibility(View.VISIBLE);
            mPreviewLayout.setVisibility(View.GONE);
        }
    }

    /**
     * Start the camera preview
     */
    private void startCameraPreview() {
        if (mCamera != null) {
            try {
                mCamera.startPreview();
            } catch (Exception ex) {
                Log.w(LOG_TAG, "## startCameraPreview(): Exception Msg=" + ex.getMessage());
            }

            startAutoFocus();
        }
    }

    //==============================================================================================================
    // image management
    //==============================================================================================================

    /**
     * Take a photo
     */
    private void takePhoto() {
        Log.d(LOG_TAG, "## takePhoto");

        try {
            mCamera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    Log.d(LOG_TAG, "## onPictureTaken(): success");

                    ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                    File dstFile;
                    String fileName = getSavedImageName(VectorMediaPickerActivity.this);

                    // remove any previously saved image
                    if (!TextUtils.isEmpty(fileName)) {
                        dstFile = new File(getCacheDir().getAbsolutePath(), fileName);
                        if (dstFile.exists()) {
                            dstFile.delete();
                        }
                    }

                    // get new name
                    fileName = buildNewImageName(VectorMediaPickerActivity.this);
                    dstFile = new File(getCacheDir().getAbsolutePath(), fileName);

                    // Copy source file to destination
                    FileOutputStream outputStream = null;

                    try {
                        dstFile.createNewFile();

                        outputStream = new FileOutputStream(dstFile);

                        byte[] buffer = new byte[1024 * 10];
                        int len;
                        while ((len = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, len);
                        }

                        mShotPicturePath = dstFile.getAbsolutePath();
                        displayImagePreview(null, mShotPicturePath, null, IMAGE_ORIGIN_CAMERA);

                        // force to stop preview:
                        // some devices do not stop preview after the picture was taken (ie. G6 edge)
                        mCamera.stopPreview();

                        Log.d(LOG_TAG, "onPictureTaken processed");

                    } catch (Exception e) {
                        Toast.makeText(VectorMediaPickerActivity.this, "Exception onPictureTaken(): " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    } finally {

                        // Close resources
                        try {
                            inputStream.close();

                            if (outputStream != null) {
                                outputStream.close();
                            }

                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## onPictureTaken(): EXCEPTION Msg=" + e.getMessage(), e);
                        }
                    }
                }
            });

        } catch (Exception e) {
            Log.e(LOG_TAG, "## takePicture(): EXCEPTION Msg=" + e.getMessage(), e);
        }
    }

    /**
     * Take a picture of the current preview
     */
    private void onClickTakeImage() {
        Log.d(LOG_TAG, "onClickTakeImage");

        if (null != mCamera) {
            try {
                List<String> supportedFocusModes = null;

                if (null != mCamera.getParameters()) {
                    supportedFocusModes = mCamera.getParameters().getSupportedFocusModes();
                }

                Log.d(LOG_TAG, "onClickTakeImage : supported focus modes " + supportedFocusModes);

                if ((null != supportedFocusModes) && (supportedFocusModes.indexOf(Camera.Parameters.FOCUS_MODE_AUTO) >= 0)) {
                    Log.d(LOG_TAG, "onClickTakeImage : autofocus starts");

                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
                        public void onAutoFocus(boolean success, Camera camera) {
                            if (!success) {
                                Log.e(LOG_TAG, "## autoFocus(): fails");
                            } else {
                                Log.d(LOG_TAG, "## autoFocus(): succeeds");
                            }

                            playShutterSound();

                            // take a photo event if the autofocus fails
                            takePhoto();
                        }
                    });
                } else {
                    Log.d(LOG_TAG, "onClickTakeImage : no autofocus : take photo");
                    playShutterSound();
                    takePhoto();
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## autoFocus(): EXCEPTION Msg=" + e.getMessage(), e);

                // take a photo even if the autofocus fails
                playShutterSound();
                takePhoto();
            }
        }
    }

    /**
     * Create an unique image name.
     *
     * @return the unique file name
     */
    private static String buildNewImageName(Context context) {
        String nameRetValue = "VectorImage_" + new SimpleDateFormat("yyyy-MM-dd_hhmmss").format(new Date()) + ".jpg";

        // save new name in preference
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(KEY_PREFERENCE_CAMERA_IMAGE_NAME, nameRetValue)
                .apply();

        return nameRetValue;
    }

    /**
     * Retrieves the saved image name.
     *
     * @param context the context
     * @return the saved image name.
     */
    private String getSavedImageName(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString(KEY_PREFERENCE_CAMERA_IMAGE_NAME, null);
    }

    /**
     * Create a thumbnail from an image stream with a rotation angle.
     *
     * @param imageStream   the image stream
     * @param rotationAngle the rotation angle
     * @return the thumbnail
     */
    private Bitmap createPhotoThumbnail(InputStream imageStream, int rotationAngle) {
        Bitmap bitmapRetValue = null;
        final int MAX_SIZE = 1024, SAMPLE_SIZE = 0, QUALITY = 100;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.outWidth = -1;
        options.outHeight = -1;

        try {
            // create a thumbnail
            InputStream stream = ImageUtils.resizeImage(imageStream, MAX_SIZE, SAMPLE_SIZE, QUALITY);
            imageStream.close();

            bitmapRetValue = BitmapFactory.decodeStream(stream, null, options);

            if (0 != rotationAngle) {
                // apply a rotation
                android.graphics.Matrix bitmapMatrix = new android.graphics.Matrix();
                bitmapMatrix.postRotate(rotationAngle);
                bitmapRetValue = Bitmap.createBitmap(bitmapRetValue, 0, 0, bitmapRetValue.getWidth(), bitmapRetValue.getHeight(), bitmapMatrix, false);
            }

            System.gc();

        } catch (OutOfMemoryError e) {
            Log.e(LOG_TAG, "## createPhotoThumbnail : out of memory", e);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## createPhotoThumbnail() Exception Msg=" + e.getMessage(), e);
        }

        return bitmapRetValue;
    }

    /**
     * Create a thumbnail bitmap from an image URL if there is some exif metadata which implies to rotate
     * the image. This method is used to process the image taken by the from the camera.
     *
     * @param aImageUrl the image url
     * @return a thumbnail if the exif metadata implies to rotate the image.
     */
    private Bitmap createPhotoThumbnail(final String aImageUrl) {
        Bitmap bitmapRetValue = null;

        // sanity check
        if (null != aImageUrl) {
            Uri imageUri = Uri.fromFile(new File(aImageUrl));
            int rotationAngle = ImageUtils.getRotationAngleForBitmap(VectorMediaPickerActivity.this, imageUri);

            try {
                final String filename = imageUri.getPath();

                FileInputStream imageStream = new FileInputStream(new File(filename));
                bitmapRetValue = createPhotoThumbnail(imageStream, rotationAngle);
                imageStream.close();

                System.gc();

            } catch (OutOfMemoryError e) {
                Log.e(LOG_TAG, "## createPhotoThumbnail : out of memory", e);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## createPhotoThumbnail() Exception Msg=" + e.getMessage(), e);
            }

        }

        return bitmapRetValue;
    }

    /**
     * Cancel the current image preview, and setup the UI to
     * start a new image capture.
     */
    private void cancelTakeImage() {
        mShotPicturePath = null;
        mSelectedGalleryImage = null;
        VectorApp.setSavedCameraImagePreview(null);

        startCameraPreview();
        // reset UI ot default: "take picture" button screen
        updateUiConfiguration(UI_SHOW_CAMERA_PREVIEW, IMAGE_ORIGIN_CAMERA);
    }

    /**
     * "attach image" dispatcher.
     *
     * @param aImageOrigin camera, otherwise gallery
     */
    private void attachImageFrom(int aImageOrigin) {
        if (IMAGE_ORIGIN_CAMERA == aImageOrigin) {
            attachImageFromCamera();
        } else if (IMAGE_ORIGIN_GALLERY == aImageOrigin) {
            attachImageFromGallery();
        } else {
            Log.w(LOG_TAG, "## attachImageFrom(): unknown image origin");
        }
    }

    /**
     * Returns the thumbnail path of shot image.
     *
     * @param picturePath the image path
     * @return the thumbnail image path.
     */
    private static String getThumbnailPath(String picturePath) {
        if (!TextUtils.isEmpty(picturePath) && picturePath.endsWith(".jpg")) {
            return picturePath.replace(".jpg", "_thumb.jpg");
        }

        return null;
    }

    /**
     * Return the taken image from the camera to the calling activity.
     * This method returns to the calling activity.
     */
    private void attachImageFromCamera() {
        try {
            // sanity check
            if (null != mShotPicturePath) {
                Uri uri;
                if (!mIsAvatarMode) {
                    uri = Uri.fromFile(new File(mShotPicturePath));
                } else {
                    uri = getPreviewImageFileUri();
                }

                try {
                    Bitmap previewBitmap = VectorApp.getSavedPickerImagePreview();
                    String thumbnailPath = getThumbnailPath(mShotPicturePath);

                    int rotationAngle = ImageUtils.getRotationAngleForBitmap(VectorMediaPickerActivity.this, uri);

                    // detect exif rotation
                    if (0 != rotationAngle) {
                        android.graphics.Matrix bitmapMatrix = new android.graphics.Matrix();
                        bitmapMatrix.postRotate(360 - rotationAngle);
                        previewBitmap = Bitmap.createBitmap(previewBitmap, 0, 0, previewBitmap.getWidth(), previewBitmap.getHeight(), bitmapMatrix, false);
                    }

                    File file = new File(thumbnailPath);
                    FileOutputStream outStream = new FileOutputStream(file);
                    previewBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outStream);
                    outStream.flush();
                    outStream.close();

                } catch (Exception e) {
                    Log.e(LOG_TAG, "attachImageFromCamera fails to create thumbnail file " + e.getMessage(), e);
                }

                // provide the Uri
                Bundle conData = new Bundle();
                Intent intent = new Intent();
                intent.setData(uri);
                intent.putExtras(conData);
                setResult(RESULT_OK, intent);
            }
        } catch (Exception e) {
            setResult(RESULT_CANCELED, null);

        } finally {
            // clean footprint in App
            VectorApp.setSavedCameraImagePreview(null);
            finish();
        }
    }

    /**
     * Play the camera shutter sound
     */
    private void playShutterSound() {
        if (PreferencesManager.useShutterSound(this)) {
            MediaActionSound sound = new MediaActionSound();
            sound.play(MediaActionSound.SHUTTER_CLICK);
        }
    }

    /**
     * Compute the avatar mask bitmap and apply it to the provided ImageView
     *
     * @param maskView the mask view
     * @param width    the image width to hide
     * @param height   the image height to hide
     */
    private void drawCircleMask(final ImageView maskView, final int width, final int height) {
        // remove any background
        maskView.setBackgroundResource(0);

        // create a bitmap with a transparent hole
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        canvas.drawColor(ThemeUtils.INSTANCE.getColor(this, android.R.attr.colorBackground));

        Paint eraser = new Paint(Paint.ANTI_ALIAS_FLAG);
        eraser.setStyle(Paint.Style.FILL);
        // require to make a transparent hole
        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT));
        eraser.setColor(Color.TRANSPARENT);

        canvas.drawCircle(width / 2, height / 2, Math.min(width / 2, height / 2), eraser);
        canvas.drawBitmap(bitmap, 0, 0, null);

        maskView.setImageBitmap(bitmap);
    }

    //==============================================================================================================
    // TextureView.SurfaceTextureListener
    //==============================================================================================================

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        try {
            mCamera = Camera.open(mCameraId);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Cannot open the camera " + mCameraId + " " + e.getMessage(), e);
        }

        // fall back: the camera initialisation failed
        if (null == mCamera) {
            // assume that only one camera can be used.
            mSwitchCameraImageView.setVisibility(View.GONE);
            try {
                mCamera = Camera.open((Camera.CameraInfo.CAMERA_FACING_BACK == mCameraId) ?
                        Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Cannot open the camera " + mCameraId + " " + e.getMessage(), e);
            }
        }

        // cannot start the cam
        if (null == mCamera) {
            Log.w(LOG_TAG, "## onSurfaceTextureAvailable() camera creation failed");
            return;
        }

        try {
            mSurfaceTexture = surface;
            mCamera.setPreviewTexture(surface);

            mPreviewTextureWidth = width;
            mPreviewTextureHeight = height;

            initCameraSettings();

            startCameraPreview();
        } catch (Exception e) {
            if (null != mCamera) {
                try {
                    mCamera.stopPreview();
                    mCamera.release();
                } catch (Exception e2) {
                    Log.e(LOG_TAG, "## onSurfaceTextureAvailable() : " + e2.getMessage(), e2);
                }
                mCamera = null;
            }
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(LOG_TAG, "## onSurfaceTextureSizeChanged(): width=" + width + " height=" + height);

        if (null != surface) {
            try {
                // clear the texture to avoid staled area
                // when switching the camera, some texture areas are not refreshed/cleared
                // so, paint it in black
                EGL10 egl = (EGL10) EGLContext.getEGL();
                EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
                egl.eglInitialize(display, null);

                int[] attribList = {
                        EGL10.EGL_RED_SIZE, 8,
                        EGL10.EGL_GREEN_SIZE, 8,
                        EGL10.EGL_BLUE_SIZE, 8,
                        EGL10.EGL_ALPHA_SIZE, 8,
                        EGL10.EGL_RENDERABLE_TYPE, EGL10.EGL_WINDOW_BIT,
                        EGL10.EGL_NONE, 0,
                        EGL10.EGL_NONE
                };
                EGLConfig[] configs = new EGLConfig[1];
                int[] numConfigs = new int[1];
                egl.eglChooseConfig(display, attribList, configs, configs.length, numConfigs);
                EGLConfig config = configs[0];
                EGLContext context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, new int[]{
                        12440, 2,
                        EGL10.EGL_NONE
                });
                EGLSurface eglSurface = egl.eglCreateWindowSurface(display, config, surface,
                        new int[]{
                                EGL10.EGL_NONE
                        });

                egl.eglMakeCurrent(display, eglSurface, eglSurface, context);
                GLES20.glClearColor(0, 0, 0, 1);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                egl.eglSwapBuffers(display, eglSurface);
                egl.eglDestroySurface(display, eglSurface);
                egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                        EGL10.EGL_NO_CONTEXT);
                egl.eglDestroyContext(display, context);
                egl.eglTerminate(display);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## onSurfaceTextureSizeChanged() failed " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (null != mCamera) {
            mCamera.stopPreview();
            mCamera.release();
        }
        mSurfaceTexture = null;
        mCamera = null;
        return true;
    }

    //==============================================================================================================
    // Video recording
    //==============================================================================================================

    // true when the activity is in video recording mode
    private boolean mIsVideoMode = false;

    // the video recording profile
    private CamcorderProfile mCamcorderProfile = null;

    // the recording video file
    private Uri mVideoUri = null;

    // true when a recording is in progress
    private boolean mIsRecording = false;

    // the media recorder
    private MediaRecorder mMediaRecorder;

    // the orientation is locked during the video recording
    private int mActivityOrientation;

    private BitmapDrawable mVideoThumbnail;

    /**
     * Provide the camera recording profile
     *
     * @param cameraId the selected camera id
     * @return the profile (cannot be null);
     */
    private static CamcorderProfile getCamcorderProfile(int cameraId) {
        CamcorderProfile camcorderProfile = null;

        // we should test by camera id but hasProfile failed on some devices
        if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)) {
            try {
                camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getCamcorderProfile() : " + e.getMessage(), e);
            }
        }

        if ((null == camcorderProfile) && CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
            try {
                camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getCamcorderProfile() : " + e.getMessage(), e);
            }
        }

        if (null == camcorderProfile) {
            camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        }

        Log.d(LOG_TAG, "getCamcorderProfile for camera " + cameraId
                + " width " + camcorderProfile.videoFrameWidth + " height " + camcorderProfile.videoFrameWidth);
        return camcorderProfile;
    }

    /**
     * @return an unique video file name
     */
    private static String buildNewVideoName() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_hhmmss");
        return "VectorVideo_" + dateFormat.format(new Date()) + ".mp4";
    }

    /**
     * @return the video rotation angle
     */
    private int getVideoRotation() {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(mCameraId, info);

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (info.orientation + degrees) % 360;
        } else {  // back-facing
            return (info.orientation - degrees + 360) % 360;
        }
    }

    /**
     * Start the video recording
     */
    @SuppressLint("NewApi")
    private void startVideoRecord() {
        if (null != mCamera) {
            // lock the orientation
            mActivityOrientation = getRequestedOrientation();
            setRequestedOrientation((Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) ?
                    ActivityInfo.SCREEN_ORIENTATION_LOCKED : ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);

            mTakeImageView.setAlpha(0.0f);
            mRecordAnimationView.setVisibility(View.VISIBLE);
            mRecordAnimationView.startAnimation();

            mIsVideoMode = true;

            initCameraSettings();

            mMediaRecorder = new MediaRecorder();

            mCamera.unlock();
            mMediaRecorder.setCamera(mCamera);

            try {
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## startVideoRecord() : setAudioSource fails " + e.getMessage(), e);
            }

            try {
                mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## startVideoRecord() : setVideoSource fails " + e.getMessage(), e);
            }

            try {
                mMediaRecorder.setProfile(mCamcorderProfile);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## startVideoRecord() : setProfile fails " + e.getMessage(), e);
            }

            File videoFile = new File(getCacheDir().getAbsolutePath(), buildNewVideoName());
            mVideoUri = Uri.fromFile(videoFile);

            mMediaRecorder.setOutputFile(videoFile.getPath());
            mMediaRecorder.setOrientationHint(getVideoRotation());

            // Step 5: Prepare configured MediaRecorder
            try {
                mMediaRecorder.prepare();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## startVideoRecord() : cannot prepare the media recorder " + e.getMessage(), e);
                Toast.makeText(this, getString(R.string.media_picker_cannot_record_video), Toast.LENGTH_SHORT).show();
                stopVideoRecord();
                return;
            }

            try {
                mMediaRecorder.start();
                mIsRecording = true;
            } catch (Exception e) {
                Log.e(LOG_TAG, "## startVideoRecord() : cannot start the media recorder " + e.getMessage(), e);
                Toast.makeText(this, getString(R.string.media_picker_cannot_record_video), Toast.LENGTH_SHORT).show();
                stopVideoRecord();
            }
        }
    }

    /**
     * Release the media recorder
     */
    private void releaseMediaRecorder() {
        if (null != mMediaRecorder) {
            try {
                mMediaRecorder.stop();
                mMediaRecorder.release();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## releaseMediaRecorder() : mMediaRecorder release failed " + e.getMessage(), e);
            }

            mMediaRecorder = null;
        }

        try {
            mCamera.reconnect();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## releaseMediaRecorder() : mCamera reconnect failed " + e.getMessage(), e);
        }
    }

    /**
     * Stop the video recording
     */
    private void stopVideoRecord() {
        if (mIsRecording) {
            mIsVideoMode = false;
            mIsRecording = false;
            mTakeImageView.setAlpha(1.0f);
            mRecordAnimationView.setVisibility(View.GONE);
            mRecordAnimationView.startAnimation();

            releaseMediaRecorder();
            setRequestedOrientation(mActivityOrientation);

            initCameraSettings();
        }
    }

    /**
     * Provide the video file to the caller activity.
     */
    private void sendVideoFile() {
        Bundle conData = new Bundle();
        Intent intent = new Intent();
        intent.setData(mVideoUri);
        intent.putExtras(conData);
        setResult(RESULT_OK, intent);
        finish();
    }

    /**
     * Stop the video preview.
     */
    private void stopVideoPreview() {
        if (mVideoView.isPlaying()) {
            mVideoView.stopPlayback();
            mVideoView.setVideoURI(null);
        }

        // we need to set the visibility to gone
        // to remove the staled video texture
        mVideoView.setVisibility(View.GONE);

        mPreviewLayout.setVisibility(View.GONE);
        mImagePreviewLayout.setVisibility(View.VISIBLE);
        mVideoPreviewLayout.setVisibility(View.GONE);
        mVideoUri = null;
        refreshPlayVideoButton();
    }

    /**
     * Start the video preview
     */
    private void startVideoPreviewVideo(Bitmap aThumbnail) {
        mPreviewLayout.setVisibility(View.VISIBLE);
        mImagePreviewLayout.setVisibility(View.GONE);
        mVideoPreviewLayout.setVisibility(View.VISIBLE);

        Bitmap thumb = aThumbnail;

        if (null == thumb) {
            ThumbnailUtils.createVideoThumbnail(mVideoUri.getPath(), MediaStore.Images.Thumbnails.FULL_SCREEN_KIND);
        }

        if (null == thumb) {
            thumb = ThumbnailUtils.createVideoThumbnail(mVideoUri.getPath(), MediaStore.Images.Thumbnails.MINI_KIND);
        }

        mVideoThumbnail = (null != thumb) ? new BitmapDrawable(thumb) : null;

        mVideoView.setVisibility(View.VISIBLE);
        mVideoView.setBackground(mVideoThumbnail);
        mVideoView.setVideoURI(mVideoUri);

        refreshPlayVideoButton();

        mVideoButtonView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mVideoView.isPlaying()) {
                    mVideoView.stopPlayback();

                    mVideoView.post(new Runnable() {
                        @Override
                        public void run() {
                            mVideoView.setBackground(mVideoThumbnail);
                            refreshPlayVideoButton();
                        }
                    });
                } else {
                    mVideoView.setBackground(null);
                    mVideoView.start();

                    mVideoView.post(new Runnable() {
                        @Override
                        public void run() {
                            refreshPlayVideoButton();
                        }
                    });
                }
            }
        });

        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mVideoView.setBackground(mVideoThumbnail);
                refreshPlayVideoButton();
            }
        });

        // manage video error cases
        mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                mVideoView.setBackground(mVideoThumbnail);
                refreshPlayVideoButton();
                return false;
            }
        });
    }

    /**
     * Update the video button.
     */
    private void refreshPlayVideoButton() {
        mVideoButtonView.setImageResource(((null != mVideoView) && mVideoView.isPlaying()) ? R.drawable.camera_stop : R.drawable.camera_play);
    }

    //==============================================================================================================
    // Medias gallery
    //==============================================================================================================

    /**
     * Populate mMediaStoreImagesList with the images retrieved from the MediaStore.
     * Max number of retrieved medias is set to GALLERY_TABLE_ITEM_SIZE.
     *
     * @return the medias list
     */
    private List<MediaStoreMedia> listLatestMedias() {
        List<MediaStoreMedia> mediasList = new ArrayList<>();

        // images
        String[] imagesProjection = {
                MediaStore.Images.ImageColumns._ID,
                MediaStore.Images.ImageColumns.DATE_TAKEN,
                MediaStore.Images.ImageColumns.MIME_TYPE
        };
        Cursor imagesThumbnailsCursor = null;

        try {
            imagesThumbnailsCursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imagesProjection, // Which columns to return
                    null,       // Return all image files
                    null,
                    MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC LIMIT " + GALLERY_TABLE_ITEM_SIZE);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## listLatestMedias() : " + e.getMessage(), e);
        }

        if (null != imagesThumbnailsCursor) {
            int timeIndex = imagesThumbnailsCursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN);
            int idIndex = imagesThumbnailsCursor.getColumnIndex(MediaStore.Images.ImageColumns._ID);
            int mimeTypeIndex = imagesThumbnailsCursor.getColumnIndex(MediaStore.Images.ImageColumns.MIME_TYPE);

            if (imagesThumbnailsCursor.moveToFirst()) {
                do {
                    try {
                        MediaStoreMedia recentMedia = new MediaStoreMedia();
                        recentMedia.mIsVideo = false;

                        String id = imagesThumbnailsCursor.getString(idIndex);
                        String dateAsString = imagesThumbnailsCursor.getString(timeIndex);
                        recentMedia.mMimeType = imagesThumbnailsCursor.getString(mimeTypeIndex);
                        recentMedia.mCreationTime = Long.parseLong(dateAsString);

                        recentMedia.mThumbnail = MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(),
                                Long.parseLong(id), MediaStore.Images.Thumbnails.MINI_KIND, null);
                        recentMedia.mFileUri = Uri.parse(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString() + "/" + id);

                        int rotationAngle = ImageUtils.getRotationAngleForBitmap(VectorMediaPickerActivity.this, recentMedia.mFileUri);

                        if (0 != rotationAngle) {
                            android.graphics.Matrix bitmapMatrix = new android.graphics.Matrix();
                            bitmapMatrix.postRotate(rotationAngle);
                            recentMedia.mThumbnail = Bitmap.createBitmap(recentMedia.mThumbnail,
                                    0,
                                    0,
                                    recentMedia.mThumbnail.getWidth(),
                                    recentMedia.mThumbnail.getHeight(),
                                    bitmapMatrix,
                                    false);
                        }

                        mediasList.add(recentMedia);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## listLatestMedias(): Msg=" + e.getMessage(), e);
                    }
                } while (imagesThumbnailsCursor.moveToNext());
            }
            imagesThumbnailsCursor.close();
        }

        if (mIsVideoRecordingSupported) {

            // videos
            String[] videosProjection = {
                    MediaStore.Video.VideoColumns._ID,
                    MediaStore.Video.VideoColumns.DATE_TAKEN,
                    MediaStore.Video.VideoColumns.MIME_TYPE
            };
            Cursor videoThumbnailsCursor = null;

            try {
                videoThumbnailsCursor = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        videosProjection, // Which columns to return
                        null,       // Return all image files
                        null,
                        MediaStore.Video.VideoColumns.DATE_TAKEN + " DESC LIMIT " + GALLERY_TABLE_ITEM_SIZE);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## listLatestMedias(): " + e.getMessage(), e);
            }

            if (null != videoThumbnailsCursor) {
                int timeIndex = videoThumbnailsCursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN);
                int idIndex = videoThumbnailsCursor.getColumnIndex(MediaStore.Images.ImageColumns._ID);
                int mimeTypeIndex = videoThumbnailsCursor.getColumnIndex(MediaStore.Images.ImageColumns.MIME_TYPE);

                if (videoThumbnailsCursor.moveToFirst()) {
                    do {
                        try {
                            MediaStoreMedia recentMedia = new MediaStoreMedia();
                            recentMedia.mIsVideo = true;

                            String id = videoThumbnailsCursor.getString(idIndex);
                            String dateAsString = videoThumbnailsCursor.getString(timeIndex);
                            recentMedia.mMimeType = videoThumbnailsCursor.getString(mimeTypeIndex);
                            recentMedia.mCreationTime = Long.parseLong(dateAsString);

                            recentMedia.mThumbnail = MediaStore.Video.Thumbnails.getThumbnail(getContentResolver(),
                                    Long.parseLong(id), MediaStore.Video.Thumbnails.MINI_KIND, null);
                            recentMedia.mFileUri = Uri.parse(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString() + "/" + id);

                            mediasList.add(recentMedia);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## listLatestMedias(): Msg=" + e.getMessage(), e);
                        }
                    } while (videoThumbnailsCursor.moveToNext());
                }
                videoThumbnailsCursor.close();
            }

            Collections.sort(mediasList, new Comparator<MediaStoreMedia>() {
                @Override
                public int compare(MediaStoreMedia r1, MediaStoreMedia r2) {
                    long t1 = r1.mCreationTime;
                    long t2 = r2.mCreationTime;

                    // sort from the most recent
                    return -(t1 < t2 ? -1 : (t1 == t2 ? 0 : 1));
                }
            });
        }

        if (mediasList.size() > GALLERY_TABLE_ITEM_SIZE) {
            Log.d(LOG_TAG, "## listLatestMedias(): Added count=" + GALLERY_TABLE_ITEM_SIZE);
            return mediasList.subList(0, GALLERY_TABLE_ITEM_SIZE);
        } else {
            Log.d(LOG_TAG, "## listLatestMedias(): Added count=" + mediasList.size());
            return mediasList;
        }
    }

    /**
     * Provides the number of medias which will be displayed in the gallery.
     * The maximum value is GALLERY_TABLE_ITEM_SIZE.
     *
     * @return the number of displayed medias
     */
    private int getMediaStoreMediaCount() {
        int retValue = 0;
        Cursor imageThumbnailsCursor = null;

        try {
            imageThumbnailsCursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    null, // no projection
                    null,
                    null,
                    null);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getMediaStoreImageCount() Exception Msg=" + e.getMessage(), e);
        }

        if (null != imageThumbnailsCursor) {
            retValue += imageThumbnailsCursor.getCount();
            imageThumbnailsCursor.close();
        }

        if (mIsVideoRecordingSupported) {
            Cursor videoThumbnailsCursor = null;

            try {
                videoThumbnailsCursor = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        null, // no projection
                        null,
                        null,
                        null);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getMediaStoreImageCount() Exception Msg=" + e.getMessage(), e);
            }

            if (null != videoThumbnailsCursor) {
                retValue += videoThumbnailsCursor.getCount();
                videoThumbnailsCursor.close();
            }
        }

        return Math.min(retValue, GALLERY_TABLE_ITEM_SIZE);
    }

    /**
     * Populate the gallery view with the image/video contents.
     */
    private void refreshRecentMediaList() {
        // start the progress bar and disable the take button
        final RelativeLayout progressBar = findViewById(R.id.media_preview_progress_bar_layout);
        progressBar.setVisibility(View.VISIBLE);
        mTakeImageView.setEnabled(false);
        mTakeImageView.setAlpha(ViewUtilKt.UTILS_OPACITY_HALF);

        mMediaStoreMediaList.clear();

        // run away from the UI thread
        mFileHandler.post(new Runnable() {
            @Override
            public void run() {
                // populate the image thumbnails from multimedia store
                final List<MediaStoreMedia> medias = listLatestMedias();

                // update the UI part
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMediaStoreMediaList.addAll(medias);
                        buildGalleryTableLayout();
                        progressBar.setVisibility(View.GONE);
                        mTakeImageView.setEnabled(true);
                        mTakeImageView.setAlpha(ViewUtilKt.UTILS_OPACITY_FULL);
                    }
                });
            }
        });
    }

    /**
     * Build the image gallery widget programmatically.
     */
    private void buildGalleryTableLayout() {
        TableRow tableRow = null;
        RecentMediaLayout recentMediaView;
        int tableLayoutWidth;
        int cellWidth;
        int cellHeight;
        int itemIndex;
        TableRow.LayoutParams rowLayoutParams;
        TableLayout.LayoutParams tableLayoutParams = new TableLayout.LayoutParams();

        if (null != mGalleryTableLayout) {
            mGalleryTableLayout.removeAllViews();
            mGalleryTableLayout.setBackgroundColor(ThemeUtils.INSTANCE.getColor(this, android.R.attr.colorBackground));

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            tableLayoutWidth = metrics.widthPixels;

            // row layout configuration
            cellWidth = (tableLayoutWidth - GALLERY_COLUMN_COUNT) / GALLERY_COLUMN_COUNT;
            cellHeight = cellWidth;

            if (0 == tableLayoutWidth) {
                // fall back
                rowLayoutParams = new TableRow.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            } else {
                rowLayoutParams = new TableRow.LayoutParams(cellWidth, cellHeight);
            }

            MediaStoreMedia recentMedia;
            mGalleryImageCount = getMediaStoreMediaCount() - 1;

            // set up view for gallery selection
            recentMediaView = new RecentMediaLayout(this);
            recentMediaView.setThumbnailByResource(R.drawable.ic_material_folder_green_vector);
            recentMediaView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openFileExplorer();
                }
            });

            // loop to produce full rows filled in, with an icon folder in last cell
            for (itemIndex = 0; itemIndex <= mGalleryImageCount; itemIndex++) {
                try {
                    recentMedia = mMediaStoreMediaList.get(itemIndex);
                } catch (IndexOutOfBoundsException e) {
                    recentMedia = null;
                }

                // detect row is complete
                if (0 == (itemIndex % GALLERY_COLUMN_COUNT)) {
                    if (null != tableRow) {
                        mGalleryTableLayout.addView(tableRow, tableLayoutParams);
                    }
                    tableRow = new TableRow(this);
                }

                if (itemIndex == 0) {
                    if (tableRow != null)
                        tableRow.addView(recentMediaView, rowLayoutParams);
                    continue;
                }

                // build the content layout for each cell
                if (null != recentMedia) {
                    recentMediaView = new RecentMediaLayout(this);

                    if (null != recentMedia.mThumbnail) {
                        recentMediaView.setThumbnail(recentMedia.mThumbnail);
                    } else {
                        recentMediaView.setThumbnailByUri(recentMedia.mFileUri);
                    }

                    recentMediaView.setBackgroundColor(ThemeUtils.INSTANCE.getColor(this, android.R.attr.colorBackground));
                    final MediaStoreMedia finalRecentMedia = recentMedia;

                    recentMediaView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (!finalRecentMedia.mIsVideo) {
                                onClickGalleryImage(finalRecentMedia);
                            } else {
                                mVideoUri = finalRecentMedia.mFileUri;
                                startVideoPreviewVideo(finalRecentMedia.mThumbnail);
                            }
                        }
                    });

                    // set image logo between gif, image, or video
                    recentMediaView.setVideoType(recentMedia.mIsVideo);
                    if (!recentMedia.mIsVideo) {
                        recentMediaView.setGifType(MIME_TYPE_IMAGE_GIF.equals(recentMedia.mMimeType));
                    }

                    if (tableRow != null) {
                        tableRow.addView(recentMediaView, rowLayoutParams);
                    }
                }
            }

            if (itemIndex == 0) {
                tableRow = new TableRow(this);
            }

            // do not forget to add last row
            if (null != tableRow) {
                mGalleryTableLayout.addView(tableRow, tableLayoutParams);
            }
        } else {
            Log.w(LOG_TAG, "## buildGalleryImageTableLayout(): failure - TableLayout widget missing");
        }
    }

    /**
     * The user clicked on a gallery image
     */
    private void onClickGalleryImage(final MediaStoreMedia aMediaItem) {
        if (null != mCamera) {
            mCamera.stopPreview();
        }

        // add the selected image to be returned by the activity
        mSelectedGalleryImage = aMediaItem;

        // display the image as preview
        if ((null != aMediaItem.mThumbnail) && !mIsAvatarMode) {
            updateUiConfiguration(UI_SHOW_TAKEN_IMAGE, IMAGE_ORIGIN_GALLERY);
            mImagePreviewImageView.setImageBitmap(aMediaItem.mThumbnail);
            // save bitmap to speed up UI restore (life cycle)
            VectorApp.setSavedCameraImagePreview(aMediaItem.mThumbnail);
        } else if (null != aMediaItem.mFileUri) {
            // fall back in case bitmap is not available (unlikely..)
            displayImagePreview(null, null, aMediaItem.mFileUri, IMAGE_ORIGIN_GALLERY);
        } else {
            Log.e(LOG_TAG, "## onClickGalleryImage(): no image to display");
        }

        // save the uri to be accessible for life cycle management
        mImagePreviewImageView.setTag(aMediaItem.mFileUri);
    }

    /**
     * Return the taken image from the gallery to the calling activity.
     * This method returns to the calling activity.
     */
    @SuppressLint("NewApi")
    private void attachImageFromGallery() {

        Bundle conData = new Bundle();
        Intent intent = new Intent();

        if (null != mSelectedGalleryImage) {
            if (!mIsAvatarMode) {
                intent.setData(mSelectedGalleryImage.mFileUri);
            } else {
                intent.setData(getPreviewImageFileUri());
            }
        } else {
            // attach after a screen rotation, the file uri must was saved in the tag
            Uri uriSavedFromLifeCycle = (Uri) mImagePreviewImageView.getTag();

            if (null != uriSavedFromLifeCycle) {
                intent.setData(uriSavedFromLifeCycle);
            }
        }

        intent.putExtras(conData);
        setResult(RESULT_OK, intent);
        // clean footprint in App
        VectorApp.setSavedCameraImagePreview(null);
        finish();
    }

    /**
     * Create bitmap of mImagePreviewImageView.
     * Save it as JEPG format with name: "preview_edit_" + date of creation.
     *
     * @return {@link Uri}of saved image.
     */
    private Uri getPreviewImageFileUri() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Bitmap bitmap = Bitmap.createBitmap(mImagePreviewImageView.getWidth(), mImagePreviewImageView.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        mImagePreviewImageView.draw(c);
        mImagePreviewImageView.invalidate();
        bitmap.compress(Bitmap.CompressFormat.JPEG, AVATAR_COMPRESSION_LEVEL, bytes);

        String fileName = "preview_edit_" + new SimpleDateFormat("yyyy-MM-dd_hhmmss").format(new Date()) + ".jpg";
        File file;

        // remove any previously saved image
        if (!TextUtils.isEmpty(fileName)) {
            file = new File(getCacheDir().getAbsolutePath(), fileName);
            if (file.exists()) {
                file.delete();
            }
        }

        // get new name
        file = new File(getCacheDir().getAbsolutePath(), fileName);

        FileOutputStream outputStream = null;
        try {
            file.createNewFile();

            outputStream = new FileOutputStream(file);
            outputStream.write(bytes.toByteArray());
        } catch (IOException e) {
            Toast.makeText(VectorMediaPickerActivity.this, "Exception getPreviewImageFileUri(): " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            // Close resources
            try {
                if (outputStream != null) {
                    outputStream.close();
                }

            } catch (Exception e) {
                Log.e(LOG_TAG, "## getPreviewImageFileUri(): EXCEPTION Msg=" + e.getMessage(), e);
            }
        }

        return Uri.fromFile(file);
    }
}
