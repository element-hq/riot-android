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

package im.vector.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.IMXCallListener;
import org.matrix.androidsdk.call.MXCallListener;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomEmailInvitation;
import org.matrix.androidsdk.data.RoomMediaMessage;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.fragments.IconAndTextDialogFragment;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.listeners.MXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.publicroom.PublicRoom;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;
import org.matrix.androidsdk.util.ResourceUtils;
import org.matrix.androidsdk.view.AutoScrollDownListView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.ViewedRoomTracker;
import im.vector.fragments.VectorMessageListFragment;
import im.vector.fragments.VectorUnknownDevicesFragment;
import im.vector.services.EventStreamService;
import im.vector.util.CallsManager;
import im.vector.util.NotificationUtils;
import im.vector.util.PreferencesManager;
import im.vector.util.ReadMarkerManager;
import im.vector.util.SlashComandsParser;
import im.vector.util.ThemeUtils;
import im.vector.util.VectorMarkdownParser;
import im.vector.util.VectorRoomMediasSender;
import im.vector.util.VectorUtils;
import im.vector.view.ActiveWidgetsBanner;
import im.vector.view.VectorAutoCompleteTextView;
import im.vector.view.VectorOngoingConferenceCallView;
import im.vector.view.VectorPendingCallView;
import im.vector.widgets.Widget;
import im.vector.widgets.WidgetsManager;

/**
 * Displays a single room with messages.
 */
public class VectorRoomActivity extends MXCActionBarActivity implements MatrixMessageListFragment.IRoomPreviewDataListener, MatrixMessageListFragment.IEventSendingListener, MatrixMessageListFragment.IOnScrollListener {

    /**
     * the session
     **/
    public static final String EXTRA_MATRIX_ID = MXCActionBarActivity.EXTRA_MATRIX_ID;
    /**
     * the room id (string)
     **/
    public static final String EXTRA_ROOM_ID = "EXTRA_ROOM_ID";
    /**
     * the event id (universal link management - string)
     **/
    public static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";
    /**
     * whether the preview is to display unread messages
     */
    public static final String EXTRA_IS_UNREAD_PREVIEW_MODE = "EXTRA_IS_UNREAD_PREVIEW_MODE";
    /**
     * the forwarded data (list of media uris)
     **/
    public static final String EXTRA_ROOM_INTENT = "EXTRA_ROOM_INTENT";
    /**
     * the room is opened in preview mode (string)
     **/
    public static final String EXTRA_ROOM_PREVIEW_ID = "EXTRA_ROOM_PREVIEW_ID";
    /**
     * the room alias of the room in preview mode (string)
     **/
    public static final String EXTRA_ROOM_PREVIEW_ROOM_ALIAS = "EXTRA_ROOM_PREVIEW_ROOM_ALIAS";
    /**
     * expand the room header when the activity is launched (boolean)
     **/
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

    private static final String LOG_TAG = VectorRoomActivity.class.getSimpleName();
    private static final int TYPING_TIMEOUT_MS = 10000;

    private static final String FIRST_VISIBLE_ROW = "FIRST_VISIBLE_ROW";

    // activity result request code
    private static final int REQUEST_FILES_REQUEST_CODE = 0;
    private static final int TAKE_IMAGE_REQUEST_CODE = 1;
    public static final int GET_MENTION_REQUEST_CODE = 2;
    private static final int REQUEST_ROOM_AVATAR_CODE = 3;
    private static final int INVITE_USER_REQUEST_CODE = 4;
    public static final int UNREAD_PREVIEW_REQUEST_CODE = 5;

    private static final String CAMERA_VALUE_TITLE = "attachment"; // Samsung devices need a filepath to write to or else won't return a Uri (!!!)
    private String mLatestTakePictureCameraUri = null; // has to be String not Uri because of Serializable

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

    private View mSendingMessagesLayout;
    private View mSendButtonLayout;
    private ImageView mSendImageView;
    private VectorAutoCompleteTextView mEditText;
    private ImageView mAvatarImageView;
    private View mCanNotPostTextView;
    private ImageView mE2eImageView;

    // call
    private View mStartCallLayout;
    private View mStopCallLayout;

    // action bar header
    private android.support.v7.widget.Toolbar mToolbar;
    private TextView mActionBarCustomTitle;
    private TextView mActionBarCustomTopic;
    private ImageView mActionBarCustomArrowImageView;
    private RelativeLayout mRoomHeaderView;
    private TextView mActionBarHeaderRoomName;

    private View mActionBarHeaderActiveMembersLayout;
    private TextView mActionBarHeaderActiveMembersTextView;

    private View mActionBarHeaderActiveMembersInviteButton;
    private View mActionBarHeaderActiveMembersListButton;

    private TextView mActionBarHeaderRoomTopic;
    private ImageView mActionBarHeaderRoomAvatar;

    // notifications area
    private View mNotificationsArea;
    private ImageView mNotificationIconImageView;
    private TextView mNotificationTextView;
    private String mLatestTypingMessage;
    private Boolean mIsScrolledToTheBottom;
    private Event mLatestDisplayedEvent; // the event at the bottom of the list

    private ReadMarkerManager mReadMarkerManager;

    // room preview
    private View mRoomPreviewLayout;

    private MenuItem mResendUnsentMenuItem;
    private MenuItem mResendDeleteMenuItem;
    private MenuItem mSearchInRoomMenuItem;
    private MenuItem mUseMatrixAppsMenuItem;

    // medias sending helper
    private VectorRoomMediasSender mVectorRoomMediasSender;

    // pending call
    private VectorPendingCallView mVectorPendingCallView;

    // outgoing call
    private VectorOngoingConferenceCallView mVectorOngoingConferenceCallView;

    // pending active view
    private ActiveWidgetsBanner mActiveWidgetsBanner;

    // network events
    private final IMXNetworkEventListener mNetworkEventListener = new IMXNetworkEventListener() {
        @Override
        public void onNetworkConnectionUpdate(boolean isConnected) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshNotificationsArea();
                    refreshCallButtons(true);
                }
            });
        }
    };

    private String mCallId = null;

    // typing event management
    private Timer mTypingTimer = null;
    private TimerTask mTypingTimerTask;
    private long mLastTypingDate = 0;

    // scroll to a dedicated index
    private int mScrollToIndex = -1;

    private boolean mIgnoreTextUpdate = false;

    // https://github.com/vector-im/vector-android/issues/323
    // on some devices, the toolbar background is set to transparent
    // when an activity is opened from this one.
    // It should not but it does.
    private boolean mIsHeaderViewDisplayed = false;

    // True if we are in preview mode to display unread message
    private boolean mIsUnreadPreviewMode;

    // progress bar to warn that the sync is not yet done
    private View mSyncInProgressView;

    // action to do after requesting the camera permission
    private int mCameraPermissionAction;

    /** **/
    private final ApiCallback<Void> mDirectMessageListener = new SimpleApiCallback<Void>(this) {
        @Override
        public void onMatrixError(MatrixError e) {
            if (MatrixError.FORBIDDEN.equals(e.errcode)) {
                Toast.makeText(VectorRoomActivity.this, e.error, Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onSuccess(Void info) {
        }

        @Override
        public void onNetworkError(Exception e) {
            Toast.makeText(VectorRoomActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onUnexpectedError(Exception e) {
            Toast.makeText(VectorRoomActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    };

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

        @Override
        public void onLiveEventsChunkProcessed(String fromToken, String toToken) {
            mSyncInProgressView.setVisibility(View.GONE);
        }
    };

    /**
     * The room events listener
     */
    private final MXEventListener mRoomEventListener = new MXEventListener() {
        @Override
        public void onRoomFlush(String roomId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateActionBarTitleAndTopic();
                    updateRoomHeaderMembersStatus();
                    updateRoomHeaderAvatar();
                }
            });
        }

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
                    String eventType = event.getType();

                    // The various events that could possibly change the room title
                    if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(eventType)
                            || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(eventType)
                            || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType)) {
                        setTitle();
                        updateRoomHeaderMembersStatus();
                        updateRoomHeaderAvatar();
                    } else if (Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS.equals(eventType)) {
                        checkSendEventStatus();
                    } else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(eventType)) {
                        Log.d(LOG_TAG, "Updating room topic.");
                        RoomState roomState = JsonUtils.toRoomState(event.getContent());
                        setTopic(roomState.topic);
                    } else if (Event.EVENT_TYPE_TYPING.equals(eventType)) {
                        Log.d(LOG_TAG, "on room typing");
                        onRoomTypings();
                    }
                    // header room specific
                    else if (Event.EVENT_TYPE_STATE_ROOM_AVATAR.equals(eventType)) {
                        Log.d(LOG_TAG, "Event room avatar");
                        updateRoomHeaderAvatar();
                    } else if (Event.EVENT_TYPE_MESSAGE_ENCRYPTION.equals(eventType)) {
                        boolean canSendEncryptedEvent = mRoom.isEncrypted() && mSession.isCryptoEnabled();
                        mE2eImageView.setImageResource(canSendEncryptedEvent ? R.drawable.e2e_verified : R.drawable.e2e_unencrypted);
                        mVectorMessageListFragment.setIsRoomEncrypted(mRoom.isEncrypted());
                    }

                    if (!VectorApp.isAppInBackground()) {
                        // do not send read receipt for the typing events
                        // they are ephemeral ones.
                        if (!Event.EVENT_TYPE_TYPING.equals(eventType)) {
                            if (null != mRoom) {
                                refreshNotificationsArea();
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
        public void onEventSentStateUpdated(Event event) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshNotificationsArea();
                }
            });
        }

        @Override
        public void onEventSent(Event event, String prevEventId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshNotificationsArea();
                }
            });
        }

        @Override
        public void onReceiptEvent(String roomId, List<String> senderIds) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshNotificationsArea();
                }
            });
        }

        @Override
        public void onReadMarkerEvent(String roomId) {
            if (mReadMarkerManager != null) {
                mReadMarkerManager.onReadMarkerChanged(roomId);
            }
        }
    };

    private final IMXCallListener mCallListener = new MXCallListener() {
        @Override
        public void onCallError(String error) {
            refreshCallButtons(true);
        }

        @Override
        public void onCallAnsweredElsewhere() {
            refreshCallButtons(true);
        }

        @Override
        public void onCallEnd(final int aReasonId) {
            refreshCallButtons(true);
        }

        @Override
        public void onPreviewSizeChanged(int width, int height) {
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

        final Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }

        mSession = MXCActionBarActivity.getSession(this, intent);

        if ((mSession == null) || !mSession.isAlive()) {
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

        //setDragEdge(SwipeBackLayout.DragEdge.LEFT);

        // bind the widgets of the room header view. The room header view is displayed by
        // clicking on the title of the action bar
        mRoomHeaderView = findViewById(R.id.action_bar_header);
        mActionBarHeaderRoomTopic = findViewById(R.id.action_bar_header_room_topic);
        mActionBarHeaderRoomName = findViewById(R.id.action_bar_header_room_title);

        mActionBarHeaderActiveMembersLayout = findViewById(R.id.action_bar_header_room_members_layout);
        mActionBarHeaderActiveMembersTextView = findViewById(R.id.action_bar_header_room_members_text_view);
        mActionBarHeaderActiveMembersListButton = findViewById(R.id.action_bar_header_room_members_settings_view);
        mActionBarHeaderActiveMembersInviteButton = findViewById(R.id.action_bar_header_room_members_invite_view);
        mActionBarHeaderRoomAvatar = mRoomHeaderView.findViewById(R.id.avatar_img);
        mRoomPreviewLayout = findViewById(R.id.room_preview_info_layout);
        mVectorPendingCallView = findViewById(R.id.room_pending_call_view);
        mVectorOngoingConferenceCallView = findViewById(R.id.room_ongoing_conference_call_view);
        mActiveWidgetsBanner = findViewById(R.id.room_pending_widgets_view);
        mE2eImageView = findViewById(R.id.room_encrypted_image_view);
        mSyncInProgressView = findViewById(R.id.room_sync_in_progress);

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
        mToolbar = findViewById(R.id.room_toolbar);
        setSupportActionBar(mToolbar);

        if (null != getSupportActionBar()) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // set the default custom action bar layout,
        // that will be displayed from the custom action bar layout
        setActionBarDefaultCustomLayout();

        mCallId = intent.getStringExtra(EXTRA_START_CALL_ID);
        mEventId = intent.getStringExtra(EXTRA_EVENT_ID);
        mDefaultRoomName = intent.getStringExtra(EXTRA_DEFAULT_NAME);
        mDefaultTopic = intent.getStringExtra(EXTRA_DEFAULT_TOPIC);
        mIsUnreadPreviewMode = intent.getBooleanExtra(EXTRA_IS_UNREAD_PREVIEW_MODE, false);

        // the user has tapped on the "View" notification button
        if ((null != intent.getAction()) && (intent.getAction().startsWith(NotificationUtils.TAP_TO_VIEW_ACTION))) {
            // remove any pending notifications
            NotificationManager notificationsManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationsManager.cancelAll();
        }

        if (mIsUnreadPreviewMode) {
            Log.d(LOG_TAG, "Displaying " + roomId + " in unread preview mode");
        } else if (!TextUtils.isEmpty(mEventId) || (null != sRoomPreviewData)) {
            Log.d(LOG_TAG, "Displaying " + roomId + " in preview mode");
        } else {
            Log.d(LOG_TAG, "Displaying " + roomId);
        }

        mEditText = findViewById(R.id.editText_messageBox);

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

                if ((null != keyEvent) && !keyEvent.isShiftPressed() && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && getResources().getConfiguration().keyboard != Configuration.KEYBOARD_NOKEYS) {
                    sendTextMessage();
                    return true;
                }

                return false;
            }
        });

        mEditText.setAddColonOnFirstItem(true);

        mSendingMessagesLayout = findViewById(R.id.room_sending_message_layout);
        mSendImageView = findViewById(R.id.room_send_image_view);
        mSendButtonLayout = findViewById(R.id.room_send_layout);
        mSendButtonLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!TextUtils.isEmpty(mEditText.getText())) {
                    sendTextMessage();
                } else {
                    // hide the header room
                    enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

                    FragmentManager fm = getSupportFragmentManager();
                    IconAndTextDialogFragment fragment = (IconAndTextDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_ATTACHMENTS_DIALOG);

                    if (fragment != null) {
                        fragment.dismissAllowingStateLoss();
                    }

                    final Integer[] messages;
                    final Integer[] icons;

                    if (PreferencesManager.useNativeCamera(VectorRoomActivity.this)) {
                        messages  = new Integer[]{
                                R.string.option_send_files,
                                R.string.option_take_photo,
                                R.string.option_take_video,
                        };

                        icons = new Integer[]{
                                R.drawable.ic_material_file,
                                R.drawable.ic_material_camera,
                                R.drawable.ic_material_videocam
                        };
                    } else {
                        messages  = new Integer[]{
                                R.string.option_send_files,
                                R.string.option_take_photo_video
                        };

                        icons = new Integer[]{
                                R.drawable.ic_material_file,  // R.string.option_send_files
                                R.drawable.ic_material_camera, // R.string.option_take_photo
                        };
                    }

                    fragment = IconAndTextDialogFragment.newInstance(icons, messages,
                            ThemeUtils.getColor(VectorRoomActivity.this, R.attr.riot_primary_background_color),
                            ThemeUtils.getColor(VectorRoomActivity.this, R.attr.riot_primary_text_color));
                    fragment.setOnClickListener(new IconAndTextDialogFragment.OnItemClickListener() {
                        @Override
                        public void onItemClick(IconAndTextDialogFragment dialogFragment, int position) {
                            Integer selectedVal = messages[position];

                            if (selectedVal == R.string.option_send_files) {
                                VectorRoomActivity.this.launchFileSelectionIntent();
                            } else if (selectedVal == R.string.option_take_photo_video) {
                                if (CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_TAKE_PHOTO, VectorRoomActivity.this)) {
                                    launchCamera();
                                } else {
                                    mCameraPermissionAction = R.string.option_take_photo_video;
                                }
                            } else if (selectedVal == R.string.option_take_photo) {
                                if (CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_TAKE_PHOTO, VectorRoomActivity.this)) {
                                    launchNativeCamera();
                                } else {
                                    mCameraPermissionAction = R.string.option_take_photo;
                                }
                            } else if (selectedVal == R.string.option_take_video) {
                                if (CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_TAKE_PHOTO, VectorRoomActivity.this)) {
                                    launchNativeVideoRecorder();
                                } else {
                                    mCameraPermissionAction = R.string.option_take_video;
                                }
                            }
                        }
                    });

                    fragment.show(fm, TAG_FRAGMENT_ATTACHMENTS_DIALOG);
                }
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
                    refreshCallButtons(true);
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
                IMXCall call = CallsManager.getSharedInstance().getActiveCall();
                if (null != call) {
                    final Intent intent = new Intent(VectorRoomActivity.this, VectorCallViewActivity.class);
                    intent.putExtra(VectorCallViewActivity.EXTRA_MATRIX_ID, call.getSession().getCredentials().userId);
                    intent.putExtra(VectorCallViewActivity.EXTRA_CALL_ID, call.getCallId());

                    VectorRoomActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            VectorRoomActivity.this.startActivity(intent);
                        }
                    });
                } else {
                    // if the call is no more active, just remove the view
                    mVectorPendingCallView.onCallTerminated();
                }
            }
        });

        // notifications area
        mNotificationsArea = findViewById(R.id.room_notifications_area);
        mNotificationIconImageView = mNotificationsArea.findViewById(R.id.room_notification_icon);
        mNotificationTextView = mNotificationsArea.findViewById(R.id.room_notification_message);

        mCanNotPostTextView = findViewById(R.id.room_cannot_post_textview);

        // increase the clickable area to open the keyboard.
        // when there is no text, it is quite small and some user thought the edition was disabled.
        findViewById(R.id.room_sending_message_layout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mEditText.requestFocus()) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(mEditText, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        mStartCallLayout = findViewById(R.id.room_start_call_layout);
        mStartCallLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((null != mRoom) && mRoom.isEncrypted() && (mRoom.getActiveMembers().size() > 2)) {
                    // display the dialog with the info text
                    AlertDialog.Builder permissionsInfoDialog = new AlertDialog.Builder(VectorRoomActivity.this);
                    Resources resource = getResources();
                    permissionsInfoDialog.setMessage(resource.getString(R.string.room_no_conference_call_in_encrypted_rooms));
                    permissionsInfoDialog.setIcon(android.R.drawable.ic_dialog_alert);
                    permissionsInfoDialog.setPositiveButton(resource.getString(R.string.ok), null);
                    permissionsInfoDialog.show();

                } else if (isUserAllowedToStartConfCall()) {
                    if (mRoom.getActiveMembers().size() > 2) {
                        AlertDialog.Builder startConfDialog = new AlertDialog.Builder(VectorRoomActivity.this);
                        startConfDialog.setTitle(R.string.conference_call_warning_title);
                        startConfDialog.setMessage(R.string.conference_call_warning_message);
                        startConfDialog.setIcon(android.R.drawable.ic_dialog_alert);
                        startConfDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (PreferencesManager.useJitsiConfCall(VectorRoomActivity.this)) {
                                    startJitsiCall(true);
                                } else {
                                    displayVideoCallIpDialog();
                                }
                            }
                        });
                        startConfDialog.setNegativeButton(R.string.cancel, null);
                        startConfDialog.show();
                    } else {
                        displayVideoCallIpDialog();
                    }
                } else {
                    displayConfCallNotAllowed();
                }
            }
        });

        mStopCallLayout = findViewById(R.id.room_end_call_layout);
        mStopCallLayout.setOnClickListener(new View.OnClickListener() {
                                               @Override
                                               public void onClick(View v) {
                                                   CallsManager.getSharedInstance().onHangUp(null);
                                               }
                                           }
        );

        findViewById(R.id.room_button_margin_right).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // extend the right side of right button
                // to avoid clicking in the void
                if (mStopCallLayout.getVisibility() == View.VISIBLE) {
                    mStopCallLayout.performClick();
                } else if (mStartCallLayout.getVisibility() == View.VISIBLE) {
                    mStartCallLayout.performClick();
                } else if (mSendButtonLayout.getVisibility() == View.VISIBLE) {
                    mSendButtonLayout.performClick();
                }
            }
        });

        mMyUserId = mSession.getCredentials().userId;

        CommonActivityUtils.resumeEventStream(this);

        mRoom = mSession.getDataHandler().getRoom(roomId, false);

        FragmentManager fm = getSupportFragmentManager();
        mVectorMessageListFragment = (VectorMessageListFragment) fm.findFragmentByTag(TAG_FRAGMENT_MATRIX_MESSAGE_LIST);
        if (mVectorMessageListFragment == null) {
            Log.d(LOG_TAG, "Create VectorMessageListFragment");

            // this fragment displays messages and handles all message logic
            final String previewMode = (null == sRoomPreviewData) ? (mIsUnreadPreviewMode
                    ? VectorMessageListFragment.PREVIEW_MODE_UNREAD_MESSAGE : null) : VectorMessageListFragment.PREVIEW_MODE_READ_ONLY;
            mVectorMessageListFragment = VectorMessageListFragment.newInstance(mMyUserId, roomId, mEventId,
                    previewMode,
                    org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
            fm.beginTransaction().add(R.id.anchor_fragment_messages, mVectorMessageListFragment, TAG_FRAGMENT_MATRIX_MESSAGE_LIST).commit();
        } else {
            Log.d(LOG_TAG, "Reuse VectorMessageListFragment");
        }

        mVectorRoomMediasSender = new VectorRoomMediasSender(this, mVectorMessageListFragment, Matrix.getInstance(this).getMediasCache());

        manageRoomPreview();

        addRoomHeaderClickListeners();

        // in timeline mode (i.e search in the forward and backward room history)
        // or in room preview mode
        // the edition items are not displayed
        if ((!TextUtils.isEmpty(mEventId) || (null != sRoomPreviewData))) {
            if (!mIsUnreadPreviewMode) {
                mNotificationsArea.setVisibility(View.GONE);
                findViewById(R.id.bottom_separator).setVisibility(View.GONE);
                findViewById(R.id.room_notification_separator).setVisibility(View.GONE);
                findViewById(R.id.room_notifications_area).setVisibility(View.GONE);
            }

            View v = findViewById(R.id.room_bottom_layout);
            ViewGroup.LayoutParams params = v.getLayoutParams();
            params.height = 0;
            v.setLayoutParams(params);
        }

        mLatestChatMessageCache = Matrix.getInstance(this).getDefaultLatestChatMessageCache();

        // some medias must be sent while opening the chat
        if (intent.hasExtra(EXTRA_ROOM_INTENT)) {
            // fix issue #1276
            // if there is a saved instance, it means that onSaveInstanceState has been called.
            // theses parameters must only be used at activity creation.
            // The activity might have been created after being killed by android while the application is in background
            if (null == savedInstanceState) {
                final Intent mediaIntent = intent.getParcelableExtra(EXTRA_ROOM_INTENT);

                // sanity check
                if (null != mediaIntent) {
                    mEditText.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            intent.removeExtra(EXTRA_ROOM_INTENT);
                            sendMediasIntent(mediaIntent);
                        }
                    }, 1000);
                }
            } else {
                intent.removeExtra(EXTRA_ROOM_INTENT);
                Log.e(LOG_TAG, "## onCreate() : ignore EXTRA_ROOM_INTENT because savedInstanceState != null");
            }
        }

        if (PreferencesManager.useMatrixApps(this)) {
            mActiveWidgetsBanner.initRoomInfo(mSession, mRoom);
        }
        mActiveWidgetsBanner.setOnUpdateListener(new ActiveWidgetsBanner.onUpdateListener() {
            @Override
            public void onCloseWidgetClick(final Widget widget) {

                new AlertDialog.Builder(VectorRoomActivity.this)
                        .setMessage(R.string.widget_delete_message_confirmation)
                        .setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                setProgressVisibility(View.VISIBLE);

                                WidgetsManager.getSharedInstance().closeWidget(mSession, mRoom, widget.getWidgetId(), new ApiCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void info) {
                                        setProgressVisibility(View.GONE);
                                    }

                                    private void onError(String errorMessage) {
                                        CommonActivityUtils.displayToast(VectorRoomActivity.this, errorMessage);
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
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create()
                        .show();
            }

            @Override
            public void onActiveWidgetsListUpdate() {
                // something todo ?
            }

            private void displayWidget(Widget widget) {
                final Intent intent = new Intent(VectorRoomActivity.this, WidgetActivity.class);
                intent.putExtra(WidgetActivity.EXTRA_WIDGET_ID, widget);
                VectorRoomActivity.this.startActivity(intent);
            }

            @Override
            public void onClick(final List<Widget> widgets) {
                if (widgets.size() == 1) {
                    displayWidget(widgets.get(0));
                } else if (widgets.size() > 1) {
                    List<CharSequence> widgetNames = new ArrayList<>();
                    CharSequence[] CharSequences = new CharSequence[widgetNames.size()];

                    for (Widget widget : widgets) {
                        widgetNames.add(widget.getHumanName());
                    }

                    new AlertDialog.Builder(VectorRoomActivity.this)
                            .setSingleChoiceItems(widgetNames.toArray(CharSequences), 0, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int n) {
                                    d.cancel();
                                    displayWidget(widgets.get(n));
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                }
            }
        });

        mVectorOngoingConferenceCallView.initRoomInfo(mSession, mRoom);
        mVectorOngoingConferenceCallView.setCallClickListener(new VectorOngoingConferenceCallView.ICallClickListener() {
            private void startCall(boolean isVideo) {
                if (CommonActivityUtils.checkPermissions(isVideo ? CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL : CommonActivityUtils.REQUEST_CODE_PERMISSION_AUDIO_IP_CALL,
                        VectorRoomActivity.this)) {
                    startIpCall(false, isVideo);
                }
            }

            private void onCallClick(Widget widget, boolean isVideo) {
                if (null != widget) {
                    launchJitsiActivity(widget, isVideo);
                } else {
                    startCall(isVideo);
                }
            }

            @Override
            public void onVoiceCallClick(Widget widget) {
                onCallClick(widget, false);
            }

            @Override
            public void onVideoCallClick(Widget widget) {
                onCallClick(widget, true);
            }

            @Override
            public void onCloseWidgetClick(Widget widget) {
                setProgressVisibility(View.VISIBLE);

                WidgetsManager.getSharedInstance().closeWidget(mSession, mRoom, widget.getWidgetId(), new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        setProgressVisibility(View.GONE);
                    }

                    private void onError(String errorMessage) {
                        CommonActivityUtils.displayToast(VectorRoomActivity.this, errorMessage);
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

            @Override
            public void onActiveWidgetUpdate() {
                refreshCallButtons(false);
            }
        });

        View avatarLayout = findViewById(R.id.room_self_avatar);

        if (null != avatarLayout) {
            mAvatarImageView = avatarLayout.findViewById(R.id.avatar_img);
        }

        refreshSelfAvatar();

        // in case a "Send as" dialog was in progress when the activity was destroyed (life cycle)
        mVectorRoomMediasSender.resumeResizeMediaAndSend();

        // header visibility has launched
        enableActionBarHeader(intent.getBooleanExtra(EXTRA_EXPAND_ROOM_HEADER, false) ? SHOW_ACTION_BAR_HEADER : HIDE_ACTION_BAR_HEADER);

        // the both flags are only used once
        intent.removeExtra(EXTRA_EXPAND_ROOM_HEADER);

        // Init read marker manager
        if (mIsUnreadPreviewMode || (mRoom != null && mRoom.getLiveTimeLine() != null && mRoom.getLiveTimeLine().isLiveTimeline() && TextUtils.isEmpty(mEventId))) {
            if (null == mRoom) {
                Log.e(LOG_TAG, "## onCreate() : null room");
            } else if (null == mSession.getDataHandler().getStore().getSummary(mRoom.getRoomId())) {
                Log.e(LOG_TAG, "## onCreate() : there is no summary for this room");
            } else {
                mReadMarkerManager = new ReadMarkerManager(this, mVectorMessageListFragment, mSession, mRoom,
                        mIsUnreadPreviewMode ? ReadMarkerManager.PREVIEW_MODE : ReadMarkerManager.LIVE_MODE,
                        findViewById(R.id.jump_to_first_unread));
            }
        }

        Log.d(LOG_TAG, "End of create");
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
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

        if (null != mVectorOngoingConferenceCallView) {
            mVectorOngoingConferenceCallView.setCallClickListener(null);
        }

        if (null != mActiveWidgetsBanner) {
            mActiveWidgetsBanner.setOnUpdateListener(null);
        }

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mReadMarkerManager != null) {
            mReadMarkerManager.onPause();
        }

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

        mVectorOngoingConferenceCallView.onActivityPause();
        mActiveWidgetsBanner.onActivityPause();

        // to have notifications for this room
        ViewedRoomTracker.getInstance().setViewedRoomId(null);
        ViewedRoomTracker.getInstance().setMatrixId(null);
        mEditText.initAutoCompletion(mSession, null);
    }

    @Override
    protected void onResume() {
        Log.d(LOG_TAG, "++ Resume the activity");
        super.onResume();

        ViewedRoomTracker.getInstance().setMatrixId(mSession.getCredentials().userId);

        if (null != mRoom) {
            // check if the room has been left from another client.
            if (mRoom.isReady()) {
                if (null == mRoom.getMember(mMyUserId)) {
                    Log.e(LOG_TAG, "## onResume() : the user is not anymore a member of the room.");
                    finish();
                    return;
                }

                if (!mSession.getDataHandler().doesRoomExist(mRoom.getRoomId())) {
                    Log.e(LOG_TAG, "## onResume() : the user is not anymore a member of the room.");
                    finish();
                    return;
                }

                if (mRoom.isLeaving()) {
                    Log.e(LOG_TAG, "## onResume() : the user is leaving the room.");
                    finish();
                    return;
                }
            }

            // to do not trigger notifications for this room
            // because it is displayed.
            ViewedRoomTracker.getInstance().setViewedRoomId(mRoom.getRoomId());

            // listen for room name or topic changes
            mRoom.addEventListener(mRoomEventListener);

            mEditText.setHint((mRoom.isEncrypted() && mSession.isCryptoEnabled()) ? R.string.room_message_placeholder_encrypted : R.string.room_message_placeholder_not_encrypted);

            mSyncInProgressView.setVisibility(VectorApp.isSessionSyncing(mSession) ? View.VISIBLE : View.GONE);
        } else {
            mSyncInProgressView.setVisibility(View.GONE);
        }

        mSession.getDataHandler().addListener(mGlobalEventListener);

        Matrix.getInstance(this).addNetworkEventListener(mNetworkEventListener);

        if (null != mRoom) {
            EventStreamService.cancelNotificationsForRoomId(mSession.getCredentials().userId, mRoom.getRoomId());
        }

        // sanity checks
        if ((null != mRoom) && (null != Matrix.getInstance(this).getDefaultLatestChatMessageCache())) {
            String cachedText = Matrix.getInstance(this).getDefaultLatestChatMessageCache().getLatestText(this, mRoom.getRoomId());

            if (!cachedText.equals(mEditText.getText().toString())) {
                mIgnoreTextUpdate = true;
                mEditText.setText("");
                mEditText.append(cachedText);
                mIgnoreTextUpdate = false;
            }

            mVectorMessageListFragment.setIsRoomEncrypted(mRoom.isEncrypted());

            boolean canSendEncryptedEvent = mRoom.isEncrypted() && mSession.isCryptoEnabled();
            mE2eImageView.setImageResource(canSendEncryptedEvent ? R.drawable.e2e_verified : R.drawable.e2e_unencrypted);
            mVectorMessageListFragment.setIsRoomEncrypted(mRoom.isEncrypted());
        }

        manageSendMoreButtons();

        updateActionBarTitleAndTopic();

        sendReadReceipt();

        refreshCallButtons(true);

        updateRoomHeaderMembersStatus();

        checkSendEventStatus();

        enableActionBarHeader(mIsHeaderViewDisplayed);

        // refresh the UI : the timezone could have been updated
        mVectorMessageListFragment.refresh();

        // the list automatically scrolls down when its top moves down
        if (mVectorMessageListFragment.mMessageListView instanceof AutoScrollDownListView) {
            ((AutoScrollDownListView) mVectorMessageListFragment.mMessageListView).lockSelectionOnResize();
        }

        // the device has been rotated
        // so try to keep the same top/left item;
        if (mScrollToIndex > 0) {
            mVectorMessageListFragment.scrollToIndexWhenLoaded(mScrollToIndex);
            mScrollToIndex = -1;
        }

        if (null != mCallId) {
            IMXCall call = CallsManager.getSharedInstance().getActiveCall();

            // can only manage one call instance.
            // either there is no active call or resume the active one
            if ((null == call) || call.getCallId().equals(mCallId)) {
                final Intent intent = new Intent(VectorRoomActivity.this, VectorCallViewActivity.class);
                intent.putExtra(VectorCallViewActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                intent.putExtra(VectorCallViewActivity.EXTRA_CALL_ID, mCallId);

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

        // the pending call view is only displayed with "active " room
        if ((null == sRoomPreviewData) && (null == mEventId)) {
            mVectorPendingCallView.checkPendingCall();
            mVectorOngoingConferenceCallView.onActivityResume();
            mActiveWidgetsBanner.onActivityResume();
        }

        displayE2eRoomAlert();

        // init the auto-completion list from the room members
        mEditText.initAutoCompletion(mSession, (null != mRoom) ? mRoom.getRoomId() : null);


        if (mReadMarkerManager != null) {
            mReadMarkerManager.onResume();
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
                insertUserDisplayNameInTextEditor(data.getStringExtra(VectorMemberDetailsActivity.RESULT_MENTION_ID));
            } else if (requestCode == REQUEST_ROOM_AVATAR_CODE) {
                onActivityResultRoomAvatarUpdate(data);
            } else if (requestCode == INVITE_USER_REQUEST_CODE) {
                final List<String> userIds = (List<String>) data.getSerializableExtra(VectorRoomInviteMembersActivity.EXTRA_OUT_SELECTED_USER_IDS);

                if ((null != userIds) && (userIds.size() > 0)) {
                    setProgressVisibility(View.VISIBLE);

                    mRoom.invite(userIds, new ApiCallback<Void>() {

                        private void onDone(String errorMessage) {
                            if (!TextUtils.isEmpty(errorMessage)) {
                                CommonActivityUtils.displayToast(VectorRoomActivity.this, errorMessage);
                            }
                            setProgressVisibility(View.GONE);
                        }

                        @Override
                        public void onSuccess(Void info) {
                            onDone(null);
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            onDone(e.getMessage());
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            onDone(e.getMessage());
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            onDone(e.getMessage());
                        }
                    });
                }
            } else if (requestCode == UNREAD_PREVIEW_REQUEST_CODE) {
                mVectorMessageListFragment.scrollToBottom(0);
            }
        }
    }

    //================================================================================
    // IEventSendingListener
    //================================================================================

    @Override
    public void onMessageSendingSucceeded(Event event) {
        refreshNotificationsArea();
    }

    @Override
    public void onMessageSendingFailed(Event event) {
        refreshNotificationsArea();
    }

    @Override
    public void onMessageRedacted(Event event) {
        refreshNotificationsArea();
    }

    @Override
    public void onUnknownDevices(Event event, MXCryptoError error) {
        refreshNotificationsArea();
        CommonActivityUtils.displayUnknownDevicesDialog(mSession, this, (MXUsersDevicesMap<MXDeviceInfo>) error.mExceptionData, new VectorUnknownDevicesFragment.IUnknownDevicesSendAnywayListener() {
            @Override
            public void onSendAnyway() {
                mVectorMessageListFragment.resendUnsentMessages();
                refreshNotificationsArea();
            }
        });
    }

    //================================================================================
    // IOnScrollListener
    //================================================================================

    /**
     * Send a read receipt to the latest displayed event.
     */
    private void sendReadReceipt() {
        if ((null != mRoom) && (null == sRoomPreviewData)) {
            final Event latestDisplayedEvent = mLatestDisplayedEvent;

            // send the read receipt
            mRoom.sendReadReceipt(latestDisplayedEvent, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    // reported by a rageshake that mLatestDisplayedEvent.evenId was null whereas it was tested before being used
                    // use a final copy of the event
                    try {
                        if (!isFinishing() && (null != latestDisplayedEvent) && mVectorMessageListFragment.getMessageAdapter() != null) {
                            mVectorMessageListFragment.getMessageAdapter().updateReadMarker(mRoom.getReadMarkerEventId(), latestDisplayedEvent.eventId);
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## sendReadReceipt() : failed " + e.getMessage());
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.e(LOG_TAG, "## sendReadReceipt() : failed " + e.getMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.e(LOG_TAG, "## sendReadReceipt() : failed " + e.getMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.e(LOG_TAG, "## sendReadReceipt() : failed " + e.getMessage());
                }
            });
            refreshNotificationsArea();
        }
    }

    @Override
    public void onScroll(int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        final Event eventAtBottom = mVectorMessageListFragment.getEvent(firstVisibleItem + visibleItemCount - 1);
        final Event eventAtTop = mVectorMessageListFragment.getEvent(firstVisibleItem);

        if ((null != eventAtBottom) && ((null == mLatestDisplayedEvent) || !TextUtils.equals(eventAtBottom.eventId, mLatestDisplayedEvent.eventId))) {

            Log.d(LOG_TAG, "## onScroll firstVisibleItem " + firstVisibleItem + " visibleItemCount " + visibleItemCount + " totalItemCount " + totalItemCount);
            mLatestDisplayedEvent = eventAtBottom;

            // don't send receive if the app is in background
            if (!VectorApp.isAppInBackground()) {
                sendReadReceipt();
            } else {
                Log.d(LOG_TAG, "## onScroll : the app is in background");
            }
        }

        if (mReadMarkerManager != null) {
            mReadMarkerManager.onScroll(firstVisibleItem, visibleItemCount, totalItemCount, eventAtTop, eventAtBottom);
        }
    }

    @Override
    public void onScrollStateChanged(int scrollState) {
        if (mReadMarkerManager != null) {
            mReadMarkerManager.onScrollStateChanged(scrollState);
        }
    }

    @Override
    public void onLatestEventDisplay(boolean isDisplayed) {
        // not yet initialized or a new value
        if ((null == mIsScrolledToTheBottom) || (isDisplayed != mIsScrolledToTheBottom)) {
            Log.d(LOG_TAG, "## onLatestEventDisplay : isDisplayed " + isDisplayed);

            if (isDisplayed && (null != mRoom)) {
                mLatestDisplayedEvent = mRoom.getDataHandler().getStore().getLatestEvent(mRoom.getRoomId());
                // ensure that the latest message is displayed
                mRoom.sendReadReceipt();
            }

            mIsScrolledToTheBottom = isDisplayed;
            refreshNotificationsArea();
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
            CommonActivityUtils.tintMenuIcons(menu, ThemeUtils.getColor(this, R.attr.icon_tint_on_dark_action_bar_color));

            mResendUnsentMenuItem = menu.findItem(R.id.ic_action_room_resend_unsent);
            mResendDeleteMenuItem = menu.findItem(R.id.ic_action_room_delete_unsent);
            mSearchInRoomMenuItem = menu.findItem(R.id.ic_action_search_in_room);
            mUseMatrixAppsMenuItem = menu.findItem(R.id.ic_action_matrix_apps);

            // hide / show the unsent / resend all entries.
            refreshNotificationsArea();
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.ic_action_matrix_apps) {
            final Intent intent = new Intent(this, IntegrationManagerActivity.class);
            intent.putExtra(IntegrationManagerActivity.EXTRA_SESSION_ID, mMyUserId);
            intent.putExtra(IntegrationManagerActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
            startActivity(intent);
        } else if (id == R.id.ic_action_search_in_room) {
            try {
                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

                final Intent searchIntent = new Intent(VectorRoomActivity.this, VectorUnifiedSearchActivity.class);
                searchIntent.putExtra(VectorUnifiedSearchActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
                VectorRoomActivity.this.startActivity(searchIntent);

            } catch (Exception e) {
                Log.i(LOG_TAG, "## onOptionsItemSelected(): ");
            }
        } else if (id == R.id.ic_action_room_settings) {
            launchRoomDetails(VectorRoomDetailsActivity.PEOPLE_TAB_INDEX);
        } else if (id == R.id.ic_action_room_resend_unsent) {
            mVectorMessageListFragment.resendUnsentMessages();
            refreshNotificationsArea();
        } else if (id == R.id.ic_action_room_delete_unsent) {
            mVectorMessageListFragment.deleteUnsentEvents();
            refreshNotificationsArea();
        } else if (id == R.id.ic_action_room_leave) {
            if (null != mRoom) {
                Log.d(LOG_TAG, "Leave the room " + mRoom.getRoomId());
                new AlertDialog.Builder(VectorRoomActivity.this)
                        .setTitle(R.string.room_participants_leave_prompt_title)
                        .setMessage(R.string.room_participants_leave_prompt_msg)
                        .setPositiveButton(R.string.leave, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                setProgressVisibility(View.VISIBLE);

                                mRoom.leave(new ApiCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void info) {
                                        Log.d(LOG_TAG, "The room " + mRoom.getRoomId() + " is left");
                                        // close the activity
                                        finish();
                                    }

                                    private void onError(String errorMessage) {
                                        setProgressVisibility(View.GONE);
                                        Log.e(LOG_TAG, "Cannot leave the room " + mRoom.getRoomId() + " : " + errorMessage);
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
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create()
                        .show();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Check if the current user is allowed to perform a conf call.
     * The user power level is checked against the invite power level.
     * <p>To start a conf call, the user needs to invite the CFU to the room.
     *
     * @return true if the user is allowed, false otherwise
     */
    private boolean isUserAllowedToStartConfCall() {
        boolean isAllowed = false;

        if (mRoom.isOngoingConferenceCall()) {
            // if a conf is in progress, the user can join the established conf anyway
            Log.d(LOG_TAG, "## isUserAllowedToStartConfCall(): conference in progress");
            isAllowed = true;
        } else if ((null != mRoom) && (mRoom.getActiveMembers().size() > 2)) {
            PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

            if (null != powerLevels) {
                // to start a conf call, the user MUST have the power to invite someone (CFU)
                isAllowed = powerLevels.getUserPowerLevel(mSession.getMyUserId()) >= powerLevels.invite;
            }
        } else {
            // 1:1 call
            isAllowed = true;
        }

        Log.d(LOG_TAG, "## isUserAllowedToStartConfCall(): isAllowed=" + isAllowed);
        return isAllowed;
    }

    /**
     * Display a dialog box to indicate that the conf call can no be performed.
     * <p>See {@link #isUserAllowedToStartConfCall()}
     */
    private void displayConfCallNotAllowed() {
        // display the dialog with the info text
        AlertDialog.Builder permissionsInfoDialog = new AlertDialog.Builder(VectorRoomActivity.this);
        Resources resource = getResources();

        if ((null != resource)) {
            permissionsInfoDialog.setTitle(resource.getString(R.string.missing_permissions_title_to_start_conf_call));
            permissionsInfoDialog.setMessage(resource.getString(R.string.missing_permissions_to_start_conf_call));

            permissionsInfoDialog.setIcon(android.R.drawable.ic_dialog_alert);
            permissionsInfoDialog.setPositiveButton(resource.getString(R.string.ok), null);
            permissionsInfoDialog.show();
        } else {
            Log.e(LOG_TAG, "## displayConfCallNotAllowed(): impossible to create dialog");
        }
    }

    /**
     * Start an IP call with the management of the corresponding permissions.
     * According to the IP call, the corresponding permissions are asked: {@link CommonActivityUtils#REQUEST_CODE_PERMISSION_AUDIO_IP_CALL}
     * or {@link CommonActivityUtils#REQUEST_CODE_PERMISSION_VIDEO_IP_CALL}.
     */
    private void displayVideoCallIpDialog() {
        // hide the header room
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        final Integer[] lIcons = new Integer[]{R.drawable.voice_call_green, R.drawable.video_call_green};
        final Integer[] lTexts = new Integer[]{R.string.action_voice_call, R.string.action_video_call};

        IconAndTextDialogFragment fragment = IconAndTextDialogFragment.newInstance(lIcons, lTexts,
                ThemeUtils.getColor(this, R.attr.riot_primary_background_color),
                ThemeUtils.getColor(this, R.attr.riot_primary_text_color));
        fragment.setOnClickListener(new IconAndTextDialogFragment.OnItemClickListener() {
            @Override
            public void onItemClick(IconAndTextDialogFragment dialogFragment, int position) {
                boolean isVideoCall = false;
                int requestCode = CommonActivityUtils.REQUEST_CODE_PERMISSION_AUDIO_IP_CALL;

                if (1 == position) {
                    isVideoCall = true;
                    requestCode = CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL;
                }

                if (CommonActivityUtils.checkPermissions(requestCode, VectorRoomActivity.this)) {
                    startIpCall(PreferencesManager.useJitsiConfCall(VectorRoomActivity.this), isVideoCall);
                }
            }
        });

        // display the fragment dialog
        fragment.show(getSupportFragmentManager(), TAG_FRAGMENT_CALL_OPTIONS);
    }

    /**
     * Manage widget
     *
     * @param widget       the widget
     * @param aIsVideoCall true if it is a video call
     */
    private void launchJitsiActivity(Widget widget, boolean aIsVideoCall) {
        final Intent intent = new Intent(VectorRoomActivity.this, JitsiCallActivity.class);
        intent.putExtra(JitsiCallActivity.EXTRA_WIDGET_ID, widget);
        intent.putExtra(JitsiCallActivity.EXTRA_ENABLE_VIDEO, aIsVideoCall);
        VectorRoomActivity.this.startActivity(intent);
    }

    /**
     * Start a jisti call
     *
     * @param aIsVideoCall true if the call is a video one
     */
    private void startJitsiCall(final boolean aIsVideoCall) {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
        setProgressVisibility(View.VISIBLE);

        WidgetsManager.getSharedInstance().createJitsiWidget(mSession, mRoom, aIsVideoCall, new ApiCallback<Widget>() {
            @Override
            public void onSuccess(Widget widget) {
                setProgressVisibility(View.GONE);

                final Intent intent = new Intent(VectorRoomActivity.this, JitsiCallActivity.class);
                intent.putExtra(JitsiCallActivity.EXTRA_WIDGET_ID, widget);
                VectorRoomActivity.this.startActivity(intent);
            }

            private void onError(String errorMessage) {
                setProgressVisibility(View.GONE);
                CommonActivityUtils.displayToast(VectorRoomActivity.this, errorMessage);
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

    /**
     * Start an IP call: audio call if aIsVideoCall is false or video call if aIsVideoCall
     * is true.
     *
     * @param useJitsiCall true to use jitsi calls
     * @param aIsVideoCall true to video call, false to audio call
     */
    private void startIpCall(final boolean useJitsiCall, final boolean aIsVideoCall) {
        if ((mRoom.getActiveMembers().size() > 2) && useJitsiCall) {
            startJitsiCall(aIsVideoCall);
            return;
        }

        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
        setProgressVisibility(View.VISIBLE);

        // create the call object
        mSession.mCallsManager.createCallInRoom(mRoom.getRoomId(), aIsVideoCall, new ApiCallback<IMXCall>() {
            @Override
            public void onSuccess(final IMXCall call) {
                Log.d(LOG_TAG, "## startIpCall(): onSuccess");
                VectorRoomActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setProgressVisibility(View.GONE);

                        final Intent intent = new Intent(VectorRoomActivity.this, VectorCallViewActivity.class);

                        intent.putExtra(VectorCallViewActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                        intent.putExtra(VectorCallViewActivity.EXTRA_CALL_ID, call.getCallId());

                        VectorRoomActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                VectorRoomActivity.this.startActivity(intent);
                            }
                        });
                    }
                });
            }

            private void onError(final String errorMessage) {
                VectorRoomActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setProgressVisibility(View.GONE);
                        Activity activity = VectorRoomActivity.this;
                        CommonActivityUtils.displayToastOnUiThread(activity, activity.getString(R.string.cannot_start_call) + " (" + errorMessage + ")");
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## startIpCall(): onNetworkError Msg=" + e.getMessage());
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## startIpCall(): onMatrixError Msg=" + e.getLocalizedMessage());

                if (e instanceof MXCryptoError) {
                    MXCryptoError cryptoError = (MXCryptoError) e;
                    if (MXCryptoError.UNKNOWN_DEVICES_CODE.equals(cryptoError.errcode)) {
                        setProgressVisibility(View.GONE);
                        CommonActivityUtils.displayUnknownDevicesDialog(mSession, VectorRoomActivity.this, (MXUsersDevicesMap<MXDeviceInfo>) cryptoError.mExceptionData, new VectorUnknownDevicesFragment.IUnknownDevicesSendAnywayListener() {
                            @Override
                            public void onSendAnyway() {
                                startIpCall(useJitsiCall, aIsVideoCall);
                            }
                        });

                        return;
                    }
                }

                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## startIpCall(): onUnexpectedError Msg=" + e.getLocalizedMessage());
                onError(e.getLocalizedMessage());
            }
        });
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

    private boolean mIsMarkDowning;

    /**
     * Send the editText text.
     */
    private void sendTextMessage() {
        if (mIsMarkDowning) {
            return;
        }

        // ensure that a message is not sent twice
        // markdownToHtml seems being slow in some cases
        mSendButtonLayout.setEnabled(false);
        mIsMarkDowning = true;

        VectorApp.markdownToHtml(mEditText.getText().toString().trim(), new VectorMarkdownParser.IVectorMarkdownParserListener() {
            @Override
            public void onMarkdownParsed(final String text, final String HTMLText) {
                VectorRoomActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mSendButtonLayout.setEnabled(true);
                        mIsMarkDowning = false;
                        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                        sendMessage(text, TextUtils.equals(text, HTMLText) ? null : HTMLText, Message.FORMAT_MATRIX_HTML);
                        mEditText.setText("");
                    }
                });
            }
        });
    }

    /**
     * Send a text message with its formatted format
     *
     * @param body          the text message.
     * @param formattedBody the formatted message
     * @param format        the message format
     */
    public void sendMessage(String body, String formattedBody, String format) {
        if (!TextUtils.isEmpty(body)) {
            if (!SlashComandsParser.manageSplashCommand(this, mSession, mRoom, body, formattedBody, format)) {
                cancelSelectionMode();
                mVectorMessageListFragment.sendTextMessage(body, formattedBody, format);
            }
        }
    }

    /**
     * Send an emote in the opened room
     *
     * @param emote the emote
     */
    public void sendEmote(String emote, String formattedEmote, String format) {
        if (null != mVectorMessageListFragment) {
            mVectorMessageListFragment.sendEmote(emote, formattedEmote, format);
        }
    }

    /**
     * Send the medias defined in the intent.
     * They are listed, checked and sent when it is possible.
     */
    @SuppressLint("NewApi")
    private void sendMediasIntent(final Intent intent) {
        // sanity check
        if ((null == intent) && (null == mLatestTakePictureCameraUri)) {
            return;
        }

        ArrayList<RoomMediaMessage> sharedDataItems = new ArrayList<>();

        if (null != intent) {
            sharedDataItems = new ArrayList<>(RoomMediaMessage.listRoomMediaMessages(intent, RoomMediaMessage.class.getClassLoader()));
        }

        if (null != mLatestTakePictureCameraUri) {
            if (0 == sharedDataItems.size()) {
                sharedDataItems.add(new RoomMediaMessage(Uri.parse(mLatestTakePictureCameraUri)));
            }
            mLatestTakePictureCameraUri = null;
        }

        // check the extras
        if ((0 == sharedDataItems.size()) && (null != intent)) {
            Bundle bundle = intent.getExtras();

            // sanity checks
            if (null != bundle) {
                if (bundle.containsKey(Intent.EXTRA_TEXT)) {
                    mEditText.setText(mEditText.getText() + bundle.getString(Intent.EXTRA_TEXT));

                    mEditText.post(new Runnable() {
                        @Override
                        public void run() {
                            mEditText.setSelection(mEditText.getText().length());
                        }
                    });
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
     *
     * @param isTyping typing param
     */
    private void handleTypingNotification(boolean isTyping) {
        // the typing notifications are disabled ?
        if (PreferencesManager.dontSendTypingNotifs(this)) {
            Log.d(LOG_TAG, "##handleTypingNotification() : the typing notifs are disabled");
            return;
        }

        Log.d(LOG_TAG, "##handleTypingNotification() : isTyping " + isTyping);

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

                try {
                    mTypingTimerTask = new TimerTask() {
                        public void run() {
                            synchronized (LOG_TAG) {
                                if (mTypingTimerTask != null) {
                                    mTypingTimerTask.cancel();
                                    mTypingTimerTask = null;
                                }

                                if (mTypingTimer != null) {
                                    mTypingTimer.cancel();
                                    mTypingTimer = null;
                                }

                                Log.d(LOG_TAG, "##handleTypingNotification() : send end of typing");

                                // Post a new typing notification
                                VectorRoomActivity.this.handleTypingNotification(0 != mLastTypingDate);
                            }
                        }
                    };
                } catch (Throwable throwable) {
                    Log.e(LOG_TAG, "## mTypingTimerTask creation failed " + throwable.getMessage());
                    return;
                }

                try {
                    synchronized (LOG_TAG) {
                        mTypingTimer = new Timer();
                        mTypingTimer.schedule(mTypingTimerTask, TYPING_TIMEOUT_MS);
                    }
                } catch (Throwable throwable) {
                    Log.e(LOG_TAG, "fails to launch typing timer " + throwable.getMessage());
                    mTypingTimer = null;
                    mTypingTimerTask = null;
                }

                // Compute the notification timeout in ms (consider the double of the local typing timeout)
                notificationTimeoutMS = TYPING_TIMEOUT_MS * 2;
            } else {
                // This typing event is too old, we will ignore it
                isTyping = false;
            }
        } else {
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
     *
     * @param visibility the visibility.
     */
    public void setProgressVisibility(int visibility) {
        View progressLayout = findViewById(R.id.main_progress_layout);

        if ((null != progressLayout) && (progressLayout.getVisibility() != visibility)) {
            progressLayout.setVisibility(visibility);
        }
    }

    /**
     * Launch the room details activity with a selected tab.
     *
     * @param selectedTab the selected tab index.
     */
    private void launchRoomDetails(int selectedTab) {
        if ((null != mSession) && (null != mRoom) && (null != mRoom.getMember(mSession.getMyUserId()))) {
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
     * Launch the invite people activity
     */
    private void launchInvitePeople() {
        if ((null != mSession) && (null != mRoom)) {
            Intent intent = new Intent(this, VectorRoomInviteMembersActivity.class);
            intent.putExtra(VectorRoomInviteMembersActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
            intent.putExtra(VectorRoomInviteMembersActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
            intent.putExtra(VectorRoomInviteMembersActivity.EXTRA_ADD_CONFIRMATION_DIALOG, true);
            startActivityForResult(intent, INVITE_USER_REQUEST_CODE);
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
    private void launchNativeVideoRecorder() {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        final Intent captureIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        // lowest quality
        captureIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
        startActivityForResult(captureIntent, TAKE_IMAGE_REQUEST_CODE);
    }

    /**
     * Launch the camera
     */
    private void launchNativeCamera() {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        final Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // the following is a fix for buggy 2.x devices
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, CAMERA_VALUE_TITLE + formatter.format(date));
        // The Galaxy S not only requires the name of the file to output the image to, but will also not
        // set the mime type of the picture it just took (!!!). We assume that the Galaxy S takes image/jpegs
        // so the attachment uploader doesn't freak out about there being no mimetype in the content database.
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        Uri dummyUri = null;
        try {
            dummyUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (null == dummyUri) {
                Log.e(LOG_TAG, "Cannot use the external storage media to save image");
            }
        }
        catch (UnsupportedOperationException uoe) {
            Log.e(LOG_TAG, "Unable to insert camera URI into MediaStore.Images.Media.EXTERNAL_CONTENT_URI - no SD card? Attempting to insert into device storage.");
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "Unable to insert camera URI into MediaStore.Images.Media.EXTERNAL_CONTENT_URI. "+e);
        }

        if (null == dummyUri) {
            try {
                dummyUri = getContentResolver().insert(MediaStore.Images.Media.INTERNAL_CONTENT_URI, values);
                if (null == dummyUri) {
                    Log.e(LOG_TAG, "Cannot use the internal storage to save media to save image");
                }

            } catch (Exception e) {
                Log.e(LOG_TAG, "Unable to insert camera URI into internal storage. Giving up. " + e);
            }
        }

        if (dummyUri != null) {
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, dummyUri);
            Log.d(LOG_TAG, "trying to take a photo on " + dummyUri.toString());
        } else {
            Log.d(LOG_TAG, "trying to take a photo with no predefined uri");
        }

        // Store the dummy URI which will be set to a placeholder location. When all is lost on samsung devices,
        // this will point to the data we're looking for.
        // Because Activities tend to use a single MediaProvider for all their intents, this field will only be the
        // *latest* TAKE_PICTURE Uri. This is deemed acceptable as the normal flow is to create the intent then immediately
        // fire it, meaning onActivityResult/getUri will be the next thing called, not another createIntentFor.
        mLatestTakePictureCameraUri = dummyUri == null ? null : dummyUri.toString();

        startActivityForResult(captureIntent, TAKE_IMAGE_REQUEST_CODE);
    }

    /**
     * Launch the camera
     */
    private void launchCamera() {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        Intent intent = new Intent(this, VectorMediasPickerActivity.class);
        intent.putExtra(VectorMediasPickerActivity.EXTRA_VIDEO_RECORDING_MODE, true);
        startActivityForResult(intent, TAKE_IMAGE_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int aRequestCode, @NonNull String[] aPermissions, @NonNull int[] aGrantResults) {
        if (0 == aPermissions.length) {
            Log.e(LOG_TAG, "## onRequestPermissionsResult(): cancelled " + aRequestCode);
        } else if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_ROOM_DETAILS) {
            boolean isCameraPermissionGranted = false;

            for (int i = 0; i < aPermissions.length; i++) {
                Log.d(LOG_TAG, "## onRequestPermissionsResult(): " + aPermissions[i] + "=" + aGrantResults[i]);

                if (Manifest.permission.CAMERA.equals(aPermissions[i])) {
                    if (PackageManager.PERMISSION_GRANTED == aGrantResults[i]) {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): CAMERA permission granted");
                        isCameraPermissionGranted = true;
                    } else {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): CAMERA permission not granted");
                    }
                }
            }

            // the user allows to use to the camera.
            if (isCameraPermissionGranted) {
                Intent intent = new Intent(VectorRoomActivity.this, VectorMediasPickerActivity.class);
                intent.putExtra(VectorMediasPickerActivity.EXTRA_AVATAR_MODE, true);
                startActivityForResult(intent, REQUEST_ROOM_AVATAR_CODE);
            } else {
                launchRoomDetails(VectorRoomDetailsActivity.SETTINGS_TAB_INDEX);
            }
        } else if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_TAKE_PHOTO) {
            boolean isCameraPermissionGranted = false;

            for (int i = 0; i < aPermissions.length; i++) {
                Log.d(LOG_TAG, "## onRequestPermissionsResult(): " + aPermissions[i] + "=" + aGrantResults[i]);

                if (Manifest.permission.CAMERA.equals(aPermissions[i])) {
                    if (PackageManager.PERMISSION_GRANTED == aGrantResults[i]) {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): CAMERA permission granted");
                        isCameraPermissionGranted = true;
                    } else {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): CAMERA permission not granted");
                    }
                }

                if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(aPermissions[i])) {
                    if (PackageManager.PERMISSION_GRANTED == aGrantResults[i]) {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): WRITE_EXTERNAL_STORAGE permission granted");
                    } else {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): WRITE_EXTERNAL_STORAGE permission not granted");
                    }
                }
            }

            // Because external storage permission is not mandatory to launch the camera,
            // external storage permission is not tested.
            if (isCameraPermissionGranted) {
                if (R.string.option_take_photo_video == mCameraPermissionAction) {
                    launchCamera();
                } else if (R.string.option_take_photo == mCameraPermissionAction) {
                    launchNativeCamera();
                } else if (R.string.option_take_video == mCameraPermissionAction) {
                    launchNativeVideoRecorder();
                }
            } else {
                CommonActivityUtils.displayToast(this, getString(R.string.missing_permissions_warning));
            }
        } else if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_AUDIO_IP_CALL) {
            if (CommonActivityUtils.onPermissionResultAudioIpCall(this, aPermissions, aGrantResults)) {
                startIpCall(PreferencesManager.useJitsiConfCall(this), false);
            }
        } else if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_VIDEO_IP_CALL) {
            if (CommonActivityUtils.onPermissionResultVideoIpCall(this, aPermissions, aGrantResults)) {
                startIpCall(		PreferencesManager.useJitsiConfCall(this), true);
            }
        } else {
            Log.w(LOG_TAG, "## onRequestPermissionsResult(): Unknown requestCode =" + aRequestCode);
        }
    }

    /**
     * Display UI buttons according to user input text.
     */
    private void manageSendMoreButtons() {
        boolean hasText = (mEditText.getText().length() > 0);
        mSendImageView.setImageResource(hasText ? R.drawable.ic_material_send_green : R.drawable.ic_material_file);
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
     * Sanitize the display name.
     *
     * @param displayName the display name to sanitize
     * @return the sanitized display name
     */
    public static String sanitizeDisplayname(String displayName) {
        // sanity checks
        if (!TextUtils.isEmpty(displayName)) {
            final String ircPattern = " (IRC)";

            if (displayName.endsWith(ircPattern)) {
                displayName = displayName.substring(0, displayName.length() - ircPattern.length());
            }
        }

        return displayName;
    }

    /**
     * Insert an user displayname  in the message editor.
     *
     * @param text the text to insert.
     */
    public void insertUserDisplayNameInTextEditor(String text) {
        if (null != text) {
            if (TextUtils.equals(mSession.getMyUser().displayname, text)) {
                // current user
                if (TextUtils.isEmpty(mEditText.getText())) {
                    mEditText.setText(String.format(VectorApp.getApplicationLocale(), "%s ", SlashComandsParser.CMD_EMOTE));
                    mEditText.setSelection(mEditText.getText().length());
                }
            } else {
                // another user
                if (TextUtils.isEmpty(mEditText.getText())) {
                    mEditText.append(sanitizeDisplayname(text) + ": ");
                } else {
                    mEditText.getText().insert(mEditText.getSelectionStart(), sanitizeDisplayname(text) + " ");
                }
            }
        }
    }

    /**
     * Insert a quote  in the message editor.
     *
     * @param quote the quote to insert.
     */
    public void insertQuoteInTextEditor(String quote) {
        if (!TextUtils.isEmpty(quote)) {
            if (TextUtils.isEmpty(mEditText.getText())) {
                mEditText.setText("");
                mEditText.append(quote);
            } else {
                mEditText.getText().insert(mEditText.getSelectionStart(), "\n" + quote);
            }
        }
    }

    //================================================================================
    // Notifications area management (... is typing and so on)
    //================================================================================

    /**
     * Track the cancel all click.
     */
    private class cancelAllClickableSpan extends ClickableSpan {
        @Override
        public void onClick(View widget) {
            mVectorMessageListFragment.deleteUnsentEvents();
            refreshNotificationsArea();
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setColor(ContextCompat.getColor(VectorRoomActivity.this, R.color.vector_fuchsia_color));
            ds.bgColor = 0;
            ds.setUnderlineText(true);
        }
    }

    /**
     * Track the resend all click.
     */
    private class resendAllClickableSpan extends ClickableSpan {
        @Override
        public void onClick(View widget) {
            mVectorMessageListFragment.resendUnsentMessages();
            refreshNotificationsArea();
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setColor(ContextCompat.getColor(VectorRoomActivity.this, R.color.vector_fuchsia_color));
            ds.bgColor = 0;
            ds.setUnderlineText(true);
        }
    }

    /**
     * Refresh the notifications area.
     */
    private void refreshNotificationsArea() {
        // sanity check
        // might happen when the application is logged out
        if ((null == mSession.getDataHandler()) || (null == mRoom) || (null != sRoomPreviewData)) {
            return;
        }

        int iconId = -1;
        @ColorInt int textColor = -1;
        boolean isAreaVisible = false;
        SpannableString text = new SpannableString("");
        boolean hasUnsentEvent = false;

        // remove any listeners
        mNotificationTextView.setOnClickListener(null);
        mNotificationIconImageView.setOnClickListener(null);

        //  no network
        if (!Matrix.getInstance(this).isConnected()) {
            isAreaVisible = true;
            iconId = R.drawable.error;
            textColor = ContextCompat.getColor(VectorRoomActivity.this, R.color.vector_fuchsia_color);
            text = new SpannableString(getResources().getString(R.string.room_offline_notification));
        } else if (mIsUnreadPreviewMode) {
            isAreaVisible = true;
            iconId = R.drawable.scrolldown;
            textColor = ThemeUtils.getColor(this, R.attr.room_notification_text_color);

            mNotificationIconImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setResult(RESULT_OK);
                    finish();
                }
            });
        } else {
            List<Event> undeliveredEvents = mSession.getDataHandler().getStore().getUndeliverableEvents(mRoom.getRoomId());
            List<Event> unknownDeviceEvents = mSession.getDataHandler().getStore().getUnknownDeviceEvents(mRoom.getRoomId());

            boolean hasUndeliverableEvents = (null != undeliveredEvents) && (undeliveredEvents.size() > 0);
            boolean hasUnknownDeviceEvents = (null != unknownDeviceEvents) && (unknownDeviceEvents.size() > 0);

            if (hasUndeliverableEvents || hasUnknownDeviceEvents) {
                hasUnsentEvent = true;
                isAreaVisible = true;
                iconId = R.drawable.error;

                String cancelAll = getResources().getString(R.string.room_prompt_cancel);
                String resendAll = getResources().getString(R.string.room_prompt_resend);
                String message = getResources().getString(hasUnknownDeviceEvents ? R.string.room_unknown_devices_messages_notification : R.string.room_unsent_messages_notification, resendAll, cancelAll);

                int cancelAllPos = message.indexOf(cancelAll);
                int resendAllPos = message.indexOf(resendAll);

                text = new SpannableString(message);

                // cancelAllPos should always be > 0 but a GA crash reported here
                if (cancelAllPos >= 0) {
                    text.setSpan(new cancelAllClickableSpan(), cancelAllPos, cancelAllPos + cancelAll.length(), 0);
                }

                // resendAllPos should always be > 0 but a GA crash reported here
                if (resendAllPos >= 0) {
                    text.setSpan(new resendAllClickableSpan(), resendAllPos, resendAllPos + resendAll.length(), 0);
                }

                mNotificationTextView.setMovementMethod(LinkMovementMethod.getInstance());
                textColor = ContextCompat.getColor(VectorRoomActivity.this, R.color.vector_fuchsia_color);
            } else if ((null != mIsScrolledToTheBottom) && (!mIsScrolledToTheBottom)) {
                isAreaVisible = true;

                int unreadCount = 0;

                RoomSummary summary = mRoom.getDataHandler().getStore().getSummary(mRoom.getRoomId());

                if (null != summary) {
                    unreadCount = mRoom.getDataHandler().getStore().eventsCountAfter(mRoom.getRoomId(), summary.getReadReceiptEventId());
                }

                if (unreadCount > 0) {
                    iconId = R.drawable.newmessages;
                    textColor = ContextCompat.getColor(VectorRoomActivity.this, R.color.vector_fuchsia_color);

                    if (unreadCount == 1) {
                        text = new SpannableString(getResources().getString(R.string.room_new_message_notification));
                    } else {
                        text = new SpannableString(getResources().getString(R.string.room_new_messages_notification, unreadCount));
                    }
                } else {
                    iconId = R.drawable.scrolldown;
                    textColor = ThemeUtils.getColor(this, R.attr.room_notification_text_color);

                    if (!TextUtils.isEmpty(mLatestTypingMessage)) {
                        text = new SpannableString(mLatestTypingMessage);
                    }
                }

                mNotificationTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mReadMarkerManager != null) {
                            mReadMarkerManager.handleJumpToBottom();
                        } else {
                            mVectorMessageListFragment.scrollToBottom(0);
                        }
                    }
                });

                mNotificationIconImageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mReadMarkerManager != null) {
                            mReadMarkerManager.handleJumpToBottom();
                        } else {
                            mVectorMessageListFragment.scrollToBottom(0);
                        }
                    }
                });

            } else if (!TextUtils.isEmpty(mLatestTypingMessage)) {
                isAreaVisible = true;

                iconId = R.drawable.vector_typing;
                text = new SpannableString(mLatestTypingMessage);
                textColor = ThemeUtils.getColor(this, R.attr.room_notification_text_color);
            }
        }

        if (mIsUnreadPreviewMode) {
            mNotificationsArea.setVisibility(View.VISIBLE);
        } else if (TextUtils.isEmpty(mEventId)) {
            mNotificationsArea.setVisibility(isAreaVisible ? View.VISIBLE : View.INVISIBLE);
        }

        if (-1 != iconId) {
            mNotificationIconImageView.setImageResource(iconId);
            mNotificationTextView.setText(text);
            mNotificationTextView.setTextColor(textColor);
        }

        //
        if (null != mResendUnsentMenuItem) {
            mResendUnsentMenuItem.setVisible(hasUnsentEvent);
        }

        if (null != mResendDeleteMenuItem) {
            mResendDeleteMenuItem.setVisible(hasUnsentEvent);
        }

        if (null != mSearchInRoomMenuItem) {
            // the server search does not work on encrypted rooms.
            mSearchInRoomMenuItem.setVisible(!mRoom.isEncrypted());
        }

        if (null != mUseMatrixAppsMenuItem) {
            mUseMatrixAppsMenuItem.setVisible(TextUtils.isEmpty(mEventId) && (null == sRoomPreviewData) && PreferencesManager.useMatrixApps(this));
        }
    }

    /**
     * Refresh the call buttons display.
     */
    private void refreshCallButtons(boolean refreshOngoingConferenceCallView) {
        if ((null == sRoomPreviewData) && (null == mEventId) && canSendMessages()) {
            boolean isCallSupported = mRoom.canPerformCall() && mSession.isVoipCallSupported();
            IMXCall call = CallsManager.getSharedInstance().getActiveCall();
            Widget activeWidget = mVectorOngoingConferenceCallView.getActiveWidget();

            if ((null == call) && (null == activeWidget)) {
                mStartCallLayout.setVisibility((isCallSupported && (mEditText.getText().length() == 0)) ? View.VISIBLE : View.GONE);
                mStopCallLayout.setVisibility(View.GONE);
            } else if (null != activeWidget) {
                mStartCallLayout.setVisibility(View.GONE);
                mStopCallLayout.setVisibility(View.GONE);
            } else {
                IMXCall roomCall = mSession.mCallsManager.getCallWithRoomId(mRoom.getRoomId());

                // ensure that the listener is defined once
                call.removeListener(mCallListener);
                call.addListener(mCallListener);

                mStartCallLayout.setVisibility(View.GONE);
                mStopCallLayout.setVisibility((call == roomCall) ? View.VISIBLE : View.GONE);
            }

            if (refreshOngoingConferenceCallView) {
                mVectorOngoingConferenceCallView.refresh();
            }
        }
    }

    /**
     * Display the typing status in the notification area.
     */
    private void onRoomTypings() {
        mLatestTypingMessage = null;

        List<String> typingUsers = mRoom.getTypingUsers();

        if ((null != typingUsers) && (typingUsers.size() > 0)) {
            String myUserId = mSession.getMyUserId();

            // get the room member names
            ArrayList<String> names = new ArrayList<>();

            for (int i = 0; i < typingUsers.size(); i++) {
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
                mLatestTypingMessage = String.format(VectorApp.getApplicationLocale(), this.getString(R.string.room_one_user_is_typing), names.get(0));
            } else if (2 == names.size()) {
                mLatestTypingMessage = String.format(VectorApp.getApplicationLocale(), this.getString(R.string.room_two_users_are_typing), names.get(0), names.get(1));
            } else if (names.size() > 2) {
                mLatestTypingMessage = String.format(VectorApp.getApplicationLocale(), this.getString(R.string.room_many_users_are_typing), names.get(0), names.get(1));
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
     *
     * @param aTopicValue the new topic value
     */
    private void setTopic(String aTopicValue) {
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
     * <p>
     * This action bar layout will contain a title, a topic and an arrow.
     * The arrow is updated (down/up) according to if the room header is
     * displayed or not.
     */
    private void setActionBarDefaultCustomLayout() {
        // binding the widgets of the custom view
        mActionBarCustomTitle = findViewById(R.id.room_action_bar_title);
        mActionBarCustomTopic = findViewById(R.id.room_action_bar_topic);
        mActionBarCustomArrowImageView = findViewById(R.id.open_chat_header_arrow);

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
        if ((null != mSession) && (null != mRoom)) {
            titleToApply = VectorUtils.getRoomDisplayName(this, mSession, mRoom);

            if (TextUtils.isEmpty(titleToApply)) {
                titleToApply = mDefaultRoomName;
            }

            // in context mode, add search to the title.
            if (!TextUtils.isEmpty(mEventId) && !mIsUnreadPreviewMode) {
                titleToApply = getResources().getText(R.string.search) + " : " + titleToApply;
            }
        } else if (null != sRoomPreviewData) {
            titleToApply = sRoomPreviewData.getRoomName();
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
            mActionBarHeaderRoomName.setText(VectorUtils.getRoomDisplayName(this, mSession, mRoom));
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
     * Tell if the user can send a message in this room.
     *
     * @return true if the user is allowed to send messages in this room.
     */
    private boolean canSendMessages() {
        boolean canSendMessage = false;

        if ((null != mRoom) && (null != mRoom.getLiveState())) {
            canSendMessage = true;
            PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

            if (null != powerLevels) {
                canSendMessage = powerLevels.maySendMessage(mMyUserId);
            }
        }

        return canSendMessage;
    }

    /**
     * Check if the user can send a message in this room
     */
    private void checkSendEventStatus() {
        if ((null != mRoom) && (null != mRoom.getLiveState())) {
            boolean canSendMessage = canSendMessages();
            mSendingMessagesLayout.setVisibility(canSendMessage ? View.VISIBLE : View.GONE);
            mCanNotPostTextView.setVisibility(!canSendMessage ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Display the active members count / members count in the expendable header.
     */
    private void updateRoomHeaderMembersStatus() {
        if (null != mActionBarHeaderActiveMembersLayout) {
            // refresh only if the action bar is hidden
            if (mActionBarCustomTitle.getVisibility() == View.GONE) {
                if ((null != mRoom) || (null != sRoomPreviewData)) {
                    // update the members status: "active members"/"members"
                    int joinedMembersCount = 0;
                    int activeMembersCount = 0;

                    RoomState roomState = (null != sRoomPreviewData) ? sRoomPreviewData.getRoomState() : mRoom.getState();

                    if (null != roomState) {
                        Collection<RoomMember> members = roomState.getDisplayableMembers();

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
                            activeMembersCount = joinedMembersCount = ((PublicRoom) roomState).numJoinedMembers;
                        }

                        String text;

                        if (joinedMembersCount == 1) {
                            text = getResources().getString(R.string.room_title_one_member);
                        } else if (null != sRoomPreviewData) {
                            text = getResources().getString(R.string.room_title_members, joinedMembersCount);
                        } else {
                            text = getString(R.string.room_header_active_members, activeMembersCount, joinedMembersCount);
                        }

                        if (!TextUtils.isEmpty(text)) {
                            mActionBarHeaderActiveMembersTextView.setText(text);
                            mActionBarHeaderActiveMembersLayout.setVisibility(View.VISIBLE);

                            // display the both action buttons only when it makes sense
                            // i.e not a room preview
                            boolean hideMembersButtons = (null == mRoom) || !TextUtils.isEmpty(mEventId) || (null != sRoomPreviewData);
                            mActionBarHeaderActiveMembersListButton.setVisibility(hideMembersButtons ? View.INVISIBLE : View.VISIBLE);
                            mActionBarHeaderActiveMembersInviteButton.setVisibility(hideMembersButtons ? View.INVISIBLE : View.VISIBLE);
                        } else {
                            mActionBarHeaderActiveMembersLayout.setVisibility(View.GONE);
                        }
                    } else {
                        mActionBarHeaderActiveMembersLayout.setVisibility(View.GONE);
                    }
                } else {
                    mActionBarHeaderActiveMembersLayout.setVisibility(View.GONE);
                }
            } else {
                mActionBarHeaderActiveMembersLayout.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Dismiss the keyboard
     */
    public void dismissKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
    }

    /**
     * Show or hide the action bar header view according to aIsHeaderViewDisplayed
     *
     * @param aIsHeaderViewDisplayed true to show the header view, false to hide
     */
    private void enableActionBarHeader(boolean aIsHeaderViewDisplayed) {

        mIsHeaderViewDisplayed = aIsHeaderViewDisplayed;
        if (SHOW_ACTION_BAR_HEADER == aIsHeaderViewDisplayed) {
            dismissKeyboard();

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
                mToolbar.setBackgroundColor(ThemeUtils.getColor(this, R.attr.primary_color));
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
     * Manage the room preview buttons area
     */
    private void manageRoomPreview() {
        if (null != sRoomPreviewData) {
            mRoomPreviewLayout.setVisibility(View.VISIBLE);

            TextView invitationTextView = findViewById(R.id.room_preview_invitation_textview);
            TextView subInvitationTextView = findViewById(R.id.room_preview_subinvitation_textview);

            Button joinButton = findViewById(R.id.button_join_room);
            Button declineButton = findViewById(R.id.button_decline);

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

                    if (null != sRoomPreviewData) {
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
                    } else {
                        VectorRoomActivity.this.finish();
                    }
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

            processDirectMessageRoom();

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

    /**
     * If the joined room was tagged as "direct chat room", it is required to update the
     * room as a "direct chat room" (account_data)
     */
    private void processDirectMessageRoom() {
        Room room = sRoomPreviewData.getSession().getDataHandler().getRoom(sRoomPreviewData.getRoomId());
        if ((null != room) && (room.isDirectChatInvitation())) {
            String myUserId = mSession.getMyUserId();
            Collection<RoomMember> members = mRoom.getMembers();

            if (2 == members.size()) {
                String participantUserId;

                // test if room is already seen as "direct message"
                if (mSession.getDirectChatRoomIdsList().indexOf(sRoomPreviewData.getRoomId()) < 0) {
                    for (RoomMember member : members) {
                        // search for the second participant
                        if (!member.getUserId().equals(myUserId)) {
                            participantUserId = member.getUserId();
                            CommonActivityUtils.setToggleDirectMessageRoom(mSession, sRoomPreviewData.getRoomId(), participantUserId, this, mDirectMessageListener);
                            break;
                        }
                    }
                } else {
                    Log.d(LOG_TAG, "## processDirectMessageRoom(): attempt to add an already direct message room");
                }
            }
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
                mSession.getMediasCache().uploadContent(resource.mContentStream, null, resource.mMimeType, null, new MXMediaUploadListener() {
                    @Override
                    public void onUploadError(String uploadId, int serverResponseCode, String serverErrorMessage) {
                        Log.e(LOG_TAG, "Fail to upload the avatar");
                    }

                    @Override
                    public void onUploadComplete(final String uploadId, final String contentUri) {
                        VectorRoomActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(LOG_TAG, "The avatar has been uploaded, update the room avatar");
                                mRoom.updateAvatarUrl(contentUri, new ApiCallback<Void>() {

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

        TextView titleText = dialogView.findViewById(R.id.dialog_title);
        titleText.setText(getResources().getString(R.string.room_info_room_name));

        final EditText textInput = dialogView.findViewById(R.id.dialog_edit_text);
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

        TextView titleText = dialogView.findViewById(R.id.dialog_title);
        titleText.setText(getResources().getString(R.string.room_info_room_topic));

        final EditText textInput = dialogView.findViewById(R.id.dialog_edit_text);
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
                        if (CommonActivityUtils.isPowerLevelEnoughForAvatarUpdate(mRoom, mSession)) {
                            // need to check if the camera permission has been granted
                            if (CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_ROOM_DETAILS, VectorRoomActivity.this)) {
                                Intent intent = new Intent(VectorRoomActivity.this, VectorMediasPickerActivity.class);
                                intent.putExtra(VectorMediasPickerActivity.EXTRA_AVATAR_MODE, true);
                                startActivityForResult(intent, REQUEST_ROOM_AVATAR_CODE);
                            }
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

        if (null != mActionBarHeaderActiveMembersListButton) {
            mActionBarHeaderActiveMembersListButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchRoomDetails(VectorRoomDetailsActivity.PEOPLE_TAB_INDEX);
                }
            });
        }

        if (null != mActionBarHeaderActiveMembersTextView) {
            mActionBarHeaderActiveMembersTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchRoomDetails(VectorRoomDetailsActivity.PEOPLE_TAB_INDEX);
                }
            });
        }

        if (null != mActionBarHeaderActiveMembersInviteButton) {
            mActionBarHeaderActiveMembersInviteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchInvitePeople();
                }
            });
        }
    }

    private static final String E2E_WARNINGS_PREFERENCES = "E2E_WARNINGS_PREFERENCES";

    /**
     * Display an e2e alert for the first opened room.
     */
    private void displayE2eRoomAlert() {
        if (!isFinishing()) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

            if (!preferences.contains(E2E_WARNINGS_PREFERENCES) && (null != mRoom) && mRoom.isEncrypted()) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(E2E_WARNINGS_PREFERENCES, false);
                editor.commit();

                android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this);
                builder.setTitle(R.string.room_e2e_alert_title);
                builder.setMessage(R.string.room_e2e_alert_message);
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // NOP
                    }
                });
                builder.create().show();
            }
        }
    }
}


