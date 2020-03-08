/*
 * Copyright 2015 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
 * Copyright 2019 New Vector Ltd
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
import android.app.ActivityOptions;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import com.google.gson.JsonParser;

import org.jetbrains.annotations.NotNull;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.IMXCallListener;
import org.matrix.androidsdk.call.MXCallListener;
import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.PermalinkUtils;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.core.model.MatrixError;
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
import org.matrix.androidsdk.features.identityserver.IdentityServerNotConfiguredException;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomTombstoneContent;
import org.matrix.androidsdk.rest.model.StateEvent;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.message.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.OnClick;
import butterknife.OnLongClick;
import butterknife.OnTouch;
import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.activity.util.RequestCodesKt;
import im.vector.dialogs.DialogCallAdapter;
import im.vector.dialogs.DialogListItem;
import im.vector.dialogs.DialogSendItemAdapter;
import im.vector.features.hhs.LimitResourceState;
import im.vector.features.hhs.ResourceLimitEventListener;
import im.vector.fragments.VectorMessageListFragment;
import im.vector.fragments.VectorReadReceiptsDialogFragment;
import im.vector.fragments.VectorUnknownDevicesFragment;
import im.vector.fragments.roomwidgets.RoomWidgetPermissionBottomSheet;
import im.vector.listeners.IMessagesAdapterActionsListener;
import im.vector.ui.themes.ThemeUtils;
import im.vector.util.CallsManager;
import im.vector.util.ExternalApplicationsUtilKt;
import im.vector.util.MatrixURLSpan;
import im.vector.util.PermissionsToolsKt;
import im.vector.util.PreferencesManager;
import im.vector.util.ReadMarkerManager;
import im.vector.util.RoomUtils;
import im.vector.util.SlashCommandsParser;
import im.vector.util.UrlUtilKt;
import im.vector.util.VectorMarkdownParser;
import im.vector.util.VectorRoomMediasSender;
import im.vector.util.VectorUtils;
import im.vector.view.ActiveWidgetsBanner;
import im.vector.view.NotificationAreaView;
import im.vector.view.VectorAutoCompleteTextView;
import im.vector.view.VectorOngoingConferenceCallView;
import im.vector.view.VectorPendingCallView;
import im.vector.widgets.Widget;
import im.vector.widgets.WidgetsManager;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;
import kotlin.Unit;

/**
 * Displays a single room with messages.
 */
public class VectorRoomActivity extends MXCActionBarActivity implements
        MatrixMessageListFragment.IRoomPreviewDataListener,
        MatrixMessageListFragment.IEventSendingListener,
        MatrixMessageListFragment.IOnScrollListener,
        VectorMessageListFragment.VectorMessageListFragmentListener,
        VectorReadReceiptsDialogFragment.VectorReadReceiptsDialogFragmentListener {

    // the session
    public static final String EXTRA_MATRIX_ID = MXCActionBarActivity.EXTRA_MATRIX_ID;
    // the room id (string)
    public static final String EXTRA_ROOM_ID = "EXTRA_ROOM_ID";
    // the event id (universal link management - string)
    public static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";
    // whether the preview is to display unread messages
    public static final String EXTRA_IS_UNREAD_PREVIEW_MODE = "EXTRA_IS_UNREAD_PREVIEW_MODE";
    // the forwarded data (list of media uris)
    public static final String EXTRA_ROOM_INTENT = "EXTRA_ROOM_INTENT";
    // the room is opened in preview mode (string)
    public static final String EXTRA_ROOM_PREVIEW_ID = "EXTRA_ROOM_PREVIEW_ID";
    // the room alias of the room in preview mode (string)
    public static final String EXTRA_ROOM_PREVIEW_ROOM_ALIAS = "EXTRA_ROOM_PREVIEW_ROOM_ALIAS";
    // expand the room header when the activity is launched (boolean)
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

    private static final String LOG_TAG = VectorRoomActivity.class.getSimpleName();
    private static final int TYPING_TIMEOUT_MS = 10000;

    private static final String FIRST_VISIBLE_ROW = "FIRST_VISIBLE_ROW";

    // activity result request code
    private static final int REQUEST_FILES_REQUEST_CODE = 0;
    private static final int TAKE_IMAGE_REQUEST_CODE = 1;
    public static final int GET_MENTION_REQUEST_CODE = 2;
    private static final int INVITE_USER_REQUEST_CODE = 4;
    public static final int UNREAD_PREVIEW_REQUEST_CODE = 5;
    private static final int RECORD_AUDIO_REQUEST_CODE = 6;

    // media selection
    private static final int MEDIA_SOURCE_FILE = 1;
    private static final int MEDIA_SOURCE_VOICE = 2;
    private static final int MEDIA_SOURCE_STICKER = 3;
    private static final int MEDIA_SOURCE_PHOTO = 4;
    private static final int MEDIA_SOURCE_VIDEO = 5;

    private static final String CAMERA_VALUE_TITLE = "attachment"; // Samsung devices need a filepath to write to or else won't return a Uri (!!!)
    private String mLatestTakePictureCameraUri = null; // has to be String not Uri because of Serializable

    public static final int CONFIRM_MEDIA_REQUEST_CODE = 7;

    private VectorMessageListFragment mVectorMessageListFragment;
    private MXSession mSession;

    @Nullable
    private Room mRoom;

    private String mMyUserId;
    // the parameter is too big to be sent by the intent
    // so use a static variable to send it
    // FIXME Remove this static variable. The VectorRoomActivity should load the RoomPreviewData itself
    public static RoomPreviewData sRoomPreviewData = null;
    private String mEventId;
    private String mDefaultRoomName;
    private String mDefaultTopic;

    private MXLatestChatMessageCache mLatestChatMessageCache;

    @BindView(R.id.room_sending_message_layout)
    View mSendingMessagesLayout;

    @BindView(R.id.room_send_image_view)
    ImageView mSendImageView;

    @BindView(R.id.editText_messageBox)
    VectorAutoCompleteTextView mEditText;

    @BindView(R.id.room_self_avatar)
    ImageView mAvatarImageView;

    @BindView(R.id.bottom_separator)
    View mBottomSeparator;

    @BindView(R.id.room_cannot_post_textview)
    View mCanNotPostTextView;

    @BindView(R.id.room_bottom_layout)
    View mBottomLayout;

    @BindView(R.id.room_encrypted_image_view)
    ImageView mE2eImageView;

    // call
    @BindView(R.id.room_start_call_image_view)
    View mStartCallLayout;

    @BindView(R.id.room_end_call_image_view)
    View mStopCallLayout;

    // action bar header
    @BindView(R.id.room_action_bar_title)
    TextView mActionBarCustomTitle;

    @BindView(R.id.room_action_bar_topic)
    TextView mActionBarCustomTopic;

    @BindView(R.id.open_chat_header_arrow)
    ImageView mActionBarCustomArrowImageView;

    // The room header view is displayed by clicking on the title of the action bar
    @BindView(R.id.action_bar_header)
    ViewGroup mRoomHeaderView;

    @BindView(R.id.action_bar_header_room_title)
    TextView mActionBarHeaderRoomName;

    @BindView(R.id.action_bar_header_room_members_layout)
    View mActionBarHeaderActiveMembersLayout;

    @BindView(R.id.action_bar_header_room_members_text_view)
    TextView mActionBarHeaderActiveMembersTextView;

    @BindView(R.id.action_bar_header_room_members_invite_view)
    View mActionBarHeaderActiveMembersInviteButton;

    @BindView(R.id.action_bar_header_room_members_settings_view)
    View mActionBarHeaderActiveMembersListButton;

    @BindView(R.id.action_bar_header_room_topic)
    TextView mActionBarHeaderRoomTopic;

    @BindView(R.id.room_header_avatar)
    ImageView mActionBarHeaderRoomAvatar;

    // notifications area
    @BindView(R.id.room_notifications_area)
    NotificationAreaView mNotificationsArea;

    private String mLatestTypingMessage;
    private Boolean mIsScrolledToTheBottom;
    private Event mLatestDisplayedEvent; // the event at the bottom of the list

    private ReadMarkerManager mReadMarkerManager;

    // room preview
    @BindView(R.id.room_preview_info_layout)
    View mRoomPreviewLayout;

    // medias sending helper
    private VectorRoomMediasSender mVectorRoomMediasSender;

    // pending call
    @BindView(R.id.room_pending_call_view)
    VectorPendingCallView mVectorPendingCallView;

    // outgoing call
    @BindView(R.id.room_ongoing_conference_call_view)
    VectorOngoingConferenceCallView mVectorOngoingConferenceCallView;

    // pending active view
    @BindView(R.id.room_pending_widgets_view)
    ActiveWidgetsBanner mActiveWidgetsBanner;

    // spinners
    @BindView(R.id.loading_room_paginate_back_progress)
    View mBackProgressView;
    @BindView(R.id.loading_room_paginate_forward_progress)
    View mForwardProgressView;
    @BindView(R.id.main_progress_layout)
    View mMainProgressView;

    @BindView(R.id.room_preview_invitation_textview)
    TextView invitationTextView;
    @BindView(R.id.room_preview_subinvitation_textview)
    TextView subInvitationTextView;

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

    // True when this room has unsent event(s)
    private boolean mHasUnsentEvents;

    // progress bar to warn that the sync is not yet done
    @BindView(R.id.room_sync_in_progress)
    View mSyncInProgressView;

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

    private ResourceLimitEventListener mResourceLimitEventListener;

    /**
     * Presence and room preview listeners
     */
    private final MXEventListener mGlobalEventListener = new MXEventListener() {

        @Override
        public void onSyncError(MatrixError matrixError) {
            mSyncInProgressView.setVisibility(View.GONE);

            checkSendEventStatus();
            refreshNotificationsArea();
        }

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

            checkSendEventStatus();
            refreshNotificationsArea();
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
                    finish();
                }
            });
        }

        @Override
        public void onRoomKick(String roomId) {
            Map<String, Object> params = new HashMap<>();

            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
            params.put(VectorRoomActivity.EXTRA_ROOM_ID, mRoom.getRoomId());

            // clear the activity stack to home activity
            Intent intent = new Intent(VectorRoomActivity.this, VectorHomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

            intent.putExtra(VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS, (HashMap) params);
            startActivity(intent);
        }

        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    String eventType = event.getType();
                    Log.d(LOG_TAG, "Received event type: " + eventType);

                    switch (eventType) {
                        case Event.EVENT_TYPE_STATE_ROOM_NAME:
                        case Event.EVENT_TYPE_STATE_ROOM_ALIASES:
                        case Event.EVENT_TYPE_STATE_ROOM_MEMBER:
                            setTitle();
                            updateRoomHeaderMembersStatus();
                            updateRoomHeaderAvatar();
                            break;
                        case Event.EVENT_TYPE_STATE_ROOM_TOPIC:
                            StateEvent stateEvent = JsonUtils.toStateEvent(event.getContent());
                            setTopic(stateEvent.topic);
                            break;
                        case Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS:
                            checkSendEventStatus();
                            break;
                        case Event.EVENT_TYPE_TYPING:
                            onRoomTyping();
                            break;
                        case Event.EVENT_TYPE_STATE_ROOM_AVATAR:
                            updateRoomHeaderAvatar();
                            break;
                        case Event.EVENT_TYPE_MESSAGE_ENCRYPTION:
                            boolean canSendEncryptedEvent = mRoom.isEncrypted() && mSession.isCryptoEnabled();
                            mE2eImageView.setImageResource(canSendEncryptedEvent ? R.drawable.e2e_verified : R.drawable.e2e_unencrypted);
                            mVectorMessageListFragment.setIsRoomEncrypted(mRoom.isEncrypted());
                            break;
                        case Event.EVENT_TYPE_STATE_ROOM_TOMBSTONE:
                            checkSendEventStatus();
                            break;
                        default:
                            Log.d(LOG_TAG, "Ignored event type: " + eventType);
                            break;
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
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_vector_room;
    }

    @Override
    public void initUiAndData() {
        setWaitingView(findViewById(R.id.main_progress_layout));

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

        mSession = getSession(intent);

        if ((mSession == null) || !mSession.isAlive()) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }
        mResourceLimitEventListener = new ResourceLimitEventListener(mSession.getDataHandler(), new ResourceLimitEventListener.Callback() {
            @Override
            public void onResourceLimitStateChanged() {
                refreshNotificationsArea();
            }
        });

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

        // hide the header room as soon as the bottom layout (text edit zone) is touched
        mBottomLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                return false;
            }
        });

        mNotificationsArea.setDelegate(new NotificationAreaView.Delegate() {
            @NotNull
            @Override
            public IMessagesAdapterActionsListener providesMessagesActionListener() {
                return mVectorMessageListFragment;
            }

            @Override
            public void resendUnsentEvents() {
                mVectorMessageListFragment.resendUnsentMessages();
            }

            @Override
            public void deleteUnsentEvents() {
                mVectorMessageListFragment.deleteUnsentEvents();
            }

            @Override
            public void closeScreen() {
                setResult(Activity.RESULT_OK);
                finish();
            }

            @Override
            public void jumpToBottom() {
                if (mReadMarkerManager != null) {
                    mReadMarkerManager.handleJumpToBottom();
                } else {
                    mVectorMessageListFragment.scrollToBottom(0);
                }
            }
        });

        // use a toolbar instead of the actionbar
        // to be able to display an expandable header
        configureToolbar();

        mCallId = intent.getStringExtra(EXTRA_START_CALL_ID);
        mEventId = intent.getStringExtra(EXTRA_EVENT_ID);
        mDefaultRoomName = intent.getStringExtra(EXTRA_DEFAULT_NAME);
        mDefaultTopic = intent.getStringExtra(EXTRA_DEFAULT_TOPIC);
        mIsUnreadPreviewMode = intent.getBooleanExtra(EXTRA_IS_UNREAD_PREVIEW_MODE, false);

        if (mIsUnreadPreviewMode) {
            Log.d(LOG_TAG, "Displaying " + roomId + " in unread preview mode");
        } else if (!TextUtils.isEmpty(mEventId) || (null != sRoomPreviewData)) {
            Log.d(LOG_TAG, "Displaying " + roomId + " in preview mode");
        } else {
            Log.d(LOG_TAG, "Displaying " + roomId);
        }

        if (PreferencesManager.sendMessageWithEnter(this)) {
            // imeOptions="actionSend" only works with single line, so we remove multiline inputType
            mEditText.setInputType(mEditText.getInputType() & ~EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
            mEditText.setImeOptions(EditorInfo.IME_ACTION_SEND);
        }

        // IME's DONE and SEND button is treated as a send action
        mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                int imeActionId = actionId & EditorInfo.IME_MASK_ACTION;

                if (EditorInfo.IME_ACTION_DONE == imeActionId || EditorInfo.IME_ACTION_SEND == imeActionId) {
                    sendTextMessage();
                    return true;
                }

                if ((null != keyEvent) && !keyEvent.isShiftPressed() && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && getResources().getConfiguration().keyboard != Configuration.KEYBOARD_NOKEYS) {
                    sendTextMessage();
                    return true;
                }
                return false;
            }
        });

        mRoom = mSession.getDataHandler().getRoom(roomId, false);

        mEditText.setAddColonOnFirstItem(true);
        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (null != mRoom) {
                    MXLatestChatMessageCache latestChatMessageCache = mLatestChatMessageCache;
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
                // Auto completion mode management
                // The auto completion mode depends on the first character of the message
                mEditText.updateAutoCompletionMode(false);
            }
        });

        mMyUserId = mSession.getCredentials().userId;

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

        mVectorMessageListFragment.setListener(this);

        mVectorRoomMediasSender = new VectorRoomMediasSender(this, mVectorMessageListFragment, Matrix.getInstance(this).getMediaCache());

        manageRoomPreview();

        if (mRoom != null) {
            // Ensure menu and UI is up to date (ignore any error)
            mRoom.getMembersAsync(new SimpleApiCallback<List<RoomMember>>() {
                @Override
                public void onSuccess(List<RoomMember> info) {
                    refreshNotificationsArea();

                    checkIfUserHasBeenKicked();
                }
            });
        }

        checkIfUserHasBeenKicked();

        mLatestChatMessageCache = Matrix.getInstance(this).getDefaultLatestChatMessageCache();

        // some medias must be sent while opening the chat
        if (intent.hasExtra(EXTRA_ROOM_INTENT)) {
            // fix issue #1276
            // if there is a saved instance, it means that onSaveInstanceState has been called.
            // theses parameters must only be used at activity creation.
            // The activity might have been created after being killed by android while the application is in background
            if (isFirstCreation()) {
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

        mActiveWidgetsBanner.initRoomInfo(mSession, mRoom);

        mActiveWidgetsBanner.setOnUpdateListener(new ActiveWidgetsBanner.onUpdateListener() {
            @Override
            public void onCloseWidgetClick(final Widget widget) {

                new AlertDialog.Builder(VectorRoomActivity.this)
                        .setMessage(R.string.widget_delete_message_confirmation)
                        .setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                WidgetsManager wm = Matrix.getWidgetManager(VectorRoomActivity.this);
                                if (wm != null) {
                                    showWaitingView();

                                    wm.closeWidget(mSession, mRoom, widget.getWidgetId(), new ApiCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void info) {
                                            hideWaitingView();
                                        }

                                        private void onError(String errorMessage) {
                                            hideWaitingView();
                                            Toast.makeText(VectorRoomActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
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
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }

            @Override
            public void onActiveWidgetsListUpdate() {
                // something todo ?
            }

            private void displayWidget(Widget widget) {
                Intent intent = WidgetActivity.Companion.getIntent(VectorRoomActivity.this, widget);

                startActivity(intent);
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
                            .setItems(widgetNames.toArray(CharSequences), new DialogInterface.OnClickListener() {
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
                if (PermissionsToolsKt.checkPermissions(isVideo ? PermissionsToolsKt.PERMISSIONS_FOR_VIDEO_IP_CALL
                                : PermissionsToolsKt.PERMISSIONS_FOR_AUDIO_IP_CALL,
                        VectorRoomActivity.this,
                        isVideo ? PermissionsToolsKt.PERMISSION_REQUEST_CODE_VIDEO_CALL : PermissionsToolsKt.PERMISSION_REQUEST_CODE_AUDIO_CALL)) {
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
                WidgetsManager wm = Matrix.getWidgetManager(VectorRoomActivity.this);
                if (wm != null) {
                    showWaitingView();

                    wm.closeWidget(mSession, mRoom, widget.getWidgetId(), new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            hideWaitingView();
                        }

                        private void onError(String errorMessage) {
                            hideWaitingView();
                            Toast.makeText(VectorRoomActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
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
            }

            @Override
            public void onActiveWidgetUpdate() {
                refreshCallButtons(false);
            }
        });

        refreshSelfAvatar();

        // in case a "Send as" dialog was in progress when the activity was destroyed (life cycle)
        mVectorRoomMediasSender.resumeResizeMediaAndSend();

        // header visibility has launched
        enableActionBarHeader(intent.getBooleanExtra(EXTRA_EXPAND_ROOM_HEADER, false) ? SHOW_ACTION_BAR_HEADER : HIDE_ACTION_BAR_HEADER);

        // the both flags are only used once
        intent.removeExtra(EXTRA_EXPAND_ROOM_HEADER);

        // Init read marker manager
        if (mIsUnreadPreviewMode
                || (mRoom != null && mRoom.getTimeline() != null && mRoom.getTimeline().isLiveTimeline() && TextUtils.isEmpty(mEventId))) {
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

    private void checkIfUserHasBeenKicked() {
        RoomMember member = (null != mRoom) ? mRoom.getMember(mMyUserId) : null;
        boolean hasBeenKicked = (null != member) && member.kickedOrBanned();

        // in timeline mode (i.e search in the forward and backward room history)
        // or in room preview mode
        // the edition items are not displayed
        if ((!TextUtils.isEmpty(mEventId) || (null != sRoomPreviewData)) || hasBeenKicked) {
            if (!mIsUnreadPreviewMode || hasBeenKicked) {
                mNotificationsArea.setVisibility(View.GONE);
                mBottomSeparator.setVisibility(View.GONE);
                findViewById(R.id.room_notification_separator).setVisibility(View.GONE);
            }

            mBottomLayout.getLayoutParams().height = 0;
        }

        if ((null == sRoomPreviewData) && hasBeenKicked) {
            manageBannedHeader(member);
        }
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
                mSession.getDataHandler().removeListener(mResourceLimitEventListener);
            }
        }

        mVectorOngoingConferenceCallView.onActivityPause();
        mActiveWidgetsBanner.onActivityPause();

        // to have notifications for this room
        VectorApp.getInstance().getNotificationDrawerManager().setCurrentRoom(null);
    }

    @Override
    protected void onResume() {
        Log.d(LOG_TAG, "++ Resume the activity");
        super.onResume();

        if (null != mRoom) {
            // check if the room has been left from another client.
            if (mRoom.isReady()) {
                if (!mRoom.isMember()) {
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
            VectorApp.getInstance().getNotificationDrawerManager().setCurrentRoom(mRoom.getRoomId());

            // listen for room name or topic changes
            mRoom.addEventListener(mRoomEventListener);

            setEditTextHint(mVectorMessageListFragment.getCurrentSelectedEvent());

            mSyncInProgressView.setVisibility(VectorApp.isSessionSyncing(mSession) ? View.VISIBLE : View.GONE);
        } else {
            mSyncInProgressView.setVisibility(View.GONE);
        }

        mSession.getDataHandler().addListener(mGlobalEventListener);
        mSession.getDataHandler().addListener(mResourceLimitEventListener);

        Matrix.getInstance(this).addNetworkEventListener(mNetworkEventListener);

        // sanity checks
        if ((null != mRoom) && (null != Matrix.getInstance(this).getDefaultLatestChatMessageCache())) {
            String cachedText = Matrix.getInstance(this).getDefaultLatestChatMessageCache().getLatestText(this, mRoom.getRoomId());

            if (!cachedText.equals(mEditText.getText().toString())) {
                mIgnoreTextUpdate = true;
                mEditText.setText("");
                mEditText.append(cachedText);
                mIgnoreTextUpdate = false;
            }

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
        if (null != mVectorMessageListFragment.mMessageListView) {
            mVectorMessageListFragment.mMessageListView.lockSelectionOnResize();
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
                final Intent intent = new Intent(this, VectorCallViewActivity.class);
                intent.putExtra(VectorCallViewActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                intent.putExtra(VectorCallViewActivity.EXTRA_CALL_ID, mCallId);

                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        startActivity(intent);
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

        // init the auto-completion list from the room members
        mEditText.initAutoCompletions(mSession, mRoom);

        if (mReadMarkerManager != null) {
            mReadMarkerManager.onResume();
        }

        Log.d(LOG_TAG, "-- Resume the activity");
    }

    /**
     * Update the edit text hint. It depends on the encryption and on the currently selected event
     *
     * @param selectedEvent the currently selected event or null if no event is selected
     */
    private void setEditTextHint(@Nullable Event selectedEvent) {
        if (mRoom == null) {
            return;
        }

        if (mRoom.canReplyTo(selectedEvent)) {
            // User can reply to this event
            mEditText.setHint((mRoom.isEncrypted() && mSession.isCryptoEnabled()) ?
                    R.string.room_message_placeholder_reply_to_encrypted : R.string.room_message_placeholder_reply_to_not_encrypted);
        } else {
            // default hint
            mEditText.setHint((mRoom.isEncrypted() && mSession.isCryptoEnabled()) ?
                    R.string.room_message_placeholder_encrypted : R.string.room_message_placeholder_not_encrypted);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_FILES_REQUEST_CODE:
                case TAKE_IMAGE_REQUEST_CODE:
                case RECORD_AUDIO_REQUEST_CODE:
                    sendMediasIntent(data);
                    break;
                case RequestCodesKt.STICKER_PICKER_ACTIVITY_REQUEST_CODE:
                    sendSticker(data);
                    break;
                case GET_MENTION_REQUEST_CODE:
                    insertUserDisplayNameInTextEditor(data.getStringExtra(VectorMemberDetailsActivity.RESULT_MENTION_ID));
                    break;
                case INVITE_USER_REQUEST_CODE:
                    onActivityResultRoomInvite(data);
                    break;
                case UNREAD_PREVIEW_REQUEST_CODE:
                    mVectorMessageListFragment.scrollToBottom(0);
                    break;
                case CONFIRM_MEDIA_REQUEST_CODE:
                    List<RoomMediaMessage> sharedDataItems = new ArrayList<>(RoomMediaMessage.listRoomMediaMessages(data));
                    if (0 == sharedDataItems.size()) {
                        sharedDataItems.add(new RoomMediaMessage(Uri.parse(data.getStringExtra(MediaPreviewerActivity.EXTRA_CAMERA_PICTURE_URI))));
                    }
                    mVectorRoomMediasSender.sendMedias(sharedDataItems);
                    break;
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
        CommonActivityUtils.displayUnknownDevicesDialog(mSession,
                this,
                (MXUsersDevicesMap<MXDeviceInfo>) error.mExceptionData,
                false,
                new VectorUnknownDevicesFragment.IUnknownDevicesSendAnywayListener() {
                    @Override
                    public void onSendAnyway() {
                        mVectorMessageListFragment.resendUnsentMessages();
                        refreshNotificationsArea();
                    }
                });
    }

    @Override
    public void onConsentNotGiven(Event event, MatrixError matrixError) {
        refreshNotificationsArea();
        getConsentNotGivenHelper().displayDialog(matrixError);
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
                        Log.e(LOG_TAG, "## sendReadReceipt() : failed " + e.getMessage(), e);
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.e(LOG_TAG, "## sendReadReceipt() : failed " + e.getMessage(), e);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.e(LOG_TAG, "## sendReadReceipt() : failed " + e.getMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.e(LOG_TAG, "## sendReadReceipt() : failed " + e.getMessage(), e);
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

            Log.d(LOG_TAG, "## onScroll firstVisibleItem " + firstVisibleItem
                    + " visibleItemCount " + visibleItemCount
                    + " totalItemCount " + totalItemCount);
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

        if (mNotificationsArea != null) {
            mNotificationsArea.setScrollState(scrollState);
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
    public int getMenuRes() {
        return R.menu.vector_room;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem searchInRoomMenuItem = menu.findItem(R.id.ic_action_search_in_room);
        MenuItem useMatrixAppsMenuItem = menu.findItem(R.id.ic_action_matrix_apps);
        MenuItem resendUnsentMenuItem = menu.findItem(R.id.ic_action_room_resend_unsent);
        MenuItem deleteUnsentMenuItem = menu.findItem(R.id.ic_action_room_delete_unsent);
        MenuItem settingsMenuItem = menu.findItem(R.id.ic_action_room_settings);
        MenuItem leaveRoomMenuItem = menu.findItem(R.id.ic_action_room_leave);

        // the application is in a weird state
        // GA : mSession is null, mRoom is null
        // This is the case in the room preview for public rooms
        if (CommonActivityUtils.shouldRestartApp(this) || null == mSession || null == mRoom) {
            // Hide all items
            if (searchInRoomMenuItem != null) {
                searchInRoomMenuItem.setVisible(false);
            }
            if (useMatrixAppsMenuItem != null) {
                useMatrixAppsMenuItem.setVisible(false);
            }
            if (resendUnsentMenuItem != null) {
                resendUnsentMenuItem.setVisible(false);
            }
            if (deleteUnsentMenuItem != null) {
                deleteUnsentMenuItem.setVisible(false);
            }
            if (settingsMenuItem != null) {
                settingsMenuItem.setVisible(false);
            }
            if (leaveRoomMenuItem != null) {
                leaveRoomMenuItem.setVisible(false);
            }

            return true;
        }

        boolean hasIntegrationManager = Matrix.getWidgetManager(this) != null;

        // the menu is only displayed when the current activity does not display a timeline search
        if (TextUtils.isEmpty(mEventId) && (null == sRoomPreviewData)) {
            RoomMember member = mRoom.getMember(mSession.getMyUserId());

            // the server search does not work on encrypted rooms.
            if (searchInRoomMenuItem != null) {
                searchInRoomMenuItem.setVisible(!mRoom.isEncrypted());
            }
            if (useMatrixAppsMenuItem != null) {
                useMatrixAppsMenuItem.setVisible(false);
                //useMatrixAppsMenuItem.setVisible(hasIntegrationManager && TextUtils.isEmpty(mEventId) && null == sRoomPreviewData);
            }
            if (resendUnsentMenuItem != null) {
                resendUnsentMenuItem.setVisible(mHasUnsentEvents);
            }
            if (deleteUnsentMenuItem != null) {
                deleteUnsentMenuItem.setVisible(mHasUnsentEvents);
            }
            if (settingsMenuItem != null) {
                settingsMenuItem.setVisible(true);
            }
            // kicked / banned room
            if (leaveRoomMenuItem != null) {
                leaveRoomMenuItem.setVisible(member != null && !member.kickedOrBanned());
            }
        } else {
            // Hide all items
            if (searchInRoomMenuItem != null) {
                searchInRoomMenuItem.setVisible(false);
            }
            if (useMatrixAppsMenuItem != null) {
                useMatrixAppsMenuItem.setVisible(false);
            }
            if (resendUnsentMenuItem != null) {
                resendUnsentMenuItem.setVisible(false);
            }
            if (deleteUnsentMenuItem != null) {
                deleteUnsentMenuItem.setVisible(false);
            }
            if (settingsMenuItem != null) {
                settingsMenuItem.setVisible(false);
            }
            if (leaveRoomMenuItem != null) {
                leaveRoomMenuItem.setVisible(false);
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ic_action_matrix_apps:
                openIntegrationManagerActivity(null);
                return true;
            case R.id.ic_action_search_in_room:
                try {
                    enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

                    final Intent searchIntent = new Intent(this, VectorUnifiedSearchActivity.class);
                    searchIntent.putExtra(VectorUnifiedSearchActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
                    startActivity(searchIntent);
                } catch (Exception e) {
                    Log.i(LOG_TAG, "## onOptionsItemSelected(): ");
                }
                return true;
            case R.id.ic_action_room_settings:
                launchRoomDetails(VectorRoomDetailsActivity.PEOPLE_TAB_INDEX);
                return true;
            case R.id.ic_action_room_resend_unsent:
                mVectorMessageListFragment.resendUnsentMessages();
                refreshNotificationsArea();
                return true;
            case R.id.ic_action_room_delete_unsent:
                mVectorMessageListFragment.deleteUnsentEvents();
                refreshNotificationsArea();
                return true;
            case R.id.ic_action_room_leave:
                if (null != mRoom) {
                    Log.d(LOG_TAG, "Leave the room " + mRoom.getRoomId());
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.room_participants_leave_prompt_title)
                            .setMessage(R.string.room_participants_leave_prompt_msg)
                            .setPositiveButton(R.string.leave, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    showWaitingView();

                                    mRoom.leave(new ApiCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void info) {
                                            Log.d(LOG_TAG, "The room " + mRoom.getRoomId() + " is left");
                                            // close the activity
                                            finish();
                                        }

                                        private void onError(String errorMessage) {
                                            hideWaitingView();
                                            Log.e(LOG_TAG, "Cannot leave the room " + mRoom.getRoomId() + " : " + errorMessage);
                                        }

                                        @Override
                                        public void onNetworkError(Exception e) {
                                            onError(e.getLocalizedMessage());
                                        }

                                        @Override
                                        public void onMatrixError(MatrixError e) {
                                            if (MatrixError.M_CONSENT_NOT_GIVEN.equals(e.errcode)) {
                                                hideWaitingView();
                                                getConsentNotGivenHelper().displayDialog(e);
                                            } else {
                                                onError(e.getLocalizedMessage());
                                            }
                                        }

                                        @Override
                                        public void onUnexpectedError(Exception e) {
                                            onError(e.getLocalizedMessage());
                                        }
                                    });
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Open Integration Manager activity
     *
     * @param screenId to open a specific screen. Can be null
     */
    private void openIntegrationManagerActivity(@Nullable String screenId) {
        if (mRoom == null) {
            return;
        }

        WidgetsManager wm = Matrix.getWidgetManager(this);
        if (wm == null) {
            //Should not happen this action is not activated if no wm
            return;
        }

        final Intent intent = IntegrationManagerActivity.Companion.getIntent(this, mMyUserId, mRoom.getRoomId(), null, screenId);
        startActivity(intent);
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

        if (mRoom != null && mRoom.isOngoingConferenceCall()) {
            // if a conf is in progress, the user can join the established conf anyway
            Log.d(LOG_TAG, "## isUserAllowedToStartConfCall(): conference in progress");
            isAllowed = true;
        } else if ((null != mRoom) && (mRoom.getNumberOfMembers() > 2)) {
            PowerLevels powerLevels = mRoom.getState().getPowerLevels();

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
        new AlertDialog.Builder(this)
                .setTitle(R.string.missing_permissions_title_to_start_conf_call)
                .setMessage(R.string.missing_permissions_to_start_conf_call)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    /**
     * Start an IP call with the management of the corresponding permissions.
     * According to the IP call, the corresponding permissions are asked: {@link im.vector.util.PermissionsToolsKt#PERMISSIONS_FOR_AUDIO_IP_CALL}
     * or {@link im.vector.util.PermissionsToolsKt#PERMISSIONS_FOR_VIDEO_IP_CALL}.
     */
    private void displayVideoCallIpDialog() {
        // hide the header room
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        new AlertDialog.Builder(this)
                .setAdapter(new DialogCallAdapter(this), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onCallItemClicked(which);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * @param which 0 for voice call, 1 for video call
     */
    private void onCallItemClicked(int which) {
        final boolean isVideoCall;
        final int permissions;
        final int requestCode;

        if (which == 0) {
            isVideoCall = false;
            permissions = PermissionsToolsKt.PERMISSIONS_FOR_AUDIO_IP_CALL;
            requestCode = PermissionsToolsKt.PERMISSION_REQUEST_CODE_AUDIO_CALL;
        } else {
            isVideoCall = true;
            permissions = PermissionsToolsKt.PERMISSIONS_FOR_VIDEO_IP_CALL;
            requestCode = PermissionsToolsKt.PERMISSION_REQUEST_CODE_VIDEO_CALL;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(VectorRoomActivity.this)
                .setTitle(R.string.dialog_title_confirmation);

        if (isVideoCall) {
            builder.setMessage(getString(R.string.start_video_call_prompt_msg));
        } else {
            builder.setMessage(getString(R.string.start_voice_call_prompt_msg));
        }

        builder
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (PermissionsToolsKt.checkPermissions(permissions, VectorRoomActivity.this, requestCode)) {
                            startIpCall(PreferencesManager.useJitsiConfCall(VectorRoomActivity.this), isVideoCall);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Manage widget
     *
     * @param widget       the widget
     * @param aIsVideoCall true if it is a video call
     */
    private void launchJitsiActivity(Widget widget, boolean aIsVideoCall) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // Display a error dialog for old API
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_title_error)
                    .setMessage(R.string.error_jitsi_not_supported_on_old_device)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        } else {
            //Here check native widget perm

            String domain = UrlUtilKt.extractDomain(JitsiCallActivity.JITSI_SERVER_URL);
            if (domain == null) return; //display a toast?
            boolean isAllowed = mSession.getIntegrationManager().isNativeWidgetAllowed("jitsi", domain);
            if (isAllowed) {
                final Intent intent = new Intent(this, JitsiCallActivity.class);
                intent.putExtra(JitsiCallActivity.EXTRA_WIDGET_ID, widget);
                intent.putExtra(JitsiCallActivity.EXTRA_ENABLE_VIDEO, aIsVideoCall);
                startActivity(intent);
            } else {
                //we need to prompt for permissions
                RoomWidgetPermissionBottomSheet bs = RoomWidgetPermissionBottomSheet.Companion
                        .newInstance(mSession.getMyUserId(), widget);
                bs.setOnFinish((accepted) -> {
                    if (accepted) {
                        final Intent intent = new Intent(this, JitsiCallActivity.class);
                        intent.putExtra(JitsiCallActivity.EXTRA_WIDGET_ID, widget);
                        intent.putExtra(JitsiCallActivity.EXTRA_ENABLE_VIDEO, aIsVideoCall);
                        startActivity(intent);
                    }
                    return Unit.INSTANCE;
                });
                bs.show(getSupportFragmentManager(), "JitsiPerm");
            }
        }
    }

    /**
     * Start a jisti call
     *
     * @param aIsVideoCall true if the call is a video one
     */
    private void startJitsiCall(final boolean aIsVideoCall) {
        WidgetsManager wm = Matrix.getWidgetManager(this);
        if (wm != null) {
            enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
            showWaitingView();

            wm.createJitsiWidget(mSession, mRoom, aIsVideoCall, new ApiCallback<Widget>() {
                @Override
                public void onSuccess(Widget widget) {
                    hideWaitingView();

                    launchJitsiActivity(widget, aIsVideoCall);
                }

                private void onError(String errorMessage) {
                    hideWaitingView();
                    Toast.makeText(VectorRoomActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
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
    }

    /**
     * Start an IP call: audio call if aIsVideoCall is false or video call if aIsVideoCall
     * is true.
     *
     * @param useJitsiCall true to use jitsi calls
     * @param aIsVideoCall true to video call, false to audio call
     */
    private void startIpCall(final boolean useJitsiCall, final boolean aIsVideoCall) {
        if (mRoom == null) {
            return;
        }

        if ((mRoom.getNumberOfMembers() > 2) && useJitsiCall) {
            startJitsiCall(aIsVideoCall);
            return;
        }

        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
        showWaitingView();

        // create the call object
        mSession.mCallsManager.createCallInRoom(mRoom.getRoomId(), aIsVideoCall, new ApiCallback<IMXCall>() {
            @Override
            public void onSuccess(final IMXCall call) {
                Log.d(LOG_TAG, "## startIpCall(): onSuccess");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideWaitingView();

                        final Intent intent = new Intent(VectorRoomActivity.this, VectorCallViewActivity.class);

                        intent.putExtra(VectorCallViewActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                        intent.putExtra(VectorCallViewActivity.EXTRA_CALL_ID, call.getCallId());

                        startActivity(intent);
                    }
                });
            }

            private void onError(final String errorMessage) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideWaitingView();
                        Toast.makeText(VectorRoomActivity.this,
                                getString(R.string.cannot_start_call) + " (" + errorMessage + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## startIpCall(): onNetworkError Msg=" + e.getMessage(), e);
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## startIpCall(): onMatrixError Msg=" + e.getLocalizedMessage());

                if (e instanceof MXCryptoError) {
                    MXCryptoError cryptoError = (MXCryptoError) e;
                    if (MXCryptoError.UNKNOWN_DEVICES_CODE.equals(cryptoError.errcode)) {
                        hideWaitingView();
                        CommonActivityUtils.displayUnknownDevicesDialog(mSession,
                                VectorRoomActivity.this,
                                (MXUsersDevicesMap<MXDeviceInfo>) cryptoError.mExceptionData,
                                true,
                                new VectorUnknownDevicesFragment.IUnknownDevicesSendAnywayListener() {
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
                Log.e(LOG_TAG, "## startIpCall(): onUnexpectedError Msg=" + e.getLocalizedMessage(), e);
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
        mSendImageView.setEnabled(false);
        mIsMarkDowning = true;

        String textToSend = mEditText.getText().toString().trim();

        final boolean handleSlashCommand;
        if (textToSend.startsWith("\\/")) {
            handleSlashCommand = false;
            textToSend = textToSend.substring(1);
        } else {
            handleSlashCommand = true;
        }

        VectorApp.markdownToHtml(textToSend, new VectorMarkdownParser.IVectorMarkdownParserListener() {
            @Override
            public void onMarkdownParsed(final String text, final String htmlText) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mSendImageView.setEnabled(true);
                        mIsMarkDowning = false;
                        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                        sendMessage(text, TextUtils.equals(text, htmlText) ? null : htmlText, Message.FORMAT_MATRIX_HTML, handleSlashCommand);
                        mEditText.setText("");
                    }
                });
            }
        });
    }

    /**
     * Send a text message with its formatted format
     *
     * @param body               the text message.
     * @param formattedBody      the formatted message
     * @param format             the message format
     * @param handleSlashCommand true to try to handle a Slash command
     */
    public void sendMessage(String body, String formattedBody, String format, boolean handleSlashCommand) {
        if (!TextUtils.isEmpty(body)) {
            if (!handleSlashCommand
                    || !SlashCommandsParser.manageSplashCommand(this, mSession, mRoom, body, formattedBody, format)) {
                Event currentSelectedEvent = mVectorMessageListFragment.getCurrentSelectedEvent();

                cancelSelectionMode();

                mVectorMessageListFragment.sendTextMessage(body, formattedBody, currentSelectedEvent, format);
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
    private void sendMediasIntent(Intent intent) {
        // sanity check
        if ((null == intent) && (null == mLatestTakePictureCameraUri)) {
            return;
        }

        List<RoomMediaMessage> sharedDataItems = new ArrayList<>();

        if (null != intent) {
            sharedDataItems = new ArrayList<>(RoomMediaMessage.listRoomMediaMessages(intent));
        }

        // check the extras
        if ((0 == sharedDataItems.size()) && (null != intent)) {
            Bundle bundle = intent.getExtras();
            // sanity checks
            if (null != bundle) {
                if (bundle.containsKey(Intent.EXTRA_TEXT)) {
                    mEditText.append(bundle.getString(Intent.EXTRA_TEXT));

                    mEditText.post(new Runnable() {
                        @Override
                        public void run() {
                            mEditText.setSelection(mEditText.getText().length());
                        }
                    });
                }
            }
        }
        boolean hasItemToShare = !sharedDataItems.isEmpty();
        boolean isTextOnly = sharedDataItems.size() == 1
                && "text/plain".equals(sharedDataItems.get(0).getMimeType(this));
        boolean shouldPreviewMedia = PreferencesManager.previewMediaWhenSending(this);

        if (hasItemToShare && !isTextOnly && shouldPreviewMedia) {
            if (null != intent) {
                intent.setClass(this, MediaPreviewerActivity.class);
            } else {
                intent = new Intent(this, MediaPreviewerActivity.class);
            }
            intent.setExtrasClassLoader(RoomMediaMessage.class.getClassLoader());
            if (mRoom != null) {
                intent.putExtra(MediaPreviewerActivity.EXTRA_ROOM_TITLE, mRoom.getRoomDisplayName(this));
            }
            if (null != mLatestTakePictureCameraUri) {
                intent.putExtra(MediaPreviewerActivity.EXTRA_CAMERA_PICTURE_URI, mLatestTakePictureCameraUri);
            }
            startActivityForResult(intent, CONFIRM_MEDIA_REQUEST_CODE);
        } else {
            if (null != mLatestTakePictureCameraUri) {
                if (0 == sharedDataItems.size()) {
                    sharedDataItems.add(new RoomMediaMessage(Uri.parse(mLatestTakePictureCameraUri)));
                }
            }
            mVectorRoomMediasSender.sendMedias(sharedDataItems);
        }

        mLatestTakePictureCameraUri = null;
    }

    /**
     * Send a sticker
     *
     * @param data
     */
    private void sendSticker(Intent data) {
        if (mRoom == null) {
            return;
        }

        String contentStr = StickerPickerActivity.Companion.getResultContent(data);

        Event event = new Event(Event.EVENT_TYPE_STICKER,
                new JsonParser().parse(contentStr).getAsJsonObject(),
                mSession.getCredentials().userId,
                mRoom.getRoomId());

        mVectorMessageListFragment.sendStickerMessage(event);
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
        if (!PreferencesManager.sendTypingNotifs(this)) {
            Log.d(LOG_TAG, "##handleTypingNotification() : the typing notifications are disabled");
            return;
        }

        if (mRoom == null) {
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
                                handleTypingNotification(0 != mLastTypingDate);
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

        mRoom.sendTypingNotification(typingStatus, notificationTimeoutMS, new SimpleApiCallback<Void>(this) {
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
        if (mRoom == null) {
            return;
        }

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

            mRoom.sendTypingNotification(false, -1, new SimpleApiCallback<Void>(this) {
                @Override
                public void onSuccess(Void info) {
                    // Ignore
                }
            });
        }
    }

    //================================================================================
    // Actions
    //================================================================================

    /**
     * Launch the room details activity with a selected tab.
     *
     * @param selectedTab the selected tab index.
     */
    private void launchRoomDetails(int selectedTab) {
        if (mSession != null && mRoom != null) {
            enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

            // pop to the home activity
            Intent intent = new Intent(this, VectorRoomDetailsActivity.class);
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
     * Launch audio recorder intent
     */
    private void launchAudioRecorderIntent() {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        ExternalApplicationsUtilKt.openSoundRecorder(this, RECORD_AUDIO_REQUEST_CODE);
    }

    /**
     * Launch the files selection intent
     */
    private void launchFileSelectionIntent() {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        ExternalApplicationsUtilKt.openFileSelection(this, null, true, REQUEST_FILES_REQUEST_CODE);
    }

    private void startStickerPickerActivity() {
        // Search for the sticker picker widget in the user account
        Map<String, Object> userWidgets = mSession.getUserWidgets();

        String stickerWidgetUrl = null;
        String stickerWidgetId = null;

        for (Object o : userWidgets.values()) {
            if (o instanceof Map) {
                Object content = ((Map) o).get("content");
                if (content != null && content instanceof Map) {
                    Object type = ((Map) content).get("type");
                    if (type != null && type instanceof String && type.equals(StickerPickerActivity.WIDGET_NAME)) {
                        stickerWidgetUrl = (String) ((Map) content).get("url");
                        stickerWidgetId = (String) ((Map) o).get("id");
                        break;
                    }
                }
            }
        }

        if (TextUtils.isEmpty(stickerWidgetUrl)) {
            // The Sticker picker widget is not installed yet. Propose the user to install it
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            // Use the builder context
            View v = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_no_sticker_pack, null);

            builder
                    .setView(v)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Open integration manager, to the sticker installation page
                            openIntegrationManagerActivity("type_" + StickerPickerActivity.WIDGET_NAME);
                        }
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
        } else {
            if (mRoom == null) {
                return;
            }

            Intent intent = StickerPickerActivity.Companion.getIntent(this, mMyUserId, mRoom.getRoomId(), stickerWidgetUrl, stickerWidgetId);
            startActivityForResult(intent, RequestCodesKt.STICKER_PICKER_ACTIVITY_REQUEST_CODE);
        }

    }

    /**
     * Launch the camera
     */
    private void launchNativeVideoRecorder() {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        ExternalApplicationsUtilKt.openVideoRecorder(this, TAKE_IMAGE_REQUEST_CODE);
    }

    /**
     * Launch the camera
     */
    private void launchNativeCamera() {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        mLatestTakePictureCameraUri = ExternalApplicationsUtilKt.openCamera(this, CAMERA_VALUE_TITLE, TAKE_IMAGE_REQUEST_CODE);
    }

    /**
     * Launch the camera
     */
    private void launchCamera() {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        Intent intent = new Intent(this, VectorMediaPickerActivity.class);
        intent.putExtra(VectorMediaPickerActivity.EXTRA_VIDEO_RECORDING_MODE, true);
        startActivityForResult(intent, TAKE_IMAGE_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (0 == permissions.length) {
            Log.d(LOG_TAG, "## onRequestPermissionsResult(): cancelled " + requestCode);
        } else if (requestCode == PermissionsToolsKt.PERMISSION_REQUEST_CODE_LAUNCH_CAMERA
                || requestCode == PermissionsToolsKt.PERMISSION_REQUEST_CODE_LAUNCH_NATIVE_CAMERA
                || requestCode == PermissionsToolsKt.PERMISSION_REQUEST_CODE_LAUNCH_NATIVE_VIDEO_CAMERA) {
            boolean isCameraPermissionGranted = false;
            boolean isWritePermissionGranted = false;

            for (int i = 0; i < permissions.length; i++) {
                Log.d(LOG_TAG, "## onRequestPermissionsResult(): " + permissions[i] + "=" + grantResults[i]);

                if (Manifest.permission.CAMERA.equals(permissions[i])) {
                    if (PackageManager.PERMISSION_GRANTED == grantResults[i]) {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): CAMERA permission granted");
                        isCameraPermissionGranted = true;
                    } else {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): CAMERA permission not granted");
                    }
                }

                if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[i])) {
                    if (PackageManager.PERMISSION_GRANTED == grantResults[i]) {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): WRITE_EXTERNAL_STORAGE permission granted");
                        isWritePermissionGranted = true;
                    } else {
                        Log.d(LOG_TAG, "## onRequestPermissionsResult(): WRITE_EXTERNAL_STORAGE permission not granted");
                    }
                }
            }

            // Because external storage permission is not mandatory to launch the camera,
            // external storage permission is not tested.
            if (isCameraPermissionGranted) {
                if (requestCode == PermissionsToolsKt.PERMISSION_REQUEST_CODE_LAUNCH_CAMERA) {
                    launchCamera();
                } else if (requestCode == PermissionsToolsKt.PERMISSION_REQUEST_CODE_LAUNCH_NATIVE_CAMERA) {
                    if (isWritePermissionGranted) {
                        launchNativeCamera();
                    } else {
                        Toast.makeText(this, getString(R.string.missing_permissions_error), Toast.LENGTH_SHORT).show();
                    }
                } else if (requestCode == PermissionsToolsKt.PERMISSION_REQUEST_CODE_LAUNCH_NATIVE_VIDEO_CAMERA) {
                    if (isWritePermissionGranted) {
                        launchNativeVideoRecorder();
                    } else {
                        Toast.makeText(this, getString(R.string.missing_permissions_error), Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, getString(R.string.missing_permissions_warning), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == PermissionsToolsKt.PERMISSION_REQUEST_CODE_AUDIO_CALL) {
            if (PermissionsToolsKt.onPermissionResultAudioIpCall(this, grantResults)) {
                startIpCall(PreferencesManager.useJitsiConfCall(this), false);
            }
        } else if (requestCode == PermissionsToolsKt.PERMISSION_REQUEST_CODE_VIDEO_CALL) {
            if (PermissionsToolsKt.onPermissionResultVideoIpCall(this, grantResults)) {
                startIpCall(PreferencesManager.useJitsiConfCall(this), true);
            }
        } else {
            // Transmit to Fragment
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Display UI buttons according to user input text.
     */
    private void manageSendMoreButtons() {
        int img = R.drawable.ic_material_file;
        if (!PreferencesManager.sendMessageWithEnter(this) && mEditText.getText().length() > 0) {
            img = R.drawable.ic_material_send_green;
        } else {
            switch (PreferencesManager.getSelectedDefaultMediaSource(this)) {
                case MEDIA_SOURCE_VOICE:
                    if (PreferencesManager.isSendVoiceFeatureEnabled(this)) {
                        img = R.drawable.vector_micro_green;
                    }
                    break;
                case MEDIA_SOURCE_STICKER:
                    img = R.drawable.ic_send_sticker;
                    break;
                case MEDIA_SOURCE_PHOTO:
                    img = R.drawable.ic_material_camera;
                    break;
                case MEDIA_SOURCE_VIDEO:
                    img = R.drawable.ic_material_videocam;
                    break;
            }
        }
        mSendImageView.setImageResource(img);
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
     * Insert a text in the text editor
     *
     * @param text the text
     */
    public void insertTextInTextEditor(String text) {
        // another user
        if (TextUtils.isEmpty(mEditText.getText())) {
            mEditText.append(text);
        } else {
            mEditText.getText().insert(mEditText.getSelectionStart(), text + " ");
        }
    }

    /**
     * Insert an user displayname  in the message editor.
     *
     * @param text the text to insert.
     */
    public void insertUserDisplayNameInTextEditor(String text) {
        if (null != text) {
            boolean vibrate = false;

            if (TextUtils.equals(mSession.getMyUser().displayname, text)) {
                // current user
                if (TextUtils.isEmpty(mEditText.getText())) {
                    mEditText.append(SlashCommandsParser.SlashCommand.EMOTE.getCommand() + " ");
                    mEditText.setSelection(mEditText.getText().length());
                    vibrate = true;
                }
            } else {
                // another user
                if (TextUtils.isEmpty(mEditText.getText())) {
                    // Ensure displayName will not be interpreted as a Slash command
                    if (text.startsWith("/")) {
                        mEditText.append("\\");
                    }
                    mEditText.append(sanitizeDisplayname(text) + ": ");
                } else {
                    mEditText.getText().insert(mEditText.getSelectionStart(), sanitizeDisplayname(text) + " ");
                }

                vibrate = true;
            }

            if (vibrate && PreferencesManager.vibrateWhenMentioning(this)) {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if ((null != v) && v.hasVibrator()) {
                    v.vibrate(100);
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

    /* ==========================================================================================
     * Implement VectorMessageListFragmentListener
     * ========================================================================================== */

    @Override
    public void showPreviousEventsLoadingWheel() {
        mBackProgressView.setVisibility(View.VISIBLE);
    }

    @Override
    public void hidePreviousEventsLoadingWheel() {
        mBackProgressView.setVisibility(View.GONE);
    }

    @Override
    public void showNextEventsLoadingWheel() {
        mForwardProgressView.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideNextEventsLoadingWheel() {
        mForwardProgressView.setVisibility(View.GONE);
    }

    @Override
    public void showMainLoadingWheel() {
        mMainProgressView.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideMainLoadingWheel() {
        mMainProgressView.setVisibility(View.GONE);
    }

    @Override
    public void onSelectedEventChange(@Nullable Event currentSelectedEvent) {
        // Update hint
        setEditTextHint(currentSelectedEvent);
    }

    //================================================================================
    // Notifications area management (... is typing and so on)
    //================================================================================

    /**
     * Refresh the notifications area.
     */
    private void refreshNotificationsArea() {
        // sanity check
        // might happen when the application is logged out (NPE reported on store)
        if (null == mSession
                || (null == mSession.getDataHandler())
                || (null == mSession.getDataHandler().getStore())
                || (null == mRoom)
                || (null != sRoomPreviewData)) {
            return;
        }
        final LimitResourceState limitResourceState = mResourceLimitEventListener.getLimitResourceState();
        final MatrixError hardResourceLimitExceededError = mSession.getDataHandler().getResourceLimitExceededError();
        final MatrixError softResourceLimitExceededError = limitResourceState.softErrorOrNull();

        NotificationAreaView.State state = NotificationAreaView.State.Default.INSTANCE;
        mHasUnsentEvents = false;
        if (!mIsUnreadPreviewMode && !TextUtils.isEmpty(mEventId)) {
            state = NotificationAreaView.State.Hidden.INSTANCE;
        } else if (hardResourceLimitExceededError != null) {
            state = new NotificationAreaView.State.ResourceLimitExceededError(false, hardResourceLimitExceededError);
        } else if (softResourceLimitExceededError != null) {
            state = new NotificationAreaView.State.ResourceLimitExceededError(true, softResourceLimitExceededError);
        } else if (!Matrix.getInstance(this).isConnected()) {
            state = NotificationAreaView.State.ConnectionError.INSTANCE;
        } else if (mIsUnreadPreviewMode) {
            state = NotificationAreaView.State.UnreadPreview.INSTANCE;
        } else {
            final List<Event> undeliveredEvents = mSession.getDataHandler().getStore().getUndeliveredEvents(mRoom.getRoomId());
            final List<Event> unknownDeviceEvents = mSession.getDataHandler().getStore().getUnknownDeviceEvents(mRoom.getRoomId());
            boolean hasUndeliverableEvents = (undeliveredEvents != null) && (undeliveredEvents.size() > 0);
            boolean hasUnknownDeviceEvents = (unknownDeviceEvents != null) && (unknownDeviceEvents.size() > 0);
            if (hasUndeliverableEvents || hasUnknownDeviceEvents) {
                mHasUnsentEvents = true;
                state = new NotificationAreaView.State.UnsentEvents(hasUndeliverableEvents, hasUnknownDeviceEvents);
            } else if ((null != mIsScrolledToTheBottom) && (!mIsScrolledToTheBottom)) {
                int unreadCount = 0;
                final RoomSummary summary = mRoom.getDataHandler().getStore().getSummary(mRoom.getRoomId());
                if (summary != null) {
                    unreadCount = mRoom.getDataHandler().getStore().eventsCountAfter(mRoom.getRoomId(), summary.getReadReceiptEventId());
                }
                state = new NotificationAreaView.State.ScrollToBottom(unreadCount, mLatestTypingMessage);
            } else if (!TextUtils.isEmpty(mLatestTypingMessage)) {
                state = new NotificationAreaView.State.Typing(mLatestTypingMessage);
            } else if (mRoom.getState().isVersioned()) {
                final RoomTombstoneContent roomTombstoneContent = mRoom.getState().getRoomTombstoneContent();
                final List<Event> events = mRoom.getState().getStateEvents(new HashSet<>(Arrays.asList(Event.EVENT_TYPE_STATE_ROOM_TOMBSTONE)));

                String sender = "";
                if (events != null && !events.isEmpty()) {
                    sender = events.get(0).sender;
                }

                state = new NotificationAreaView.State.Tombstone(roomTombstoneContent, sender);
            }
        }
        mNotificationsArea.render(state);

        supportInvalidateOptionsMenu();
    }

    /**
     * Refresh the call buttons display.
     */
    private void refreshCallButtons(boolean refreshOngoingConferenceCallView) {
        if (mRoom == null) {
            return;
        }

        if ((null == sRoomPreviewData) && (null == mEventId) && canSendMessages(mRoom.getState())) {
            boolean isCallSupported = mRoom.canPerformCall() && mSession.isVoipCallSupported();
            IMXCall call = CallsManager.getSharedInstance().getActiveCall();
            Widget activeWidget = mVectorOngoingConferenceCallView.getActiveWidget();

            if ((null == call) && (null == activeWidget)) {
                mStartCallLayout.setVisibility((isCallSupported && (mEditText.getText().length() == 0
                        || PreferencesManager.sendMessageWithEnter(this))) ? View.VISIBLE : View.GONE);
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
    private void onRoomTyping() {
        mLatestTypingMessage = null;

        if (mRoom == null) {
            return;
        }

        List<String> typingUsers = mRoom.getTypingUsers();

        if (!typingUsers.isEmpty()) {
            String myUserId = mSession.getMyUserId();

            // get the room member names
            List<String> names = new ArrayList<>();

            for (int i = 0; i < typingUsers.size(); i++) {
                RoomMember member = mRoom.getMember(typingUsers.get(i));

                // check if the user is known and not oneself
                if ((null != member) && !TextUtils.equals(myUserId, member.getUserId()) && (null != member.displayname)) {
                    names.add(member.displayname);
                }
            }

            if (names.isEmpty()) {
                // nothing to display
                mLatestTypingMessage = null;
            } else if (names.size() == 1) {
                mLatestTypingMessage = getString(R.string.room_one_user_is_typing, names.get(0));
            } else if (names.size() == 2) {
                mLatestTypingMessage = getString(R.string.room_two_users_are_typing, names.get(0), names.get(1));
            } else {
                mLatestTypingMessage = getString(R.string.room_many_users_are_typing, names.get(0), names.get(1));
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
        } else if ((null != sRoomPreviewData) && (null != sRoomPreviewData.getPublicRoom())) {
            topic = sRoomPreviewData.getPublicRoom().topic;
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
            VectorUtils.loadUserAvatar(this,
                    sRoomPreviewData.getSession(), mActionBarHeaderRoomAvatar, sRoomPreviewData.getRoomAvatarUrl(), sRoomPreviewData.getRoomId(), roomName);
        }
    }


    /**
     * Create a custom action bar layout to process the room header view.
     * <p>
     * This action bar layout will contain a title, a topic and an arrow.
     * The arrow is updated (down/up) according to if the room header is
     * displayed or not.
     */
    @OnClick(R.id.open_chat_header_arrow)
    void OnOpenHeaderClick() {
        // display/hide the header view
        if (null != mRoomHeaderView) {
            if (View.GONE == mRoomHeaderView.getVisibility()) {
                enableActionBarHeader(SHOW_ACTION_BAR_HEADER);
            } else {
                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
            }
        }
    }

    @OnClick(R.id.header_texts_container)
    void onTextsContainerClick() {
        if (TextUtils.isEmpty(mEventId) && (null == sRoomPreviewData)) {
            enableActionBarHeader(SHOW_ACTION_BAR_HEADER);
        }
    }

    // last position
    private float mStartX;
    private float mStartY;

    // add touch listener on the header view itself
    @OnTouch(R.id.action_bar_header)
    boolean onRoomHeaderTouch(View v, MotionEvent event) {
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

    /**
     * Set the title value in the action bar and in the
     * room header layout
     */
    private void setTitle() {
        String titleToApply = mDefaultRoomName;
        if ((null != mSession) && (null != mRoom)) {
            titleToApply = mRoom.getRoomDisplayName(this);

            if (TextUtils.isEmpty(titleToApply)) {
                titleToApply = mDefaultRoomName;
            }

            // in context mode, add search to the title.
            if (!TextUtils.isEmpty(mEventId) && !mIsUnreadPreviewMode) {
                titleToApply = getText(R.string.search) + " : " + titleToApply;
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
        if (null != sRoomPreviewData) {
            mActionBarHeaderRoomName.setText(sRoomPreviewData.getRoomName());
        } else if (null != mRoom) {
            mActionBarHeaderRoomName.setText(mRoom.getRoomDisplayName(this));
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

            if ((null != sRoomPreviewData) && (null != sRoomPreviewData.getRoomState())) {
                value = sRoomPreviewData.getRoomState().topic;
            } else if ((null != sRoomPreviewData) && (null != sRoomPreviewData.getPublicRoom())) {
                value = sRoomPreviewData.getPublicRoom().topic;
            } else if (null != mRoom) {
                value = mRoom.isReady() ? mRoom.getTopic() : mDefaultTopic;
            }

            // if topic value is empty, just hide the topic TextView
            if (TextUtils.isEmpty(value)) {
                mActionBarHeaderRoomTopic.setVisibility(View.GONE);
            } else {
                mActionBarHeaderRoomTopic.setVisibility(View.VISIBLE);

                SpannableStringBuilder strBuilder = new SpannableStringBuilder(value);
                MatrixURLSpan.refreshMatrixSpans(strBuilder, mVectorMessageListFragment);
                mActionBarHeaderRoomTopic.setText(strBuilder);

                URLSpan[] urls = strBuilder.getSpans(0, value.length(), URLSpan.class);

                if ((null != urls) && (urls.length > 0)) {
                    for (URLSpan span : urls) {
                        makeLinkClickable(strBuilder, span, value);
                    }
                }
            }
        }
    }

    /**
     * Trap the clicked URL.
     *
     * @param strBuilder the input string
     * @param span       the URL
     * @param value      roomAlias, roomId, groupId, eventId, etc.
     */
    private void makeLinkClickable(SpannableStringBuilder strBuilder, final URLSpan span, final String value) {
        int start = strBuilder.getSpanStart(span);
        int end = strBuilder.getSpanEnd(span);

        if (start >= 0 && end >= 0) {
            int flags = strBuilder.getSpanFlags(span);

            ClickableSpan clickable = new ClickableSpan() {
                public void onClick(View view) {
                    if (null != mVectorMessageListFragment) {
                        mVectorMessageListFragment.onURLClick(Uri.parse(PermalinkUtils.createPermalink(value)));
                    }
                }
            };

            strBuilder.setSpan(clickable, start, end, flags);
            strBuilder.removeSpan(span);
        }
    }

    /**
     * Tell if the user can send a message in this room.
     *
     * @return true if the user is allowed to send messages in this room.
     */
    private boolean canSendMessages(@NonNull final RoomState state) {
        boolean canSendMessage = !state.isVersioned();
        if (canSendMessage) {
            final PowerLevels powerLevels = state.getPowerLevels();
            canSendMessage = (powerLevels != null && powerLevels.maySendMessage(mMyUserId));
        }
        if (canSendMessage) {
            canSendMessage = mSession.getDataHandler().getResourceLimitExceededError() == null;
        }
        return canSendMessage;
    }

    /**
     * Check if the user can send a message in this room
     */
    private void checkSendEventStatus() {
        if ((null != mRoom) && (null != mRoom.getState())) {
            final RoomState state = mRoom.getState();

            if (canSendMessages(state)) {
                mBottomLayout.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mBottomSeparator.setVisibility(View.VISIBLE);
                mSendingMessagesLayout.setVisibility(View.VISIBLE);
                mCanNotPostTextView.setVisibility(View.GONE);
            } else if (state.isVersioned() || mSession.getDataHandler().getResourceLimitExceededError() != null) {
                mBottomSeparator.setVisibility(View.GONE);
                mBottomLayout.getLayoutParams().height = 0;
            } else {
                mBottomSeparator.setVisibility(View.GONE);
                mSendingMessagesLayout.setVisibility(View.GONE);
                mCanNotPostTextView.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * Display the active members count / members count in the expendable header.
     */
    private void updateRoomHeaderMembersStatus() {
        if (null != mActionBarHeaderActiveMembersLayout) {
            // refresh only if the action bar is hidden
            if (mActionBarCustomTitle.getVisibility() == View.GONE) {
                if (mRoom != null || sRoomPreviewData != null) {
                    // update the members status: "active members"/"members"

                    final RoomState roomState = (null != sRoomPreviewData) ? sRoomPreviewData.getRoomState() : mRoom.getState();

                    if (null != roomState) {
                        // display the both action buttons only when it makes sense
                        // i.e not a room preview
                        boolean hideMembersButtons = (null == mRoom) || !TextUtils.isEmpty(mEventId) || (null != sRoomPreviewData);
                        mActionBarHeaderActiveMembersListButton.setVisibility(hideMembersButtons ? View.INVISIBLE : View.VISIBLE);
                        mActionBarHeaderActiveMembersInviteButton.setVisibility(hideMembersButtons ? View.INVISIBLE : View.VISIBLE);

                        // Display what we have synchronously first. Use 0 as active members number
                        if (mRoom != null) {
                            setMemberHeaderText(0, mRoom.getNumberOfJoinedMembers());

                            // Then request the list of members asynchronously
                            mRoom.getDisplayableMembersAsync(new ApiCallback<List<RoomMember>>() {
                                @Override
                                public void onSuccess(List<RoomMember> members) {
                                    int joinedMembersCount = 0;
                                    int activeMembersCount = 0;

                                    for (RoomMember member : members) {
                                        if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
                                            joinedMembersCount++;

                                            User user = mSession.getDataHandler().getStore().getUser(member.getUserId());

                                            if ((null != user) && user.isActive()) {
                                                activeMembersCount++;
                                            }
                                        }
                                    }

                                    setMemberHeaderText(activeMembersCount, joinedMembersCount);

                                    if (joinedMembersCount == 0) {
                                        checkPublicRoom();
                                    }
                                }

                                @Override
                                public void onNetworkError(Exception e) {
                                    checkPublicRoom();
                                }

                                @Override
                                public void onMatrixError(MatrixError e) {
                                    checkPublicRoom();
                                }

                                @Override
                                public void onUnexpectedError(Exception e) {
                                    checkPublicRoom();
                                }
                            });
                        } else if (sRoomPreviewData != null) {
                            checkPublicRoom();
                        }
                    } else if (sRoomPreviewData != null && sRoomPreviewData.getPublicRoom() != null) {
                        checkPublicRoom();
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

    private void checkPublicRoom() {
        if (sRoomPreviewData != null && sRoomPreviewData.getPublicRoom() != null) {
            setMemberHeaderText(0, sRoomPreviewData.getPublicRoom().numJoinedMembers);
        } else {
            // Should not happen
            setMemberHeaderText(0, 0);
        }
    }

    /**
     * Set the text in Room header about active/joined member count
     *
     * @param activeMembersCount number of active members
     * @param joinedMembersCount number of joined members
     */
    private void setMemberHeaderText(int activeMembersCount, int joinedMembersCount) {
        String text;

        if (joinedMembersCount == 1) {
            text = getString(R.string.room_title_one_member);
        } else if (null != sRoomPreviewData) {
            text = getResources().getQuantityString(R.plurals.room_title_members, joinedMembersCount, joinedMembersCount);
        } else {
            text = getString(R.string.room_header_online_members, joinedMembersCount, activeMembersCount);
        }

        mActionBarHeaderActiveMembersTextView.setText(text);
        mActionBarHeaderActiveMembersLayout.setVisibility(View.VISIBLE);
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
            toolbar.setBackgroundColor(Color.TRANSPARENT);
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
                toolbar.setBackgroundColor(ThemeUtils.INSTANCE.getColor(this, R.attr.colorPrimary));
            }
        }
    }

    //================================================================================
    // Kick / ban mode management
    //================================================================================

    /**
     * Manage the room preview buttons area
     */
    private void manageBannedHeader(RoomMember member) {
        mRoomPreviewLayout.setVisibility(View.VISIBLE);

        TextView invitationTextView = findViewById(R.id.room_preview_invitation_textview);

        if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_BAN)) {
            invitationTextView.setText(getString(R.string.has_been_banned, mRoom.getRoomDisplayName(this), mRoom.getState().getMemberName(member.mSender)));
        } else {
            invitationTextView.setText(getString(R.string.has_been_kicked, mRoom.getRoomDisplayName(this), mRoom.getState().getMemberName(member.mSender)));
        }

        // On mobile side, the modal to allow to add a reason to ban/kick someone isn't yet implemented
        // That's why, we don't display the TextView "Motif :" for now.
        TextView subInvitationTextView = findViewById(R.id.room_preview_subinvitation_textview);
        if (!TextUtils.isEmpty(member.reason)) {
            final String reason = getString(R.string.reason_colon, member.reason);
            subInvitationTextView.setText(reason);
        } else {
            subInvitationTextView.setText(null);
        }

        Button joinButton = findViewById(R.id.button_join_room);

        if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_BAN)) {
            joinButton.setVisibility(View.INVISIBLE);
        } else {
            joinButton.setText(getString(R.string.rejoin));

            joinButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showWaitingView();
                    mSession.joinRoom(mRoom.getRoomId(), new ApiCallback<String>() {
                        @Override
                        public void onSuccess(String roomId) {
                            hideWaitingView();

                            Map<String, Object> params = new HashMap<>();

                            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                            params.put(VectorRoomActivity.EXTRA_ROOM_ID, mRoom.getRoomId());

                            // clear the activity stack to home activity
                            Intent intent = new Intent(VectorRoomActivity.this, VectorHomeActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

                            intent.putExtra(VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS, (HashMap) params);
                            startActivity(intent);
                        }

                        private void onError(String errorMessage) {
                            Log.d(LOG_TAG, "re join failed " + errorMessage);
                            Toast.makeText(VectorRoomActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                            hideWaitingView();
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            onError(e.getLocalizedMessage());
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            if (MatrixError.M_CONSENT_NOT_GIVEN.equals(e.errcode)) {
                                hideWaitingView();
                                getConsentNotGivenHelper().displayDialog(e);
                            } else {
                                onError(e.getLocalizedMessage());
                            }
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            onError(e.getLocalizedMessage());
                        }
                    });
                }
            });
        }

        Button forgetRoomButton = findViewById(R.id.button_decline);
        forgetRoomButton.setText(getString(R.string.forget_room));

        forgetRoomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRoom.forget(new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        finish();
                    }

                    private void onError(String errorMessage) {
                        Log.d(LOG_TAG, "forget failed " + errorMessage);
                        Toast.makeText(VectorRoomActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                        hideWaitingView();
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
                    mRoom.getActiveMembersAsync(new SimpleApiCallback<List<RoomMember>>(this) {
                        @Override
                        public void onSuccess(List<RoomMember> members) {
                            String inviter = "";

                            for (RoomMember member : members) {
                                if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
                                    inviter = TextUtils.isEmpty(member.displayname) ? member.getUserId() : member.displayname;
                                }
                            }

                            invitationTextView.setText(getString(R.string.room_preview_invitation_format, inviter));
                        }
                    });
                } else {
                    invitationTextView.setText(getString(R.string.room_preview_invitation_format, inviter));
                }

                declineButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(LOG_TAG, "The user clicked on decline.");

                        showWaitingView();

                        mRoom.leave(new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void info) {
                                Log.d(LOG_TAG, "The invitation is rejected");
                                onDeclined();
                            }

                            private void onError(String errorMessage) {
                                Log.d(LOG_TAG, "The invitation rejection failed " + errorMessage);
                                Toast.makeText(VectorRoomActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                                hideWaitingView();
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                onError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                if (MatrixError.M_CONSENT_NOT_GIVEN.equals(e.errcode)) {
                                    hideWaitingView();
                                    getConsentNotGivenHelper().displayDialog(e);
                                } else {
                                    onError(e.getLocalizedMessage());
                                }
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
                    invitationTextView.setText(getString(R.string.room_preview_invitation_format, roomEmailInvitation.inviterName));
                    subInvitationTextView.setText(getString(R.string.room_preview_unlinked_email_warning, roomEmailInvitation.email));
                } else {
                    invitationTextView.setText(getString(R.string.room_preview_try_join_an_unknown_room,
                            TextUtils.isEmpty(sRoomPreviewData.getRoomName()) ? getString(R.string.room_preview_try_join_an_unknown_room_default) : roomName));

                    // the room preview has some messages
                    if ((null != sRoomPreviewData.getRoomResponse()) && (null != sRoomPreviewData.getRoomResponse().messages)) {
                        subInvitationTextView.setText(getString(R.string.room_preview_room_interactions_disabled));
                    }
                }

                declineButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(LOG_TAG, "The invitation is declined (unknown room)");

                        sRoomPreviewData = null;
                        finish();
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

                        showWaitingView();

                        room.joinWithThirdPartySigned(sRoomPreviewData.getSession(), sRoomPreviewData.getRoomIdOrAlias(), signUrl, new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void info) {
                                onJoined();
                            }

                            private void onError(String errorMessage) {
                                Toast.makeText(VectorRoomActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                                hideWaitingView();
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                onError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                if (MatrixError.M_CONSENT_NOT_GIVEN.equals(e.errcode)) {
                                    hideWaitingView();
                                    getConsentNotGivenHelper().displayDialog(e);
                                } else {
                                    onError(e.getLocalizedMessage());
                                }
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                onError(e.getLocalizedMessage());
                            }
                        });
                    } else {
                        finish();
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
            finish();
            sRoomPreviewData = null;
        }
    }

    /**
     * the room has been joined
     */
    private void onJoined() {
        if (null != sRoomPreviewData) {
            Map<String, Object> params = new HashMap<>();

            processDirectMessageRoom();

            params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
            params.put(VectorRoomActivity.EXTRA_ROOM_ID, sRoomPreviewData.getRoomId());

            if (null != sRoomPreviewData.getEventId()) {
                params.put(VectorRoomActivity.EXTRA_EVENT_ID, sRoomPreviewData.getEventId());
            }

            // clear the activity stack to home activity
            Intent intent = new Intent(this, VectorHomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

            intent.putExtra(VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS, (HashMap) params);
            startActivity(intent);

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
            if (mRoom == null) {
                return;
            }

            if (mRoom.getNumberOfMembers() == 2) {
                // test if room is already seen as "direct message"
                if (!RoomUtils.isDirectChat(mSession, sRoomPreviewData.getRoomId())) {
                    mRoom.getMembersAsync(new SimpleApiCallback<List<RoomMember>>(this) {
                        @Override
                        public void onSuccess(List<RoomMember> members) {
                            String myUserId = mSession.getMyUserId();
                            String participantUserId;

                            for (RoomMember member : members) {
                                // search for the second participant
                                if (!member.getUserId().equals(myUserId)) {
                                    participantUserId = member.getUserId();
                                    CommonActivityUtils.setToggleDirectMessageRoom(mSession,
                                            sRoomPreviewData.getRoomId(), participantUserId, VectorRoomActivity.this, mDirectMessageListener);
                                    break;
                                }
                            }
                        }
                    });
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
     * Invite a user from the data provided by the invite activity.
     *
     * @param aData the provider date
     */
    private void onActivityResultRoomInvite(final Intent aData) {
        final List<String> userIds = (List<String>) aData.getSerializableExtra(VectorRoomInviteMembersActivity.EXTRA_OUT_SELECTED_USER_IDS);

        if (mRoom != null && (null != userIds) && (userIds.size() > 0)) {
            showWaitingView();

            mRoom.invite(mSession, userIds, new ApiCallback<Void>() {

                private void onDone(String errorMessage) {
                    if (!TextUtils.isEmpty(errorMessage)) {
                        Toast.makeText(VectorRoomActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                    hideWaitingView();
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
                    if (e instanceof IdentityServerNotConfiguredException) {
                        onDone(getString(R.string.invite_no_identity_server_error));
                    } else {
                        onDone(e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * The user clicks on the room title.
     * Assume he wants to update it.
     */
    private void onRoomTitleClick() {
        if (mRoom == null) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_base_edit_text, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder
                .setTitle(R.string.room_info_room_name)
                .setView(dialogView);

        final EditText textInput = dialogView.findViewById(R.id.edit_text);
        textInput.setText(mRoom.getState().name);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                showWaitingView();

                                mRoom.updateName(textInput.getText().toString(), new ApiCallback<Void>() {

                                    private void onDone(String message) {
                                        if (!TextUtils.isEmpty(message)) {
                                            Toast.makeText(VectorRoomActivity.this, message, Toast.LENGTH_SHORT).show();
                                        }

                                        hideWaitingView();
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
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * The user clicks on the room topic.
     * Assume he wants to update it.
     */
    private void onRoomTopicClick() {
        if (mRoom == null) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_base_edit_text, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder
                .setTitle(R.string.room_info_room_topic)
                .setView(dialogView);

        final EditText textInput = dialogView.findViewById(R.id.edit_text);
        textInput.setText(mRoom.getState().topic);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                showWaitingView();

                                mRoom.updateTopic(textInput.getText().toString(), new ApiCallback<Void>() {

                                    private void onDone(String message) {
                                        if (!TextUtils.isEmpty(message)) {
                                            Toast.makeText(VectorRoomActivity.this, message, Toast.LENGTH_SHORT).show();
                                        }

                                        hideWaitingView();
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
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @OnClick(R.id.room_header_avatar)
    void onRoomAvatarClick() {
        if (mRoom != null && !TextUtils.isEmpty(mRoom.getAvatarUrl()))
            // Display the avatar in fullscreen with animation
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ActivityOptions options =
                        ActivityOptions.makeSceneTransitionAnimation(this, mActionBarHeaderRoomAvatar, "vector_transition_avatar");
                startActivity(VectorAvatarViewerActivity.Companion.getIntent(this, mSession.getMyUserId(), mRoom.getAvatarUrl()), options.toBundle());
            } else {
                // No transition
                startActivity(VectorAvatarViewerActivity.Companion.getIntent(this, mSession.getMyUserId(), mRoom.getAvatarUrl()));
            }
    }

    @OnClick(R.id.action_bar_header_room_title)
    void onRoomHeaderTitleClick() {
        // sanity checks : reported by GA
        if ((null != mRoom) && (null != mRoom.getState())) {
            boolean canUpdateTitle = false;
            PowerLevels powerLevels = mRoom.getState().getPowerLevels();

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

    @OnClick(R.id.action_bar_header_room_topic)
    void onRoomHeaderTopicClick() {
        // sanity checks : reported by GA
        if ((null != mRoom) && (null != mRoom.getState())) {
            boolean canUpdateTopic = false;
            PowerLevels powerLevels = mRoom.getState().getPowerLevels();

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

    @OnClick(R.id.action_bar_header_room_members_settings_view)
    void onRoomMembersSettingClick() {
        launchRoomDetails(VectorRoomDetailsActivity.PEOPLE_TAB_INDEX);
    }

    @OnClick(R.id.action_bar_header_room_members_text_view)
    void onRoomMembersClick() {
        launchRoomDetails(VectorRoomDetailsActivity.PEOPLE_TAB_INDEX);
    }

    @OnClick(R.id.action_bar_header_room_members_invite_view)
    void onRoomMemberInviteClick() {
        launchInvitePeople();
    }

    /* ==========================================================================================
     * Interface VectorReadReceiptsDialogFragmentListener
     * ========================================================================================== */

    @Override
    public void onMemberClicked(@NotNull String userId) {
        if (mRoom != null) {
            Intent vectorMemberDetailIntent = new Intent(this, VectorMemberDetailsActivity.class);
            vectorMemberDetailIntent.putExtra(VectorMemberDetailsActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
            vectorMemberDetailIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, userId);
            vectorMemberDetailIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
            startActivityForResult(vectorMemberDetailIntent, VectorRoomActivity.GET_MENTION_REQUEST_CODE);
        }
    }

    /* ==========================================================================================
     * UI Event
     * ========================================================================================== */

    @OnClick(R.id.editText_messageBox)
    void onEditTextClick() {
        // hide the header room as soon as the message input text area is touched
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
    }

    private void chooseMediaSource(boolean useNativeCamera, boolean isVoiceFeatureEnabled) {
        // hide the header room
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);

        final List<DialogListItem> items = new ArrayList<>();

        // Send file
        items.add(DialogListItem.SendFile.INSTANCE);

        // Send voice
        if (isVoiceFeatureEnabled) {
            items.add(DialogListItem.SendVoice.INSTANCE);
        }

        // Send sticker
        if (Matrix.getWidgetManager(this) != null) {
            items.add(DialogListItem.SendSticker.INSTANCE);
        }

        // Camera
        if (useNativeCamera) {
            items.add(DialogListItem.TakePhoto.INSTANCE);
            items.add(DialogListItem.TakeVideo.INSTANCE);
        } else {
            items.add(DialogListItem.TakePhotoVideo.INSTANCE);
        }

        new AlertDialog.Builder(this)
                .setAdapter(new DialogSendItemAdapter(this, items), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onSendChoiceClicked(items.get(which));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @OnClick(R.id.room_send_image_view)
    void onSendClick() {
        if (!TextUtils.isEmpty(mEditText.getText()) && !PreferencesManager.sendMessageWithEnter(this)) {
            sendTextMessage();
        } else {
            boolean useNativeCamera = PreferencesManager.useNativeCamera(this);
            boolean isVoiceFeatureEnabled = PreferencesManager.isSendVoiceFeatureEnabled(this);

            switch (PreferencesManager.getSelectedDefaultMediaSource(this)) {
                case MEDIA_SOURCE_FILE:
                    onSendChoiceClicked(DialogListItem.SendFile.INSTANCE);
                    return;
                case MEDIA_SOURCE_VOICE:
                    if (isVoiceFeatureEnabled) {
                        onSendChoiceClicked(DialogListItem.SendVoice.INSTANCE);
                        return;
                    }
                    // show all options if voice feature is disabled
                    break;
                case MEDIA_SOURCE_STICKER:
                    onSendChoiceClicked(DialogListItem.SendSticker.INSTANCE);
                    return;
                case MEDIA_SOURCE_PHOTO:
                    if (useNativeCamera) {
                        onSendChoiceClicked(DialogListItem.TakePhoto.INSTANCE);
                        return;
                    } else {
                        onSendChoiceClicked(DialogListItem.TakePhotoVideo.INSTANCE);
                        return;
                    }
                case MEDIA_SOURCE_VIDEO:
                    if (useNativeCamera) {
                        onSendChoiceClicked(DialogListItem.TakeVideo.INSTANCE);
                        return;
                    } else {
                        onSendChoiceClicked(DialogListItem.TakePhotoVideo.INSTANCE);
                        return;
                    }
            }

            chooseMediaSource(useNativeCamera, isVoiceFeatureEnabled);
        }
    }

    @OnLongClick(R.id.room_send_image_view)
    boolean onLongClick() {
        if (!TextUtils.isEmpty(mEditText.getText()) && !PreferencesManager.sendMessageWithEnter(this)) {
            return false;
        }
        boolean useNativeCamera = PreferencesManager.useNativeCamera(this);
        boolean isVoiceFeatureEnabled = PreferencesManager.isSendVoiceFeatureEnabled(this);
        chooseMediaSource(useNativeCamera, isVoiceFeatureEnabled);

        return true;
    }

    private void onSendChoiceClicked(DialogListItem dialogListItem) {
        if (dialogListItem instanceof DialogListItem.SendFile) {
            launchFileSelectionIntent();
        } else if (dialogListItem instanceof DialogListItem.SendVoice) {
            launchAudioRecorderIntent();
        } else if (dialogListItem instanceof DialogListItem.SendSticker) {
            startStickerPickerActivity();
        } else if (dialogListItem instanceof DialogListItem.TakePhotoVideo) {
            if (PermissionsToolsKt.checkPermissions(PermissionsToolsKt.PERMISSIONS_FOR_TAKING_PHOTO,
                    VectorRoomActivity.this, PermissionsToolsKt.PERMISSION_REQUEST_CODE_LAUNCH_CAMERA)) {
                launchCamera();
            }
        } else if (dialogListItem instanceof DialogListItem.TakePhoto) {
            if (PermissionsToolsKt.checkPermissions(PermissionsToolsKt.PERMISSIONS_FOR_TAKING_PHOTO,
                    VectorRoomActivity.this, PermissionsToolsKt.PERMISSION_REQUEST_CODE_LAUNCH_NATIVE_CAMERA)) {
                launchNativeCamera();
            }
        } else if (dialogListItem instanceof DialogListItem.TakeVideo) {
            if (PermissionsToolsKt.checkPermissions(PermissionsToolsKt.PERMISSIONS_FOR_TAKING_PHOTO,
                    VectorRoomActivity.this, PermissionsToolsKt.PERMISSION_REQUEST_CODE_LAUNCH_NATIVE_VIDEO_CAMERA)) {
                launchNativeVideoRecorder();
            }
        }
    }

    @OnClick(R.id.room_pending_call_view)
    void onPendingCallClick() {
        IMXCall call = CallsManager.getSharedInstance().getActiveCall();
        if (null != call) {
            final Intent intent = new Intent(this, VectorCallViewActivity.class);
            intent.putExtra(VectorCallViewActivity.EXTRA_MATRIX_ID, call.getSession().getCredentials().userId);
            intent.putExtra(VectorCallViewActivity.EXTRA_CALL_ID, call.getCallId());
            startActivity(intent);
        } else {
            // if the call is no more active, just remove the view
            mVectorPendingCallView.onCallTerminated();
        }
    }

    // notifications area
    // increase the clickable area to open the keyboard.
    // when there is no text, it is quite small and some user thought the edition was disabled.
    @OnClick(R.id.room_sending_message_layout)
    void onSendingMessageLayoutClick() {
        if (mEditText.requestFocus()) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(mEditText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @OnClick(R.id.room_start_call_image_view)
    void onStartCallClick() {
        if ((null != mRoom) && mRoom.isEncrypted() && (mRoom.getNumberOfMembers() > 2)) {
            // display the dialog with the info text
            new AlertDialog.Builder(this)
                    .setMessage(R.string.room_no_conference_call_in_encrypted_rooms)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(R.string.ok, null)
                    .show();
//        } else if (Matrix.getWidgetManager(this) == null) {
//            // display the dialog with the info text
//            new AlertDialog.Builder(this)
//                    .setMessage(R.string.integration_manager_not_configured)
//                    .setIcon(android.R.drawable.ic_dialog_alert)
//                    .setPositiveButton(R.string.ok, null)
//                    .show();
        } else if (isUserAllowedToStartConfCall()) {
            if (mRoom.getNumberOfMembers() > 2) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.conference_call_warning_title)
                        .setMessage(R.string.conference_call_warning_message)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (PreferencesManager.useJitsiConfCall(VectorRoomActivity.this)) {
                                    if (Matrix.getWidgetManager(VectorRoomActivity.this) == null) {
                                        // display the dialog with the info text
                                        new AlertDialog.Builder(VectorRoomActivity.this)
                                                .setMessage(R.string.integration_manager_not_configured)
                                                .setIcon(android.R.drawable.ic_dialog_alert)
                                                .setPositiveButton(R.string.ok, null)
                                                .show();
                                    } else {
                                        startJitsiCall(true);
                                    }
                                } else {
                                    displayVideoCallIpDialog();
                                }
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            } else {
                displayVideoCallIpDialog();
            }
        } else {
            displayConfCallNotAllowed();
        }
    }

    @OnClick(R.id.room_end_call_image_view)
    void onStopCallClick() {
        CallsManager.getSharedInstance().onHangUp(null);
    }

    @OnClick(R.id.room_button_margin_right)
    void onMarginRightClick() {
        // extend the right side of right button
        // to avoid clicking in the void
        if (mStopCallLayout.getVisibility() == View.VISIBLE) {
            mStopCallLayout.performClick();
        } else if (mStartCallLayout.getVisibility() == View.VISIBLE) {
            mStartCallLayout.performClick();
        } else if (mSendImageView.getVisibility() == View.VISIBLE) {
            mSendImageView.performClick();
        }
    }
}