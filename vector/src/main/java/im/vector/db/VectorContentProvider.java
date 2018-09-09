/*
 * Copyright 2015 OpenMarket Ltd
 * Copyright 2018 New Vector Ltd
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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;

import im.vector.VectorApp;
import im.vector.BuildConfig;

public class VectorContentProvider extends ContentProvider {
    private static final String LOG_TAG = VectorContentProvider.class.getSimpleName();

    private static final String AUTHORITIES = BuildConfig.APPLICATION_ID + ".provider";

    private static final String BUG_SEPARATOR = "bugreport";

    /**
     * Convert an absolute file path to a Content path
     *
     * @param context the application context
     * @param path    the absolute path to convert.
     * @return the content URI.
     */
    @Nullable
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
    @Nullable
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        File privateFile = null;

        if (uri.getPath().contains("/" + BUG_SEPARATOR + "/")) {
            if (null != VectorApp.mLogsDirectoryFile) {
                privateFile = new File(VectorApp.mLogsDirectoryFile, uri.getLastPathSegment());
            }
        } else if (getContext() != null) {
            privateFile = new File(getContext().getFilesDir(), uri.getPath());
        }

        if (privateFile != null && privateFile.exists()) {
            return ParcelFileDescriptor.open(privateFile, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    @Nullable
    public String getType(@NonNull Uri uri) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString().toLowerCase(VectorApp.getApplicationLocale()));
        if (extension != null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            type = mime.getMimeTypeFromExtension(extension);
        }

        return type;
    }

    @Override
    @Nullable
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    @Nullable
    public Cursor query(@NonNull Uri uri,
                        @Nullable String[] projection,
                        @Nullable String selection,
                        @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        return null;
    }

    @Override
    public int update(@NonNull Uri uri,
                      @Nullable ContentValues values,
                      @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}
