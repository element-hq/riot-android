/*
 * Copyright 2017 Vector Creations Ltd
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

import android.content.Context;
import android.graphics.Color;
import android.media.ExifInterface;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonElement;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.IMXMediaDownloadListener;
import org.matrix.androidsdk.listeners.IMXMediaUploadListener;
import org.matrix.androidsdk.listeners.MXMediaDownloadListener;
import org.matrix.androidsdk.listeners.MXMediaUploadListener;
import org.matrix.androidsdk.rest.model.crypto.EncryptedFileInfo;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.message.FileMessage;
import org.matrix.androidsdk.rest.model.message.ImageInfo;
import org.matrix.androidsdk.rest.model.message.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.message.VideoInfo;
import org.matrix.androidsdk.rest.model.message.VideoMessage;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;

import java.util.HashMap;
import java.util.Map;

import im.vector.R;
import im.vector.listeners.IMessagesAdapterActionsListener;

/**
 * An helper to display medias information
 */
class VectorMessagesAdapterMediasHelper {
    private static final String LOG_TAG = VectorMessagesAdapterMediasHelper.class.getSimpleName();

    private final MXSession mSession;
    private final Context mContext;
    private final int mMaxImageWidth;
    private final int mMaxImageHeight;
    private final MXMediasCache mMediasCache;
    private IMessagesAdapterActionsListener mVectorMessagesAdapterEventsListener;

    private final int mNotSentMessageTextColor;
    private final int mDefaultMessageTextColor;

    VectorMessagesAdapterMediasHelper(Context context, MXSession session, int maxImageWidth, int maxImageHeight, int notSentMessageTextColor, int defaultMessageTextColor) {
        mContext = context;
        mSession = session;
        mMaxImageWidth = maxImageWidth;
        mMaxImageHeight = maxImageHeight;
        mMediasCache = mSession.getMediasCache();

        mNotSentMessageTextColor = notSentMessageTextColor;
        mDefaultMessageTextColor = defaultMessageTextColor;
    }

    /**
     * Define the events listener
     *
     * @param listener teh events listener
     */
    void setVectorMessagesAdapterActionsListener(IMessagesAdapterActionsListener listener) {
        mVectorMessagesAdapterEventsListener = listener;
    }

    /**
     * Check if there is a linked upload.
     *
     * @param convertView the media view
     * @param event       teh related event
     * @param type        the media type
     * @param mediaUrl    the media url
     */
    void managePendingUpload(final View convertView, final Event event, final int type, final String mediaUrl) {
        final View uploadProgressLayout = convertView.findViewById(R.id.content_upload_progress_layout);
        final ProgressBar uploadSpinner = convertView.findViewById(R.id.upload_event_spinner);

        // the dedicated UI items are not found
        if ((null == uploadProgressLayout) || (null == uploadSpinner)) {
            return;
        }

        // Mark the upload layout as
        uploadProgressLayout.setTag(mediaUrl);

        // no upload in progress
        if (!mSession.getMyUserId().equals(event.getSender()) || !event.isSending()) {
            uploadProgressLayout.setVisibility(View.GONE);
            uploadSpinner.setVisibility(View.GONE);
            showUploadFailure(convertView, type, event.isUndeliverable());
            return;
        }

        IMXMediaUploadListener.UploadStats uploadStats = mSession.getMediasCache().getStatsForUploadId(mediaUrl);

        if (null != uploadStats) {
            mSession.getMediasCache().addUploadListener(mediaUrl, new MXMediaUploadListener() {
                @Override
                public void onUploadProgress(String uploadId, UploadStats uploadStats) {
                    if (TextUtils.equals((String) uploadProgressLayout.getTag(), uploadId)) {
                        refreshUploadViews(event, uploadStats, uploadProgressLayout);
                    }
                }

                private void onUploadStop(String message) {
                    if (!TextUtils.isEmpty(message)) {
                        Toast.makeText(mContext,
                                message,
                                Toast.LENGTH_LONG).show();
                    }

                    showUploadFailure(convertView, type, true);
                    uploadProgressLayout.setVisibility(View.GONE);
                    uploadSpinner.setVisibility(View.GONE);
                }

                @Override
                public void onUploadCancel(String uploadId) {
                    if (TextUtils.equals((String) uploadProgressLayout.getTag(), uploadId)) {
                        onUploadStop(null);
                    }
                }

                @Override
                public void onUploadError(String uploadId, int serverResponseCode, String serverErrorMessage) {
                    if (TextUtils.equals((String) uploadProgressLayout.getTag(), uploadId)) {
                        onUploadStop(serverErrorMessage);
                    }
                }

                @Override
                public void onUploadComplete(final String uploadId, final String contentUri) {
                    if (TextUtils.equals((String) uploadProgressLayout.getTag(), uploadId)) {
                        uploadSpinner.setVisibility(View.GONE);
                    }
                }

            });
        }

        showUploadFailure(convertView, type, false);
        uploadSpinner.setVisibility((null == uploadStats) ? View.VISIBLE : View.GONE);
        refreshUploadViews(event, uploadStats, uploadProgressLayout);
    }

    // the image / video bitmaps are set to null if the matching URL is not the same
    // to avoid flickering
    private Map<String, String> mUrlByBitmapIndex = new HashMap<>();

    /**
     * Manage the image/video download.
     *
     * @param convertView the parent view.
     * @param event       the event
     * @param message     the image / video message
     * @param position    the message position
     */
    void managePendingImageVideoDownload(final View convertView, final Event event, final Message message, final int position) {
        int maxImageWidth = mMaxImageWidth;
        int maxImageHeight = mMaxImageHeight;
        int rotationAngle = 0;
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        String thumbUrl = null;
        int thumbWidth = -1;
        int thumbHeight = -1;

        EncryptedFileInfo encryptedFileInfo = null;

        // retrieve the common items
        if (message instanceof ImageMessage) {
            ImageMessage imageMessage = (ImageMessage) message;
            imageMessage.checkMediaUrls();

            // Backwards compatibility with events from before Synapse 0.6.0
            if (imageMessage.getThumbnailUrl() != null) {
                thumbUrl = imageMessage.getThumbnailUrl();

                if (null != imageMessage.info) {
                    encryptedFileInfo = imageMessage.info.thumbnail_file;
                }

            } else if (imageMessage.getUrl() != null) {
                thumbUrl = imageMessage.getUrl();
                encryptedFileInfo = imageMessage.file;
            }

            rotationAngle = imageMessage.getRotation();

            ImageInfo imageInfo = imageMessage.info;

            if (null != imageInfo) {
                if ((null != imageInfo.w) && (null != imageInfo.h)) {
                    thumbWidth = imageInfo.w;
                    thumbHeight = imageInfo.h;
                }

                if (null != imageInfo.orientation) {
                    orientation = imageInfo.orientation;
                }
            }
        } else { // video
            VideoMessage videoMessage = (VideoMessage) message;
            videoMessage.checkMediaUrls();

            thumbUrl = videoMessage.getThumbnailUrl();
            if (null != videoMessage.info) {
                encryptedFileInfo = videoMessage.info.thumbnail_file;
            }

            VideoInfo videoinfo = videoMessage.info;

            if (null != videoinfo) {
                if ((null != videoMessage.info.thumbnail_info) && (null != videoMessage.info.thumbnail_info.w) && (null != videoMessage.info.thumbnail_info.h)) {
                    thumbWidth = videoMessage.info.thumbnail_info.w;
                    thumbHeight = videoMessage.info.thumbnail_info.h;
                }
            }
        }

        ImageView imageView = convertView.findViewById(R.id.messagesAdapter_image);

        // reset the bitmap if the url is not the same than before
        if ((null == thumbUrl) || !TextUtils.equals(imageView.hashCode() + "", mUrlByBitmapIndex.get(thumbUrl))) {
            imageView.setImageBitmap(null);
            if (null != thumbUrl) {
                mUrlByBitmapIndex.put(thumbUrl, imageView.hashCode() + "");
            }
        }

        RelativeLayout informationLayout = convertView.findViewById(R.id.messagesAdapter_image_layout);
        final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) informationLayout.getLayoutParams();

        // the thumbnails are always pre - rotated
        String downloadId = mMediasCache.loadBitmap(mSession.getHomeServerConfig(), imageView, thumbUrl, maxImageWidth, maxImageHeight, rotationAngle, ExifInterface.ORIENTATION_UNDEFINED, "image/jpeg", encryptedFileInfo);

        // test if the media is downloading the thumbnail is not downloading
        if (null == downloadId) {
            if (message instanceof VideoMessage) {
                downloadId = mMediasCache.downloadIdFromUrl(((VideoMessage) message).getUrl());
            } else {
                downloadId = mMediasCache.downloadIdFromUrl(((ImageMessage) message).getUrl());
            }
        }

        final View downloadProgressLayout = convertView.findViewById(R.id.content_download_progress_layout);

        if (null == downloadProgressLayout) {
            return;
        }

        // the tag is used to detect if the progress value is linked to this layout
        downloadProgressLayout.setTag(downloadId);

        int frameHeight = -1;
        int frameWidth = -1;

        // if the image size is known
        // compute the expected thumbnail height
        if ((thumbWidth > 0) && (thumbHeight > 0)) {

            // swap width and height if the image is side oriented
            if ((rotationAngle == 90) || (rotationAngle == 270)) {
                int tmp = thumbWidth;
                thumbWidth = thumbHeight;
                thumbHeight = tmp;
            } else if ((orientation == ExifInterface.ORIENTATION_ROTATE_90) || (orientation == ExifInterface.ORIENTATION_ROTATE_270)) {
                int tmp = thumbWidth;
                thumbWidth = thumbHeight;
                thumbHeight = tmp;
            }

            frameHeight = Math.min(maxImageWidth * thumbHeight / thumbWidth, maxImageHeight);
            frameWidth = frameHeight * thumbWidth / thumbHeight;
        }

        // ensure that some values are properly initialized
        if (frameHeight < 0) {
            frameHeight = mMaxImageHeight;
        }

        if (frameWidth < 0) {
            frameWidth = mMaxImageWidth;
        }

        // apply it the layout
        // it avoid row jumping when the image is downloaded
        layoutParams.height = frameHeight;
        layoutParams.width = frameWidth;

        // no download in progress
        if (null != downloadId) {
            downloadProgressLayout.setVisibility(View.VISIBLE);

            mMediasCache.addDownloadListener(downloadId, new MXMediaDownloadListener() {
                @Override
                public void onDownloadCancel(String downloadId) {
                    if (TextUtils.equals(downloadId, (String) downloadProgressLayout.getTag())) {
                        downloadProgressLayout.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onDownloadError(String downloadId, JsonElement jsonElement) {
                    if (TextUtils.equals(downloadId, (String) downloadProgressLayout.getTag())) {
                        MatrixError error = null;

                        try {
                            error = JsonUtils.toMatrixError(jsonElement);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Cannot cast to Matrix error " + e.getLocalizedMessage());
                        }

                        downloadProgressLayout.setVisibility(View.GONE);

                        if ((null != error) && error.isSupportedErrorCode()) {
                            Toast.makeText(mContext, error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        } else if (null != jsonElement) {
                            Toast.makeText(mContext, jsonElement.toString(), Toast.LENGTH_LONG).show();
                        }
                    }
                }

                @Override
                public void onDownloadProgress(String aDownloadId, DownloadStats stats) {
                    if (TextUtils.equals(aDownloadId, (String) downloadProgressLayout.getTag())) {
                        refreshDownloadViews(event, stats, downloadProgressLayout);
                    }
                }

                @Override
                public void onDownloadComplete(String aDownloadId) {
                    if (TextUtils.equals(aDownloadId, (String) downloadProgressLayout.getTag())) {
                        downloadProgressLayout.setVisibility(View.GONE);

                        if (null != mVectorMessagesAdapterEventsListener) {
                            mVectorMessagesAdapterEventsListener.onMediaDownloaded(position);
                        }
                    }
                }
            });

            refreshDownloadViews(event, mMediasCache.getStatsForDownloadId(downloadId), downloadProgressLayout);
        } else {
            downloadProgressLayout.setVisibility(View.GONE);
        }

        imageView.setBackgroundColor(Color.TRANSPARENT);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }

    /**
     * Manage the video upload
     *
     * @param convertView the base view
     * @param event       the image or video event
     * @param message     the image or video message
     */
    void managePendingImageVideoUpload(final View convertView, final Event event, Message message) {
        final View uploadProgressLayout = convertView.findViewById(R.id.content_upload_progress_layout);
        final ProgressBar uploadSpinner = convertView.findViewById(R.id.upload_event_spinner);

        final boolean isVideoMessage = message instanceof VideoMessage;

        // the dedicated UI items are not found
        if ((null == uploadProgressLayout) || (null == uploadSpinner)) {
            return;
        }

        // refresh the progress only if it is the expected URL
        uploadProgressLayout.setTag(null);

        boolean hasContentInfo = (null != (isVideoMessage ? ((VideoMessage) message).info : ((ImageMessage) message).info));

        // not the sender ?
        if (!mSession.getMyUserId().equals(event.getSender()) || event.isUndeliverable() || !hasContentInfo) {
            uploadProgressLayout.setVisibility(View.GONE);
            uploadSpinner.setVisibility(View.GONE);
            showUploadFailure(convertView, isVideoMessage ? VectorMessagesAdapter.ROW_TYPE_VIDEO : VectorMessagesAdapter.ROW_TYPE_IMAGE, event.isUndeliverable());
            return;
        }

        String uploadingUrl;
        final boolean isUploadingThumbnail;
        boolean isUploadingContent = false;

        if (isVideoMessage) {
            uploadingUrl = ((VideoMessage) message).getThumbnailUrl();
            isUploadingThumbnail = ((VideoMessage) message).isThumbnailLocalContent();
        } else {
            uploadingUrl = ((ImageMessage) message).getThumbnailUrl();
            isUploadingThumbnail = ((ImageMessage) message).isThumbnailLocalContent();
        }

        int progress;

        if (isUploadingThumbnail) {
            progress = mSession.getMediasCache().getProgressValueForUploadId(uploadingUrl);
        } else {
            if (isVideoMessage) {
                uploadingUrl = ((VideoMessage) message).getUrl();
                isUploadingContent = ((VideoMessage) message).isLocalContent();

            } else {
                uploadingUrl = ((ImageMessage) message).getUrl();
                isUploadingContent = ((ImageMessage) message).isLocalContent();
            }

            progress = mSession.getMediasCache().getProgressValueForUploadId(uploadingUrl);
        }

        if (progress >= 0) {
            uploadProgressLayout.setTag(uploadingUrl);
            mSession.getMediasCache().addUploadListener(uploadingUrl, new MXMediaUploadListener() {
                @Override
                public void onUploadProgress(String uploadId, UploadStats uploadStats) {
                    if (TextUtils.equals((String) uploadProgressLayout.getTag(), uploadId)) {
                        refreshUploadViews(event, uploadStats, uploadProgressLayout);

                        int progress;

                        if (!isUploadingThumbnail) {
                            progress = 10 + (uploadStats.mProgress * 90 / 100);
                        } else {
                            progress = (uploadStats.mProgress * 10 / 100);
                        }

                        updateUploadProgress(uploadProgressLayout, progress);
                    }
                }

                private void onUploadStop(String message) {
                    if (!TextUtils.isEmpty(message)) {
                        Toast.makeText(mContext,
                                message,
                                Toast.LENGTH_LONG).show();
                    }

                    showUploadFailure(convertView, isVideoMessage ? VectorMessagesAdapter.ROW_TYPE_VIDEO : VectorMessagesAdapter.ROW_TYPE_IMAGE, true);
                    uploadProgressLayout.setVisibility(View.GONE);
                    uploadSpinner.setVisibility(View.GONE);
                }

                @Override
                public void onUploadCancel(String uploadId) {
                    if (TextUtils.equals((String) uploadProgressLayout.getTag(), uploadId)) {
                        onUploadStop(null);
                    }
                }

                @Override
                public void onUploadError(String uploadId, int serverResponseCode, String serverErrorMessage) {
                    if (TextUtils.equals((String) uploadProgressLayout.getTag(), uploadId)) {
                        onUploadStop(serverErrorMessage);
                    }
                }

                @Override
                public void onUploadComplete(final String uploadId, final String contentUri) {
                    if (TextUtils.equals((String) uploadProgressLayout.getTag(), uploadId)) {
                        uploadSpinner.setVisibility(View.GONE);
                    }
                }
            });
        }

        showUploadFailure(convertView, isVideoMessage ? VectorMessagesAdapter.ROW_TYPE_VIDEO : VectorMessagesAdapter.ROW_TYPE_IMAGE, false);
        uploadSpinner.setVisibility(((progress < 0) && event.isSending()) ? View.VISIBLE : View.GONE);
        refreshUploadViews(event, mSession.getMediasCache().getStatsForUploadId(uploadingUrl), uploadProgressLayout);

        if (isUploadingContent) {
            progress = 10 + (progress * 90 / 100);
        } else if (isUploadingThumbnail) {
            progress = (progress * 10 / 100);
        }

        updateUploadProgress(uploadProgressLayout, progress);
        uploadProgressLayout.setVisibility(((progress >= 0) && event.isSending()) ? View.VISIBLE : View.GONE);
    }

    /**
     * Update the progress bar
     *
     * @param uploadProgressLayout the progress layout
     * @param progress             the progress value
     */
    private static void updateUploadProgress(View uploadProgressLayout, int progress) {
        ProgressBar progressBar = uploadProgressLayout.findViewById(R.id.media_progress_view);

        if (null != progressBar) {
            progressBar.setProgress(progress);
        }
    }

    /**
     * Refresh the upload views.
     *
     * @param event                the event
     * @param uploadStats          the upload stats
     * @param uploadProgressLayout the progress layout
     */
    private void refreshUploadViews(final Event event, final IMXMediaUploadListener.UploadStats uploadStats, final View uploadProgressLayout) {
        if (null != uploadStats) {
            uploadProgressLayout.setVisibility(View.VISIBLE);

            TextView uploadProgressStatsTextView = uploadProgressLayout.findViewById(R.id.media_progress_text_view);
            ProgressBar progressBar = uploadProgressLayout.findViewById(R.id.media_progress_view);

            if (null != uploadProgressStatsTextView) {
                uploadProgressStatsTextView.setText(formatUploadStats(mContext, uploadStats));
            }

            if (null != progressBar) {
                progressBar.setProgress(uploadStats.mProgress);
            }

            final View cancelLayout = uploadProgressLayout.findViewById(R.id.media_progress_cancel);

            if (null != cancelLayout) {
                cancelLayout.setTag(event);

                cancelLayout.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (event == cancelLayout.getTag()) {
                            if (null != mVectorMessagesAdapterEventsListener) {
                                mVectorMessagesAdapterEventsListener.onEventAction(event, "", R.id.ic_action_vector_cancel_upload);
                            }
                        }
                    }
                });
            }
        } else {
            uploadProgressLayout.setVisibility(View.GONE);
        }
    }

    /**
     * Manage the file download items.
     *
     * @param convertView the message cell view.
     * @param event       the event
     * @param fileMessage the file message.
     * @param position    the position in the listview.
     */
    void managePendingFileDownload(View convertView, final Event event, FileMessage fileMessage, final int position) {
        String downloadId = mMediasCache.downloadIdFromUrl(fileMessage.getUrl());
        final View downloadProgressLayout = convertView.findViewById(R.id.content_download_progress_layout);

        if (null == downloadProgressLayout) {
            return;
        }

        downloadProgressLayout.setTag(downloadId);

        // no download in progress
        if (null != downloadId) {
            downloadProgressLayout.setVisibility(View.VISIBLE);

            mMediasCache.addDownloadListener(downloadId, new MXMediaDownloadListener() {
                @Override
                public void onDownloadCancel(String downloadId) {
                    if (TextUtils.equals(downloadId, (String) downloadProgressLayout.getTag())) {
                        downloadProgressLayout.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onDownloadError(String downloadId, JsonElement jsonElement) {
                    if (TextUtils.equals(downloadId, (String) downloadProgressLayout.getTag())) {
                        MatrixError error = null;

                        try {
                            error = JsonUtils.toMatrixError(jsonElement);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Cannot cast to Matrix error " + e.getLocalizedMessage());
                        }

                        downloadProgressLayout.setVisibility(View.GONE);

                        if ((null != error) && error.isSupportedErrorCode()) {
                            Toast.makeText(mContext, error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        } else if (null != jsonElement) {
                            Toast.makeText(mContext, jsonElement.toString(), Toast.LENGTH_LONG).show();
                        }
                    }
                }

                @Override
                public void onDownloadProgress(String aDownloadId, DownloadStats stats) {
                    if (TextUtils.equals(aDownloadId, (String) downloadProgressLayout.getTag())) {
                        refreshDownloadViews(event, stats, downloadProgressLayout);
                    }
                }

                @Override
                public void onDownloadComplete(String aDownloadId) {
                    if (TextUtils.equals(aDownloadId, (String) downloadProgressLayout.getTag())) {
                        downloadProgressLayout.setVisibility(View.GONE);

                        if (null != mVectorMessagesAdapterEventsListener) {
                            mVectorMessagesAdapterEventsListener.onMediaDownloaded(position);
                        }
                    }
                }
            });
            refreshDownloadViews(event, mMediasCache.getStatsForDownloadId(downloadId), downloadProgressLayout);
        } else {
            downloadProgressLayout.setVisibility(View.GONE);
        }
    }

    /**
     * Show the upload failure items
     *
     * @param convertView the cell view
     * @param type        the media type
     * @param show        true to show the failure items
     */
    private void showUploadFailure(View convertView, int type, boolean show) {
        if (VectorMessagesAdapter.ROW_TYPE_FILE == type) {
            TextView fileTextView = convertView.findViewById(R.id.messagesAdapter_filename);

            if (null != fileTextView) {
                fileTextView.setTextColor(show ? mNotSentMessageTextColor : mDefaultMessageTextColor);
            }
        } else if ((VectorMessagesAdapter.ROW_TYPE_IMAGE == type) || (VectorMessagesAdapter.ROW_TYPE_VIDEO == type)) {
            View failedLayout = convertView.findViewById(R.id.media_upload_failed);

            if (null != failedLayout) {
                failedLayout.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        }
    }

    /**
     * Refresh the download views
     *
     * @param event                  the event
     * @param downloadStats          the download stats
     * @param downloadProgressLayout the download progress layout
     */
    private void refreshDownloadViews(final Event event, final IMXMediaDownloadListener.DownloadStats downloadStats, final View downloadProgressLayout) {
        if ((null != downloadStats) && isMediaDownloading(event)) {
            downloadProgressLayout.setVisibility(View.VISIBLE);

            TextView downloadProgressStatsTextView = downloadProgressLayout.findViewById(R.id.media_progress_text_view);
            ProgressBar progressBar = downloadProgressLayout.findViewById(R.id.media_progress_view);

            if (null != downloadProgressStatsTextView) {
                downloadProgressStatsTextView.setText(formatDownloadStats(mContext, downloadStats));
            }

            if (null != progressBar) {
                progressBar.setProgress(downloadStats.mProgress);
            }

            final View cancelLayout = downloadProgressLayout.findViewById(R.id.media_progress_cancel);

            if (null != cancelLayout) {
                cancelLayout.setTag(event);

                cancelLayout.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (event == cancelLayout.getTag()) {
                            if (null != mVectorMessagesAdapterEventsListener) {
                                mVectorMessagesAdapterEventsListener.onEventAction(event, "", R.id.ic_action_vector_cancel_download);
                            }
                        }
                    }
                });
            }
        } else {
            downloadProgressLayout.setVisibility(View.GONE);
        }
    }

    /**
     * Tells if the downloadId is the media download id.
     *
     * @param event the event
     * @return true if the media is downloading (not the thumbnail)
     */
    private boolean isMediaDownloading(Event event) {

        if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
            Message message = JsonUtils.toMessage(event.getContent());

            String url = null;

            if (TextUtils.equals(message.msgtype, Message.MSGTYPE_IMAGE)) {
                url = JsonUtils.toImageMessage(event.getContent()).getUrl();
            } else if (TextUtils.equals(message.msgtype, Message.MSGTYPE_VIDEO)) {
                url = JsonUtils.toVideoMessage(event.getContent()).getUrl();
            } else if (TextUtils.equals(message.msgtype, Message.MSGTYPE_FILE)) {
                url = JsonUtils.toFileMessage(event.getContent()).getUrl();
            }

            if (!TextUtils.isEmpty(url)) {
                return null != mSession.getMediasCache().downloadIdFromUrl(url);
            }
        }

        return false;
    }

    //==============================================================================================================
    // Download / upload progress management
    //==============================================================================================================

    /**
     * Format a second time range.
     *
     * @param seconds the seconds time
     * @return the formatted string
     */
    private static String vectorRemainingTimeToString(Context context, int seconds) {
        if (seconds < 0) {
            return "";
        } else if (seconds <= 1) {
            return "< 1s";
        } else if (seconds < 60) {
            return context.getString(R.string.attachment_remaining_time_seconds, seconds);
        } else if (seconds < 3600) {
            return context.getString(R.string.attachment_remaining_time_minutes, (seconds / 60), (seconds % 60));
        } else {
            return DateUtils.formatElapsedTime(seconds);
        }
    }

    /**
     * Format the download / upload stats.
     *
     * @param context          the context.
     * @param progressFileSize the upload / download media size.
     * @param fileSize         the expected media size.
     * @param remainingTime    the remaining time (seconds)
     * @return the formatted string.
     */
    private static String formatStats(Context context, int progressFileSize, int fileSize, int remainingTime) {
        String formattedString = "";

        if (fileSize > 0) {
            formattedString += android.text.format.Formatter.formatShortFileSize(context, progressFileSize);
            formattedString += " / " + android.text.format.Formatter.formatShortFileSize(context, fileSize);
        }

        if (remainingTime > 0) {
            if (!TextUtils.isEmpty(formattedString)) {
                formattedString += " (" + vectorRemainingTimeToString(context, remainingTime) + ")";
            } else {
                formattedString += vectorRemainingTimeToString(context, remainingTime);
            }
        }

        return formattedString;
    }

    /**
     * Format the download stats.
     *
     * @param context the context.
     * @param stats   the download stats
     * @return the formatted string
     */
    private static String formatDownloadStats(Context context, IMXMediaDownloadListener.DownloadStats stats) {
        return formatStats(context, stats.mDownloadedSize, stats.mFileSize, stats.mEstimatedRemainingTime);
    }

    /**
     * Format the upload stats.
     *
     * @param context the context.
     * @param stats   the upload stats
     * @return the formatted string
     */
    private static String formatUploadStats(Context context, IMXMediaUploadListener.UploadStats stats) {
        return formatStats(context, stats.mUploadedSize, stats.mFileSize, stats.mEstimatedRemainingTime);
    }
}
