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
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import im.vector.R;

import android.hardware.Camera;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class VectorMediasPickerActivity extends MXCActionBarActivity implements SurfaceHolder.Callback {

    // medias folder
    private static final int REQUEST_MEDIAS = 0;

    // camera object
    private Camera mCamera = null;
    // start with back camera
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

    // graphical items
    ImageView mSwitchCameraImageView = null;
    ImageView mTakePhotoImageView = null;
    SurfaceHolder mCameraSurfaceHolder = null;
    SurfaceView mCameraSurfaceView = null;
    ImageView mCameraDefaultView = null;
    Button mRetakeButton = null;
    Button mAttachImageButton = null;
    Button mOpenLibraryButton = null;
    Button mUseImagesListButton = null;

    private String mShootedPicturePath = null;
    private Boolean mIsPreviewStarted = false;

    int mCameraOrientation = 0;

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
        if (null == mCamera) {
            mCameraDefaultView.setVisibility(View.VISIBLE);
            mCameraSurfaceView.setVisibility(View.GONE);
        } else {
            mCameraDefaultView.setVisibility(View.GONE);
            mCameraSurfaceView.setVisibility(View.VISIBLE);
        }

        if (null == mCamera) {
            disableView(mTakePhotoImageView);
            disableView(mRetakeButton);
            disableView(mAttachImageButton);
            disableView(mSwitchCameraImageView);
        } else if (null == mShootedPicturePath) {
            enableView(mTakePhotoImageView);
            disableView(mRetakeButton);
            disableView(mAttachImageButton);
            enableView(mSwitchCameraImageView);
        } else {
            disableView(mTakePhotoImageView);
            enableView(mRetakeButton);
            enableView(mAttachImageButton);
            disableView(mSwitchCameraImageView);
        }

        // must have more than 2 cameras
        if (2 > Camera.getNumberOfCameras()) {
            disableView(mSwitchCameraImageView);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // cancel the camera use
        // to avoid locking it
        if (null != mCamera) {
            mCamera.stopPreview();
            mIsPreviewStarted = false;
            manageButtons();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // should always be true
        if (null == mCamera) {
            // check if the device has at least camera
            if (Camera.getNumberOfCameras() > 0) {
                mCameraDefaultView.setVisibility(View.GONE);
                mCameraSurfaceView.setVisibility(View.VISIBLE);

                if (null == mCameraSurfaceHolder) {
                    mCameraSurfaceHolder = mCameraSurfaceView.getHolder();
                    mCameraSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                    mCameraSurfaceHolder.setSizeFromLayout();
                    mCameraSurfaceHolder.addCallback(this);
                }
            }
        } else {
            if (null == mShootedPicturePath) {
                mCamera.startPreview();
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
     * Take a picture of the current preview
     */
    void takePicture() {
        if (null != mCamera) {
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
        }
    }

    /**
     * Cancel the taken image
     */
    void retake() {
        if (null != mCamera) {
            mCamera.startPreview();
            mShootedPicturePath = null;
            manageButtons();
        }
    }

    /**
     * The taken image is accpeted
     */
    void attachImage() {
        try {
            String uriString = CommonActivityUtils.saveImageIntoGallery(this, new File(mShootedPicturePath));

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
        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        // did not find a way to filter image and video files
        fileIntent.setType("*/*");
        startActivityForResult(fileIntent, REQUEST_MEDIAS);
    }

    void attachCarouselMedias() {
        // TODO
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
        if ((null != mCamera) && (holder == mCameraSurfaceHolder) && !mIsPreviewStarted) {
            try {
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
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }
}
