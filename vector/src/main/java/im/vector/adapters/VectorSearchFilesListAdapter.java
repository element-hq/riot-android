/*
 * Copyright 2015 OpenMarket Ltd
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
import android.media.ExifInterface;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.crypto.EncryptedFileInfo;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.message.FileMessage;
import org.matrix.androidsdk.rest.model.message.ImageMessage;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.message.VideoMessage;
import org.matrix.androidsdk.util.JsonUtils;

import im.vector.R;
import im.vector.util.VectorUtils;

/**
 * An adapter which display a files search result
 */
public class VectorSearchFilesListAdapter extends VectorMessagesAdapter {

    // display the room name in the result view
    private final boolean mDisplayRoomName;

    public VectorSearchFilesListAdapter(MXSession session, Context context, boolean displayRoomName, MXMediasCache mediasCache) {
        super(session, context, mediasCache);

        mDisplayRoomName = displayRoomName;
        setNotifyOnChange(true);
    }


    protected boolean mergeView(Event event, int position, boolean shouldBeMerged) {
        return false;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.adapter_item_vector_search_file_by_name, parent, false);
        }

        if (!mSession.isAlive()) {
            return convertView;
        }

        MessageRow row = getItem(position);
        Event event = row.getEvent();

        Message message = JsonUtils.toMessage(event.getContent());

        // common info
        String thumbUrl = null;
        Long mediaSize = null;
        int avatarId = R.drawable.filetype_attachment;
        EncryptedFileInfo encryptedFileInfo = null;

        if (Message.MSGTYPE_IMAGE.equals(message.msgtype)) {
            ImageMessage imageMessage = JsonUtils.toImageMessage(event.getContent());
            thumbUrl = imageMessage.getThumbnailUrl();

            if (null == thumbUrl) {
                thumbUrl = imageMessage.getUrl();
            }

            if (null != imageMessage.info) {
                mediaSize = imageMessage.info.size;
            }

            if ("image/gif".equals(imageMessage.getMimeType())) {
                avatarId = R.drawable.filetype_gif;
            } else {
                avatarId = R.drawable.filetype_image;
            }

            if (null != imageMessage.info) {
                encryptedFileInfo = imageMessage.info.thumbnail_file;
            }
        } else if (Message.MSGTYPE_VIDEO.equals(message.msgtype)) {
            VideoMessage videoMessage = JsonUtils.toVideoMessage(event.getContent());

            thumbUrl = videoMessage.getThumbnailUrl();

            if (null != videoMessage.info) {
                mediaSize = videoMessage.info.size;
            }

            avatarId = R.drawable.filetype_video;

            if (null != videoMessage.info) {
                encryptedFileInfo = videoMessage.info.thumbnail_file;
            }

        } else if (Message.MSGTYPE_FILE.equals(message.msgtype) || Message.MSGTYPE_AUDIO.equals(message.msgtype)) {
            FileMessage fileMessage = JsonUtils.toFileMessage(event.getContent());

            if (null != fileMessage.info) {
                mediaSize = fileMessage.info.size;
            }

            avatarId = Message.MSGTYPE_AUDIO.equals(message.msgtype) ? R.drawable.filetype_audio : R.drawable.filetype_attachment;
        }

        // thumbnail
        ImageView thumbnailView = convertView.findViewById(R.id.file_search_thumbnail);

        // default avatar
        thumbnailView.setImageResource(avatarId);

        if (null != thumbUrl) {
            // detect if the media is encrypted
            if (null == encryptedFileInfo) {
                int size = getContext().getResources().getDimensionPixelSize(R.dimen.member_list_avatar_size);
                mSession.getMediasCache().loadAvatarThumbnail(mSession.getHomeServerConfig(), thumbnailView, thumbUrl, size);
            } else {
                mSession.getMediasCache().loadBitmap(mSession.getHomeServerConfig(), thumbnailView, thumbUrl, 0, ExifInterface.ORIENTATION_UNDEFINED, null, encryptedFileInfo);
            }
        }

        // filename
        TextView filenameTextView = convertView.findViewById(R.id.file_search_filename);
        filenameTextView.setText(message.body);

        // room and date&time
        TextView roomNameTextView = convertView.findViewById(R.id.file_search_room_name);
        String info = "";
        if (mDisplayRoomName) {
            Room room = mSession.getDataHandler().getStore().getRoom(event.roomId);

            if (null != room) {
                info += VectorUtils.getRoomDisplayName(mContext, mSession, room);
                info += " - ";
            }
        }

        info += AdapterUtils.tsToString(mContext, event.getOriginServerTs(), false);
        roomNameTextView.setText(info);

        // file size
        TextView fileSizeTextView = convertView.findViewById(R.id.search_file_size);

        if ((null != mediaSize) && (mediaSize > 1)) {
            fileSizeTextView.setText(Formatter.formatFileSize(mContext, mediaSize));
        } else {
            fileSizeTextView.setText("");
        }

        return convertView;
    }
}
