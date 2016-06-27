/* 
 * Copyright 2016 OpenMarket Ltd
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

package im.vector.util;

import android.app.AlertDialog;
import android.content.ClipDescription;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.ImageUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorMediasPickerActivity;
import im.vector.activity.VectorRoomActivity;
import im.vector.fragments.ImageSizeSelectionDialogFragment;
import im.vector.fragments.VectorMessageListFragment;

// VectorRoomMediasSender helps the vectorRoomActivity to manage medias .
public class VectorRoomMediasSender {
    private static final String LOG_TAG = "VectorRoomMedHelp";

    private static final String TAG_FRAGMENT_IMAGE_SIZE_DIALOG = "TAG_FRAGMENT_IMAGE_SIZE_DIALOG";

    /**
     * This listener is displayed when the image has been resized.
     */
    private interface OnImageUploadListener {
        // the image has been successfully resized and the upload starts
        void onDone();
        // the resize has been cancelled
        void onCancel();
    };

    private static final String PENDING_THUMBNAIL_URL = "PENDING_THUMBNAIL_URL";
    private static final String PENDING_MEDIA_URL = "PENDING_MEDIA_URL";
    private static final String PENDING_MIMETYPE = "PENDING_MIMETYPE";
    private static final String PENDING_FILENAME = "PENDING_FILENAME";
    private static final String KEY_BUNDLE_PENDING_QUALITY_IMAGE_POPUP = "KEY_BUNDLE_PENDING_QUALITY_IMAGE_POPUP";

    // pending infos
   /* private String mPendingThumbnailUrl;
    private String mPendingMediaUrl;
    private String mPendingMimeType;
    private String mPendingFilename;
    private boolean mImageQualityPopUpInProgress;*/

    // when there are several images to send in one batch,
    // the compression dialog is displayed once.
    // We assume that the user would like to keep the same compression for each of them.
    private Integer mSelectedImageCompression = null;

    private AlertDialog mImageSizesListDialog;

    // the linked room activity
    private VectorRoomActivity mVectorRoomActivity;

    // the room fragment
    private VectorMessageListFragment mVectorMessageListFragment;

    // the medias cache
    private MXMediasCache mMediasCache;

    // the background thread
    private static HandlerThread mHandlerThread = null;
    private static android.os.Handler mMediasSendingHandler = null;


    /**
     * Constructor
     * @param roomActivity the room activity.
     */
    public VectorRoomMediasSender(VectorRoomActivity roomActivity, VectorMessageListFragment vectorMessageListFragment, MXMediasCache mediasCache) {
        mVectorRoomActivity = roomActivity;
        mVectorMessageListFragment = vectorMessageListFragment;
        mMediasCache = mediasCache;

        if (null == mHandlerThread) {
            mHandlerThread = new HandlerThread("VectorRoomMediasSender", Thread.MIN_PRIORITY);
            mHandlerThread.start();

            mMediasSendingHandler = new android.os.Handler(mHandlerThread.getLooper());
        }
    }

    /**
     * Restore some saved info.
     * @param savedInstanceState the bundle
     */
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (null != savedInstanceState) {
            /*if (savedInstanceState.containsKey(PENDING_THUMBNAIL_URL)) {
                mPendingThumbnailUrl = savedInstanceState.getString(PENDING_THUMBNAIL_URL);
            }

            if (savedInstanceState.containsKey(PENDING_MEDIA_URL)) {
                mPendingMediaUrl = savedInstanceState.getString(PENDING_MEDIA_URL);
            }

            if (savedInstanceState.containsKey(PENDING_MIMETYPE)) {
                mPendingMimeType = savedInstanceState.getString(PENDING_MIMETYPE);
            }

            if (savedInstanceState.containsKey(PENDING_FILENAME)) {
                mPendingFilename = savedInstanceState.getString(PENDING_FILENAME);
            }

            // indicate if an image camera upload was in progress (image quality "Send as" dialog displayed).
            mImageQualityPopUpInProgress = savedInstanceState.getBoolean(KEY_BUNDLE_PENDING_QUALITY_IMAGE_POPUP, false);*/
        }
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {

       /* if (null != mPendingThumbnailUrl) {
            savedInstanceState.putString(PENDING_THUMBNAIL_URL, mPendingThumbnailUrl);
        }

        if (null != mPendingMediaUrl) {
            savedInstanceState.putString(PENDING_MEDIA_URL, mPendingMediaUrl);
        }

        if (null != mPendingMimeType) {
            savedInstanceState.putString(PENDING_MIMETYPE, mPendingMimeType);
        }

        if (null != mPendingFilename) {
            savedInstanceState.putString(PENDING_FILENAME, mPendingFilename);
        }

        savedInstanceState.putBoolean(KEY_BUNDLE_PENDING_QUALITY_IMAGE_POPUP, mImageQualityPopUpInProgress);*/
    }

    /**
     * Resume any camera image upload that could have been in progress and
     * stopped due to activity lifecycle event.
     */
    public void resumeResizeMediaAndSend() {
        /*if (mImageQualityPopUpInProgress) {
            mVectorRoomActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resizeMediaAndSend();
                }
            });
        }*/
    }

    /**
     * Send a text message.
     * @param sharedDataItem the media item.
     */
    private void sendTextMessage(SharedDataItem sharedDataItem) {
        CharSequence sequence = sharedDataItem.getText();
        String htmlText = sharedDataItem.getHtmlText();
        String text;

        if (null == sequence) {
            if (null != htmlText) {
                text = Html.fromHtml(htmlText).toString();
            } else {
                text = htmlText;
            }
        } else {
            text = sequence.toString();
        }

        Log.d(LOG_TAG, "sendTextMessage " + text);

        final String fText = text;
        final String fHtmlText = htmlText;

        mVectorRoomActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVectorRoomActivity.sendMessage(fText, fHtmlText, "org.matrix.custom.html");
            }
        });
    }

    /**
     * Send a list of images from their URIs
     * @param sharedDataItems the media URIs
     */
    public void sendMedias(final ArrayList<SharedDataItem> sharedDataItems) {
        // detect end of messages sending
        if ((null == sharedDataItems) || (0 == sharedDataItems.size())) {
            Log.d(LOG_TAG, "sendMedias : done");
            mVectorRoomActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mVectorMessageListFragment.scrollToBottom();
                    mVectorRoomActivity.cancelSelectionMode();
                    mVectorRoomActivity.setProgressVisibility(View.GONE);
                }
            });

            return;
        }

        // display a spinner
        mVectorRoomActivity.cancelSelectionMode();
        mVectorRoomActivity.setProgressVisibility(View.VISIBLE);

        Log.d(LOG_TAG, "sendMedias : " + sharedDataItems.size() + " items to send");

        mMediasSendingHandler.post(new Runnable() {
            public void run() {
                SharedDataItem sharedDataItem = sharedDataItems.get(0);
                sharedDataItems.remove(0);

                String mimeType = sharedDataItem.getMimeType(mVectorRoomActivity);

                // avoid null case
                if (null == mimeType) {
                    mimeType = "";
                }

                if (TextUtils.equals(ClipDescription.MIMETYPE_TEXT_INTENT, mimeType)) {
                    Log.d(LOG_TAG, "sendMedias :  unsupported mime type");
                    // don't know how to manage it -> skip it
                    sendMedias(sharedDataItems);
                } else if (TextUtils.equals(ClipDescription.MIMETYPE_TEXT_PLAIN, mimeType) || TextUtils.equals(ClipDescription.MIMETYPE_TEXT_HTML, mimeType)) {
                    sendTextMessage(sharedDataItem);
                    // manage others
                    sendMedias(sharedDataItems);
                } else {
                    // check if it is an uri
                    // else we don't know what to do
                    if (null == sharedDataItem.getUri()) {
                        Log.e(LOG_TAG, "sendMedias : null uri");
                        // manage others
                        sendMedias(sharedDataItems);
                        return;
                    }

                    final String fFilename = sharedDataItem.getFileName(mVectorRoomActivity);
                    ResourceUtils.Resource resource = ResourceUtils.openResource(mVectorRoomActivity, sharedDataItem.getUri(), sharedDataItem.getMimeType(mVectorRoomActivity));

                    if (null == resource) {
                        Log.e(LOG_TAG, "sendMedias : " + fFilename + " is not found");

                        mVectorRoomActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mVectorRoomActivity,
                                        mVectorRoomActivity.getString(R.string.room_message_file_not_found),
                                        Toast.LENGTH_LONG).show();
                            }
                        });

                        // manage others
                        sendMedias(sharedDataItems);

                        return;
                    }

                    if (mimeType.startsWith("video/")) {
                        mVectorMessageListFragment.uploadVideoContent(fMediaUrl, fThumbUrl, null, fMimeType);
                    }

                    final String fMediaUrl = mediaUrl;
                    final String fMimeType = mimeType;
                    final boolean isVideo = ((null != fMimeType) && fMimeType.startsWith("video/"));
                    final String fThumbUrl = isVideo ? mVectorMessageListFragment.getVideoThumbailUrl(fMediaUrl) : null;

                    mVectorRoomActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isVideo) {

                            } else {
                                mVectorMessageListFragment.uploadFileContent(fMediaUrl, fMimeType, fFilename);
                            }
                        }
                    });


                    // save the file in the filesystem
                    String mediaUrl = mMediasCache.saveMedia(resource.contentStream, null, resource.mimeType);
                    boolean isManaged = false;

                    if ((null != resource.mimeType) && resource.mimeType.startsWith("image/")) {
                        // manage except if there is an error
                        isManaged = true;

                        // try to retrieve the gallery thumbnail
                        // if the image comes from the gallery..
                        Bitmap thumbnailBitmap = null;
                        Bitmap defaultThumbnailBitmap = null;

                        try {
                            ContentResolver resolver = mVectorRoomActivity.getContentResolver();

                            List uriPath = sharedDataItem.getUri().getPathSegments();
                            long imageId;
                            String lastSegment = (String) uriPath.get(uriPath.size() - 1);

                            // > Kitkat
                            if (lastSegment.startsWith("image:")) {
                                lastSegment = lastSegment.substring("image:".length());
                            }

                            imageId = Long.parseLong(lastSegment);
                            defaultThumbnailBitmap = MediaStore.Images.Thumbnails.getThumbnail(resolver, imageId, MediaStore.Images.Thumbnails.MINI_KIND, null);
                            thumbnailBitmap = MediaStore.Images.Thumbnails.getThumbnail(resolver, imageId, MediaStore.Images.Thumbnails.FULL_SCREEN_KIND, null);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "MediaStore.Images.Thumbnails.getThumbnail " + e.getMessage());
                        }

                        // the medias picker stores its own thumbnail to avoid inflating large one
                        if (null == thumbnailBitmap) {
                            try {
                                String thumbPath = VectorMediasPickerActivity.getThumbnailPath(sharedDataItem.getUri().getPath());

                                if (null != thumbPath) {
                                    File thumbFile = new File(thumbPath);

                                    if (thumbFile.exists()) {
                                        BitmapFactory.Options options = new BitmapFactory.Options();
                                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                                        thumbnailBitmap = BitmapFactory.decodeFile(thumbPath, options);
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "cannot restore the medias picker thumbnail " + e.getMessage());
                            }
                        }

                        double thumbnailWidth = mVectorMessageListFragment.getMaxThumbnailWith();
                        double thumbnailHeight = mVectorMessageListFragment.getMaxThumbnailHeight();

                        // no thumbnail has been found or the mimetype is unknown
                        if ((null == thumbnailBitmap) || (thumbnailBitmap.getHeight() > thumbnailHeight) || (thumbnailBitmap.getWidth() > thumbnailWidth)) {
                            // need to decompress the high res image
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                            resource = ResourceUtils.openResource(mVectorRoomActivity, sharedDataItem.getUri(), sharedDataItem.getMimeType(mVectorRoomActivity));

                            // get the full size bitmap
                            Bitmap fullSizeBitmap = null;

                            if (null == thumbnailBitmap) {
                                fullSizeBitmap = BitmapFactory.decodeStream(resource.contentStream, null, options);
                            }

                            if ((fullSizeBitmap != null) || (thumbnailBitmap != null)) {
                                double imageWidth;
                                double imageHeight;

                                if (null == thumbnailBitmap) {
                                    imageWidth = fullSizeBitmap.getWidth();
                                    imageHeight = fullSizeBitmap.getHeight();
                                } else {
                                    imageWidth = thumbnailBitmap.getWidth();
                                    imageHeight = thumbnailBitmap.getHeight();
                                }

                                if (imageWidth > imageHeight) {
                                    thumbnailHeight = thumbnailWidth * imageHeight / imageWidth;
                                } else {
                                    thumbnailWidth = thumbnailHeight * imageWidth / imageHeight;
                                }

                                try {
                                    thumbnailBitmap = Bitmap.createScaledBitmap((null == fullSizeBitmap) ? thumbnailBitmap : fullSizeBitmap, (int) thumbnailWidth, (int) thumbnailHeight, false);
                                } catch (OutOfMemoryError ex) {
                                    Log.e(LOG_TAG, "Bitmap.createScaledBitmap " + ex.getMessage());
                                }
                            }

                            // the valid mimetype is not provided
                            if ("image/*".equals(mimeType)) {
                                // make a jpg snapshot.
                                mimeType = null;
                            }

                            // unknown mimetype
                            if ((null == mimeType) || (mimeType.startsWith("image/"))) {
                                try {
                                    // try again
                                    if (null == fullSizeBitmap) {
                                        System.gc();
                                        fullSizeBitmap = BitmapFactory.decodeStream(resource.contentStream, null, options);
                                    }

                                    if (null != fullSizeBitmap) {
                                        Uri uri = Uri.parse(mediaUrl);

                                        if (null == mimeType) {
                                            // the images are save in jpeg format
                                            mimeType = "image/jpeg";
                                        }

                                        resource.contentStream.close();
                                        resource = ResourceUtils.openResource(mVectorRoomActivity, sharedDataItem.getUri(), sharedDataItem.getMimeType(mVectorRoomActivity));

                                        try {
                                            mMediasCache.saveMedia(resource.contentStream, uri.getPath(), mimeType);
                                        } catch (OutOfMemoryError ex) {
                                            Log.e(LOG_TAG, "mMediasCache.saveMedia" + ex.getMessage());
                                        }

                                    } else {
                                        isManaged = false;
                                    }

                                    resource.contentStream.close();

                                } catch (Exception e) {
                                    isManaged = false;
                                    Log.e(LOG_TAG, "sendMedias " + e.getMessage());
                                }
                            }

                            // reduce the memory consumption
                            if (null != fullSizeBitmap) {
                                fullSizeBitmap.recycle();
                                System.gc();
                            }
                        }

                        if (null == thumbnailBitmap) {
                            thumbnailBitmap = defaultThumbnailBitmap;
                        }

                        String thumbnailURL = mMediasCache.saveBitmap(thumbnailBitmap, null);

                        if (null != thumbnailBitmap) {
                            thumbnailBitmap.recycle();
                        }

                        //
                        if (("image/jpg".equals(mimeType) || "image/jpeg".equals(mimeType)) && (null != mediaUrl)) {

                            Uri imageUri = Uri.parse(mediaUrl);
                            // get the exif rotation angle
                            final int rotationAngle = ImageUtils.getRotationAngleForBitmap(mVectorRoomActivity, imageUri);

                            if (0 != rotationAngle) {
                                // always apply the rotation to the image
                                ImageUtils.rotateImage(mVectorRoomActivity, thumbnailURL, rotationAngle, mMediasCache);

                                // the high res media orientation should be not be done on uploading
                                //ImageUtils.rotateImage(RoomActivity.this, mediaUrl, rotationAngle, mMediasCache))
                            }
                        }

                        // is the image content valid ?
                        if (isManaged && (null != thumbnailURL)) {

                            final String fThumbnailURL = thumbnailURL;
                            final String fMediaUrl = mediaUrl;
                            final String fMimeType = mimeType;

                            mVectorRoomActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {

                                    sendImageMessage(fThumbnailURL, fMediaUrl, fFilename, fMimeType, new OnImageUploadListener() {
                                        @Override
                                        public void onDone() {

                                        }

                                        @Override
                                        public void onCancel() {

                                        }
                                    });
                                }
                            });
                        }
                    }

                    // default behaviour
                    if ((!isManaged) && (null != mediaUrl)) {

                    }
                }
            }

        };


    }


    //================================================================================
    // Image resizing
    //================================================================================

    /**
     * Class storing the image information
     */
    private class ImageSize {
        public int mWidth;
        public int mHeight;

        public ImageSize(int width, int height) {
            mWidth = width;
            mHeight = height;
        }

        public ImageSize(ImageSize anotherOne) {
            mWidth = anotherOne.mWidth;
            mHeight = anotherOne.mHeight;
        }

        /**
         * Compute the image size to fit in a square.
         * @param maxSide the square to fit size
         * @return the image size
         */
        private ImageSize computeSizeToFit(float maxSide) {
            if (0 == maxSide) {
                return new ImageSize(0, 0);
            }

            ImageSize resized = new ImageSize(this);

            if ((this.mWidth > maxSide) || (this.mHeight > maxSide)) {
                double ratioX = maxSide / this.mWidth;
                double ratioY = maxSide / this.mHeight;

                double scale = Math.min(ratioX, ratioY);

                // the ratio must a power of 2
                scale = 1.0d / Integer.highestOneBit((int)Math.floor(1.0 / scale));

                // apply the scale factor and padding to 2
                resized.mWidth  = (int)(Math.floor(resized.mWidth * scale / 2) * 2);
                resized.mHeight = (int)(Math.floor(resized.mHeight * scale / 2) * 2);
            }

            return resized;
        }

    }

    // max image sizes
    private static final int MAX_IMAGE_SIZE = 1000000;
    private static final int LARGE_IMAGE_SIZE = 2048;
    private static final int MEDIUM_IMAGE_SIZE = 1024;
    private static final int SMALL_IMAGE_SIZE = 512;

    /**
     * Class storing an image compression size
     */
    private class ImageCompressionSizes {
        // high res image size
        public ImageSize mFullImageSize;
        // large image size (i.e MEDIUM_IMAGE_SIZE < side < LARGE_IMAGE_SIZE)
        public ImageSize mLargeImageSize;
        // medium  image size (i.e SMALL_IMAGE_SIZE < side < MEDIUM_IMAGE_SIZE)
        public ImageSize mMediumImageSize;
        // small  image size (i.e side < SMALL_IMAGE_SIZE)
        public ImageSize mSmallImageSize;

        /**
         * @return the image sizes list.
         */
        public List<ImageSize> getImageSizesList() {
            ArrayList<ImageSize> imagesSizesList = new ArrayList<>();

            if (null != mFullImageSize) {
                imagesSizesList.add(mFullImageSize);
            }

            if (null != mLargeImageSize) {
                imagesSizesList.add(mLargeImageSize);
            }

            if (null != mMediumImageSize) {
                imagesSizesList.add(mMediumImageSize);
            }

            if (null != mSmallImageSize) {
                imagesSizesList.add(mSmallImageSize);
            }

            return imagesSizesList;
        }
    }

    /**
     * Compute the compressed image sizes.
     * @param imageWidth the image width
     * @param imageHeight the image height
     * @return the compression sizes
     */
    private ImageCompressionSizes computeImageSizes(int imageWidth, int imageHeight) {
        ImageCompressionSizes imageCompressionSizes = new ImageCompressionSizes();

        imageCompressionSizes.mFullImageSize = new ImageSize(imageWidth, imageHeight);

        int maxSide = (imageHeight > imageWidth) ? imageHeight : imageWidth;

        // can be rescaled ?
        if (maxSide > SMALL_IMAGE_SIZE) {

            if (maxSide > LARGE_IMAGE_SIZE) {
                imageCompressionSizes.mLargeImageSize = imageCompressionSizes.mFullImageSize.computeSizeToFit(LARGE_IMAGE_SIZE);
            }

            if (maxSide > MEDIUM_IMAGE_SIZE) {
                imageCompressionSizes.mMediumImageSize = imageCompressionSizes.mFullImageSize.computeSizeToFit(MEDIUM_IMAGE_SIZE);
            }

            if (maxSide > SMALL_IMAGE_SIZE) {
                imageCompressionSizes.mSmallImageSize = imageCompressionSizes.mFullImageSize.computeSizeToFit(SMALL_IMAGE_SIZE);
            }
        }

        return imageCompressionSizes;
    }

    /**
     * @return the estimated file size (in bytes)
     */
    private static int estimateFileSize(ImageSize imageSize) {
        if (null != imageSize) {
            return imageSize.mWidth * imageSize.mHeight * 2 / 10 / 1024 * 1024;
        } else {
            return 0;
        }
    }

    /**
     * Add an entry in the dialog lists.
     * @param context the context.
     * @param textsList the texts list.
     * @param descriptionText the image description text
     * @param imageSize the image size.
     * @param fileSize the file size (in bytes)
     */
    private static void addDialogEntry (Context context, ArrayList<String> textsList, String descriptionText, ImageSize imageSize, int fileSize) {
        if (null != imageSize) {
            textsList.add(descriptionText + ": " + android.text.format.Formatter.formatFileSize(context, fileSize) + " (" + imageSize.mWidth + "x" + imageSize.mHeight + ")");
        }
    }

    /**
     * Create the image compression texts list.
     * @param context  the context
     * @param imageSizes the image compressions
     * @param imagefileSize the image file size
     * @return the texts list to display
     */
    private static String[] getImagesCompressionTextsList(Context context, ImageCompressionSizes imageSizes, int imagefileSize) {
        final ArrayList<String> textsList = new ArrayList<>();

        addDialogEntry(context, textsList, context.getString(R.string.compression_opt_list_original), imageSizes.mFullImageSize, imagefileSize);
        addDialogEntry(context, textsList, context.getString(R.string.compression_opt_list_large), imageSizes.mLargeImageSize, estimateFileSize(imageSizes.mLargeImageSize));
        addDialogEntry(context, textsList, context.getString(R.string.compression_opt_list_medium), imageSizes.mMediumImageSize, estimateFileSize(imageSizes.mMediumImageSize));
        addDialogEntry(context, textsList, context.getString(R.string.compression_opt_list_small), imageSizes.mSmallImageSize, estimateFileSize(imageSizes.mSmallImageSize));

        return textsList.toArray(new String[textsList.size()]);
    }

    /**
     * Offer to resize the image before sending it.
     * @param aThumbnailURL the thumbnail url
     * @param anImageUrl the image url.
     * @param anImageFilename the image filename
     * @param anImageMimeType the image mimetype
     * @param aListener the listener
     */
    private void sendImageMessage(final String aThumbnailURL, final String anImageUrl, final String anImageFilename, final String anImageMimeType, final OnImageUploadListener aListener) {
        // sanity check
        if ((null == anImageUrl) || (null == aListener)) {
            return;
        }

        boolean isManaged = false;

        // check if the media could be resized
        if ("image/jpeg".equals(anImageMimeType)) {

            System.gc();

            System.gc();
            FileInputStream imageStream;

            try {
                Uri uri = Uri.parse(anImageUrl);
                final String filename = uri.getPath();

                final int rotationAngle = ImageUtils.getRotationAngleForBitmap(mVectorRoomActivity, uri);

                imageStream = new FileInputStream(new File(filename));

                int fileSize = imageStream.available();

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                options.outWidth = -1;
                options.outHeight = -1;

                // retrieve the image size
                try {
                    BitmapFactory.decodeStream(imageStream, null, options);
                } catch (OutOfMemoryError e) {
                    Log.e(LOG_TAG, "Onclick BitmapFactory.decodeStream : " + e.getMessage());
                }

                final ImageCompressionSizes imageSizes = computeImageSizes(options.outWidth, options.outHeight);

                imageStream.close();

                // can be rescaled ?
                if (null != imageSizes.mSmallImageSize) {
                    isManaged = true;

                    FragmentManager fm = mVectorRoomActivity.getSupportFragmentManager();
                    ImageSizeSelectionDialogFragment fragment = (ImageSizeSelectionDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_IMAGE_SIZE_DIALOG);

                    if (fragment != null) {
                        fragment.dismissAllowingStateLoss();
                    }

                    String[] stringsArray = getImagesCompressionTextsList(mVectorRoomActivity, imageSizes, fileSize);

                    final AlertDialog.Builder alert = new AlertDialog.Builder(mVectorRoomActivity);
                    alert.setTitle(mVectorRoomActivity.getString(im.vector.R.string.compression_options));
                    alert.setSingleChoiceItems(stringsArray, -1, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final int fPos = which;
                            mImageSizesListDialog.dismiss();

                            mVectorRoomActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mVectorRoomActivity.setProgressVisibility(View.VISIBLE);

                                    Thread thread = new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            String imageUrl = anImageUrl;

                                            try {
                                                // pos == 0 -> genuine
                                                if (0 != fPos) {
                                                    FileInputStream imageStream = new FileInputStream(new File(filename));

                                                    ImageSize imageSize = imageSizes.getImageSizesList().get(fPos);
                                                    InputStream resizeBitmapStream = null;

                                                    try {
                                                        resizeBitmapStream = ImageUtils.resizeImage(imageStream, -1, (imageSizes.mFullImageSize.mWidth + imageSize.mWidth - 1) / imageSize.mWidth, 75);
                                                    } catch (OutOfMemoryError ex) {
                                                        Log.e(LOG_TAG, "Onclick BitmapFactory.createScaledBitmap : " + ex.getMessage());
                                                    } catch (Exception e) {
                                                        Log.e(LOG_TAG, "Onclick BitmapFactory.createScaledBitmap failed : " + e.getMessage());
                                                    }

                                                    if (null != resizeBitmapStream) {
                                                        String bitmapURL = mMediasCache.saveMedia(resizeBitmapStream, null, "image/jpeg");

                                                        if (null != bitmapURL) {
                                                            imageUrl = bitmapURL;
                                                        }

                                                        resizeBitmapStream.close();
                                                    }
                                                }

                                                // try to apply exif rotation
                                                if (0 != rotationAngle) {
                                                    // rotate the image content
                                                    ImageUtils.rotateImage(mVectorRoomActivity, imageUrl, rotationAngle, mMediasCache);
                                                }
                                            } catch (Exception e) {
                                                Log.e(LOG_TAG, "Onclick " + e.getMessage());
                                            }

                                            mVectorMessageListFragment.uploadImageContent(aThumbnailURL, imageUrl, anImageFilename, anImageMimeType);
                                            aListener.onDone();
                                        }
                                    });

                                    thread.setPriority(Thread.MIN_PRIORITY);
                                    thread.start();
                                }
                            });
                        }
                    });

                    mImageSizesListDialog = alert.show();
                    mImageSizesListDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            mImageSizesListDialog = null;
                            aListener.onCancel();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Onclick " + e.getMessage());
            }

            // cannot resize, let assumes that it has been done
            if (!isManaged) {
                mVectorMessageListFragment.uploadImageContent(aThumbnailURL, anImageUrl, anImageFilename, anImageMimeType);
                aListener.onDone();
            }
        }
    }
}
