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

package im.vector.activity;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.util.ContentUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import im.vector.Matrix;
import im.vector.util.ResourceUtils;
import im.vector.util.VectorUtils;

/**
 * Dummy activity used to manage the shared
 */
public class VectorSharedFilesActivity extends Activity {
    private static final String LOG_TAG = "VectorSharedFilesAct";

    final String SHARED_FOLDER = "VectorShared";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // retrieve the current intent
        Intent anIntent = getIntent();

        if (null != anIntent) {
            String action = anIntent.getAction();
            String type = anIntent.getType();

            Log.d(LOG_TAG, "onCreate : action " + action + " type " + type);

            // send files from external application
            // check the params
            if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
                boolean hasCredentials = false;
                boolean isLaunched = false;

                try {
                    MXSession session = Matrix.getInstance(this).getDefaultSession();

                    if (null != session) {
                        hasCredentials = true;
                        isLaunched = session.getDataHandler().getStore().isReady();
                    }
                } catch (Exception e) {
                }

                // go to the home screen if the application is launched
                if (hasCredentials) {
                    launchActivity(anIntent, isLaunched);
                } else {
                    Log.d(LOG_TAG, "onCreate : go to login screen");

                    // don't know what to do, go to the login screen
                    Intent loginIntent = new Intent(this, LoginActivity.class);
                    startActivity(loginIntent);
                }
            } else {
                Log.d(LOG_TAG, "onCreate : unsupported action");

                Intent homeIntent = new Intent(this, VectorHomeActivity.class);
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(homeIntent);
            }
        }  else {
            Log.d(LOG_TAG, "onCreate : null intent");

            Intent homeIntent = new Intent(this, VectorHomeActivity.class);
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
        }

        finish();
    }

    /**
     * Extract the medias list, copy them into a tmp directory and provide them to the home activity
     * @param intent the intent
     * @param isAppLaunched true if the application is resumed
     */
    private void launchActivity(Intent intent, boolean isAppLaunched) {
        File sharedFolder = new File(getCacheDir(), SHARED_FOLDER);

        /**
         * Clear the existing folder to reduce storage memory usage
         */
        if (sharedFolder.exists()) {
            ContentUtils.deleteDirectory(sharedFolder);
        }

        sharedFolder.mkdir();

        ArrayList<Uri> cachedFiles = new ArrayList<Uri>();
        List<Uri> uris = VectorUtils.listMediaUris(intent);

        // for security reason, the files must be copied before being forwarded to another activity
        // else it is not possible to read them
        if (null != uris) {
            Log.d(LOG_TAG, "launchHomeActivity : " + uris.size() + " media uris");

            for(Uri mediaUri : uris) {
                String filename = null;

                if (mediaUri.toString().startsWith("content://")) {
                    Cursor cursor = null;
                    try {
                        cursor = getContentResolver().query(mediaUri, null, null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            filename = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "cursor.getString " + e.getMessage());
                    } finally {
                        if (null != cursor) {
                            cursor.close();
                        }
                    }

                    if (TextUtils.isEmpty(filename)) {
                        List uriPath = mediaUri.getPathSegments();
                        filename = (String) uriPath.get(uriPath.size() - 1);
                    }
                } else if (mediaUri.toString().startsWith("file://")) {
                    // try to retrieve the filename from the file url.
                    try {
                        filename = mediaUri.getLastPathSegment();
                    } catch (Exception e) {
                    }

                    if (TextUtils.isEmpty(filename)) {
                        filename = null;
                    }
                }

                try {
                    ResourceUtils.Resource resource = ResourceUtils.openResource(this, mediaUri);

                    if (null == resource) {

                    } else {
                        Uri savedMediaUri = saveFile(sharedFolder, resource.contentStream, filename, resource.mimeType);

                        if (null != savedMediaUri) {
                            cachedFiles.add(savedMediaUri);
                        }
                        resource.contentStream.close();
                    }
                } catch (Exception e) {

                }
            }
        }

        Log.d(LOG_TAG, "onCreate : launch home activity with the files list " + cachedFiles.size() + " files");

        Intent activityIntent;

        if (isAppLaunched) {
            activityIntent = new Intent(this, VectorHomeActivity.class);
        } else {
            activityIntent = new Intent(this, SplashActivity.class);
        }

        activityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);

        if (0 != cachedFiles.size()) {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
            shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, cachedFiles);
            shareIntent.setType("*/*");

            // files to share
            activityIntent.putExtra(VectorHomeActivity.EXTRA_SHARED_INTENT_PARAMS, shareIntent);
        }

        startActivity(activityIntent);
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
}
