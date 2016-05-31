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
package im.vector.util;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import im.vector.R;

/**
 * Static resource utility methods.
 */
public class ResourceUtils {

    private static final String LOG_TAG = "ResourceUtils";

    public static class Resource {
        public final InputStream contentStream;
        public final String mimeType;

        public Resource(InputStream contentStream, String mimeType) {
            this.contentStream = contentStream;
            this.mimeType = mimeType;
        }
    }

    /**
     * Get a resource stream and metadata about it given its URI returned from onActivityResult.
     *
     * @param context the context.
     * @param uri     the URI
     * @return a {@link Resource} encapsulating the opened resource stream and associated metadata
     * or {@code null} if opening the resource stream failed.
     */
    public static Resource openResource(Context context, Uri uri) {
        try {
            String mimetype = context.getContentResolver().getType(uri);

            // try to find the mimetype from the filename
            if (null == mimetype) {
                String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString().toLowerCase());
                if (extension != null) {
                    mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                }
            }

            return new Resource(
                    context.getContentResolver().openInputStream(uri),
                    mimetype);

        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to open resource input stream", e);
        }

        return null;
    }

    /**
     * Return the thumbnail bitmap from a media Uri
     *
     * @param context  the context
     * @param mediaUri the media Uri
     * @return the bitmap.
     */
    public static Bitmap getThumbnailBitmap(Context context, Uri mediaUri) {
        Bitmap thumbnailBitmap = null;
        ResourceUtils.Resource resource = ResourceUtils.openResource(context, mediaUri);

        // check if the resource can be i
        if (null == resource) {
            return null;
        }

        if ((null != resource.mimeType) && resource.mimeType.startsWith("image/")) {
            try {
                ContentResolver resolver = context.getContentResolver();

                List uriPath = mediaUri.getPathSegments();
                long imageId;
                String lastSegment = (String) uriPath.get(uriPath.size() - 1);

                // > Kitkat
                if (lastSegment.startsWith("image:")) {
                    lastSegment = lastSegment.substring("image:".length());
                }

                imageId = Long.parseLong(lastSegment);

                thumbnailBitmap = MediaStore.Images.Thumbnails.getThumbnail(resolver, imageId, MediaStore.Images.Thumbnails.MINI_KIND, null);
            } catch (Exception e) {
                Log.e(LOG_TAG, "getThumbnailBitmap " + e.getMessage());
            }

            //
            double thumbnailWidth = 1024.0;
            double thumbnailHeight = 1024.0;

            // no thumbnail has been found or the mimetype is unknown
            if ((null == thumbnailBitmap) || (thumbnailBitmap.getHeight() > thumbnailHeight) || (thumbnailBitmap.getWidth() > thumbnailWidth)) {
                // need to decompress the high res image
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                resource = ResourceUtils.openResource(context, mediaUri);

                // get the full size bitmap
                Bitmap fullSizeBitmap = null;

                if (null == thumbnailBitmap) {
                    try {
                        fullSizeBitmap = BitmapFactory.decodeStream(resource.contentStream, null, options);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "BitmapFactory.decodeStream fails " + e.getLocalizedMessage());
                    }
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

                // reduce the memory consumption
                if (null != fullSizeBitmap) {
                    fullSizeBitmap.recycle();
                    System.gc();
                }
            }
        }

        return thumbnailBitmap;
    }
}