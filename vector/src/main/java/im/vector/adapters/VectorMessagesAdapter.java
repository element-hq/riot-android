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
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.IMXMediaDownloadListener;
import org.matrix.androidsdk.listeners.IMXMediaUploadListener;
import org.matrix.androidsdk.rest.model.EncryptedEventContent;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.JsonUtils;

import im.vector.VectorApp;
import im.vector.R;
import im.vector.util.VectorUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.acl.LastOwnerException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;

/**
 * An adapter which can display room information.
 */
public class VectorMessagesAdapter extends MessagesAdapter {

    private static final String LOG_TAG = "VMessagesAdapter";

    public interface VectorMessagesAdapterActionsListener {
        /**
         * An action has been  triggered on an event.
         * @param event the event.
         * @param textMsg the text message
         * @param action an action ic_action_vector_XXX
         */
        void onEventAction(final Event event, final String textMsg, final int action);

        /**
         * the user taps on the e2e icon
         * @param event the event
         * @param deviceInfo the deviceinfo
         */
        void onE2eIconClick(final Event event, final MXDeviceInfo deviceInfo);
    }

    // an event is highlighted when the user taps on it
    private String mHighlightedEventId;

    // events listeners
    private VectorMessagesAdapterActionsListener mVectorMessagesAdapterEventsListener = null;

    // current date : used to compute the day header
    private Date mReferenceDate = new Date();

    // day date of each message
    // the hours, minutes and seconds are removed
    private ArrayList<Date> mMessagesDateList = new ArrayList<>();

    // when the adapter is used in search mode
    // the searched message should be highlighted
    private String mSearchedEventId = null;

    // formatted time by event id
    // it avoids computing them several times
    private final HashMap<String, String> mEventFormattedTsMap = new HashMap<>();

    // define the e2e icon to use for a dedicated eventId
    private HashMap<String, Integer> mE2eIconByEventId = new HashMap<>();

    // device info by device id
    private HashMap<String, MXDeviceInfo> mE2eDeviceByEventId = new HashMap<>();

    // true when the room is encrypted
    public boolean mIsRoomEncrypted;

    /**
     * Expanded constructor.
     */
    public VectorMessagesAdapter(MXSession session, Context context, int textResLayoutId, int imageResLayoutId,
                                 int noticeResLayoutId, int emoteRestLayoutId, int fileResLayoutId, int videoResLayoutId, MXMediasCache mediasCache) {

        super(session, context,
                textResLayoutId,
                imageResLayoutId,
                noticeResLayoutId,
                emoteRestLayoutId,
                fileResLayoutId,
                videoResLayoutId,
                mediasCache);
    }

    /**
     * Creates a messages adapter with the default layouts.
     */
    public VectorMessagesAdapter(MXSession session, Context context, MXMediasCache mediasCache) {
        super(session, context,
                R.layout.adapter_item_vector_message_text_emote_notice,
                R.layout.adapter_item_vector_message_image_video,
                R.layout.adapter_item_vector_message_text_emote_notice,
                R.layout.adapter_item_vector_message_text_emote_notice,
                R.layout.adapter_item_vector_message_file,
                R.layout.adapter_item_vector_message_image_video,
                mediasCache);
    }

    @Override
    public int getEncryptingMessageTextColor(Context context) {
        return context.getResources().getColor(R.color.vector_green_color);
    }

    /**
     * the parent fragment is paused.
     */
    public void onPause() {
        mEventFormattedTsMap.clear();
    }

    /**
     * Toggle the selection mode.
     * @param eventId the tapped eventID.
     */
    public void onEventTap(String eventId) {
        // the tap to select is only enabled when the adapter is not in search mode.
        if (!mIsSearchMode) {
            if (null == mHighlightedEventId) {
                mHighlightedEventId = eventId;
            } else {
                mHighlightedEventId = null;
            }
            notifyDataSetChanged();
        }
    }

    /**
     * Display a bar to the left of the message
     * @param eventId the event id
     */
    public void setSearchedEventId(String eventId) {
       mSearchedEventId = eventId;
    }

    /**
     * Cancel the message selection mode
     */
    public void cancelSelectionMode() {
        if (null != mHighlightedEventId) {
            mHighlightedEventId = null;
            notifyDataSetChanged();
        }
    }

    /**
     * @return true if there is a selected item.
     */
    public boolean isInSelectionMode() {
        return null != mHighlightedEventId;
    }

    /**
     * Define the events listener
     * @param listener teh events listener
     */
    public void setVectorMessagesAdapterActionsListener(VectorMessagesAdapterActionsListener listener) {
        mVectorMessagesAdapterEventsListener = listener;
    }

    /**
     * Provides the formatted timestamp to display.
     * null means that the timestamp text must be hidden.
     * @param event the event.
     * @return  the formatted timestamp to display.
     */
    @Override
    protected String getFormattedTimestamp(Event event) {
        String res = mEventFormattedTsMap.get(event.eventId);

        if (null != res) {
            return res;
        }

        if (event.isValidOriginServerTs()) {
            res = AdapterUtils.tsToString(mContext, event.getOriginServerTs(), true);
        } else {
            res = " ";
        }

        mEventFormattedTsMap.put(event.eventId, res);

        return res;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);

        if (null != view) {
            view.setBackgroundColor(Color.TRANSPARENT);
        }

        ImageView e2eIconView = (ImageView)view.findViewById(R.id.message_adapter_e2e_icon);
        View senderMargin = view.findViewById(R.id.e2e_sender_margin);
        View senderNameView = view.findViewById(R.id.messagesAdapter_sender);

        MessageRow row = getItem(position);
        final Event event = row.getEvent();

        if (mE2eIconByEventId.containsKey(event.eventId)) {
            senderMargin.setVisibility(senderNameView.getVisibility());
            e2eIconView.setVisibility(View.VISIBLE);
            e2eIconView.setImageResource(mE2eIconByEventId.get(event.eventId));

            int type = getItemViewType(position);

            if ((type == ROW_TYPE_IMAGE) || (type == ROW_TYPE_VIDEO)) {
                View bodyLayoutView = view.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_body_layout);
                ViewGroup.MarginLayoutParams bodyLayout = (ViewGroup.MarginLayoutParams) bodyLayoutView.getLayoutParams();
                ViewGroup.MarginLayoutParams e2eIconViewLayout = (ViewGroup.MarginLayoutParams) e2eIconView.getLayoutParams();

                e2eIconViewLayout.setMargins(bodyLayout.leftMargin, e2eIconViewLayout.topMargin, e2eIconViewLayout.rightMargin, e2eIconViewLayout.bottomMargin);
                bodyLayout.setMargins(4, bodyLayout.topMargin, bodyLayout.rightMargin, bodyLayout.bottomMargin);
                e2eIconView.setLayoutParams(e2eIconViewLayout);
                bodyLayoutView.setLayoutParams(bodyLayout);
            }

            e2eIconView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mVectorMessagesAdapterEventsListener) {
                        mVectorMessagesAdapterEventsListener.onE2eIconClick(event, mE2eDeviceByEventId.get(event.eventId));
                    }
                }
            });
        } else {
            e2eIconView.setVisibility(View.GONE);
            senderMargin.setVisibility(View.GONE);
        }

        return view;
    }


    /**
     * Retrieves the MXDevice info from an event id
     * @param eventId the event id
     * @return the linked device info, null it it does not exist.
     */
    public MXDeviceInfo getDeviceInfo(String eventId) {
        MXDeviceInfo deviceInfo = null;

        if (null != eventId) {
            deviceInfo = mE2eDeviceByEventId.get(eventId);
        }

        return deviceInfo;
    }

    @Override
    protected void setTypingVisibility(View avatarLayoutView, int status) {
    }

    @Override
    protected void refreshPresenceRing(ImageView presenceView, String userId) {
    }

    @Override
    protected void loadMemberAvatar(ImageView avatarView, RoomMember member, String userId, String displayName, String url) {
        if (!mSession.isAlive()) {
            return;
        }

        // if there is no preferred display name, use the member one
        if (TextUtils.isEmpty(displayName) && (null != member)) {
            displayName = member.displayname;
        }

        if ((member != null) && (null == url)) {
            url = member.avatarUrl;
        }

        if (null != member) {
            VectorUtils.loadUserAvatar(mContext, mSession, avatarView, url, member.getUserId(), displayName);
        } else {
            VectorUtils.loadUserAvatar(mContext, mSession, avatarView, url, userId, displayName);
        }
    }

    /**
     * Found the dedicated icon to display for each event id
     */
    private void manageCryptoEvents() {
        HashMap<String, Integer> e2eIconByEventId = new HashMap<>();
        HashMap<String, MXDeviceInfo> e2eDeviceInfoByEventId = new HashMap<>();

        if (mIsRoomEncrypted &&  mSession.isCryptoEnabled()) {
            // the key is "userid_deviceid"
            HashMap<String, MXDeviceInfo> deviceInfoHashMap = new HashMap<>();
            for (int index = 0; index < this.getCount(); index++) {
                MessageRow row = getItem(index);
                Event event = row.getEvent();

                // oneself event
                if (event.mSentState != Event.SentState.SENT) {
                    e2eIconByEventId.put(event.eventId, R.drawable.e2e_verified);
                }
                // not encrypted event
                else if (!event.isEncrypted()) {
                    e2eIconByEventId.put(event.eventId, R.drawable.e2e_unencrypted);
                }
                // in error cases, do not display
                else if (null != event.getCryptoError()) {
                    e2eIconByEventId.put(event.eventId, R.drawable.e2e_blocked);
                } else {
                    EncryptedEventContent encryptedEventContent = JsonUtils.toEncryptedEventContent(event.getWireContent().getAsJsonObject());

                    if (TextUtils.equals(mSession.getCredentials().deviceId, encryptedEventContent.device_id) &&
                            TextUtils.equals(mSession.getMyUserId(), event.getSender())
                            ) {
                        e2eIconByEventId.put(event.eventId, R.drawable.e2e_verified);
                    } else {
                        MXDeviceInfo deviceInfo = mSession.getCrypto().deviceWithIdentityKey(encryptedEventContent.sender_key, event.getSender(), encryptedEventContent.algorithm);

                        if (null != deviceInfo) {
                            e2eDeviceInfoByEventId.put(event.eventId, deviceInfo);
                            if (deviceInfo.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED) {
                                e2eIconByEventId.put(event.eventId, R.drawable.e2e_verified);
                            } else if (deviceInfo.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED) {
                                e2eIconByEventId.put(event.eventId, R.drawable.e2e_blocked);
                            } else {
                                e2eIconByEventId.put(event.eventId, R.drawable.e2e_warning);
                            }
                        } else {
                            e2eIconByEventId.put(event.eventId, R.drawable.e2e_warning);
                        }
                    }
                }
            }
        }

        mE2eDeviceByEventId = e2eDeviceInfoByEventId;
        mE2eIconByEventId = e2eIconByEventId;
    }

    @Override
    public void notifyDataSetChanged() {
        // the event with invalid timestamp must be pushed at the end of the history
        this.setNotifyOnChange(false);
        ArrayList<MessageRow> undeliverableEvents = null;

        for(int i = 0; i < getCount(); i++) {
            MessageRow row = getItem(i);

            if ((null != row.getEvent()) && !row.getEvent().isValidOriginServerTs()) {
                if (null == undeliverableEvents) {
                    undeliverableEvents = new ArrayList<>();
                }
                undeliverableEvents.add(row);
                removeRow(row);
                i--;
            }
        }

        if (null != undeliverableEvents) {
            this.addAll(undeliverableEvents);
        }

        this.setNotifyOnChange(true);

        // build messages timestamps
        ArrayList<Date> dates = new ArrayList<>();

        Date latestDate = AdapterUtils.zeroTimeDate(new Date());

        for(int index = 0; index < this.getCount(); index++) {
            MessageRow row = getItem(index);
            Event event = row.getEvent();

            if (event.isValidOriginServerTs()) {
                latestDate = AdapterUtils.zeroTimeDate(new Date(event.getOriginServerTs()));
            }

            dates.add(latestDate);
        }

        synchronized (this) {
            mMessagesDateList = dates;
            mReferenceDate = new Date();
        }

        manageCryptoEvents();

        //  do not refresh the room when the application is in background
        // on large rooms, it drains a lot of battery
        if (!VectorApp.isAppInBackground()) {
            super.notifyDataSetChanged();
        }
    }

    /**
     * Converts a difference of days to a string.
     * @param date the date to display
     * @param nbrDays the number of days between the reference days
     * @return the date text
     */
    private String dateDiff(Date date, long nbrDays) {
        if (nbrDays == 0) {
            return mContext.getResources().getString(R.string.today);
        } else if (nbrDays == 1) {
            return mContext.getResources().getString(R.string.yesterday);
        } else if (nbrDays < 7) {
            return (new SimpleDateFormat("EEEE", AdapterUtils.getLocale(mContext))).format(date);
        } else  {
            int flags = DateUtils.FORMAT_SHOW_DATE |
                    DateUtils.FORMAT_SHOW_YEAR |
                    DateUtils.FORMAT_ABBREV_ALL |
                    DateUtils.FORMAT_SHOW_WEEKDAY;

            Formatter f = new Formatter(new StringBuilder(50), AdapterUtils.getLocale(mContext));
            return DateUtils.formatDateRange(mContext, f, date.getTime(), date.getTime(), flags).toString();
        }
    }

    protected String headerMessage(int position) {
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

        return dateDiff(messageDate, (mReferenceDate.getTime() - messageDate.getTime()) / AdapterUtils.MS_IN_DAY);
    }

    @Override
    protected boolean isAvatarDisplayedOnRightSide(Event event) {
        return false;
    }

    @Override
    protected void refreshReceiverLayout(final LinearLayout receiversLayout, final boolean leftAlign, final String eventId, final RoomState roomState) {
        if (null != receiversLayout) {
            // replaced by displayReadReceipts
            receiversLayout.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onTypingUsersUpdate() {
        // the typing users are now displayed in a dedicated area in the activity
    }

    /**
     * Display the read receipts within the dedicated vector layout.
     * Console application displays them on the message side.
     * Vector application displays them in a dedicated line under the message
     * @param avatarsListView the read receipts layout
     * @param eventId the event Id.
     * @param roomState the room state.
     */
    private void displayReadReceipts(final View avatarsListView, final String eventId, final RoomState roomState) {
        if (!mSession.isAlive()) {
            return;
        }

        IMXStore store = mSession.getDataHandler().getStore();

        // sanity check
        if (null == roomState) {
            avatarsListView.setVisibility(View.GONE);
            return;
        }

        // hide the read receipts until there is a way to retrieve them
        // without triggering a request per message
        if (mIsPreviewMode) {
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

        TextView moreText = (TextView)avatarsListView.findViewById(R.id.message_more_than_expected);

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

        for(; index < imageViews.size(); index++) {
            imageViews.get(index).setVisibility(View.INVISIBLE);
        }

        if (receipts.size() > 0) {
            avatarsListView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mMessagesAdapterEventsListener) {
                        mMessagesAdapterEventsListener.onMoreReadReceiptClick(eventId);
                    }
                }
            });
        } else {
            avatarsListView.setOnClickListener(null);
        }
    }

    /**
     * The user taps on the action icon.
     * @param event the selected event.
     * @param textMsg the event text
     * @param anchorView the popup anchor.
     */
    @SuppressLint("NewApi")
    private void onMessageClick(final Event event, final String textMsg, final View anchorView) {
        final PopupMenu popup = (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) ? new PopupMenu(mContext, anchorView, Gravity.END) : new PopupMenu(mContext, anchorView);

        popup.getMenuInflater().inflate(R.menu.vector_room_message_settings, popup.getMenu());

        // force to display the icons
        try {
            Field[] fields = popup.getClass().getDeclaredFields();
            for (Field field : fields) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(popup);
                    Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
                    Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                    setForceIcons.invoke(menuPopupHelper, true);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "onMessageClick : force to display the icons failed " + e.getLocalizedMessage());
        }

        Menu menu = popup.getMenu();

        // hide entries
        for(int i = 0; i < menu.size(); i++) {
            menu.getItem(i).setVisible(false);
        }

        menu.findItem(R.id.ic_action_view_source).setVisible(true);
        menu.findItem(R.id.ic_action_vector_permalink).setVisible(true);

        if (!TextUtils.isEmpty(textMsg)) {
            menu.findItem(R.id.ic_action_vector_copy).setVisible(true);
            menu.findItem(R.id.ic_action_vector_quote).setVisible(true);
        }

        if (event.isUploadingMedias(mMediasCache)) {
            menu.findItem(R.id.ic_action_vector_cancel_upload).setVisible(true);
        }

        if (event.isDownloadingMedias(mMediasCache)) {
            menu.findItem(R.id.ic_action_vector_cancel_download).setVisible(true);
        }

        if (event.canBeResent()) {
            menu.findItem(R.id.ic_action_vector_resend_message).setVisible(true);

            if (event.isUndeliverable()) {
                menu.findItem(R.id.ic_action_vector_redact_message).setVisible(true);
            }
        } else if (event.mSentState == Event.SentState.SENT) {

            // test if the event can be redacted
            boolean canBeRedacted = !mIsPreviewMode;

            if (canBeRedacted) {
                // oneself message -> can redact it
                if (TextUtils.equals(event.sender, mSession.getMyUserId())) {
                    canBeRedacted = true;
                } else {
                    // need the mininum power level to redact an event
                    Room room = mSession.getDataHandler().getRoom(event.roomId);

                    if ((null != room) && (null != room.getLiveState().getPowerLevels())) {
                        PowerLevels powerLevels = room.getLiveState().getPowerLevels();
                        canBeRedacted = powerLevels.getUserPowerLevel(mSession.getMyUserId()) >= powerLevels.redact;
                    }
                }
            }

            menu.findItem(R.id.ic_action_vector_redact_message).setVisible(canBeRedacted);

            if (Event.EVENT_TYPE_MESSAGE.equals(event.getType())) {
                Message message = JsonUtils.toMessage(event.getContentAsJsonObject());

                // share / forward the message
                menu.findItem(R.id.ic_action_vector_share).setVisible(true);
                menu.findItem(R.id.ic_action_vector_forward).setVisible(true);

                // save the media in the downloads directory
                if (Message.MSGTYPE_IMAGE.equals(message.msgtype) || Message.MSGTYPE_VIDEO.equals(message.msgtype) || Message.MSGTYPE_FILE.equals(message.msgtype)) {
                    menu.findItem(R.id.ic_action_vector_save).setVisible(true);
                }

                // offer to report a message content
                menu.findItem(R.id.ic_action_vector_report).setVisible(!mIsPreviewMode && !TextUtils.equals(event.sender, mSession.getMyUserId()));
            }

        }

        // e2e
        menu.findItem(R.id.ic_action_device_verification).setVisible(mE2eIconByEventId.containsKey(event.eventId));

        // display the menu
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                // warn the listener
                if (null != mVectorMessagesAdapterEventsListener) {
                    mVectorMessagesAdapterEventsListener.onEventAction(event, textMsg, item.getItemId());
                }

                // disable the selection
                mHighlightedEventId = null;
                notifyDataSetChanged();

                return true;
            }
        });

        popup.show();
    }

    /**
     * Manage the select mode i.e highlight an item when the user tap on it
     * @param contentView the cell view.
     * @param event the linked event
     */
    private void manageSelectionMode(final View contentView, final Event event) {
        final String eventId = event.eventId;

        boolean isInSelectionMode = (null != mHighlightedEventId);
        boolean isHighlighted = TextUtils.equals(eventId, mHighlightedEventId);

        // display the action icon when selected
        contentView.findViewById(R.id.messagesAdapter_action_image).setVisibility(isHighlighted ? View.VISIBLE : View.GONE);

        float alpha = (!isInSelectionMode || isHighlighted) ? 1.0f : 0.2f;

        // the message body is dimmed when not selected
        contentView.findViewById(R.id.messagesAdapter_body_view).setAlpha(alpha);
        contentView.findViewById(R.id.messagesAdapter_avatars_list).setAlpha(alpha);

        TextView tsTextView = (TextView)contentView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_timestamp);
        if (isInSelectionMode && isHighlighted) {
            tsTextView.setVisibility(View.VISIBLE);
        }

        contentView.findViewById(org.matrix.androidsdk.R.id.message_timestamp_layout_right).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TextUtils.equals(eventId, mHighlightedEventId)) {
                    onMessageClick(event, getEventText(contentView), contentView.findViewById(R.id.messagesAdapter_action_anchor));
                } else {
                    onEventTap(eventId);
                }
            }
        });

        contentView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!mIsSearchMode) {
                    onMessageClick(event, getEventText(contentView), contentView.findViewById(R.id.messagesAdapter_action_anchor));
                    mHighlightedEventId = eventId;
                    notifyDataSetChanged();
                    return true;
                }

                return false;
            }
        });
    }

    @Override
    protected boolean mergeView(Event event, int position, boolean shouldBeMerged) {
        if (shouldBeMerged) {
            shouldBeMerged = null == headerMessage(position);
        }

        return shouldBeMerged && !event.isCallEvent();
    }

    @Override
    protected boolean isMergeableEvent(Event event) {
        return super.isMergeableEvent(event) && !event.isCallEvent();
    }

    /**
     * Return the text displayed in a convertView in the chat history.
     * @param contentView the cell view
     * @return the displayed text.
     */
    private String getEventText(View contentView) {
        String text = null;

        if (null != contentView) {
            TextView bodyTextView = (TextView)contentView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_body);

            if (null != bodyTextView) {
                text = bodyTextView.getText().toString();
            }
        }

        return text;
    }

    @Override
    protected void addContentViewListeners(final View convertView, final View contentView, final int position) {
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mMessagesAdapterEventsListener) {
                    // GA issue
                    if (position < getCount()) {
                        mMessagesAdapterEventsListener.onContentClick(position);
                    }
                }
            }
        });

        contentView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // GA issue
                if (position < getCount()) {
                    MessageRow row = getItem(position);
                    Event event = row.getEvent();

                    if (!mIsSearchMode) {
                        onMessageClick(event, getEventText(contentView), convertView.findViewById(R.id.messagesAdapter_action_anchor));
                        mHighlightedEventId = event.eventId;
                        notifyDataSetChanged();
                        return true;
                    }
                }

                return true;
            }
        });
    }


    @Override
    protected boolean manageSubView(int position, View convertView, View subView, int msgType) {
        MessageRow row = getItem(position);
        Event event = row.getEvent();

        // mother class implementation
        boolean isMergedView = super.manageSubView(position, convertView, subView, msgType);

        // remove the message separator when it is not required
        View view = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_separator);
        if (null != view) {
            View line = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_separator_line);

            if (null != line) {
                line.setBackgroundColor(Color.TRANSPARENT);
            }
        }

        // display the day separator
        View headerLayout = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_header);
        if (null != headerLayout) {
            String header = headerMessage(position);

            if (null != header) {
                TextView headerText = (TextView) convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_message_header_text);
                headerText.setText(header);
                headerLayout.setVisibility(View.VISIBLE);

                View topHeaderMargin = headerLayout.findViewById(R.id.messagesAdapter_message_header_top_margin);
                topHeaderMargin.setVisibility((0 == position) ? View.GONE : View.VISIBLE);
            } else {
                headerLayout.setVisibility(View.GONE);
            }
        }

        // the timestamp is hidden except for the latest message and when there is no search
        View rightTsTextLayout = convertView.findViewById(org.matrix.androidsdk.R.id.message_timestamp_layout_right);

        if (null != rightTsTextLayout) {
            TextView tsTextView = (TextView)rightTsTextLayout.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_timestamp);

            if (null != tsTextView) {
                tsTextView.setVisibility((((position + 1) == this.getCount()) || mIsSearchMode) ? View.VISIBLE : View.GONE);
            }
        }

        // On Vector application, the read receipts are displayed in a dedicated line under the message
        View avatarsListView = convertView.findViewById(R.id.messagesAdapter_avatars_list);

        if (null != avatarsListView) {
            displayReadReceipts(avatarsListView, event.eventId, row.getRoomState());
        }

        // selection mode
        manageSelectionMode(convertView, event);


        // search message mode
        View highlightMakerView = convertView.findViewById(R.id.messagesAdapter_highlight_message_marker);

        if (null != highlightMakerView) {
            if (TextUtils.equals(mSearchedEventId, event.eventId)) {
                View avatarView = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_roundAvatar_left);
                ViewGroup.LayoutParams avatarLayout = avatarView.getLayoutParams();

                // align marker view with the message
                ViewGroup.MarginLayoutParams highlightMakerLayout = (ViewGroup.MarginLayoutParams) highlightMakerView.getLayoutParams();

                if (isMergedView) {
                    highlightMakerLayout.setMargins(avatarLayout.width + 5, highlightMakerLayout.topMargin, 5, highlightMakerLayout.bottomMargin);

                } else {
                    highlightMakerLayout.setMargins(5, highlightMakerLayout.topMargin, 5, highlightMakerLayout.bottomMargin);
                }

                highlightMakerView.setLayoutParams(highlightMakerLayout);

                // move left the body
                View bodyLayoutView = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_body_layout);
                ViewGroup.MarginLayoutParams bodyLayout = (ViewGroup.MarginLayoutParams) bodyLayoutView.getLayoutParams();
                bodyLayout.setMargins(4, bodyLayout.topMargin, 4, bodyLayout.bottomMargin);

                highlightMakerView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.vector_green_color));
            } else {
                highlightMakerView.setBackgroundColor(ContextCompat.getColor(mContext, android.R.color.transparent));
            }
        }

        // download / upload progress layout
        if ((ROW_TYPE_IMAGE == msgType) || (ROW_TYPE_FILE == msgType) || (ROW_TYPE_VIDEO == msgType)) {
            View bodyLayoutView = convertView.findViewById(org.matrix.androidsdk.R.id.messagesAdapter_body_layout);
            ViewGroup.MarginLayoutParams bodyLayoutParams = (ViewGroup.MarginLayoutParams) bodyLayoutView.getLayoutParams();
            int marginLeft = bodyLayoutParams.leftMargin;

            View downloadProgressLayout = convertView.findViewById(org.matrix.androidsdk.R.id.content_download_progress_layout);

            if (null != downloadProgressLayout) {
                ViewGroup.MarginLayoutParams downloadProgressLayoutParams = (ViewGroup.MarginLayoutParams) downloadProgressLayout.getLayoutParams();
                downloadProgressLayoutParams.setMargins(marginLeft, downloadProgressLayoutParams.topMargin, downloadProgressLayoutParams.rightMargin, downloadProgressLayoutParams.bottomMargin);
                downloadProgressLayout.setLayoutParams(downloadProgressLayoutParams);
            }

            View uploadProgressLayout = convertView.findViewById(org.matrix.androidsdk.R.id.content_upload_progress_layout);

            if (null != uploadProgressLayout) {
                ViewGroup.MarginLayoutParams uploadProgressLayoutParams = (ViewGroup.MarginLayoutParams) uploadProgressLayout.getLayoutParams();
                uploadProgressLayoutParams.setMargins(marginLeft, uploadProgressLayoutParams.topMargin, uploadProgressLayoutParams.rightMargin, uploadProgressLayoutParams.bottomMargin);
                uploadProgressLayout.setLayoutParams(uploadProgressLayoutParams);
            }
        }
        return isMergedView;
    }

    public int presenceOnlineColor() {
        return ContextCompat.getColor(mContext, R.color.presence_online);
    }

    public int presenceOfflineColor() {
        return ContextCompat.getColor(mContext, R.color.presence_offline);
    }

    public int presenceUnavailableColor() {
        return ContextCompat.getColor(mContext, R.color.presence_unavailable);
    }

    @Override
    public int getHighlightMessageTextColor(Context context) {
        return ContextCompat.getColor(mContext, R.color.vector_fuchsia_color);
    }

    @Override
    public int getSearchHighlightMessageTextColor(Context context) {
        return ContextCompat.getColor(mContext, R.color.vector_green_color);
    }

    @Override
    public int getNotSentMessageTextColor(Context context) {
        return ContextCompat.getColor(mContext, R.color.vector_not_send_color);
    }

    //==============================================================================================================
    // Download / upload progress management
    //==============================================================================================================

    /**
     * Format a second time range.
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
            return context.getString(R.string.attachment_remaining_time_minutes, seconds / 60, seconds % 60);
        } else {
            return DateUtils.formatElapsedTime(seconds);
        }
    }

    /**
     * Format the download / upload stats.
     * @param context the context.
     * @param progressFileSize the upload / download media size.
     * @param fileSize the expected media size.
     * @param remainingTime the remaining time (seconds)
     * @return the formatted string.
     */
    private static String formatStats(Context context, int progressFileSize, int fileSize, int remainingTime) {
        String formattedString = "";

        if (fileSize > 0) {
            formattedString += android.text.format.Formatter.formatShortFileSize(context,progressFileSize);
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
     * @param context the context.
     * @param stats the download stats
     * @return the formatted string
     */
    private static String formatDownloadStats(Context context, IMXMediaDownloadListener.DownloadStats stats) {
        return formatStats(context, stats.mDownloadedSize, stats.mFileSize, stats.mEstimatedRemainingTime);
    }

    /**
     * Format the upload stats.
     * @param context the context.
     * @param stats the upload stats
     * @return the formatted string
     */
    private static String formatUploadStats(Context context, IMXMediaUploadListener.UploadStats stats) {
        return formatStats(context, stats.mUploadedSize, stats.mFileSize, stats.mEstimatedRemainingTime);
    }

    //
    private final HashMap<String, String> mMediaDownloadIdByEventId = new HashMap<>();

    /**
     * Tells if the downloadId is the media download id.
     * @param event the event
     * @param downloadId the download id.
     * @return true if the media is downloading (not the thumbnail)
     */
    private boolean isMediaDownloading(Event event, String downloadId) {
        String mediaDownloadId = mMediaDownloadIdByEventId.get(event.eventId);

        if (null == mediaDownloadId) {
            mediaDownloadId = "";

            if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                Message message = JsonUtils.toMessage(event.getContent());

                String url = null;

                if (TextUtils.equals(message.msgtype, Message.MSGTYPE_IMAGE)) {
                    url = JsonUtils.toImageMessage(event.getContent()).url;
                } else if (TextUtils.equals(message.msgtype, Message.MSGTYPE_VIDEO)) {
                    url = JsonUtils.toVideoMessage(event.getContent()).url;
                } else if (TextUtils.equals(message.msgtype, Message.MSGTYPE_FILE)) {
                    url = JsonUtils.toFileMessage(event.getContent()).url;
                }

                if (!TextUtils.isEmpty(url)) {
                    mediaDownloadId = mSession.getMediasCache().downloadIdFromUrl(url);
                }
            }

            mMediaDownloadIdByEventId.put(event.eventId, mediaDownloadId);
        }

        return TextUtils.equals(mediaDownloadId, downloadId);
    }

    @Override
    protected void refreshDownloadViews(final Event event, final IMXMediaDownloadListener.DownloadStats downloadStats, final View downloadProgressLayout) {
        if ((null != downloadStats) && isMediaDownloading(event, downloadStats.mDownloadId)) {
            downloadProgressLayout.setVisibility(View.VISIBLE);

            TextView downloadProgressStatsTextView = (TextView) downloadProgressLayout.findViewById(R.id.media_progress_text_view);
            ProgressBar progressBar = (ProgressBar) downloadProgressLayout.findViewById(R.id.media_progress_view);

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

    @Override
    protected void updateUploadProgress(View uploadProgressLayout, int progress) {
        ProgressBar progressBar = (ProgressBar) uploadProgressLayout.findViewById(R.id.media_progress_view);

        if (null != progressBar) {
            progressBar.setProgress(progress);
        }
    }

    @Override
    protected void refreshUploadViews(final Event event, final IMXMediaUploadListener.UploadStats uploadStats, final View uploadProgressLayout) {
        if (null != uploadStats) {
            uploadProgressLayout.setVisibility(View.VISIBLE);

            TextView uploadProgressStatsTextView = (TextView) uploadProgressLayout.findViewById(R.id.media_progress_text_view);
            ProgressBar progressBar = (ProgressBar) uploadProgressLayout.findViewById(R.id.media_progress_view);

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
}
