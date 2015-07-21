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
package org.matrix.vector.util;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

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
     * @param activity the activity
     * @param uri the URI
     * @return a {@link Resource} encapsulating the opened resource stream and associated metadata
     *      or {@code null} if opening the resource stream failed.
     */
    public static Resource openResource(Activity activity, Uri uri) {
        try {
            String mimetype = activity.getContentResolver().getType(uri);

            // try to find the mimetype from the filename
            if (null == mimetype) {
                String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
                if (extension != null) {
                    mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                }
            }

            return new Resource(
                    activity.getContentResolver().openInputStream(uri),
                    mimetype);

        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to open resource input stream", e);
        }

        return null;
    }
}
