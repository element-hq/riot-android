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

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.JsonNull;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.JsonUtils;
import im.vector.VectorApp;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.ImageSliderActivity;
import im.vector.activity.MemberDetailsActivity;
import im.vector.util.SlidableImageInfo;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * An adapter which can display room information.
 */
public class ConsoleMessagesAdapter extends MessagesAdapter {

    private Date mReferenceDate = new Date();
    private ArrayList<Date> mMessagesDateList = new ArrayList<Date>();
    private Handler mUiHandler;

    private final long MS_IN_DAY = 1000 * 60 * 60 * 24;

    public static interface MessageLongClickListener {
        public void onMessageLongClick(int position, Message message);
    }

    public static interface AvatarClickListener {
        public Boolean onAvatarClick(String roomId, String userId);
        public Boolean onAvatarLongClick(String roomId, String userId);
        public Boolean onDisplayNameClick(String userId, String displayName);
    }

    private MessageLongClickListener mLongClickListener = null;
    private AvatarClickListener mAvatarClickListener = null;

    public ConsoleMessagesAdapter(MXSession session, Context context, MXMediasCache mediasCache) {
        super(session, context,
                R.layout.adapter_item_vector_message_text,
                R.layout.adapter_item_vector_message_image,
                R.layout.adapter_item_vector_message_notice,
                R.layout.adapter_item_vector_message_emote,
                R.layout.adapter_item_vector_message_file,
                R.layout.adapter_item_vector_message_video,
                mediasCache);

        // for dispatching data to add to the adapter we need to be on the main thread
        mUiHandler = new Handler(Looper.getMainLooper());
    }

    public void setMessageLongClickListener(MessageLongClickListener listener) {
        mLongClickListener = listener;
    }

    public void setAvatarClickListener(AvatarClickListener listener) {
        mAvatarClickListener = listener;
    }

    @Override
    public void onAvatarClick(String roomId, String userId){
        if (null != mAvatarClickListener) {
            if (mAvatarClickListener.onAvatarClick(roomId, userId)) {
                return;
            }
        }

        Intent startRoomInfoIntent = new Intent(mContext, MemberDetailsActivity.class);
        startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_ROOM_ID, roomId);
        startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_MEMBER_ID, userId);
        startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
        mContext.startActivity(startRoomInfoIntent);
    }

    @Override
    public Boolean onAvatarLongClick(String roomId, String userId) {
        if (null != mAvatarClickListener) {
            if (mAvatarClickListener.onAvatarLongClick(roomId, userId)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void onSenderNameClick(String userId, String displayName) {
        if (null != mAvatarClickListener) {
            if (mAvatarClickListener.onDisplayNameClick(userId, displayName)) {
                // do something here ?
            }
        }

        // default behaviour
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);

        if (null != view) {
            view.setBackgroundColor(Color.TRANSPARENT);
        }

        return view;
    }

    /**
     * @return the imageMessages list
     */
    private ArrayList<SlidableImageInfo> listImageMessages() {
        ArrayList<SlidableImageInfo> res = new ArrayList<SlidableImageInfo>();

        for(int position = 0; position < getCount(); position++) {
            MessageRow row = this.getItem(position);
            Message message = JsonUtils.toMessage(row.getEvent().content);

            if (Message.MSGTYPE_IMAGE.equals(message.msgtype)) {
                ImageMessage imageMessage = (ImageMessage)message;

                SlidableImageInfo info = new SlidableImageInfo();

                info.mImageUrl = imageMessage.url;
                info.mRotationAngle = imageMessage.getRotation();
                info.mOrientation = imageMessage.getOrientation();
                info.mMimeType = imageMessage.getMimeType();
                info.midentifier = row.getEvent().eventId;
                res.add(info);
            }
        }

        return res;
    }

    /**
     * Returns the imageMessages position in listImageMessages.
     * @param listImageMessages the messages list.
     * @param imageMessage the imageMessage
     * @return the imageMessage position. -1 if not found.
     */
    private int getImageMessagePosition(ArrayList<SlidableImageInfo> listImageMessages, ImageMessage imageMessage) {

        for(int index = 0; index < listImageMessages.size(); index++) {
            if (listImageMessages.get(index).mImageUrl.equals(imageMessage.url)) {
                return index;
            }
        }

        return -1;
    }

    @Override
    public void onImageClick(int position, ImageMessage imageMessage, int maxImageWidth, int maxImageHeight, int rotationAngle){
        if (null != imageMessage.url) {

            ArrayList<SlidableImageInfo> listImageMessages = listImageMessages();
            int listPosition = getImageMessagePosition(listImageMessages, imageMessage);

            if (listPosition >= 0) {
                Intent viewImageIntent = new Intent(mContext, ImageSliderActivity.class);

                viewImageIntent.putExtra(ImageSliderActivity.KEY_THUMBNAIL_WIDTH, maxImageWidth);
                viewImageIntent.putExtra(ImageSliderActivity.KEY_THUMBNAIL_HEIGHT, maxImageHeight);
                viewImageIntent.putExtra(ImageSliderActivity.KEY_INFO_LIST, listImageMessages);
                viewImageIntent.putExtra(ImageSliderActivity.KEY_INFO_LIST_INDEX, listPosition);

                mContext.startActivity(viewImageIntent);
            }
        }
    }

    @Override
    public boolean onImageLongClick(int position, ImageMessage imageMessage, int maxImageWidth, int maxImageHeight, int rotationAngle){
        if (null != mLongClickListener) {
            mLongClickListener.onMessageLongClick(position, imageMessage);
            return true;
        }

        return false;
    }

    @Override
    public void onFileDownloaded(int position, FileMessage fileMessage) {
        // save into the downloads
        File mediaFile = mMediasCache.mediaCacheFile(fileMessage.url, fileMessage.getMimeType());

        if (null != mediaFile) {
            CommonActivityUtils.saveMediaIntoDownloads(mContext, mediaFile, fileMessage.body, fileMessage.getMimeType());
        }
    }

    @Override
         public void onFileClick(int position, FileMessage fileMessage) {
        if (null != fileMessage.url) {
            File mediaFile =  mMediasCache.mediaCacheFile(fileMessage.url, fileMessage.getMimeType());

            // is the file already saved
            if (null != mediaFile) {
                String savedMediaPath = CommonActivityUtils.saveMediaIntoDownloads(mContext, mediaFile, fileMessage.body, fileMessage.getMimeType());
                CommonActivityUtils.openMedia(VectorApp.getCurrentActivity(), savedMediaPath, fileMessage.getMimeType());
            }
        }
    }

    @Override
    public boolean onFileLongClick(int position, FileMessage fileMessage) {
        if (null != mLongClickListener) {
            mLongClickListener.onMessageLongClick(position, fileMessage);
            return true;
        }

        return false;
    }

    @Override
    public void onVideoClick(int position, VideoMessage videoMessage) {
        if (null != videoMessage.url) {
            File mediaFile =  mMediasCache.mediaCacheFile(videoMessage.url, videoMessage.getVideoMimeType());

            // is the file already saved
            if (null != mediaFile) {
                String savedMediaPath = CommonActivityUtils.saveMediaIntoDownloads(mContext, mediaFile, videoMessage.body, videoMessage.getVideoMimeType());
                CommonActivityUtils.openMedia(VectorApp.getCurrentActivity(), savedMediaPath, videoMessage.getVideoMimeType());
            } else {
                final String downloadId = mMediasCache.downloadMedia(mContext, videoMessage.url, videoMessage.getVideoMimeType());
                final VideoMessage fVideoMessage = videoMessage;
                final int fPosition = position;

                mMediasCache.addDownloadListener(downloadId, new MXMediasCache.DownloadCallback() {
                    @Override
                    public void onDownloadStart(String aDownloadId) {
                        if (downloadId.equals(aDownloadId)) {
                            mUiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    ConsoleMessagesAdapter.this.notifyDataSetChanged();
                                }
                            });
                        }
                    }

                    @Override
                    public void onDownloadProgress(String aDownloadId, int percentageProgress) {
                    }

                    @Override
                    public void onDownloadComplete(String aDownloadId) {
                        if (aDownloadId.equals(downloadId)) {
                            onVideoClick(fPosition, fVideoMessage);
                        }
                    }
                });
            }
        }
    }

    public boolean onVideoLongClick(int position, VideoMessage videoMessage) {
        if (null != mLongClickListener) {
            mLongClickListener.onMessageLongClick(position, videoMessage);
            return true;
        }

        return false;
    }

    @Override
    public void notifyDataSetChanged() {
        //  do not refresh the room when the application is in background
        // on large rooms, it drains a lot of battery
        if (!VectorApp.isAppInBackground()) {
            super.notifyDataSetChanged();
        }

        // build messages timestamps
        ArrayList<Date> dates = new ArrayList<Date>();

        for(int index = 0; index < this.getCount(); index++) {
            MessageRow row = getItem(index);
            Event msg = row.getEvent();
            dates.add(zeroTimeDate(new Date(msg.getOriginServerTs())));
        }

        synchronized (this) {
            mMessagesDateList = dates;
            mReferenceDate = new Date();
        }
    }

    /**
     * Reset the time of a date
     * @param date the date with time to reset
     * @return the 0 time date.
     */
    private Date zeroTimeDate(Date date) {
        final GregorianCalendar gregorianCalendar = new GregorianCalendar();
        gregorianCalendar.setTime(date);
        gregorianCalendar.set(Calendar.HOUR_OF_DAY, 0);
        gregorianCalendar.set(Calendar.MINUTE, 0);
        gregorianCalendar.set(Calendar.SECOND, 0);
        gregorianCalendar.set(Calendar.MILLISECOND, 0);
        return gregorianCalendar.getTime();
    }

    /**
     * Converts a difference of days to a string.
     * @param date the date to dislay
     * @param nbrDays the number of days between the reference days
     * @return the date text
     */
    private String dateDiff(Date date, long nbrDays) {
        if (nbrDays == 0) {
            return mContext.getResources().getString(R.string.today);
        } else if (nbrDays == 1) {
            return mContext.getResources().getString(R.string.yesterday);
        } else if (nbrDays < 7) {
            return (new SimpleDateFormat("EEEE")).format(date);
        } else  {
            int flags = DateUtils.FORMAT_SHOW_DATE |
                    DateUtils.FORMAT_NO_YEAR |
                    DateUtils.FORMAT_ABBREV_ALL |
                    DateUtils.FORMAT_SHOW_WEEKDAY;

            return DateUtils.formatDateTime(mContext, date.getTime(), flags);
        }
    }

    private String headerMessage(int position) {
        Date prevMessageDate = null;
        Date messageDate = null;

        synchronized (this) {
            if ((position > 0) && (position < mMessagesDateList.size())) {
                prevMessageDate = mMessagesDateList.get(position -1);
            }
            if (position < mMessagesDateList.size()) {
                messageDate = mMessagesDateList.get(position);
            }
        }

        // sanity check
        if (null == messageDate) {
            return null;
        }

        // same day or get the oldest message
        if ((null != prevMessageDate) && 0 == (prevMessageDate.getTime() - messageDate.getTime())) {
            return null;
        }

        return dateDiff(messageDate, (mReferenceDate.getTime() - messageDate.getTime()) / MS_IN_DAY);
    }

    @Override
    protected boolean manageSubView(int position, View convertView, View subView, int msgType) {
        Boolean isMergedView = super.manageSubView(position, convertView, subView, msgType);

        View view = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_separator);
        if (null != view) {
            View line = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_separator_line);

            if (null != line) {
                line.setBackgroundColor(Color.TRANSPARENT);
            }

            MessageRow row = getItem(position);
            Event msg = row.getEvent();
            String nextUserId = null;

            if ((position + 1) < this.getCount()) {
                MessageRow nextRow = getItem(position + 1);

                if (null != nextRow)  {
                    nextUserId = nextRow.getEvent().userId;
                }
            }

            view.setVisibility(((null != nextUserId) && (nextUserId.equals(msg.userId)) || ((position + 1) == this.getCount())) ? View.GONE : View.VISIBLE);
        }

        View headerLayout = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_header);

        if (null != headerLayout) {
            String header = headerMessage(position);

            if (null != header) {
                View headerLine = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_header_separator);
                headerLine.setVisibility(View.GONE);
                TextView headerText = (TextView) convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_header_text);
                headerText.setTextColor(mContext.getResources().getColor(R.color.vector_title_color));
                headerText.setText(header);
                headerText.setGravity(Gravity.CENTER);
                headerLayout.setVisibility(View.VISIBLE);
            } else {
                headerLayout.setVisibility(View.GONE);
            }
        }

        return isMergedView;
    }


    public int presenceOnlineColor() {
        return mContext.getResources().getColor(R.color.presence_online);
    }

    public int presenceOfflineColor() {
        return mContext.getResources().getColor(R.color.presence_offline);
    }

    public int presenceUnavailableColor() {
        return mContext.getResources().getColor(R.color.presence_unavailable);
    }
}
