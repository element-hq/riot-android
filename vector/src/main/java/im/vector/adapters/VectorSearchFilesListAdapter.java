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
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileInfo;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageInfo;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.VideoInfo;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.EventUtils;
import org.matrix.androidsdk.util.JsonUtils;

import im.vector.R;
import im.vector.util.VectorUtils;

/**
 * An adapter which display a files search result
 */
public class VectorSearchFilesListAdapter extends VectorMessagesAdapter {

    // display the room name in the result view
    private boolean mDisplayRoomName;

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

        Message message = JsonUtils.toMessage(event.content);

        // common info
        String thumbUrl = null;
        Long mediaSize = null;
        int avatarId = org.matrix.androidsdk.R.drawable.filetype_attachment;

        if (Message.MSGTYPE_IMAGE.equals(message.msgtype)) {
            ImageMessage imageMessage = JsonUtils.toImageMessage(event.content);
            thumbUrl = imageMessage.thumbnailUrl;

            if (null == thumbUrl) {
                thumbUrl = imageMessage.url;
            }

            if (null != imageMessage.info) {
                mediaSize = imageMessage.info.size;
            }

            if ("image/gif".equals(imageMessage.getMimeType())) {
                avatarId = org.matrix.androidsdk.R.drawable.filetype_gif;
            } else {
                avatarId = org.matrix.androidsdk.R.drawable.filetype_image;
            }

        } else if (Message.MSGTYPE_VIDEO.equals(message.msgtype)) {
            VideoMessage videoMessage = JsonUtils.toVideoMessage(event.content);

            if (null != videoMessage.info) {
                thumbUrl = videoMessage.info.thumbnail_url;
                mediaSize = videoMessage.info.size;
            }

            avatarId = org.matrix.androidsdk.R.drawable.filetype_video;

        } else if(Message.MSGTYPE_FILE.equals(message.msgtype)) {
            FileMessage fileMessage = JsonUtils.toFileMessage(event.content);

            if (null != fileMessage.info) {
                mediaSize = fileMessage.info.size;
            }

            avatarId = org.matrix.androidsdk.R.drawable.filetype_attachment;
        }

        // thumbnail
        ImageView thumbnailView = (ImageView)convertView.findViewById(R.id.file_search_thumbnail);

        // default avatar
        thumbnailView.setImageResource(avatarId);

        if (null != thumbUrl) {
            int size = getContext().getResources().getDimensionPixelSize(R.dimen.member_list_avatar_size);
            mSession.getMediasCache().loadAvatarThumbnail(mSession.getHomeserverConfig(), thumbnailView, thumbUrl, size);
        }

        // filename
        TextView filenameTextView = (TextView)convertView.findViewById(R.id.file_search_filename);
        filenameTextView.setText(message.body);

        // room and date&time
        TextView roomNameTextView = (TextView)convertView.findViewById(R.id.file_search_room_name);
        String info = "";
        if (mDisplayRoomName) {
            Room room = mSession.getDataHandler().getStore().getRoom(event.roomId);

            if (null != room) {
                info += VectorUtils.getRoomDisplayname(mContext, mSession, room);
                info += " - ";
            }
        }

        info +=  AdapterUtils.tsToString(mContext, event.getOriginServerTs(), false);
        roomNameTextView.setText(info);

        // file size
        TextView fileSizeTextView = (TextView)convertView.findViewById(R.id.search_file_size);

        if ((null != mediaSize) && (mediaSize > 1)) {
            fileSizeTextView.setText(Formatter.formatFileSize(mContext, mediaSize));
        } else {
            fileSizeTextView.setText("");
        }

        return convertView;
    }
}
