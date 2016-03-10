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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import im.vector.R;
import im.vector.view.RecentMediaLayout;

import android.hardware.Camera;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
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

    // public keys
    public static final String EXTRA_SINGLE_IMAGE_MODE = "EXTRA_SINGLE_IMAGE_MODE";

    // internal keys
    private static final String KEY_EXTRA_IS_TAKEN_IMAGE_DISPLAYED = "IS_TAKEN_IMAGE_DISPLAYED";
    private static final String KEY_EXTRA_TAKEN_IMAGE_ORIGIN = "TAKEN_IMAGE_ORIGIN";
    private static final String KEY_EXTRA_TAKEN_IMAGE_URI = "TAKEN_IMAGE_URI";

    private final int IMAGE_ORIGIN_CAMERA = 1;
    private final int IMAGE_ORIGIN_GALLERY = 2;
    private final boolean UI_SHOW_TAKEN_IMAGE = true;
    private final boolean UI_SHOW_CAMERA_PREVIEW = false;
    private final int GALLERY_COLUMN_COUNT = 4;
    private final int GALLERY_RAW_COUNT = 3;
    private final int GALLERY_TABLE_ITEM_SIZE = (GALLERY_COLUMN_COUNT * GALLERY_RAW_COUNT);

    /**
     * define a recent media
     */
    private class RecentMedia {
        public Uri mFileUri;
        public long mCreationTime;
        public Bitmap mThumbnail;
        public Boolean mIsVideo;
        public RecentMediaLayout mRecentMediaLayout;
    }

    // recents medias list
    private final ArrayList<RecentMedia> mRecentsMedias = new ArrayList<RecentMedia>();
    private final ArrayList<RecentMedia> mSelectedRecents = new ArrayList<RecentMedia>();

    // camera object
    private Camera mCamera;
    private int mCameraId;
    private int mCameraOrientation = 0;

    // graphical items
    private ImageView mSwitchCameraImageView;
    private ImageView mExitActivityImageView;


    // camera preview and gallery selection layout
    private View mPreviewScrollView;
    private ImageView mTakeImageView;
    private SurfaceHolder mCameraSurfaceHolder;
    private SurfaceView mCameraSurfaceView;
    private TableLayout mGalleryTableLayout;

    private View mShootedImagePreviewLayout;
    private ImageView mGalleryImagePreviewImageView;

    // lifecycle management variable
    private boolean mIsTakenImageDisplayed;
    private int mTakenImageOrigin;

    private String mShootedPicturePath;
    private Boolean mIsPreviewStarted = false;

    /**
     * The recent requests are performed in a dedicated thread
     */
    private HandlerThread mHandlerThread;
    private android.os.Handler mFileHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_medias_picker);
        mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

        // camera preview
        mPreviewScrollView = findViewById(R.id.medias_picker_scrollView);
        mSwitchCameraImageView = (ImageView) findViewById(R.id.medias_picker_switch_camera);
        mExitActivityImageView = (ImageView) findViewById(R.id.medias_picker_exit);
        mCameraSurfaceView = (SurfaceView) findViewById(R.id.medias_picker_surface_view);

        // image preview
        mShootedImagePreviewLayout = findViewById(R.id.medias_picker_preview);
        mGalleryImagePreviewImageView = (ImageView) findViewById(R.id.medias_picker_gallery_preview_image_view);
        mTakeImageView = (ImageView) findViewById(R.id.medias_picker_camera_button);
        mGalleryTableLayout = (TableLayout)findViewById(R.id.gallery_table_layout);

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


        findViewById(R.id.medias_picker_cancel_take_picture_imageview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VectorMediasPickerActivity.this.reTakeImage();
            }
        });

        findViewById(R.id.medias_picker_attach_take_picture_imageview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (0 == mSelectedRecents.size())
                    VectorMediasPickerActivity.this.attachImageFrom(IMAGE_ORIGIN_CAMERA);
                else
                    VectorMediasPickerActivity.this.attachImageFrom(IMAGE_ORIGIN_GALLERY);
            }
        });

        // fix the surfaceView size and its container size
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenHeight = metrics.heightPixels;
        int screenWidth = metrics.widthPixels;

        int cameraPreviewHeight = (screenHeight * 95 / 100);

        RelativeLayout previewLayout = (RelativeLayout)findViewById(R.id.medias_picker_camera_preview_layout);
        ViewGroup.LayoutParams previewLayoutParams = previewLayout.getLayoutParams();
        previewLayoutParams.height = cameraPreviewHeight;
        previewLayout.setLayoutParams(previewLayoutParams);

        RelativeLayout previewAndGalleryLayout = (RelativeLayout)findViewById(R.id.medias_picker_preview_gallery_layout);
        ViewGroup.LayoutParams previewAndGalleryLayoutParams = previewAndGalleryLayout.getLayoutParams();
        previewAndGalleryLayoutParams.height = cameraPreviewHeight + (3 * screenWidth / 4);
        previewAndGalleryLayout.setLayoutParams(previewAndGalleryLayoutParams);


        // setup separate thread for image gallery update
        mHandlerThread = new HandlerThread("VectorMediasPickerActivityThread");
        mHandlerThread.start();
        mFileHandler = new android.os.Handler(mHandlerThread.getLooper());

        // set default UI: camera preview
        updateUiConfiguration(UI_SHOW_CAMERA_PREVIEW, IMAGE_ORIGIN_CAMERA);
    }


    /**
     * mExitActivityImageView handler. The activity is finished.
     * @param aView view
     */
    public void onExitButton(View aView) {
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

    /**
     * Disable user interactivity
     * @param view the view
     */
    private void disableView(View view) {
        view.setEnabled(false);
        view.setAlpha(0.5f);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // cancel the camera use
        // to avoid locking it
        if (null != mCamera) {
            mCamera.stopPreview();
            mIsPreviewStarted = false;
            //manageButtons();
        }
    }

    private int getCarouselItemWidth() {
        /*HorizontalScrollView galleryHorizScrollView = (HorizontalScrollView)findViewById(R.id.medias_picker_recents_scrollview);
        int scrollHOrizWidth = galleryHorizScrollView.getWidth();
        int scrollHOrizHeight= galleryHorizScrollView.getHeight();
        int scrollHOrizWidth2 = galleryHorizScrollView.getLayoutParams().width;
        int scrollHOrizHeight2 = galleryHorizScrollView.getLayoutParams().height;*/

        /*RelativeLayout rootRelativeLayout = (RelativeLayout)findViewById(R.id.layout_root_container);
        int rootWidth0 = rootRelativeLayout.getLayoutParams().width;
        int rootWidth0_ = rootRelativeLayout.getWidth();*/

        ViewGroup rootViewGroup = (ViewGroup) ((ViewGroup) this.findViewById(android.R.id.content)).getChildAt(0);
        int width50 = rootViewGroup.getWidth();
        int width50Bis = rootViewGroup.getLayoutParams().width;

        //int width1 = (findViewById(R.id.relative_layout_main_container1)).getLayoutParams().width;
        //int width2 = mGalleryRecentImagesLayout.getLayoutParams().width;
        //int galleryHeight = mGalleryRecentImagesLayout.getLayoutParams().height;
        //int galleryHeight2 = mGalleryRecentImagesLayout.getHeight();

        return -2;//galleryHeight;
    }

    private void startCameraPreview() {
        // init UI: "take picture" button is displayed and the cancel & attach buttons are hidden
        updateUiConfiguration(UI_SHOW_CAMERA_PREVIEW, IMAGE_ORIGIN_CAMERA);

        // should always be true
        if (null == mCamera) {
            // check if the device has at least camera
            if (Camera.getNumberOfCameras() > 0) {
                mCameraSurfaceView.setVisibility(View.VISIBLE);

                // set the surfaceholder listener
                if (null == mCameraSurfaceHolder) {
                    mCameraSurfaceHolder = mCameraSurfaceView.getHolder();
                    mCameraSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                    mCameraSurfaceHolder.setSizeFromLayout();
                    mCameraSurfaceHolder.addCallback(VectorMediasPickerActivity.this);
                }
            }
        } else {
            mCamera.startPreview();
            //manageButtons();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // update gallery content
        if (0 == mRecentsMedias.size()) {
            refreshRecentsMediasList();
        }

        // should always be true
        if (null == mCamera) {
            startCameraPreview();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Uri uriImage = null;
        String uriString = null;

        // save UI configuration
        outState.putBoolean(KEY_EXTRA_IS_TAKEN_IMAGE_DISPLAYED, mIsTakenImageDisplayed);
        outState.putInt(KEY_EXTRA_TAKEN_IMAGE_ORIGIN, mTakenImageOrigin);

        // save the image reference
        if (mIsTakenImageDisplayed) {
            if (IMAGE_ORIGIN_CAMERA == mTakenImageOrigin) {
                if (null != mShootedPicturePath) {
                    if(null != (uriString = CommonActivityUtils.saveImageIntoGallery(this, new File(mShootedPicturePath))))
                        uriImage = Uri.fromFile(new File(uriString));
                }
            } else { // IMAGE_ORIGIN_GALLERY
                uriImage = (Uri)mGalleryImagePreviewImageView.getTag();
            }

            if(null != uriImage)
                outState.putParcelable(KEY_EXTRA_TAKEN_IMAGE_URI, uriImage);
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle aBundle){

    }

    /**
     * Result handler associated to {@Link #openFileExplorer()} request.
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
                //Bundle conData = new Bundle();
                Intent intent = new Intent();
                intent.setData(data.getData());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    intent.setClipData(data.getClipData());
                }
                //intent.putExtras(conData);
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
                    MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC LIMIT "+ GALLERY_TABLE_ITEM_SIZE);
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
                        recentMedia.mIsVideo = false;

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
     * Populate the gallery view with the image/video contents.
     */
    private void refreshRecentsMediasList() {
        // the last 30 days
        final long maxLifetime = 1000L * 60L * 60L * 24L * 30L; // tt

        //mGalleryImagesListLayout.removeAllViews();
        mRecentsMedias.clear();

        // run away from the UI thread
        mFileHandler.post(new Runnable() {
            @Override
            public void run() {
                // populate the image thumbnails from multimedia store
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
                        buildGalleryImageTableLayout();
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
        ImageView imageView = null;
        int tableLayoutWidth = 0;
        int cellWidth = 0;
        int cellHeight = 0;
        int itemIndex = 0;
        ImageView.ScaleType scaleType = ImageView.ScaleType.FIT_CENTER;
        TableRow.LayoutParams rawLayoutParams = null;
        TableLayout.LayoutParams tableLayoutParams = new TableLayout.LayoutParams();
        //TableRow.LayoutParams rawLayoutParams = new TableRow.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        if(null != mGalleryTableLayout) {
            mGalleryTableLayout.setBackgroundColor(Color.WHITE);
            tableLayoutWidth = mGalleryTableLayout.getWidth();
            //mGalleryTableLayout.setDividerPadding(50);

            // raw layout configuration
            cellWidth = tableLayoutWidth / GALLERY_COLUMN_COUNT;
            cellHeight = cellWidth;
            if(0 == tableLayoutWidth) {
                // fall back
                scaleType = ImageView.ScaleType.FIT_XY;
                rawLayoutParams = new TableRow.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            } else {
                scaleType = ImageView.ScaleType.FIT_CENTER;
                rawLayoutParams = new TableRow.LayoutParams(cellWidth, cellHeight);
            }
            rawLayoutParams.setMargins(CELL_MARGIN, CELL_MARGIN, CELL_MARGIN, CELL_MARGIN);
            rawLayoutParams.weight = 1;

            for (final RecentMedia recentMedia : mRecentsMedias) {
                // detect raw is complete
                if (0 == (itemIndex % GALLERY_COLUMN_COUNT)) {
                    if (null != tableRow) {
                        mGalleryTableLayout.addView(tableRow/*, tableLayoutParams*/);
                    }
                    tableRow = new TableRow(this);
                }

                // add first cell: set the folder icon to allow image browsing
                if (0 == itemIndex) {
                    ImageView iconFolderImageView = new ImageView(this);
                    iconFolderImageView.setImageResource(R.drawable.ic_material_folder_green_vector);
                    iconFolderImageView.setScaleType(scaleType);
                    iconFolderImageView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openFileExplorer();
                        }
                    });

                    tableRow.addView(iconFolderImageView, rawLayoutParams);
                    itemIndex++;
                }

                // build the image image view in the raw
                imageView = new ImageView(this);
                imageView.setImageBitmap(recentMedia.mThumbnail);
                imageView.setScaleType(scaleType);
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onClickGalleryImage(recentMedia);
                    }
                });
                tableRow.addView(imageView, rawLayoutParams);

                if (++itemIndex >= GALLERY_TABLE_ITEM_SIZE) {
                    break;
                }
            }

            // do not forget to add last row
            if (null != tableRow) {
                mGalleryTableLayout.addView(tableRow/*, tableLayoutParams*/);
            }

            mGalleryTableLayout.startAnimation(AnimationUtils.loadAnimation(getApplicationContext(), android.R.anim.slide_in_left));
        } else {
            Log.w(LOG_TAG, "## buildGalleryImageTableLayout(): failure - TableLayout widget missing");
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
        //VectorMediasPickerActivity.this.manageButtons();

        // add the selected image to be returned by the activity
        mSelectedRecents.add(aMediaItem);

        // display the image as preview
        if(null != aMediaItem.mThumbnail) {
            mGalleryImagePreviewImageView.setImageURI(aMediaItem.mFileUri);
            mGalleryImagePreviewImageView.setTag(aMediaItem.mFileUri);
        }
    }

    private void buildGalleryImageWithSelectionLayout() {
        int itemWidth = getCarouselItemWidth();

        for (RecentMedia recentMedia : mRecentsMedias) {
            final RecentMediaLayout recentMediaLayout = new RecentMediaLayout(VectorMediasPickerActivity.this);

            recentMediaLayout.setThumbnail(recentMedia.mThumbnail);
            recentMediaLayout.setIsVideo(recentMedia.mIsVideo);

            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(itemWidth, ViewGroup.LayoutParams.MATCH_PARENT);
            recentMediaLayout.setLayoutParams(params);

            recentMedia.mRecentMediaLayout = recentMediaLayout;
            //mGalleryImagesListLayout.addView(recentMediaLayout, params);

            final RecentMedia frecentMedia = recentMedia;

            recentMediaLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // unselect it ?
                    if (recentMediaLayout.isSelected()) {
                        mSelectedRecents.remove(frecentMedia);
                    } else {
                        // single image mode : disable any previously selected image
                        if (((Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)) && (mSelectedRecents.size() > 0)) {
                            mSelectedRecents.get(0).mRecentMediaLayout.setIsSelected(false);
                            mSelectedRecents.clear();
                        }

                        mSelectedRecents.add(frecentMedia);
                    }

                    // set the new selection display as the opposite of the previous selection state
                    recentMediaLayout.setIsSelected(!recentMediaLayout.isSelected());
                    //VectorMediasPickerActivity.this.manageButtons();
                }
            });
        }
    }

    /**
     * Take a picture of the current preview
     */
    private void takeImage() {
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
                        //manageButtons();

                        mGalleryImagePreviewImageView.setImageURI(Uri.fromFile(new File(mShootedPicturePath)));


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
                            if (inputStream != null)
                                inputStream.close();

                            if (outputStream != null)
                                outputStream.close();
                        } catch (Exception e) {
                            Log.e(LOG_TAG,"## takeImage(): EXCEPTION Msg="+e.getMessage());
                        }
                    }
                }
            });
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
        // save current configuration for lifecyle management
        mIsTakenImageDisplayed = aIsTakenImageDisplayed;
        mTakenImageOrigin = aImageOrigin;

        // if more than two cameras are available, just disable the "switch camera" capability
        if (2 > Camera.getNumberOfCameras()) {
            disableView(mSwitchCameraImageView);
        }

        if (false == aIsTakenImageDisplayed) {
            // clear the selected image from the gallery (if any)
            mSelectedRecents.clear();
        }

        if (aIsTakenImageDisplayed) {
            mShootedImagePreviewLayout.setVisibility(View.VISIBLE);
            mPreviewScrollView.setVisibility(View.GONE);
        }
        else {
            // the default UI: hide gallery preview, show the surface view
            mPreviewScrollView.setVisibility(View.VISIBLE);
            mShootedImagePreviewLayout.setVisibility(View.GONE);
        }
    }

    /**
     * Cancel the current image preview, and setup the UI to
     * start a new image capture.
     */
    private void reTakeImage() {
        mShootedPicturePath = null;
        //mRecordedVideoPath = null;
        //manageButtons();

        startCameraPreview();
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
     * Return the taken image from the camera to the calling activity.
     * This method returns to the calling activity.
     */
    private void attachImageFromCamera() {

        try {
            String uriString = null;

            if (null != mShootedPicturePath) {
                uriString = CommonActivityUtils.saveImageIntoGallery(this, new File(mShootedPicturePath));
            } /*else {
                uriString = CommonActivityUtils.saveIntoMovies(this, new File(mRecordedVideoPath));
            }*/

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
    private void attachImageFrommGallery() {

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
    private void switchCamera() {
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
            Log.e(LOG_TAG,"## switchCamera(): setPreviewDisplay EXCEPTION Msg="+e.getMessage());
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

        // fall back: the camera initialisation failed
        if (null == mCamera) {
            mCamera = Camera.open((Camera.CameraInfo.CAMERA_FACING_BACK == mCameraId) ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK);
        }

        // cannot start the cam
        if (null == mCamera) {
            Log.w(LOG_TAG,"## surfaceCreated() camera creation failed");
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

            //manageButtons();
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
        mCameraSurfaceHolder = null;
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }
    // *********************************************************************************************

}
