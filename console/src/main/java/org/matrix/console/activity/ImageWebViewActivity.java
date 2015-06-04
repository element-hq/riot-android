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

package org.matrix.console.activity;

import java.io.File;
import java.io.FileInputStream;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;

import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;

import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.util.ImageUtils;
import org.matrix.androidsdk.view.PieFractionView;
import org.matrix.console.Matrix;
import org.matrix.console.R;

public class ImageWebViewActivity extends Activity {
    private static final String LOG_TAG = "ImageWebViewActivity";

    public static final String KEY_HIGHRES_IMAGE_URI = "org.matrix.console.activity.ImageWebViewActivity.KEY_HIGHRES_IMAGE_URI";
    public static final String KEY_THUMBNAIL_WIDTH = "org.matrix.console.activity.ImageWebViewActivity.KEY_THUMBNAIL_WIDTH";
    public static final String KEY_THUMBNAIL_HEIGHT = "org.matrix.console.activity.ImageWebViewActivity.KEY_THUMBNAIL_HEIGHT";
    public static final String KEY_HIGHRES_MIME_TYPE = "org.matrix.console.activity.ImageWebViewActivity.KEY_HIGHRES_MIME_TYPE";
    public static final String KEY_IMAGE_ROTATION = "org.matrix.console.activity.ImageWebViewActivity.KEY_IMAGE_ROTATION";
    public static final String KEY_IMAGE_ORIENTATION = "org.matrix.console.activity.ImageWebViewActivity.KEY_IMAGE_ORIENTATION";

    private WebView mWebView;

    private int mRotationAngle = 0;
    private int mOrientation = ExifInterface.ORIENTATION_UNDEFINED;
    private String mThumbnailUri = null;
    private String mHighResUri = null;
    private String mHighResMimeType = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_web_view);

        mWebView = (WebView)findViewById(R.id.image_webview);
        
        final Intent intent = getIntent();
        if (intent == null) {
            Log.e(LOG_TAG, "Need an intent to view.");
            finish();
            return;
        }

        mHighResUri = intent.getStringExtra(KEY_HIGHRES_IMAGE_URI);
        mRotationAngle = intent.getIntExtra(KEY_IMAGE_ROTATION, 0);
        mOrientation = intent.getIntExtra(KEY_IMAGE_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        mHighResMimeType = intent.getStringExtra(KEY_HIGHRES_MIME_TYPE);

        if (mHighResUri == null) {
            Log.e(LOG_TAG, "No Image URI");
            finish();
            return;
        }

        int thumbnailWidth = intent.getIntExtra(KEY_THUMBNAIL_WIDTH, 0);
        int thumbnailHeight = intent.getIntExtra(KEY_THUMBNAIL_HEIGHT, 0);

        if ((thumbnailWidth <= 0) || (thumbnailHeight <= 0)) {
            Log.e(LOG_TAG, "Invalid thumbnail size");
            finish();
            return;
        }

        String css = "body { background-color: #000; height: 100%; width: 100%; margin: 0px; padding: 0px; }" +
                ".wrap { position: absolute; left: 0px; right: 0px; width: 100%; height: 100%; " +
                "display: -webkit-box; -webkit-box-pack: center; -webkit-box-align: center; " +
                "display: box; box-pack: center; box-align: center; } ";

        final MXMediasCache mediasCache = Matrix.getInstance(this).getMediasCache();
        File mediaFile = mediasCache.mediaCacheFile(mHighResUri, mHighResMimeType);

        // is the high picture already downloaded ?
        if (null != mediaFile) {
            mThumbnailUri = mHighResUri = "file://" + mediaFile.getPath();
        }

        // the rotation angle must be retrieved from the exif metadata
        if (mRotationAngle == Integer.MAX_VALUE) {
            if (null != mThumbnailUri) {
                mRotationAngle = ImageUtils.getRotationAngleForBitmap(this, Uri.parse(mThumbnailUri));
            } else {
                mRotationAngle = 0;
            }
        }

        if (mRotationAngle != 0) {
            // get the image size to scale it to fill in the device screen.
            int imageWidth = thumbnailWidth;
            int imageHeight = thumbnailHeight;

            try {
                Uri uri = Uri.parse(mHighResUri);

                FileInputStream imageStream = new FileInputStream(new File(uri.getPath()));
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                options.outWidth = -1;
                options.outHeight = -1;

                // get the full size bitmap
                Bitmap fullSizeBitmap = null;
                try {
                    fullSizeBitmap = BitmapFactory.decodeStream(imageStream, null, options);
                } catch (OutOfMemoryError e) {
                    Log.e(LOG_TAG, "Onclick BitmapFactory.decodeStream : " + e.getMessage());
                }

                imageWidth = options.outWidth;
                imageHeight =  options.outHeight;

                imageStream.close();
                fullSizeBitmap.recycle();
            } catch (Exception e) {
            }

            String cssRotation = calcCssRotation(mRotationAngle, imageWidth, imageHeight);


            css += "#image { " + cssRotation + " } ";
            css += "#thumbnail { " + cssRotation + " } ";
        }

        final String fcss= css;
        final String viewportContent = "width=640";

        final PieFractionView pieFractionView = (PieFractionView)findViewById(R.id.download_zoomed_image_piechart);

        // is the high picture already downloaded ?
        if (null != mediaFile) {
            pieFractionView.setVisibility(View.GONE);
        } else {
            mThumbnailUri = null;

            // try to retrieve the thumbnail
            mediaFile = mediasCache.mediaCacheFile(mHighResUri, thumbnailWidth, thumbnailHeight, null);
            if (null == mediaFile) {
                Log.e(LOG_TAG, "No Image thumbnail");
                finish();
                return;
            }

            final String loadingUri = mHighResUri;
            mThumbnailUri = mHighResUri = "file://" + mediaFile.getPath();

            final String downloadId = mediasCache.loadBitmap(this, loadingUri, mRotationAngle, mOrientation, mHighResMimeType);

            if (null != downloadId) {
                pieFractionView.setFraction(mediasCache.progressValueForDownloadId(downloadId));

                mediasCache.addDownloadListener(downloadId, new MXMediasCache.DownloadCallback() {
                    @Override
                    public void onDownloadProgress(String aDownloadId, int percentageProgress) {
                        if (aDownloadId.equals(downloadId)) {
                            pieFractionView.setFraction(percentageProgress);
                        }
                    }

                    @Override
                    public void onDownloadComplete(String aDownloadId) {
                        if (aDownloadId.equals(downloadId)) {
                            pieFractionView.setVisibility(View.GONE);

                            final File mediaFile = mediasCache.mediaCacheFile(loadingUri, mHighResMimeType);

                            if (null != mediaFile) {
                                Uri uri = Uri.fromFile(mediaFile);
                                mHighResUri = uri.toString();

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Uri mediaUri = Uri.parse(mHighResUri);

                                        // save in the gallery
                                        CommonActivityUtils.saveImageIntoGallery(ImageWebViewActivity.this, mediaFile);

                                        // refresh the UI
                                        loadImage(mediaUri, viewportContent, fcss);
                                    }
                                });
                            }
                        }
                    }
                });
            }
        }

        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setLoadWithOverviewMode(true);
        mWebView.getSettings().setUseWideViewPort(true);
        mWebView.getSettings().setBuiltInZoomControls(true);

        loadImage(Uri.parse(mHighResUri), viewportContent, css);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ( keyCode == KeyEvent.KEYCODE_MENU ) {
            // This is to fix a bug in the v7 support lib. If there is no options menu and you hit MENU, it will crash with a
            // NPE @ android.support.v7.app.ActionBarImplICS.getThemedContext(ActionBarImplICS.java:274)
            // This can safely be removed if we add in menu options on this screen
            return true;
        }
        return super.onKeyDown(keyCode, event);
    } 
    
    private void loadImage(Uri imageUri, String viewportContent, String css) {
        String html =
                "<html><head><meta name='viewport' content='" +
                        viewportContent +
                        "'/>" +
                        "<style type='text/css'>" +
                        css +
                        "</style></head>" +
                        "<body> <div class='wrap'>" + "<img " +
                        ( "src='" + imageUri.toString() + "'") +
                        " onerror='this.style.display=\"none\"' id='image' " + viewportContent + "/>" + "</div>" +
                        "</body>" + "</html>";

        String mime = "text/html";
        String encoding = "utf-8";

        Log.i(LOG_TAG, html);
        mWebView.loadDataWithBaseURL(null, html, mime, encoding, null);
        mWebView.requestLayout();
    }

    private Point getDisplaySize() {
        Point size = new Point();
        WindowManager w = getWindowManager();

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)    {
            w.getDefaultDisplay().getSize(size);
        }else{
            Display d = w.getDefaultDisplay();
            size.x = d.getWidth();
            size.y = d.getHeight();
        }

        return size;
    }

    private String calcCssRotation(int rot, int imageWidth, int imageHeight) {
        if (rot == 90 || rot == 180 || rot == 270) {
            Point displaySize = getDisplaySize();
            double scale = Math.min((double)imageWidth / imageHeight, (double)displaySize.y / displaySize.x);

            final String rot180 = "-webkit-transform: rotate(180deg);";

            switch (rot) {
            case 90:
                return "-webkit-transform-origin: 50% 50%; -webkit-transform: rotate(90deg) scale(" + scale + " , " + scale + ");";
            case 180:
                return rot180;
            case 270:
                return "-webkit-transform-origin: 50% 50%; -webkit-transform: rotate(270deg) scale(" + scale + " , " + scale + ");";
            }
        }
        return "";
    }
}
