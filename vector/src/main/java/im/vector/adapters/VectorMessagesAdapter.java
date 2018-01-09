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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.AbstractMessagesAdapter;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.crypto.EncryptedEventContent;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.message.FileMessage;
import org.matrix.androidsdk.rest.model.message.ImageMessage;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.CommonActivityUtils;
import im.vector.listeners.IMessagesAdapterActionsListener;
import im.vector.util.MatrixLinkMovementMethod;
import im.vector.util.MatrixURLSpan;
import im.vector.util.EventGroup;
import im.vector.util.PreferencesManager;
import im.vector.util.RiotEventDisplay;
import im.vector.util.ThemeUtils;
import im.vector.util.VectorMarkdownParser;
import im.vector.widgets.WidgetsManager;

/**
 * An adapter which can display room information.
 */
public class VectorMessagesAdapter extends AbstractMessagesAdapter {
    private static final String LOG_TAG = VectorMessagesAdapter.class.getSimpleName();

    // an event is selected when the user taps on it
    private String mSelectedEventId;

    // events listeners
    IMessagesAdapterActionsListener mVectorMessagesAdapterEventsListener = null;

    // current date : used to compute the day header
    private Date mReferenceDate = new Date();

    // day date of each message
    // the hours, minutes and seconds are removed
    private ArrayList<Date> mMessagesDateList = new ArrayList<>();

    // when the adapter is used in search mode
    // the searched message should be highlighted
    private String mSearchedEventId = null;
    private String mHighlightedEventId = null;

    // formatted time by event id
    // it avoids computing them several times
    private final HashMap<String, String> mEventFormattedTsMap = new HashMap<>();

    // define the e2e icon to use for a dedicated eventId
    // can be a drawable or
    private HashMap<String, Object> mE2eIconByEventId = new HashMap<>();

    // device info by device id
    private HashMap<String, MXDeviceInfo> mE2eDeviceByEventId = new HashMap<>();

    // true when the room is encrypted
    public boolean mIsRoomEncrypted;

    static final int ROW_TYPE_TEXT = 0;
    static final int ROW_TYPE_IMAGE = 1;
    static final int ROW_TYPE_NOTICE = 2;
    static final int ROW_TYPE_EMOTE = 3;
    static final int ROW_TYPE_FILE = 4;
    static final int ROW_TYPE_VIDEO = 5;
    static final int ROW_TYPE_MERGE = 6;
    static final int ROW_TYPE_HIDDEN = 7;
    static final int ROW_TYPE_ROOM_MEMBER = 8;
    static final int ROW_TYPE_EMOJI = 9;
    static final int ROW_TYPE_CODE = 10;
    static final int NUM_ROW_TYPES = 11;

    final Context mContext;
    private final HashMap<Integer, Integer> mRowTypeToLayoutId = new HashMap<>();
    final LayoutInflater mLayoutInflater;

    // To keep track of events and avoid duplicates. For instance, we add a message event
    // when the current user sends one but it will also come down the event stream
    private final HashMap<String, MessageRow> mEventRowMap = new HashMap<>();

    private final HashMap<String, Integer> mEventType = new HashMap<>();

    // the message text colors
    private final int mDefaultMessageTextColor;
    private final int mNotSentMessageTextColor;
    private final int mSendingMessageTextColor;
    private final int mEncryptingMessageTextColor;
    private final int mHighlightMessageTextColor;
    int mSearchHighlightMessageTextColor;

    private final int mMaxImageWidth;
    private final int mMaxImageHeight;

    // media cache
    private final MXMediasCache mMediasCache;

    // session
    final MXSession mSession;

    private boolean mIsSearchMode = false;
    private boolean mIsPreviewMode = false;
    private boolean mIsUnreadViewMode = false;
    private String mPattern = null;
    private ArrayList<MessageRow> mLiveMessagesRowList = null;

    // id of the read markers event
    private String mReadReceiptEventId;

    private MatrixLinkMovementMethod mLinkMovementMethod;

    private final VectorMessagesAdapterMediasHelper mMediasHelper;
    final VectorMessagesAdapterHelper mHelper;

    private final Set<String> mHiddenEventIds = new HashSet<>();

    private final Locale mLocale;

    // custom settings
    private final boolean mAlwaysShowTimeStamps;
    private final boolean mHideReadReceipts;

    private static final Pattern mEmojisPattern = Pattern.compile("((?:[\uD83C\uDF00-\uD83D\uDDFF]|[\uD83E\uDD00-\uD83E\uDDFF]|[\uD83D\uDE00-\uD83D\uDE4F]|[\uD83D\uDE80-\uD83D\uDEFF]|[\u2600-\u26FF]\uFE0F?|[\u2700-\u27BF]\uFE0F?|\u24C2\uFE0F?|[\uD83C\uDDE6-\uD83C\uDDFF]{1,2}|[\uD83C\uDD70\uD83C\uDD71\uD83C\uDD7E\uD83C\uDD7F\uD83C\uDD8E\uD83C\uDD91-\uD83C\uDD9A]\uFE0F?|[\u0023\u002A\u0030-\u0039]\uFE0F?\u20E3|[\u2194-\u2199\u21A9-\u21AA]\uFE0F?|[\u2B05-\u2B07\u2B1B\u2B1C\u2B50\u2B55]\uFE0F?|[\u2934\u2935]\uFE0F?|[\u3030\u303D]\uFE0F?|[\u3297\u3299]\uFE0F?|[\uD83C\uDE01\uD83C\uDE02\uD83C\uDE1A\uD83C\uDE2F\uD83C\uDE32-\uD83C\uDE3A\uD83C\uDE50\uD83C\uDE51]\uFE0F?|[\u203C\u2049]\uFE0F?|[\u25AA\u25AB\u25B6\u25C0\u25FB-\u25FE]\uFE0F?|[\u00A9\u00AE]\uFE0F?|[\u2122\u2139]\uFE0F?|\uD83C\uDC04\uFE0F?|\uD83C\uDCCF\uFE0F?|[\u231A\u231B\u2328\u23CF\u23E9-\u23F3\u23F8-\u23FA]\uFE0F?))");

    // the color depends in the theme
    private final Drawable mPadlockDrawable;

    /**
     * Creates a messages adapter with the default layouts.
     */
    public VectorMessagesAdapter(MXSession session, Context context, MXMediasCache mediasCache) {
        this(session, context,
                R.layout.adapter_item_vector_message_text_emote_notice,
                R.layout.adapter_item_vector_message_image_video,
                R.layout.adapter_item_vector_message_text_emote_notice,
                R.layout.adapter_item_vector_message_room_member,
                R.layout.adapter_item_vector_message_text_emote_notice,
                R.layout.adapter_item_vector_message_file,
                R.layout.adapter_item_vector_message_image_video,
                R.layout.adapter_item_vector_message_merge,
                R.layout.adapter_item_vector_message_emoji,
                R.layout.adapter_item_vector_message_code,
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
    VectorMessagesAdapter(MXSession session,
                          Context context,
                          int textResLayoutId,
                          int imageResLayoutId,
                          int noticeResLayoutId,
                          int roomMemberResLayoutId,
                          int emoteRestLayoutId,
                          int fileResLayoutId,
                          int videoResLayoutId,
                          int mergeResLayoutId,
                          int emojiResLayoutId,
                          int codeResLayoutId,
                          MXMediasCache mediasCache) {
        super(context, 0);
        mContext = context;
        mRowTypeToLayoutId.put(ROW_TYPE_TEXT, textResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_IMAGE, imageResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_NOTICE, noticeResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_ROOM_MEMBER, roomMemberResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_EMOTE, emoteRestLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_FILE, fileResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_VIDEO, videoResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_MERGE, mergeResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_HIDDEN, R.layout.adapter_item_vector_hidden_message);
        mRowTypeToLayoutId.put(ROW_TYPE_EMOJI, emojiResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_CODE, codeResLayoutId);

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

        // helpers
        mMediasHelper = new VectorMessagesAdapterMediasHelper(context, mSession, mMaxImageWidth, mMaxImageHeight, mNotSentMessageTextColor, mDefaultMessageTextColor);
        mHelper = new VectorMessagesAdapterHelper(context, mSession);

        mLocale = VectorApp.getApplicationLocale();

        mAlwaysShowTimeStamps = PreferencesManager.alwaysShowTimeStamps(VectorApp.getInstance());
        mHideReadReceipts = PreferencesManager.hideReadReceipts(VectorApp.getInstance());

        mPadlockDrawable = CommonActivityUtils.tintDrawable(mContext, ContextCompat.getDrawable(mContext, R.drawable.e2e_unencrypted), R.attr.settings_icon_tint_color);
    }

    /*
     * *********************************************************************************************
     * Graphical items
     * *********************************************************************************************
     */

    /**
     * Return the screen size.
     *
     * @param size the size to set
     */
    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    private void getScreenSize(Point size) {
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getSize(size);
    }

    /**
     * @return the max thumbnail width
     */
    public int getMaxThumbnailWidth() {
        return mMaxImageWidth;
    }

    /**
     * @return the max thumbnail height
     */
    public int getMaxThumbnailHeight() {
        return mMaxImageHeight;
    }

    // customization methods
    private int getDefaultMessageTextColor() {
        return ThemeUtils.getColor(mContext, R.attr.message_text_color);
    }

    private int getNoticeTextColor() {
        return ThemeUtils.getColor(mContext, R.attr.notice_text_color);
    }

    private int getEncryptingMessageTextColor() {
        return ThemeUtils.getColor(mContext, R.attr.encrypting_message_text_color);
    }

    private int getSendingMessageTextColor() {
        return ThemeUtils.getColor(mContext, R.attr.sending_message_text_color);
    }

    private int getHighlightMessageTextColor() {
        return ThemeUtils.getColor(mContext, R.attr.highlighted_message_text_color);
    }

    private int getSearchHighlightMessageTextColor() {
        return ThemeUtils.getColor(mContext, R.attr.highlighted_searched_message_text_color);
    }

    private int getNotSentMessageTextColor() {
        return ThemeUtils.getColor(mContext, R.attr.unsent_message_text_color);
    }

    /*
     * *********************************************************************************************
     * Items getter / setter
     * *********************************************************************************************
     */

    /**
     * Tests if the row can be inserted in a merge row.
     *
     * @param row the message row to test
     * @return true if the row can be merged
     */
    boolean supportMessageRowMerge(MessageRow row) {
        return EventGroup.isSupported(row);
    }

    @Override
    public void addToFront(MessageRow row) {
        if (isSupportedRow(row)) {
            // ensure that notifyDataSetChanged is not called
            // it seems that setNotifyOnChange is reinitialized to true;
            setNotifyOnChange(false);

            if (mIsSearchMode) {
                mLiveMessagesRowList.add(0, row);
            } else {
                insert(row, (!addToEventGroupToFront(row)) ? 0 : 1);
            }

            if (row.getEvent().eventId != null) {
                mEventRowMap.put(row.getEvent().eventId, row);
            }
        }
    }

    @Override
    public void remove(MessageRow row) {
        if (null != row) {
            if (mIsSearchMode) {
                mLiveMessagesRowList.remove(row);
            } else {
                removeFromEventGroup(row);

                // get the position before removing the item
                int position = getPosition(row);

                // remove it
                super.remove(row);

                // check merge
                checkEventGroupsMerge(row, position);
            }
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
                addToEventGroup(row);
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
                if (!(row.getEvent() instanceof EventGroup)) {
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
                if (!(row.getEvent() instanceof EventGroup)) {
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

    /*
     * *********************************************************************************************
     * ArrayAdapter methods
     * *********************************************************************************************
     */

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

    @Override
    public int getItemViewType(int position) {
        // GA Crash
        if (position >= getCount()) {
            return ROW_TYPE_TEXT;
        }

        MessageRow row = getItem(position);
        return getItemViewType(row.getEvent());
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
        int viewType = getItemViewType(position);

        switch (viewType) {
            case ROW_TYPE_EMOJI:
            case ROW_TYPE_CODE:
            case ROW_TYPE_TEXT:
                inflatedView = getTextView(viewType, position, convertView, parent);
                break;
            case ROW_TYPE_IMAGE:
            case ROW_TYPE_VIDEO:
                inflatedView = getImageVideoView(viewType, position, convertView, parent);
                break;
            case ROW_TYPE_NOTICE:
            case ROW_TYPE_ROOM_MEMBER:
                inflatedView = getNoticeRoomMemberView(viewType, position, convertView, parent);
                break;
            case ROW_TYPE_EMOTE:
                inflatedView = getEmoteView(position, convertView, parent);
                break;
            case ROW_TYPE_FILE:
                inflatedView = getFileView(position, convertView, parent);
                break;
            case ROW_TYPE_HIDDEN:
                inflatedView = getHiddenView(position, convertView, parent);
                break;
            case ROW_TYPE_MERGE:
                inflatedView = getMergeView(position, convertView, parent);
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

        displayE2eIcon(inflatedView, position);

        return inflatedView;
    }

    @Override
    public void notifyDataSetChanged() {
        // the event with invalid timestamp must be pushed at the end of the history
        this.setNotifyOnChange(false);
        List<MessageRow> undeliverableEvents = new ArrayList<>();

        for (int i = 0; i < getCount(); i++) {
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

        // build event -> date list
        refreshRefreshDateList();

        manageCryptoEvents();

        //  do not refresh the room when the application is in background
        // on large rooms, it drains a lot of battery
        if (!VectorApp.isAppInBackground()) {
            super.notifyDataSetChanged();
        }
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    /**
     * Notify the fragment that some bing rules could have been updated.
     */
    public void onBingRulesUpdate() {
        this.notifyDataSetChanged();
    }

    /**
     * the parent fragment is paused.
     */
    public void onPause() {
        mEventFormattedTsMap.clear();
    }

    /**
     * Toggle the selection mode.
     *
     * @param eventId the tapped eventID.
     */
    public void onEventTap(String eventId) {
        // the tap to select is only enabled when the adapter is not in search mode.
        if (!mIsSearchMode) {
            if (null == mSelectedEventId) {
                mSelectedEventId = eventId;
            } else {
                mSelectedEventId = null;
            }
            notifyDataSetChanged();
        }
    }

    /**
     * Display a bar to the left of the message
     *
     * @param eventId the event id
     */
    public void setSearchedEventId(String eventId) {
        mSearchedEventId = eventId;
        updateHighlightedEventId();
    }

    /**
     * Cancel the message selection mode
     */
    public void cancelSelectionMode() {
        if (null != mSelectedEventId) {
            mSelectedEventId = null;
            notifyDataSetChanged();
        }
    }

    /**
     * @return true if there is a selected item.
     */
    public boolean isInSelectionMode() {
        return null != mSelectedEventId;
    }

    /**
     * Define the events listener
     *
     * @param listener teh events listener
     */
    public void setVectorMessagesAdapterActionsListener(IMessagesAdapterActionsListener listener) {
        mVectorMessagesAdapterEventsListener = listener;
        mMediasHelper.setVectorMessagesAdapterActionsListener(listener);
        mHelper.setVectorMessagesAdapterActionsListener(listener);

        if (null != mLinkMovementMethod) {
            mLinkMovementMethod.updateListener(listener);
        } else if (null != listener) {
            mLinkMovementMethod = new MatrixLinkMovementMethod(listener);
        }
        mHelper.setLinkMovementMethod(mLinkMovementMethod);
    }

    /**
     * Retrieves the MXDevice info from an event id
     *
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

    /*
     * *********************************************************************************************
     * Item view methods
     * *********************************************************************************************
     */

    /**
     * Test if a string contains emojis.
     * It seems that the regex [emoji_regex]+ does not work.
     * Some characters like ?, # or digit are accepted.
     *
     * @param body the body to test
     * @return true if the body contains only emojis
     */
    private static boolean containsOnlyEmojis(String body) {
        boolean res = false;

        if (!TextUtils.isEmpty(body)) {
            Matcher matcher = mEmojisPattern.matcher(body);

            int start = -1;
            int end = -1;

            while (matcher.find()) {
                int nextStart = matcher.start();

                // first emoji position
                if (start < 0) {
                    if (nextStart > 0) {
                        return false;
                    }
                } else {
                    // must not have a character between
                    if (nextStart != end) {
                        return false;
                    }
                }
                start = nextStart;
                end = matcher.end();
            }

            res = (-1 != start) && (end == body.length());
        }

        return res;
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

        if ((null != eventId) && mHiddenEventIds.contains(eventId)) {
            return ROW_TYPE_HIDDEN;
        }

        // never cache the view type of the encrypted messages
        if (Event.EVENT_TYPE_MESSAGE_ENCRYPTED.equals(eventType)) {
            return ROW_TYPE_TEXT;
        }

        if (event instanceof EventGroup) {
            return ROW_TYPE_MERGE;
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
            Message message = JsonUtils.toMessage(event.getContent());
            String msgType = message.msgtype;

            if (Message.MSGTYPE_TEXT.equals(msgType)) {
                if (containsOnlyEmojis(message.body)) {
                    viewType = ROW_TYPE_EMOJI;
                } else if (!TextUtils.isEmpty(message.formatted_body) && mHelper.containsFencedCodeBlocks(message)) {
                    viewType = ROW_TYPE_CODE;
                } else {
                    viewType = ROW_TYPE_TEXT;
                }
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
            viewType = ROW_TYPE_ROOM_MEMBER;

        } else if (WidgetsManager.WIDGET_EVENT_TYPE.equals(eventType)) {
            return ROW_TYPE_ROOM_MEMBER;
        } else {
            throw new RuntimeException("Unknown event type: " + eventType);
        }

        if (null != eventId) {
            mEventType.put(eventId, new Integer(viewType));
        }

        return viewType;
    }

    /**
     * Tells if the event of type 'eventType' can be merged.
     *
     * @param eventType the event type to test
     * @return true if the event can be merged
     */
    private static boolean isMergeableEvent(int eventType) {
        return (ROW_TYPE_NOTICE != eventType) && (ROW_TYPE_ROOM_MEMBER != eventType) && (ROW_TYPE_HIDDEN != eventType);
    }

    /**
     * Common view management.
     *
     * @param position    the item position.
     * @param convertView the row view
     * @param subView     the message content view
     * @param msgType     the message type
     */
    private void manageSubView(final int position, View convertView, View subView, int msgType) {
        MessageRow row = getItem(position);

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
                return (null != mVectorMessagesAdapterEventsListener) && mVectorMessagesAdapterEventsListener.onRowLongClick(position);
            }
        });

        Event event = row.getEvent();

        // isMergedView -> the message is going to be merged with the previous one
        // willBeMerged ->tell if a message separator must be displayed
        boolean isMergedView = false;
        boolean willBeMerged = false;

        // the notices are never merged
        if (!mIsSearchMode && isMergeableEvent(msgType)) {
            if (position > 0) {
                Event prevEvent = getItem(position - 1).getEvent();
                isMergedView = isMergeableEvent(getItemViewType(prevEvent)) && TextUtils.equals(prevEvent.getSender(), event.getSender());
            }

            // not the last message
            if ((position + 1) < this.getCount()) {
                Event nextEvent = getItem(position + 1).getEvent();
                willBeMerged = isMergeableEvent(getItemViewType(nextEvent)) && TextUtils.equals(nextEvent.getSender(), event.getSender());
            }
        }

        // inherited class custom behaviour
        isMergedView = mergeView(event, position, isMergedView);

        // init senders
        mHelper.setSenderValue(convertView, row, isMergedView);

        // message timestamp
        TextView tsTextView = VectorMessagesAdapterHelper.setTimestampValue(convertView, getFormattedTimestamp(event));

        if (null != tsTextView) {
            if (row.getEvent().isUndeliverable() || row.getEvent().isUnkownDevice()) {
                tsTextView.setTextColor(mNotSentMessageTextColor);
            } else {
                tsTextView.setTextColor(ThemeUtils.getColor(mContext, R.attr.default_text_light_color));
            }

            tsTextView.setVisibility((((position + 1) == this.getCount()) || mIsSearchMode || mAlwaysShowTimeStamps) ? View.VISIBLE : View.GONE);
        }

        // Sender avatar
        View avatarLayoutView = mHelper.setSenderAvatar(convertView, row, isMergedView);

        // if the messages are merged
        // the thumbnail is hidden
        // and the subview must be moved to be aligned with the previous body
        View bodyLayoutView = convertView.findViewById(R.id.messagesAdapter_body_layout);
        VectorMessagesAdapterHelper.alignSubviewToAvatarView(subView, bodyLayoutView, avatarLayoutView, isMergedView);

        // messages separator
        View messageSeparatorView = convertView.findViewById(R.id.messagesAdapter_message_separator);

        if (null != messageSeparatorView) {
            messageSeparatorView.setVisibility((willBeMerged || ((position + 1) == this.getCount())) ? View.GONE : View.VISIBLE);
        }

        // display the day separator
        VectorMessagesAdapterHelper.setHeader(convertView, headerMessage(position), position);

        // read receipts
        if (mHideReadReceipts) {
            mHelper.hideReadReceipts(convertView);
        } else {
            mHelper.displayReadReceipts(convertView, row, mIsPreviewMode);
        }

        // selection mode
        manageSelectionMode(convertView, event, msgType);

        // read marker
        setReadMarker(convertView, row, isMergedView, avatarLayoutView, bodyLayoutView);

        // download / upload progress layout
        if ((ROW_TYPE_IMAGE == msgType) || (ROW_TYPE_FILE == msgType) || (ROW_TYPE_VIDEO == msgType)) {
            VectorMessagesAdapterHelper.setMediaProgressLayout(convertView, bodyLayoutView);
        }
    }

    /**
     * Text message management
     *
     * @param viewType    the view type
     * @param position    the message position
     * @param convertView the text message view
     * @param parent      the parent view
     * @return the updated text view.
     */
    private View getTextView(final int viewType, final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(viewType), parent, false);
        }

        try {
            MessageRow row = getItem(position);
            Event event = row.getEvent();
            Message message = JsonUtils.toMessage(event.getContent());
            RoomState roomState = row.getRoomState();

            EventDisplay display = new RiotEventDisplay(mContext, event, roomState);
            CharSequence textualDisplay = display.getTextualDisplay();

            SpannableString body = new SpannableString((null == textualDisplay) ? "" : textualDisplay);

            boolean shouldHighlighted = (null != mVectorMessagesAdapterEventsListener) && mVectorMessagesAdapterEventsListener.shouldHighlightEvent(event);

            final List<TextView> textViews;

            if (ROW_TYPE_CODE == viewType) {
                textViews = populateRowTypeCode(message, convertView, shouldHighlighted);
            } else {
                final TextView bodyTextView = convertView.findViewById(R.id.messagesAdapter_body);

                // cannot refresh it
                if (null == bodyTextView) {
                    Log.e(LOG_TAG, "getTextView : invalid layout");
                    return convertView;
                }

                highlightPattern(bodyTextView, body,
                        TextUtils.equals(Message.FORMAT_MATRIX_HTML, message.format) ? mHelper.getSanitisedHtml(message.formatted_body) : null,
                        mPattern, shouldHighlighted);

                textViews = new ArrayList<>();
                textViews.add(bodyTextView);
            }

            int textColor;

            if (row.getEvent().isEncrypting()) {
                textColor = mEncryptingMessageTextColor;
            } else if (row.getEvent().isSending() || row.getEvent().isUnsent()) {
                textColor = mSendingMessageTextColor;
            } else if (row.getEvent().isUndeliverable() || row.getEvent().isUnkownDevice()) {
                textColor = mNotSentMessageTextColor;
            } else {
                textColor = shouldHighlighted ? mHighlightMessageTextColor : mDefaultMessageTextColor;
            }

            for (final TextView tv : textViews) {
                tv.setTextColor(textColor);
            }

            View textLayout = convertView.findViewById(R.id.messagesAdapter_text_layout);
            this.manageSubView(position, convertView, textLayout, viewType);

            for (final TextView tv : textViews) {
                addContentViewListeners(convertView, tv, position, viewType);
            }
        } catch (Exception e) {
            StackTraceElement[] callstacks = e.getStackTrace();

            StringBuilder sb = new StringBuilder();
            for (StackTraceElement element : callstacks) {
                sb.append(element.toString());
                sb.append("\n");
            }

            Log.e(LOG_TAG, "## getTextView() failed : " + e.getMessage());
            Log.e(LOG_TAG, "## getTextView() callstack \n" + sb.toString());
        }

        return convertView;
    }

    /**
     * For ROW_TYPE_CODE message which may contain mixture of
     * fenced and inline code blocks and non-code (issue 145)
     */
    private List<TextView> populateRowTypeCode(final Message message,
                                               final View convertView,
                                               final boolean shouldHighlighted) {
        final List<TextView> textViews = new ArrayList<>();
        final LinearLayout container = convertView.findViewById(R.id.messages_container);

        // remove older blocks
        container.removeAllViews();

        final String[] blocks = mHelper.getFencedCodeBlocks(message);
        for (int i = 0; i < blocks.length; i++) {

            // Start of fenced block
            if (blocks[i].equals("```") && i <= blocks.length - 3 && blocks[i + 2].equals("```")) {
                final SpannableString threeTickBlock = new SpannableString(blocks[i + 1]);
                final View blockView = mLayoutInflater.inflate(R.layout.adapter_item_vector_message_code_block, null);
                container.addView(blockView);
                final TextView tv = blockView.findViewById(R.id.messagesAdapter_body);
                mHelper.highlightFencedCode(tv, threeTickBlock);
                i += 2;
                textViews.add(tv);

                ((View)tv.getParent()).setBackgroundColor(ThemeUtils.getColor(mContext, R.attr.markdown_block_background_color));
            } else {
                // Not a fenced block
                final TextView tv = (TextView) mLayoutInflater.inflate(R.layout.adapter_item_vector_message_code_text, null);
                final String ithBlock = blocks[i];
                VectorApp.markdownToHtml(blocks[i],
                        new VectorMarkdownParser.IVectorMarkdownParserListener() {
                            @Override
                            public void onMarkdownParsed(final String text, final String HTMLText) {
                                highlightPattern(tv, new SpannableString(ithBlock),
                                        TextUtils.equals(Message.FORMAT_MATRIX_HTML, message.format) ? mHelper.getSanitisedHtml(HTMLText) : null,
                                        mPattern, shouldHighlighted);
                            }
                        });

                container.addView(tv);
                textViews.add(tv);
            }
        }

        return textViews;
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

        try {
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
            final ImageView imageTypeView = convertView.findViewById(R.id.messagesAdapter_image_type);

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
            mMediasHelper.managePendingImageVideoDownload(convertView, event, message, position);

            // upload management
            mMediasHelper.managePendingImageVideoUpload(convertView, event, message);

            // dimmed when the message is not sent
            View imageLayout = convertView.findViewById(R.id.messagesAdapter_image_layout);
            imageLayout.setAlpha(event.isSent() ? 1.0f : 0.5f);

            this.manageSubView(position, convertView, imageLayout, type);

            ImageView imageView = convertView.findViewById(R.id.messagesAdapter_image);
            addContentViewListeners(convertView, imageView, position, type);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getImageVideoView() failed : " + e.getMessage());
        }

        return convertView;
    }

    /**
     * Notice message management
     *
     * @param position    the message position
     * @param convertView the message view
     * @param parent      the parent view
     * @return the updated text view.
     */
    private View getNoticeRoomMemberView(final int viewType, final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(viewType), parent, false);
        }

        try {
            MessageRow row = getItem(position);
            Event msg = row.getEvent();
            RoomState roomState = row.getRoomState();

            CharSequence notice;

            EventDisplay display = new RiotEventDisplay(mContext, msg, roomState);
            notice = display.getTextualDisplay();

            TextView noticeTextView = convertView.findViewById(R.id.messagesAdapter_body);

            if (null == noticeTextView) {
                Log.e(LOG_TAG, "getNoticeRoomMemberView : invalid layout");
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
            this.manageSubView(position, convertView, textLayout, viewType);

            addContentViewListeners(convertView, noticeTextView, position, viewType);

            // android seems having a big issue when the text is too long and an alpha !=1 is applied:
            // ---> the text is not displayed.
            // It is sometimes partially displayed and/or flickers while scrolling.
            // Apply an alpha != 1, trigger the same issue.
            // It is related to the number of characters not to the number of lines.
            // I don't understand why the render graph fails to do it.
            // the patch apply the alpha to the text color but it does not work for the hyperlinks.
            noticeTextView.setAlpha(1.0f);
            noticeTextView.setTextColor(getNoticeTextColor());
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getNoticeRoomMemberView() failed : " + e.getMessage());
        }

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

        try {
            MessageRow row = getItem(position);
            Event event = row.getEvent();
            RoomState roomState = row.getRoomState();

            TextView emoteTextView = convertView.findViewById(R.id.messagesAdapter_body);

            if (null == emoteTextView) {
                Log.e(LOG_TAG, "getEmoteView : invalid layout");
                return convertView;
            }

            Message message = JsonUtils.toMessage(event.getContent());
            String userDisplayName = (null == roomState) ? event.getSender() : roomState.getMemberName(event.getSender());

            String body = "* " + userDisplayName + " " + message.body;

            String htmlString = null;

            if (TextUtils.equals(Message.FORMAT_MATRIX_HTML, message.format)) {
                htmlString = mHelper.getSanitisedHtml(message.formatted_body);

                if (null != htmlString) {
                    htmlString = "* " + userDisplayName + " " + message.formatted_body;
                }
            }

            highlightPattern(emoteTextView, new SpannableString(body), htmlString, null, false);

            int textColor;

            if (row.getEvent().isEncrypting()) {
                textColor = mEncryptingMessageTextColor;
            } else if (row.getEvent().isSending() || row.getEvent().isUnsent()) {
                textColor = mSendingMessageTextColor;
            } else if (row.getEvent().isUndeliverable() || row.getEvent().isUnkownDevice()) {
                textColor = mNotSentMessageTextColor;
            } else {
                textColor = mDefaultMessageTextColor;
            }

            emoteTextView.setTextColor(textColor);

            View textLayout = convertView.findViewById(R.id.messagesAdapter_text_layout);
            this.manageSubView(position, convertView, textLayout, ROW_TYPE_EMOTE);

            addContentViewListeners(convertView, emoteTextView, position, ROW_TYPE_EMOTE);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getEmoteView() failed : " + e.getMessage());
        }

        return convertView;
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

        try {
            MessageRow row = getItem(position);
            Event event = row.getEvent();

            final FileMessage fileMessage = JsonUtils.toFileMessage(event.getContent());
            final TextView fileTextView = convertView.findViewById(R.id.messagesAdapter_filename);

            if (null == fileTextView) {
                Log.e(LOG_TAG, "getFileView : invalid layout");
                return convertView;
            }

            fileTextView.setPaintFlags(fileTextView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            fileTextView.setText("\n" + fileMessage.body + "\n");

            // display the right message type icon.
            // Audio and File messages are managed by the same method
            final ImageView imageTypeView = convertView.findViewById(R.id.messagesAdapter_image_type);

            if (null != imageTypeView) {
                imageTypeView.setImageResource(Message.MSGTYPE_AUDIO.equals(fileMessage.msgtype) ? R.drawable.filetype_audio : R.drawable.filetype_attachment);
            }
            imageTypeView.setBackgroundColor(Color.TRANSPARENT);

            mMediasHelper.managePendingFileDownload(convertView, event, fileMessage, position);
            mMediasHelper.managePendingUpload(convertView, event, ROW_TYPE_FILE, fileMessage.url);

            View fileLayout = convertView.findViewById(R.id.messagesAdapter_file_layout);
            this.manageSubView(position, convertView, fileLayout, ROW_TYPE_FILE);

            addContentViewListeners(convertView, fileTextView, position, ROW_TYPE_FILE);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getFileView() failed " + e.getMessage());
        }

        return convertView;
    }

    /**
     * Hidden message management.
     *
     * @param position    the message position
     * @param convertView the message view
     * @param parent      the parent view
     * @return the updated text view.
     */
    private View getHiddenView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_HIDDEN), parent, false);
        }

        // display the day separator
        VectorMessagesAdapterHelper.setHeader(convertView, headerMessage(position), position);

        return convertView;
    }

    /**
     * Get a merge view for a position.
     *
     * @param position    the message position
     * @param convertView the message view
     * @param parent      the parent view
     * @return the updated text view.
     */
    private View getMergeView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_MERGE), parent, false);
        }

        try {
            MessageRow row = getItem(position);
            final EventGroup event = (EventGroup) row.getEvent();

            View headerLayout = convertView.findViewById(R.id.messagesAdapter_merge_header_layout);
            TextView headerTextView = convertView.findViewById(R.id.messagesAdapter_merge_header_text_view);
            TextView summaryTextView = convertView.findViewById(R.id.messagesAdapter_merge_summary);
            View separatorLayout = convertView.findViewById(R.id.messagesAdapter_merge_separator);
            View avatarsLayout = convertView.findViewById(R.id.messagesAdapter_merge_avatar_list);

            // test if the layout is still valid
            // reported by a rageshake
            if ((null == headerLayout) || (null == headerTextView) || (null == summaryTextView)
                    || (null == separatorLayout) || (null == avatarsLayout)) {
                Log.e(LOG_TAG, "getMergeView : invalid layout");
                return convertView;
            }

            separatorLayout.setVisibility(event.isExpanded() ? View.VISIBLE : View.GONE);
            summaryTextView.setVisibility(event.isExpanded() ? View.GONE : View.VISIBLE);
            avatarsLayout.setVisibility(event.isExpanded() ? View.GONE : View.VISIBLE);

            headerTextView.setText(event.isExpanded() ? "collapse" : "expand");

            if (!event.isExpanded()) {
                avatarsLayout.setVisibility(View.VISIBLE);
                List<ImageView> avatarView = new ArrayList<>();

                avatarView.add((ImageView) convertView.findViewById(R.id.mels_list_avatar_1));
                avatarView.add((ImageView) convertView.findViewById(R.id.mels_list_avatar_2));
                avatarView.add((ImageView) convertView.findViewById(R.id.mels_list_avatar_3));
                avatarView.add((ImageView) convertView.findViewById(R.id.mels_list_avatar_4));
                avatarView.add((ImageView) convertView.findViewById(R.id.mels_list_avatar_5));

                List<MessageRow> messageRows = event.getAvatarRows(avatarView.size());

                for (int i = 0; i < avatarView.size(); i++) {
                    ImageView imageView = avatarView.get(i);

                    if (i < messageRows.size()) {
                        mHelper.loadMemberAvatar(imageView, messageRows.get(i));
                        imageView.setVisibility(View.VISIBLE);
                    } else {
                        imageView.setVisibility(View.GONE);
                    }
                }


                summaryTextView.setText(event.toString(mContext));
            }

            headerLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    event.setIsExpanded(!event.isExpanded());
                    updateHighlightedEventId();

                    if (event.contains(mSelectedEventId)) {
                        cancelSelectionMode();
                    } else {
                        notifyDataSetChanged();
                    }
                }
            });

            // set the message marker
            convertView.findViewById(R.id.messagesAdapter_highlight_message_marker).setBackgroundColor(ContextCompat.getColor(mContext, TextUtils.equals(mHighlightedEventId, event.eventId) ? R.color.vector_green_color : android.R.color.transparent));

            // display the day separator
            VectorMessagesAdapterHelper.setHeader(convertView, headerMessage(position), position);

            boolean isInSelectionMode = (null != mSelectedEventId);
            boolean isSelected = TextUtils.equals(event.eventId, mSelectedEventId);

            float alpha = (!isInSelectionMode || isSelected) ? 1.0f : 0.2f;

            // the message body is dimmed when not selected
            convertView.findViewById(R.id.messagesAdapter_body_view).setAlpha(alpha);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getMergeView() failed " + e.getMessage());
        }

        return convertView;
    }

    /**
     * Highlight a pattern in a text view.
     *
     * @param textView the text view
     * @param text     the text to display
     * @param pattern  the pattern to highlight
     */
    void highlightPattern(TextView textView, Spannable text, String pattern) {
        highlightPattern(textView, text, null, pattern, false);
    }

    /**
     * Highlight a pattern in a text view.
     *
     * @param textView          the text view
     * @param text              the text to display
     * @param htmlFormattedText the text in HTML format
     * @param pattern           the pattern to highlight
     * @param isHighlighted     true when the event is highlighted
     */
    private void highlightPattern(TextView textView, Spannable text, String htmlFormattedText, String pattern, boolean isHighlighted) {
        mHelper.highlightPattern(textView, text, htmlFormattedText, pattern, new BackgroundColorSpan(mSearchHighlightMessageTextColor), isHighlighted);
    }

    /**
     * Check if the row must be added to the list.
     *
     * @param row the row to check.
     * @return true if should be added
     */
    private boolean isSupportedRow(MessageRow row) {
        boolean isSupported = VectorMessagesAdapterHelper.isDisplayableEvent(mContext, row);

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

            if (TextUtils.equals(row.getEvent().getType(), Event.EVENT_TYPE_STATE_ROOM_MEMBER)) {
                RoomMember roomMember = JsonUtils.toRoomMember(row.getEvent().getContent());
                String membership = roomMember.membership;

                if (PreferencesManager.hideJoinLeaveMessages(mContext)) {
                    isSupported = !TextUtils.equals(membership, RoomMember.MEMBERSHIP_LEAVE) && !TextUtils.equals(membership, RoomMember.MEMBERSHIP_JOIN);
                }

                if (isSupported && PreferencesManager.hideAvatarDisplayNameChangeMessages(mContext) && TextUtils.equals(membership, RoomMember.MEMBERSHIP_JOIN)) {
                    EventContent eventContent = JsonUtils.toEventContent(row.getEvent().getContentAsJsonObject());
                    EventContent prevEventContent = row.getEvent().getPrevContent();

                    String senderDisplayName = eventContent.displayname;
                    String prevUserDisplayName = null;
                    String avatar = eventContent.avatar_url;
                    String prevAvatar = null;

                    if ((null != prevEventContent)) {
                        prevUserDisplayName = prevEventContent.displayname;
                        prevAvatar = prevEventContent.avatar_url;
                    }

                    // !Updated display name && same avatar
                    isSupported = TextUtils.equals(prevUserDisplayName, senderDisplayName) && TextUtils.equals(avatar, prevAvatar);
                }
            }
        }

        return isSupported;
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Provides the formatted timestamp to display.
     * null means that the timestamp text must be hidden.
     *
     * @param event the event.
     * @return the formatted timestamp to display.
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

    /**
     * Refresh the messages date list
     */
    private void refreshRefreshDateList() {
        // build messages timestamps
        ArrayList<Date> dates = new ArrayList<>();

        Date latestDate = AdapterUtils.zeroTimeDate(new Date());

        for (int index = 0; index < this.getCount(); index++) {
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
    }

    /**
     * Converts a difference of days to a string.
     *
     * @param date    the date to display
     * @param nbrDays the number of days between the reference days
     * @return the date text
     */
    private String dateDiff(Date date, long nbrDays) {
        if (nbrDays == 0) {
            return mContext.getResources().getString(R.string.today);
        } else if (nbrDays == 1) {
            return mContext.getResources().getString(R.string.yesterday);
        } else if (nbrDays < 7) {
            return (new SimpleDateFormat("EEEE", mLocale)).format(date);
        } else {
            int flags = DateUtils.FORMAT_SHOW_DATE |
                    DateUtils.FORMAT_SHOW_YEAR |
                    DateUtils.FORMAT_ABBREV_ALL |
                    DateUtils.FORMAT_SHOW_WEEKDAY;

            Formatter f = new Formatter(new StringBuilder(50), mLocale);
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
    String headerMessage(int position) {
        Date prevMessageDate = null;
        Date messageDate = null;

        synchronized (this) {
            if ((position > 0) && (position < mMessagesDateList.size())) {
                prevMessageDate = mMessagesDateList.get(position - 1);
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
     * Manage the select mode i.e highlight an item when the user tap on it
     *
     * @param contentView the cell view.
     * @param event       the linked event
     */
    private void manageSelectionMode(final View contentView, final Event event, final int msgType) {
        final String eventId = event.eventId;

        boolean isInSelectionMode = (null != mSelectedEventId);
        boolean isSelected = TextUtils.equals(eventId, mSelectedEventId);

        // display the action icon when selected
        contentView.findViewById(R.id.messagesAdapter_action_image).setVisibility(isSelected ? View.VISIBLE : View.GONE);

        float alpha = (!isInSelectionMode || isSelected) ? 1.0f : 0.2f;

        // the message body is dimmed when not selected
        contentView.findViewById(R.id.messagesAdapter_body_view).setAlpha(alpha);
        contentView.findViewById(R.id.messagesAdapter_avatars_list).setAlpha(alpha);

        TextView tsTextView = contentView.findViewById(R.id.messagesAdapter_timestamp);
        if (isInSelectionMode && isSelected) {
            tsTextView.setVisibility(View.VISIBLE);
        }

        if (!(event instanceof EventGroup)) {
            contentView.findViewById(R.id.message_timestamp_layout).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (TextUtils.equals(eventId, mSelectedEventId)) {
                        onMessageClick(event, getEventText(contentView, event, msgType), contentView.findViewById(R.id.messagesAdapter_action_anchor));
                    } else {
                        onEventTap(eventId);
                    }
                }
            });

            contentView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (!mIsSearchMode) {
                        onMessageClick(event, getEventText(contentView, event, msgType), contentView.findViewById(R.id.messagesAdapter_action_anchor));
                        mSelectedEventId = eventId;
                        notifyDataSetChanged();
                        return true;
                    }

                    return false;
                }
            });
        }
    }

    /**
     * Check an event can be merged with the previous one
     *
     * @param event          the event to merge
     * @param position       the event position in the list
     * @param shouldBeMerged true if the event should be merged
     * @return true to merge the event
     */
    boolean mergeView(Event event, int position, boolean shouldBeMerged) {
        if (shouldBeMerged) {
            shouldBeMerged = null == headerMessage(position);
        }

        return shouldBeMerged && !event.isCallEvent();
    }

    /**
     * Return the text displayed in a convertView in the chat history.
     *
     * @param contentView the cell view
     * @return the displayed text.
     */
    private String getEventText(View contentView, Event event, int msgType) {
        String text = null;

        if (null != contentView) {
            if (ROW_TYPE_CODE == msgType) {
                final Message message = JsonUtils.toMessage(event.getContent());
                text = message.body;
            } else {
                TextView bodyTextView = contentView.findViewById(R.id.messagesAdapter_body);

                if (null != bodyTextView) {
                    text = bodyTextView.getText().toString();
                }
            }
        }

        return text;
    }

    /**
     * Add click listeners on content view
     *
     * @param convertView the cell view
     * @param contentView the main message view
     * @param position    the item position
     */
    private void addContentViewListeners(final View convertView, final View contentView, final int position, final int msgType) {
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
                        onMessageClick(event, getEventText(contentView, event, msgType), convertView.findViewById(R.id.messagesAdapter_action_anchor));
                        mSelectedEventId = event.eventId;
                        notifyDataSetChanged();
                        return true;
                    }
                }

                return true;
            }
        });
    }

    /*
     * *********************************************************************************************
     * E2e management
     * *********************************************************************************************
     */

    /**
     * Display the e2e icon
     *
     * @param inflatedView the base view
     * @param position     the item position
     */
    private void displayE2eIcon(View inflatedView, int position) {
        ImageView e2eIconView = inflatedView.findViewById(R.id.message_adapter_e2e_icon);

        if (null != e2eIconView) {
            View senderMargin = inflatedView.findViewById(R.id.e2e_sender_margin);
            View senderNameView = inflatedView.findViewById(R.id.messagesAdapter_sender);

            MessageRow row = getItem(position);
            final Event event = row.getEvent();

            if (mE2eIconByEventId.containsKey(event.eventId)) {
                if (null != senderMargin) {
                    senderMargin.setVisibility(senderNameView.getVisibility());
                }
                e2eIconView.setVisibility(View.VISIBLE);

                Object icon = mE2eIconByEventId.get(event.eventId);

                if (icon instanceof Drawable) {
                    e2eIconView.setImageDrawable((Drawable) icon);
                } else {
                    e2eIconView.setImageResource((int) icon);
                }

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
                if (null != senderMargin) {
                    senderMargin.setVisibility(View.GONE);
                }
            }
        }
    }

    /**
     * Found the dedicated icon to display for each event id
     */
    private void manageCryptoEvents() {
        HashMap<String, Object> e2eIconByEventId = new HashMap<>();
        HashMap<String, MXDeviceInfo> e2eDeviceInfoByEventId = new HashMap<>();

        if (mIsRoomEncrypted && mSession.isCryptoEnabled()) {
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
                    e2eIconByEventId.put(event.eventId, mPadlockDrawable);
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

    /*
     * *********************************************************************************************
     * Read markers
     * *********************************************************************************************
     */

    private String mReadMarkerEventId;
    private boolean mCanShowReadMarker = true;
    private ReadMarkerListener mReadMarkerListener;

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

    public interface ReadMarkerListener {
        void onReadMarkerDisplayed(Event event, View view);
    }

    /**
     * Specify a listener for read marker
     *
     * @param listener the read marker listener
     */
    public void setReadMarkerListener(final ReadMarkerListener listener) {
        mReadMarkerListener = listener;
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
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
                readMarkerView.setAnimation(animation);
            }

            final Handler uiHandler = new Handler(Looper.getMainLooper());

            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (readMarkerView != null && readMarkerView.getAnimation() != null) {
                        readMarkerView.setVisibility(View.VISIBLE);
                        readMarkerView.getAnimation().start();

                        // onAnimationEnd does not seem being called when
                        // NotifyDataSetChanged is called during the animation.
                        // This issue is easily reproducable on an Android 7.1 device.
                        // So, we ensure that the listener is always called.
                        uiHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (mReadMarkerListener != null) {
                                    mReadMarkerListener.onReadMarkerDisplayed(event, readMarkerView);
                                }
                            }
                        }, readMarkerView.getAnimation().getDuration() + readMarkerView.getAnimation().getStartOffset());

                    } else {
                        // The animation has been cancelled by a notifyDataSetChanged
                        // With the membership events merge, it will happen more often than before
                        // because many new back paginate will be required to fill the screen.
                        if (mReadMarkerListener != null) {
                            mReadMarkerListener.onReadMarkerDisplayed(event, readMarkerView);
                        }
                    }
                }
            });
        }
    }

    /**
     * Tells if the event is the mReadMarkerEventId one.
     *
     * @param event the event to test
     * @return true if the event is the mReadMarkerEventId one.
     */
    private boolean isReadMarkedEvent(Event event) {
        // if the read marked event is hidden and the event is a merged one
        if ((null != mReadMarkerEventId) && (mHiddenEventIds.contains(mReadMarkerEventId) && (event instanceof EventGroup))) {
            // check it is contains in it
            return ((EventGroup) event).contains(mReadMarkerEventId);
        }

        return event.eventId.equals(mReadMarkerEventId);
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
                    && isReadMarkedEvent(event) && !mIsPreviewMode && !mIsSearchMode
                    && (!mReadMarkerEventId.equals(mReadReceiptEventId) || position < getCount() - 1)) {
                Log.d(LOG_TAG, " Display read marker " + event.eventId + " mReadMarkerEventId" + mReadMarkerEventId);
                // Show the read marker
                animateReadMarkerView(event, readMarkerView);
            } else if (View.GONE != readMarkerView.getVisibility()) {
                Log.v(LOG_TAG, "hide read marker");
                readMarkerView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Init the read marker
     *
     * @param convertView      the main view
     * @param row              the message row
     * @param isMergedView     true if the message is merged
     * @param avatarLayoutView the avatar layout
     * @param bodyLayoutView   the body layout
     */
    private void setReadMarker(View convertView, MessageRow row, boolean isMergedView, View avatarLayoutView, View bodyLayoutView) {
        Event event = row.getEvent();

        // search message mode
        View highlightMakerView = convertView.findViewById(R.id.messagesAdapter_highlight_message_marker);
        View readMarkerView = convertView.findViewById(R.id.message_read_marker);

        if (null != highlightMakerView) {
            // align marker view with the message
            ViewGroup.MarginLayoutParams highlightMakerLayout = (ViewGroup.MarginLayoutParams) highlightMakerView.getLayoutParams();
            highlightMakerLayout.setMargins(5, highlightMakerLayout.topMargin, 5, highlightMakerLayout.bottomMargin);

            if (TextUtils.equals(mHighlightedEventId, event.eventId)) {
                if (mIsUnreadViewMode) {
                    highlightMakerView.setBackgroundColor(ContextCompat.getColor(mContext, android.R.color.transparent));
                    if (readMarkerView != null) {
                        // Show the read marker
                        animateReadMarkerView(event, readMarkerView);
                    }
                } else {
                    ViewGroup.LayoutParams avatarLayout = avatarLayoutView.getLayoutParams();
                    ViewGroup.MarginLayoutParams bodyLayout = (ViewGroup.MarginLayoutParams) bodyLayoutView.getLayoutParams();

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
    }

    /*
     * *********************************************************************************************
     * Handle message click events
     * *********************************************************************************************
     */

    /**
     * The user taps on the action icon.
     *
     * @param event      the selected event.
     * @param textMsg    the event text
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
        CommonActivityUtils.tintMenuIcons(menu, ThemeUtils.getColor(mContext, R.attr.settings_icon_tint_color));

        // hide entries
        for (int i = 0; i < menu.size(); i++) {
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
                    // need the minimum power level to redact an event
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
                menu.findItem(R.id.ic_action_vector_share).setVisible(!mIsRoomEncrypted);
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
                mSelectedEventId = null;
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

    /*
     * *********************************************************************************************
     *  EventGroups events
     * *********************************************************************************************
     */

    private final List<EventGroup> mEventGroups = new ArrayList<>();

    /**
     * Insert the MessageRow in an EventGroup to the front.
     *
     * @param row the messageRow
     * @return true if the MessageRow has been inserted
     */
    private boolean addToEventGroupToFront(MessageRow row) {
        MessageRow eventGroupRow = null;

        if (supportMessageRowMerge(row)) {
            if ((getCount() > 0) && (getItem(0).getEvent() instanceof EventGroup) && ((EventGroup) getItem(0).getEvent()).canAddRow(row)) {
                eventGroupRow = getItem(0);
            }

            if (null == eventGroupRow) {
                eventGroupRow = new MessageRow(new EventGroup(mHiddenEventIds), null);
                mEventGroups.add((EventGroup) eventGroupRow.getEvent());
                super.insert(eventGroupRow, 0);
                mEventRowMap.put(eventGroupRow.getEvent().eventId, row);
            }

            ((EventGroup) eventGroupRow.getEvent()).addToFront(row);
            updateHighlightedEventId();
        }

        return (null != eventGroupRow);
    }

    /**
     * Add a MessageRow into an EventGroup (if it is possible)
     *
     * @param row the row to added
     */
    private void addToEventGroup(MessageRow row) {
        if (supportMessageRowMerge(row)) {
            MessageRow eventGroupRow = null;

            // search backward the EventGroup event
            for (int i = getCount() - 1; i >= 0; i--) {
                MessageRow curRow = getItem(i);

                if (curRow.getEvent() instanceof EventGroup) {
                    // the event can be added (same day ?)
                    if (((EventGroup) curRow.getEvent()).canAddRow(row)) {
                        eventGroupRow = curRow;
                    }
                    break;
                } else
                    // there is no more room member events
                    if (!TextUtils.equals(curRow.getEvent().getType(), Event.EVENT_TYPE_STATE_ROOM_MEMBER)) {
                        break;
                    }
            }

            if (null == eventGroupRow) {
                eventGroupRow = new MessageRow(new EventGroup(mHiddenEventIds), null);
                super.add(eventGroupRow);
                mEventGroups.add((EventGroup) eventGroupRow.getEvent());
                mEventRowMap.put(eventGroupRow.getEvent().eventId, eventGroupRow);
            }

            ((EventGroup) eventGroupRow.getEvent()).add(row);
            updateHighlightedEventId();
        }
    }

    /**
     * Remove a message row from the known event groups
     *
     * @param row the message row
     * @return true if the message has been removed
     */
    private void removeFromEventGroup(MessageRow row) {
        if (supportMessageRowMerge(row)) {
            String eventId = row.getEvent().eventId;
            for (EventGroup eventGroup : mEventGroups) {
                if (eventGroup.contains(eventId)) {
                    eventGroup.removeByEventId(eventId);

                    if (eventGroup.isEmpty()) {
                        mEventGroups.remove(eventGroup);
                        super.remove(row);
                        updateHighlightedEventId();
                        return;
                    }
                }
            }
        }
    }

    /**
     * Update the highlighted eventId
     */
    private void updateHighlightedEventId() {
        if (null != mSearchedEventId) {
            if (!mEventGroups.isEmpty() && mHiddenEventIds.contains(mSearchedEventId)) {
                for (EventGroup eventGroup : mEventGroups) {
                    if (eventGroup.contains(mSearchedEventId)) {
                        mHighlightedEventId = eventGroup.eventId;
                        return;
                    }
                }
            }
        }

        mHighlightedEventId = mSearchedEventId;
    }

    /**
     * This method is called after a message deletion at position 'position'.
     * It checks and merges if required two EventGroup around the deleted item.
     *
     * @param deletedRow the deleted row
     * @param position   the deleted item position
     */
    private void checkEventGroupsMerge(MessageRow deletedRow, int position) {
        if ((position > 0) && (position < getCount() - 1) && !EventGroup.isSupported(deletedRow)) {
            Event eventBef = getItem(position - 1).getEvent();
            Event eventAfter = getItem(position).getEvent();

            if (TextUtils.equals(eventBef.getType(), Event.EVENT_TYPE_STATE_ROOM_MEMBER) && eventAfter instanceof EventGroup) {
                EventGroup nextEventGroup = (EventGroup) eventAfter;
                EventGroup eventGroupBefore = null;

                for (int i = position - 1; i >= 0; i--) {
                    if (getItem(i).getEvent() instanceof EventGroup) {
                        eventGroupBefore = (EventGroup) getItem(i).getEvent();
                        break;
                    }
                }

                if (null != eventGroupBefore) {
                    List<MessageRow> nextRows = new ArrayList<>(nextEventGroup.getRows());
                    // check if the next EventGroup can be added in the previous Event group.
                    // it might be impossible if the messages were not sent the same days
                    if (eventGroupBefore.canAddRow(nextRows.get(0))) {
                        for (MessageRow rowToAdd : nextRows) {
                            eventGroupBefore.add(rowToAdd);
                        }
                    }

                    MessageRow row = mEventRowMap.get(nextEventGroup.eventId);
                    mEventGroups.remove(nextEventGroup);
                    super.remove(row);

                    updateHighlightedEventId();
                }
            }
        }
    }
}