/* 
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.db;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;

import im.vector.VectorApp;

public class VectorContentProvider extends ContentProvider {
    public static final String AUTHORITIES = "im.vector.VectorApp.provider";

    private static final String BUG_SEPARATOR = "bugreport";

    /**
     * Convert an absolute file path to a Content path
     * @param context the application context
     * @param path the absolute path to convert.
     * @return the content URI.
     */
    public static Uri absolutePathToUri(Context context, String path) {
        if (null == path) {
            return null;
        }

        String attachmentsBasePath = context.getFilesDir().getAbsolutePath();

        if (path.startsWith(attachmentsBasePath)) {
            return Uri.parse("content://" + VectorContentProvider.AUTHORITIES + path.substring(attachmentsBasePath.length()));
        }

        if (null != VectorApp.mLogsDirectoryFile) {
            String logBasePath = VectorApp.mLogsDirectoryFile.getAbsolutePath();

            if (path.startsWith(logBasePath)) {
                return Uri.parse("content://" + VectorContentProvider.AUTHORITIES + "/" + BUG_SEPARATOR + path.substring(logBasePath.length()));
            }
        }

        return null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        try {
            File privateFile = null;

            if (uri.getPath().contains("/" + BUG_SEPARATOR + "/")) {
                if (null != VectorApp.mLogsDirectoryFile) {
                    privateFile = new File(VectorApp.mLogsDirectoryFile, uri.getLastPathSegment());
                }
            } else {
                privateFile = new File(getContext().getFilesDir(), uri.getPath());
            }

            if (privateFile.exists()) {
                return ParcelFileDescriptor.open(privateFile, ParcelFileDescriptor.MODE_READ_ONLY);
            }
        } catch (Exception e) {
        }

        return null;
    }

    @Override
    public int delete(Uri arg0, String arg1, String[] arg2) {
        return 0;
    }

    @Override
    public String getType(Uri arg0) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(arg0.toString().toLowerCase());
        if (extension != null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            type = mime.getMimeTypeFromExtension(extension);
        }

        return type;
    }

    @Override
    public Uri insert(Uri arg0, ContentValues arg1) {
        return null;
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public Cursor query(Uri arg0, String[] arg1, String arg2, String[] arg3,
                        String arg4) {
        return null;
    }

    @Override
    public int update(Uri arg0, ContentValues arg1, String arg2, String[] arg3) {
        return 0;
    }
}
