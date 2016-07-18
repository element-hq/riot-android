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

package im.vector.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.commonsware.cwac.anddown.AndDown;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomEmailInvitation;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.fragments.IconAndTextDialogFragment;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.view.AutoScrollDownListView;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.ViewedRoomTracker;
import im.vector.fragments.VectorMessageListFragment;
import im.vector.services.EventStreamService;
import im.vector.util.NotificationUtils;
import im.vector.util.ResourceUtils;
import im.vector.util.SharedDataItem;
import im.vector.util.SlashComandsParser;
import im.vector.util.VectorRoomMediasSender;
import im.vector.util.VectorUtils;
import im.vector.view.VectorPendingCallView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

/**
 * Displays a single room with messages.
 */
public class VectorRoomActivity extends MXCActionBarActivity implements MatrixMessageListFragment.RoomPreviewDataListener {

    /** the room id (string) **/
    public static final String EXTRA_ROOM_ID = "EXTRA_ROOM_ID";
    /** the event id (universal link management - string) **/
    public static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";
    /** the forwarded data (list of media uris) **/
    public static final String EXTRA_ROOM_INTENT = "EXTRA_ROOM_INTENT";
    /** the room is opened in preview mode (string) **/
    public static final String EXTRA_ROOM_PREVIEW_ID = "EXTRA_ROOM_PREVIEW_ID";
    /** the room alias of the room in preview mode (string) **/
    public static final String EXTRA_ROOM_PREVIEW_ROOM_ALIAS = "EXTRA_ROOM_PREVIEW_ROOM_ALIAS";
    /** expand the room header when the activity is launched (boolean) **/
    public static final String EXTRA_EXPAND_ROOM_HEADER = "EXTRA_EXPAND_ROOM_HEADER";

    // display the room information while joining a room.
    // until the join is done.
    public static final String EXTRA_DEFAULT_NAME = "EXTRA_DEFAULT_NAME";
    public static final String EXTRA_DEFAULT_TOPIC = "EXTRA_DEFAULT_TOPIC";

    private static final boolean SHOW_ACTION_BAR_HEADER = true;
    private static final boolean HIDE_ACTION_BAR_HEADER = false;

    // the room is launched but it expects to start the dedicated call activity
    public static final String EXTRA_START_CALL_ID = "EXTRA_START_CALL_ID";

    private static final String TAG_FRAGMENT_MATRIX_MESSAGE_LIST = "TAG_FRAGMENT_MATRIX_MESSAGE_LIST";
    private static final String TAG_FRAGMENT_ATTACHMENTS_DIALOG = "TAG_FRAGMENT_ATTACHMENTS_DIALOG";
    private static final String TAG_FRAGMENT_CALL_OPTIONS = "TAG_FRAGMENT_CALL_OPTIONS";

    private static final String LOG_TAG = "RoomActivity";
    private static final int TYPING_TIMEOUT_MS = 10000;

    private static final String FIRST_VISIBLE_ROW = "FIRST_VISIBLE_ROW";

    // activity result request code
    private static final int REQUEST_FILES_REQUEST_CODE = 0;
    private static final int TAKE_IMAGE_REQUEST_CODE = 1;
    public static final int GET_MENTION_REQUEST_CODE = 2;
    private static final int REQUEST_ROOM_AVATAR_CODE = 3;

    private static final int KEYBOARD_THRESHOLD_VIEW_SIZE = 1000;

    private static final AndDown mAndDown = new AndDown();

    private VectorMessageListFragment mVectorMessageListFragment;
    private MXSession mSession;
    private Room mRoom;
    private String mMyUserId;
    // the parameter is too big to be sent by the intent
    // so use a static variable to send it
    public static RoomPreviewData sRoomPreviewData = null;
    private String mEventId;
    private String mDefaultRoomName;
    private String mDefaultTopic;

    private MXLatestChatMessageCache mLatestChatMessageCache;

    private ImageButton mSendButton;
    private ImageButton mAttachmentsButton;
    private EditText mEditText;
    private ImageView mAvatarImageView;
    private View mMessageButtonLayout;
    private View mCanNotPostTextView;

    // action bar header
    private android.support.v7.widget.Toolbar mToolbar;
    private TextView mActionBarCustomTitle;
    private TextView mActionBarCustomTopic;
    private ImageView mActionBarCustomArrowImageView;
    private RelativeLayout mRoomHeaderView;
    private TextView mActionBarHeaderRoomName;
    private TextView mActionBarHeaderActiveMembers;
    private TextView mActionBarHeaderRoomTopic;
    private ImageView mActionBarHeaderRoomAvatar;
    private View mActionBarHeaderInviteMemberView;

    // notifications area
    private View mNotificationsArea;
    private View mTypingIcon;
    private View mErrorIcon;
    private TextView mNotificationsMessageTextView;
    private TextView mErrorMessageTextView;
    private String mLatestTypingMessage;

    // room preview
    private View mRoomPreviewLayout;

    private MenuItem mCallMenuItem;
    private MenuItem mResendUnsentMenuItem;
    private MenuItem mResendDeleteMenuItem;

    // medias sending helper
    private VectorRoomMediasSender mVectorRoomMediasSender;

    // pending call
    private VectorPendingCallView mVectorPendingCallView;

    // network events
    private final IMXNetworkEventListener mNetworkEventListener = new IMXNetworkEventListener() {
        @Override
        public void onNetworkConnectionUpdate(boolean isConnected) {
            refreshNotificationsArea();
        }
    };

    private String mCallId = null;

    private static String mLatestTakePictureCameraUri = null; // has to be String not Uri because of Serializable

    // typing event management
    private Timer mTypingTimer = null;
    private TimerTask mTypingTimerTask;
    private long mLastTypingDate = 0;

    // scroll to a dedicated index
    private int mScrollToIndex = -1;

    private boolean mIgnoreTextUpdate = false;

    /**
     * Presence and room preview listeners
     */
    private final MXEventListener mGlobalEventListener = new MXEventListener() {
        @Override
        public void onPresenceUpdate(Event event, User user) {
            // the header displays active members
            updateRoomHeaderMembersStatus();
        }

        @Override
        public void onLeaveRoom(String roomId) {
            // test if the user reject the invitation
            if ((null != sRoomPreviewData) && TextUtils.equals(sRoomPreviewData.getRoomId(), roomId)) {
                Log.d(LOG_TAG, "The room invitation has been declined from another client");
                onDeclined();
            }
        }

        @Override
        public void onJoinRoom(String roomId) {
            // test if the user accepts the invitation
            if ((null != sRoomPreviewData) && TextUtils.equals(sRoomPreviewData.getRoomId(), roomId)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(LOG_TAG, "The room invitation has been accepted from another client");
                        onJoined();
                    }
                });
            }
        }

    };

    /**
     * The room events listener
     */
    private final MXEventListener mRoomEventListener = new MXEventListener() {

        @Override
        public void onLeaveRoom(String roomId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    VectorRoomActivity.this.finish();
                }
            });
        }

        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            VectorRoomActivity.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    // The various events that could possibly change the room title
                    if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                        setTitle();
                        refreshNotificationsArea();
                        updateRoomHeaderMembersStatus();
                    } else if (Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS.equals(event.type)) {
                        checkSendEventStatus();
                    } else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type)) {
                        Log.d(LOG_TAG, "Updating room topic.");
                        RoomState roomState = JsonUtils.toRoomState(event.content);
                        setTopic(roomState.topic);
                    } else if (Event.EVENT_TYPE_TYPING.equals(event.type)) {
                        Log.d(LOG_TAG, "on room typing");
                        onRoomTypings();
                    }
                    // header room specific
                    else if (Event.EVENT_TYPE_STATE_ROOM_AVATAR.equals(event.type)) {
                        Log.d(LOG_TAG, "Event room avatar");
                        updateRoomHeaderAvatar();
                    }

                    if (!VectorApp.isAppInBackground()) {
                        // do not send read receipt for the typing events
                        // they are ephemeral ones.
                        if (!Event.EVENT_TYPE_TYPING.equals(event.type)) {
                            if (null != mRoom) {
                                mRoom.sendReadReceipt(null);
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void onRoomInitialSyncComplete(String roomId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // set general room information
                    mVectorMessageListFragment.onInitialMessagesLoaded();
                    updateActionBarTitleAndTopic();
                }
            });
        }

        @Override
        public void onBingRulesUpdate() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateActionBarTitleAndTopic();
                    mVectorMessageListFragment.onBingRulesUpdate();
                }
            });
        }

        @Override
        public void onSentEvent(Event event) {
            refreshNotificationsArea();
        }

        @Override
        public void onFailedSendingEvent(Event event) {
            refreshNotificationsArea();
        }

    };

    //================================================================================
    // Activity classes
    //================================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_room);

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "onCreate : Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }

        mSession = getSession(intent);

        if (mSession == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }

        String roomId = intent.getStringExtra(EXTRA_ROOM_ID);

        // ensure that the preview mode is really expected
        if (!intent.hasExtra(EXTRA_ROOM_PREVIEW_ID)) {
            sRoomPreviewData = null;
            Matrix.getInstance(this).clearTmpStoresList();
        }

        if (CommonActivityUtils.isGoingToSplash(this, mSession.getMyUserId(), roomId)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen");
            return;
        }

        // bind the widgets of the room header view. The room header view is displayed by
        // clicking on the title of the action bar
        mRoomHeaderView = (RelativeLayout) findViewById(R.id.action_bar_header);
        mActionBarHeaderRoomTopic = (TextView) findViewById(R.id.action_bar_header_room_topic);
        mActionBarHeaderRoomName = (TextView) findViewById(R.id.action_bar_header_room_title);
        mActionBarHeaderActiveMembers = (TextView) findViewById(R.id.action_bar_header_room_members);
        mActionBarHeaderRoomAvatar = (ImageView) mRoomHeaderView.findViewById(R.id.avatar_img);
        mActionBarHeaderInviteMemberView = mRoomHeaderView.findViewById(R.id.action_bar_header_invite_members);
        mRoomPreviewLayout = findViewById(R.id.room_preview_info_layout);
        mVectorPendingCallView = (VectorPendingCallView) findViewById(R.id.room_pending_call_view);

        // hide the header room as soon as the bottom layout (text edit zone) is touched
        findViewById(R.id.room_bottom_layout).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                return false;
            }
        });

        // use a toolbar instead of the actionbar
        // to be able to display an expandable header
        mToolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.room_toolbar);
        this.setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // set the default custom action bar layout,
        // that will be displayed from the custom action bar layout
        setActionBarDefaultCustomLayout();

        mCallId = intent.getStringExtra(EXTRA_START_CALL_ID);
        mEventId = intent.getStringExtra(EXTRA_EVENT_ID);
        mDefaultRoomName = intent.getStringExtra(EXTRA_DEFAULT_NAME);
        mDefaultTopic = intent.getStringExtra(EXTRA_DEFAULT_TOPIC);

        // the user has tapped on the "View" notification button
        if ((null != intent.getAction()) && (intent.getAction().startsWith(NotificationUtils.TAP_TO_VIEW_ACTION))) {
            // remove any pending notifications
            NotificationManager notificationsManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationsManager.cancelAll();
        }

        Log.d(LOG_TAG, "Displaying " + roomId);

        mEditText = (EditText) findViewById(R.id.editText_messageBox);

        // hide the header room as soon as the message input text area is touched
        mEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
            }
        });

        // IME's DONE button is treated as a send action
        mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                int imeActionId = actionId & EditorInfo.IME_MASK_ACTION;

                if (EditorInfo.IME_ACTION_DONE == imeActionId) {
                    sendTextMessage();
                }

                return false;
            }
        });

        mSendButton = (ImageButton) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                sendTextMessage();
            }
        });

        mAttachmentsButton = (ImageButton) findViewById(R.id.button_attachments);
        mAttachmentsButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // hide the header room
                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

                FragmentManager fm = getSupportFragmentManager();
                IconAndTextDialogFragment fragment = (IconAndTextDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_ATTACHMENTS_DIALOG);

                if (fragment != null) {
                    fragment.dismissAllowingStateLoss();
                }

                final Integer[] messages = new Integer[]{
                        R.string.option_send_files,
                        R.string.option_take_photo,
                };

                final Integer[] icons = new Integer[]{
                        R.drawable.ic_material_file,  // R.string.option_send_files
                        R.drawable.ic_material_camera, // R.string.option_take_photo
                };


                fragment = IconAndTextDialogFragment.newInstance(icons, messages, null, ContextCompat.getColor(VectorRoomActivity.this, R.color.vector_text_black_color));
                fragment.setOnClickListener(new IconAndTextDialogFragment.OnItemClickListener() {
                    @Override
                    public void onItemClick(IconAndTextDialogFragment dialogFragment, int position) {
                        Integer selectedVal = messages[position];

                        if (selectedVal == R.string.option_send_files) {
                            VectorRoomActivity.this.launchFileSelectionIntent();
                        } else if (selectedVal == R.string.option_take_photo) {
                            if(CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_TAKE_PHOTO, VectorRoomActivity.this)){
                                launchCamera();
                            }
                        }
                    }
                });

                fragment.show(fm, TAG_FRAGMENT_ATTACHMENTS_DIALOG);
            }
        });

        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (null != mRoom) {
                    MXLatestChatMessageCache latestChatMessageCache = VectorRoomActivity.this.mLatestChatMessageCache;
                    String textInPlace = latestChatMessageCache.getLatestText(VectorRoomActivity.this, mRoom.getRoomId());

                    // check if there is really an update
                    // avoid useless updates (initializations..)
                    if (!mIgnoreTextUpdate && !textInPlace.equals(mEditText.getText().toString())) {
                        latestChatMessageCache.updateLatestMessage(VectorRoomActivity.this, mRoom.getRoomId(), mEditText.getText().toString());
                        handleTypingNotification(mEditText.getText().length() != 0);
                    }

                    manageSendMoreButtons();
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        mVectorPendingCallView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IMXCall call = CallViewActivity.getActiveCall();
                if (null != call) {
                    final Intent intent = new Intent(VectorRoomActivity.this, CallViewActivity.class);
                    intent.putExtra(CallViewActivity.EXTRA_MATRIX_ID, call.getSession().getCredentials().userId);
                    intent.putExtra(CallViewActivity.EXTRA_CALL_ID, call.getCallId());

                    VectorRoomActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            VectorRoomActivity.this.startActivity(intent);
                        }
                    });
                }
            }
        });

        mActionBarHeaderInviteMemberView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchRoomDetails(VectorRoomDetailsActivity.PEOPLE_TAB_INDEX);
            }
        });

        // notifications area
        mNotificationsArea = findViewById(R.id.room_notifications_area);
        mTypingIcon = findViewById(R.id.room_typing_animation);
        mNotificationsMessageTextView = (TextView) findViewById(R.id.room_notification_message);
        mErrorIcon = findViewById(R.id.room_error_icon);
        mErrorMessageTextView = (TextView) findViewById(R.id.room_notification_error_message);
        mMessageButtonLayout = findViewById(R.id.buttons_layout);
        mCanNotPostTextView = findViewById(R.id.room_cannot_post_textview);

        mMyUserId = mSession.getCredentials().userId;

        CommonActivityUtils.resumeEventStream(this);

        mRoom = mSession.getDataHandler().getRoom(roomId, false);

        FragmentManager fm = getSupportFragmentManager();
        mVectorMessageListFragment = (VectorMessageListFragment) fm.findFragmentByTag(TAG_FRAGMENT_MATRIX_MESSAGE_LIST);

        if (mVectorMessageListFragment == null) {
            Log.d(LOG_TAG, "Create VectorMessageListFragment");

            // this fragment displays messages and handles all message logic
            mVectorMessageListFragment = VectorMessageListFragment.newInstance(mMyUserId, roomId, mEventId, (null == sRoomPreviewData) ? null : VectorMessageListFragment.PREVIEW_MODE_READ_ONLY, org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
            fm.beginTransaction().add(R.id.anchor_fragment_messages, mVectorMessageListFragment, TAG_FRAGMENT_MATRIX_MESSAGE_LIST).commit();
        } else {
            Log.d(LOG_TAG, "Reuse VectorMessageListFragment");
        }

        mVectorRoomMediasSender = new VectorRoomMediasSender(this, mVectorMessageListFragment, Matrix.getInstance(this).getMediasCache());
        mVectorRoomMediasSender.onRestoreInstanceState(savedInstanceState);

        manageRoomPreview();

        addRoomHeaderClickListeners();

        // in timeline mode (i.e search in the forward and backward room history)
        // or in room preview mode
        // the edition items are not displayed
        if (!TextUtils.isEmpty(mEventId) || (null != sRoomPreviewData)) {
            mNotificationsArea.setVisibility(View.GONE);
            findViewById(R.id.bottom_separator).setVisibility(View.GONE);
            findViewById(R.id.room_notification_separator).setVisibility(View.GONE);
            findViewById(R.id.room_notifications_area).setVisibility(View.GONE);

            View v = findViewById(R.id.room_bottom_layout);
            ViewGroup.LayoutParams params = v.getLayoutParams();
            params.height = 0;
            v.setLayoutParams(params);
        }

        mLatestChatMessageCache = Matrix.getInstance(this).getDefaultLatestChatMessageCache();

        // some medias must be sent while opening the chat
        if (intent.hasExtra(EXTRA_ROOM_INTENT)) {
            final Intent mediaIntent = intent.getParcelableExtra(EXTRA_ROOM_INTENT);

            // sanity check
            if (null != mediaIntent) {
                mEditText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        sendMediasIntent(mediaIntent);
                    }
                }, 1000);
            }
        }

        View avatarLayout = findViewById(R.id.room_self_avatar);

        if (null != avatarLayout) {
            mAvatarImageView = (ImageView) avatarLayout.findViewById(R.id.avatar_img);
        }

        refreshSelfAvatar();

        // in case a "Send as" dialog was in progress when the activity was destroyed (life cycle)
        mVectorRoomMediasSender.resumeResizeMediaAndSend();

        // header visibility has launched
        enableActionBarHeader(intent.getBooleanExtra(EXTRA_EXPAND_ROOM_HEADER, false) ? SHOW_ACTION_BAR_HEADER : HIDE_ACTION_BAR_HEADER);

        // the both flags are only used once
        intent.removeExtra(EXTRA_EXPAND_ROOM_HEADER);

        Log.d(LOG_TAG, "End of create");
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);

        mVectorRoomMediasSender.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putInt(FIRST_VISIBLE_ROW, mVectorMessageListFragment.mMessageListView.getFirstVisiblePosition());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        // the listView will be refreshed so the offset might be lost.
        mScrollToIndex = savedInstanceState.getInt(FIRST_VISIBLE_ROW, -1);
    }

    @Override
    public void onDestroy() {
        if (null != mVectorMessageListFragment) {
            mVectorMessageListFragment.onDestroy();
        }

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // warn other member that the typing is ended
        cancelTypingNotification();

        if (null != mRoom) {
            // listen for room name or topic changes
            mRoom.removeEventListener(mRoomEventListener);
        }

        Matrix.getInstance(this).removeNetworkEventListener(mNetworkEventListener);

        if (mSession.isAlive()) {
            // GA reports a null dataHandler instance event if it seems impossible
            if (null != mSession.getDataHandler()) {
                mSession.getDataHandler().removeListener(mGlobalEventListener);
            }
        }
    }

    @Override
    public void finish() {
        super.finish();

        // do not reset ViewedRoomTracker in onPause
        // else the messages received while the application is in background
        // are marked as unread in the home/recents activity.
        // Assume that the finish method is the right place to manage it.
        ViewedRoomTracker.getInstance().setViewedRoomId(null);
        ViewedRoomTracker.getInstance().setMatrixId(null);
    }

    @Override
    protected void onResume() {
        Log.d(LOG_TAG, "++ Resume the activity");
        super.onResume();

        ViewedRoomTracker.getInstance().setMatrixId(mSession.getCredentials().userId);

        if (null != mRoom) {
            ViewedRoomTracker.getInstance().setViewedRoomId(mRoom.getRoomId());

            // check if the room has been left from another client.
            if (mRoom.isReady()) {
                if ((null == mRoom.getMember(mMyUserId)) || !mSession.getDataHandler().doesRoomExist(mRoom.getRoomId())) {
                    VectorRoomActivity.this.finish();
                    return;
                }
            }

            // listen for room name or topic changes
            mRoom.addEventListener(mRoomEventListener);
        }

        mSession.getDataHandler().addListener(mGlobalEventListener);

        Matrix.getInstance(this).addNetworkEventListener(mNetworkEventListener);

        if (null != mRoom) {
            EventStreamService.cancelNotificationsForRoomId(mSession.getCredentials().userId, mRoom.getRoomId());
        }

        if (null != mRoom) {
            // reset the unread messages counter
            mRoom.sendReadReceipt(null);

            String cachedText = Matrix.getInstance(this).getDefaultLatestChatMessageCache().getLatestText(this, mRoom.getRoomId());

            if (!cachedText.equals(mEditText.getText().toString())) {
                mIgnoreTextUpdate = true;
                mEditText.setText("");
                mEditText.append(cachedText);
                mIgnoreTextUpdate = false;
            }
        }

        manageSendMoreButtons();

        updateActionBarTitleAndTopic();

        refreshNotificationsArea();

        updateRoomHeaderMembersStatus();

        checkSendEventStatus();

        // refresh the UI : the timezone could have been updated
        mVectorMessageListFragment.refresh();

        // the device has been rotated
        // so try to keep the same top/left item;
        if (mScrollToIndex > 0) {
            mVectorMessageListFragment.scrollToIndexWhenLoaded(mScrollToIndex);
            mScrollToIndex = -1;
        }

        if (null != mCallId) {
            IMXCall call = CallViewActivity.getActiveCall();

            // can only manage one call instance.
            // either there is no active call or resume the active one
            if ((null == call) || call.getCallId().equals(mCallId)) {
                final Intent intent = new Intent(VectorRoomActivity.this, CallViewActivity.class);
                intent.putExtra(CallViewActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                intent.putExtra(CallViewActivity.EXTRA_CALL_ID, mCallId);

                if (null == call) {
                    intent.putExtra(CallViewActivity.EXTRA_AUTO_ACCEPT, "anything");
                }

                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                VectorRoomActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        VectorRoomActivity.this.startActivity(intent);
                    }
                });

            }

            mCallId = null;
        }

        if (null != mRoom) {
            // check if the room has been left from another activity
            if (mRoom.isLeaving() || !mSession.getDataHandler().doesRoomExist(mRoom.getRoomId())) {

                runOnUiThread(new Runnable() {
                                  @Override
                                  public void run() {
                                      VectorRoomActivity.this.finish();
                                  }
                              }
                );
            }
        }

        // the pending call view is only displayed with "active " room
        if ((null == sRoomPreviewData) && (null == mEventId)) {
            mVectorPendingCallView.checkPendingCall();
        }

        Log.d(LOG_TAG, "-- Resume the activity");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if ((requestCode == REQUEST_FILES_REQUEST_CODE) || (requestCode == TAKE_IMAGE_REQUEST_CODE)) {
                sendMediasIntent(data);
            } else if (requestCode == GET_MENTION_REQUEST_CODE) {
                appendInTextEditor(data.getStringExtra(VectorMemberDetailsActivity.RESULT_MENTION_ID));
            } else if (requestCode == REQUEST_ROOM_AVATAR_CODE) {
                onActivityResultRoomAvatarUpdate(data);
            }
        }
    }

    //================================================================================
    // Menu management
    //================================================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // the application is in a weird state
        // GA : mSession is null
        if (CommonActivityUtils.shouldRestartApp(this) || (null == mSession)) {
            return false;
        }

        // the menu is only displayed when the current activity does not display a timeline search
        if (TextUtils.isEmpty(mEventId) && (null == sRoomPreviewData)) {
            // Inflate the menu; this adds items to the action bar if it is present.
            getMenuInflater().inflate(R.menu.vector_room, menu);

            mCallMenuItem = menu.findItem(R.id.ic_action_call_in_room);
            mResendUnsentMenuItem = menu.findItem(R.id.ic_action_room_resend_unsent);
            mResendDeleteMenuItem = menu.findItem(R.id.ic_action_room_delete_unsent);

            // hide / show the unsent / resend all entries.
            refreshNotificationsArea();
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.ic_action_search_in_room) {
            try {
                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

                // pop to the home activity
                Intent intent = new Intent(VectorRoomActivity.this, VectorRoomMessagesSearchActivity.class);
                intent.putExtra(VectorRoomMessagesSearchActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
                intent.putExtra(VectorRoomMessagesSearchActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                VectorRoomActivity.this.startActivity(intent);
            } catch (Exception e){
                Log.i(LOG_TAG,"## onOptionsItemSelected(): ");
            }
        } else if (id == R.id.ic_action_room_settings) {
            launchRoomDetails(VectorRoomDetailsActivity.PEOPLE_TAB_INDEX);
        } else if (id == R.id.ic_action_room_resend_unsent) {
            mVectorMessageListFragment.resendUnsentMessages();
            refreshNotificationsArea();
        } else if (id == R.id.ic_action_room_delete_unsent) {
            mVectorMessageListFragment.deleteUnsentMessages();
            refreshNotificationsArea();
        } else if (id == R.id.ic_action_call_in_room){
            displayVideoCallIpDialog();
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Start an IP call with the management of the corresponding permissions.
     * According to the IP call, the corresponding permissions are asked: {@link CommonActivityUtils#REQUEST_CODE_PERMISSION_AUDIO_IP_CALL}
     * or {@link CommonActivityUtils#REQUEST_CODE_PERMISSION_VIDEO_IP_CALL}.
     */
    private void displayVideoCallIpDialog() {
        // hide the header room
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        final Integer[] lIcons = new Integer[]{ R.drawable.voice_call_black, R.drawable.video_call_black};
        final Integer[] lTexts = new Integer[]{ R.string.action_voice_call, R.string.action_video_call};

        IconAndTextDialogFragment fragment = IconAndTextDialogFragment.newInstance(lIcons, lTexts);
        fragment.setOnClickListener(new IconAndTextDialogFragment.OnItemClickListener() {
            @Override
            public void onItemClick(IconAndTextDialogFragment dialogFragment, int position) {
                boolean isVideoCall = false;
                int requestCode = CommonActivityUtils.REQUEST_CODE_PERMISSION_AUDIO_IP_CALL;

                if(1 == position){
                    isVideoCall = true;
                    requestCode = CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL;
                }

                if(CommonActivityUtils.checkPermissions(requestCode, VectorRoomActivity.this)){
                    startIpCall(isVideoCall);
                }
            }
        });

        // display the fragment dialog
        fragment.show(getSupportFragmentManager(), TAG_FRAGMENT_CALL_OPTIONS);
    }

    /**
     * Start an IP call: audio call if aIsVideoCall is false or video call if aIsVideoCall
     * is true.
     * @param aIsVideoCall true to video call, false to audio call
     */
    private void startIpCall(boolean aIsVideoCall){
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        // create the call object
        IMXCall call = mSession.mCallsManager.createCallInRoom(mRoom.getRoomId());

        if (null != call) {
            call.setIsVideo(aIsVideoCall);
            call.setRoom(mRoom);
            call.setIsIncoming(false);

            final Intent intent = new Intent(VectorRoomActivity.this, CallViewActivity.class);

            intent.putExtra(CallViewActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
            intent.putExtra(CallViewActivity.EXTRA_CALL_ID, call.getCallId());

            VectorRoomActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    VectorRoomActivity.this.startActivity(intent);
                }
            });
        }
    }

    //================================================================================
    // messages sending
    //================================================================================
    /**
     * Cancels the room selection mode.
     */
    public void cancelSelectionMode() {
        mVectorMessageListFragment.cancelSelectionMode();
    }

    private static final Pattern mHashPattern = Pattern.compile("(#+)[^( |#)]", Pattern.CASE_INSENSITIVE);

    /**
     * The antdown parser does not manage as expected the # to display header.
     * It should only be displayed when there is a pending space after the # char.
     * @param markdownString the text to check.
     * @return the filtered string.
     */
    private static String checkHashes(String markdownString) {
        if (TextUtils.isEmpty(markdownString) || !markdownString.contains("#")) {
            return markdownString;
        }

        // search pattern with starting with # and finishing with # or space
        // replace first character (#+) i.e #
        return mHashPattern.matcher(markdownString).replaceAll("\\\\$0");
    }

    /**
     * Send the editText text.
     */
    private void sendTextMessage() {
        String body = mEditText.getText().toString().trim();

        // markdownToHtml does not manage properly urls with underscores
        // so we replace the urls by a tmp value before parsing it.
        List<String> urls = VectorUtils.listURLs(body);
        List<String> tmpUrlsValue = new ArrayList<>();

        String modifiedBody = body;

        if (urls.size() > 0) {
            // sort by length -> largest before
            Collections.sort(urls, new Comparator<String>() {
                @Override
                public int compare(String str1, String str2) {
                    return str2.length() - str1.length();
                }
            });

            for(String url : urls) {
                String tmpValue = "url" + Math.abs(url.hashCode());

                modifiedBody = modifiedBody.replace(url, tmpValue);
                tmpUrlsValue.add(tmpValue);
            }
        }

        String html = mAndDown.markdownToHtml(checkHashes(modifiedBody));

        if (null != html) {
            for(int index = 0; index < tmpUrlsValue.size(); index++) {
                html = html.replace(tmpUrlsValue.get(index), urls.get(index));
            }

            html = html.trim();

            if (html.startsWith("<p>")) {
                html = html.substring("<p>".length());
            }

            if (html.endsWith("</p>\n")) {
                html = html.substring(0, html.length() - "</p>\n".length());
            } else if (html.endsWith("</p>")) {
                html = html.substring(0, html.length() - "</p>".length());
            }

            if (TextUtils.equals(html, body)) {
                html = null;
            } else {
                // remove the markdowns
                body = Html.fromHtml(html).toString();
            }
        }

        // hide the header room
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        sendMessage(body, html, "org.matrix.custom.html");
        mEditText.setText("");
    }

    /**
     * Send a text message with its formatted format
     * @param body the text message.
     * @param formattedBody the formatted message
     * @param format the message format
     */
    public void sendMessage(String body, String formattedBody, String format) {
        if (!TextUtils.isEmpty(body)) {
            if (!SlashComandsParser.manageSplashCommand(this, mSession, mRoom, body)) {
                cancelSelectionMode();
                mVectorMessageListFragment.sendTextMessage(body, formattedBody, format);
            }
        }
    }

    /**
     * Send an emote in the opened room
     * @param emote the emote
     */
    public void sendEmote(String emote) {
        if (null != mVectorMessageListFragment) {
            mVectorMessageListFragment.sendEmote(emote);
        }
    }

    @SuppressLint("NewApi")
    /**
     * Send the medias defined in the intent.
     * They are listed, checked and sent when it is possible.
     */
    private void sendMediasIntent(final Intent intent) {
        // sanity check
        if ((null == intent) && (null == mLatestTakePictureCameraUri)) {
            return;
        }

        ArrayList<SharedDataItem> sharedDataItems = new ArrayList<>();

        if (null != intent) {
            sharedDataItems = new ArrayList<>(SharedDataItem.listSharedDataItems(intent));
        } else if (null != mLatestTakePictureCameraUri) {
            sharedDataItems.add(new SharedDataItem(Uri.parse(mLatestTakePictureCameraUri)));
            mLatestTakePictureCameraUri = null;
        }

        // check the extras
        if (0 == sharedDataItems.size()) {
            Bundle bundle = intent.getExtras();

            // sanity checks
            if (null != bundle) {
                bundle.setClassLoader(SharedDataItem.class.getClassLoader());

                if (bundle.containsKey(Intent.EXTRA_STREAM)) {
                    try {
                        Object streamUri = bundle.get(Intent.EXTRA_STREAM);

                        if (streamUri instanceof Uri) {
                            sharedDataItems.add(new SharedDataItem((Uri) streamUri));
                        } else if (streamUri instanceof List) {
                            List<Object> streams = (List<Object>)streamUri;

                            for(Object object : streams) {
                                if (object instanceof Uri) {
                                    sharedDataItems.add(new SharedDataItem((Uri) object));
                                } else if (object instanceof SharedDataItem) {
                                    sharedDataItems.add((SharedDataItem)object);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "fail to extract the extra stream");
                    }
                } else if (bundle.containsKey(Intent.EXTRA_TEXT)) {
                    this.sendMessage(bundle.getString(Intent.EXTRA_TEXT), null, null);
                }
            }
        }

        if (0 != sharedDataItems.size()) {
            mVectorRoomMediasSender.sendMedias(sharedDataItems);
        }
    }

    //================================================================================
    // typing
    //================================================================================

    /**
     * send a typing event notification
     * @param isTyping typing param
     */
    private void handleTypingNotification(boolean isTyping) {
        int notificationTimeoutMS = -1;
        if (isTyping) {
            // Check whether a typing event has been already reported to server (We wait for the end of the local timeout before considering this new event)
            if (null != mTypingTimer) {
                // Refresh date of the last observed typing
                System.currentTimeMillis();
                mLastTypingDate = System.currentTimeMillis();
                return;
            }

            int timerTimeoutInMs = TYPING_TIMEOUT_MS;

            if (0 != mLastTypingDate) {
                long lastTypingAge = System.currentTimeMillis() - mLastTypingDate;
                if (lastTypingAge < timerTimeoutInMs) {
                    // Subtract the time interval since last typing from the timer timeout
                    timerTimeoutInMs -= lastTypingAge;
                } else {
                    timerTimeoutInMs = 0;
                }
            } else {
                // Keep date of this typing event
                mLastTypingDate = System.currentTimeMillis();
            }

            if (timerTimeoutInMs > 0) {

                mTypingTimerTask = new TimerTask() {
                    public void run() {
                        synchronized (LOG_TAG) {
                            if (mTypingTimerTask != null){
                                mTypingTimerTask.cancel();
                                mTypingTimerTask = null;
                            }

                            if (mTypingTimer != null) {
                                mTypingTimer.cancel();
                                mTypingTimer = null;
                            }
                            // Post a new typing notification
                            VectorRoomActivity.this.handleTypingNotification(0 != mLastTypingDate);
                        }
                    }
                };

                try {
                    synchronized (LOG_TAG) {
                        mTypingTimer = new Timer();
                        mTypingTimer.schedule(mTypingTimerTask, TYPING_TIMEOUT_MS);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "fails to launch typing timer " + e.getLocalizedMessage());
                }

                // Compute the notification timeout in ms (consider the double of the local typing timeout)
                notificationTimeoutMS = TYPING_TIMEOUT_MS * 2;
            } else {
                // This typing event is too old, we will ignore it
                isTyping = false;
            }
        }
        else {
            // Cancel any typing timer
            if (mTypingTimerTask != null) {
                mTypingTimerTask.cancel();
                mTypingTimerTask = null;
            }

            if (mTypingTimer != null) {
                mTypingTimer.cancel();
                mTypingTimer = null;
            }
            // Reset last typing date
            mLastTypingDate = 0;
        }

        final boolean typingStatus = isTyping;

        mRoom.sendTypingNotification(typingStatus, notificationTimeoutMS, new SimpleApiCallback<Void>(VectorRoomActivity.this) {
            @Override
            public void onSuccess(Void info) {
                // Reset last typing date
                mLastTypingDate = 0;
            }

            @Override
            public void onNetworkError(Exception e) {
                if (mTypingTimerTask != null) {
                    mTypingTimerTask.cancel();
                    mTypingTimerTask = null;
                }

                if (mTypingTimer != null) {
                    mTypingTimer.cancel();
                    mTypingTimer = null;
                }
                // do not send again
                // assume that the typing event is optional
            }
        });
    }

    private void cancelTypingNotification() {
        if (0 != mLastTypingDate) {
            if (mTypingTimerTask != null) {
                mTypingTimerTask.cancel();
                mTypingTimerTask = null;
            }
            if (mTypingTimer != null) {
                mTypingTimer.cancel();
                mTypingTimer = null;
            }

            mLastTypingDate = 0;

            mRoom.sendTypingNotification(false, -1, new SimpleApiCallback<Void>(VectorRoomActivity.this) {
            });
        }
    }

    //================================================================================
    // Actions
    //================================================================================

    /**
     * Update the spinner visibility.
     * @param visibility  the visibility.
     */
    public void setProgressVisibility(int visibility) {
        View progressLayout = findViewById(R.id.main_progress_layout);

        if ((null != progressLayout) && (progressLayout.getVisibility() != visibility)) {
            progressLayout.setVisibility(visibility);
        }
    }

    /**
     * Launch the room details activity with a selected tab.
     * @param selectedTab the selected tab index.
     */
    private void launchRoomDetails(int selectedTab) {
        if ((null != mRoom) && (null != mRoom.getMember(mSession.getMyUserId()))) {
            enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

            // pop to the home activity
            Intent intent = new Intent(VectorRoomActivity.this, VectorRoomDetailsActivity.class);
            intent.putExtra(VectorRoomDetailsActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
            intent.putExtra(VectorRoomDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
            intent.putExtra(VectorRoomDetailsActivity.EXTRA_SELECTED_TAB_ID, selectedTab);
            startActivityForResult(intent, GET_MENTION_REQUEST_CODE);
        }
    }

    /**
     * Launch the files selection intent
     */
    @SuppressLint("NewApi")
    private void launchFileSelectionIntent() {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        fileIntent.setType("*/*");
        startActivityForResult(fileIntent, REQUEST_FILES_REQUEST_CODE);
    }

    /**
     * Launch the camera
     */
    private void launchCamera() {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        Intent intent = new Intent(this, VectorMediasPickerActivity.class);
        startActivityForResult(intent, TAKE_IMAGE_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int aRequestCode, @NonNull String[] aPermissions, @NonNull int[] aGrantResults) {
           if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_TAKE_PHOTO) {
               boolean isCameraPermissionGranted = false;

            for( int i = 0; i < aPermissions.length; i++ ) {
                Log.d(LOG_TAG, "## onRequestPermissionsResult(): "+aPermissions[i]+"="+aGrantResults[i]);

                if( Manifest.permission.CAMERA.equals(aPermissions[i])) {
                    if (PackageManager.PERMISSION_GRANTED == aGrantResults[i]) {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): CAMERA permission granted");
                        isCameraPermissionGranted = true;
                    } else {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): CAMERA permission not granted");
                    }
                }

                if( Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(aPermissions[i])) {
                    if (PackageManager.PERMISSION_GRANTED == aGrantResults[i]) {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): WRITE_EXTERNAL_STORAGE permission granted");
                    } else {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): WRITE_EXTERNAL_STORAGE permission not granted");
                    }
                }
            }

            // Because external storage permission is not mandatory to launch the camera,
            // external storage permission is not tested.
            if(isCameraPermissionGranted){
                launchCamera();
            } else {
                CommonActivityUtils.displayToast(this, getString(R.string.missing_permissions_warning));
            }
        } else if(aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_AUDIO_IP_CALL){
           if( CommonActivityUtils.onPermissionResultAudioIpCall(this, aPermissions, aGrantResults)) {
               startIpCall(false);
           }
        } else if(aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL){
           if( CommonActivityUtils.onPermissionResultVideoIpCall(this, aPermissions, aGrantResults)) {
               startIpCall(true);
           }
        } else {
           Log.w(LOG_TAG, "## onRequestPermissionsResult(): Unknown requestCode =" + aRequestCode);
        }
    }

    /**
     * Display UI buttons according to user input text.
     */
    private void manageSendMoreButtons() {
        boolean hasText = mEditText.getText().length() > 0;

        mSendButton.setVisibility(hasText ? View.VISIBLE : View.GONE);

        mAttachmentsButton.setVisibility(!hasText ? View.VISIBLE : View.GONE);
    }

    /**
     * Refresh the Account avatar
     */
    private void refreshSelfAvatar() {
        // sanity check
        if (null != mAvatarImageView) {
            VectorUtils.loadUserAvatar(this, mSession, mAvatarImageView, mSession.getMyUser());
        }
    }

    /**
     * Insert a text in the message editor.
     * @param text the text to insert.
     */
    public void insertInTextEditor(String text) {
        if (null != text) {
            if (TextUtils.isEmpty(mEditText.getText())) {
                mEditText.append(text + ": ");
            } else {
                mEditText.getText().insert(mEditText.getSelectionStart(), text);
            }
        }
    }

    /**
     * Append a text in the message editor.
     * @param text the text to append
     */
    private void appendInTextEditor(String text) {
        if (null != text) {
            if (TextUtils.isEmpty(mEditText.getText())) {
                mEditText.append(text + ": ");
            } else {
                mEditText.append(text + " ");
            }
        }
    }

    //================================================================================
    // Notifications area management (... is typing and so on)
    //================================================================================

    /**
     * Refresh the notifications area.
     */
    private void refreshNotificationsArea() {
        // sanity check
        // might happen when the application is logged out
        if ((null == mSession.getDataHandler()) || (null == mRoom)) {
            return;
        }

        boolean isAreaVisible = false;
        boolean isTypingIconDisplayed = false;
        boolean isErrorIconDisplayed = false;
        SpannableString notificationsText = null;
        SpannableString errorText = null;
        boolean hasUnsentEvent = false;

        //  no network
        if (!Matrix.getInstance(this).isConnected()) {
            isAreaVisible = true;
            isErrorIconDisplayed = true;
            errorText = new SpannableString(getResources().getString(R.string.room_offline_notification));
            mErrorMessageTextView.setOnClickListener(null);
        } else {
            Collection<Event> undeliveredEvents = mSession.getDataHandler().getStore().getUndeliverableEvents(mRoom.getRoomId());
            if ((null != undeliveredEvents) && (undeliveredEvents.size() > 0)) {
                hasUnsentEvent = true;
                isAreaVisible = true;
                isErrorIconDisplayed = true;

                String part1 = getResources().getString(R.string.room_unsent_messages_notification);
                String part2 = getResources().getString(R.string.room_prompt_resent);

                errorText = new SpannableString(part1 + part2);
                errorText.setSpan(new UnderlineSpan(), part1.length(), part1.length() + part2.length(), 0);

                mErrorMessageTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mVectorMessageListFragment.resendUnsentMessages();
                        refreshNotificationsArea();
                    }
                });
            } else if (!TextUtils.isEmpty(mLatestTypingMessage)) {
                isAreaVisible = true;
                isTypingIconDisplayed = true;
                notificationsText = new SpannableString(mLatestTypingMessage);
            }
        }

        if (TextUtils.isEmpty(mEventId)) {
            mNotificationsArea.setVisibility(isAreaVisible ? View.VISIBLE : View.INVISIBLE);
        }

        // typing
        mTypingIcon.setVisibility(isTypingIconDisplayed? View.VISIBLE : View.INVISIBLE);
        mNotificationsMessageTextView.setText(notificationsText);

        // error
        mErrorIcon.setVisibility(isErrorIconDisplayed? View.VISIBLE : View.INVISIBLE);
        mErrorMessageTextView.setText(errorText);

        //
        if (null != mResendUnsentMenuItem) {
            mResendUnsentMenuItem.setVisible(hasUnsentEvent);
        }

        if (null != mResendDeleteMenuItem) {
            mResendDeleteMenuItem.setVisible(hasUnsentEvent);
        }

        if (null != mCallMenuItem) {
            boolean isCallSupported = mRoom.canPerformCall() && mSession.isVoipCallSupported() && (null == CallViewActivity.getActiveCall());
            mCallMenuItem.setVisible(isCallSupported);
        }
    }

    /**
     * Display the typing status in the notification area.
     */
    private void onRoomTypings() {
        mLatestTypingMessage = null;

        ArrayList<String> typingUsers = mRoom.getTypingUsers();

        if ((null != typingUsers) && (typingUsers.size() > 0)) {
            String myUserId = mSession.getMyUserId();

            // get the room member names
            ArrayList<String> names = new ArrayList<>();

            for(int i = 0; i < typingUsers.size(); i++) {
                RoomMember member = mRoom.getMember(typingUsers.get(i));

                // check if the user is known and not oneself
                if ((null != member) && !TextUtils.equals(myUserId, member.getUserId()) && (null != member.displayname)) {
                    names.add(member.displayname);
                }
            }

            // nothing to display ?
            if (0 == names.size()) {
                mLatestTypingMessage = null;
            } else if (1 == names.size()) {
                mLatestTypingMessage = String.format(this.getString(R.string.room_one_user_is_typing), names.get(0));
            } else if (2 == names.size()) {
                mLatestTypingMessage = String.format(this.getString(R.string.room_two_users_are_typing), names.get(0), names.get(1));
            } else if (names.size() > 2) {
                mLatestTypingMessage = String.format(this.getString(R.string.room_many_users_are_typing), names.get(0), names.get(1));
            }
        }

        refreshNotificationsArea();
    }

    //================================================================================
    // expandable header management command
    //================================================================================

    /**
     * Refresh the collapsed or the expanded headers
     */
    private void updateActionBarTitleAndTopic() {
        setTitle();
        setTopic();
    }

    /**
     * Set the topic
     */
    private void setTopic() {
        String topic = null;

        if (null != mRoom) {
            topic = mRoom.getTopic();
        } else if ((null != sRoomPreviewData) && (null != sRoomPreviewData.getRoomState())) {
            topic = sRoomPreviewData.getRoomState().topic;
        }

        setTopic(topic);
    }

    /**
     * Set the topic.
     * @param aTopicValue the new topic value
     */
    private void setTopic(String aTopicValue){
        // in search mode, the topic is not displayed
        if (!TextUtils.isEmpty(mEventId)) {
            mActionBarCustomTopic.setVisibility(View.GONE);
        } else {
            // update the topic of the room header
            updateRoomHeaderTopic();

            // update the action bar topic anyway
            mActionBarCustomTopic.setText(aTopicValue);

            // set the visibility of topic on the custom action bar only
            // if header room view is gone, otherwise skipp it
            if (View.GONE == mRoomHeaderView.getVisibility()) {
                // topic is only displayed if its content is not empty
                if (TextUtils.isEmpty(aTopicValue)) {
                    mActionBarCustomTopic.setVisibility(View.GONE);
                } else {
                    mActionBarCustomTopic.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    /**
     * Refresh the room avatar.
     */
    private void updateRoomHeaderAvatar() {
        if (null != mRoom) {
            VectorUtils.loadRoomAvatar(this, mSession, mActionBarHeaderRoomAvatar, mRoom);
        } else if (null != sRoomPreviewData) {
            String roomName = sRoomPreviewData.getRoomName();
            if (TextUtils.isEmpty(roomName)) {
                roomName = " ";
            }
            VectorUtils.loadUserAvatar(this, sRoomPreviewData.getSession(), mActionBarHeaderRoomAvatar, sRoomPreviewData.getRoomAvatarUrl(), sRoomPreviewData.getRoomId(), roomName);
        }
    }


    /**
     * Create a custom action bar layout to process the room header view.
     *
     * This action bar layout will contain a title, a topic and an arrow.
     * The arrow is updated (down/up) according to if the room header is
     * displayed or not.
     *
     */
    private void setActionBarDefaultCustomLayout(){
        // binding the widgets of the custom view
        mActionBarCustomTitle = (TextView)findViewById(R.id.room_action_bar_title);
        mActionBarCustomTopic = (TextView)findViewById(R.id.room_action_bar_topic);
        mActionBarCustomArrowImageView = (ImageView)findViewById(R.id.open_chat_header_arrow);

        // custom header
        View headerTextsContainer = findViewById(R.id.header_texts_container);

        // add click listener on custom action bar to display/hide the header view
        mActionBarCustomArrowImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (null != mRoomHeaderView) {
                    if (View.GONE == mRoomHeaderView.getVisibility()) {
                        enableActionBarHeader(SHOW_ACTION_BAR_HEADER);
                    } else {
                        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                    }
                }
            }
        });

        headerTextsContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TextUtils.isEmpty(mEventId) && (null == sRoomPreviewData)) {
                    enableActionBarHeader(SHOW_ACTION_BAR_HEADER);
                }
            }
        });

        // add touch listener on the header view itself
        if (null != mRoomHeaderView) {
            mRoomHeaderView.setOnTouchListener(new View.OnTouchListener() {
                // last position
                private float mStartX;
                private float mStartY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        mStartX = event.getX();
                        mStartY = event.getY();
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        float curX = event.getX();
                        float curY = event.getY();

                        float deltaX = curX - mStartX;
                        float deltaY = curY - mStartY;

                        // swipe up to hide room header
                        if ((Math.abs(deltaY) > Math.abs(deltaX)) && (deltaY < 0)) {
                            enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                        } else {
                            // wait the touch up to display the room settings page
                            launchRoomDetails(VectorRoomDetailsActivity.SETTINGS_TAB_INDEX);
                        }
                    }
                    return true;
                }
            });
        }
    }

    /**
     * Set the title value in the action bar and in the
     * room header layout
     */
    private void setTitle() {
        String titleToApply = mDefaultRoomName;
        if((null != mSession) && (null != mRoom)) {
            titleToApply = VectorUtils.getRoomDisplayname(this, mSession, mRoom);

            if (TextUtils.isEmpty(titleToApply)) {
                titleToApply = mDefaultRoomName;
            }

            // in context mode, add search to the title.
            if (!TextUtils.isEmpty(mEventId)) {
                titleToApply = getResources().getText(R.string.search) + " : " + titleToApply;
            }
        } else if (null != sRoomPreviewData) {
            titleToApply =sRoomPreviewData.getRoomName();
        }

        // set action bar title
        if (null != mActionBarCustomTitle) {
            mActionBarCustomTitle.setText(titleToApply);
        } else {
            setTitle(titleToApply);
        }

        // set title in the room header (no matter if not displayed)
        if (null != mActionBarHeaderRoomName) {
            mActionBarHeaderRoomName.setText(titleToApply);
        }
    }

    /**
     * Update the UI content of the action bar header view
     */
    private void updateActionBarHeaderView() {
        // update room avatar content
        updateRoomHeaderAvatar();

        // update the room name
        if (null != mRoom) {
            mActionBarHeaderRoomName.setText(VectorUtils.getRoomDisplayname(this, mSession, mRoom));
        } else if (null != sRoomPreviewData) {
            mActionBarHeaderRoomName.setText(sRoomPreviewData.getRoomName());
        } else {
            mActionBarHeaderRoomName.setText("");
        }

        // update topic and members status
        updateRoomHeaderTopic();
        updateRoomHeaderMembersStatus();
    }

    private void updateRoomHeaderTopic() {
        if (null != mActionBarCustomTopic) {
            String value = null;

            if (null != mRoom) {
                value = mRoom.isReady() ? mRoom.getTopic() : mDefaultTopic;
            } else if ((null != sRoomPreviewData) && (null != sRoomPreviewData.getRoomState())) {
                value = sRoomPreviewData.getRoomState().topic;
            }

            // if topic value is empty, just hide the topic TextView
            if (TextUtils.isEmpty(value)) {
                mActionBarHeaderRoomTopic.setVisibility(View.GONE);
            } else {
                mActionBarHeaderRoomTopic.setVisibility(View.VISIBLE);
                mActionBarHeaderRoomTopic.setText(value);
            }
        }
    }

    /**
     * Check if the user can send a message in this room
     */
    private void checkSendEventStatus() {
        if (null != mRoom) {
            boolean canSendMessage = true;

            PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

            if (null != powerLevels) {
                canSendMessage = powerLevels.maySendMessage(mMyUserId);
            }

            mEditText.setVisibility(canSendMessage ? View.VISIBLE : View.GONE);
            mMessageButtonLayout.setVisibility(canSendMessage ? View.VISIBLE : View.GONE);
            mCanNotPostTextView.setVisibility(!canSendMessage ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Display the active members count / members count in the expendable header.
     */
    private void updateRoomHeaderMembersStatus() {
        if (null != mActionBarHeaderActiveMembers) {
            // refresh only if the action bar is hidden
            if (mActionBarCustomTitle.getVisibility() == View.GONE) {

                    if ((null != mRoom) || (null != sRoomPreviewData)) {
                        // update the members status: "active members"/"members"
                        int joinedMembersCount = 0;
                        int activeMembersCount = 0;

                        RoomState roomState = (null != sRoomPreviewData) ? sRoomPreviewData.getRoomState() : mRoom.getState();

                        if (null != roomState) {
                            Collection<RoomMember> members = roomState.getMembers();

                            for (RoomMember member : members) {
                                if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
                                    joinedMembersCount++;

                                    User user = mSession.getDataHandler().getStore().getUser(member.getUserId());

                                    if ((null != user) && user.isActive()) {
                                        activeMembersCount++;
                                    }
                                }
                            }

                            // in preview mode, the room state might be a publicRoom
                            // so try to use the public room info.
                            if ((roomState instanceof PublicRoom) && (0 == joinedMembersCount)) {
                                activeMembersCount = joinedMembersCount = ((PublicRoom)roomState).numJoinedMembers;
                            }

                            boolean displayInvite = TextUtils.isEmpty(mEventId) && (null == sRoomPreviewData) && (1 == joinedMembersCount);

                            if (displayInvite) {
                                mActionBarHeaderActiveMembers.setVisibility(View.GONE);
                                mActionBarHeaderInviteMemberView.setVisibility(View.VISIBLE);
                            } else {
                                mActionBarHeaderInviteMemberView.setVisibility(View.GONE);
                                String text;
                                if (null != sRoomPreviewData) {
                                    if (joinedMembersCount == 1) {
                                        text = getResources().getString(R.string.room_title_one_member);
                                    } else {
                                        text = getResources().getString(R.string.room_title_members, joinedMembersCount);
                                    }
                                } else {
                                    text = getString(R.string.room_header_active_members, activeMembersCount, joinedMembersCount);
                                }

                                mActionBarHeaderActiveMembers.setText(text);
                                mActionBarHeaderActiveMembers.setVisibility(View.VISIBLE);
                            }
                        } else {
                            mActionBarHeaderActiveMembers.setVisibility(View.GONE);
                            mActionBarHeaderActiveMembers.setVisibility(View.GONE);
                        }
                    }

             } else {
                mActionBarHeaderActiveMembers.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Show or hide the action bar header view according to aIsHeaderViewDisplayed
     * @param aIsHeaderViewDisplayed true to show the header view, false to hide
     */
    private void enableActionBarHeader(boolean aIsHeaderViewDisplayed) {
        if (SHOW_ACTION_BAR_HEADER == aIsHeaderViewDisplayed) {

            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);

            // hide the name and the topic in the action bar.
            // these items are hidden when the header view is opened
            mActionBarCustomTitle.setVisibility(View.GONE);
            mActionBarCustomTopic.setVisibility(View.GONE);

            // update the UI content of the action bar header
            updateActionBarHeaderView();
            // set the arrow to up
            mActionBarCustomArrowImageView.setImageResource(R.drawable.ic_arrow_drop_up_white);
            // enable the header view to make it visible
            mRoomHeaderView.setVisibility(View.VISIBLE);
            mToolbar.setBackgroundColor(Color.TRANSPARENT);

            // the list automatically scrolls down when its top moves down
            if (mVectorMessageListFragment.mMessageListView instanceof AutoScrollDownListView) {
                ((AutoScrollDownListView)mVectorMessageListFragment.mMessageListView).lockSelectionOnResize();
            }
        } else {
            // hide the room header only if it is displayed
            if (View.VISIBLE == mRoomHeaderView.getVisibility()) {
                // show the name and the topic in the action bar.
                mActionBarCustomTitle.setVisibility(View.VISIBLE);
                // if the topic is empty, do not show it
                if (!TextUtils.isEmpty(mActionBarCustomTopic.getText())) {
                    mActionBarCustomTopic.setVisibility(View.VISIBLE);
                }

                // update title and topic (action bar)
                updateActionBarTitleAndTopic();

                // hide the action bar header view and reset the arrow image (arrow reset to down)
                mActionBarCustomArrowImageView.setImageResource(R.drawable.ic_arrow_drop_down_white);
                mRoomHeaderView.setVisibility(View.GONE);
                mToolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.vector_green_color));
            }
        }
    }

    //================================================================================
    // Room preview management
    //================================================================================

    @Override
    public RoomPreviewData getRoomPreviewData() {
        return sRoomPreviewData;
    }

    /**
     *  Manage the room preview buttons area
     */
    private void manageRoomPreview() {
        if (null != sRoomPreviewData) {
            mRoomPreviewLayout.setVisibility(View.VISIBLE);

            TextView invitationTextView = (TextView)findViewById(R.id.room_preview_invitation_textview);
            TextView subInvitationTextView = (TextView)findViewById(R.id.room_preview_subinvitation_textview);

            Button joinButton = (Button)findViewById(R.id.button_join_room);
            Button declineButton = (Button)findViewById(R.id.button_decline);

            final RoomEmailInvitation roomEmailInvitation = sRoomPreviewData.getRoomEmailInvitation();

            String roomName = sRoomPreviewData.getRoomName();
            if (TextUtils.isEmpty(roomName)) {
                roomName = " ";
            }

            Log.d(LOG_TAG, "Preview the room " + sRoomPreviewData.getRoomId());


            // if the room already exists
            if (null != mRoom) {
                Log.d(LOG_TAG, "manageRoomPreview : The room is known");

                String inviter = "";

                if (null != roomEmailInvitation) {
                    inviter = roomEmailInvitation.inviterName;
                }

                if (TextUtils.isEmpty(inviter)) {
                    Collection<RoomMember> members = mRoom.getActiveMembers();
                    for (RoomMember member : members) {
                        if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
                            inviter = TextUtils.isEmpty(member.displayname) ? member.getUserId() : member.displayname;
                        }
                    }
                }

                invitationTextView.setText(getResources().getString(R.string.room_preview_invitation_format, inviter));

                declineButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(LOG_TAG, "The user clicked on decline.");

                        setProgressVisibility(View.VISIBLE);

                        mRoom.leave(new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void info) {
                                Log.d(LOG_TAG, "The invitation is rejected");
                                onDeclined();
                            }

                            private void onError(String errorMessage) {
                                Log.d(LOG_TAG, "The invitation rejection failed " + errorMessage);
                                CommonActivityUtils.displayToast(VectorRoomActivity.this, errorMessage);
                                setProgressVisibility(View.GONE);
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                onError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                onError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                onError(e.getLocalizedMessage());
                            }
                        });
                    }
                });

            } else {
                if ((null != roomEmailInvitation) && !TextUtils.isEmpty(roomEmailInvitation.email)) {
                    invitationTextView.setText(getResources().getString(R.string.room_preview_invitation_format, roomEmailInvitation.inviterName));
                    subInvitationTextView.setText(getResources().getString(R.string.room_preview_unlinked_email_warning, roomEmailInvitation.email));
                } else {
                    invitationTextView.setText(getResources().getString(R.string.room_preview_try_join_an_unknown_room, TextUtils.isEmpty(sRoomPreviewData.getRoomName()) ? getResources().getString(R.string.room_preview_try_join_an_unknown_room_default) : roomName));

                    // the room preview has some messages
                    if ((null != sRoomPreviewData.getRoomResponse()) && (null != sRoomPreviewData.getRoomResponse().messages)) {
                        subInvitationTextView.setText(getResources().getString(R.string.room_preview_room_interactions_disabled));
                    }
                }

                declineButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(LOG_TAG, "The invitation is declined (unknown room)");

                        sRoomPreviewData = null;
                        VectorRoomActivity.this.finish();
                    }
                });
            }

            joinButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(LOG_TAG, "The user clicked on Join.");

                    Room room = sRoomPreviewData.getSession().getDataHandler().getRoom(sRoomPreviewData.getRoomId());

                    String signUrl = null;

                    if (null != roomEmailInvitation) {
                        signUrl = roomEmailInvitation.signUrl;
                    }

                    setProgressVisibility(View.VISIBLE);

                    room.joinWithThirdPartySigned(sRoomPreviewData.getRoomIdOrAlias(), signUrl, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            onJoined();
                        }

                        private void onError(String errorMessage) {
                            // delete the created room.
                            // it is a temporary room.
                            // having it implies that the user has been invited or joined it.
                            sRoomPreviewData.getSession().getDataHandler().getStore().deleteRoom(sRoomPreviewData.getRoomId());
                            CommonActivityUtils.displayToast(VectorRoomActivity.this, errorMessage);
                            setProgressVisibility(View.GONE);
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            onError(e.getLocalizedMessage());
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            onError(e.getLocalizedMessage());
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            onError(e.getLocalizedMessage());
                        }
                    });

                }
            });

            enableActionBarHeader(SHOW_ACTION_BAR_HEADER);

        } else {
            mRoomPreviewLayout.setVisibility(View.GONE);
        }
    }

    /**
     * The room invitation has been declined
     */
    private void onDeclined() {
        if (null != sRoomPreviewData) {
            VectorRoomActivity.this.finish();
            sRoomPreviewData = null;
        }
    }

    /**
     * the room has been joined
     */
    private void onJoined() {
        if (null != sRoomPreviewData) {
            HashMap<String, Object> params = new HashMap<>();

            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
            params.put(VectorRoomActivity.EXTRA_ROOM_ID, sRoomPreviewData.getRoomId());

            if (null != sRoomPreviewData.getEventId()) {
                params.put(VectorRoomActivity.EXTRA_EVENT_ID, sRoomPreviewData.getEventId());
            }

            // clear the activity stack to home activity
            Intent intent = new Intent(VectorRoomActivity.this, VectorHomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

            intent.putExtra(VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS, params);
            VectorRoomActivity.this.startActivity(intent);

            sRoomPreviewData = null;
        }
    }

    //================================================================================
    // Room header clicks management.
    //================================================================================

    /**
     * Update the avatar from the data provided the medias picker.
     *
     * @param aData the provided data.
     */
    private void onActivityResultRoomAvatarUpdate(final Intent aData) {
        // sanity check
        if (null == mSession) {
            return;
        }

        Uri thumbnailUri = VectorUtils.getThumbnailUriFromIntent(this, aData, mSession.getMediasCache());

        if (null != thumbnailUri) {
            setProgressVisibility(View.VISIBLE);

            // save the bitmap URL on the server
            ResourceUtils.Resource resource = ResourceUtils.openResource(this, thumbnailUri, null);
            if (null != resource) {
                mSession.getContentManager().uploadContent(resource.mContentStream, null, resource.mMimeType, null, new ContentManager.UploadCallback() {
                    @Override
                    public void onUploadStart(String uploadId) {
                    }

                    @Override
                    public void onUploadProgress(String anUploadId, int percentageProgress) {
                    }

                    @Override
                    public void onUploadComplete(final String anUploadId, final ContentResponse uploadResponse, final int serverResponseCode, final String serverErrorMessage) {
                        VectorRoomActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if ((null != uploadResponse) && (null != uploadResponse.contentUri)) {
                                    Log.d(LOG_TAG, "The avatar has been uploaded, update the room avatar");
                                    mRoom.updateAvatarUrl(uploadResponse.contentUri, new ApiCallback<Void>() {

                                        private void onDone(String message) {
                                            if (!TextUtils.isEmpty(message)) {
                                                CommonActivityUtils.displayToast(VectorRoomActivity.this, message);
                                            }

                                            setProgressVisibility(View.GONE);
                                            updateRoomHeaderAvatar();
                                        }

                                        @Override
                                        public void onSuccess(Void info) {
                                            onDone(null);
                                        }

                                        @Override
                                        public void onNetworkError(Exception e) {
                                            onDone(e.getLocalizedMessage());
                                        }

                                        @Override
                                        public void onMatrixError(MatrixError e) {
                                            onDone(e.getLocalizedMessage());
                                        }

                                        @Override
                                        public void onUnexpectedError(Exception e) {
                                            onDone(e.getLocalizedMessage());
                                        }
                                    });
                                } else {
                                    Log.e(LOG_TAG, "Fail to upload the avatar");
                                }
                            }
                        });
                    }
                });
            }
        }
    }

    /**
     * The user clicks on the room title.
     * Assume he wants to update it.
     */
    private void onRoomTitleClick() {
        LayoutInflater inflater = LayoutInflater.from(this);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

        View dialogView = inflater.inflate(R.layout.dialog_text_edittext, null);
        alertDialogBuilder.setView(dialogView);

        TextView titleText = (TextView) dialogView.findViewById(R.id.dialog_title);
        titleText.setText(getResources().getString(R.string.room_info_room_name));

        final EditText textInput = (EditText) dialogView.findViewById(R.id.dialog_edit_text);
        textInput.setText(mRoom.getLiveState().name);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                setProgressVisibility(View.VISIBLE);

                                mRoom.updateName(textInput.getText().toString(), new ApiCallback<Void>() {

                                    private void onDone(String message) {
                                        if (!TextUtils.isEmpty(message)) {
                                            CommonActivityUtils.displayToast(VectorRoomActivity.this, message);
                                        }

                                        setProgressVisibility(View.GONE);
                                        updateActionBarTitleAndTopic();
                                    }

                                    @Override
                                    public void onSuccess(Void info) {
                                        onDone(null);
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        onDone(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onMatrixError(MatrixError e) {
                                        onDone(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onUnexpectedError(Exception e) {
                                        onDone(e.getLocalizedMessage());
                                    }
                                });
                            }
                        })
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    /**
     * The user clicks on the room topic.
     * Assume he wants to update it.
     */
    private void onRoomTopicClick() {
        LayoutInflater inflater = LayoutInflater.from(this);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

        View dialogView = inflater.inflate(R.layout.dialog_text_edittext, null);
        alertDialogBuilder.setView(dialogView);

        TextView titleText = (TextView) dialogView.findViewById(R.id.dialog_title);
        titleText.setText(getResources().getString(R.string.room_info_room_topic));

        final EditText textInput = (EditText) dialogView.findViewById(R.id.dialog_edit_text);
        textInput.setText(mRoom.getLiveState().topic);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                setProgressVisibility(View.VISIBLE);

                                mRoom.updateTopic(textInput.getText().toString(), new ApiCallback<Void>() {

                                    private void onDone(String message) {
                                        if (!TextUtils.isEmpty(message)) {
                                            CommonActivityUtils.displayToast(VectorRoomActivity.this, message);
                                        }

                                        setProgressVisibility(View.GONE);
                                        updateActionBarTitleAndTopic();
                                    }

                                    @Override
                                    public void onSuccess(Void info) {
                                        onDone(null);
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        onDone(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onMatrixError(MatrixError e) {
                                        onDone(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onUnexpectedError(Exception e) {
                                        onDone(e.getLocalizedMessage());
                                    }
                                });
                            }
                        })
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    /**
     * Add click management on expanded header
     */
    private void addRoomHeaderClickListeners() {
        // tap on the expanded room avatar
        View roomAvatarView = findViewById(R.id.room_avatar);

        if (null != roomAvatarView) {
            roomAvatarView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // sanity checks : reported by GA
                    if ((null != mRoom) && (null != mRoom.getLiveState())) {
                        boolean canUpdateAvatar = false;

                        PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

                        if (null != powerLevels) {
                            int powerLevel = powerLevels.getUserPowerLevel(mSession.getMyUserId());
                            canUpdateAvatar = powerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_AVATAR);
                        }

                        if (canUpdateAvatar) {
                            Intent intent = new Intent(VectorRoomActivity.this, VectorMediasPickerActivity.class);
                            intent.putExtra(VectorMediasPickerActivity.EXTRA_AVATAR_MODE, true);
                            startActivityForResult(intent, REQUEST_ROOM_AVATAR_CODE);
                        } else {
                            launchRoomDetails(VectorRoomDetailsActivity.SETTINGS_TAB_INDEX);
                        }
                    }
                }
            });
        }

        // tap on the room name to update it
        View titleText = findViewById(R.id.action_bar_header_room_title);

        if (null != titleText) {
            titleText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // sanity checks : reported by GA
                    if ((null != mRoom) && (null != mRoom.getLiveState())) {
                        boolean canUpdateTitle = false;
                        PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

                        if (null != powerLevels) {
                            int powerLevel = powerLevels.getUserPowerLevel(mSession.getMyUserId());
                            canUpdateTitle = powerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_NAME);
                        }

                        if (canUpdateTitle) {
                            onRoomTitleClick();
                        } else {
                            launchRoomDetails(VectorRoomDetailsActivity.SETTINGS_TAB_INDEX);
                        }
                    }
                }
            });
        }

        // tap on the room name to update it
        View topicText = findViewById(R.id.action_bar_header_room_topic);

        if (null != topicText) {
            topicText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // sanity checks : reported by GA
                    if ((null != mRoom) && (null != mRoom.getLiveState())) {
                        boolean canUpdateTopic = false;
                        PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

                        if (null != powerLevels) {
                            int powerLevel = powerLevels.getUserPowerLevel(mSession.getMyUserId());
                            canUpdateTopic = powerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(Event.EVENT_TYPE_STATE_ROOM_NAME);
                        }

                        if (canUpdateTopic) {
                            onRoomTopicClick();
                        } else {
                            launchRoomDetails(VectorRoomDetailsActivity.SETTINGS_TAB_INDEX);
                        }
                    }
                }
            });
        }

        View membersListTextView = findViewById(R.id.action_bar_header_room_members);

        if (null != membersListTextView) {
            membersListTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchRoomDetails(VectorRoomDetailsActivity.PEOPLE_TAB_INDEX);
                }
            });
        }
    }
}


