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

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.ExifInterface;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.console.ConsoleApplication;
import org.matrix.console.R;
import org.matrix.console.activity.CommonActivityUtils;
import org.matrix.console.activity.ImageSliderActivity;
import org.matrix.console.activity.ImageWebViewActivity;
import org.matrix.console.activity.MemberDetailsActivity;
import org.matrix.console.util.SlidableImageInfo;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 * An adapter which can display room information.
 */
public class ConsoleMessagesAdapter extends MessagesAdapter {

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
        super(session, context, mediasCache);
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
        view.setBackgroundColor(Color.TRANSPARENT);
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
                CommonActivityUtils.openMedia(ConsoleApplication.getCurrentActivity(), savedMediaPath, fileMessage.getMimeType());
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
    public void notifyDataSetChanged() {
        //  do not refresh the room when the application is in background
        // on large rooms, it drains a lot of battery
        if (!ConsoleApplication.isAppInBackground()) {
            super.notifyDataSetChanged();
        }
    }

    @Override
    protected boolean manageSubView(int position, View convertView, View subView, int msgType) {
        MessageRow row = getItem(position);
        Event msg = row.getEvent();
        RoomState roomState = row.getRoomState();

        MyUser myUser = mSession.getMyUser();

        // isMergedView -> the message is going to be merged with the previous one
        // willBeMerged -> false if it is the last message of the user
        boolean isMergedView = false;
        boolean willBeMerged = false;

        convertView.setClickable(false);

        // the notice messages are never merged
        /*if (msgType != ROW_TYPE_NOTICE)*/ {
            //
            String prevUserId = null;
            if (position > 0) {
                MessageRow prevRow = getItem(position - 1);

                if ((null != prevRow) /*&& (getItemViewType(prevRow.getEvent()) != ROW_TYPE_NOTICE)*/) {
                    prevUserId = prevRow.getEvent().userId;
                }
            }

            String nextUserId = null;

            if ((position + 1) < this.getCount()) {
                MessageRow nextRow = getItem(position + 1);

                if ((null != nextRow) /*&& (getItemViewType(nextRow.getEvent()) != ROW_TYPE_NOTICE)*/) {
                    nextUserId = nextRow.getEvent().userId;
                }
            }

            isMergedView = (null != prevUserId) && (prevUserId.equals(msg.userId));
            willBeMerged = (null != nextUserId) && (nextUserId.equals(msg.userId));
        }

        View leftTsTextLayout = convertView.findViewById(org.matrix.androidsdk.R.id.message_timestamp_layout_left);
        View rightTsTextLayout = convertView.findViewById(org.matrix.androidsdk.R.id.message_timestamp_layout_right);

        // manage sender text
        TextView textView = (TextView) convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_sender);
        if (null != textView) {
            if (null == rightTsTextLayout) {
                textView.setVisibility(View.VISIBLE);

                if (isMergedView) {
                    textView.setText("");
                } else {
                    textView.setText(getUserDisplayName(msg.userId, row.getRoomState()));
                }
            }
            else if (isMergedView || (msgType == ROW_TYPE_NOTICE)) {
                textView.setVisibility(View.GONE);
            } else {
                textView.setVisibility(View.VISIBLE);
                textView.setText(getUserDisplayName(msg.userId, row.getRoomState()));
            }

            final String fSenderId = msg.userId;
            final String fDisplayName = textView.getText().toString();

            textView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onSenderNameClick(fSenderId, fDisplayName);
                }
            });
        }

        TextView tsTextView;

        if (null == rightTsTextLayout) {
            tsTextView = (TextView)leftTsTextLayout.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_timestamp);
        } else {
            TextView leftTsTextView = (TextView)leftTsTextLayout.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_timestamp);
            TextView rightTsTextView = (TextView)rightTsTextLayout.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_timestamp);

            leftTsTextView.setVisibility(View.GONE);
            tsTextView = rightTsTextView;
        }

        tsTextView.setVisibility(View.VISIBLE);
        tsTextView.setText(msg.formattedOriginServerTs());

        if (row.getEvent().isUndeliverable()) {
            tsTextView.setTextColor(notSentColor);
        } else {
            tsTextView.setTextColor(mContext.getResources().getColor(org.matrix.androidsdk.R.color.chat_gray_text));
        }

        // Sender avatar
        RoomMember sender = roomState.getMember(msg.userId);

        View avatarLeftView = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_roundAvatar_left);
        View avatarRightView = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_roundAvatar_right);

        // does the layout display the avatar ?
        if ((null != avatarLeftView) && (null != avatarRightView)) {
            View avatarLayoutView = null;

            avatarLayoutView = avatarLeftView;
            avatarRightView.setVisibility(View.GONE);

            final String userId = msg.userId;
            final String roomId = roomState.roomId;

            avatarLeftView.setClickable(true);

            avatarLeftView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return onAvatarLongClick(roomId, userId);
                }
            });

            // click on the avatar opens the details page
            avatarLeftView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onAvatarClick(roomId, userId);
                }
            });

            ImageView avatarImageView = (ImageView) avatarLayoutView.findViewById(org.matrix.androidsdk.R.id.avatar_img);

            ImageView presenceView = (ImageView) avatarLayoutView.findViewById(org.matrix.androidsdk.R.id.imageView_presenceRing);
            presenceView.setColorFilter(mContext.getResources().getColor(android.R.color.transparent));

            updatePresenceRing(presenceView, userId);

            if (isMergedView) {
                avatarLayoutView.setVisibility(View.GONE);
            } else {
                avatarLayoutView.setVisibility(View.VISIBLE);
                avatarImageView.setTag(null);
                avatarImageView.setImageResource(org.matrix.androidsdk.R.drawable.ic_contact_picture_holo_light);

                String url = null;

                // Check whether this avatar url is updated by the current event (This happens in case of new joined member)
                if (msg.content.has("avatar_url")) {
                    url = msg.content.get("avatar_url") == JsonNull.INSTANCE ? null : msg.content.get("avatar_url").getAsString();
                }

                if ((sender != null) && (null == url)) {
                    url = sender.avatarUrl;
                }

                if (TextUtils.isEmpty(url) && (null != msg.userId)) {
                    url = ContentManager.getIdenticonURL(msg.userId);
                }

                if (!TextUtils.isEmpty(url)) {
                    loadAvatar(avatarImageView, url);
                }

                // display the typing icon when required
                ImageView typingImage = (ImageView) avatarLayoutView.findViewById(org.matrix.androidsdk.R.id.avatar_typing_img);
                typingImage.setVisibility(((mTypingUsers.indexOf(msg.userId) >= 0)) ? View.VISIBLE : View.GONE);
            }

            // if the messages are merged
            // the thumbnail is hidden
            // and the subview must be moved to be aligned with the previous body
            View bodyLayoutView = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_body_layout);
            ViewGroup.MarginLayoutParams bodyLayout = (ViewGroup.MarginLayoutParams) bodyLayoutView.getLayoutParams();
            FrameLayout.LayoutParams subViewLinearLayout = (FrameLayout.LayoutParams) subView.getLayoutParams();

            View view = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_roundAvatar_left);
            ViewGroup.LayoutParams avatarLayout = view.getLayoutParams();

            subViewLinearLayout.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;

            if (isMergedView) {
                bodyLayout.setMargins(avatarLayout.width, bodyLayout.topMargin, 4, bodyLayout.bottomMargin);

            } else {
                bodyLayout.setMargins(4, bodyLayout.topMargin, 4, bodyLayout.bottomMargin);
            }
            subView.setLayoutParams(bodyLayout);

            bodyLayoutView.setLayoutParams(bodyLayout);
            subView.setLayoutParams(subViewLinearLayout);

            view = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_separator);

            if (null != view) {
                view.setVisibility((willBeMerged || ((position + 1) == this.getCount())) ? View.GONE : View.VISIBLE);
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
