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
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import im.vector.R;
import im.vector.view.RecentMediaLayout;

import android.hardware.Camera;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class VectorMediasPickerActivity extends MXCActionBarActivity implements SurfaceHolder.Callback {
    // medias folder
    private static final int REQUEST_MEDIAS = 54;
    private static final String LOG_TAG = "VectorMedPicker";
    public static final String SQL_IMAGE_MAX_LIMIT = "12";

    public static final String EXTRA_SINGLE_IMAGE_MODE = "im.vector.activity.VectorMediasPickerActivity.EXTRA_SINGLE_IMAGE_MODE";
    private static final int IMAGE_ORIGIN_CAMERA = 1;
    private static final int IMAGE_ORIGIN_GALLERY = 2;
    private static final boolean UI_SHOW_TAKEN_IMAGE = true;
    private static final boolean UI_SHOW_CAMERA_PREVIEW = false;

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
    ImageView mExitActivityImageView;
    // no more video capture, only pictures are taken
    // ImageView mRecordModeImageView = null; // recording mode: video<=>image

    //TextView mCaptureTitleTextView = null; display
    ImageView mTakeImageView = null;
    SurfaceHolder mCameraSurfaceHolder = null;
    SurfaceView mCameraSurfaceView = null;
    //ImageView mCameraDefaultView = null;
    //Button mCancelTakenPhotoButton = null;
    Button mAttachImageButton = null;
    ImageView mOpenLibraryImageView = null;
    //Button mGalleryAttachImageButton = null;
    LinearLayout mGalleryImagesListLayout = null;
    LinearLayout mGalleryRecentImagesLayout = null;
    ImageView mGalleryImagePreviewImageView;
    LinearLayout mCancelAndAttachImageLayout;
    //LinearLayout mGalleryButtonsLayout;
    //VideoView mVideoView = null;  rendu video
    private ImageView mCancelTakenImageImageView;
    private ImageView mAttachTakenImageImageView;

    //
    MediaRecorder mMediaRecorder = null;

    private String mShootedPicturePath = null;
    private String mRecordedVideoPath = null;
    private Boolean mIsPreviewStarted = false;

    static private Boolean mIsSingleImageMode = false; // TODO keep it for further use?
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
        mExitActivityImageView = (ImageView) findViewById(R.id.medias_picker_exit);
        mCameraSurfaceView = (SurfaceView) findViewById(R.id.medias_picker_surface_view);
        mGalleryImagePreviewImageView = (ImageView) findViewById(R.id.medias_picker_gallery_preview_image_view);
        //mCameraDefaultView = (ImageView) findViewById(R.id.medias_picker_preview);

        //mCancelTakenPhotoButton = (Button) findViewById(R.id.medias_picker_cancel_take_picture_button);
        // live camera layout: take image, then attach or cancel (redo)
        mCancelAndAttachImageLayout = (LinearLayout) findViewById(R.id.cancel_attach_picture_layout);
        mCancelTakenImageImageView = (ImageView) findViewById(R.id.medias_picker_cancel_take_picture_imageview);
        mAttachTakenImageImageView = (ImageView) findViewById(R.id.medias_picker_attach_take_picture_imageview);
        mTakeImageView = (ImageView) findViewById(R.id.medias_picker_camera_button);

        //mAttachImageButton = (Button) findViewById(R.id.medias_picker_attach_take_picture_button);

        mOpenLibraryImageView = (ImageView) findViewById(R.id.medias_picker_attach_from_library_imageview);
        //mGalleryButtonsLayout = (LinearLayout) findViewById(R.id.picture_gallery_menu_layout); // only the attach button
        //mGalleryAttachImageButton = (Button) findViewById(R.id.medias_picker_attach_gallery_image_button);
        mGalleryImagesListLayout = (LinearLayout) findViewById(R.id.medias_picker_recents_layout);
        mGalleryRecentImagesLayout = (LinearLayout) findViewById(R.id.medias_picker_recents_container);

        //mRecordModeImageView = (ImageView) findViewById(R.id.medias_picker_recording_mode);
        //mVideoView = (VideoView) findViewById(R.id.medias_picker_video_view);
        //mCaptureTitleTextView = (TextView) findViewById(R.id.medias_picker_camera_title);

        //Intent intent = getIntent();
        //mIsSingleImageMode = intent.hasExtra(EXTRA_SINGLE_IMAGE_MODE);

        /*if (mIsSingleImageMode) {
            mRecordModeImageView.setVisibility(View.INVISIBLE);
        }*/

        // click action
        mSwitchCameraImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.switchCamera();
            }
        });

        mTakeImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.takeImage();
            }
        });

        mCancelTakenImageImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.reTakeImage();
            }
        });
        /*mCancelTakenPhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.reTakeImage();
            }
        });*/

        mAttachTakenImageImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(0 == mSelectedRecents.size())
                    VectorMediasPickerActivity.this.attachImageFrom(IMAGE_ORIGIN_CAMERA);
                else
                    VectorMediasPickerActivity.this.attachImageFrom(IMAGE_ORIGIN_GALLERY);
            }
        });
        /*mAttachImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.attachImageFromCamera();
            }
        });*/

        // carousel images
        mOpenLibraryImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.openFileExplorer();
            }
        });

        /*mGalleryAttachImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.attachImageFrom(IMAGE_ORIGIN_GALLERY);
            }
        });*/

        /*mRecordModeImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsPhotoMode = !mIsPhotoMode;
                VectorMediasPickerActivity.this.manageButtons();
            }
        });*/

        mHandlerThread = new HandlerThread("VectorMediasPickerActivityThread");
        mHandlerThread.start();
        mFileHandler = new android.os.Handler(mHandlerThread.getLooper());

        // set default UI: camera preview
        updateUiConfiguration(UI_SHOW_CAMERA_PREVIEW, IMAGE_ORIGIN_CAMERA);
    }


    /**
     * mExitActivityImageView handler
     * @param aView
     */
    public void onExitButton(View aView) {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

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
            //mCameraDefaultView.setVisibility(View.VISIBLE);
            mCameraSurfaceView.setVisibility(View.GONE);
            //mVideoView.setVisibility(View.GONE);
        } else if (null != mRecordedVideoPath) {
            mCameraSurfaceView.setVisibility(View.GONE);
            //mVideoView.setVisibility(View.VISIBLE);
        } else {
            //mCameraDefaultView.setVisibility(View.GONE);
            mCameraSurfaceView.setVisibility(View.VISIBLE);
            //mVideoView.setVisibility(View.GONE);
        }

        // no camera
        if ((null == mCamera) && (null == mRecordedVideoPath)) {
            //disableView(mRecordModeImageView);
            disableView(mTakeImageView);
            //disableView(mCancelTakenPhotoButton);
            //disableView(mAttachImageButton);
            disableView(mSwitchCameraImageView);
        } else if (null != mMediaRecorder)  {
            enableView(mTakeImageView);
            //disableView(mCancelTakenPhotoButton);
            //disableView(mAttachImageButton);
            disableView(mSwitchCameraImageView);
            //disableView(mRecordModeImageView);
        } else if (null != mRecordedVideoPath) {
            enableView(mTakeImageView);
            //enableView(mCancelTakenPhotoButton);
            //enableView(mAttachImageButton);
            enableView(mSwitchCameraImageView);
            //enableView(mRecordModeImageView);
        } else if (null == mShootedPicturePath) {
            enableView(mTakeImageView);
            //disableView(mCancelTakenPhotoButton);
            //disableView(mAttachImageButton);
            enableView(mSwitchCameraImageView);
            //enableView(mRecordModeImageView);
        } else {
            disableView(mTakeImageView);
            //enableView(mCancelTakenPhotoButton);
            //enableView(mAttachImageButton);
            disableView(mSwitchCameraImageView);
        }

        // must have more than 2 cameras
        /*if (2 > Camera.getNumberOfCameras()) {
            disableView(mSwitchCameraImageView);
        }*/

        // selection from the media list
        /*if (mSelectedRecents.size() > 0)  {
            enableView(mGalleryAttachImageButton);
        } else {
            disableView(mGalleryAttachImageButton);
        }
        */

        // manage the take picture button
        /*if (mIsPhotoMode) {
            //mRecordModeImageView.setImageResource(R.drawable.ic_material_camera);
            mTakeImageView.setImageResource(R.drawable.ic_take_picture_vector_green);
            //mCaptureTitleTextView.setText(R.string.media_picker_picture_capture_title);
        } else {
            //mCaptureTitleTextView.setText(R.string.media_picker_video_capture_title);
            //mRecordModeImageView.setImageResource(R.drawable.ic_material_videocam);
            // playing mode
            if (mVideoView.getVisibility() == View.VISIBLE) {
                if (mVideoView.isPlaying()) {
                    mTakeImageView.setImageResource(R.drawable.ic_material_stop);
                } else {
                    mTakeImageView.setImageResource(R.drawable.ic_material_play_circle);
                }
            } else if (null != mMediaRecorder) {
                // stop the record
                mTakeImageView.setImageResource(R.drawable.ic_material_stop);
            } else {
                // wait that the user start the video recording
                mTakeImageView.setImageResource(R.drawable.ic_material_videocam);
            }
        }*/
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

    private int getCarouselItemWidth() {
        return mGalleryRecentImagesLayout.getLayoutParams().height;
    }

    private void startCameraPreview() {
        // init UI: "take picture" button is displayed and the cancel & attach buttons are hidden
        updateUiConfiguration(UI_SHOW_CAMERA_PREVIEW, IMAGE_ORIGIN_CAMERA);

        // should always be true
        if (null == mCamera) {
            // check if the device has at least camera
            if (Camera.getNumberOfCameras() > 0) {
                //mVideoView.setVisibility(View.GONE);
                //mCameraDefaultView.setVisibility(View.GONE);
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

    /**
     * Result handler associated to {@Link #openFileExplorer()} request.
     * This method returns to the calling activity.
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
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
     * Populate mRecentsMedias with the images retrieved from the MediaStore images.
     * Max number of retrieved images is set to 12.
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
                    MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC LIMIT "+ SQL_IMAGE_MAX_LIMIT);
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
     * Populate the gallery view with the image/video contents.
     */
    private void refreshRecentsMediasList() {
        // the last 30 days
        final long maxLifetime = 1000L * 60L * 60L * 24L * 30L; // tt

        mGalleryImagesListLayout.removeAllViews();
        mRecentsMedias.clear();

        // run away from the UI thread
        mFileHandler.post(new Runnable() {
            @Override
            public void run() {
                addImagesThumbnails(maxLifetime);

                Collections.sort(mRecentsMedias, new Comparator<RecentMedia>() {
                    @Override
                    public int compare(RecentMedia r1, RecentMedia r2) {
                        long t1 = r1.mCreationTime;
                        long t2 = r2.mCreationTime;

                        // sort from the most recent
                        return -(t1 < t2 ? -1 : (t1 == t2 ? 0 : 1));
                    }
                });

                // update the UI part
                VectorMediasPickerActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        buildGalleryImageLayout();
                    }
                });

                // test build tablelayout
                // update the UI part
                /*VectorMediasPickerActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        buildGalleryImageTableLayout();
                    }
                });*/
            }
        });
    }

    private void buildGalleryImageLayout() {
        int itemWidth = getCarouselItemWidth();

        for (RecentMedia recentMedia : mRecentsMedias) {
            final RecentMediaLayout recentMediaLayout = new RecentMediaLayout(VectorMediasPickerActivity.this);

            recentMediaLayout.setThumbnail(recentMedia.mThumbnail);
            recentMediaLayout.enableMediaTypeDisplay(false);

            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(itemWidth, ViewGroup.LayoutParams.MATCH_PARENT);
            recentMediaLayout.setLayoutParams(params);

            // build the gallery layout by adding a new layout
            recentMedia.mRecentMediaLayout = recentMediaLayout;
            mGalleryImagesListLayout.addView(recentMediaLayout, params);

            final RecentMedia fRecentMedia = recentMedia;

            // add the listener handler: display the selected image in fullscreen preview
            recentMediaLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onClickGalleryImage(fRecentMedia);
                }
            });
        }
    }


    private void buildGalleryImageTableLayout() {
        final int CELL_PADD = 0, CELL_MARG = 1;
        final int COLUMN_COUNT = 4, RAW_COUNT = 3;
        TableRow tableRow = null;
        ImageView imageView = null;
        TableLayout tableLayout= (TableLayout)findViewById(R.id.action_bar_title); //R.id.gallery_table_layout
        int tableLayoutHeight = tableLayout.getLayoutParams().height;
        int rawHeight = tableLayoutHeight / 3;
        int rawWidth = rawHeight;
        int itemIndex = 0;

        TableLayout.LayoutParams tableLayoutParams = new TableLayout.LayoutParams();
        TableRow.LayoutParams rawLayoutParams = new TableRow.LayoutParams(100, 100);
        //TableRow.LayoutParams rawLayoutParams = new TableRow.LayoutParams(400,400);
        rawLayoutParams.setMargins(CELL_MARG, CELL_MARG, CELL_MARG, CELL_MARG);
        rawLayoutParams.weight = 1;

        for(int rawIdx=0;rawIdx<RAW_COUNT;rawIdx++) {
            tableRow = new TableRow(this);

            for (int i = 0; i < COLUMN_COUNT; i++) {
                imageView = new ImageView(this);
                //imageView.setAdjustViewBounds(false);
                //imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                //imageView.setPadding(CELL_PADD, CELL_PADD, CELL_PADD, CELL_PADD);
                imageView.setImageResource(R.drawable.ic_material_folder_green_vector);
                // imageView.setLayoutParams(new TableLayout.LayoutParams(500, 500)); ignored!

                //******************************************************
                // size is used here:
                //TableRow.LayoutParams rawLayoutParams = new TableRow.LayoutParams(500,500);
                //TableRow.LayoutParams rawLayoutParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT);
                //******************************************************

                tableRow.addView(imageView, rawLayoutParams);
            }
            tableLayout.addView(tableRow, tableLayoutParams);
        }
        int itemWidth = getCarouselItemWidth();

        for (final RecentMedia recentMedia : mRecentsMedias) {
            if(0 == itemIndex%COLUMN_COUNT){
                if(null != tableRow){
                    tableLayout.addView(tableRow, tableLayoutParams);
                }
                tableRow = new TableRow(this);
            }

            imageView = new ImageView(this);
            imageView.setImageBitmap(recentMedia.mThumbnail);
            //imageView.setAdjustViewBounds(false);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);//FIT_CENTER
            //ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(itemWidth, ViewGroup.LayoutParams.MATCH_PARENT);

            // add the listener handler: display the selected image in fullscreen preview
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onClickGalleryImage(recentMedia);
                }
            });

            tableRow.addView(imageView, rawLayoutParams);
            itemIndex++;
        }

        // add last row
        if(null != tableRow){
            tableLayout.addView(tableRow, tableLayoutParams);
        }
    }


    /*private void createTableLayout() {
        String[] rowList = {"ROW1", "ROW2", "Row3", "Row4", "Row 5", "Row 6", "Row 7"};
        String[] columnList = {"COLUMN1", "COLUMN2", "COLUMN3", "COLUMN4",  "COLUMN5", "COLUMN6"};
        int rowCount = rowList.length;
        int columnCount = columnList.length;

        // 1) Create a tableLayout and its params
        //TableLayout.LayoutParams tableLayoutParams = new TableLayout.LayoutParams();
        TableLayout tableLayout = (TableLayout)findViewById(R.id.gallery_table_layout);
        tableLayout.setBackgroundColor(getResources().getColor(R.color.error_color));

        // 2) create tableRow params
        TableRow.LayoutParams tableRowParams = new TableRow.LayoutParams();
        tableRowParams.setMargins(1, 1, 1, 1);
        tableRowParams.weight = 1;

        for (int i = 0; i <= rowCount; i++) {
            // 3) create tableRow
            TableRow tableRow = new TableRow(this);
            tableRow.setBackgroundColor(Color.BLACK);

            for (int j= 0; j <= columnCount; j++) {
                // 4) create textView
                TextView textView = new TextView(this);
                //  textView.setText(String.valueOf(j));
                textView.setBackgroundColor(Color.WHITE);
                textView.setGravity(Gravity.CENTER);

                String s1 = Integer.toString(i);
                String s2 = Integer.toString(j);
                String s3 = s1 + s2;
                int id = Integer.parseInt(s3);

                if (i ==0 && j==0){
                    textView.setText("0==0");
                } else if(i==0){
                    textView.setText(columnList[j-1]);
                }else if( j==0){
                    textView.setText(rowList[i-1]);
                }else {
                    textView.setText(""+id);
                    // check id=23
                    if(id==23){
                        textView.setText("ID=23");
                    }
                }

                // 5) add textView to tableRow
                tableRow.addView(textView, tableRowParams);
            }

            // 6) add tableRow to tableLayout
            tableLayout.addView(tableRow, tableLayoutParams);
        }
    }*/

    private void onClickGalleryImage(final RecentMedia aMediaItem){
        mCamera.stopPreview();
        // setup the UI for a preview from a gallery selection
        updateUiConfiguration(UI_SHOW_TAKEN_IMAGE, IMAGE_ORIGIN_GALLERY);
        VectorMediasPickerActivity.this.manageButtons();

        // add the selected image to be returned by the activity
        mSelectedRecents.add(aMediaItem);

        // display the image as preview
        if(null != aMediaItem.mFileUri)
            mGalleryImagePreviewImageView.setImageURI(aMediaItem.mFileUri);
    }

    private void buildGalleryImageWithSelectionLayout() {
        int itemWidth = getCarouselItemWidth();

        for (RecentMedia recentMedia : mRecentsMedias) {
            final RecentMediaLayout recentMediaLayout = new RecentMediaLayout(VectorMediasPickerActivity.this);

            recentMediaLayout.setThumbnail(recentMedia.mThumbnail);
            recentMediaLayout.setIsVideo(recentMedia.mIsvideo);

            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(itemWidth, ViewGroup.LayoutParams.MATCH_PARENT);
            recentMediaLayout.setLayoutParams(params);

            recentMedia.mRecentMediaLayout = recentMediaLayout;
            mGalleryImagesListLayout.addView(recentMediaLayout, params);

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

                    // set the new selection display as the opposite of the previous selection state
                    recentMediaLayout.setIsSelected(!recentMediaLayout.isSelected());
                    VectorMediasPickerActivity.this.manageButtons();
                }
            });
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
    void takeImage() {
        // a video is recorded
        if (null != mRecordedVideoPath) {
            notImplementedFeature("takeImage() - playing a recorded video");
            // play a video
            /*if (mVideoView.isPlaying()) {
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
            manageButtons();*/
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

                            // force to stop preview:
                            // some devices do not stop preview after the picture was taken (ie. G6 edge)
                            mCamera.stopPreview();
                            // set the UI preview
                            updateUiConfiguration(UI_SHOW_TAKEN_IMAGE, IMAGE_ORIGIN_CAMERA);
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
                notImplementedFeature("takeImage() - camera");
                /*
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
            */}
        }
    }

    /**
     * Update the UI according to camera action. Two UIs are displayed:
     * the camera preview (default configuration) and the taken picture (when
     * the user took a picture via the camera or has chosen one from the gallery)
     *
     * When the taken image is displayed, only two buttons are displayed: attach
     * the current image or re take another image with the camera.
     * We also have to distinguish the origin of the taken image: from the camera
     * or from the gallery.
     *
     * @param aIsTakenImageDisplayed true to display the taken image, false to show the camera preview
     * @param aImageOrigin IMAGE_ORIGIN_CAMERA or IMAGE_ORIGIN_GALLERY
     */
    private void updateUiConfiguration(boolean aIsTakenImageDisplayed, int aImageOrigin){
        // "cancel & attach" buttons of the picture taken: show it, only if
        // an picture has been taken. These buttons allow the
        // user dismiss the current picture or to send it to the room
        mCancelAndAttachImageLayout.setVisibility(aIsTakenImageDisplayed ? View.VISIBLE : View.GONE);

        // "take image" button: hide it when the taken image is displayed
        mTakeImageView.setVisibility(aIsTakenImageDisplayed ? View.INVISIBLE : View.VISIBLE);

        // "camera switch & exit" buttons: hide it when the taken image is displayed
        mExitActivityImageView.setVisibility(aIsTakenImageDisplayed ? View.GONE : View.VISIBLE);
        mSwitchCameraImageView.setVisibility(aIsTakenImageDisplayed ? View.GONE : View.VISIBLE);
        // if more than two cameras are available, just disable the "switch camera" capability
        if (2 > Camera.getNumberOfCameras()) {
            disableView(mSwitchCameraImageView);
        }

        // gallery widgets: hide it when the taken image is displayed
        mGalleryRecentImagesLayout.setVisibility(aIsTakenImageDisplayed ? View.GONE : View.VISIBLE);

        if(false == aIsTakenImageDisplayed) {
            // clear the selected image from the gallery (if any)
            mSelectedRecents.clear();
        }

        if((IMAGE_ORIGIN_GALLERY == aImageOrigin) && (aIsTakenImageDisplayed)){
            mGalleryImagePreviewImageView.setVisibility(View.VISIBLE);
            mCameraSurfaceView.setVisibility(View.GONE);
        }
        else {
            // the default UI: hide gallery preview, show the surface view
            mGalleryImagePreviewImageView.setVisibility(View.GONE);
            mCameraSurfaceView.setVisibility(View.VISIBLE);
        }
    }

    @SuppressLint("NewApi")
    private void refreshVideoThumbnail(boolean show) {
        BitmapDrawable bitmapDrawable = null;

        notImplementedFeature("refreshVideoThumbnail()");
        /*
        if (show && (null != mRecordedVideoPath)) {
            Bitmap thumb = ThumbnailUtils.createVideoThumbnail(mRecordedVideoPath, MediaStore.Images.Thumbnails.MINI_KIND);
            bitmapDrawable = new BitmapDrawable(VectorMediasPickerActivity.this.getResources(), thumb);
        }
        // display the video thumbnail
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)) {
            mVideoView.setBackground(bitmapDrawable);
        } else {
            mVideoView.setBackgroundDrawable(bitmapDrawable);
        }*/
    }

    /**
     * Cancel the current image preview, and setup the UI to
     * start a new image capture.
     */
    void reTakeImage() {
        mShootedPicturePath = null;
        mRecordedVideoPath = null;
        manageButtons();

        startCameraPreview();
    }

    /**
     * "attach image" dispatcher.
     *
     * @param aImageOrigin camera, otherwise gallery
     */
    void attachImageFrom(int aImageOrigin) {
        if(IMAGE_ORIGIN_CAMERA == aImageOrigin){
            attachImageFromCamera();
        }
        else if(IMAGE_ORIGIN_GALLERY == aImageOrigin){
            attachImageFrommGallery();
        }
        else {
            Log.w(LOG_TAG,"## attachImageFrom(): unknown image origin");
        }
    }

    /**
     * Return the taken image from the camera to the calling activity.
     * This method returns to the calling activity.
     */
    void attachImageFromCamera() {

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

    private void openFileExplorer() {
        Intent fileIntent = new Intent(Intent.ACTION_PICK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        // did not find a way to filter image and video files
        fileIntent.setType(CommonActivityUtils.MIME_TYPE_IMAGE_ALL);
        startActivityForResult(fileIntent, REQUEST_MEDIAS);
    }

    /**
     * Return the taken image from the gallery to the calling activity.
     * This method returns to the calling activity.
     */
    @SuppressLint("NewApi")
    void attachImageFrommGallery() {

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


    // *********************************************************************************************
    // SurfaceHolder.Callback implementation
    @Override
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
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if ((null != mCamera) && !mIsPreviewStarted) {
            try {
                mCameraSurfaceHolder = holder;
                mCamera.setPreviewDisplay(mCameraSurfaceHolder);
                setCameraDisplayOrientation();

                Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
                android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();

                //  Valid values are 0, 90, 180, and 270 (0 = landscape)
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
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mIsPreviewStarted = false;
        stopVideoRecord();
        mCameraSurfaceHolder = null;
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }
    // *********************************************************************************************

    private void notImplementedFeature(String aErrorMsg){
        Log.e("EXCEP","## Unexpected code for "+aErrorMsg);
    }
}
