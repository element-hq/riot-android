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
import android.widget.LinearLayout;
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
import im.vector.activity.VectorHomeActivity;
import im.vector.listeners.IMessagesAdapterActionsListener;
import im.vector.util.MatrixLinkMovementMethod;
import im.vector.util.MatrixURLSpan;
import im.vector.util.VectorUtils;

/**
 * An adapter which can display room information.
 */
public class VectorMessagesAdapter extends AbstractMessagesAdapter {

    private static final String LOG_TAG = "VMessagesAdapter";

    // an event is highlighted when the user taps on it
    private String mHighlightedEventId;

    // events listeners
    protected IMessagesAdapterActionsListener mVectorMessagesAdapterEventsListener = null;

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

    protected static final int ROW_TYPE_TEXT = 0;
    protected static final int ROW_TYPE_IMAGE = 1;
    protected static final int ROW_TYPE_NOTICE = 2;
    protected static final int ROW_TYPE_EMOTE = 3;
    protected static final int ROW_TYPE_FILE = 4;
    protected static final int ROW_TYPE_VIDEO = 5;
    protected static final int NUM_ROW_TYPES = 6;

    protected final Context mContext;
    private final HashMap<Integer, Integer> mRowTypeToLayoutId = new HashMap<>();
    protected final LayoutInflater mLayoutInflater;

    // To keep track of events and avoid duplicates. For instance, we add a message event
    // when the current user sends one but it will also come down the event stream
    private final HashMap<String, MessageRow> mEventRowMap = new HashMap<>();

    // avoid searching bing rule at each refresh
    private HashMap<String, Integer> mTextColorByEventId = new HashMap<>();

    private final HashMap<String, User> mUserByUserId = new HashMap<>();

    private final HashMap<String, Integer> mEventType = new HashMap<>();

    // the message text colors
    private int mDefaultMessageTextColor;
    private int mNotSentMessageTextColor;
    private int mSendingMessageTextColor;
    private int mEncryptingMessageTextColor;
    private int mHighlightMessageTextColor;
    protected int mSearchHighlightMessageTextColor;

    private final int mMaxImageWidth;
    private final int mMaxImageHeight;

    // media cache
    private final MXMediasCache mMediasCache;

    // session
    protected final MXSession mSession;

    private boolean mIsSearchMode = false;
    private boolean mIsPreviewMode = false;
    private boolean mIsUnreadViewMode = false;
    private String mPattern = null;
    private ArrayList<MessageRow> mLiveMessagesRowList = null;

    // id of the read markers event
    private String mReadMarkerEventId;
    private boolean mCanShowReadMarker = true;
    private String mReadReceiptEventId;
    private boolean mIsInBackground;

    private ReadMarkerListener mReadMarkerListener;

    private MatrixLinkMovementMethod mLinkMovementMethod;

    /**
     * Creates a messages adapter with the default layouts.
     */
    public VectorMessagesAdapter(MXSession session, Context context, MXMediasCache mediasCache) {
        this(session, context,
                R.layout.adapter_item_vector_message_text_emote_notice,
                R.layout.adapter_item_vector_message_image_video,
                R.layout.adapter_item_vector_message_text_emote_notice,
                R.layout.adapter_item_vector_message_text_emote_notice,
                R.layout.adapter_item_vector_message_file,
                R.layout.adapter_item_vector_message_image_video,
                mediasCache);
    }

    /**
     * Expanded constructor.
     * each message type has its own layout.
     *
     * @param session           the dedicated layout.
     * @param context           the context
     * @param textResLayoutId   the text message layout.
     * @param imageResLayoutId  the image message layout.
     * @param noticeResLayoutId the notice message layout.
     * @param emoteRestLayoutId the emote message layout
     * @param fileResLayoutId   the file message layout
     * @param videoResLayoutId  the video message layout
     * @param mediasCache       the medias cache.
     */
    public VectorMessagesAdapter(MXSession session, Context context, int textResLayoutId, int imageResLayoutId,
                           int noticeResLayoutId, int emoteRestLayoutId, int fileResLayoutId, int videoResLayoutId, MXMediasCache mediasCache) {
        super(context, 0);
        mContext = context;
        mRowTypeToLayoutId.put(ROW_TYPE_TEXT, textResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_IMAGE, imageResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_NOTICE, noticeResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_EMOTE, emoteRestLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_FILE, fileResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_VIDEO, videoResLayoutId);
        mMediasCache = mediasCache;
        mLayoutInflater = LayoutInflater.from(mContext);
        // the refresh will be triggered only when it is required
        // for example, retrieve the historical messages triggers a refresh for each message
        setNotifyOnChange(false);

        mDefaultMessageTextColor = getDefaultMessageTextColor();
        mNotSentMessageTextColor = getNotSentMessageTextColor();
        mSendingMessageTextColor = getSendingMessageTextColor();
        mEncryptingMessageTextColor = getEncryptingMessageTextColor();
        mHighlightMessageTextColor = getHighlightMessageTextColor();
        mSearchHighlightMessageTextColor = getSearchHighlightMessageTextColor();

        Point size = new Point(0, 0);
        getScreenSize(size);

        int screenWidth = size.x;
        int screenHeight = size.y;

        // landscape / portrait
        if (screenWidth < screenHeight) {
            mMaxImageWidth = Math.round(screenWidth * 0.6f);
            mMaxImageHeight = Math.round(screenHeight * 0.4f);
        } else {
            mMaxImageWidth = Math.round(screenWidth * 0.4f);
            mMaxImageHeight = Math.round(screenHeight * 0.6f);
        }

        mSession = session;
    }

    /*
     * *********************************************************************************************
     * Graphical items
     * *********************************************************************************************
     */

    /**
     * @return the max thumbnail width
     */
    public int getMaxThumbnailWith() {
        return mMaxImageWidth;
    }

    /**
     * @return the max thumbnail height
     */
    public int getMaxThumbnailHeight() {
        return mMaxImageHeight;
    }

    // customization methods
    public int getDefaultMessageTextColor() {
        return ContextCompat.getColor(mContext, R.color.message_normal);
    }

    public int getEncryptingMessageTextColor() {
        return ContextCompat.getColor(mContext, R.color.vector_green_color);
    }

    public int getSendingMessageTextColor() {
        return ContextCompat.getColor(mContext, R.color.message_sending);
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

    public int getHighlightMessageTextColor() {
        return ContextCompat.getColor(mContext, R.color.vector_fuchsia_color);
    }

    public int getSearchHighlightMessageTextColor() {
        return ContextCompat.getColor(mContext, R.color.vector_green_color);
    }

    public int getNotSentMessageTextColor() {
        return ContextCompat.getColor(mContext, R.color.vector_not_send_color);
    }

    /*
     * *********************************************************************************************
     * Items getter / setter
     * *********************************************************************************************
     */

    @Override
    public void addToFront(MessageRow row) {
        if (isSupportedRow(row)) {
            // ensure that notifyDataSetChanged is not called
            // it seems that setNotifyOnChange is reinitialized to true;
            setNotifyOnChange(false);

            if (mIsSearchMode) {
                mLiveMessagesRowList.add(0, row);
            } else {
                insert(row, 0);
            }

            if (row.getEvent().eventId != null) {
                mEventRowMap.put(row.getEvent().eventId, row);
            }
        }
    }

    @Override
    public void remove(MessageRow row) {
        if (mIsSearchMode) {
            mLiveMessagesRowList.remove(row);
        } else {
            super.remove(row);
        }
    }

    @Override
    public void add(MessageRow row) {
        add(row, true);
    }

    @Override
    public void add(MessageRow row, boolean refresh) {
        // ensure that notifyDataSetChanged is not called
        // it seems that setNotifyOnChange is reinitialized to true;
        setNotifyOnChange(false);

        if (isSupportedRow(row)) {
            if (mIsSearchMode) {
                mLiveMessagesRowList.add(row);
            } else {
                super.add(row);
            }
            if (row.getEvent().eventId != null) {
                mEventRowMap.put(row.getEvent().eventId, row);
            }

            if ((!mIsSearchMode) && refresh) {
                this.notifyDataSetChanged();
            } else {
                setNotifyOnChange(true);
            }
        } else {
            setNotifyOnChange(true);
        }
    }

    @Override
    public MessageRow getMessageRow(String eventId) {
        if (null != eventId) {
            return mEventRowMap.get(eventId);
        } else {
            return null;
        }
    }

    @Override
    public MessageRow getClosestRow(Event event) {
        if (event == null) {
            return null;
        } else {
            return getClosestRowFromTs(event.eventId, event.getOriginServerTs());
        }
    }

    @Override
    public MessageRow getClosestRowFromTs(final String eventId, final long eventTs) {
        MessageRow messageRow = getMessageRow(eventId);

        if (messageRow == null) {
            List<MessageRow> rows = new ArrayList<>(mEventRowMap.values());

            // loop because the list is not sorted
            for (MessageRow row : rows) {
                long rowTs = row.getEvent().getOriginServerTs();

                // check if the row event has been received after eventTs (from)
                if (rowTs > eventTs) {
                    // not yet initialised
                    if (messageRow == null) {
                        messageRow = row;
                    }
                    // keep the closest row
                    else if (rowTs < messageRow.getEvent().getOriginServerTs()) {
                        messageRow = row;
                        Log.d(LOG_TAG, "## getClosestRowFromTs() " + row.getEvent().eventId);
                    }
                }
            }
        }

        return messageRow;
    }

    @Override
    public MessageRow getClosestRowBeforeTs(final String eventId, final long eventTs) {
        MessageRow messageRow = getMessageRow(eventId);

        if (messageRow == null) {
            List<MessageRow> rows = new ArrayList<>(mEventRowMap.values());

            // loop because the list is not sorted
            for (MessageRow row : rows) {
                long rowTs = row.getEvent().getOriginServerTs();

                // check if the row event has been received before eventTs (from)
                if (rowTs < eventTs) {
                    // not yet initialised
                    if (messageRow == null) {
                        messageRow = row;
                    }
                    // keep the closest row
                    else if (rowTs > messageRow.getEvent().getOriginServerTs()) {
                        messageRow = row;
                        Log.d(LOG_TAG, "## getClosestRowBeforeTs() " + row.getEvent().eventId);
                    }
                }
            }
        }

        return messageRow;
    }

    @Override
    public void updateEventById(Event event, String oldEventId) {
        MessageRow row = mEventRowMap.get(event.eventId);

        // the event is not yet defined
        if (null == row) {
            MessageRow oldRow = mEventRowMap.get(oldEventId);

            if (null != oldRow) {
                mEventRowMap.remove(oldEventId);
                mEventRowMap.put(event.eventId, oldRow);
            }
        } else {
            // the eventId already exists
            // remove the old display
            removeEventById(oldEventId);
        }

        notifyDataSetChanged();
    }

    @Override
    public void removeEventById(String eventId) {
        // ensure that notifyDataSetChanged is not called
        // it seems that setNotifyOnChange is reinitialized to true;
        setNotifyOnChange(false);

        MessageRow row = mEventRowMap.get(eventId);

        if (row != null) {
            remove(row);
        }
    }

    /*
     * *********************************************************************************************
     * Display modes
     * *********************************************************************************************
     */

    @Override
    public void setIsPreviewMode(boolean isPreviewMode) {
        mIsPreviewMode = isPreviewMode;
    }

    @Override
    public void setIsUnreadViewMode(boolean isUnreadViewMode) {
        mIsUnreadViewMode = isUnreadViewMode;
    }

    @Override
    public boolean isUnreadViewMode() {
        return mIsUnreadViewMode;
    }

    /*
     * *********************************************************************************************
     * Preview mode
     * *********************************************************************************************
     */
    @Override
    public void setSearchPattern(String pattern) {
        if (!TextUtils.equals(pattern, mPattern)) {
            mPattern = pattern;
            mIsSearchMode = !TextUtils.isEmpty(mPattern);

            // in search mode, the live row are cached.
            if (mIsSearchMode) {
                // save once
                if (null == mLiveMessagesRowList) {
                    // backup live events
                    mLiveMessagesRowList = new ArrayList<>();
                    for (int pos = 0; pos < this.getCount(); pos++) {
                        mLiveMessagesRowList.add(this.getItem(pos));
                    }
                }
            } else if (null != mLiveMessagesRowList) {
                // clear and restore the cached live events.
                this.clear();
                this.addAll(mLiveMessagesRowList);
                mLiveMessagesRowList = null;
            }
        }
    }


    /**
     * Find the user from his user ID
     *
     * @param userId the user ID
     * @return the linked User
     */
    private User getUser(String userId) {
        if (mUserByUserId.containsKey(userId)) {
            return mUserByUserId.get(userId);
        }

        IMXStore store = mSession.getDataHandler().getStore();
        User user = store.getUser(userId);

        if (null != user) {
            mUserByUserId.put(userId, user);
        }

        return user;
    }


    /**
     * Return the screen size.
     *
     * @param size the size to set
     */
    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    private void getScreenSize(Point size) {
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            display.getSize(size);
        } else {
            size.set(display.getWidth(), display.getHeight());
        }
    }



    @Override
    public int getViewTypeCount() {
        return NUM_ROW_TYPES;
    }

    @Override
    public void clear() {
        super.clear();
        if (!mIsSearchMode) {
            mEventRowMap.clear();
        }
    }




    /**
     * Check if the row must be added to the list.
     *
     * @param row the row to check.
     * @return true if should be added
     */
    private boolean isSupportedRow(MessageRow row) {
        boolean isSupported = isDisplayableEvent(row.getEvent(), row.getRoomState());

        if (isSupported) {
            String eventId = row.getEvent().eventId;

            MessageRow currentRow = mEventRowMap.get(eventId);

            // the row should be added only if the message has not been received
            isSupported = (null == currentRow);

            // check if the message is already received
            if (null != currentRow) {
                // waiting for echo
                // the message is displayed as sent event if the echo has not been received
                // it avoids displaying a pending message whereas the message has been sent
                if (currentRow.getEvent().getAge() == Event.DUMMY_EVENT_AGE) {
                    currentRow.updateEvent(row.getEvent());
                }
            }
        }

        return isSupported;
    }

    /**
     * Convert Event to view type.
     *
     * @param event the event to convert
     * @return the view type.
     */
    private int getItemViewType(Event event) {
        String eventId = event.eventId;
        String eventType = event.getType();

        // never cache the view type of the encypted messages
        if (Event.EVENT_TYPE_MESSAGE_ENCRYPTED.equals(eventType)) {
            return ROW_TYPE_TEXT;
        }

        // never cache the view type of encrypted events
        if (null != eventId) {
            Integer type = mEventType.get(eventId);

            if (null != type) {
                return type;
            }
        }

        int viewType;

        if (Event.EVENT_TYPE_MESSAGE.equals(eventType)) {

            String msgType = JsonUtils.getMessageMsgType(event.getContent());

            if (Message.MSGTYPE_TEXT.equals(msgType)) {
                viewType = ROW_TYPE_TEXT;
            } else if (Message.MSGTYPE_IMAGE.equals(msgType)) {
                viewType = ROW_TYPE_IMAGE;
            } else if (Message.MSGTYPE_EMOTE.equals(msgType)) {
                viewType = ROW_TYPE_EMOTE;
            } else if (Message.MSGTYPE_NOTICE.equals(msgType)) {
                viewType = ROW_TYPE_NOTICE;
            } else if (Message.MSGTYPE_FILE.equals(msgType) || Message.MSGTYPE_AUDIO.equals(msgType)) {
                viewType = ROW_TYPE_FILE;
            } else if (Message.MSGTYPE_VIDEO.equals(msgType)) {
                viewType = ROW_TYPE_VIDEO;
            } else {
                // Default is to display the body as text
                viewType = ROW_TYPE_TEXT;
            }
        } else if (
                event.isCallEvent() ||
                        Event.EVENT_TYPE_STATE_HISTORY_VISIBILITY.equals(eventType) ||
                        Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(eventType) ||
                        Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType) ||
                        Event.EVENT_TYPE_STATE_ROOM_NAME.equals(eventType) ||
                        Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(eventType) ||
                        Event.EVENT_TYPE_MESSAGE_ENCRYPTION.equals(eventType)) {
            viewType = ROW_TYPE_NOTICE;

        } else {
            throw new RuntimeException("Unknown event type: " + eventType);
        }

        if (null != eventId) {
            mEventType.put(eventId, new Integer(viewType));
        }

        return viewType;
    }

    @Override
    public int getItemViewType(int position) {
        // GA Crash
        if (position >= getCount()) {
            return ROW_TYPE_TEXT;
        }

        MessageRow row = getItem(position);
        return getItemViewType(row.getEvent());
    }

    /*
     * *********************************************************************************************
     * Read markers
     * *********************************************************************************************
     */

    @Override
    public void resetReadMarker() {
        Log.d(LOG_TAG, "resetReadMarker");
        mReadMarkerEventId = null;
    }

    @Override
    public void updateReadMarker(final String readMarkerEventId, final String readReceiptEventId) {
        mReadMarkerEventId = readMarkerEventId;
        mReadReceiptEventId = readReceiptEventId;
        if (readMarkerEventId != null && !readMarkerEventId.equals(mReadMarkerEventId)) {
            Log.d(LOG_TAG, "updateReadMarker read marker id has changed: " + readMarkerEventId);
            mCanShowReadMarker = true;
            notifyDataSetChanged();
        }
    }

    @Override
    public boolean containsMessagesFrom(String userId) {
        // check if the user has been displayed in the room history
        return (null != userId) && mUserByUserId.containsKey(userId);
    }

    /**
     * Animate a read marker view
     */
    private void animateReadMarkerView(final Event event, final View readMarkerView) {
        if (readMarkerView != null && mCanShowReadMarker) {
            mCanShowReadMarker = false;
            if (readMarkerView.getAnimation() == null) {
                final Animation animation = AnimationUtils.loadAnimation(getContext(), R.anim.unread_marker_anim);
                animation.setStartOffset(500);
                animation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        readMarkerView.setVisibility(View.GONE);
                        if (mReadMarkerListener != null) {
                            mReadMarkerListener.onReadMarkerDisplayed(event, readMarkerView);
                        }
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
                readMarkerView.setAnimation(animation);
            }
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (readMarkerView != null && readMarkerView.getAnimation() != null) {
                        readMarkerView.setVisibility(View.VISIBLE);
                        readMarkerView.getAnimation().start();
                    }
                }
            });
        }
    }

    /**
     * Check whether the read marker view should be displayed for the given row
     *
     * @param inflatedView row view
     * @param position     position in adapter
     */
    private void handleReadMarker(final View inflatedView, final int position) {
        final MessageRow row = getItem(position);
        final Event event = row != null ? row.getEvent() : null;
        final View readMarkerView = inflatedView.findViewById(R.id.message_read_marker);
        if (readMarkerView != null) {
            if (event != null && !event.isDummyEvent() && mReadMarkerEventId != null && mCanShowReadMarker
                    && event.eventId.equals(mReadMarkerEventId) && !mIsPreviewMode && !mIsSearchMode
                    && (!mReadMarkerEventId.equals(mReadReceiptEventId) || position < getCount() - 1)) {
                Log.d(LOG_TAG, " Display read marker " + event.eventId + " mReadMarkerEventId" + mReadMarkerEventId);
                // Show the read marker
                animateReadMarkerView(event, readMarkerView);
            } else if (View.GONE != readMarkerView.getVisibility()){
                Log.v(LOG_TAG, "hide read marker");
                readMarkerView.setVisibility(View.GONE);
            }
        }
    }

    /*
     * *********************************************************************************************
     * Others
     * *********************************************************************************************
     */
    /**
     * Notify the fragment that some bing rules could have been updated.
     */
    public void onBingRulesUpdate() {
        synchronized (this) {
            mTextColorByEventId = new HashMap<>();
        }
        this.notifyDataSetChanged();
    }


    /**
     * Returns an user display name for an user Id.
     *
     * @param userId    the user id.
     * @param roomState the room state
     * @return teh user display name.
     */
    protected String getUserDisplayName(String userId, RoomState roomState) {
        if (null != roomState) {
            return roomState.getMemberName(userId);
        } else {
            return userId;
        }
    }

    /**
     * Provides the formatted timestamp to display.
     * null means that the timestamp text must be hidden.
     * @param event the event.
     * @return  the formatted timestamp to display.
     */
    private String getFormattedTimestamp(Event event) {
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

    protected void loadMemberAvatar(ImageView avatarView, RoomMember member, String userId, String displayName, String url) {
        if (!mSession.isAlive()) {
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
            VectorUtils.loadUserAvatar(mContext, mSession, avatarView, url, member.getUserId(), displayName);
        } else {
            VectorUtils.loadUserAvatar(mContext, mSession, avatarView, url, userId, displayName);
        }
    }

    /**
     * Some event should never be merged.
     * e.g. the profile info update (avatar, display name...)
     *
     * @param event the event
     * @return true if the event can be merged.
     */
    private boolean isMergeableEvent(Event event) {
        boolean res = true;

        // user profile update should not be merged
        if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_STATE_ROOM_MEMBER)) {

            EventContent eventContent = JsonUtils.toEventContent(event.getContentAsJsonObject());
            EventContent prevEventContent = event.getPrevContent();
            String prevMembership = null;

            if (null != prevEventContent) {
                prevMembership = prevEventContent.membership;
            }

            res = !TextUtils.equals(prevMembership, eventContent.membership);
        }

        return res  && !event.isCallEvent();
    }

    /**
     * Common view management.
     *
     * @param position    the item position.
     * @param convertView the row view
     * @param subView     the message content view
     * @param msgType     the message type
     * @return true if the view is merged.
     */
    private boolean manageSubView(final int position, View convertView, View subView, int msgType) {
        MessageRow row = getItem(position);
        Event event = row.getEvent();
        RoomState roomState = row.getRoomState();

        convertView.setClickable(false);

        // isMergedView -> the message is going to be merged with the previous one
        // willBeMerged ->tell if a message separator must be displayed
        boolean isMergedView = false;
        boolean willBeMerged = false;

        if (!mIsSearchMode) {
            if ((position > 0) && isMergeableEvent(event)) {
                MessageRow prevRow = getItem(position - 1);
                isMergedView = TextUtils.equals(prevRow.getEvent().getSender(), event.getSender());
            }

            // not the last message
            if ((position + 1) < this.getCount()) {
                MessageRow nextRow = getItem(position + 1);

                if (isMergeableEvent(event) || isMergeableEvent(nextRow.getEvent())) {
                    // the message will be merged if the message senders are not the same
                    // or the message is an avatar / displayname update.
                    willBeMerged = TextUtils.equals(nextRow.getEvent().getSender(), event.getSender()) && isMergeableEvent(nextRow.getEvent());
                }
            }
        }

        // inherited class custom behaviour
        isMergedView = mergeView(event, position, isMergedView);

        // manage sender text
        TextView senderTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_sender);

        if (null != senderTextView) {
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
                }
            }

            final String fSenderId = event.getSender();
            final String fDisplayName = (null == senderTextView.getText()) ? "" : senderTextView.getText().toString();

            senderTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mVectorMessagesAdapterEventsListener) {
                        mVectorMessagesAdapterEventsListener.onSenderNameClick(fSenderId, fDisplayName);
                    }
                }
            });
        }

        View tsTextLayout = convertView.findViewById(R.id.message_timestamp_layout);
        TextView tsTextView = null;

        if (null != tsTextLayout) {
            tsTextView = (TextView) tsTextLayout.findViewById(R.id.messagesAdapter_timestamp);
        }

        if (null != tsTextView) {
            String timeStamp = getFormattedTimestamp(event);

            if (TextUtils.isEmpty(timeStamp)) {
                tsTextView.setVisibility(View.GONE);
            } else {
                tsTextView.setVisibility(View.VISIBLE);
                tsTextView.setText(timeStamp);

                tsTextView.setGravity(Gravity.RIGHT);
            }

            if (row.getEvent().isUndeliverable() || row.getEvent().isUnkownDevice()) {
                tsTextView.setTextColor(mNotSentMessageTextColor);
            } else {
                tsTextView.setTextColor(mContext.getResources().getColor(R.color.chat_gray_text));
            }

            tsTextView.setVisibility((((position + 1) == this.getCount()) || mIsSearchMode) ? View.VISIBLE : View.GONE);
        }

        // Sender avatar
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
                    if (null != mVectorMessagesAdapterEventsListener) {
                        return mVectorMessagesAdapterEventsListener.onAvatarLongClick(userId);
                    } else {
                        return false;
                    }
                }
            });

            // click on the avatar opens the details page
            avatarLayoutView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mVectorMessagesAdapterEventsListener) {
                        mVectorMessagesAdapterEventsListener.onAvatarClick(userId);
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

                loadMemberAvatar(avatarImageView, sender, userId, displayName, url);
            }
        }

        // if the messages are merged
        // the thumbnail is hidden
        // and the subview must be moved to be aligned with the previous body
        View bodyLayoutView = convertView.findViewById(R.id.messagesAdapter_body_layout);
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

        View messageSeparatorView = convertView.findViewById(R.id.messagesAdapter_message_separator);

        if (null != messageSeparatorView) {
            messageSeparatorView.setVisibility((willBeMerged || ((position + 1) == this.getCount())) ? View.GONE : View.VISIBLE);
        }


        convertView.setClickable(true);

        // click on the avatar opens the details page
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mVectorMessagesAdapterEventsListener) {
                    mVectorMessagesAdapterEventsListener.onRowClick(position);
                }
            }
        });

        // click on the avatar opens the details page
        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (null != mVectorMessagesAdapterEventsListener) {
                    return mVectorMessagesAdapterEventsListener.onRowLongClick(position);
                }

                return false;
            }
        });


        // display the day separator
        View headerLayout = convertView.findViewById(R.id.messagesAdapter_message_header);
        if (null != headerLayout) {
            String header = headerMessage(position);

            if (null != header) {
                TextView headerText = (TextView) convertView.findViewById(R.id.messagesAdapter_message_header_text);
                headerText.setText(header);
                headerLayout.setVisibility(View.VISIBLE);

                View topHeaderMargin = headerLayout.findViewById(R.id.messagesAdapter_message_header_top_margin);
                topHeaderMargin.setVisibility((0 == position) ? View.GONE : View.VISIBLE);
            } else {
                headerLayout.setVisibility(View.GONE);
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
        View readMarkerView = convertView.findViewById(R.id.message_read_marker);

        if (null != highlightMakerView) {
            // align marker view with the message
            ViewGroup.MarginLayoutParams highlightMakerLayout = (ViewGroup.MarginLayoutParams) highlightMakerView.getLayoutParams();
            highlightMakerLayout.setMargins(5, highlightMakerLayout.topMargin, 5, highlightMakerLayout.bottomMargin);

            if (TextUtils.equals(mSearchedEventId, event.eventId)) {
                if (mIsUnreadViewMode) {
                    highlightMakerView.setBackgroundColor(ContextCompat.getColor(mContext, android.R.color.transparent));
                    if (readMarkerView != null) {
                        // Show the read marker
                        animateReadMarkerView(event, readMarkerView);
                    }
                } else {
                    if (isMergedView) {
                        highlightMakerLayout.setMargins(avatarLayout.width + 5, highlightMakerLayout.topMargin, 5, highlightMakerLayout.bottomMargin);

                    } else {
                        highlightMakerLayout.setMargins(5, highlightMakerLayout.topMargin, 5, highlightMakerLayout.bottomMargin);
                    }

                    // move left the body
                      bodyLayout.setMargins(4, bodyLayout.topMargin, 4, bodyLayout.bottomMargin);

                    highlightMakerView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.vector_green_color));
                }
            } else {
                highlightMakerView.setBackgroundColor(ContextCompat.getColor(mContext, android.R.color.transparent));
            }

            highlightMakerView.setLayoutParams(highlightMakerLayout);
        }

        // download / upload progress layout
        if ((ROW_TYPE_IMAGE == msgType) || (ROW_TYPE_FILE == msgType) || (ROW_TYPE_VIDEO == msgType)) {
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

        return isMergedView;
    }

    /**
     * Highlight text style
     */
    private CharacterStyle getHighLightTextStyle() {
        return new BackgroundColorSpan(mSearchHighlightMessageTextColor);
    }

    protected void highlightPattern(TextView textView, Spannable text, String pattern) {
        highlightPattern(textView, text, null, pattern);
    }

    /**
     * Highlight the pattern in the text.
     *
     * @param textView the textView in which the text is displayed.
     * @param text     the text to display.
     * @param pattern  the pattern to highlight.
     */
    private void highlightPattern(TextView textView, Spannable text, String htmlFormattedText, String pattern) {
        // sanity check
        if (null == textView) {
            return;
        }

        if (!TextUtils.isEmpty(pattern) && !TextUtils.isEmpty(text) && (text.length() >= pattern.length())) {

            String lowerText = text.toString().toLowerCase();
            String lowerPattern = pattern.toLowerCase();

            int start = 0;
            int pos = lowerText.indexOf(lowerPattern, start);

            while (pos >= 0) {
                start = pos + lowerPattern.length();
                text.setSpan(getHighLightTextStyle(), pos, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), pos, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                pos = lowerText.indexOf(lowerPattern, start);
            }
        }

        final ConsoleHtmlTagHandler htmlTagHandler = new ConsoleHtmlTagHandler();
        htmlTagHandler.mContext = mContext;

        CharSequence sequence;

        // an html format has been released
        if (null != htmlFormattedText) {
            boolean isCustomizable = !htmlFormattedText.contains("<a href=") && !htmlFormattedText.contains("<table>");

            // the links are not yet supported by ConsoleHtmlTagHandler
            // the markdown tables are not properly supported
            sequence = Html.fromHtml(htmlFormattedText.replace("\n", "<br/>"), null, isCustomizable ? htmlTagHandler : null);

            // sanity check
            if (!TextUtils.isEmpty(sequence)) {
                // remove trailing \n to avoid having empty lines..
                int markStart = 0;
                int markEnd = sequence.length() - 1;

                // search first non \n character
                for (; (markStart < sequence.length() - 1) && ('\n' == sequence.charAt(markStart)); markStart++)
                    ;

                // search latest non \n character
                for (; (markEnd >= 0) && ('\n' == sequence.charAt(markEnd)); markEnd--) ;

                // empty string ?
                if (markEnd < markStart) {
                    sequence = sequence.subSequence(0, 0);
                } else {
                    sequence = sequence.subSequence(markStart, markEnd + 1);
                }
            }
        } else {
            sequence = text;
        }

        SpannableStringBuilder strBuilder = new SpannableStringBuilder(sequence);
        URLSpan[] urls = strBuilder.getSpans(0, text.length(), URLSpan.class);

        if ((null != urls) && (urls.length > 0)) {
            for (URLSpan span : urls) {
                makeLinkClickable(strBuilder, span);
            }
        }

        MatrixURLSpan.refreshMatrixSpans(strBuilder, mVectorMessagesAdapterEventsListener);
        textView.setText(strBuilder);

        if (null != mLinkMovementMethod) {
            textView.setMovementMethod(mLinkMovementMethod);
        }
    }

    /**
     * Trap the clicked URL.
     *
     * @param strBuilder the input string
     * @param span       the URL
     */
    private void makeLinkClickable(SpannableStringBuilder strBuilder, final URLSpan span) {
        int start = strBuilder.getSpanStart(span);
        int end = strBuilder.getSpanEnd(span);

        if (start >= 0 && end >= 0) {
            int flags = strBuilder.getSpanFlags(span);
            ClickableSpan clickable = new ClickableSpan() {
                public void onClick(View view) {
                    if (null != mVectorMessagesAdapterEventsListener) {
                        mVectorMessagesAdapterEventsListener.onURLClick(Uri.parse(span.getURL()));
                    }
                }
            };
            strBuilder.setSpan(clickable, start, end, flags);
            strBuilder.removeSpan(span);
        }
    }

    /**
     * Text message management
     *
     * @param position    the message position
     * @param convertView the text message view
     * @param parent      the parent view
     * @return the updated text view.
     */
    private View getTextView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_TEXT), parent, false);
        }

        MessageRow row = getItem(position);
        Event event = row.getEvent();
        Message message = JsonUtils.toMessage(event.getContent());
        RoomState roomState = row.getRoomState();

        EventDisplay display = new EventDisplay(mContext, event, roomState);
        CharSequence textualDisplay = display.getTextualDisplay();

        SpannableString body = new SpannableString((null == textualDisplay) ? "" : textualDisplay);
        final TextView bodyTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_body);

        // cannot refresh it
        if (null == bodyTextView) {
            Log.e(LOG_TAG, "getTextView : invalid layout");
            return convertView;
        }

        if ((null != mVectorMessagesAdapterEventsListener) && mVectorMessagesAdapterEventsListener.shouldHighlightEvent(event)) {
            body.setSpan(new ForegroundColorSpan(mHighlightMessageTextColor), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        highlightPattern(bodyTextView, body, TextUtils.equals(Message.FORMAT_MATRIX_HTML, message.format) ? getSanitisedHtml(message.formatted_body) : null, mPattern);

        int textColor;

        if (row.getEvent().isEncrypting()) {
            textColor = mEncryptingMessageTextColor;
        } else if (row.getEvent().isSending()) {
            textColor = mSendingMessageTextColor;
        } else if (row.getEvent().isUndeliverable() || row.getEvent().isUnkownDevice()) {
            textColor = mNotSentMessageTextColor;
        } else {
            textColor = mDefaultMessageTextColor;

            // sanity check
            if (null != event.eventId) {
                synchronized (this) {
                    if (!mTextColorByEventId.containsKey(event.eventId)) {
                        String sBody = body.toString();
                        String displayName = mSession.getMyUser().displayname;
                        String userID = mSession.getMyUserId();

                        if (EventUtils.caseInsensitiveFind(displayName, sBody) || EventUtils.caseInsensitiveFind(userID, sBody)) {
                            textColor = mHighlightMessageTextColor;
                        } else {
                            textColor = mDefaultMessageTextColor;
                        }

                        mTextColorByEventId.put(event.eventId, textColor);
                    } else {
                        textColor = mTextColorByEventId.get(event.eventId);
                    }
                }
            }
        }

        bodyTextView.setTextColor(textColor);

        View textLayout = convertView.findViewById(R.id.messagesAdapter_text_layout);
        this.manageSubView(position, convertView, textLayout, ROW_TYPE_TEXT);

        addContentViewListeners(convertView, bodyTextView, position);

        return convertView;
    }

    /**
     * Show the upload failure items
     *
     * @param convertView the cell view
     * @param event       the event
     * @param type        the media type
     * @param show        true to show the failure items
     */
    private void showUploadFailure(View convertView, Event event, int type, boolean show) {
        if (ROW_TYPE_FILE == type) {
            TextView fileTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_filename);

            if (null != fileTextView) {
                fileTextView.setTextColor(show ? mNotSentMessageTextColor : mDefaultMessageTextColor);
            }
        } else if ((ROW_TYPE_IMAGE == type) || (ROW_TYPE_VIDEO == type)) {
            View failedLayout = convertView.findViewById(R.id.media_upload_failed);

            if (null != failedLayout) {
                failedLayout.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        }
    }

    /**
     * Check if there is a linked upload.
     *
     * @param convertView the media view
     * @param event       teh related event
     * @param type        the media type
     * @param mediaUrl    the media url
     */
    private void managePendingUpload(final View convertView, final Event event, final int type, final String mediaUrl) {
        final View uploadProgressLayout = convertView.findViewById(R.id.content_upload_progress_layout);
        final ProgressBar uploadSpinner = (ProgressBar) convertView.findViewById(R.id.upload_event_spinner);

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
            showUploadFailure(convertView, event, type, event.isUndeliverable());
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
                        Toast.makeText(VectorMessagesAdapter.this.getContext(),
                                message,
                                Toast.LENGTH_LONG).show();
                    }

                    showUploadFailure(convertView, event, type, true);
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

        showUploadFailure(convertView, event, type, false);
        uploadSpinner.setVisibility((null == uploadStats) ? View.VISIBLE : View.GONE);
        refreshUploadViews(event, uploadStats, uploadProgressLayout);
    }

    /**
     * Manage the image/video download.
     *
     * @param convertView the parent view.
     * @param event       the event
     * @param message     the image / video message
     * @param position    the message position
     */
    private void managePendingImageVideoDownload(final View convertView, final Event event, final Message message, final int position) {
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

        ImageView imageView = (ImageView) convertView.findViewById(R.id.messagesAdapter_image);

        // reset the bitmap
        imageView.setImageBitmap(null);

        RelativeLayout informationLayout = (RelativeLayout) convertView.findViewById(R.id.messagesAdapter_image_layout);
        final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) informationLayout.getLayoutParams();

        // the thumbnails are always pre - rotated
        String downloadId = mMediasCache.loadBitmap(mSession.getHomeserverConfig(), imageView, thumbUrl, maxImageWidth, maxImageHeight, rotationAngle, ExifInterface.ORIENTATION_UNDEFINED, "image/jpeg", encryptedFileInfo);

        // test if the media is downloading the thumbnail is not downloading
        if (null == downloadId) {
            if (message instanceof VideoMessage) {
                downloadId = mMediasCache.downloadIdFromUrl(((VideoMessage) message).getUrl());
            } else {
                downloadId = mMediasCache.downloadIdFromUrl(((ImageMessage) message).getUrl());
            }

            // check the progress value
            // display the progress layout only if the video is downloading
            if (mMediasCache.getProgressValueForDownloadId(downloadId) < 0) {
                downloadId = null;
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
                            Toast.makeText(VectorMessagesAdapter.this.getContext(), error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        } else if (null != jsonElement) {
                            Toast.makeText(VectorMessagesAdapter.this.getContext(), jsonElement.toString(), Toast.LENGTH_LONG).show();
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
     * Image / Video  message management
     *
     * @param type        ROW_TYPE_IMAGE or ROW_TYPE_VIDEO
     * @param position    the message position
     * @param convertView the message view
     * @param parent      the parent view
     * @return the updated text view.
     */
    private View getImageVideoView(int type, final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(type), parent, false);
        }

        MessageRow row = getItem(position);
        Event event = row.getEvent();

        Message message;
        int waterMarkResourceId = -1;

        if (type == ROW_TYPE_IMAGE) {
            ImageMessage imageMessage = JsonUtils.toImageMessage(event.getContent());

            if ("image/gif".equals(imageMessage.getMimeType())) {
                waterMarkResourceId = R.drawable.filetype_gif;
            }
            message = imageMessage;

        } else {
            message = JsonUtils.toVideoMessage(event.getContent());
            waterMarkResourceId = R.drawable.filetype_video;
        }

        // display a type watermark
        final ImageView imageTypeView = (ImageView) convertView.findViewById(R.id.messagesAdapter_image_type);

        if (null == imageTypeView) {
            Log.e(LOG_TAG, "getImageVideoView : invalid layout");
            return convertView;
        }

        imageTypeView.setBackgroundColor(Color.TRANSPARENT);

        if (waterMarkResourceId > 0) {
            imageTypeView.setImageBitmap(BitmapFactory.decodeResource(getContext().getResources(), waterMarkResourceId));
            imageTypeView.setVisibility(View.VISIBLE);
        } else {
            imageTypeView.setVisibility(View.GONE);
        }

        // download management
        managePendingImageVideoDownload(convertView, event, message, position);

        // upload management
        managePendingImageVideoUpload(convertView, event, message);

        // dimmed when the message is not sent
        View imageLayout = convertView.findViewById(R.id.messagesAdapter_image_layout);
        imageLayout.setAlpha(event.isSent() ? 1.0f : 0.5f);

        this.manageSubView(position, convertView, imageLayout, type);

        ImageView imageView = (ImageView) convertView.findViewById(R.id.messagesAdapter_image);
        addContentViewListeners(convertView, imageView, position);

        return convertView;
    }

    static Integer mDimmedNoticeTextColor = null;

    /**
     * Notice message management
     *
     * @param position    the message position
     * @param convertView the message view
     * @param parent      the parent view
     * @return the updated text view.
     */
    private View getNoticeView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_NOTICE), parent, false);
        }

        MessageRow row = getItem(position);
        Event msg = row.getEvent();
        RoomState roomState = row.getRoomState();

        CharSequence notice;

        EventDisplay display = new EventDisplay(mContext, msg, roomState);
        notice = display.getTextualDisplay();

        TextView noticeTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_body);

        if (null == noticeTextView) {
            Log.e(LOG_TAG, "getNoticeView : invalid layout");
            return convertView;
        }

        if (TextUtils.isEmpty(notice)) {
            noticeTextView.setText("");
        } else {
            SpannableStringBuilder strBuilder = new SpannableStringBuilder(notice);
            MatrixURLSpan.refreshMatrixSpans(strBuilder, mVectorMessagesAdapterEventsListener);
            noticeTextView.setText(strBuilder);
        }

        View textLayout = convertView.findViewById(R.id.messagesAdapter_text_layout);
        this.manageSubView(position, convertView, textLayout, ROW_TYPE_NOTICE);

        addContentViewListeners(convertView, noticeTextView, position);

        // compute the notice mDimmedNoticeTextColor colors
        if (null == mDimmedNoticeTextColor) {
            int defaultNoticeColor = noticeTextView.getCurrentTextColor();
            mDimmedNoticeTextColor = Color.argb(
                    Color.alpha(defaultNoticeColor) * 6 / 10,
                    Color.red(defaultNoticeColor),
                    Color.green(defaultNoticeColor),
                    Color.blue(defaultNoticeColor));
        }

        // android seems having a big issue when the text is too long and an alpha !=1 is applied:
        // ---> the text is not displayed.
        // It is sometimes partially displayed and/or flickers while scrolling.
        // Apply an alpha != 1, trigger the same issue.
        // It is related to the number of characters not to the number of lines.
        // I don't understand why the render graph fails to do it.
        // the patch apply the alpha to the text color but it does not work for the hyperlinks.
        noticeTextView.setAlpha(1.0f);
        noticeTextView.setTextColor(mDimmedNoticeTextColor);

        return convertView;
    }

    /**
     * Emote message management
     *
     * @param position    the message position
     * @param convertView the message view
     * @param parent      the parent view
     * @return the updated text view.
     */
    private View getEmoteView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_EMOTE), parent, false);
        }

        MessageRow row = getItem(position);
        Event event = row.getEvent();
        RoomState roomState = row.getRoomState();

        TextView emoteTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_body);

        if (null == emoteTextView) {
            Log.e(LOG_TAG, "getEmoteView : invalid layout");
            return convertView;
        }

        Message message = JsonUtils.toMessage(event.getContent());
        String userDisplayName = (null == roomState) ? event.getSender() : roomState.getMemberName(event.getSender());

        String body = "* " + userDisplayName + " " + message.body;

        String htmlString = null;

        if (TextUtils.equals(Message.FORMAT_MATRIX_HTML, message.format)) {
            htmlString = getSanitisedHtml(message.formatted_body);

            if (null != htmlString) {
                htmlString = "* " + userDisplayName + " " + message.formatted_body;
            }
        }

        highlightPattern(emoteTextView, new SpannableString(body), htmlString, null);

        int textColor;

        if (row.getEvent().isEncrypting()) {
            textColor = mEncryptingMessageTextColor;
        } else if (row.getEvent().isSending()) {
            textColor = mSendingMessageTextColor;
        } else if (row.getEvent().isUndeliverable() || row.getEvent().isUnkownDevice()) {
            textColor = mNotSentMessageTextColor;
        } else {
            textColor = mDefaultMessageTextColor;
        }

        emoteTextView.setTextColor(textColor);

        View textLayout = convertView.findViewById(R.id.messagesAdapter_text_layout);
        this.manageSubView(position, convertView, textLayout, ROW_TYPE_EMOTE);

        addContentViewListeners(convertView, emoteTextView, position);

        return convertView;
    }

    /**
     * Manage the file download items.
     *
     * @param convertView the message cell view.
     * @param event       the event
     * @param fileMessage the file message.
     * @param position    the position in the listview.
     */
    private void managePendingFileDownload(View convertView, final Event event, FileMessage fileMessage, final int position) {
        String downloadId = mMediasCache.downloadIdFromUrl(fileMessage.getUrl());

        // check the progress value
        // display the progress layout only if the file is downloading
        if (mMediasCache.getProgressValueForDownloadId(downloadId) < 0) {
            downloadId = null;
        }

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
                            Toast.makeText(VectorMessagesAdapter.this.getContext(), error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        } else if (null != jsonElement) {
                            Toast.makeText(VectorMessagesAdapter.this.getContext(), jsonElement.toString(), Toast.LENGTH_LONG).show();
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
     * File message management
     *
     * @param position    the message position
     * @param convertView the message view
     * @param parent      the parent view
     * @return the updated text view.
     */
    private View getFileView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_FILE), parent, false);
        }

        MessageRow row = getItem(position);
        Event event = row.getEvent();

        final FileMessage fileMessage = JsonUtils.toFileMessage(event.getContent());
        final TextView fileTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_filename);

        if (null == fileTextView) {
            Log.e(LOG_TAG, "getFileView : invalid layout");
            return convertView;
        }

        fileTextView.setPaintFlags(fileTextView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        fileTextView.setText("\n" + fileMessage.body + "\n");

        // display the right message type icon.
        // Audio and File messages are managed by the same method
        final ImageView imageTypeView = (ImageView) convertView.findViewById(R.id.messagesAdapter_image_type);

        if (null != imageTypeView) {
            imageTypeView.setImageResource(Message.MSGTYPE_AUDIO.equals(fileMessage.msgtype) ? R.drawable.filetype_audio : R.drawable.filetype_attachment);
        }
        imageTypeView.setBackgroundColor(Color.TRANSPARENT);

        managePendingFileDownload(convertView, event, fileMessage, position);
        managePendingUpload(convertView, event, ROW_TYPE_FILE, fileMessage.url);

        View fileLayout = convertView.findViewById(R.id.messagesAdapter_file_layout);
        this.manageSubView(position, convertView, fileLayout, ROW_TYPE_FILE);

        addContentViewListeners(convertView, fileTextView, position);

        return convertView;
    }

    /**
     * Manage the video upload
     *
     * @param convertView the base view
     * @param event       the image or video event
     * @param message     the image or video message
     */
    private void managePendingImageVideoUpload(final View convertView, final Event event, Message message) {
        final View uploadProgressLayout = convertView.findViewById(R.id.content_upload_progress_layout);
        final ProgressBar uploadSpinner = (ProgressBar) convertView.findViewById(R.id.upload_event_spinner);

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
            showUploadFailure(convertView, event, isVideoMessage ? ROW_TYPE_VIDEO : ROW_TYPE_IMAGE, event.isUndeliverable());
            return;
        }

        String uploadingUrl;
        final boolean isUploadingThumbnail;

        if (isVideoMessage) {
            uploadingUrl = ((VideoMessage) message).info.thumbnail_url;
            isUploadingThumbnail = ((VideoMessage) message).isThumbnailLocalContent();
        } else {
            uploadingUrl = ((ImageMessage) message).thumbnailUrl;
            isUploadingThumbnail = ((ImageMessage) message).isThumbnailLocalContent();
        }

        int progress;

        if (isUploadingThumbnail) {
            progress = mSession.getMediasCache().getProgressValueForUploadId(uploadingUrl);
        } else {
            if (isVideoMessage) {
                uploadingUrl = ((VideoMessage) message).url;
            } else {
                uploadingUrl = ((ImageMessage) message).url;
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
                        Toast.makeText(VectorMessagesAdapter.this.getContext(),
                                message,
                                Toast.LENGTH_LONG).show();
                    }

                    showUploadFailure(convertView, event, isVideoMessage ? ROW_TYPE_VIDEO : ROW_TYPE_IMAGE, true);
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

        showUploadFailure(convertView, event, isVideoMessage ? ROW_TYPE_VIDEO : ROW_TYPE_IMAGE, false);
        uploadSpinner.setVisibility(((progress < 0) && event.isSending()) ? View.VISIBLE : View.GONE);
        refreshUploadViews(event, mSession.getMediasCache().getStatsForUploadId(uploadingUrl), uploadProgressLayout);

        if (!isUploadingThumbnail) {
            progress = 10 + (progress * 90 / 100);
        } else {
            progress = (progress * 10 / 100);
        }
        updateUploadProgress(uploadProgressLayout, progress);
        uploadProgressLayout.setVisibility(((progress >= 0) && event.isSending()) ? View.VISIBLE : View.GONE);
    }

    /**
     * Check if an event should be added to the events list.
     *
     * @param event     the event to check.
     * @param roomState the rooms state
     * @return true if the event is managed.
     */
    private boolean isDisplayableEvent(Event event, RoomState roomState) {
        String eventType = event.getType();

        if (Event.EVENT_TYPE_MESSAGE.equals(eventType)) {
            // A message is displayable as long as it has a body
            Message message = JsonUtils.toMessage(event.getContent());
            return (message.body != null) && (!message.body.equals(""));
        } else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(eventType)
                || Event.EVENT_TYPE_STATE_ROOM_NAME.equals(eventType)) {
            EventDisplay display = new EventDisplay(mContext, event, roomState);
            return display.getTextualDisplay() != null;
        } else if (event.isCallEvent()) {
            return Event.EVENT_TYPE_CALL_INVITE.equals(eventType) ||
                    Event.EVENT_TYPE_CALL_ANSWER.equals(eventType) ||
                    Event.EVENT_TYPE_CALL_HANGUP.equals(eventType)
                    ;
        } else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType) || Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(eventType)) {
            // if we can display text for it, it's valid.
            EventDisplay display = new EventDisplay(mContext, event, roomState);
            return display.getTextualDisplay() != null;
        } else if (Event.EVENT_TYPE_STATE_HISTORY_VISIBILITY.equals(eventType)) {
            return true;
        } else if (Event.EVENT_TYPE_MESSAGE_ENCRYPTED.equals(eventType) || Event.EVENT_TYPE_MESSAGE_ENCRYPTION.equals(eventType)) {
            // if we can display text for it, it's valid.
            EventDisplay display = new EventDisplay(mContext, event, roomState);
            return event.hasContentFields() && (display.getTextualDisplay() != null);
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
    private String getSanitisedHtml(final String html) {
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

    private static final List<String> mAllowedHTMLTags = Arrays.asList(
            "font", // custom to matrix for IRC-style font coloring
            "del", // for markdown
            // deliberately no h1/h2 to stop people shouting.
            "h3", "h4", "h5", "h6", "blockquote", "p", "a", "ul", "ol",
            "nl", "li", "b", "i", "u", "strong", "em", "strike", "code", "hr", "br", "div",
            "table", "thead", "caption", "tbody", "tr", "th", "td", "pre");

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

        ArrayList<String> tagsToRemove = new ArrayList<>();

        while (matcher.find()) {

            try {
                String tag = htmlString.substring(matcher.start(1), matcher.end(1));

                // test if the tag is not allowed
                if (mAllowedHTMLTags.indexOf(tag) < 0) {
                    // add it once
                    if (tagsToRemove.indexOf(tag) < 0) {
                        tagsToRemove.add(tag);
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "sanitiseHTML failed " + e.getLocalizedMessage());
            }
        }

        // some tags to remove ?
        if (tagsToRemove.size() > 0) {
            // append the tags to remove
            String tagsToRemoveString = tagsToRemove.get(0);

            for (int i = 1; i < tagsToRemove.size(); i++) {
                tagsToRemoveString += "|" + tagsToRemove.get(i);
            }

            html = html.replaceAll("<\\/?(" + tagsToRemoveString + ")[^>]*>", "");
        }

        return html;
    }

    /**
     * Format a second time range.
     *
     * @param seconds the seconds time
     * @return the formatted string
     */
    private static String remainingTimeToString(int seconds) {
        if (seconds <= 1) {
            return "< 1s";
        } else if (seconds < 60) {
            return seconds + "s";
        } else {
            return DateUtils.formatElapsedTime(seconds);
        }
    }

    //==============================================================================================================
    // Download / upload progress management
    //==============================================================================================================

    /**
     * Specify a listener for read marker
     *
     * @param listener
     */
    public void setReadMarkerListener(final ReadMarkerListener listener) {
        mReadMarkerListener = listener;
    }

    /*
     * *********************************************************************************************
     * Inner classes
     * *********************************************************************************************
     */

    public interface ReadMarkerListener {
        void onReadMarkerDisplayed(Event event, View view);
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
    public void setVectorMessagesAdapterActionsListener(IMessagesAdapterActionsListener listener) {
        mVectorMessagesAdapterEventsListener = listener;

        if (null != mLinkMovementMethod) {
            mLinkMovementMethod.updateListener(listener);
        } else if (null != listener) {
            mLinkMovementMethod = new MatrixLinkMovementMethod(listener);
        }
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // GA Crash : it seems that some invalid indexes are required
        if (position >= getCount()) {
            Log.e(LOG_TAG, "## getView() : invalid index " + position + " >= " + getCount());

            // create dummy one is required
            if (null == convertView) {
                convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_TEXT), parent, false);
            }

            if (null != mVectorMessagesAdapterEventsListener) {
                mVectorMessagesAdapterEventsListener.onInvalidIndexes();
            }

            return convertView;
        }

        final View inflatedView;
        switch (getItemViewType(position)) {
            case ROW_TYPE_TEXT:
                inflatedView = getTextView(position, convertView, parent);
                break;
            case ROW_TYPE_IMAGE:
            case ROW_TYPE_VIDEO:
                inflatedView = getImageVideoView(getItemViewType(position), position, convertView, parent);
                break;
            case ROW_TYPE_NOTICE:
                inflatedView = getNoticeView(position, convertView, parent);
                break;
            case ROW_TYPE_EMOTE:
                inflatedView = getEmoteView(position, convertView, parent);
                break;
            case ROW_TYPE_FILE:
                inflatedView = getFileView(position, convertView, parent);
                break;
            default:
                throw new RuntimeException("Unknown item view type for position " + position);
        }

        if (mReadMarkerListener != null) {
            handleReadMarker(inflatedView, position);
        }

        if (null != inflatedView) {
            inflatedView.setBackgroundColor(Color.TRANSPARENT);
        }

        ImageView e2eIconView = (ImageView)inflatedView.findViewById(R.id.message_adapter_e2e_icon);
        View senderMargin = inflatedView.findViewById(R.id.e2e_sender_margin);
        View senderNameView = inflatedView.findViewById(R.id.messagesAdapter_sender);


        MessageRow row = getItem(position);
        final Event event = row.getEvent();

        if (mE2eIconByEventId.containsKey(event.eventId)) {
            senderMargin.setVisibility(senderNameView.getVisibility());
            e2eIconView.setVisibility(View.VISIBLE);
            e2eIconView.setImageResource(mE2eIconByEventId.get(event.eventId));

            int type = getItemViewType(position);

            if ((type == ROW_TYPE_IMAGE) || (type == ROW_TYPE_VIDEO)) {
                View bodyLayoutView = inflatedView.findViewById(R.id.messagesAdapter_body_layout);
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

        return inflatedView;
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


    /**
     * Found the dedicated icon to display for each event id
     */
    private void manageCryptoEvents() {
        HashMap<String, Integer> e2eIconByEventId = new HashMap<>();
        HashMap<String, MXDeviceInfo> e2eDeviceInfoByEventId = new HashMap<>();

        if (mIsRoomEncrypted &&  mSession.isCryptoEnabled()) {
            // the key is "userid_deviceid"
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
                        MXDeviceInfo deviceInfo = mSession.getCrypto().deviceWithIdentityKey(encryptedEventContent.sender_key, event.getSender(), encryptedEventContent.algorithm);

                        if (null != deviceInfo) {
                            e2eDeviceInfoByEventId.put(event.eventId, deviceInfo);
                        }

                    } else {
                        MXDeviceInfo deviceInfo = mSession.getCrypto().deviceWithIdentityKey(encryptedEventContent.sender_key, event.getSender(), encryptedEventContent.algorithm);

                        if (null != deviceInfo) {
                            e2eDeviceInfoByEventId.put(event.eventId, deviceInfo);
                            if (deviceInfo.isVerified()) {
                                e2eIconByEventId.put(event.eventId, R.drawable.e2e_verified);
                            } else if (deviceInfo.isBlocked()) {
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
        List<MessageRow> undeliverableEvents = new ArrayList<>();

        for(int i = 0; i < getCount(); i++) {
            MessageRow row = getItem(i);
            Event event = row.getEvent();

            if ((null != event) && (!event.isValidOriginServerTs() || event.isUnkownDevice())) {
                undeliverableEvents.add(row);
                remove(row);
                i--;
            }
        }

        if (undeliverableEvents.size() > 0) {
            try {
                Collections.sort(undeliverableEvents, new Comparator<MessageRow>() {
                    @Override
                    public int compare(MessageRow m1, MessageRow m2) {
                        long diff = m1.getEvent().getOriginServerTs() - m2.getEvent().getOriginServerTs();
                        return (diff > 0) ? +1 : ((diff < 0) ? -1 : 0);
                    }
                });
            } catch (Exception e) {
                Log.e(LOG_TAG, "## notifyDataSetChanged () : failed to sort undeliverableEvents " + e.getMessage());
            }

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

    /**
     * Compute the message header for the item at position.
     * It might be null.
     *
     * @param position the event position
     * @return the header
     */
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
                    if (null != mVectorMessagesAdapterEventsListener) {
                        mVectorMessagesAdapterEventsListener.onMoreReadReceiptClick(eventId);
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
        menu.findItem(R.id.ic_action_view_decrypted_source).setVisible(event.isEncrypted() && (null != event.getClearEvent()));
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

            if (event.isUndeliverable() || event.isUnkownDevice()) {
                menu.findItem(R.id.ic_action_vector_redact_message).setVisible(true);
            }
        } else if (event.mSentState == Event.SentState.SENT) {

            // test if the event can be redacted
            boolean canBeRedacted = !mIsPreviewMode && !TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTION);

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

        // fix an issue reported by GA
        try {
            popup.show();
        } catch (Exception e) {
            Log.e(LOG_TAG, " popup.show failed " + e.getMessage());
        }
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

        TextView tsTextView = (TextView)contentView.findViewById(R.id.messagesAdapter_timestamp);
        if (isInSelectionMode && isHighlighted) {
            tsTextView.setVisibility(View.VISIBLE);
        }

        contentView.findViewById(R.id.message_timestamp_layout).setOnClickListener(new View.OnClickListener() {
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

    protected boolean mergeView(Event event, int position, boolean shouldBeMerged) {
        if (shouldBeMerged) {
            shouldBeMerged = null == headerMessage(position);
        }

        return shouldBeMerged && !event.isCallEvent();
    }

    /**
     * Return the text displayed in a convertView in the chat history.
     * @param contentView the cell view
     * @return the displayed text.
     */
    private String getEventText(View contentView) {
        String text = null;

        if (null != contentView) {
            TextView bodyTextView = (TextView)contentView.findViewById(R.id.messagesAdapter_body);

            if (null != bodyTextView) {
                text = bodyTextView.getText().toString();
            }
        }

        return text;
    }

    private void addContentViewListeners(final View convertView, final View contentView, final int position) {
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mVectorMessagesAdapterEventsListener) {
                    // GA issue
                    if (position < getCount()) {
                        mVectorMessagesAdapterEventsListener.onContentClick(position);
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
            return context.getString(R.string.attachment_remaining_time_minutes, (seconds / 60), (seconds % 60));
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
                    url = JsonUtils.toImageMessage(event.getContent()).getUrl();
                } else if (TextUtils.equals(message.msgtype, Message.MSGTYPE_VIDEO)) {
                    url = JsonUtils.toVideoMessage(event.getContent()).getUrl();
                } else if (TextUtils.equals(message.msgtype, Message.MSGTYPE_FILE)) {
                    url = JsonUtils.toFileMessage(event.getContent()).getUrl();
                }

                if (!TextUtils.isEmpty(url)) {
                    mediaDownloadId = mSession.getMediasCache().downloadIdFromUrl(url);
                }
            }

            mMediaDownloadIdByEventId.put(event.eventId, mediaDownloadId);
        }

        return TextUtils.equals(mediaDownloadId, downloadId);
    }

    private void refreshDownloadViews(final Event event, final IMXMediaDownloadListener.DownloadStats downloadStats, final View downloadProgressLayout) {
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

    private void updateUploadProgress(View uploadProgressLayout, int progress) {
        ProgressBar progressBar = (ProgressBar) uploadProgressLayout.findViewById(R.id.media_progress_view);

        if (null != progressBar) {
            progressBar.setProgress(progress);
        }
    }

    private void refreshUploadViews(final Event event, final IMXMediaUploadListener.UploadStats uploadStats, final View uploadProgressLayout) {
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
