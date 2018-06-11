/*
 * Copyright 2017 Vector Creations Ltd
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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.URLPreview;
import org.matrix.androidsdk.rest.model.group.Group;
import org.matrix.androidsdk.rest.model.group.GroupProfile;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.message.StickerMessage;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;
import org.matrix.androidsdk.view.HtmlTagHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.listeners.IMessagesAdapterActionsListener;
import im.vector.util.MatrixLinkMovementMethod;
import im.vector.util.MatrixURLSpan;
import im.vector.util.PreferencesManager;
import im.vector.util.RiotEventDisplay;
import im.vector.util.ThemeUtils;
import im.vector.util.VectorImageGetter;
import im.vector.util.VectorUtils;
import im.vector.view.PillView;
import im.vector.view.UrlPreviewView;
import im.vector.widgets.WidgetsManager;

/**
 * An helper to display message information
 */
class VectorMessagesAdapterHelper {
    private static final String LOG_TAG = VectorMessagesAdapterHelper.class.getSimpleName();

    /**
     * Enable multiline mode, split on <pre><code>...</code></pre> and retain those delimiters in
     * the returned fenced block.
     */
    public static final String START_FENCED_BLOCK = "<pre><code>";
    public static final String END_FENCED_BLOCK = "</code></pre>";
    private static final Pattern FENCED_CODE_BLOCK_PATTERN = Pattern.compile("(?m)(?=<pre><code>)|(?<=</code></pre>)");

    private IMessagesAdapterActionsListener mEventsListener;

    private final Context mContext;
    private final MXSession mSession;
    private final VectorMessagesAdapter mAdapter;
    private Room mRoom = null;

    private MatrixLinkMovementMethod mLinkMovementMethod;

    private VectorImageGetter mImageGetter;


    VectorMessagesAdapterHelper(Context context, MXSession session, VectorMessagesAdapter adapter) {
        mContext = context;
        mSession = session;
        mAdapter = adapter;
    }

    /**
     * Define the events listener
     *
     * @param listener the events listener
     */
    void setVectorMessagesAdapterActionsListener(IMessagesAdapterActionsListener listener) {
        mEventsListener = listener;
    }

    /**
     * Define the links movement method
     *
     * @param method the links movement method
     */
    void setLinkMovementMethod(MatrixLinkMovementMethod method) {
        mLinkMovementMethod = method;
    }

    /**
     * Set the image getter.
     *
     * @param imageGetter the image getter
     */
    void setImageGetter(VectorImageGetter imageGetter) {
        mImageGetter = imageGetter;
    }

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
     */
    public void setSenderValue(View convertView, MessageRow row, boolean isMergedView) {
        // manage sender text
        TextView senderTextView = convertView.findViewById(R.id.messagesAdapter_sender);
        View groupFlairView = convertView.findViewById(R.id.messagesAdapter_flair_groups_list);

        if (null != senderTextView) {
            Event event = row.getEvent();

            // Hide the group flair by default
            groupFlairView.setVisibility(View.GONE);
            groupFlairView.setTag(null);

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
                        Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(eventType) ||
                        Event.EVENT_TYPE_STATE_HISTORY_VISIBILITY.equals(eventType) ||
                        Event.EVENT_TYPE_MESSAGE_ENCRYPTION.equals(eventType)) {
                    senderTextView.setVisibility(View.GONE);
                } else {
                    senderTextView.setVisibility(View.VISIBLE);
                    senderTextView.setText(getUserDisplayName(event.getSender(), row.getRoomState()));

                    final String fSenderId = event.getSender();
                    final String fDisplayName = (null == senderTextView.getText()) ? "" : senderTextView.getText().toString();

                    senderTextView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (null != mEventsListener) {
                                mEventsListener.onSenderNameClick(fSenderId, fDisplayName);
                            }
                        }
                    });

                    refreshGroupFlairView(groupFlairView, event);
                }
            }
        }
    }

    /**
     * Refresh the flairs group view
     *
     * @param groupFlairView the flairs view
     * @param event          the event
     * @param groupIdsSet    the groupids
     * @param tag            the tag
     */
    private void refreshGroupFlairView(final View groupFlairView, final Event event, final Set<String> groupIdsSet, final String tag) {
        Log.d(LOG_TAG, "## refreshGroupFlairView () : " + event.sender + " allows flair to " + groupIdsSet);
        Log.d(LOG_TAG, "## refreshGroupFlairView () : room related groups " + mRoom.getLiveState().getRelatedGroups());

        if (!groupIdsSet.isEmpty()) {
            // keeps only the intersections
            groupIdsSet.retainAll(mRoom.getLiveState().getRelatedGroups());
        }

        Log.d(LOG_TAG, "## refreshGroupFlairView () : group ids to display " + groupIdsSet);

        if (groupIdsSet.isEmpty()) {
            groupFlairView.setVisibility(View.GONE);
        } else {

            if (!mSession.isAlive()) {
                return;
            }

            groupFlairView.setVisibility(View.VISIBLE);

            ArrayList<ImageView> imageViews = new ArrayList<>();

            imageViews.add((ImageView) (groupFlairView.findViewById(R.id.message_avatar_group_1).findViewById(R.id.avatar_img)));
            imageViews.add((ImageView) (groupFlairView.findViewById(R.id.message_avatar_group_2).findViewById(R.id.avatar_img)));
            imageViews.add((ImageView) (groupFlairView.findViewById(R.id.message_avatar_group_3).findViewById(R.id.avatar_img)));

            TextView moreText = groupFlairView.findViewById(R.id.message_more_than_expected);

            final List<String> groupIds = new ArrayList<>(groupIdsSet);
            int index = 0;
            int bound = Math.min(groupIds.size(), imageViews.size());

            for (; index < bound; index++) {
                final String groupId = groupIds.get(index);
                final ImageView imageView = imageViews.get(index);

                imageView.setVisibility(View.VISIBLE);

                Group group = mSession.getGroupsManager().getGroup(groupId);

                if (null == group) {
                    group = new Group(groupId);
                }

                GroupProfile cachedGroupProfile = mSession.getGroupsManager().getGroupProfile(groupId);

                if (null != cachedGroupProfile) {
                    Log.d(LOG_TAG, "## refreshGroupFlairView () : profile of " + groupId + " is cached");
                    group.setGroupProfile(cachedGroupProfile);
                    VectorUtils.loadGroupAvatar(mContext, mSession, imageView, group);
                } else {
                    VectorUtils.loadGroupAvatar(mContext, mSession, imageView, group);

                    Log.d(LOG_TAG, "## refreshGroupFlairView () : get profile of " + groupId);

                    mSession.getGroupsManager().getGroupProfile(groupId, new ApiCallback<GroupProfile>() {
                        private void refresh(GroupProfile profile) {
                            if (TextUtils.equals((String) groupFlairView.getTag(), tag)) {
                                Group group = new Group(groupId);
                                group.setGroupProfile(profile);
                                Log.d(LOG_TAG, "## refreshGroupFlairView () : refresh group avatar " + groupId);
                                VectorUtils.loadGroupAvatar(mContext, mSession, imageView, group);
                            }
                        }

                        @Override
                        public void onSuccess(GroupProfile groupProfile) {
                            Log.d(LOG_TAG, "## refreshGroupFlairView () : get profile of " + groupId + " succeeded");
                            refresh(groupProfile);
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            Log.e(LOG_TAG, "## refreshGroupFlairView () : get profile of " + groupId + " failed " + e.getMessage());
                            refresh(null);
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            Log.e(LOG_TAG, "## refreshGroupFlairView () : get profile of " + groupId + " failed " + e.getMessage());
                            refresh(null);
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            Log.e(LOG_TAG, "## refreshGroupFlairView () : get profile of " + groupId + " failed " + e.getMessage());
                            refresh(null);
                        }
                    });
                }
            }

            for (; index < imageViews.size(); index++) {
                imageViews.get(index).setVisibility(View.GONE);
            }

            moreText.setVisibility((groupIdsSet.size() <= imageViews.size()) ? View.GONE : View.VISIBLE);
            moreText.setText("+" + (groupIdsSet.size() - imageViews.size()));

            if (groupIdsSet.size() > 0) {
                groupFlairView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (null != mEventsListener) {
                            mEventsListener.onGroupFlairClick(event.getSender()
                                    , groupIds);
                        }
                    }
                });
            } else {
                groupFlairView.setOnClickListener(null);
            }
        }
    }

    /**
     * Refresh the group flair view
     *
     * @param groupFlairView the flairs view
     * @param event          the event
     */
    private void refreshGroupFlairView(final View groupFlairView, final Event event) {
        final String tag = event.getSender() + "__" + event.eventId;

        if (null == mRoom) {
            // The flair handling required the room state. So we retrieve the current room (if any).
            // Do not create it if it is not available. For example the room is not available during a room preview.
            // Indeed the room is then stored in memory, and we could not reach it from here for the moment.
            // TODO render the flair in the room preview history.
            mRoom = mSession.getDataHandler().getRoom(event.roomId, false);

            if (null == mRoom) {
                Log.d(LOG_TAG, "## refreshGroupFlairView () : the room is not available");
                groupFlairView.setVisibility(View.GONE);
                return;
            }
        }

        // Check whether there are some related groups to this room
        if (mRoom.getLiveState().getRelatedGroups().isEmpty()) {
            Log.d(LOG_TAG, "## refreshGroupFlairView () : no related group");
            groupFlairView.setVisibility(View.GONE);
            return;
        }

        groupFlairView.setTag(tag);

        Log.d(LOG_TAG, "## refreshGroupFlairView () : eventId " + event.eventId + " from " + event.sender);

        // cached value first
        Set<String> userPublicisedGroups = mSession.getGroupsManager().getUserPublicisedGroups(event.getSender());

        if (null != userPublicisedGroups) {
            refreshGroupFlairView(groupFlairView, event, userPublicisedGroups, tag);
        } else {
            groupFlairView.setVisibility(View.GONE);
            mSession.getGroupsManager().getUserPublicisedGroups(event.getSender(), false, new ApiCallback<Set<String>>() {
                @Override
                public void onSuccess(Set<String> groupIdsSet) {
                    refreshGroupFlairView(groupFlairView, event, groupIdsSet, tag);
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.e(LOG_TAG, "## refreshGroupFlairView failed " + e.getMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.e(LOG_TAG, "## refreshGroupFlairView failed " + e.getMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.e(LOG_TAG, "## refreshGroupFlairView failed " + e.getMessage());
                }
            });
        }
    }

    /**
     * init the timeStamp value
     *
     * @param convertView the base view
     * @param value       the new value
     * @return the dedicated textView
     */
    static TextView setTimestampValue(View convertView, String value) {
        TextView tsTextView = convertView.findViewById(R.id.messagesAdapter_timestamp);

        if (null != tsTextView) {
            if (TextUtils.isEmpty(value)) {
                tsTextView.setVisibility(View.GONE);
            } else {
                tsTextView.setVisibility(View.VISIBLE);
                tsTextView.setText(value);
            }
        }

        return tsTextView;
    }

    // JSON keys
    private static final String AVATAR_URL_KEY = "avatar_url";
    private static final String MEMBERSHIP_KEY = "membership";
    private static final String DISPLAYNAME_KEY = "displayname";

    /**
     * Load the avatar image in the avatar view
     *
     * @param avatarView the avatar view
     * @param row        the message row
     */
    void loadMemberAvatar(ImageView avatarView, MessageRow row) {
        RoomState roomState = row.getRoomState();
        Event event = row.getEvent();

        RoomMember roomMember = null;

        if (null != roomState) {
            roomMember = roomState.getMember(event.getSender());
        }

        String url = null;
        String displayName = null;

        // Check whether this avatar url is updated by the current event (This happens in case of new joined member)
        JsonObject msgContent = event.getContentAsJsonObject();

        if (msgContent.has(AVATAR_URL_KEY)) {
            url = msgContent.get(AVATAR_URL_KEY) == JsonNull.INSTANCE ? null : msgContent.get(AVATAR_URL_KEY).getAsString();
        }

        if (msgContent.has(MEMBERSHIP_KEY)) {
            String memberShip = msgContent.get(MEMBERSHIP_KEY) == JsonNull.INSTANCE ? null : msgContent.get(MEMBERSHIP_KEY).getAsString();

            // the avatar url is the invited one not the inviter one.
            if (TextUtils.equals(memberShip, RoomMember.MEMBERSHIP_INVITE)) {
                url = null;

                if (null != roomMember) {
                    url = roomMember.getAvatarUrl();
                }
            }

            if (TextUtils.equals(memberShip, RoomMember.MEMBERSHIP_JOIN)) {
                // in some cases, the displayname cannot be retrieved because the user member joined the room with this event
                // without being invited (a public room for example)
                if (msgContent.has(DISPLAYNAME_KEY)) {
                    displayName = msgContent.get(DISPLAYNAME_KEY) == JsonNull.INSTANCE ? null : msgContent.get(DISPLAYNAME_KEY).getAsString();
                }
            }
        }

        final String userId = event.getSender();

        if (!mSession.isAlive()) {
            return;
        }

        // if there is no preferred display name, use the member one
        if (TextUtils.isEmpty(displayName) && (null != roomMember)) {
            displayName = roomMember.displayname;
        }

        if ((roomMember != null) && (null == url)) {
            url = roomMember.getAvatarUrl();
        }

        if (null != roomMember) {
            VectorUtils.loadUserAvatar(mContext, mSession, avatarView, url, roomMember.getUserId(), displayName);
        } else {
            VectorUtils.loadUserAvatar(mContext, mSession, avatarView, url, userId, displayName);
        }
    }

    /**
     * init the sender avatar
     *
     * @param convertView  the base view
     * @param row          the message row
     * @param isMergedView true if the cell is merged
     * @return the avatar layout
     */
    View setSenderAvatar(View convertView, MessageRow row, boolean isMergedView) {
        Event event = row.getEvent();
        View avatarLayoutView = convertView.findViewById(R.id.messagesAdapter_roundAvatar);

        if (null != avatarLayoutView) {
            final String userId = event.getSender();

            avatarLayoutView.setClickable(true);
            avatarLayoutView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return (null != mEventsListener) && mEventsListener.onAvatarLongClick(userId);
                }
            });

            // click on the avatar opens the details page
            avatarLayoutView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mEventsListener) {
                        mEventsListener.onAvatarClick(userId);
                    }
                }
            });
        }

        if (null != avatarLayoutView) {
            ImageView avatarImageView = avatarLayoutView.findViewById(R.id.avatar_img);

            if (isMergedView) {
                avatarLayoutView.setVisibility(View.GONE);
            } else {
                avatarLayoutView.setVisibility(View.VISIBLE);
                avatarImageView.setTag(null);

                loadMemberAvatar(avatarImageView, row);
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
    static void alignSubviewToAvatarView(View subView, View bodyLayoutView, View avatarLayoutView, boolean isMergedView) {
        ViewGroup.MarginLayoutParams bodyLayout = (ViewGroup.MarginLayoutParams) bodyLayoutView.getLayoutParams();
        FrameLayout.LayoutParams subViewLinearLayout = (FrameLayout.LayoutParams) subView.getLayoutParams();

        ViewGroup.LayoutParams avatarLayout = avatarLayoutView.getLayoutParams();
        subViewLinearLayout.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;

        if (isMergedView) {
            bodyLayout.setMargins(avatarLayout.width, bodyLayout.topMargin, 4, bodyLayout.bottomMargin);
        } else {
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
    static void setHeader(View convertView, String newValue, int position) {
        // display the day separator
        View headerLayout = convertView.findViewById(R.id.messagesAdapter_message_header);

        if (null != headerLayout) {
            if (null != newValue) {
                TextView headerText = convertView.findViewById(R.id.messagesAdapter_message_header_text);
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
     * Hide the sticker description view
     *
     * @param convertView base view
     */
    public void hideStickerDescription(View convertView) {
        View stickerDescription = convertView.findViewById(R.id.message_adapter_sticker_layout);

        if (null != stickerDescription) {
            stickerDescription.setVisibility(View.GONE);
        }
    }

    /**
     * Show the sticker description view
     *
     * @param view           base view
     * @param stickerMessage the sticker message
     */
    public void showStickerDescription(View view, StickerMessage stickerMessage) {
        View stickerDescriptionLayout = view.findViewById(R.id.message_adapter_sticker_layout);
        ImageView stickerTriangle = view.findViewById(R.id.message_adapter_sticker_triangle);
        TextView stickerDescription = view.findViewById(R.id.message_adapter_sticker_description);

        if (null != stickerDescriptionLayout && null != stickerTriangle && null != stickerDescription) {
            stickerDescriptionLayout.setVisibility(View.VISIBLE);
            stickerTriangle.setVisibility(View.VISIBLE);
            stickerDescription.setVisibility(View.VISIBLE);
            stickerDescription.setText(stickerMessage.body);
        }
    }

    /**
     * Hide the read receipts view
     *
     * @param convertView base view
     */
    void hideReadReceipts(View convertView) {
        View avatarsListView = convertView.findViewById(R.id.messagesAdapter_avatars_list);

        if (null != avatarsListView) {
            avatarsListView.setVisibility(View.GONE);
        }
    }

    /**
     * Display the read receipts within the dedicated vector layout.
     * Console application displays them on the message side.
     * Vector application displays them in a dedicated line under the message
     *
     * @param convertView   base view
     * @param row           the message row
     * @param isPreviewMode true if preview mode
     */
    void displayReadReceipts(View convertView, MessageRow row, boolean isPreviewMode) {
        View avatarsListView = convertView.findViewById(R.id.messagesAdapter_avatars_list);

        if (null == avatarsListView) {
            return;
        }

        if (!mSession.isAlive()) {
            return;
        }

        final String eventId = row.getEvent().eventId;
        RoomState roomState = row.getRoomState();

        IMXStore store = mSession.getDataHandler().getStore();

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

        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_1).findViewById(R.id.avatar_img));
        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_2).findViewById(R.id.avatar_img));
        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_3).findViewById(R.id.avatar_img));
        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_4).findViewById(R.id.avatar_img));
        imageViews.add(avatarsListView.findViewById(R.id.message_avatar_receipt_5).findViewById(R.id.avatar_img));

        TextView moreText = avatarsListView.findViewById(R.id.message_more_than_expected);

        int index = 0;
        int bound = Math.min(receipts.size(), imageViews.size());

        for (; index < bound; index++) {
            final ReceiptData r = receipts.get(index);
            RoomMember member = roomState.getMember(r.userId);
            ImageView imageView = (ImageView) imageViews.get(index);

            imageView.setVisibility(View.VISIBLE);
            imageView.setTag(null);

            if (null != member) {
                VectorUtils.loadRoomMemberAvatar(mContext, mSession, imageView, member);
            } else {
                // should never happen
                VectorUtils.loadUserAvatar(mContext, mSession, imageView, null, r.userId, r.userId);
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
                    if (null != mEventsListener) {
                        mEventsListener.onMoreReadReceiptClick(eventId);
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
     * @param convertView    the convert view
     * @param bodyLayoutView the body layout
     */
    static void setMediaProgressLayout(View convertView, View bodyLayoutView) {
        ViewGroup.MarginLayoutParams bodyLayoutParams = (ViewGroup.MarginLayoutParams) bodyLayoutView.getLayoutParams();
        int marginLeft = bodyLayoutParams.leftMargin;

        View downloadProgressLayout = convertView.findViewById(R.id.content_download_progress_layout);

        if (null != downloadProgressLayout) {
            ViewGroup.MarginLayoutParams downloadProgressLayoutParams = (ViewGroup.MarginLayoutParams) downloadProgressLayout.getLayoutParams();
            downloadProgressLayoutParams.setMargins(marginLeft, downloadProgressLayoutParams.topMargin,
                    downloadProgressLayoutParams.rightMargin, downloadProgressLayoutParams.bottomMargin);
            downloadProgressLayout.setLayoutParams(downloadProgressLayoutParams);
        }

        View uploadProgressLayout = convertView.findViewById(R.id.content_upload_progress_layout);

        if (null != uploadProgressLayout) {
            ViewGroup.MarginLayoutParams uploadProgressLayoutParams = (ViewGroup.MarginLayoutParams) uploadProgressLayout.getLayoutParams();
            uploadProgressLayoutParams.setMargins(marginLeft, uploadProgressLayoutParams.topMargin,
                    uploadProgressLayoutParams.rightMargin, uploadProgressLayoutParams.bottomMargin);
            uploadProgressLayout.setLayoutParams(uploadProgressLayoutParams);
        }
    }

    // cache the pills to avoid compute them again
    private Map<String, Drawable> mPillsDrawableCache = new HashMap<>();

    /**
     * Trap the clicked URL.
     *
     * @param strBuilder    the input string
     * @param span          the URL
     * @param isHighlighted true if the message is highlighted
     */
    private void makeLinkClickable(SpannableStringBuilder strBuilder, final URLSpan span, final boolean isHighlighted) {
        int start = strBuilder.getSpanStart(span);
        int end = strBuilder.getSpanEnd(span);

        if (start >= 0 && end >= 0) {
            int flags = strBuilder.getSpanFlags(span);

            if (PillView.isPillable(span.getURL())) {
                final String key = span.getURL() + " " + isHighlighted;
                Drawable drawable = mPillsDrawableCache.get(key);

                if (null == drawable) {
                    PillView pillView = new PillView(mContext);
                    pillView.setBackgroundResource(android.R.color.transparent);
                    // Define a weak reference of the view because of the cross reference in the OnUpdateListener.
                    final WeakReference<PillView> weakView = new WeakReference<>(pillView);

                    pillView.initData(strBuilder.subSequence(start, end), span.getURL(), mSession, new PillView.OnUpdateListener() {
                        @Override
                        public void onAvatarUpdate() {
                            if ((null != weakView) && (null != weakView.get())) {
                                PillView pillView = weakView.get();
                                // get a drawable from the view (force to compose)
                                Drawable updatedDrawable = pillView.getDrawable(true);
                                mPillsDrawableCache.put(key, updatedDrawable);
                                // should update only the current cell
                                // but it might have been recycled
                                mAdapter.notifyDataSetChanged();
                            }
                        }
                    });
                    pillView.setHighlighted(isHighlighted);
                    drawable = pillView.getDrawable(false);
                }

                if (null != drawable) {
                    mPillsDrawableCache.put(key, drawable);
                    ImageSpan imageSpan = new ImageSpan(drawable);
                    drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                    strBuilder.setSpan(imageSpan, start, end, flags);
                }
            }

            ClickableSpan clickable = new ClickableSpan() {
                public void onClick(View view) {
                    if (null != mEventsListener) {
                        mEventsListener.onURLClick(Uri.parse(span.getURL()));
                    }
                }
            };

            strBuilder.setSpan(clickable, start, end, flags);
            strBuilder.removeSpan(span);
        }
    }

    /**
     * Determine if the message body contains any code blocks.
     *
     * @param message the message
     * @return true if it contains code blocks
     */
    boolean containsFencedCodeBlocks(final Message message) {
        return (null != message.formatted_body) &&
                message.formatted_body.contains(START_FENCED_BLOCK) &&
                message.formatted_body.contains(END_FENCED_BLOCK);
    }

    private Map<String, String[]> mCodeBlocksMap = new HashMap<>();

    /**
     * Split the message body with code blocks delimiters.
     *
     * @param message the message
     * @return the split message body
     */
    String[] getFencedCodeBlocks(final Message message) {
        if (TextUtils.isEmpty(message.formatted_body)) {
            return new String[0];
        }

        String[] codeBlocks = mCodeBlocksMap.get(message.formatted_body);

        if (null == codeBlocks) {
            codeBlocks = FENCED_CODE_BLOCK_PATTERN.split(message.formatted_body);
            mCodeBlocksMap.put(message.formatted_body, codeBlocks);
        }

        return codeBlocks;
    }

    /**
     * Highlight fenced code
     *
     * @param textView the text view
     */
    void highlightFencedCode(final TextView textView) {
        // sanity check
        if (null == textView) {
            return;
        }

        textView.setBackgroundColor(ThemeUtils.INSTANCE.getColor(mContext, R.attr.markdown_block_background_color));
    }

    /**
     * Apply link movement method to the TextView if not null
     *
     * @param textView
     */
    void applyLinkMovementMethod(@Nullable final TextView textView) {
        if (textView != null && mLinkMovementMethod != null) {
            textView.setMovementMethod(mLinkMovementMethod);
        }
    }

    /**
     * Highlight the pattern in the text.
     *
     * @param text               the text to display
     * @param pattern            the  pattern
     * @param highLightTextStyle the highlight text style
     * @param isHighlighted      true when the message is highlighted
     * @return CharSequence of the text with highlighted pattern
     */
    CharSequence highlightPattern(Spannable text, String pattern, CharacterStyle highLightTextStyle, boolean isHighlighted) {
        if (!TextUtils.isEmpty(pattern) && !TextUtils.isEmpty(text) && (text.length() >= pattern.length())) {

            String lowerText = text.toString().toLowerCase(VectorApp.getApplicationLocale());
            String lowerPattern = pattern.toLowerCase(VectorApp.getApplicationLocale());

            int start = 0;
            int pos = lowerText.indexOf(lowerPattern, start);

            while (pos >= 0) {
                start = pos + lowerPattern.length();
                text.setSpan(highLightTextStyle, pos, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), pos, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                pos = lowerText.indexOf(lowerPattern, start);
            }
        }

        SpannableStringBuilder strBuilder = new SpannableStringBuilder(text);
        URLSpan[] urls = strBuilder.getSpans(0, text.length(), URLSpan.class);

        if ((null != urls) && (urls.length > 0)) {
            for (URLSpan span : urls) {
                makeLinkClickable(strBuilder, span, isHighlighted);
            }
        }

        MatrixURLSpan.refreshMatrixSpans(strBuilder, mEventsListener);

        return strBuilder;
    }


    CharSequence convertToHtml(String htmlFormattedText) {
        final HtmlTagHandler htmlTagHandler = new HtmlTagHandler();
        htmlTagHandler.mContext = mContext;
        htmlTagHandler.setCodeBlockBackgroundColor(ThemeUtils.INSTANCE.getColor(mContext, R.attr.markdown_block_background_color));

        CharSequence sequence;

        // an html format has been released
        if (null != htmlFormattedText) {
            boolean isCustomizable = !htmlFormattedText.contains("<a href=") && !htmlFormattedText.contains("<table>");

            // the links are not yet supported by ConsoleHtmlTagHandler
            // the markdown tables are not properly supported
            sequence = Html.fromHtml(htmlFormattedText, mImageGetter, isCustomizable ? htmlTagHandler : null);

            // sanity check
            if (!TextUtils.isEmpty(sequence)) {
                // remove trailing \n to avoid having empty lines..
                int markStart = 0;
                int markEnd = sequence.length() - 1;

                // search first non \n character
                for (; (markStart < sequence.length() - 1) && ('\n' == sequence.charAt(markStart)); markStart++)
                    ;

                // search latest non \n character
                for (; (markEnd >= 0) && ('\n' == sequence.charAt(markEnd)); markEnd--)
                    ;

                // empty string ?
                if (markEnd < markStart) {
                    sequence = sequence.subSequence(0, 0);
                } else {
                    sequence = sequence.subSequence(markStart, markEnd + 1);
                }
            }
        } else {
            sequence = "";
        }

        return sequence;
    }

    /**
     * Check if an event is displayable
     *
     * @param context the context
     * @param row     the row
     * @return true if the event is managed.
     */
    static boolean isDisplayableEvent(Context context, MessageRow row) {
        if (null == row) {
            return false;
        }

        RoomState roomState = row.getRoomState();
        Event event = row.getEvent();

        if ((null == roomState) || (null == event)) {
            return false;
        }

        String eventType = event.getType();

        if (Event.EVENT_TYPE_MESSAGE.equals(eventType)) {
            // A message is displayable as long as it has a body
            // Redacted messages should not be displayed
            Message message = JsonUtils.toMessage(event.getContent());
            return !event.isRedacted() && (!TextUtils.isEmpty(message.body) || TextUtils.equals(message.msgtype, Message.MSGTYPE_EMOTE));
        } else if (Event.EVENT_TYPE_STICKER.equals(eventType)) {
            // A sticker is displayable as long as it has a body
            // Redacted stickers should not be displayed
            StickerMessage stickerMessage = JsonUtils.toStickerMessage(event.getContent());
            return !TextUtils.isEmpty(stickerMessage.body) && !event.isRedacted();
        } else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(eventType)
                || Event.EVENT_TYPE_STATE_ROOM_NAME.equals(eventType)) {
            EventDisplay display = new RiotEventDisplay(context, event, roomState);
            return display.getTextualDisplay() != null;
        } else if (event.isCallEvent()) {
            return Event.EVENT_TYPE_CALL_INVITE.equals(eventType) ||
                    Event.EVENT_TYPE_CALL_ANSWER.equals(eventType) ||
                    Event.EVENT_TYPE_CALL_HANGUP.equals(eventType);
        } else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType) || Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(eventType)) {
            // if we can display text for it, it's valid.
            EventDisplay display = new RiotEventDisplay(context, event, roomState);
            return display.getTextualDisplay() != null;
        } else if (Event.EVENT_TYPE_STATE_HISTORY_VISIBILITY.equals(eventType)) {
            return true;
        } else if (Event.EVENT_TYPE_MESSAGE_ENCRYPTED.equals(eventType) || Event.EVENT_TYPE_MESSAGE_ENCRYPTION.equals(eventType)) {
            // if we can display text for it, it's valid.
            EventDisplay display = new RiotEventDisplay(context, event, roomState);
            return event.hasContentFields() && (display.getTextualDisplay() != null);
        } else if (TextUtils.equals(WidgetsManager.WIDGET_EVENT_TYPE, event.getType())) {
            // Matrix apps are enabled
            return true;
        }
        return false;
    }

    //================================================================================
    // HTML management
    //================================================================================

    private final HashMap<String, String> mHtmlMap = new HashMap<>();

    /**
     * Retrieves the sanitised html.
     * !!!!!! WARNING !!!!!!
     * IT IS NOT REMOTELY A COMPREHENSIVE SANITIZER AND SHOULD NOT BE TRUSTED FOR SECURITY PURPOSES.
     * WE ARE EFFECTIVELY RELYING ON THE LIMITED CAPABILITIES OF THE HTML RENDERER UI TO AVOID SECURITY ISSUES LEAKING UP.
     *
     * @param html the html to sanitize
     * @return the sanitised HTML
     */
    @Nullable
    String getSanitisedHtml(final String html) {
        // sanity checks
        if (TextUtils.isEmpty(html)) {
            return null;
        }

        String res = mHtmlMap.get(html);

        if (null == res) {
            res = sanitiseHTML(html);
            mHtmlMap.put(html, res);
        }

        return res;
    }

    private static final Set<String> mAllowedHTMLTags = new HashSet<>(Arrays.asList(
            "font", // custom to matrix for IRC-style font coloring
            "del", // for markdown
            "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "p", "a", "ul", "ol", "sup", "sub",
            "nl", "li", "b", "i", "u", "strong", "em", "strike", "code", "hr", "br", "div",
            "table", "thead", "caption", "tbody", "tr", "th", "td", "pre", "span", "img"));

    private static final Pattern mHtmlPatter = Pattern.compile("<(\\w+)[^>]*>", Pattern.CASE_INSENSITIVE);

    /**
     * Sanitise the HTML.
     * The matrix format does not allow the use some HTML tags.
     *
     * @param htmlString the html string
     * @return the sanitised string.
     */
    private static String sanitiseHTML(final String htmlString) {
        String html = htmlString;
        Matcher matcher = mHtmlPatter.matcher(htmlString);

        HashSet<String> tagsToRemove = new HashSet<>();

        while (matcher.find()) {

            try {
                String tag = htmlString.substring(matcher.start(1), matcher.end(1));

                // test if the tag is not allowed
                if (!mAllowedHTMLTags.contains(tag)) {
                    tagsToRemove.add(tag);
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "sanitiseHTML failed " + e.getLocalizedMessage());
            }
        }

        // some tags to remove ?
        if (!tagsToRemove.isEmpty()) {
            // append the tags to remove
            String tagsToRemoveString = "";

            for (String tag : tagsToRemove) {
                if (!tagsToRemoveString.isEmpty()) {
                    tagsToRemoveString += "|";
                }

                tagsToRemoveString += tag;
            }

            html = html.replaceAll("<\\/?(" + tagsToRemoveString + ")[^>]*>", "");
        }

        return html;
    }

    /*
     * *********************************************************************************************
     *  Url preview managements
     * *********************************************************************************************
     */
    private final Map<String, List<String>> mExtractedUrls = new HashMap<>();
    private final Map<String, URLPreview> mUrlsPreview = new HashMap<>();
    private final Set<String> mPendingUrls = new HashSet<>();
    private final Set<String> mDismissedPreviews = new HashSet<>();

    /**
     * Retrieves the webUrl extracted from a text
     *
     * @param text the text
     * @return the web urls list
     */
    private List<String> extractWebUrl(String text) {
        List<String> list = mExtractedUrls.get(text);

        if (null == list) {
            list = new ArrayList<>();

            Matcher matcher = android.util.Patterns.WEB_URL.matcher(text);
            while (matcher.find()) {
                try {
                    String value = text.substring(matcher.start(0), matcher.end(0));

                    if (!list.contains(value)) {
                        list.add(value);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## extractWebUrl() " + e.getMessage());
                }
            }

            mExtractedUrls.put(text, list);
        }

        return list;
    }

    void manageURLPreviews(final Message message, final View convertView, final String id) {
        LinearLayout urlsPreviewLayout = convertView.findViewById(R.id.messagesAdapter_urls_preview_list);

        // sanity checks
        if (null == urlsPreviewLayout) {
            return;
        }

        //
        if (TextUtils.isEmpty(message.body)) {
            urlsPreviewLayout.setVisibility(View.GONE);
            return;
        }

        List<String> urls = extractWebUrl(message.body);

        if (urls.isEmpty()) {
            urlsPreviewLayout.setVisibility(View.GONE);
            return;
        }

        // avoid removing items if they are displayed
        if (TextUtils.equals((String) urlsPreviewLayout.getTag(), id)) {
            // all the urls have been displayed
            if (urlsPreviewLayout.getChildCount() == urls.size()) {
                return;
            }
        }

        urlsPreviewLayout.setTag(id);

        // remove url previews
        while (urlsPreviewLayout.getChildCount() > 0) {
            urlsPreviewLayout.removeViewAt(0);
        }

        urlsPreviewLayout.setVisibility(View.VISIBLE);

        for (final String url : urls) {
            final String downloadKey = url.hashCode() + "---";
            String displayKey = url + "<----->" + id;

            if (UrlPreviewView.didUrlPreviewDismiss(displayKey)) {
                Log.d(LOG_TAG, "## manageURLPreviews() : " + displayKey + " has been dismissed");
            } else if (mPendingUrls.contains(url)) {
                // please wait
            } else if (!mUrlsPreview.containsKey(downloadKey)) {
                mPendingUrls.add(url);
                mSession.getEventsApiClient().getURLPreview(url, System.currentTimeMillis(), new ApiCallback<URLPreview>() {
                    @Override
                    public void onSuccess(URLPreview urlPreview) {
                        mPendingUrls.remove(url);

                        if (!mUrlsPreview.containsKey(downloadKey)) {
                            mUrlsPreview.put(downloadKey, urlPreview);
                            mAdapter.notifyDataSetChanged();
                        }
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        onSuccess(null);
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        onSuccess(null);
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        onSuccess(null);
                    }
                });
            } else {
                UrlPreviewView previewView = new UrlPreviewView(mContext);
                previewView.setUrlPreview(mContext, mSession, mUrlsPreview.get(downloadKey), displayKey);
                urlsPreviewLayout.addView(previewView);
            }
        }
    }
}
