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

package org.matrix.console.adapters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.support.v4.view.PagerAdapter;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;

import com.google.android.gms.analytics.ExceptionReporter;

import org.matrix.androidsdk.util.ImageUtils;
import org.matrix.androidsdk.view.PieFractionView;
import org.matrix.console.Matrix;
import org.matrix.console.R;

import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.console.activity.CommonActivityUtils;
import org.matrix.console.util.SlidableImageInfo;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * An images slider
 */
public class ImagesSliderAdapter extends PagerAdapter {

    Context context;
    List<SlidableImageInfo> mListImageMessages = null;
    int mMaxImageWidth;
    int mMaxImageHeight;
    int mLastPrimaryItem = -1;
    ArrayList<Integer> mHighResMediaIndex = new ArrayList<Integer>();

    private LayoutInflater mLayoutInflater;

    public ImagesSliderAdapter(Context context, List<SlidableImageInfo> listImageMessages, int maxImageWidth, int maxImageHeight) {
        this.context = context;
        this.mListImageMessages = listImageMessages;
        this.mMaxImageWidth = maxImageWidth;
        this.mMaxImageHeight = maxImageHeight;
        this.mLayoutInflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return mListImageMessages.size();
    }

    @Override
    public void setPrimaryItem(ViewGroup container, final int position, Object object) {
        if (mLastPrimaryItem != position) {
            mLastPrimaryItem = position;
            //
            if (mHighResMediaIndex.indexOf(position) < 0) {
                final View view = (View)object;
                view.post(new Runnable() {
                    @Override
                    public void run() {
                       downloadHighResPict(view, position);
                    }
                });
            }
        }
    }

    private void downloadHighResPict(final View view, final int position) {
        final WebView webView = (WebView)view.findViewById(R.id.image_webview);
        final PieFractionView pieFractionView = (PieFractionView)view.findViewById(R.id.download_zoomed_image_piechart);
        final MXMediasCache mediasCache = Matrix.getInstance(this.context).getMediasCache();
        final SlidableImageInfo imageInfo = mListImageMessages.get(position);
        final String viewportContent = "width=640";
        final String loadingUri = imageInfo.mImageUrl;
        final String downloadId = mediasCache.loadBitmap(this.context, loadingUri, imageInfo.mRotationAngle, imageInfo.mOrientation, imageInfo.mMimeType);

        if (null != downloadId) {
            pieFractionView.setVisibility(View.VISIBLE);

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

                        final File mediaFile = mediasCache.mediaCacheFile(loadingUri, imageInfo.mMimeType);

                        if (null != mediaFile) {
                            mHighResMediaIndex.add(position);

                            Uri uri = Uri.fromFile(mediaFile);
                            final String newHighResUri = uri.toString();

                            webView.post(new Runnable() {
                                @Override
                                public void run() {
                                    Uri mediaUri = Uri.parse(newHighResUri);

                                    // save in the gallery
                                    //CommonActivityUtils.saveImageIntoGallery(ImagesSliderAdapter.this.context, mediaFile);

                                    // refresh the UI
                                    loadImage(webView, mediaUri, viewportContent, computeCss(newHighResUri, ImagesSliderAdapter.this.mMaxImageWidth, ImagesSliderAdapter.this.mMaxImageHeight, imageInfo.mRotationAngle));
                                }
                            });
                        }
                    }
                }
            });
        }
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    @Override
    public Object instantiateItem(ViewGroup container, final int position) {
        View view  = mLayoutInflater.inflate(R.layout.activity_image_web_view, null, false);

        // hide the pie chart
        final PieFractionView pieFractionView = (PieFractionView)view.findViewById(R.id.download_zoomed_image_piechart);
        pieFractionView.setVisibility(View.GONE);

        final WebView webView = (WebView)view.findViewById(R.id.image_webview);

        // black background
        view.setBackgroundColor(0xFF000000);
        webView.setBackgroundColor(0xFF000000);

        final SlidableImageInfo imageInfo = mListImageMessages.get(position);

        String mediaUrl = imageInfo.mImageUrl;
        final int rotationAngle = imageInfo.mRotationAngle;
        final String mimeType = imageInfo.mMimeType;

        final MXMediasCache mediasCache = Matrix.getInstance(this.context).getMediasCache();
        File mediaFile = mediasCache.mediaCacheFile(mediaUrl, mimeType);

        // is the high picture already downloaded ?
        if (null != mediaFile) {
            if (mHighResMediaIndex.indexOf(position) < 0) {
                mHighResMediaIndex.add(position);
            }
        } else {
            // try to retrieve the thumbnail
            mediaFile = mediasCache.mediaCacheFile(mediaUrl, mMaxImageWidth, mMaxImageHeight, null);
        }

        // the thumbnail is not yet downloaded
        if (null == mediaFile) {
            // display nothing
            container.addView(view, 0);
            return view;
        }

        String mediaUri = "file://" + mediaFile.getPath();

        String css = computeCss(mediaUri, mMaxImageWidth, mMaxImageHeight, rotationAngle);
        final String viewportContent = "width=640";

        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setBuiltInZoomControls(true);

        loadImage(webView, Uri.parse(mediaUri), viewportContent, css);

        container.addView(view, 0);
        return view;
    }

    private void loadImage(WebView webView, Uri imageUri, String viewportContent, String css) {
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

        webView.loadDataWithBaseURL(null, html, mime, encoding, null);
        webView.requestLayout();
    }

    private String computeCss(String mediaUrl, int thumbnailWidth, int thumbnailHeight, int rotationAngle) {
        String css = "body { background-color: #000; height: 100%; width: 100%; margin: 0px; padding: 0px; }" +
                ".wrap { position: absolute; left: 0px; right: 0px; width: 100%; height: 100%; " +
                "display: -webkit-box; -webkit-box-pack: center; -webkit-box-align: center; " +
                "display: box; box-pack: center; box-align: center; } ";

        Uri mediaUri = null;

        try {
            mediaUri = Uri.parse(mediaUrl);
        } catch (Exception e) {
        }

        if (null == mediaUri) {
            return css;
        }

        // the rotation angle must be retrieved from the exif metadata
        if (rotationAngle == Integer.MAX_VALUE) {
            if (null != mediaUrl) {
                rotationAngle = ImageUtils.getRotationAngleForBitmap(this.context, mediaUri);
            }
        }

        if (rotationAngle != 0) {
            // get the image size to scale it to fill in the device screen.
            int imageWidth = thumbnailWidth;
            int imageHeight = thumbnailHeight;

            try {
                FileInputStream imageStream = new FileInputStream(new File(mediaUri.getPath()));
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
                }

                imageWidth = options.outWidth;
                imageHeight =  options.outHeight;

                imageStream.close();
                fullSizeBitmap.recycle();
            } catch (Exception e) {
            }

            String cssRotation = calcCssRotation(rotationAngle, imageWidth, imageHeight);

            css += "#image { " + cssRotation + " } ";
            css += "#thumbnail { " + cssRotation + " } ";
        }

        return css;
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

    @SuppressLint("NewApi")
    private Point getDisplaySize() {
        Point size = new Point();
        WindowManager w = ((Activity)context).getWindowManager();

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)    {
            w.getDefaultDisplay().getSize(size);
        } else {
            Display d = w.getDefaultDisplay();
            size.x = d.getWidth();
            size.y = d.getHeight();
        }

        return size;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
    }
}
