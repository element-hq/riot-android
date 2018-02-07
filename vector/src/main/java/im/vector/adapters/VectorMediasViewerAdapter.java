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

package im.vector.adapters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.v4.view.PagerAdapter;
import android.text.TextUtils;

import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.crypto.EncryptedFileInfo;
import org.matrix.androidsdk.util.Log;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import com.google.gson.JsonElement;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.listeners.MXMediaDownloadListener;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.util.ImageUtils;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.view.PieFractionView;

import im.vector.R;

import org.matrix.androidsdk.db.MXMediasCache;

import im.vector.activity.CommonActivityUtils;
import im.vector.util.SlidableMediaInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * An images slider
 */
public class VectorMediasViewerAdapter extends PagerAdapter {
    private static final String LOG_TAG = VectorMediasViewerAdapter.class.getSimpleName();

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;

    // medias
    private List<SlidableMediaInfo> mMediasMessagesList = null;
    private final int mMaxImageWidth;
    private final int mMaxImageHeight;
    private int mLatestPrimaryItemPosition = -1;
    private View mLatestPrimaryView = null;
    private final MXMediasCache mMediasCache;
    private final ArrayList<Integer> mHighResMediaIndex = new ArrayList<>();
    // current playing video
    private VideoView mPlayingVideoView = null;
    private final MXSession mSession;

    private int mAutoPlayItemAt = -1;

    public VectorMediasViewerAdapter(Context context, MXSession session, MXMediasCache mediasCache, List<SlidableMediaInfo> mediaMessagesList, int maxImageWidth, int maxImageHeight) {
        this.mContext = context;
        this.mSession = session;
        this.mMediasMessagesList = mediaMessagesList;
        this.mMaxImageWidth = maxImageWidth;
        this.mMaxImageHeight = maxImageHeight;
        this.mLayoutInflater = LayoutInflater.from(context);
        this.mMediasCache = mediasCache;
    }

    @Override
    public int getCount() {
        return mMediasMessagesList.size();
    }


    @Override
    public void setPrimaryItem(ViewGroup container, final int position, Object object) {
        if (mLatestPrimaryItemPosition != position) {
            mLatestPrimaryItemPosition = position;

            final View view = (View) object;
            mLatestPrimaryView = view;

            view.findViewById(R.id.media_download_failed).setVisibility(View.GONE);

            view.post(new Runnable() {
                @Override
                public void run() {
                    stopPlayingVideo();
                }
            });

            view.post(new Runnable() {
                @Override
                public void run() {
                    if (mHighResMediaIndex.indexOf(position) < 0) {
                        downloadHighResMedia(view, position);
                    } else if (position == mAutoPlayItemAt) {
                        final SlidableMediaInfo mediaInfo = mMediasMessagesList.get(position);

                        if (mediaInfo.mMessageType.equals(Message.MSGTYPE_VIDEO)) {
                            final VideoView videoView = view.findViewById(R.id.media_slider_videoview);

                            if (mMediasCache.isMediaCached(mediaInfo.mMediaUrl, mediaInfo.mMimeType)) {
                                mMediasCache.createTmpMediaFile(mediaInfo.mMediaUrl, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo, new SimpleApiCallback<File>() {
                                    @Override
                                    public void onSuccess(File file) {
                                        if (null != file) {
                                            playVideo(view, videoView, file, mediaInfo.mMimeType);
                                        }
                                    }
                                });
                            }
                        }
                        mAutoPlayItemAt = -1;
                    }
                }
            });
        }
    }

    /**
     * @param position the position of the item to play.
     */
    public void autoPlayItemAt(int position) {
        mAutoPlayItemAt = position;
    }

    /**
     * Download the media if it was not yet done
     *
     * @param view     the slider page view
     * @param position the item position
     */
    private void downloadHighResMedia(final View view, final int position) {
        SlidableMediaInfo imageInfo = mMediasMessagesList.get(position);

        // image
        if (imageInfo.mMessageType.equals(Message.MSGTYPE_IMAGE)) {
            //
            if (TextUtils.isEmpty(imageInfo.mMimeType)) {
                imageInfo.mMimeType = "image/jpeg";
            }
            downloadHighResPict(view, position);
        } else {
            downloadVideo(view, position);
        }
    }

    /**
     * Download the video file.
     * The download will only start if the video should be auto played.
     *
     * @param view     the slider page view
     * @param position the item position
     */
    private void downloadVideo(final View view, final int position) {
        downloadVideo(view, position, false);
    }

    /**
     * Download the video file
     *
     * @param view     the slider page view
     * @param position the item position
     * @param force    true to do not check the auto playmode
     */
    private void downloadVideo(final View view, final int position, boolean force) {
        final VideoView videoView = view.findViewById(R.id.media_slider_videoview);
        final ImageView thumbView = view.findViewById(R.id.media_slider_video_thumbnail);
        final PieFractionView pieFractionView = view.findViewById(R.id.media_slider_piechart);
        final View downloadFailedView = view.findViewById(R.id.media_download_failed);

        final SlidableMediaInfo mediaInfo = mMediasMessagesList.get(position);
        final String loadingUri = mediaInfo.mMediaUrl;
        final String thumbnailUrl = mediaInfo.mThumbnailUrl;

        // check if the media has been downloaded
        if (mMediasCache.isMediaCached(loadingUri, mediaInfo.mMimeType)) {
            mMediasCache.createTmpMediaFile(loadingUri, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo, new SimpleApiCallback<File>() {
                @Override
                public void onSuccess(File file) {
                    if (null != file) {
                        mHighResMediaIndex.add(position);
                        loadVideo(position, view, thumbnailUrl, Uri.fromFile(file).toString(), mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo);

                        if (position == mAutoPlayItemAt) {
                            playVideo(view, videoView, file, mediaInfo.mMimeType);
                        }
                        mAutoPlayItemAt = -1;
                    }
                }
            });
            return;
        }

        // the video download starts only when the user taps on click
        // let assumes it might configurable
        if (!force && (mAutoPlayItemAt != position)) {
            return;
        }

        // else download it
        String downloadId = mMediasCache.downloadMedia(mContext, mSession.getHomeServerConfig(), loadingUri, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo);

        if (null != downloadId) {
            pieFractionView.setVisibility(View.VISIBLE);
            pieFractionView.setFraction(mMediasCache.getProgressValueForDownloadId(downloadId));
            pieFractionView.setTag(downloadId);

            mMediasCache.addDownloadListener(downloadId, new MXMediaDownloadListener() {

                @Override
                public void onDownloadError(String downloadId, JsonElement jsonElement) {
                    MatrixError error = JsonUtils.toMatrixError(jsonElement);

                    if ((null != error) && error.isSupportedErrorCode()) {
                        Toast.makeText(VectorMediasViewerAdapter.this.mContext, error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }

                    downloadFailedView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onDownloadProgress(String aDownloadId, DownloadStats stats) {
                    if (aDownloadId.equals(pieFractionView.getTag())) {
                        pieFractionView.setFraction(stats.mProgress);
                    }
                }

                @Override
                public void onDownloadComplete(String aDownloadId) {
                    if (aDownloadId.equals(pieFractionView.getTag())) {
                        pieFractionView.setVisibility(View.GONE);


                        // check if the media has been downloaded
                        if (mMediasCache.isMediaCached(loadingUri, mediaInfo.mMimeType)) {
                            mMediasCache.createTmpMediaFile(loadingUri, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo, new SimpleApiCallback<File>() {
                                @Override
                                public void onSuccess(final File mediaFile) {
                                    if (null != mediaFile) {
                                        mHighResMediaIndex.add(position);

                                        Uri uri = Uri.fromFile(mediaFile);
                                        final String newHighResUri = uri.toString();

                                        thumbView.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                loadVideo(position, view, thumbnailUrl, newHighResUri, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo);

                                                if (position == mAutoPlayItemAt) {
                                                    playVideo(view, videoView, mediaFile, mediaInfo.mMimeType);
                                                    mAutoPlayItemAt = -1;
                                                }
                                            }
                                        });
                                    }
                                }
                            });
                        } else {
                            downloadFailedView.setVisibility(View.VISIBLE);
                        }
                    }
                }
            });
        }
    }

    /**
     * Download the high res image
     *
     * @param view     the slider page view
     * @param position the item position
     */
    private void downloadHighResPict(final View view, final int position) {
        final WebView webView = view.findViewById(R.id.media_slider_image_webview);
        final PieFractionView pieFractionView = view.findViewById(R.id.media_slider_piechart);
        final View downloadFailedView = view.findViewById(R.id.media_download_failed);

        final SlidableMediaInfo imageInfo = mMediasMessagesList.get(position);
        final String viewportContent = "width=640";
        final String loadingUri = imageInfo.mMediaUrl;
        final String downloadId = mMediasCache.loadBitmap(mContext, mSession.getHomeServerConfig(), loadingUri, imageInfo.mRotationAngle, imageInfo.mOrientation, imageInfo.mMimeType, imageInfo.mEncryptedFileInfo);

        webView.getSettings().setDisplayZoomControls(false);

        if (null != downloadId) {
            pieFractionView.setVisibility(View.VISIBLE);
            pieFractionView.setFraction(mMediasCache.getProgressValueForDownloadId(downloadId));
            mMediasCache.addDownloadListener(downloadId, new MXMediaDownloadListener() {
                @Override
                public void onDownloadError(String aDownloadId, JsonElement jsonElement) {
                    if (aDownloadId.equals(downloadId)) {
                        pieFractionView.setVisibility(View.GONE);

                        MatrixError error = JsonUtils.toMatrixError(jsonElement);

                        if (null != error) {
                            Toast.makeText(VectorMediasViewerAdapter.this.mContext, error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        }

                        downloadFailedView.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onDownloadProgress(String aDownloadId, DownloadStats stats) {
                    if (aDownloadId.equals(downloadId)) {
                        pieFractionView.setFraction(stats.mProgress);
                    }
                }

                @Override
                public void onDownloadComplete(String aDownloadId) {
                    if (aDownloadId.equals(downloadId)) {
                        pieFractionView.setVisibility(View.GONE);

                        if (mMediasCache.isMediaCached(loadingUri, imageInfo.mMimeType)) {
                            mMediasCache.createTmpMediaFile(loadingUri, imageInfo.mMimeType, imageInfo.mEncryptedFileInfo, new SimpleApiCallback<File>() {
                                @Override
                                public void onSuccess(File mediaFile) {
                                    if (null != mediaFile) {
                                        mHighResMediaIndex.add(position);

                                        Uri uri = Uri.fromFile(mediaFile);
                                        final String newHighResUri = uri.toString();

                                        webView.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                Uri mediaUri = Uri.parse(newHighResUri);
                                                // refresh the UI
                                                loadImage(webView, mediaUri, viewportContent, computeCss(newHighResUri, VectorMediasViewerAdapter.this.mMaxImageWidth, VectorMediasViewerAdapter.this.mMaxImageHeight, imageInfo.mRotationAngle));
                                            }
                                        });
                                    }
                                }
                            });
                        } else {
                            downloadFailedView.setVisibility(View.VISIBLE);
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
    public Object instantiateItem(final ViewGroup container, final int position) {
        final View view = mLayoutInflater.inflate(R.layout.adapter_vector_medias_viewer, null, false);

        // hide the pie chart
        final PieFractionView pieFractionView = view.findViewById(R.id.media_slider_piechart);
        pieFractionView.setVisibility(View.GONE);

        view.findViewById(R.id.media_download_failed).setVisibility(View.GONE);

        final WebView imageWebView = view.findViewById(R.id.media_slider_image_webview);
        final View videoLayout = view.findViewById(R.id.media_slider_videolayout);
        final ImageView thumbView = view.findViewById(R.id.media_slider_video_thumbnail);

        imageWebView.getSettings().setDisplayZoomControls(false);

        imageWebView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                VectorMediasViewerAdapter.this.onLongClick();
                return true;
            }
        });

        thumbView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                VectorMediasViewerAdapter.this.onLongClick();
                return true;
            }
        });

        // black background
        view.setBackgroundColor(0xFF000000);
        imageWebView.setBackgroundColor(0xFF000000);
        videoLayout.setBackgroundColor(0xFF000000);

        final SlidableMediaInfo mediaInfo = mMediasMessagesList.get(position);
        String mediaUrl = mediaInfo.mMediaUrl;

        if (mediaInfo.mMessageType.equals(Message.MSGTYPE_IMAGE)) {
            imageWebView.setVisibility(View.VISIBLE);
            imageWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            imageWebView.getSettings().setJavaScriptEnabled(true);
            imageWebView.getSettings().setLoadWithOverviewMode(true);
            imageWebView.getSettings().setUseWideViewPort(true);
            imageWebView.getSettings().setBuiltInZoomControls(true);

            videoLayout.setVisibility(View.GONE);

            final int rotationAngle = mediaInfo.mRotationAngle;

            if (TextUtils.isEmpty(mediaInfo.mMimeType)) {
                mediaInfo.mMimeType = "image/jpeg";
            }

            final String mimeType = mediaInfo.mMimeType;
            int width = -1;
            int height = -1;

            // is the high picture already downloaded ?
            if (mMediasCache.isMediaCached(mediaUrl, mimeType)) {
                if (mHighResMediaIndex.indexOf(position) < 0) {
                    mHighResMediaIndex.add(position);
                }
            } else {
                width = mMaxImageWidth;
                height = mMaxImageHeight;
            }

            // the thumbnail is not yet downloaded
            if (!mMediasCache.isMediaCached(mediaUrl, width, height, mimeType)) {
                // display nothing
                container.addView(view, 0);
                return view;
            }

            mMediasCache.createTmpMediaFile(mediaUrl, width, height, mimeType, mediaInfo.mEncryptedFileInfo, new SimpleApiCallback<File>() {
                @Override
                public void onSuccess(File mediaFile) {
                    if (null != mediaFile) {
                        String mediaUri = "file://" + mediaFile.getPath();

                        String css = computeCss(mediaUri, mMaxImageWidth, mMaxImageHeight, rotationAngle);
                        final String viewportContent = "width=640";
                        loadImage(imageWebView, Uri.parse(mediaUri), viewportContent, css);
                        container.addView(view, 0);
                    }
                }
            });

        } else {
            loadVideo(position, view, mediaInfo.mThumbnailUrl, mediaUrl, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo);
            container.addView(view, 0);
        }

        // check if the media is downloading
        String downloadId = mMediasCache.downloadMedia(mContext, mSession.getHomeServerConfig(), mediaUrl, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo);

        if (null != downloadId) {
            pieFractionView.setVisibility(View.VISIBLE);
            pieFractionView.setFraction(mMediasCache.getProgressValueForDownloadId(downloadId));
            pieFractionView.setTag(downloadId);

            mMediasCache.addDownloadListener(downloadId, new MXMediaDownloadListener() {
                @Override
                public void onDownloadError(String downloadId, JsonElement jsonElement) {
                    pieFractionView.setVisibility(View.GONE);
                    MatrixError error = JsonUtils.toMatrixError(jsonElement);

                    if ((null != error) && error.isSupportedErrorCode()) {
                        Toast.makeText(VectorMediasViewerAdapter.this.mContext, error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onDownloadProgress(String aDownloadId, DownloadStats stats) {
                    if (aDownloadId.equals(pieFractionView.getTag())) {
                        pieFractionView.setFraction(stats.mProgress);
                    }
                }

                @Override
                public void onDownloadComplete(String aDownloadId) {
                    if (aDownloadId.equals(pieFractionView.getTag())) {
                        pieFractionView.setVisibility(View.GONE);
                    }
                }
            });
        }


        return view;
    }

    /**
     * Switch from the video view to the video thumbnail
     *
     * @param view    the page view
     * @param display true to display the video thumbnail, false to display the video player
     */
    private void displayVideoThumbnail(final View view, boolean display) {
        final VideoView videoView = view.findViewById(R.id.media_slider_videoview);
        final ImageView thumbView = view.findViewById(R.id.media_slider_video_thumbnail);
        final ImageView playView = view.findViewById(R.id.media_slider_video_playView);

        videoView.setVisibility(display ? View.GONE : View.VISIBLE);
        thumbView.setVisibility(display ? View.VISIBLE : View.GONE);
        playView.setVisibility(display ? View.VISIBLE : View.GONE);
    }

    /**
     * Stop any playing video
     */
    public void stopPlayingVideo() {
        if (null != mPlayingVideoView) {
            mPlayingVideoView.stopPlayback();
            displayVideoThumbnail((View) (mPlayingVideoView.getParent()), true);
            mPlayingVideoView = null;
        }
    }

    /**
     * Play a video.
     *
     * @param pageView      the pageView
     * @param videoView     the video view
     * @param videoFile     the video file
     * @param videoMimeType the video mime type
     */
    private void playVideo(View pageView, VideoView videoView, File videoFile, String videoMimeType) {
        if ((null != videoFile) && videoFile.exists()) {
            try {
                stopPlayingVideo();
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(videoMimeType);

                if (null != extension) {
                    extension += "." + extension;
                }

                // copy the media to ensure that it is deleted while playing
                File dstFile = new File(mContext.getCacheDir(), "sliderMedia" + extension);
                if (dstFile.exists()) {
                    dstFile.delete();
                }

                // Copy source file to destination
                FileInputStream inputStream = null;
                FileOutputStream outputStream = null;
                try {
                    // create only the
                    if (!dstFile.exists()) {
                        dstFile.createNewFile();

                        inputStream = new FileInputStream(videoFile);
                        outputStream = new FileOutputStream(dstFile);

                        byte[] buffer = new byte[1024 * 10];
                        int len;
                        while ((len = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, len);
                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## playVideo() : failed " + e.getMessage());
                    dstFile = null;
                } finally {
                    // Close resources
                    try {
                        if (inputStream != null) inputStream.close();
                        if (outputStream != null) outputStream.close();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## playVideo() : failed " + e.getMessage());
                    }
                }

                // update the source
                videoView.setVideoPath(dstFile.getAbsolutePath());
                // hide the thumbnail
                displayVideoThumbnail(pageView, false);

                // let's playing
                mPlayingVideoView = videoView;
                videoView.start();

            } catch (Exception e) {
                Log.e(LOG_TAG, "## playVideo() : videoView.start(); failed " + e.getMessage());
            }
        }
    }

    /**
     * Download the current video file
     */
    private void downloadMedia() {
        final SlidableMediaInfo mediaInfo = mMediasMessagesList.get(mLatestPrimaryItemPosition);

        if (mMediasCache.isMediaCached(mediaInfo.mMediaUrl, mediaInfo.mMimeType)) {
            mMediasCache.createTmpMediaFile(mediaInfo.mMediaUrl, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo, new SimpleApiCallback<File>() {
                @Override
                public void onSuccess(File file) {
                    if (null != file) {
                        CommonActivityUtils.saveMediaIntoDownloads(mContext, file, null, mediaInfo.mMimeType, new SimpleApiCallback<String>() {
                            @Override
                            public void onSuccess(String path) {
                                Toast.makeText(mContext, mContext.getText(R.string.media_slider_saved), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            });
        } else {
            downloadVideo(mLatestPrimaryView, mLatestPrimaryItemPosition, true);
            final String downloadId = mMediasCache.downloadMedia(mContext, mSession.getHomeServerConfig(), mediaInfo.mMediaUrl, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo);

            if (null != downloadId) {
                mMediasCache.addDownloadListener(downloadId, new MXMediaDownloadListener() {
                    @Override
                    public void onDownloadError(String downloadId, JsonElement jsonElement) {
                        MatrixError error = JsonUtils.toMatrixError(jsonElement);

                        if ((null != error) && error.isSupportedErrorCode()) {
                            Toast.makeText(VectorMediasViewerAdapter.this.mContext, error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onDownloadComplete(String aDownloadId) {
                        if (aDownloadId.equals(downloadId)) {
                            if (mMediasCache.isMediaCached(mediaInfo.mMediaUrl, mediaInfo.mMimeType)) {
                                mMediasCache.createTmpMediaFile(mediaInfo.mMediaUrl, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo, new SimpleApiCallback<File>() {
                                    @Override
                                    public void onSuccess(File file) {
                                        if (null != file) {
                                            CommonActivityUtils.saveMediaIntoDownloads(mContext, file, null, mediaInfo.mMimeType, new SimpleApiCallback<String>() {
                                                @Override
                                                public void onSuccess(String path) {
                                                    Toast.makeText(mContext, mContext.getText(R.string.media_slider_saved), Toast.LENGTH_LONG).show();
                                                }
                                            });
                                        }
                                    }
                                });
                            }
                        }
                    }
                });
            }
        }
    }

    /**
     * Long click management
     */
    private void onLongClick() {
        // The user is trying to leave with unsaved changes. Warn about that
        new AlertDialog.Builder(mContext)
                .setMessage(R.string.media_slider_saved_message)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        downloadMedia();
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create()
                .show();
    }

    /**
     * Load the video items
     *
     * @param view          the page view
     * @param thumbnailUrl  the thumbnail URL
     * @param videoUrl      the video Url
     * @param videoMimeType the video mime type
     */
    private void loadVideo(final int position, final View view, final String thumbnailUrl, final String videoUrl, final String videoMimeType, final EncryptedFileInfo encryptedFileInfo) {
        final VideoView videoView = view.findViewById(R.id.media_slider_videoview);
        final ImageView thumbView = view.findViewById(R.id.media_slider_video_thumbnail);
        final ImageView playView = view.findViewById(R.id.media_slider_video_playView);

        displayVideoThumbnail(view, !videoView.isPlaying());

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mPlayingVideoView = null;
                displayVideoThumbnail(view, true);
            }
        });

        // the video is renderer in DSA so trap the on click on the video view parent
        ((View) videoView.getParent()).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPlayingVideo();
                displayVideoThumbnail(view, true);
            }
        });

        // manage video error cases
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                mPlayingVideoView = null;
                displayVideoThumbnail(view, true);
                return false;
            }
        });

        // init the thumbnail views
        mMediasCache.loadBitmap(mSession.getHomeServerConfig(), thumbView, thumbnailUrl, 0, 0, null, null);

        playView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMediasCache.isMediaCached(videoUrl, videoMimeType)) {

                    mMediasCache.createTmpMediaFile(videoUrl, videoMimeType, encryptedFileInfo, new SimpleApiCallback<File>() {
                        @Override
                        public void onSuccess(File file) {
                            if (null != file) {
                                playVideo(view, videoView, file, videoMimeType);
                            }
                        }
                    });

                } else {
                    mAutoPlayItemAt = position;
                    downloadVideo(view, position);
                }
            }
        });
    }

    /**
     * Update the image page.
     *
     * @param webView         the image is rendered in a webview.
     * @param imageUri        the image Uri.
     * @param viewportContent the viewport.
     * @param css             the css.
     */
    private void loadImage(WebView webView, Uri imageUri, String viewportContent, String css) {
        String html =
                "<html><head><meta name='viewport' content='" +
                        viewportContent +
                        "'/>" +
                        "<style type='text/css'>" +
                        css +
                        "</style></head>" +
                        "<body> <div class='wrap'>" + "<img " +
                        ("src='" + imageUri.toString() + "'") +
                        " onerror='this.style.display=\"none\"' id='image' " + viewportContent + "/>" + "</div>" +
                        "</body>" + "</html>";

        String mime = "text/html";
        String encoding = "utf-8";

        webView.loadDataWithBaseURL(null, html, mime, encoding, null);
        webView.requestLayout();
    }

    /**
     * Image rendering subroutine
     */
    private String computeCss(String mediaUrl, int thumbnailWidth, int thumbnailHeight, int rotationAngle) {
        String css = "body { background-color: #000; height: 100%; width: 100%; margin: 0px; padding: 0px; }" +
                ".wrap { position: absolute; left: 0px; right: 0px; width: 100%; height: 100%; " +
                "display: -webkit-box; -webkit-box-pack: center; -webkit-box-align: center; " +
                "display: box; box-pack: center; box-align: center; } ";

        Uri mediaUri = null;

        try {
            mediaUri = Uri.parse(mediaUrl);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## computeCss() : Uri.parse failed " + e.getMessage());
        }

        if (null == mediaUri) {
            return css;
        }

        // the rotation angle must be retrieved from the exif metadata
        if (rotationAngle == Integer.MAX_VALUE) {
            if (null != mediaUrl) {
                rotationAngle = ImageUtils.getRotationAngleForBitmap(mContext, mediaUri);
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
                    Log.e(LOG_TAG, "## computeCss() : BitmapFactory.decodeStream failed " + e.getMessage());
                }

                imageWidth = options.outWidth;
                imageHeight = options.outHeight;

                imageStream.close();
                fullSizeBitmap.recycle();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## computeCss() : failed " + e.getMessage());
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
            double scale = Math.min((double) imageWidth / imageHeight, (double) displaySize.y / displaySize.x);

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
        WindowManager w = ((Activity) mContext).getWindowManager();
        w.getDefaultDisplay().getSize(size);
        return size;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
    }
}
