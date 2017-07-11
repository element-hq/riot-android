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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.AbstractMessagesAdapter;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.IMXMediaDownloadListener;
import org.matrix.androidsdk.listeners.IMXMediaUploadListener;
import org.matrix.androidsdk.listeners.MXMediaDownloadListener;
import org.matrix.androidsdk.listeners.MXMediaUploadListener;
import org.matrix.androidsdk.rest.model.EncryptedEventContent;
import org.matrix.androidsdk.rest.model.EncryptedFileInfo;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageInfo;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.VideoInfo;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.EventUtils;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;
import org.matrix.androidsdk.view.ConsoleHtmlTagHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.listeners.IMessagesAdapterActionsListener;
import im.vector.util.MatrixLinkMovementMethod;
import im.vector.util.MatrixURLSpan;
import im.vector.util.VectorUtils;

/**
 * An adapter which can display room information.
 */
public class VectorMessagesAdapterHelper {

    /**
     * Returns an user display name for an user Id.
     *
     * @param userId    the user id.
     * @param roomState the room state
     * @return teh user display name.
     */
    public static String getUserDisplayName(String userId, RoomState roomState) {
        if (null != roomState) {
            return roomState.getMemberName(userId);
        } else {
            return userId;
        }
    }

    /**
     * init the sender value
     *
     * @param convertView  the base view
     * @param row          the message row
     * @param isMergedView true if the cell is merged
     * @param listener     the click events listener
     * @return the dedicated textView
     */
    public static TextView setSenderValue(View convertView, MessageRow row, boolean isMergedView, final IMessagesAdapterActionsListener listener) {
        // manage sender text
        TextView senderTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_sender);

        if (null != senderTextView) {
            Event event = row.getEvent();

            if (isMergedView) {
                senderTextView.setVisibility(View.GONE);
            } else {
                String eventType = event.getType();

                // theses events are managed like notice ones
                // but they are dedicated behaviour i.e the sender must not be displayed
                if (event.isCallEvent() ||
                        Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(eventType) ||
                        Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType) ||
                        Event.EVENT_TYPE_STATE_ROOM_NAME.equals(eventType) ||
                        Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(eventType)
                        ) {
                    senderTextView.setVisibility(View.GONE);
                } else {
                    senderTextView.setVisibility(View.VISIBLE);
                    senderTextView.setText(getUserDisplayName(event.getSender(), row.getRoomState()));

                    final String fSenderId = event.getSender();
                    final String fDisplayName = (null == senderTextView.getText()) ? "" : senderTextView.getText().toString();

                    senderTextView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (null != listener) {
                                listener.onSenderNameClick(fSenderId, fDisplayName);
                            }
                        }
                    });
                }
            }
        }

        return senderTextView;
    }

    /**
     * init the timeStamp value
     *
     * @param convertView the base view
     * @param value       the new value
     * @return the dedicated textView
     */
    public static TextView setTimestampValue(View convertView, String value) {
        TextView tsTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_timestamp);

        if (null != tsTextView) {
            if (TextUtils.isEmpty(value)) {
                tsTextView.setVisibility(View.GONE);
            } else {
                tsTextView.setVisibility(View.VISIBLE);
                tsTextView.setText(value);
                tsTextView.setGravity(Gravity.RIGHT);
            }
        }

        return tsTextView;
    }

    /**
     * Load the avatar image in the avatar view
     *
     * @param session     the session
     * @param context     the context
     * @param avatarView  the avatar view
     * @param member      the room member
     * @param userId      the user id
     * @param displayName the display name
     * @param url         the avatar url
     */
    public static void loadMemberAvatar(MXSession session, Context context, ImageView avatarView, RoomMember member, String userId, String displayName, String url) {
        if (!session.isAlive()) {
            return;
        }

        // if there is no preferred display name, use the member one
        if (TextUtils.isEmpty(displayName) && (null != member)) {
            displayName = member.displayname;
        }

        if ((member != null) && (null == url)) {
            url = member.getAvatarUrl();
        }

        if (null != member) {
            VectorUtils.loadUserAvatar(context, session, avatarView, url, member.getUserId(), displayName);
        } else {
            VectorUtils.loadUserAvatar(context, session, avatarView, url, userId, displayName);
        }
    }

    /**
     * init the sender avatar
     *
     * @param session      the session
     * @param context      the context
     * @param convertView  the base view
     * @param row          the message row
     * @param isMergedView true if the cell is merged
     * @param listener     the click events listener
     * @return the avatar layout
     */
    public static View setSenderAvatar(MXSession session, Context context, View convertView, MessageRow row, boolean isMergedView, final IMessagesAdapterActionsListener listener) {
        Event event = row.getEvent();
        RoomState roomState = row.getRoomState();

        RoomMember sender = null;

        if (null != roomState) {
            sender = roomState.getMember(event.getSender());
        }

        View avatarLayoutView = convertView.findViewById(R.id.messagesAdapter_roundAvatar);

        if (null != avatarLayoutView) {
            final String userId = event.getSender();

            avatarLayoutView.setClickable(true);

            avatarLayoutView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (null != listener) {
                        return listener.onAvatarLongClick(userId);
                    } else {
                        return false;
                    }
                }
            });

            // click on the avatar opens the details page
            avatarLayoutView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != listener) {
                        listener.onAvatarClick(userId);
                    }
                }
            });
        }

        if (null != avatarLayoutView) {
            ImageView avatarImageView = (ImageView) avatarLayoutView.findViewById(R.id.avatar_img);
            final String userId = event.getSender();

            if (isMergedView) {
                avatarLayoutView.setVisibility(View.GONE);
            } else {
                avatarLayoutView.setVisibility(View.VISIBLE);
                avatarImageView.setTag(null);

                String url = null;
                String displayName = null;

                // Check whether this avatar url is updated by the current event (This happens in case of new joined member)
                JsonObject msgContent = event.getContentAsJsonObject();

                if (msgContent.has("avatar_url")) {
                    url = msgContent.get("avatar_url") == JsonNull.INSTANCE ? null : msgContent.get("avatar_url").getAsString();
                }

                if (msgContent.has("membership")) {
                    String memberShip = msgContent.get("membership") == JsonNull.INSTANCE ? null : msgContent.get("membership").getAsString();

                    // the avatar url is the invited one not the inviter one.
                    if (TextUtils.equals(memberShip, RoomMember.MEMBERSHIP_INVITE)) {
                        url = null;

                        if (null != sender) {
                            url = sender.getAvatarUrl();
                        }
                    }

                    if (TextUtils.equals(memberShip, RoomMember.MEMBERSHIP_JOIN)) {
                        // in some cases, the displayname cannot be retrieved because the user member joined the room with this event
                        // without being invited (a public room for example)
                        if (msgContent.has("displayname")) {
                            displayName = msgContent.get("displayname") == JsonNull.INSTANCE ? null : msgContent.get("displayname").getAsString();
                        }
                    }
                }

                loadMemberAvatar(session, context, avatarImageView, sender, userId, displayName, url);
            }
        }

        return avatarLayoutView;
    }

    /**
     * Align the avatar and the message body according to the mergeView flag
     *
     * @param subView          the message body
     * @param bodyLayoutView   the body layout
     * @param avatarLayoutView the avatar layout
     * @param isMergedView     true if the view is merged
     */
    public static void alignSubviewToAvatarView(View subView, View bodyLayoutView, View avatarLayoutView, boolean isMergedView) {
        ViewGroup.MarginLayoutParams bodyLayout = (ViewGroup.MarginLayoutParams) bodyLayoutView.getLayoutParams();
        FrameLayout.LayoutParams subViewLinearLayout = (FrameLayout.LayoutParams) subView.getLayoutParams();

        ViewGroup.LayoutParams avatarLayout = avatarLayoutView.getLayoutParams();

        subViewLinearLayout.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;

        if (isMergedView)

        {
            bodyLayout.setMargins(avatarLayout.width, bodyLayout.topMargin, 4, bodyLayout.bottomMargin);
        } else

        {
            bodyLayout.setMargins(4, bodyLayout.topMargin, 4, bodyLayout.bottomMargin);
        }
        subView.setLayoutParams(bodyLayout);

        bodyLayoutView.setLayoutParams(bodyLayout);
        subView.setLayoutParams(subViewLinearLayout);
    }

    /**
     * Update the header text.
     *
     * @param convertView the convert view
     * @param newValue    the new value
     * @param position    the item position
     */
    public static void setHeader(View convertView, String newValue, int position) {
        // display the day separator
        View headerLayout = convertView.findViewById(R.id.messagesAdapter_message_header);

        if (null != headerLayout) {
            if (null != newValue) {
                TextView headerText = (TextView) convertView.findViewById(R.id.messagesAdapter_message_header_text);
                headerText.setText(newValue);
                headerLayout.setVisibility(View.VISIBLE);

                View topHeaderMargin = headerLayout.findViewById(R.id.messagesAdapter_message_header_top_margin);
                topHeaderMargin.setVisibility((0 == position) ? View.GONE : View.VISIBLE);
            } else {
                headerLayout.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Display the read receipts within the dedicated vector layout.
     * Console application displays them on the message side.
     * Vector application displays them in a dedicated line under the message
     *
     * @param context       the context
     * @param session       the session
     * @param convertView   base view
     * @param row           the message row
     * @param isPreviewMode true if preview mode
     * @param listener      the listener
     */
    public static void displayReadReceipts(Context context, MXSession session, View convertView, MessageRow row, boolean isPreviewMode, final IMessagesAdapterActionsListener listener) {
        View avatarsListView = convertView.findViewById(R.id.messagesAdapter_avatars_list);

        if (null == avatarsListView) {
            return;
        }

        if (!session.isAlive()) {
            return;
        }

        final String eventId = row.getEvent().eventId;
        RoomState roomState = row.getRoomState();

        IMXStore store = session.getDataHandler().getStore();

        // sanity check
        if (null == roomState) {
            avatarsListView.setVisibility(View.GONE);
            return;
        }

        // hide the read receipts until there is a way to retrieve them
        // without triggering a request per message
        if (isPreviewMode) {
            avatarsListView.setVisibility(View.GONE);
            return;
        }

        List<ReceiptData> receipts = store.getEventReceipts(roomState.roomId, eventId, true, true);

        // if there is no receipt to display
        // hide the dedicated layout
        if ((null == receipts) || (0 == receipts.size())) {
            avatarsListView.setVisibility(View.GONE);
            return;
        }

        avatarsListView.setVisibility(View.VISIBLE);

        ArrayList<View> imageViews = new ArrayList<>();

        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_1).findViewById(org.matrix.androidsdk.R.id.avatar_img));
        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_2).findViewById(org.matrix.androidsdk.R.id.avatar_img));
        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_3).findViewById(org.matrix.androidsdk.R.id.avatar_img));
        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_4).findViewById(org.matrix.androidsdk.R.id.avatar_img));
        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_5).findViewById(org.matrix.androidsdk.R.id.avatar_img));

        TextView moreText = (TextView) avatarsListView.findViewById(R.id.message_more_than_expected);

        int index = 0;
        int bound = Math.min(receipts.size(), imageViews.size());

        for (; index < bound; index++) {
            final ReceiptData r = receipts.get(index);
            RoomMember member = roomState.getMember(r.userId);
            ImageView imageView = (ImageView) imageViews.get(index);

            imageView.setVisibility(View.VISIBLE);
            imageView.setTag(null);

            if (null != member) {
                VectorUtils.loadRoomMemberAvatar(context, session, imageView, member);
            } else {
                // should never happen
                VectorUtils.loadUserAvatar(context, session, imageView, null, r.userId, r.userId);
            }
        }

        moreText.setVisibility((receipts.size() <= imageViews.size()) ? View.GONE : View.VISIBLE);
        moreText.setText((receipts.size() - imageViews.size()) + "+");

        for (; index < imageViews.size(); index++) {
            imageViews.get(index).setVisibility(View.INVISIBLE);
        }

        if (receipts.size() > 0) {
            avatarsListView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != listener) {
                        listener.onMoreReadReceiptClick(eventId);
                    }
                }
            });
        } else {
            avatarsListView.setOnClickListener(null);
        }
    }

    /**
     * Refresh the media progress layouts
     *
     * @param convertView the convert view
     * @param bodyLayoutView the body layout
     */
    public static void setMediaProgressLayout(View convertView, View bodyLayoutView) {
        ViewGroup.MarginLayoutParams bodyLayoutParams = (ViewGroup.MarginLayoutParams) bodyLayoutView.getLayoutParams();
        int marginLeft = bodyLayoutParams.leftMargin;

        View downloadProgressLayout = convertView.findViewById(R.id.content_download_progress_layout);

        if (null != downloadProgressLayout) {
            ViewGroup.MarginLayoutParams downloadProgressLayoutParams = (ViewGroup.MarginLayoutParams) downloadProgressLayout.getLayoutParams();
            downloadProgressLayoutParams.setMargins(marginLeft, downloadProgressLayoutParams.topMargin, downloadProgressLayoutParams.rightMargin, downloadProgressLayoutParams.bottomMargin);
            downloadProgressLayout.setLayoutParams(downloadProgressLayoutParams);
        }

        View uploadProgressLayout = convertView.findViewById(R.id.content_upload_progress_layout);

        if (null != uploadProgressLayout) {
            ViewGroup.MarginLayoutParams uploadProgressLayoutParams = (ViewGroup.MarginLayoutParams) uploadProgressLayout.getLayoutParams();
            uploadProgressLayoutParams.setMargins(marginLeft, uploadProgressLayoutParams.topMargin, uploadProgressLayoutParams.rightMargin, uploadProgressLayoutParams.bottomMargin);
            uploadProgressLayout.setLayoutParams(uploadProgressLayoutParams);
        }
    }
}
