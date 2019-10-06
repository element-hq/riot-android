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

package im.vector.adapters;

import android.app.Activity;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.viewpager.widget.PagerAdapter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.gson.JsonElement;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.crypto.model.crypto.EncryptedFileInfo;
import org.matrix.androidsdk.db.MXMediaCache;
import org.matrix.androidsdk.listeners.MXMediaDownloadListener;
import org.matrix.androidsdk.rest.model.message.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import im.vector.R;
import im.vector.util.SlidableMediaInfo;
import im.vector.view.PieFractionView;

/**
 * An images slider
 */
public class VectorMediaViewerAdapter extends PagerAdapter {
    private static final String LOG_TAG = VectorMediaViewerAdapter.class.getSimpleName();

    private final Context mContext;

    private final LayoutInflater mLayoutInflater;

    private final MXSession mSession;

    private final MXMediaCache mMediasCache;

    // medias
    private List<SlidableMediaInfo> mMediasMessagesList;

    private final int mMaxImageWidth;
    private final int mMaxImageHeight;

    private int mLatestPrimaryItemPosition = -1;
    private View mLatestPrimaryView = null;

    private final List<Integer> mHighResMediaIndex = new ArrayList<>();

    // current playing video
    private VideoView mPlayingVideoView = null;

    private int mAutoPlayItemAt = -1;

    public VectorMediaViewerAdapter(Context context,
                                    MXSession session,
                                    MXMediaCache mediasCache,
                                    List<SlidableMediaInfo> mediaMessagesList,
                                    int maxImageWidth,
                                    int maxImageHeight) {
        mContext = context;
        mSession = session;
        mMediasCache = mediasCache;
        mMediasMessagesList = mediaMessagesList;
        mMaxImageWidth = maxImageWidth;
        mMaxImageHeight = maxImageHeight;

        mLayoutInflater = LayoutInflater.from(context);
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
                            final VideoView videoView = view.findViewById(R.id.media_slider_video_view);

                            if (mMediasCache.isMediaCached(mediaInfo.mMediaUrl, mediaInfo.mMimeType)) {
                                mMediasCache.createTmpDecryptedMediaFile(mediaInfo.mMediaUrl, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo,
                                        new SimpleApiCallback<File>() {
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

        if (imageInfo.mMessageType.equals(Message.MSGTYPE_IMAGE)) {
            // image
            if (TextUtils.isEmpty(imageInfo.mMimeType)) {
                imageInfo.mMimeType = "image/jpeg";
            }
            downloadHighResImage(view, position);
        } else {
            // video
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
        final VideoView videoView = view.findViewById(R.id.media_slider_video_view);
        final ImageView thumbView = view.findViewById(R.id.media_slider_video_thumbnail);
        final PieFractionView pieFractionView = view.findViewById(R.id.media_slider_pie_view);
        final ImageView playCircleView = view.findViewById(R.id.media_slider_video_play);
        final View downloadFailedView = view.findViewById(R.id.media_download_failed);

        final SlidableMediaInfo mediaInfo = mMediasMessagesList.get(position);
        final String loadingUri = mediaInfo.mMediaUrl;
        final String thumbnailUrl = mediaInfo.mThumbnailUrl;

        // check if the media has been downloaded
        if (mMediasCache.isMediaCached(loadingUri, mediaInfo.mMimeType)) {
            mMediasCache.createTmpDecryptedMediaFile(loadingUri, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo, new SimpleApiCallback<File>() {
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
            playCircleView.setVisibility(View.GONE);
            pieFractionView.setFraction(mMediasCache.getProgressValueForDownloadId(downloadId));
            pieFractionView.setTag(downloadId);

            mMediasCache.addDownloadListener(downloadId, new MXMediaDownloadListener() {

                @Override
                public void onDownloadError(String downloadId, JsonElement jsonElement) {
                    pieFractionView.setVisibility(View.GONE);
                    downloadFailedView.setVisibility(View.VISIBLE);

                    MatrixError error = JsonUtils.toMatrixError(jsonElement);
                    if ((null != error) && error.isSupportedErrorCode()) {
                        Toast.makeText(mContext, error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
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
                        // check if the media has been downloaded
                        if (mMediasCache.isMediaCached(loadingUri, mediaInfo.mMimeType)) {
                            playCircleView.setVisibility(View.VISIBLE);
                            mMediasCache.createTmpDecryptedMediaFile(loadingUri, mediaInfo.mMimeType, mediaInfo.mEncryptedFileInfo,
                                    new SimpleApiCallback<File>() {
                                        @Override
                                        public void onSuccess(final File mediaFile) {
                                            if (null != mediaFile) {
                                                mHighResMediaIndex.add(position);

                                                Uri uri = Uri.fromFile(mediaFile);
                                                final String newHighResUri = uri.toString();

                                                thumbView.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        loadVideo(position, view, thumbnailUrl, newHighResUri, mediaInfo.mMimeType,
                                                                mediaInfo.mEncryptedFileInfo);

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
    private void downloadHighResImage(final View view, final int position) {
        final PhotoView imageView = view.findViewById(R.id.media_slider_image_view);
        final PieFractionView pieFractionView = view.findViewById(R.id.media_slider_pie_view);
        final View downloadFailedView = view.findViewById(R.id.media_download_failed);

        final SlidableMediaInfo imageInfo = mMediasMessagesList.get(position);
        final String loadingUri = imageInfo.mMediaUrl;
        final String downloadId = mMediasCache.loadBitmap(mContext,
                mSession.getHomeServerConfig(),
                loadingUri,
                imageInfo.mRotationAngle,
                imageInfo.mOrientation,
                imageInfo.mMimeType,
                imageInfo.mEncryptedFileInfo);

        if (null != downloadId) {
            pieFractionView.setVisibility(View.VISIBLE);
            pieFractionView.setFraction(mMediasCache.getProgressValueForDownloadId(downloadId));
            mMediasCache.addDownloadListener(downloadId, new MXMediaDownloadListener() {
                @Override
                public void onDownloadError(String aDownloadId, JsonElement jsonElement) {
                    if (aDownloadId.equals(downloadId)) {
                        pieFractionView.setVisibility(View.GONE);
                        downloadFailedView.setVisibility(View.VISIBLE);

                        MatrixError error = JsonUtils.toMatrixError(jsonElement);
                        if (null != error) {
                            Toast.makeText(mContext, error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        }
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
                            mMediasCache.createTmpDecryptedMediaFile(loadingUri, imageInfo.mMimeType, imageInfo.mEncryptedFileInfo,
                                    new SimpleApiCallback<File>() {
                                        @Override
                                        public void onSuccess(File mediaFile) {
                                            if (null != mediaFile) {
                                                mHighResMediaIndex.add(position);

                                                Uri uri = Uri.fromFile(mediaFile);
                                                final String newHighResUri = uri.toString();

                                                imageView.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                                                                && ((Activity) mContext).isDestroyed()) {
                                                            return;
                                                        }

                                                        Uri mediaUri = Uri.parse(newHighResUri);
                                                        Glide.with(imageView)
                                                                .load(mediaUri)
                                                                .into(imageView);
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
        final View view = mLayoutInflater.inflate(R.layout.adapter_vector_media_viewer, null, false);

        // hide the pie chart
        final PieFractionView pieFractionView = view.findViewById(R.id.media_slider_pie_view);
        pieFractionView.setVisibility(View.GONE);

        view.findViewById(R.id.media_download_failed).setVisibility(View.GONE);

        final PhotoView imageView = view.findViewById(R.id.media_slider_image_view);
        final View videoLayout = view.findViewById(R.id.media_slider_video_layout);
        final ImageView thumbView = view.findViewById(R.id.media_slider_video_thumbnail);

        final SlidableMediaInfo mediaInfo = mMediasMessagesList.get(position);
        String mediaUrl = mediaInfo.mMediaUrl;

        if (mediaInfo.mMessageType.equals(Message.MSGTYPE_IMAGE)) {
            imageView.setVisibility(View.VISIBLE);
            videoLayout.setVisibility(View.GONE);

            if (TextUtils.isEmpty(mediaInfo.mMimeType)) {
                mediaInfo.mMimeType = "image/jpeg";
            }

            final String mimeType = mediaInfo.mMimeType;
            int width = -1;
            int height = -1;

            // check if the high resolution picture is already downloaded
            if (mMediasCache.isMediaCached(mediaUrl, mimeType)) {
                if (mHighResMediaIndex.indexOf(position) < 0) {
                    mHighResMediaIndex.add(position);
                }
            } else {
                width = mMaxImageWidth;
                height = mMaxImageHeight;
            }

            if (mMediasCache.isMediaCached(mediaUrl, width, height, mimeType)) {
                mMediasCache.createTmpDecryptedMediaFile(mediaUrl, width, height, mimeType, mediaInfo.mEncryptedFileInfo, new SimpleApiCallback<File>() {
                    @Override
                    public void onSuccess(File mediaFile) {
                        if (null != mediaFile) {
                            // Max zoom is PhotoViewAttacher.DEFAULT_MAX_SCALE (= 3)
                            // I set the max zoom to 1 because it leads to too many crashed due to high memory usage.
                            float maxZoom = 1; // imageView.getMaximumScale();

                            Glide.with(container)
                                    .load(mediaFile)
                                    .apply(new RequestOptions()
                                            // Override image wanted size, to keep good quality when image is zoomed in
                                            .override((int) (imageView.getWidth() * maxZoom), (int) (imageView.getHeight() * maxZoom)))
                                    .into(imageView);
                        }
                    }
                });
            }

            container.addView(view, 0);
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
                        Toast.makeText(mContext, error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
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

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
    }

    /**
     * Switch from the video view to the video thumbnail
     *
     * @param view    the page view
     * @param display true to display the video thumbnail, false to display the video player
     */
    private void displayVideoThumbnail(final View view, boolean display) {
        final VideoView videoView = view.findViewById(R.id.media_slider_video_view);
        final ImageView thumbView = view.findViewById(R.id.media_slider_video_thumbnail);
        final ImageView playView = view.findViewById(R.id.media_slider_video_play);

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
                    Log.e(LOG_TAG, "## playVideo() : failed " + e.getMessage(), e);
                    dstFile = null;
                } finally {
                    // Close resources
                    try {
                        if (inputStream != null) inputStream.close();
                        if (outputStream != null) outputStream.close();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## playVideo() : failed " + e.getMessage(), e);
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
                Log.e(LOG_TAG, "## playVideo() : videoView.start(); failed " + e.getMessage(), e);
            }
        }
    }

    /**
     * Load the video items
     *
     * @param view          the page view
     * @param thumbnailUrl  the thumbnail URL
     * @param videoUrl      the video Url
     * @param videoMimeType the video mime type
     */
    private void loadVideo(final int position,
                           final View view,
                           final String thumbnailUrl,
                           final String videoUrl,
                           final String videoMimeType,
                           final EncryptedFileInfo encryptedFileInfo) {
        final VideoView videoView = view.findViewById(R.id.media_slider_video_view);
        final ImageView thumbView = view.findViewById(R.id.media_slider_video_thumbnail);
        final ImageView playView = view.findViewById(R.id.media_slider_video_play);

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
                    mMediasCache.createTmpDecryptedMediaFile(videoUrl, videoMimeType, encryptedFileInfo, new SimpleApiCallback<File>() {
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
}
