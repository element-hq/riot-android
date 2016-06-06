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
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.view.RecentMediaLayout;

import android.hardware.Camera;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.Toast;

import org.matrix.androidsdk.util.ImageUtils;

import java.io.ByteArrayInputStream;
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

/**
 * VectorMediasPickerActivity is used to take a photo or to send an old one.
 */
public class VectorMediasPickerActivity extends MXCActionBarActivity implements TextureView.SurfaceTextureListener {
    // medias folder
    private static final int REQUEST_MEDIAS = 54;
    private static final String LOG_TAG = "VectorMedPicker";

    // public keys
    public static final String EXTRA_SINGLE_IMAGE_MODE = "EXTRA_SINGLE_IMAGE_MODE";

    // internal keys
    private static final String KEY_EXTRA_IS_TAKEN_IMAGE_DISPLAYED = "IS_TAKEN_IMAGE_DISPLAYED";
    private static final String KEY_EXTRA_TAKEN_IMAGE_ORIGIN = "TAKEN_IMAGE_ORIGIN";
    private static final String KEY_EXTRA_TAKEN_IMAGE_GALLERY_URI = "TAKEN_IMAGE_GALLERY_URI";
    private static final String KEY_EXTRA_TAKEN_IMAGE_CAMERA_URL = "TAKEN_IMAGE_CAMERA_URL";
    private static final String KEY_EXTRA_CAMERA_SIDE = "TAKEN_IMAGE_CAMERA_SIDE";
    private static final String KEY_PREFERENCE_CAMERA_IMAGE_NAME = "KEY_PREFERENCE_CAMERA_IMAGE_NAME";

    private final int IMAGE_ORIGIN_CAMERA = 1;
    private final int IMAGE_ORIGIN_GALLERY = 2;
    private final boolean UI_SHOW_TAKEN_IMAGE = true;
    private final boolean UI_SHOW_CAMERA_PREVIEW = false;
    private final int GALLERY_COLUMN_COUNT = 4;
    private final int GALLERY_RAW_COUNT = 3;
    private final double SURFACE_VIEW_HEIGHT_RATIO = 0.95;
    private final int GALLERY_TABLE_ITEM_SIZE = (GALLERY_COLUMN_COUNT * GALLERY_RAW_COUNT);
    private final int JPEG_QUALITY_MAX = 100;
    private final String MIME_TYPE_IMAGE_GIF = "image/gif";
    private final String MIME_TYPE_IMAGE = "image";

    /**
     * define a recent media
     */
    private class RecentMedia {
        public Uri mFileUri;
        public long mCreationTime;
        public Bitmap mThumbnail;
        public Boolean mIsVideo;
        public String mMimeType = "";
    }

    // recents medias list
    private final ArrayList<RecentMedia> mMediaStoreImagesList = new ArrayList<>();
    private final ArrayList<RecentMedia> mSelectedGalleryItemsList = new ArrayList<>();

    // camera object
    private Camera mCamera;
    private int mCameraId;
    private int mCameraOrientation = 0;

    // graphical items
    private ImageView mSwitchCameraImageView;

    // camera preview and gallery selection layout
    private View mPreviewScrollView;
    private ImageView mTakeImageView;
    private TableLayout mGalleryTableLayout;
    private RelativeLayout mCameraPreviewLayout;
    private TextureView mCameraTextureView;
    private SurfaceTexture mSurfaceTexture;

    private View mImagePreviewLayout;
    private ImageView mImagePreviewImageView;
    private RelativeLayout mPreviewAndGalleryLayout;
    private int mGalleryImageCount;
    private int mScreenWidth;

    // lifecycle management variable
    private boolean mIsTakenImageDisplayed;
    private int mTakenImageOrigin;

    private String mShootedPicturePath;
    private int mCameraPreviewHeight = 0;

    /**
     * The recent requests are performed in a dedicated thread
     */
    private HandlerThread mHandlerThread;
    private android.os.Handler mFileHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_medias_picker);

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

        // camera preview
        mPreviewScrollView = findViewById(R.id.medias_picker_scrollView);
        mSwitchCameraImageView = (ImageView) findViewById(R.id.medias_picker_switch_camera);
        mCameraTextureView =  (TextureView) findViewById(R.id.medias_picker_texture_view);
        mCameraTextureView.setSurfaceTextureListener(this);

        // image preview
        mImagePreviewLayout = findViewById(R.id.medias_picker_preview);
        mImagePreviewImageView = (ImageView) findViewById(R.id.medias_picker_preview_image_view);
        mTakeImageView = (ImageView) findViewById(R.id.medias_picker_camera_button);
        mGalleryTableLayout = (TableLayout)findViewById(R.id.gallery_table_layout);

        //
        mSwitchCameraImageView.setVisibility((Camera.getNumberOfCameras() > 1) ? View.VISIBLE : View.GONE);

        // click action
        mSwitchCameraImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.onSwitchCamera();
            }
        });

        mTakeImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.onClickTakeImage();
            }
        });


        findViewById(R.id.medias_picker_cancel_take_picture_imageview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.cancelTakeImage();
            }
        });

        findViewById(R.id.medias_picker_attach_take_picture_imageview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.attachImageFrom(mTakenImageOrigin);
            }
        });

        initCameraLayout();

        // setup separate thread for image gallery update
        mHandlerThread = new HandlerThread("VectorMediasPickerActivityThread");
        mHandlerThread.start();
        mFileHandler = new android.os.Handler(mHandlerThread.getLooper());

        if(!restoreInstanceState(savedInstanceState)){
            // default UI: if a taken image is not in preview, then display: live camera preview + "take picture"/switch/exit buttons
            updateUiConfiguration(UI_SHOW_CAMERA_PREVIEW, IMAGE_ORIGIN_CAMERA);
        }

        // Force screen orientation be managed by the sensor in case user's setting turned off
        // sensor-based rotation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }

    /**
     * Init the camera layout to make the surface texture + the gallery layout, both
     * enough large to enable scrolling.
     */
    private void initCameraLayout() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenHeight = metrics.heightPixels;
        mScreenWidth = metrics.widthPixels;

        mCameraPreviewHeight = (int)(screenHeight * SURFACE_VIEW_HEIGHT_RATIO);

        // set the height of the relative layout containing the texture view
        mCameraPreviewLayout = (RelativeLayout)findViewById(R.id.medias_picker_camera_preview_layout);
        ViewGroup.LayoutParams previewLayoutParams = mCameraPreviewLayout.getLayoutParams();
        previewLayoutParams.height = mCameraPreviewHeight;
        mCameraPreviewLayout.setLayoutParams(previewLayoutParams);

        // set the height of the layout including the texture view and the gallery (total sum > screen height to allow scrolling)
        mPreviewAndGalleryLayout = (RelativeLayout)findViewById(R.id.medias_picker_preview_gallery_layout);
        computePreviewAndGalleryHeight();
    }

    /**
     * Compute the height of the view containing the texture and the table layout.
     * This height is the sum of mCameraPreviewHeight + gallery height.
     * The gallery height depends of the number of the gallery rows {@link #getGalleryRowsCount()}).
     */
    private void computePreviewAndGalleryHeight() {
        int galleryRowsCount = getGalleryRowsCount();

        if(null != mPreviewAndGalleryLayout) {
            ViewGroup.LayoutParams previewAndGalleryLayoutParams = mPreviewAndGalleryLayout.getLayoutParams();
            int galleryHeight = (galleryRowsCount * mScreenWidth / GALLERY_COLUMN_COUNT);
            previewAndGalleryLayoutParams.height = mCameraPreviewHeight + galleryHeight;
            mPreviewAndGalleryLayout.setLayoutParams(previewAndGalleryLayoutParams);
        }
        else
            Log.w(LOG_TAG, "## computePreviewAndGalleryHeight(): GalleryTable height not set");
    }

    /**
     * Exit activity handler.
     * @param aView view
     */
    public void onExitButton(@SuppressWarnings("UnusedParameters") View aView) {
        finish();
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

        // cancel the camera use
        // to avoid locking it
        if (null != mCamera) {
            mCamera.stopPreview();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // update the gallery height, to follow
        // the content of the device gallery
        computePreviewAndGalleryHeight();

        // update gallery content
        refreshRecentsMediasList();

        startCameraPreview();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // save camera UI configuration
        outState.putBoolean(KEY_EXTRA_IS_TAKEN_IMAGE_DISPLAYED, mIsTakenImageDisplayed);
        outState.putInt(KEY_EXTRA_TAKEN_IMAGE_ORIGIN, mTakenImageOrigin);
        outState.putInt(KEY_EXTRA_CAMERA_SIDE, mCameraId);

        // save image preview that may be currently displayed:
        // -camera flow
        outState.putString(KEY_EXTRA_TAKEN_IMAGE_CAMERA_URL, mShootedPicturePath);
        // -gallery flow
        Uri uriImage = (Uri) mImagePreviewImageView.getTag();
        outState.putParcelable(KEY_EXTRA_TAKEN_IMAGE_GALLERY_URI, uriImage);
    }

    private boolean restoreInstanceState(Bundle savedInstanceState) {
        boolean isRestoredInstance = false;
        if(null != savedInstanceState){
            isRestoredInstance = true;
            mIsTakenImageDisplayed = savedInstanceState.getBoolean(KEY_EXTRA_IS_TAKEN_IMAGE_DISPLAYED);
            mShootedPicturePath = savedInstanceState.getString(KEY_EXTRA_TAKEN_IMAGE_CAMERA_URL);
            mTakenImageOrigin = savedInstanceState.getInt(KEY_EXTRA_TAKEN_IMAGE_ORIGIN);

            // restore gallery image preview (the image can be saved from the preview even after rotation)
            Uri uriImage = savedInstanceState.getParcelable(KEY_EXTRA_TAKEN_IMAGE_GALLERY_URI);
            mImagePreviewImageView.setTag(uriImage);

            // display a preview image?
            if (mIsTakenImageDisplayed) {
                Bitmap savedBitmap = VectorApp.getSavedPickerImagePreview();
                if (null != savedBitmap) {
                    // image preview from camera only
                    mImagePreviewImageView.setImageBitmap(savedBitmap);
                } else {
                    // image preview from gallery or camera (mShootedPicturePath)
                    displayImagePreview(mShootedPicturePath, uriImage, mTakenImageOrigin);
                }
            }

            // restore UI display
            updateUiConfiguration(mIsTakenImageDisplayed, mTakenImageOrigin);

            // general data to be restored
            mCameraId = savedInstanceState.getInt(KEY_EXTRA_CAMERA_SIDE);
        }
        return isRestoredInstance;
    }

    /**
     * Result handler associated to {@link #openFileExplorer()} request.
     * This method returns the selected image to the calling activity.
     *
     * @param requestCode request ID
     * @param resultCode operation status
     * @param data data passed from the called activity
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

    /**
     * Populate mMediaStoreImagesList with the images retrieved from the MediaStore.
     * Max number of retrieved images is set to GALLERY_TABLE_ITEM_SIZE.
     */
    private void addImagesThumbnails() {
        final String[] projection = {MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.DATE_TAKEN, MediaStore.Images.ImageColumns.MIME_TYPE};
        Cursor thumbnailsCursor = null;

        try {
            thumbnailsCursor = this.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, // Which columns to return
                    null,       // Return all image files
                    null,
                    MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC LIMIT "+ GALLERY_TABLE_ITEM_SIZE);
        } catch (Exception e) {
            Log.e(LOG_TAG, "addImagesThumbnails" + e.getLocalizedMessage());
        }

        if (null != thumbnailsCursor) {
            int timeIndex = thumbnailsCursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN);
            int idIndex = thumbnailsCursor.getColumnIndex(MediaStore.Images.ImageColumns._ID);
            int mimeTypeIndex = thumbnailsCursor.getColumnIndex(MediaStore.Images.ImageColumns.MIME_TYPE);

            if (thumbnailsCursor.moveToFirst()) {
                do {
                    try {
                        RecentMedia recentMedia = new RecentMedia();
                        recentMedia.mIsVideo = false;

                        String id = thumbnailsCursor.getString(idIndex);
                        String dateAsString = thumbnailsCursor.getString(timeIndex);
                        recentMedia.mMimeType = thumbnailsCursor.getString(mimeTypeIndex);
                        recentMedia.mCreationTime = Long.parseLong(dateAsString);

                        recentMedia.mThumbnail = MediaStore.Images.Thumbnails.getThumbnail(this.getContentResolver(), Long.parseLong(id), MediaStore.Images.Thumbnails.MINI_KIND, null);
                        recentMedia.mFileUri = Uri.parse(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString() + "/" + id);

                        int rotationAngle = ImageUtils.getRotationAngleForBitmap(VectorMediasPickerActivity.this, recentMedia.mFileUri);

                        if (0 != rotationAngle) {
                            android.graphics.Matrix bitmapMatrix = new android.graphics.Matrix();
                            bitmapMatrix.postRotate(rotationAngle);
                            recentMedia.mThumbnail = Bitmap.createBitmap(recentMedia.mThumbnail, 0, 0, recentMedia.mThumbnail.getWidth(), recentMedia.mThumbnail.getHeight(), bitmapMatrix, false);
                        }

                        // Note: getThumbnailUriFromMediaStorage() can return null for non jpeg images (ie png).
                        // We could then use the bitmap(mThumbnail)) that is never null, but with no rotation applied
                        mMediaStoreImagesList.add(recentMedia);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## addImagesThumbnails(): Msg=" + e.getMessage());
                    }
                } while (thumbnailsCursor.moveToNext());
            }
            thumbnailsCursor.close();
        }

        Log.d(LOG_TAG, "## addImagesThumbnails(): Added count=" + mMediaStoreImagesList.size());
    }

    private int getMediaStoreImageCount(){
        int retValue = 0;
        Cursor thumbnailsCursor = null;

        try {
            thumbnailsCursor = this.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    null, // no projection
                    null,
                    null,
                    MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC LIMIT "+ GALLERY_TABLE_ITEM_SIZE);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getMediaStoreImageCount() Exception Msg=" + e.getLocalizedMessage());
        }

        if (null != thumbnailsCursor) {
            retValue = thumbnailsCursor.getCount();
            thumbnailsCursor.close();
        }

        return retValue;
    }

    private int getGalleryRowsCount() {
        int rowsCountRetVal;

        mGalleryImageCount = getMediaStoreImageCount();
        if((0==mGalleryImageCount) || (0 != (mGalleryImageCount%GALLERY_COLUMN_COUNT))) {
            rowsCountRetVal = (mGalleryImageCount/GALLERY_COLUMN_COUNT) +1;
        } else {
            rowsCountRetVal = mGalleryImageCount/GALLERY_COLUMN_COUNT;
            mGalleryImageCount--; // save one cell for the folder icon
        }

        return rowsCountRetVal;
    }

    /**
     * Populate the gallery view with the image/video contents.
     */
    private void refreshRecentsMediasList() {
        // start the pregress bar and disable the take button
        final RelativeLayout progressBar = (RelativeLayout)(findViewById(R.id.medias_preview_progress_bar_layout));
        progressBar.setVisibility(View.VISIBLE);
        mTakeImageView.setEnabled(false);
        mTakeImageView.setAlpha(CommonActivityUtils.UTILS_OPACITY_HALF);

        mMediaStoreImagesList.clear();

        // run away from the UI thread
        mFileHandler.post(new Runnable() {
            @Override
            public void run() {
                // populate the image thumbnails from multimedia store
                addImagesThumbnails();

                Collections.sort(mMediaStoreImagesList, new Comparator<RecentMedia>() {
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
                        buildGalleryImageTableLayout();
                        progressBar.setVisibility(View.GONE);
                        mTakeImageView.setEnabled(true);
                        mTakeImageView.setAlpha(CommonActivityUtils.UTILS_OPACITY_NONE);
                    }
                });
            }
        });
    }

    /**
     * Build the image gallery widget programmatically.
     */
    private void buildGalleryImageTableLayout() {
        final int CELL_MARGIN = 2;
        TableRow tableRow = null;
        RecentMediaLayout recentImageView;
        int tableLayoutWidth;
        int cellWidth;
        int cellHeight;
        int itemIndex;
        ImageView.ScaleType scaleType;
        TableRow.LayoutParams rawLayoutParams;
        TableLayout.LayoutParams tableLayoutParams = new TableLayout.LayoutParams();

        if(null != mGalleryTableLayout) {
            mGalleryTableLayout.removeAllViews();
            mGalleryTableLayout.setBackgroundColor(Color.WHITE);

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            tableLayoutWidth = metrics.widthPixels;

            // raw layout configuration
            cellWidth = (tableLayoutWidth -(GALLERY_COLUMN_COUNT * CELL_MARGIN)) / GALLERY_COLUMN_COUNT;
            cellHeight = cellWidth;
            if(0 == tableLayoutWidth) {
                // fall back
                scaleType = ImageView.ScaleType.FIT_XY;
                rawLayoutParams = new TableRow.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            } else {
                scaleType = ImageView.ScaleType.FIT_CENTER;
                rawLayoutParams = new TableRow.LayoutParams(cellWidth, cellHeight);
            }
            rawLayoutParams.setMargins(CELL_MARGIN, 0, CELL_MARGIN, 0);
            tableLayoutParams.setMargins(CELL_MARGIN, CELL_MARGIN, CELL_MARGIN, CELL_MARGIN);


            RecentMedia recentMedia;
            // loop to produce full raws filled in, with an icon folder in last cell
            for(itemIndex=0; itemIndex<mGalleryImageCount; itemIndex++) {
                try {
                    recentMedia = mMediaStoreImagesList.get(itemIndex);
                } catch (IndexOutOfBoundsException e) {
                    recentMedia = null;
                }

                // detect raw is complete
                if (0 == (itemIndex % GALLERY_COLUMN_COUNT)) {
                    if (null != tableRow) {
                        mGalleryTableLayout.addView(tableRow, tableLayoutParams);
                    }
                    tableRow = new TableRow(this);
                }

                // build the content layout for each cell
                if(null != recentMedia) {
                    recentImageView = new RecentMediaLayout(this);

                    if (null != recentMedia.mThumbnail) {
                        recentImageView.setThumbnail(recentMedia.mThumbnail);
                    } else {
                        recentImageView.setThumbnailByUri(recentMedia.mFileUri);
                    }

                    recentImageView.setBackgroundColor(Color.BLACK);
                    recentImageView.setThumbnailScaleType(scaleType);
                    final RecentMedia finalRecentMedia = recentMedia;
                    recentImageView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            onClickGalleryImage(finalRecentMedia);
                        }
                    });

                    // set image logo: gif, image or video
                    recentImageView.enableGifLogoImage(MIME_TYPE_IMAGE_GIF.equals(recentMedia.mMimeType));
                    recentImageView.enableMediaTypeLogoImage(!MIME_TYPE_IMAGE_GIF.equals(recentMedia.mMimeType));
                    recentImageView.setIsVideo(recentMedia.mMimeType.contains(MIME_TYPE_IMAGE));

                    if(null != tableRow)
                        tableRow.addView(recentImageView, rawLayoutParams);
                }
            }

            // add the icon folder in last cell
            recentImageView = new RecentMediaLayout(this);
            recentImageView.setThumbnailScaleType(scaleType);
            recentImageView.setThumbnailByResource(R.drawable.ic_material_folder_green_vector);
            recentImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openFileExplorer();
                }
            });

            if(0 == itemIndex) {
                tableRow = new TableRow(this);
            }

            if(null != tableRow)
                tableRow.addView(recentImageView, rawLayoutParams);

            // do not forget to add last row
            if (null != tableRow)
                mGalleryTableLayout.addView(tableRow, tableLayoutParams);

        } else {
            Log.w(LOG_TAG, "## buildGalleryImageTableLayout(): failure - TableLayout widget missing");
        }
    }

    private void onClickGalleryImage(final RecentMedia aMediaItem){
        mCamera.stopPreview();

        // add the selected image to be returned by the activity
        mSelectedGalleryItemsList.add(aMediaItem);

        // display the image as preview
        if (null != aMediaItem.mThumbnail) {
            updateUiConfiguration(UI_SHOW_TAKEN_IMAGE, IMAGE_ORIGIN_GALLERY);
            mImagePreviewImageView.setImageBitmap(aMediaItem.mThumbnail);
            // save bitmap to speed up UI restore (life cycle)
            VectorApp.setSavedCameraImagePreview(aMediaItem.mThumbnail);
        } else if(null != aMediaItem.mFileUri) {
            // fall back in case bitmap is not available (unlikely..)
            displayImagePreview(null, aMediaItem.mFileUri, IMAGE_ORIGIN_GALLERY);
        } else {
            Log.e(LOG_TAG, "## onClickGalleryImage(): no image to display");
        }

        // save the uri to be accessible for life cycle management
        mImagePreviewImageView.setTag(aMediaItem.mFileUri);
    }

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
                    String fileName = getSavedImageName();

                    // remove any previously saved image
                    if (!TextUtils.isEmpty(fileName)) {
                        dstFile = new File(getCacheDir().getAbsolutePath(), fileName);
                        if (dstFile.exists()) {
                            dstFile.delete();
                        }
                    }

                    // get new name
                    fileName = buildNewImageName();
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

                        mShootedPicturePath = dstFile.getAbsolutePath();
                        displayImagePreview(mShootedPicturePath, null, IMAGE_ORIGIN_CAMERA);

                        // force to stop preview:
                        // some devices do not stop preview after the picture was taken (ie. G6 edge)
                        mCamera.stopPreview();

                        Log.d(LOG_TAG, "onPictureTaken processed");

                    } catch (Exception e) {
                        Toast.makeText(VectorMediasPickerActivity.this, "Exception onPictureTaken(): " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    } finally {

                        // Close resources
                        try {
                            inputStream.close();

                            if (outputStream != null) {
                                outputStream.close();
                            }

                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## onPictureTaken(): EXCEPTION Msg=" + e.getMessage());
                        }
                    }
                }
            });

        } catch (Exception e) {
            Log.e(LOG_TAG, "## takePicture(): EXCEPTION Msg=" + e.getMessage());
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
                Log.e(LOG_TAG, "## autoFocus(): EXCEPTION Msg=" + e.getMessage());

                // take a photo event if the autofocus fails
                playShutterSound();
                takePhoto();
            }
        }
    }


    private String buildNewImageName(){
        String nameRetValue;

        // build name based on the date
        //String fileSufixTime = DateFormat.getDateTimeInstance().format(new Date());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_hhmmss") ;
        String fileSufixTime = dateFormat.format(new Date());
        //fileSufixTime += "_"+ (SystemClock.uptimeMillis()/1000);
        nameRetValue = "VectorImage_"+fileSufixTime+".jpg";

        // save new name in preference
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_PREFERENCE_CAMERA_IMAGE_NAME, nameRetValue);
        editor.commit();

        return nameRetValue;
    }

    private String getSavedImageName(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return preferences.getString(KEY_PREFERENCE_CAMERA_IMAGE_NAME, null);
    }

    /**
     * Create a thumbnail bitmap from an image URL if there is some exif metadata which implies to rotate
     * the image. This method is used to process the image taken by the from the camera.
     * @param aImageUrl the image url
     * @return a thumbnail if the exif metadata implies to rotate the image.
     */
    private Bitmap createPhotoThumbnail(final String aImageUrl) {
        Bitmap bitmapRetValue = null;
        final int MAX_SIZE = 1024, SAMPLE_SIZE = 0, QUALITY = 100;

        // sanity check
        if (null != aImageUrl) {
            Uri imageUri = Uri.fromFile(new File(aImageUrl));
            int rotationAngle = ImageUtils.getRotationAngleForBitmap(VectorMediasPickerActivity.this, imageUri);

            // the exif metadata implies a rotation
            if (0 != rotationAngle) {
                // create a thumbnail

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                options.outWidth = -1;
                options.outHeight = -1;

                try {
                    final String filename = imageUri.getPath();
                    FileInputStream imageStream = new FileInputStream(new File(filename));

                    // create a thumbnail
                    InputStream stream = ImageUtils.resizeImage(imageStream, MAX_SIZE, SAMPLE_SIZE, QUALITY);
                    imageStream.close();

                    Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);

                    // apply a rotation
                    android.graphics.Matrix bitmapMatrix = new android.graphics.Matrix();
                    bitmapMatrix.postRotate(rotationAngle);
                    bitmapRetValue = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), bitmapMatrix, false);

                    System.gc();

                } catch (OutOfMemoryError e) {
                    Log.e(LOG_TAG, "## createPhotoThumbnail : out of memory");
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## createPhotoThumbnail() Exception Msg=" + e.getMessage());
                }
            }
        }

        return bitmapRetValue;
    }

    /**
     * Display the image preview.
     *
     * @param aCameraImageUrl image from camera
     * @param aGalleryImageUri image ref as an Uri
     * @param aOrigin CAMERA or GALLERY
     */
    private void displayImagePreview(final String aCameraImageUrl, final Uri aGalleryImageUri, final int aOrigin){
        final RelativeLayout progressBar = (RelativeLayout)(findViewById(R.id.medias_preview_progress_bar_layout));
        progressBar.setVisibility(View.VISIBLE);
        mTakeImageView.setEnabled(false);

        Bitmap newBitmap = null;
        Uri defaultUri;

        if (IMAGE_ORIGIN_CAMERA == aOrigin) {
            newBitmap = mCameraTextureView.getBitmap();
            if (null == newBitmap) {
                newBitmap = createPhotoThumbnail(aCameraImageUrl);
            }
            defaultUri = Uri.fromFile(new File(aCameraImageUrl));
        } else {
            // in gallery
            defaultUri = aGalleryImageUri;
        }

        // save bitmap to speed up UI restore (life cycle)
        VectorApp.setSavedCameraImagePreview(newBitmap);

        // update the UI part
        if (null != newBitmap) {// from camera
            mImagePreviewImageView.setImageBitmap(newBitmap);
        } else {
            if(null != defaultUri) {
                mImagePreviewImageView.setImageURI(defaultUri);
            }
        }

        mTakeImageView.setEnabled(true);
        updateUiConfiguration(UI_SHOW_TAKEN_IMAGE, aOrigin);
        progressBar.setVisibility(View.GONE);
    }

    /**
     * Update the UI according to camera action. Two UIs are displayed:
     * the camera real time preview (default configuration) or the taken picture.
     * (the taken picture comes from the camera or from the gallery)
     *
     * When the taken image is displayed, only two buttons are displayed: "attach"
     * the current image or "re take"(cancel) another image with the camera.
     * We also have to distinguish the origin of the taken image: from the camera
     * or from the gallery.
     *
     * @param aIsTakenImageDisplayed true to display the taken image, false to show the camera preview
     * @param aImageOrigin IMAGE_ORIGIN_CAMERA or IMAGE_ORIGIN_GALLERY
     */
    private void updateUiConfiguration(boolean aIsTakenImageDisplayed, int aImageOrigin){
        // save current configuration for lifecyle management
        mIsTakenImageDisplayed = aIsTakenImageDisplayed;
        mTakenImageOrigin = aImageOrigin;

        if (!aIsTakenImageDisplayed) {
            // clear the selected image from the gallery (if any)
            mSelectedGalleryItemsList.clear();
        }

        if (aIsTakenImageDisplayed) {
            mImagePreviewLayout.setVisibility(View.VISIBLE);
            mPreviewScrollView.setVisibility(View.GONE);
        }
        else {
            // the default UI: hide gallery preview, show the surface view
            mPreviewScrollView.setVisibility(View.VISIBLE);
            mImagePreviewLayout.setVisibility(View.GONE);
        }
    }

    /**
     * Start the camera preview
     */
    private void startCameraPreview() {
        try {
            if (null != mCamera) {
                mCamera.startPreview();
            }
        } catch (Exception ex){
            Log.w(LOG_TAG,"## startCameraPreview(): Exception Msg="+ ex.getMessage());
        }
    }

    /**
     * Cancel the current image preview, and setup the UI to
     * start a new image capture.
     */
    private void cancelTakeImage() {
        mShootedPicturePath = null;
        mSelectedGalleryItemsList.clear();
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
     * Returns the thumbnail path of shot image.
     * @param picturePath the image path
     * @return the thumbnail image path.
     */
    public static String getThumbnailPath(String picturePath) {
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
            if (null != mShootedPicturePath) {
                try {
                    Bitmap previewBitmap = VectorApp.getSavedPickerImagePreview();
                    String thumbnailPath = getThumbnailPath(mShootedPicturePath);

                    File file = new File(thumbnailPath);
                    FileOutputStream outStream = new FileOutputStream(file);
                    previewBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outStream);
                    outStream.flush();
                    outStream.close();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "attachImageFromCamera fails to create thumbnail file");
                }

                Uri uri = Uri.fromFile(new File(mShootedPicturePath));

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

    private void openFileExplorer() {
        try {
            Intent fileIntent = new Intent(Intent.ACTION_PICK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
            }
            // no mime type filter to allow any kind of content
            fileIntent.setType(CommonActivityUtils.MIME_TYPE_IMAGE_ALL);
            startActivityForResult(fileIntent, REQUEST_MEDIAS);
        } catch(Exception e) {
            Toast.makeText(VectorMediasPickerActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Return the taken image from the gallery to the calling activity.
     * This method returns to the calling activity.
     */
    @SuppressLint("NewApi")
    private void attachImageFrommGallery() {

        Bundle conData = new Bundle();
        Intent intent = new Intent();

        if ((mSelectedGalleryItemsList.size() == 1) || (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)) {
            // provide the Uri
            intent.setData(mSelectedGalleryItemsList.get(0).mFileUri);
        } else if (mSelectedGalleryItemsList.size() > 0) {
            ClipData.Item firstUri = new ClipData.Item(null, null, null, mSelectedGalleryItemsList.get(0).mFileUri);
            String[] mimeType = { "*/*" };
            ClipData clipData = new ClipData("", mimeType, firstUri);

            for(int index = 1; index < mSelectedGalleryItemsList.size(); index++) {
                ClipData.Item item = new ClipData.Item(null, null, null, mSelectedGalleryItemsList.get(index).mFileUri);
                clipData.addItem(item);
            }
            intent.setClipData(clipData);
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
     * Switch camera (front <-> back)
     */
    private void onSwitchCamera() {
        // can only switch if the device has more than two camera
        if (Camera.getNumberOfCameras() >= 2) {

            // stop camera
            if (null != mCameraTextureView) {
                mCamera.stopPreview();
            }
            mCamera.release();

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
                    Log.e(LOG_TAG, "## onSwitchCamera(): setPreviewTexture EXCEPTION Msg=" + e.getMessage());
                }

                mCamera.startPreview();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## onSwitchCamera(): cannot init the other camera");
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
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(mCameraId, info);

        int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break; // portrait
            case Surface.ROTATION_90: degrees = 90; break; // landscape
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break; // landscape
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

        // apply the rotation
        params.setRotation(imageRotation);

        // set the best quality
        List<Camera.Size> supportedSizes = params.getSupportedPictureSizes();
        if (supportedSizes.size() > 0) {

            // search the highest image quality
            // they are not always sorted in the same order (sometimes it is asc sort ..)
            Camera.Size maxSizePicture = supportedSizes.get(0);
            long mult = maxSizePicture.width * maxSizePicture.height;

            for(int i = 1; i < supportedSizes.size(); i++) {
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
            Log.e(LOG_TAG, "## initCameraSettings(): set size fails EXCEPTION Msg=" + e.getMessage());
        }


        // set auto focus
        try {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            mCamera.setParameters(params);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## initCameraSettings(): set auto focus fails EXCEPTION Msg=" + e.getMessage());
        }

        // set jpeg quality
        try {
            params.setPictureFormat(ImageFormat.JPEG);
            params.setJpegQuality(JPEG_QUALITY_MAX);
            mCamera.setParameters(params);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## initCameraSettings(): set jpeg quality fails EXCEPTION Msg=" + e.getMessage());
        }
    }

    private void playShutterSound() {
        MediaActionSound sound = new MediaActionSound();
        sound.play(MediaActionSound.SHUTTER_CLICK);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        try {
            mCamera = Camera.open(mCameraId);
        } catch (Exception e) {
            Log.e(LOG_TAG,"Cannot open the camera " + mCameraId);
        }

        // fall back: the camera initialisation failed
        if (null == mCamera) {
            // assume that only one camera can be used.
            mSwitchCameraImageView.setVisibility(View.GONE);
            try {
                mCamera = Camera.open((Camera.CameraInfo.CAMERA_FACING_BACK == mCameraId) ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK);
            }  catch (Exception e) {
                Log.e(LOG_TAG,"Cannot open the camera " + mCameraId);
            }
        }

        // cannot start the cam
        if (null == mCamera) {
            Log.w(LOG_TAG,"## onSurfaceTextureAvailable() camera creation failed");
            return;
        }

        try {
            mSurfaceTexture = surface;
            mCamera.setPreviewTexture(surface);
            initCameraSettings();

            Camera.Size previewSize = mCamera.getParameters().getPreviewSize();

            //  Valid values are 0, 90, 180, and 270 (0 = landscape)
            if ((mCameraOrientation == 90) || (mCameraOrientation == 270)) {
                int tmp = previewSize.width;
                previewSize.width = previewSize.height;
                previewSize.height = tmp;
            }

            // check that the aspect ratio is kept
            int sourceRatio = previewSize.height * 100 / previewSize.width;
            int dstRatio = height * 100 / width;

            // the camera preview size must fit the size provided by the surface texture
            if (sourceRatio != dstRatio) {
                int newWidth;
                int newHeight;

                newHeight = height;
                newWidth = (int) (((float) newHeight) * previewSize.width / previewSize.height);

                if (newWidth > width) {
                    newWidth = width;
                    newHeight = (int) (((float) newWidth) * previewSize.height / previewSize.width);
                }

                // apply the size provided by the texture to the texture layout
                ViewGroup.LayoutParams layout = mCameraTextureView.getLayoutParams();
                layout.width = newWidth;
                layout.height = newHeight;
                mCameraTextureView.setLayoutParams(layout);

                if (layout.height < mCameraPreviewHeight) {
                    mCameraPreviewHeight = layout.height;

                    // set the height of the relative layout containing the texture view
                    if(null != mCameraPreviewLayout) {
                        RelativeLayout.LayoutParams previewLayoutParams = (RelativeLayout.LayoutParams)mCameraPreviewLayout.getLayoutParams();
                        previewLayoutParams.height = mCameraPreviewHeight;
                        mCameraPreviewLayout.setLayoutParams(previewLayoutParams);
                    }

                    // define the gallery height: height of the texture view + height of the gallery (total sum > screen height to allow scrolling)
                    computePreviewAndGalleryHeight();
                }
            }

            mCamera.startPreview();

        } catch (Exception e) {
            if (null != mCamera) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(LOG_TAG, "## onSurfaceTextureSizeChanged(): width="+width+" height="+height);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mCamera.stopPreview();
        mCamera.release();
        mSurfaceTexture = null;
        mCamera = null;
        return true;
    }

    // *********************************************************************************************
}
