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
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.v4.app.FragmentManager;
import android.text.Html;
import android.text.TextUtils;

import org.matrix.androidsdk.data.RoomMediaMessage;
import org.matrix.androidsdk.util.Log;

import android.view.View;
import android.widget.Toast;

import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.util.ImageUtils;
import org.matrix.androidsdk.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;

import java.util.List;

import im.vector.R;
import im.vector.activity.VectorRoomActivity;
import im.vector.fragments.ImageSizeSelectionDialogFragment;
import im.vector.fragments.VectorMessageListFragment;

// VectorRoomMediasSender helps the vectorRoomActivity to manage medias .
public class VectorRoomMediasSender {
    private static final String LOG_TAG = VectorRoomMediasSender.class.getSimpleName();

    private static final String TAG_FRAGMENT_IMAGE_SIZE_DIALOG = "TAG_FRAGMENT_IMAGE_SIZE_DIALOG";

    /**
     * This listener is displayed when the image has been resized.
     */
    private interface OnImageUploadListener {
        // the image has been successfully resized and the upload starts
        void onDone();

        // the resize has been cancelled
        void onCancel();
    }

    private AlertDialog mImageSizesListDialog;

    // the linked room activity
    private final VectorRoomActivity mVectorRoomActivity;

    // the room fragment
    private final VectorMessageListFragment mVectorMessageListFragment;

    // the medias cache
    private final MXMediasCache mMediasCache;

    // the background thread
    private static HandlerThread mHandlerThread = null;
    private static android.os.Handler mMediasSendingHandler = null;

    // pending
    private List<RoomMediaMessage> mSharedDataItems;
    private String mImageCompressionDescription;

    /**
     * Constructor
     *
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
     * Resume any camera image upload that could have been in progress and
     * stopped due to activity lifecycle event.
     */
    public void resumeResizeMediaAndSend() {
        if (null != mSharedDataItems) {
            mVectorRoomActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    sendMedias();
                }
            });
        }
    }

    /**
     * Send a list of images from their URIs
     *
     * @param sharedDataItems the media URIs
     */
    public void sendMedias(final ArrayList<RoomMediaMessage> sharedDataItems) {
        if (null != sharedDataItems) {
            mSharedDataItems = new ArrayList<>(sharedDataItems);
            sendMedias();
        }
    }

    /**
     * Send a list of images from their URIs
     */
    private void sendMedias() {
        // sanity checks
        if ((null == mVectorRoomActivity) || (null == mVectorMessageListFragment) || (null == mMediasCache)) {
            Log.d(LOG_TAG, "sendMedias : null parameters");
            return;
        }

        // detect end of messages sending
        if ((null == mSharedDataItems) || (0 == mSharedDataItems.size())) {
            Log.d(LOG_TAG, "sendMedias : done");
            mImageCompressionDescription = null;
            mSharedDataItems = null;

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

        Log.d(LOG_TAG, "sendMedias : " + mSharedDataItems.size() + " items to send");

        mMediasSendingHandler.post(new Runnable() {
            @Override
            public void run() {
                final RoomMediaMessage sharedDataItem = mSharedDataItems.get(0);
                String mimeType = sharedDataItem.getMimeType(mVectorRoomActivity);

                // avoid null case
                if (null == mimeType) {
                    mimeType = "";
                }

                if (TextUtils.equals(ClipDescription.MIMETYPE_TEXT_INTENT, mimeType)) {
                    Log.d(LOG_TAG, "sendMedias :  unsupported mime type");
                    // don't know how to manage it -> skip it
                    // GA issue
                    if (mSharedDataItems.size() > 0) {
                        mSharedDataItems.remove(0);
                    }
                    sendMedias();
                } else if ((null == sharedDataItem.getUri()) && (TextUtils.equals(ClipDescription.MIMETYPE_TEXT_PLAIN, mimeType) || TextUtils.equals(ClipDescription.MIMETYPE_TEXT_HTML, mimeType))) {
                    sendTextMessage(sharedDataItem);
                } else {
                    // check if it is an uri
                    // else we don't know what to do
                    if (null == sharedDataItem.getUri()) {
                        Log.e(LOG_TAG, "sendMedias : null uri");
                        // manage others
                        if (mSharedDataItems.size() > 0) {
                            mSharedDataItems.remove(0);
                        }
                        sendMedias();
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
                        if (mSharedDataItems.size() > 0) {
                            mSharedDataItems.remove(0);
                        }
                        sendMedias();

                        return;
                    }

                    if (mimeType.startsWith("image/") &&
                            (ResourceUtils.MIME_TYPE_JPEG.equals(mimeType) ||
                                    ResourceUtils.MIME_TYPE_JPG.equals(mimeType) ||
                                    ResourceUtils.MIME_TYPE_IMAGE_ALL.equals(mimeType))) {
                        sendJpegImage(sharedDataItem, resource);
                    } else {
                        resource.close();
                        mVectorRoomActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mVectorMessageListFragment.sendMediaMessage(sharedDataItem);
                            }
                        });

                        // manage others
                        if (mSharedDataItems.size() > 0) {
                            mSharedDataItems.remove(0);
                        }
                        sendMedias();
                    }
                }
            }
        });
    }

    //================================================================================
    // text messages management
    //================================================================================

    /**
     * Send a text message.
     *
     * @param sharedDataItem the media item.
     */
    private void sendTextMessage(RoomMediaMessage sharedDataItem) {
        final CharSequence sequence = sharedDataItem.getText();
        String htmlText = sharedDataItem.getHtmlText();

        // content only text -> insert it in the room editor
        // to let the user decides to send the message
        if (!TextUtils.isEmpty(sequence) && (null == htmlText)) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    mVectorRoomActivity.insertTextInTextEditor(sequence.toString());
                }
            });
        } else {

            String text = null;

            if (null == sequence) {
                if (null != htmlText) {
                    text = Html.fromHtml(htmlText).toString();
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
                    mVectorRoomActivity.sendMessage(fText, fHtmlText, Message.FORMAT_MATRIX_HTML);
                }
            });
        }

        // manage others
        if (mSharedDataItems.size() > 0) {
            mSharedDataItems.remove(0);
        }
        sendMedias();
    }

    //================================================================================
    // image messages management
    //================================================================================

    /**
     * Send  message.
     *
     * @param sharedDataItem the item to send
     * @param resource       the media resource
     */
    private void sendJpegImage(final RoomMediaMessage sharedDataItem, final ResourceUtils.Resource resource) {
        String mimeType = sharedDataItem.getMimeType(mVectorRoomActivity);

        // save the file in the filesystem
        String mediaUrl = mMediasCache.saveMedia(resource.mContentStream, null, mimeType);
        resource.close();

        final String fMediaUrl = mediaUrl;
        final String fMimeType = mimeType;

        mVectorRoomActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if ((null != mSharedDataItems) && (mSharedDataItems.size() > 0)) {
                    sendJpegImage(sharedDataItem, fMediaUrl, fMimeType, new OnImageUploadListener() {
                        @Override
                        public void onDone() {
                            // reported by GA
                            if ((null != mSharedDataItems) && (mSharedDataItems.size() > 0)) {
                                mSharedDataItems.remove(0);
                            }
                            // go to the next item
                            sendMedias();
                        }

                        @Override
                        public void onCancel() {
                            // cancel any media sending
                            // reported by GA
                            if (null != mSharedDataItems) {
                                mSharedDataItems.clear();
                            }
                            sendMedias();
                        }
                    });
                }
            }
        });
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
         *
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
                scale = 1.0d / Integer.highestOneBit((int) Math.floor(1.0 / scale));

                // apply the scale factor and padding to 2
                resized.mWidth = (int) (Math.floor(resized.mWidth * scale / 2) * 2);
                resized.mHeight = (int) (Math.floor(resized.mHeight * scale / 2) * 2);
            }

            return resized;
        }

    }

    // max image sizes
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

        /**
         * Provides the defined compression description.
         *
         * @param context the context
         * @return the list of compression description
         */
        public List<String> getImageSizesDescription(Context context) {
            ArrayList<String> imagesSizesDescriptionList = new ArrayList<>();

            if (null != mFullImageSize) {
                imagesSizesDescriptionList.add(context.getString(R.string.compression_opt_list_original));
            }

            if (null != mLargeImageSize) {
                imagesSizesDescriptionList.add(context.getString(R.string.compression_opt_list_large));
            }

            if (null != mMediumImageSize) {
                imagesSizesDescriptionList.add(context.getString(R.string.compression_opt_list_medium));
            }

            if (null != mSmallImageSize) {
                imagesSizesDescriptionList.add(context.getString(R.string.compression_opt_list_small));
            }

            return imagesSizesDescriptionList;
        }

        /**
         * Returns the scaled size from a compression description
         *
         * @param context                the context
         * @param compressionDescription the compression description
         * @return the scaled size.
         */
        public ImageSize getImageSize(Context context, String compressionDescription) {
            boolean isGenuineDesc = TextUtils.equals(context.getString(R.string.compression_opt_list_original), compressionDescription);

            if (TextUtils.isEmpty(compressionDescription) || isGenuineDesc) {
                return mFullImageSize;
            }

            boolean isSmallDesc = TextUtils.equals(context.getString(R.string.compression_opt_list_small), compressionDescription);
            boolean isMediumDesc = TextUtils.equals(context.getString(R.string.compression_opt_list_medium), compressionDescription);
            boolean isLargeDesc = TextUtils.equals(context.getString(R.string.compression_opt_list_large), compressionDescription);

            ImageSize size = null;

            // small size
            if (isSmallDesc) {
                size = mSmallImageSize;
            }

            if ((null == size) && (isSmallDesc || isMediumDesc)) {
                size = mMediumImageSize;
            }

            if ((null == size) && (isSmallDesc || isMediumDesc || isLargeDesc)) {
                size = mLargeImageSize;
            }

            if (null == size) {
                size = mFullImageSize;
            }

            return size;
        }
    }

    /**
     * Compute the compressed image sizes.
     *
     * @param imageWidth  the image width
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

                // ensure that the computed is really smaller
                if ((imageCompressionSizes.mLargeImageSize.mWidth == imageWidth) && (imageCompressionSizes.mLargeImageSize.mHeight == imageHeight)) {
                    imageCompressionSizes.mLargeImageSize = null;
                }
            }

            if (maxSide > MEDIUM_IMAGE_SIZE) {
                imageCompressionSizes.mMediumImageSize = imageCompressionSizes.mFullImageSize.computeSizeToFit(MEDIUM_IMAGE_SIZE);

                // ensure that the computed is really smaller
                if ((imageCompressionSizes.mMediumImageSize.mWidth == imageWidth) && (imageCompressionSizes.mMediumImageSize.mHeight == imageHeight)) {
                    imageCompressionSizes.mMediumImageSize = null;
                }
            }

            if (maxSide > SMALL_IMAGE_SIZE) {
                imageCompressionSizes.mSmallImageSize = imageCompressionSizes.mFullImageSize.computeSizeToFit(SMALL_IMAGE_SIZE);

                // ensure that the computed is really smaller
                if ((imageCompressionSizes.mSmallImageSize.mWidth == imageWidth) && (imageCompressionSizes.mSmallImageSize.mHeight == imageHeight)) {
                    imageCompressionSizes.mSmallImageSize = null;
                }
            }
        }

        return imageCompressionSizes;
    }

    /**
     * @return the estimated file size (in bytes)
     */
    private static int estimateFileSize(ImageSize imageSize) {
        if (null != imageSize) {
            // rounded the size in 1024 multiplier
            return imageSize.mWidth * imageSize.mHeight * 2 / 10 / 1024 * 1024;
        } else {
            return 0;
        }
    }

    /**
     * Add an entry in the dialog lists.
     *
     * @param context         the context.
     * @param textsList       the texts list.
     * @param descriptionText the image description text
     * @param imageSize       the image size.
     * @param fileSize        the file size (in bytes)
     */
    private static void addDialogEntry(Context context, ArrayList<String> textsList, String descriptionText, ImageSize imageSize, int fileSize) {
        if ((null != imageSize) && (null != textsList)) {
            textsList.add(descriptionText + ": " + android.text.format.Formatter.formatFileSize(context, fileSize) + " (" + imageSize.mWidth + "x" + imageSize.mHeight + ")");
        }
    }

    /**
     * Create the image compression texts list.
     *
     * @param context       the context
     * @param imageSizes    the image compressions
     * @param imagefileSize the image file size
     * @return the texts list to display
     */
    private static String[] getImagesCompressionTextsList(Context context, ImageCompressionSizes imageSizes, int imagefileSize) {
        final ArrayList<String> textsList = new ArrayList<>();

        addDialogEntry(context, textsList, context.getString(R.string.compression_opt_list_original), imageSizes.mFullImageSize, imagefileSize);
        addDialogEntry(context, textsList, context.getString(R.string.compression_opt_list_large), imageSizes.mLargeImageSize, Math.min(estimateFileSize(imageSizes.mLargeImageSize), imagefileSize));
        addDialogEntry(context, textsList, context.getString(R.string.compression_opt_list_medium), imageSizes.mMediumImageSize, Math.min(estimateFileSize(imageSizes.mMediumImageSize), imagefileSize));
        addDialogEntry(context, textsList, context.getString(R.string.compression_opt_list_small), imageSizes.mSmallImageSize, Math.min(estimateFileSize(imageSizes.mSmallImageSize), imagefileSize));

        return textsList.toArray(new String[textsList.size()]);
    }

    /**
     * Apply an image with an expected size.
     * A rotation might also be applied if provided.
     *
     * @param anImageUrl    the image URI.
     * @param filename      the image filename.
     * @param srcImageSize  the source image size
     * @param dstImageSize  the expected image size.
     * @param rotationAngle the rotation angle to apply.
     * @return the resized image.
     */
    private String resizeImage(String anImageUrl, String filename, ImageSize srcImageSize, ImageSize dstImageSize, int rotationAngle) {
        String imageUrl = anImageUrl;

        try {
            // got a dst image size
            if (null != dstImageSize) {
                FileInputStream imageStream = new FileInputStream(new File(filename));

                InputStream resizeBitmapStream = null;

                try {
                    resizeBitmapStream = ImageUtils.resizeImage(imageStream, -1, (srcImageSize.mWidth + dstImageSize.mWidth - 1) / dstImageSize.mWidth, 75);
                } catch (OutOfMemoryError ex) {
                    Log.e(LOG_TAG, "resizeImage out of memory : " + ex.getMessage());
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resizeImage failed : " + e.getMessage());
                }

                if (null != resizeBitmapStream) {
                    String bitmapURL = mMediasCache.saveMedia(resizeBitmapStream, null, ResourceUtils.MIME_TYPE_JPEG);

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
            Log.e(LOG_TAG, "resizeImage " + e.getMessage());
        }

        return imageUrl;
    }

    /**
     * Offer to resize the image before sending it.
     *
     * @param anImageUrl      the image url.
     * @param anImageMimeType the image mimetype
     * @param aListener       the listener
     */
    private void sendJpegImage(final RoomMediaMessage roomMediaMessage, final String anImageUrl, final String anImageMimeType, final OnImageUploadListener aListener) {
        // sanity check
        if ((null == anImageUrl) || (null == aListener)) {
            return;
        }

        boolean isManaged = false;

        // check if the media could be resized
        if ((ResourceUtils.MIME_TYPE_JPEG.equals(anImageMimeType) || ResourceUtils.MIME_TYPE_JPG.equals(anImageMimeType) || ResourceUtils.MIME_TYPE_IMAGE_ALL.equals(anImageMimeType))) {
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
                    Log.e(LOG_TAG, "sendImageMessage out of memory error : " + e.getMessage());
                }

                final ImageCompressionSizes imageSizes = computeImageSizes(options.outWidth, options.outHeight);

                imageStream.close();

                // the user already selects a compression
                if (null != mImageCompressionDescription) {
                    isManaged = true;

                    final ImageSize expectedSize = imageSizes.getImageSize(mVectorRoomActivity, mImageCompressionDescription);
                    final String fImageUrl = resizeImage(anImageUrl, filename, imageSizes.mFullImageSize, expectedSize, rotationAngle);

                    mVectorRoomActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mVectorMessageListFragment.sendMediaMessage(new RoomMediaMessage(Uri.parse(fImageUrl), roomMediaMessage.getFileName(mVectorRoomActivity)));
                            aListener.onDone();
                        }
                    });
                }
                // can be rescaled ?
                else if (null != imageSizes.mSmallImageSize) {
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
                                            ImageSize expectedSize = null;

                                            // full size
                                            if (0 != fPos) {
                                                expectedSize = imageSizes.getImageSizesList().get(fPos);
                                            }

                                            // stored the compression selected by the user
                                            mImageCompressionDescription = imageSizes.getImageSizesDescription(mVectorRoomActivity).get(fPos);

                                            final String fImageUrl = resizeImage(anImageUrl, filename, imageSizes.mFullImageSize, expectedSize, rotationAngle);

                                            mVectorRoomActivity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mVectorMessageListFragment.sendMediaMessage(new RoomMediaMessage(Uri.parse(fImageUrl), roomMediaMessage.getFileName(mVectorRoomActivity)));
                                                    aListener.onDone();
                                                }
                                            });
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
                            if (null != aListener) {
                                aListener.onCancel();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "sendImageMessage failed " + e.getMessage());
            }
        }

        // cannot resize, let assumes that it has been done
        if (!isManaged) {
            mVectorRoomActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mVectorMessageListFragment.sendMediaMessage(roomMediaMessage);
                    if (null != aListener) {
                        aListener.onDone();
                    }
                }
            });
        }
    }
}
