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

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * SharedDataItem defines the 3rd party shared items
 */
public class SharedDataItem {

    private static String LOG_TAG = "SharedDataItem";

    // the item is defined either from an uri
    private Uri mUri;
    private String mMimeType;

    // or a clipdataItem
    private ClipData.Item mClipDataItem;

    // the filename
    private String mFileName;

    /**
     * Constructor
     *
     * @param clipDataItem the data item
     * @param mimeType     the mime type
     */
    public SharedDataItem(ClipData.Item clipDataItem, String mimeType) {
        mClipDataItem = clipDataItem;
        mMimeType = mimeType;
    }

    /**
     * Constructor
     *
     * @param uri the media uri
     */
    public SharedDataItem(Uri uri) {
        mUri = uri;
    }

    /**
     * Retrieve the raw text contained in this Item.
     */
    public CharSequence getText() {
        if (null != mClipDataItem) {
            return mClipDataItem.getText();
        }
        return null;
    }

    /**
     * Retrieve the raw HTML text contained in this Item.
     */
    public String getHtmlText() {
        if (null != mClipDataItem) {
            return mClipDataItem.getHtmlText();
        }

        return null;
    }

    /**
     * Retrieve the raw Intent contained in this Item.
     */
    public Intent getIntent() {
        if (null != mClipDataItem) {
            return mClipDataItem.getIntent();
        }

        return null;
    }

    /**
     * Retrieve the raw URI contained in this Item.
     */
    public Uri getUri() {
        if (null != mUri) {
            return mUri;
        } else if (null != mClipDataItem) {
            return mClipDataItem.getUri();
        }

        return null;
    }

    /**
     * Returns the mimetype.
     *
     * @param context the context
     * @return the mimetype
     */
    public String getMimeType(Context context) {
        if ((null == mMimeType) && (null != getUri())) {
            try {
                Uri uri = getUri();
                mMimeType = context.getContentResolver().getType(uri);

                // try to find the mimetype from the filename
                if (null == mMimeType) {
                    String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString().toLowerCase());
                    if (extension != null) {
                        mMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to open resource input stream", e);
            }
        }

        return null;
    }

    /**
     * @return the filename
     */
    public String getFileName(Context context) {
        if ((null != mFileName) && (null != getUri())) {
            Uri mediaUri = getUri();

            if (null != mediaUri) {
                if (mediaUri.toString().startsWith("content://")) {
                    Cursor cursor = null;
                    try {
                        cursor = context.getContentResolver().query(mediaUri, null, null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            mFileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "cursor.getString " + e.getMessage());
                    } finally {
                        if (null != cursor) {
                            cursor.close();
                        }
                    }

                    if (TextUtils.isEmpty(mFileName)) {
                        List uriPath = mediaUri.getPathSegments();
                        mFileName = (String) uriPath.get(uriPath.size() - 1);
                    }
                } else if (mediaUri.toString().startsWith("file://")) {
                    // try to retrieve the filename from the file url.
                    try {
                        mFileName = mediaUri.getLastPathSegment();
                    } catch (Exception e) {
                    }

                    if (TextUtils.isEmpty(mFileName)) {
                        mFileName = null;
                    }
                }
            }
        }

        return mFileName;
    }

    /**
     * Save a media into a dedicated folder
     *
     * @param context the context
     * @param folder  the folder.
     */
    public void saveMedia(Context context, File folder) {
        mFileName = null;
        Uri mediaUri = getUri();

        if (null != mediaUri) {
            try {
                ResourceUtils.Resource resource = ResourceUtils.openResource(context, mediaUri);

                if (null == resource) {
                } else {
                    mUri = saveFile(folder, resource.contentStream, mFileName, resource.mimeType);
                    resource.contentStream.close();
                }
            } catch (Exception e) {
            }
        }
    }

    /**
     * Save a file in a dedicated directory.
     * The filename is optional.
     * @param folder the destinated folder
     * @param stream teh file stream
     * @param defaultFileName the filename, null to generate a new one
     * @param mimeType the file mimetype.
     * @return the file uri
     */
    private static final Uri saveFile(File folder, InputStream stream, String defaultFileName, String mimeType) {
        String filename = defaultFileName;

        if (null == filename) {
            filename = "file" + System.currentTimeMillis();

            if (null != mimeType) {
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);

                if (null != extension) {
                    filename += "." + extension;
                }
            }
        }

        Uri fileUri = null;

        try {
            File file = new File(folder, filename);

            // if the file exits, delete it
            if (file.exists()) {
                file.delete();
            }

            FileOutputStream fos = new FileOutputStream(file.getPath());

            try {
                byte[] buf = new byte[1024 * 32];

                int len;
                while ((len = stream.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
            } catch (Exception e) {
            }

            fos.flush();
            fos.close();
            stream.close();

            fileUri = Uri.fromFile(file);
        } catch (Exception e) {
        }

        return fileUri;
    }

    /**
     * List the item provided in an intent.
     * @param intent the intent.
     * @return the SharedDataItem list
     */
    public static List<SharedDataItem> listSharedDataItems(Intent intent) {
        ArrayList<SharedDataItem> sharedDataItems = new ArrayList<SharedDataItem>();

        if (null != intent) {
            ClipData clipData = null;
            ArrayList<String> mimetypes = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                clipData = intent.getClipData();
            }

            // multiple data
            if (null != clipData) {
                if (null != clipData.getDescription()) {
                    if (0 != clipData.getDescription().getMimeTypeCount()) {
                        mimetypes = new ArrayList<String>();

                        for(int i = 0; i < clipData.getDescription().getMimeTypeCount(); i++) {
                            mimetypes.add(clipData.getDescription().getMimeType(i));
                        }

                        // if the filter is "accept anything" the mimetype does not make sense
                        if (1 == mimetypes.size()) {
                            if (mimetypes.get(0).endsWith("/*")) {
                                mimetypes = null;
                            }
                        }
                    }
                }

                int count = clipData.getItemCount();

                for (int i = 0; i < count; i++) {
                    ClipData.Item item = clipData.getItemAt(i);
                    String mimetype = null;

                    if (null != mimetypes) {
                        if (i < mimetypes.size()) {
                            mimetype = mimetypes.get(i);
                        } else {
                            mimetype = mimetypes.get(0);
                        }
                    }

                    sharedDataItems.add(new SharedDataItem(item, mimetype));
                }
            } else if (null != intent.getData()) {
                sharedDataItems.add(new SharedDataItem(intent.getData()));
            }
        }

        return sharedDataItems;
    }
}
