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

import android.os.Bundle;
import im.vector.R;
import android.hardware.Camera;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

public class VectorMediasPickerActivity extends MXCActionBarActivity implements SurfaceHolder.Callback {

    private Camera mCamera = null;
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;

    ImageView mSwitchCameraImageView = null;
    ImageView mTakePhotoImageView = null;

    SurfaceHolder mCameraSurfaceHolder = null;
    SurfaceView mCameraSurfaceView = null;
    ImageView mCameraDefaultView = null;

    Button mRetakeButton = null;
    Button mAttachImageButton = null;

    Button mOpenLibraryButton = null;
    Button mUseImagesListButton = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_medias_picker);

        mSwitchCameraImageView = (ImageView) findViewById(R.id.medias_picker_switch_camera);
        mCameraSurfaceView = (SurfaceView) findViewById(R.id.medias_picker_surface_view);
        mCameraDefaultView = (ImageView) findViewById(R.id.medias_picker_preview);

        mRetakeButton = (Button) findViewById(R.id.medias_picker_retake_button);
        mTakePhotoImageView = (ImageView) findViewById(R.id.medias_picker_camera_button);
        mAttachImageButton = (Button) findViewById(R.id.medias_picker_attach1_button);

        mOpenLibraryButton = (Button) findViewById(R.id.medias_picker_library_button);
        mUseImagesListButton = (Button) findViewById(R.id.medias_picker_attach2_button);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (null == mCamera) {
            mCameraDefaultView.setVisibility(View.GONE);
            mCameraSurfaceView.setVisibility(View.VISIBLE);

            try {
                mCameraSurfaceHolder = mCameraSurfaceView.getHolder();
                mCameraSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                mCameraSurfaceHolder.setSizeFromLayout();
                mCameraSurfaceHolder.addCallback(this);
            } catch (Exception e) {
                e = e;
            }
        }
    }


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

        int result;

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        mCamera.setDisplayOrientation(result);
    }



    // SurfaceHolder.Callback
    /**
     * This is called immediately after the surface is first created.
     * Implementations of this should start up whatever rendering code
     * they desire.  Note that only one thread can ever draw into
     * a {@link Surface}, so you should not draw into the Surface here
     * if your normal rendering will be in another thread.
     *
     * @param holder The SurfaceHolder whose surface is being created.
     */
    public void surfaceCreated(SurfaceHolder holder) {
        mCamera = Camera.open(mCameraId);
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
        try {
            mCamera.setPreviewDisplay(mCameraSurfaceHolder);
            setCameraDisplayOrientation();
            mCamera.startPreview();
        } catch (Exception e) {
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
