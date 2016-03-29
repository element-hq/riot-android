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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.fragments.IconAndTextDialogFragment;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.ImageUtils;
import org.matrix.androidsdk.util.JsonUtils;
import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.ViewedRoomTracker;
import im.vector.fragments.VectorMessageListFragment;
import im.vector.fragments.ImageSizeSelectionDialogFragment;
import im.vector.services.EventStreamService;
import im.vector.util.NotificationUtils;
import im.vector.util.ResourceUtils;
import im.vector.util.VectorUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays a single room with messages.
 */
public class VectorRoomActivity extends MXCActionBarActivity implements VectorMessageListFragment.IListFragmentEventListener {

    public static final String EXTRA_ROOM_ID = "EXTRA_ROOM_ID";
    private static final boolean SHOW_ACTION_BAR_HEADER = true;
    private static final boolean HIDE_ACTION_BAR_HEADER = false;

    // the room is launched but it expects to start the dedicated call activity
    public static final String EXTRA_START_CALL_ID = "EXTRA_START_CALL_ID";

    private static final String TAG_FRAGMENT_MATRIX_MESSAGE_LIST = "TAG_FRAGMENT_MATRIX_MESSAGE_LIST";
    private static final String TAG_FRAGMENT_INVITATION_MEMBERS_DIALOG = "TAG_FRAGMENT_INVITATION_MEMBERS_DIALOG";
    private static final String TAG_FRAGMENT_ATTACHMENTS_DIALOG = "TAG_FRAGMENT_ATTACHMENTS_DIALOG";
    private static final String TAG_FRAGMENT_IMAGE_SIZE_DIALOG = "TAG_FRAGMENT_IMAGE_SIZE_DIALOG";

    private static final String LOG_TAG = "RoomActivity";
    private static final int TYPING_TIMEOUT_MS = 10000;

    private static final String PENDING_THUMBNAIL_URL = "PENDING_THUMBNAIL_URL";
    private static final String PENDING_MEDIA_URL = "PENDING_MEDIA_URL";
    private static final String PENDING_MIMETYPE = "PENDING_MIMETYPE";
    private static final String PENDING_FILENAME = "PENDING_FILENAME";
    private static final String FIRST_VISIBLE_ROW = "FIRST_VISIBLE_ROW";

    private static final String CAMERA_VALUE_TITLE = "attachment"; // Samsung devices need a filepath to write to or else won't return a Uri (!!!)

    // defines the command line operations
    // the user can write theses messages to perform some room events
    private static final String CMD_CHANGE_DISPLAY_NAME = "/nick";
    private static final String CMD_EMOTE = "/me";
    private static final String CMD_JOIN_ROOM = "/join";
    private static final String CMD_KICK_USER = "/kick";
    private static final String CMD_BAN_USER = "/ban";
    private static final String CMD_UNBAN_USER = "/unban";
    private static final String CMD_SET_USER_POWER_LEVEL = "/op";
    private static final String CMD_RESET_USER_POWER_LEVEL = "/deop";

    private static final int REQUEST_FILES = 0;
    private static final int TAKE_IMAGE = 1;
    private static final int CREATE_DOCUMENT = 2;

    // max image sizes
    private static final int LARGE_IMAGE_SIZE  = 2000;
    private static final int MEDIUM_IMAGE_SIZE = 1000;
    private static final int SMALL_IMAGE_SIZE  = 500;
    private static final int KEYBOARD_THRESHOLD_VIEW_SIZE = 1000;

    private VectorMessageListFragment mVectorMessageListFragment;
    private MXSession mSession;
    private Room mRoom;
    private String mMyUserId;

    private MXLatestChatMessageCache mLatestChatMessageCache;
    private MXMediasCache mMediasCache;

    private ImageButton mSendButton;
    private ImageButton mAttachmentsButton;
    private ImageButton mCallButton;
    private EditText mEditText;
    private ImageView mAvatarImageView;
    // action bar header
    private TextView mActionBarCustomTitle;
    private TextView mActionBarCustomTopic;
    private ImageView mActionBarCustomArrowImageView;
    private RelativeLayout mRoomHeaderView;
    private TextView mActionBarHeaderRoomName;
    private TextView mActionBarHeaderActiveMembers;
    private TextView mActionBarHeaderRoomTopic;
    private ImageView mActionBarHeaderRoomAvatar;
    private boolean mIsKeyboardDisplayed;
    // keyboard listener to detect when the keyboard is displayed
    private ViewTreeObserver.OnGlobalLayoutListener mKeyboardListener;

    // notifications area
    private View mNotificationsArea;
    private View mTypingIcon;
    private View mErrorIcon;
    private TextView mNotificationsMessageTextView;
    private TextView mErrorMessageTextView;
    private String mLatestTypingMessage;

    private MenuItem mResendUnsentMenuItem;
    private MenuItem mResendDeleteMenuItem;

    // network events
    private IMXNetworkEventListener mNetworkEventListener = new IMXNetworkEventListener() {
        @Override
        public void onNetworkConnectionUpdate(boolean isConnected) {
            refreshNotificationsArea();
        }
    };

    private String mPendingThumbnailUrl;
    private String mPendingMediaUrl;
    private String mPendingMimeType;
    private String mPendingFilename;

    private String mCallId = null;

    private static String mLatestTakePictureCameraUri = null; // has to be String not Uri because of Serializable

    // typing event management
    private Timer mTypingTimer = null;
    private TimerTask mTypingTimerTask;
    private long  mLastTypingDate = 0;

    // scroll to a dedicated index
    private int mScrollToIndex = -1;

    private Boolean mIgnoreTextUpdate = false;

    private AlertDialog mImageSizesListDialog;

    private final MXEventListener mPresenceEventListener = new MXEventListener() {
        @Override
        public void onPresenceUpdate(Event event, User user) {
            // the header displays active members
            updateRoomHeaderMembersStatus();
        }
    };

    private final MXEventListener mEventListener = new MXEventListener() {

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
                        updateRoomHeaderMembersStatus();
                    }
                    else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type)) {
                        Log.d(LOG_TAG, "Updating room topic.");
                        RoomState roomState = JsonUtils.toRoomState(event.content);
                        setTopic(roomState.topic);
                    }
                    else if (Event.EVENT_TYPE_TYPING.equals(event.type)) {
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
                            mRoom.sendReadReceipt();
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

    // *********************************************************************************************
    // IListFragmentEventListener implementation
    /**
     * Listener on the underlying fragment list view.
     * When the list view is scrolled, the header room view must be hidden.
     */
    @Override
    public void onListTouch() {
        enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
    }

    // *********************************************************************************************

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_room);

        // bind the widgets of the room header view. The room header view is displayed by
        // clicking on the title of the action bar
        mRoomHeaderView = (RelativeLayout) findViewById(R.id.action_bar_header);
        mActionBarHeaderRoomTopic = (TextView)findViewById(R.id.action_bar_header_room_topic);
        mActionBarHeaderRoomName = (TextView)findViewById(R.id.action_bar_header_room_title);
        mActionBarHeaderActiveMembers = (TextView)findViewById(R.id.action_bar_header_room_members);
        mActionBarHeaderRoomAvatar = (ImageView) mRoomHeaderView.findViewById(R.id.avatar_img);
        // hide the header room as soon as the bootom layout (text edit zone) is touched
        findViewById(R.id.room_bottom_layout).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                return false;
            }
        });

        // set the default custom action bar layout,
        // that will be displayed from the custom action bar layout
        setActionBarDefaultCustomLayout();

        Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }

        if (intent.hasExtra(EXTRA_START_CALL_ID)) {
            mCallId = intent.getStringExtra(EXTRA_START_CALL_ID);
        }

        // the user has tapped on the "View" notification button
        if ((null != intent.getAction()) && (intent.getAction().startsWith(NotificationUtils.TAP_TO_VIEW_ACTION))) {
            // remove any pending notifications
            NotificationManager notificationsManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationsManager.cancelAll();
        }

        mPendingThumbnailUrl = null;
        mPendingMediaUrl = null;
        mPendingMimeType = null;
        mPendingFilename = null;

        if (null != savedInstanceState) {
            if (savedInstanceState.containsKey(PENDING_THUMBNAIL_URL)) {
                mPendingThumbnailUrl = savedInstanceState.getString(PENDING_THUMBNAIL_URL);
            }

            if (savedInstanceState.containsKey(PENDING_MEDIA_URL)) {
                mPendingMediaUrl = savedInstanceState.getString(PENDING_MEDIA_URL);
            }

            if (savedInstanceState.containsKey(PENDING_MIMETYPE)) {
                mPendingMimeType = savedInstanceState.getString(PENDING_MIMETYPE);
            }

            if (savedInstanceState.containsKey(PENDING_FILENAME)) {
                mPendingFilename = savedInstanceState.getString(PENDING_FILENAME);
            }
        }

        String roomId = intent.getStringExtra(EXTRA_ROOM_ID);
        Log.i(LOG_TAG, "Displaying " + roomId);

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
                boolean retCode = false;
                int imeActionId = actionId & EditorInfo.IME_MASK_ACTION;

                if (EditorInfo.IME_ACTION_DONE == imeActionId) {
                    enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                    String body = mEditText.getText().toString();
                    sendMessage(body);
                    mEditText.setText("");
                    retCode = false;
                }
                return retCode;
            }
        });

        mSendButton = (ImageButton) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // hide the header room
                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                String body = mEditText.getText().toString();
                sendMessage(body);
                mEditText.setText("");
            }
        });

        mCallButton = (ImageButton) findViewById(R.id.button_call);
        mCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // hide the header room
                enableActionBarHeader(HIDE_ACTION_BAR_HEADER);
                // TODO implement a dedicated call activity
                // we do not get any design by now
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


                fragment = IconAndTextDialogFragment.newInstance(icons, messages, null, VectorRoomActivity.this.getResources().getColor(R.color.vector_text_black_color));
                fragment.setOnClickListener(new IconAndTextDialogFragment.OnItemClickListener() {
                    @Override
                    public void onItemClick(IconAndTextDialogFragment dialogFragment, int position) {
                        Integer selectedVal = messages[position];

                        if (selectedVal == R.string.option_send_files) {
                            VectorRoomActivity.this.launchFileSelectionIntent();
                        } else if (selectedVal == R.string.option_take_photo) {
                            VectorRoomActivity.this.launchCamera();
                        }
                    }
                });

                fragment.show(fm, TAG_FRAGMENT_ATTACHMENTS_DIALOG);
            }
        });

        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
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

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        // notifications area
        mNotificationsArea = findViewById(R.id.room_notifications_area);
        mTypingIcon = findViewById(R.id.room_typing_animation);
        mNotificationsMessageTextView = (TextView)findViewById(R.id.room_notification_message);
        mErrorIcon = findViewById(R.id.room_error_icon);
        mErrorMessageTextView = (TextView)findViewById(R.id.room_notification_error_message);

        mSession = getSession(intent);

        if (mSession == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }

        mMyUserId = mSession.getCredentials().userId;

        CommonActivityUtils.resumeEventStream(this);

        mRoom = mSession.getDataHandler().getRoom(roomId);

        FragmentManager fm = getSupportFragmentManager();
        mVectorMessageListFragment = (VectorMessageListFragment) fm.findFragmentByTag(TAG_FRAGMENT_MATRIX_MESSAGE_LIST);

        if (mVectorMessageListFragment == null) {
            // this fragment displays messages and handles all message logic
            mVectorMessageListFragment = VectorMessageListFragment.newInstance(mMyUserId, mRoom.getRoomId(), org.matrix.androidsdk.R.layout.fragment_matrix_message_list_fragment);
            fm.beginTransaction().add(R.id.anchor_fragment_messages, mVectorMessageListFragment, TAG_FRAGMENT_MATRIX_MESSAGE_LIST).commit();
        }

        mLatestChatMessageCache = Matrix.getInstance(this).getDefaultLatestChatMessageCache();
        mMediasCache = Matrix.getInstance(this).getMediasCache();

        // some medias must be sent while opening the chat
        if (intent.hasExtra(VectorHomeActivity.EXTRA_ROOM_INTENT)) {
            final Intent mediaIntent = intent.getParcelableExtra(VectorHomeActivity.EXTRA_ROOM_INTENT);

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
            mAvatarImageView = (ImageView)avatarLayout.findViewById(R.id.avatar_img);
        }

        refreshSelfAvatar();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);

        if (null != mPendingThumbnailUrl) {
            savedInstanceState.putString(PENDING_THUMBNAIL_URL, mPendingThumbnailUrl);
        }

        if (null != mPendingMediaUrl) {
            savedInstanceState.putString(PENDING_MEDIA_URL, mPendingMediaUrl);
        }

        if (null != mPendingMimeType) {
            savedInstanceState.putString(PENDING_MIMETYPE, mPendingMimeType);
        }

        if (null != mPendingFilename) {
            savedInstanceState.putString(PENDING_FILENAME, mPendingFilename);
        }

        savedInstanceState.putInt(FIRST_VISIBLE_ROW, mVectorMessageListFragment.mMessageListView.getFirstVisiblePosition());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if (savedInstanceState.containsKey(FIRST_VISIBLE_ROW)) {
            // the scroll will be done in resume.
            // the listView will be refreshed so the offset might be lost.
            mScrollToIndex = savedInstanceState.getInt(FIRST_VISIBLE_ROW);
        }
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

        // listen for room name or topic changes
        mRoom.removeEventListener(mEventListener);

        Matrix.getInstance(this).removeNetworkEventListener(mNetworkEventListener);

        if (mSession.isActive()) {
            mSession.getDataHandler().removeListener(mPresenceEventListener);
        }

        // remove listener on keyboard display
        enableKeyboardShownListener(false);
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
        super.onResume();
        ViewedRoomTracker.getInstance().setViewedRoomId(mRoom.getRoomId());
        ViewedRoomTracker.getInstance().setMatrixId(mSession.getCredentials().userId);

        // check if the room has been left from another client.
        if ((null == mRoom.getMember(mMyUserId)) || !mSession.getDataHandler().doesRoomExist(mRoom.getRoomId())) {
            VectorRoomActivity.this.finish();
            return;
        }

        // listen for room name or topic changes
        mRoom.addEventListener(mEventListener);

        mSession.getDataHandler().addListener(mPresenceEventListener);

        Matrix.getInstance(this).addNetworkEventListener(mNetworkEventListener);

        EventStreamService.cancelNotificationsForRoomId(mSession.getCredentials().userId, mRoom.getRoomId());

        // listen to keyboard display
        enableKeyboardShownListener(true);

        // reset the unread messages counter
        mRoom.sendReadReceipt();

        String cachedText = Matrix.getInstance(this).getDefaultLatestChatMessageCache().getLatestText(this, mRoom.getRoomId());

        if (!cachedText.equals(mEditText.getText().toString())) {
            mIgnoreTextUpdate = true;
            mEditText.setText("");
            mEditText.append(cachedText);
            mIgnoreTextUpdate = false;
        }

        manageSendMoreButtons();

        updateActionBarTitleAndTopic();

        refreshNotificationsArea();

        updateRoomHeaderMembersStatus();

        // refresh the UI : the timezone could have been updated
        mVectorMessageListFragment.refresh();

        // the device has been rotated
        // so try to keep the same top/left item;
        if (mScrollToIndex > 0) {
            mVectorMessageListFragment.mMessageListView.post(new Runnable() {
                @Override
                public void run() {
                    mVectorMessageListFragment.mMessageListView.setSelection(mScrollToIndex);
                    mScrollToIndex = -1;
                }
            });
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

                VectorRoomActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            VectorRoomActivity.this.startActivity(intent);
                        }
                    });

            }

            mCallId = null;
        }

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

    private void updateActionBarTitleAndTopic() {
        setTitle();
        setTopic();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.vector_room, menu);

        mResendUnsentMenuItem = menu.findItem(R.id.ic_action_room_resend_unsent);
        mResendDeleteMenuItem = menu.findItem(R.id.ic_action_room_delete_unsent);

        // hide / show the unsent / resend all entries.
        refreshNotificationsArea();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.ic_action_search_in_room) {
            try {
                // pop to the home activity
                Intent intent = new Intent(VectorRoomActivity.this, VectorRoomMessagesSearchActivity.class);
                intent.putExtra(VectorRoomMessagesSearchActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
                intent.putExtra(VectorRoomMessagesSearchActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                VectorRoomActivity.this.startActivity(intent);
            } catch (Exception e){
                Log.i(LOG_TAG,"## onOptionsItemSelected(): ");
            }
        } else if (id == R.id.ic_action_room_settings) {
            // pop to the home activity
            Intent intent = new Intent(VectorRoomActivity.this, VectorRoomDetailsActivity.class);
            intent.putExtra(VectorRoomDetailsActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
            intent.putExtra(VectorRoomDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
            VectorRoomActivity.this.startActivity(intent);
        } else if (id == R.id.ic_action_room_resend_unsent) {
            mVectorMessageListFragment.resendUnsentMessages();
            refreshNotificationsArea();
        } else if (id == R.id.ic_action_room_delete_unsent) {
            mVectorMessageListFragment.deleteUnsentMessages();
            refreshNotificationsArea();
        }

        return super.onOptionsItemSelected(item);
    }

    //================================================================================
    // medias sending
    //================================================================================

    private void sendMessage(String body) {
        if (!TextUtils.isEmpty(body)) {
            if (!manageIRCCommand(body)) {
                mVectorMessageListFragment.cancelSelectionMode();
                mVectorMessageListFragment.sendTextMessage(body);
            }
        }
    }

    /**
     * Send a list of images from their URIs
     * @param mediaUris the media URIs
     */
    private void sendMedias(final ArrayList<Uri> mediaUris) {
        mVectorMessageListFragment.cancelSelectionMode();

        final View progressLayout =  findViewById(R.id.medias_processing_progress_layout_background);
        progressLayout.setVisibility(View.VISIBLE);

        final HandlerThread handlerThread = new HandlerThread("MediasEncodingThread");
        handlerThread.start();

        final android.os.Handler handler = new android.os.Handler(handlerThread.getLooper());

        Runnable r = new Runnable() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        final int mediaCount = mediaUris.size();

                        for (Uri anUri : mediaUris) {
                            // crash from Google Analytics : null URI on a nexus 5
                            if (null != anUri) {
                                final Uri mediaUri = anUri;
                                String filename = null;

                                if (mediaUri.toString().startsWith("content://")) {
                                    Cursor cursor = null;
                                    try {
                                        cursor = VectorRoomActivity.this.getContentResolver().query(mediaUri, null, null, null, null);
                                        if (cursor != null && cursor.moveToFirst()) {
                                            filename = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                                        }
                                    } catch (Exception e) {
                                        Log.e(LOG_TAG, "cursor.getString " + e.getMessage());
                                    } finally {
                                        if (null != cursor) {
                                            cursor.close();
                                        }
                                    }

                                    if (TextUtils.isEmpty(filename)) {
                                        List uriPath = mediaUri.getPathSegments();
                                        filename = (String) uriPath.get(uriPath.size() - 1);
                                    }
                                } else if (mediaUri.toString().startsWith("file://")) {
                                    // try to retrieve the filename from the file url.
                                    try {
                                        filename = anUri.getLastPathSegment();
                                    } catch (Exception e) {
                                    }

                                    if (TextUtils.isEmpty(filename)) {
                                        filename = null;
                                    }
                                }

                                final String fFilename = filename;

                                ResourceUtils.Resource resource = ResourceUtils.openResource(VectorRoomActivity.this, mediaUri);

                                if (null == resource) {
                                    VectorRoomActivity.this.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            handlerThread.quit();
                                            progressLayout.setVisibility(View.GONE);

                                            Toast.makeText(VectorRoomActivity.this,
                                                    getString(R.string.message_failed_to_upload),
                                                    Toast.LENGTH_LONG).show();
                                        }

                                    });

                                    return;
                                }

                                // save the file in the filesystem
                                String mediaUrl = mMediasCache.saveMedia(resource.contentStream, null, resource.mimeType);
                                String mimeType = resource.mimeType;
                                Boolean isManaged = false;

                                if ((null != resource.mimeType) && resource.mimeType.startsWith("image/")) {
                                    // manage except if there is an error
                                    isManaged = true;

                                    // try to retrieve the gallery thumbnail
                                    // if the image comes from the gallery..
                                    Bitmap thumbnailBitmap = null;

                                    try {
                                        ContentResolver resolver = getContentResolver();

                                        List uriPath = mediaUri.getPathSegments();
                                        long imageId = -1;
                                        String lastSegment = (String) uriPath.get(uriPath.size() - 1);

                                        // > Kitkat
                                        if (lastSegment.startsWith("image:")) {
                                            lastSegment = lastSegment.substring("image:".length());
                                        }

                                        imageId = Long.parseLong(lastSegment);

                                        thumbnailBitmap = MediaStore.Images.Thumbnails.getThumbnail(resolver, imageId, MediaStore.Images.Thumbnails.MINI_KIND, null);
                                    } catch (Exception e) {
                                        Log.e(LOG_TAG, "MediaStore.Images.Thumbnails.getThumbnail " + e.getMessage());
                                    }

                                    double thumbnailWidth = mVectorMessageListFragment.getMaxThumbnailWith();
                                    double thumbnailHeight = mVectorMessageListFragment.getMaxThumbnailHeight();

                                    // no thumbnail has been found or the mimetype is unknown
                                    if ((null == thumbnailBitmap) || (thumbnailBitmap.getHeight() > thumbnailHeight) || (thumbnailBitmap.getWidth() > thumbnailWidth)) {
                                        // need to decompress the high res image
                                        BitmapFactory.Options options = new BitmapFactory.Options();
                                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                                        resource = ResourceUtils.openResource(VectorRoomActivity.this, mediaUri);

                                        // get the full size bitmap
                                        Bitmap fullSizeBitmap = null;

                                        if (null == thumbnailBitmap) {
                                            fullSizeBitmap = BitmapFactory.decodeStream(resource.contentStream, null, options);
                                        }

                                        if ((fullSizeBitmap != null) || (thumbnailBitmap != null)) {
                                            double imageWidth;
                                            double imageHeight;

                                            if (null == thumbnailBitmap) {
                                                imageWidth = fullSizeBitmap.getWidth();
                                                imageHeight = fullSizeBitmap.getHeight();
                                            } else {
                                                imageWidth = thumbnailBitmap.getWidth();
                                                imageHeight = thumbnailBitmap.getHeight();
                                            }

                                            if (imageWidth > imageHeight) {
                                                thumbnailHeight = thumbnailWidth * imageHeight / imageWidth;
                                            } else {
                                                thumbnailWidth = thumbnailHeight * imageWidth / imageHeight;
                                            }

                                            try {
                                                thumbnailBitmap = Bitmap.createScaledBitmap((null == fullSizeBitmap) ? thumbnailBitmap : fullSizeBitmap, (int) thumbnailWidth, (int) thumbnailHeight, false);
                                            } catch (OutOfMemoryError ex) {
                                                Log.e(LOG_TAG, "Bitmap.createScaledBitmap " + ex.getMessage());
                                            }
                                        }

                                        // the valid mimetype is not provided
                                        if ("image/*".equals(mimeType)) {
                                            // make a jpg snapshot.
                                            mimeType = null;
                                        }

                                        // unknown mimetype
                                        if ((null == mimeType) || (mimeType.startsWith("image/"))) {
                                            try {
                                                // try again
                                                if (null == fullSizeBitmap) {
                                                    System.gc();
                                                    fullSizeBitmap = BitmapFactory.decodeStream(resource.contentStream, null, options);
                                                }

                                                if (null != fullSizeBitmap) {
                                                    Uri uri = Uri.parse(mediaUrl);

                                                    if (null == mimeType) {
                                                        // the images are save in jpeg format
                                                        mimeType = "image/jpeg";
                                                    }

                                                    resource.contentStream.close();
                                                    resource = ResourceUtils.openResource(VectorRoomActivity.this, mediaUri);

                                                    try {
                                                        mMediasCache.saveMedia(resource.contentStream, uri.getPath(), mimeType);
                                                    } catch (OutOfMemoryError ex) {
                                                        Log.e(LOG_TAG, "mMediasCache.saveMedia" + ex.getMessage());
                                                    }

                                                } else {
                                                    isManaged = false;
                                                }

                                                resource.contentStream.close();

                                            } catch (Exception e) {
                                                isManaged = false;
                                                Log.e(LOG_TAG, "sendMedias " + e.getMessage());
                                            }
                                        }

                                        // reduce the memory consumption
                                        if (null != fullSizeBitmap) {
                                            fullSizeBitmap.recycle();
                                            System.gc();
                                        }
                                    }

                                    String thumbnailURL = mMediasCache.saveBitmap(thumbnailBitmap, null);

                                    if (null != thumbnailBitmap) {
                                        thumbnailBitmap.recycle();
                                    }

                                    //
                                    if (("image/jpg".equals(mimeType) || "image/jpeg".equals(mimeType)) && (null != mediaUrl)) {

                                        Uri imageUri = Uri.parse(mediaUrl);
                                        // get the exif rotation angle
                                        final int rotationAngle = ImageUtils.getRotationAngleForBitmap(VectorRoomActivity.this, imageUri);

                                        if (0 != rotationAngle) {
                                            // always apply the rotation to the image
                                            ImageUtils.rotateImage(VectorRoomActivity.this, thumbnailURL, rotationAngle, mMediasCache);

                                            // the high res media orientation should be not be done on uploading
                                            //ImageUtils.rotateImage(RoomActivity.this, mediaUrl, rotationAngle, mMediasCache))
                                        }
                                    }

                                    // is the image content valid ?
                                    if (isManaged && (null != thumbnailURL)) {

                                        final String fThumbnailURL = thumbnailURL;
                                        final String fMediaUrl = mediaUrl;
                                        final String fMimeType = mimeType;

                                        VectorRoomActivity.this.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                // if there is only one image
                                                if (mediaCount == 1) {

                                                    // display an image preview before sending it
                                                    mPendingThumbnailUrl = fThumbnailURL;
                                                    mPendingMediaUrl = fMediaUrl;
                                                    mPendingMimeType = fMimeType;
                                                    mPendingFilename = fFilename;
                                                    mVectorMessageListFragment.scrollToBottom();

                                                    manageSendMoreButtons();

                                                    VectorRoomActivity.this.runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            resizeMediaAndSend();
                                                        }
                                                    });
                                                } else {

                                                    // apply a rotation
                                                    if (null != fThumbnailURL) {
                                                        // check if the media could be resized
                                                        if ("image/jpeg".equals(fMimeType)) {

                                                            System.gc();

                                                            try {
                                                                Uri uri = Uri.parse(fMediaUrl);

                                                                final int rotationAngle = ImageUtils.getRotationAngleForBitmap(VectorRoomActivity.this, uri);

                                                                // try to apply exif rotation
                                                                if (0 != rotationAngle) {
                                                                    // rotate the image content
                                                                    ImageUtils.rotateImage(VectorRoomActivity.this, fMediaUrl, rotationAngle, mMediasCache);
                                                                }

                                                            } catch (Exception e) {
                                                            }
                                                        }
                                                    }

                                                    mVectorMessageListFragment.uploadImageContent(fThumbnailURL, fMediaUrl, fFilename, fMimeType);
                                                }
                                            }
                                        });
                                    }
                                }

                                // default behaviour
                                if ((!isManaged) && (null != mediaUrl)) {
                                    final String fMediaUrl = mediaUrl;
                                    final String fMimeType = mimeType;

                                    VectorRoomActivity.this.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if ((null != fMimeType) && fMimeType.startsWith("video/")) {
                                                mVectorMessageListFragment.uploadVideoContent(fMediaUrl, null, fMimeType);
                                            } else {
                                                mVectorMessageListFragment.uploadFileContent(fMediaUrl, fMimeType, fFilename);
                                            }
                                        }
                                    });
                                }
                            }
                        }

                        VectorRoomActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                handlerThread.quit();
                                progressLayout.setVisibility(View.GONE);
                            }
                        });
                    }
                });
            }
        };

        Thread t = new Thread(r);
        t.start();
    }

    @SuppressLint("NewApi")
    private void sendMediasIntent(final Intent data) {
        // sanity check
        if ((null == data) && (null == mLatestTakePictureCameraUri)) {
            return;
        }

        ArrayList<Uri> uris = new ArrayList<Uri>();

        if (null != data) {
            ClipData clipData = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                clipData = data.getClipData();
            }

            // multiple data
            if (null != clipData) {
                int count = clipData.getItemCount();

                for (int i = 0; i < count; i++) {
                    ClipData.Item item = clipData.getItemAt(i);
                    Uri uri = item.getUri();

                    if (null != uri) {
                        uris.add(uri);
                    }
                }
            } else if (null != data.getData()) {
                uris.add(data.getData());
            }
        } else if (null != mLatestTakePictureCameraUri) {
            uris.add(Uri.parse(mLatestTakePictureCameraUri));
            mLatestTakePictureCameraUri = null;
        }

        // check the extras
        if (0 == uris.size()) {
            Bundle bundle = data.getExtras();

            // sanity checks
            if (null != bundle) {
                if (bundle.containsKey(Intent.EXTRA_STREAM)) {
                    Object streamUri = bundle.get(Intent.EXTRA_STREAM);

                    if (streamUri instanceof Uri) {
                        uris.add((Uri) streamUri);
                    }
                } else if (bundle.containsKey(Intent.EXTRA_TEXT)) {
                    this.sendMessage(bundle.getString(Intent.EXTRA_TEXT));
                }
            } else {
                uris.add( mLatestTakePictureCameraUri == null ? null : Uri.parse(mLatestTakePictureCameraUri));
                mLatestTakePictureCameraUri = null;
            }
        }

        if (0 != uris.size()) {
            sendMedias(uris);
        }
    }

    @Override
         protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if ((requestCode == REQUEST_FILES) || (requestCode == TAKE_IMAGE)) {
                sendMediasIntent(data);
            } else if (requestCode == CREATE_DOCUMENT) {
                Uri currentUri = data.getData();
                writeMediaUrl(currentUri);
            }
        }

        if (requestCode == CREATE_DOCUMENT) {
            mPendingMediaUrl = null;
            mPendingMimeType = null;
        }
    }

    /**
     *
     * @param message
     * @param mediaUrl
     * @param mediaMimeType
     */
    public void createDocument(Message message, final String mediaUrl, final String mediaMimeType) {
        String filename = "Vector_" + System.currentTimeMillis();

        MimeTypeMap mime = MimeTypeMap.getSingleton();
        filename += "." + mime.getExtensionFromMimeType(mediaMimeType);

        if (message instanceof FileMessage) {
            FileMessage fileMessage = (FileMessage)message;

            if (null != fileMessage.body) {
                filename = fileMessage.body;
            }
        }

        mPendingMediaUrl = mediaUrl;
        mPendingMimeType = mediaMimeType;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mediaMimeType)
                .putExtra(Intent.EXTRA_TITLE, filename);

        startActivityForResult(intent, CREATE_DOCUMENT);

    }

    private void writeMediaUrl(Uri destUri) {
        try {
            ParcelFileDescriptor pfd = this.getContentResolver().openFileDescriptor(destUri, "w");

            FileOutputStream fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());

            File sourceFile = mMediasCache.mediaCacheFile(mPendingMediaUrl, mPendingMimeType);

            FileInputStream inputStream = new FileInputStream(sourceFile);

            byte[] buffer = new byte[1024 * 10];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, len);
            }

            fileOutputStream.close();
            pfd.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
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
            // Check whether a typing event has been already reported to server (We wait for the end of the local timout before considering this new event)
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
                mTypingTimer = new Timer();
                mTypingTimerTask = new TimerTask() {
                    public void run() {
                        if (mTypingTimerTask != null) {
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
                };
                mTypingTimer.schedule(mTypingTimerTask, TYPING_TIMEOUT_MS);

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
    // Image resizing
    //================================================================================

    private class ImageSize {
        public final int mWidth;
        public final int mHeight;

        public ImageSize(int width, int height) {
            mWidth = width;
            mHeight = height;
        }
    }

    private void resizeMediaAndSend() {
        if (null != mPendingThumbnailUrl) {
            boolean sendMedia = true;

            // check if the media could be resized
            if ("image/jpeg".equals(mPendingMimeType)) {

                System.gc();
                FileInputStream imageStream = null;

                try {
                    Uri uri = Uri.parse(mPendingMediaUrl);
                    final String filename = uri.getPath();

                    final int rotationAngle = ImageUtils.getRotationAngleForBitmap(this, uri);

                    imageStream = new FileInputStream(new File(filename));

                    int fileSize = imageStream.available();

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    options.outWidth = -1;
                    options.outHeight = -1;

                    // retrieve the image size
                    try {
                        BitmapFactory.decodeStream(imageStream, null, options);
                    } catch (OutOfMemoryError e) {
                        Log.e(LOG_TAG, "Onclick BitmapFactory.decodeStream : " + e.getMessage());
                    }

                    final ImageSize fullImageSize = new ImageSize(options.outWidth, options.outHeight);

                    imageStream.close();

                    int maxSide = (fullImageSize.mHeight > fullImageSize.mWidth) ? fullImageSize.mHeight : fullImageSize.mWidth;

                    // can be rescaled ?
                    if (maxSide > SMALL_IMAGE_SIZE) {
                        ImageSize largeImageSize = null;

                        int divider = 2;

                        if (maxSide > LARGE_IMAGE_SIZE) {
                            largeImageSize = new ImageSize((fullImageSize.mWidth + (divider - 1)) / divider, (fullImageSize.mHeight + (divider - 1)) / divider);
                            divider *= 2;
                        }

                        ImageSize mediumImageSize = null;

                        if (maxSide > MEDIUM_IMAGE_SIZE) {
                            mediumImageSize = new ImageSize((fullImageSize.mWidth + (divider - 1)) / divider, (fullImageSize.mHeight + (divider - 1)) / divider);
                            divider *= 2;
                        }

                        ImageSize smallImageSize = null;

                        if (maxSide > SMALL_IMAGE_SIZE) {
                            smallImageSize = new ImageSize((fullImageSize.mWidth + (divider - 1)) / divider, (fullImageSize.mHeight + (divider - 1)) / divider);
                        }

                        FragmentManager fm = getSupportFragmentManager();
                        ImageSizeSelectionDialogFragment fragment = (ImageSizeSelectionDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_IMAGE_SIZE_DIALOG);

                        if (fragment != null) {
                            fragment.dismissAllowingStateLoss();
                        }

                        final ArrayList<String> textsList = new ArrayList<String>();
                        final ArrayList<ImageSize> sizesList = new ArrayList<ImageSize>();

                        textsList.add( getString(R.string.compression_opt_list_original) + ": " + android.text.format.Formatter.formatFileSize(this, fileSize) + "(" + fullImageSize.mWidth + "x" + fullImageSize.mHeight + ")");
                        sizesList.add(fullImageSize);

                        if (null != largeImageSize) {
                            int estFileSize = largeImageSize.mWidth * largeImageSize.mHeight * 2 / 10 / 1024 * 1024;

                            textsList.add( getString(R.string.compression_opt_list_large) + ": " + android.text.format.Formatter.formatFileSize(this, estFileSize) + "(" + largeImageSize.mWidth + "x" + largeImageSize.mHeight + ")");
                            sizesList.add(largeImageSize);
                        }

                        if (null != mediumImageSize) {
                            int estFileSize = mediumImageSize.mWidth * mediumImageSize.mHeight * 2 / 10 / 1024 * 1024;

                            textsList.add( getString(R.string.compression_opt_list_medium) + ": " + android.text.format.Formatter.formatFileSize(this, estFileSize) + "(" + mediumImageSize.mWidth + "x" + mediumImageSize.mHeight + ")");
                            sizesList.add(mediumImageSize);
                        }

                        if (null != smallImageSize) {
                            int estFileSize = smallImageSize.mWidth * smallImageSize.mHeight * 2 / 10 / 1024 * 1024;

                            textsList.add(getString(R.string.compression_opt_list_small) + ": " + android.text.format.Formatter.formatFileSize(this, estFileSize) + "(" + smallImageSize.mWidth + "x" + smallImageSize.mHeight + ")");
                            sizesList.add(smallImageSize);
                        }

                        String[] stringsArray = new String[textsList.size()];

                        final AlertDialog.Builder alert = new AlertDialog.Builder(this);
                        alert.setTitle(getString(im.vector.R.string.compression_options));
                        alert.setSingleChoiceItems(textsList.toArray(stringsArray), -1, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final int fPos = which;

                                mImageSizesListDialog.dismiss();

                                VectorRoomActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        final View progressLayout =  findViewById(R.id.medias_processing_progress_layout_background);
                                        progressLayout.setVisibility(View.VISIBLE);

                                        Thread thread = new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    // pos == 0 -> original
                                                    if (0 != fPos) {
                                                        FileInputStream imageStream = new FileInputStream(new File(filename));

                                                        ImageSize imageSize = sizesList.get(fPos);
                                                        InputStream resizeBitmapStream = null;

                                                        try {
                                                            resizeBitmapStream = ImageUtils.resizeImage(imageStream, -1, (fullImageSize.mWidth + imageSize.mWidth - 1) / imageSize.mWidth, 75);
                                                        } catch (OutOfMemoryError ex) {
                                                            Log.e(LOG_TAG, "Onclick BitmapFactory.createScaledBitmap : " + ex.getMessage());
                                                        } catch (Exception e) {
                                                            Log.e(LOG_TAG, "Onclick BitmapFactory.createScaledBitmap failed : " + e.getMessage());
                                                        }

                                                        if (null != resizeBitmapStream) {
                                                            String bitmapURL = mMediasCache.saveMedia(resizeBitmapStream, null, "image/jpeg");

                                                            if (null != bitmapURL) {
                                                                mPendingMediaUrl = bitmapURL;
                                                            }

                                                            resizeBitmapStream.close();
                                                        }
                                                    }

                                                    // try to apply exif rotation
                                                    if (0 != rotationAngle) {
                                                        // rotate the image content
                                                        ImageUtils.rotateImage(VectorRoomActivity.this, mPendingMediaUrl, rotationAngle, mMediasCache);
                                                    }
                                                } catch (Exception e) {
                                                    Log.e(LOG_TAG, "Onclick " + e.getMessage());
                                                }

                                                VectorRoomActivity.this.runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        progressLayout.setVisibility(View.GONE);
                                                        mVectorMessageListFragment.uploadImageContent(mPendingThumbnailUrl, mPendingMediaUrl, mPendingFilename, mPendingMimeType);
                                                        mPendingThumbnailUrl = null;
                                                        mPendingMediaUrl = null;
                                                        mPendingMimeType = null;
                                                        mPendingFilename = null;
                                                        manageSendMoreButtons();
                                                    }
                                                });
                                            }
                                        });

                                        thread.setPriority(Thread.MIN_PRIORITY);
                                        thread.start();
                                    }
                                });
                            }
                        });

                        mImageSizesListDialog = alert.show();
                        mImageSizesListDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                mImageSizesListDialog = null;
                            }
                        });

                        sendMedia = false;
                    }

                } catch (Exception e) {
                    Log.e(LOG_TAG, "Onclick " + e.getMessage());
                }
            }

            if (sendMedia) {
                mVectorMessageListFragment.uploadImageContent(mPendingThumbnailUrl, mPendingMediaUrl, mPendingFilename, mPendingMimeType);
                mPendingThumbnailUrl = null;
                mPendingMediaUrl = null;
                mPendingMimeType = null;
                mPendingFilename = null;
                manageSendMoreButtons();
            }
        }
    }

    //================================================================================
    // Actions
    //================================================================================

    /**
     * Launch the files selection intent
     */
    private void launchFileSelectionIntent() {
        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        fileIntent.setType("*/*");
        startActivityForResult(fileIntent, REQUEST_FILES);
    }

    /**
     * Launch the camera
     */
    private void launchCamera() {
        Intent intent = new Intent(this, VectorMediasPickerActivity.class);
        startActivityForResult(intent, TAKE_IMAGE);
    }

    /**
     * Display UI buttons according to user input text.
     */
    private void manageSendMoreButtons() {
        boolean hasText = mEditText.getText().length() > 0;

        mSendButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
        // TODO manage Call support
        mCallButton.setVisibility(View.GONE);
        //mCallButton.setVisibility(!hasText ? View.VISIBLE : View.GONE);
        mAttachmentsButton.setVisibility(!hasText ? View.VISIBLE : View.GONE);
    }

    /**
     * Set the topic value contained in the Room object.
     */
    private void setTopic() {
        if (null != mRoom) {
            String topicValue = mRoom.getTopic();
            setTopic(topicValue);
        }
    }

    private void setTopic(String aTopicValue){
        // update the topic of the room header
        updateRoomHeaderTopic();

        // update the action bar topic anyway
        mActionBarCustomTopic.setText(aTopicValue);

        // set the visibility of topic on the custom action bar only
        // if header room view is gone, otherwise skipp it
        if (View.GONE == mRoomHeaderView.getVisibility()) {
            // topic is only displayed if its content is not empty
            if (TextUtils.isEmpty(aTopicValue))
                mActionBarCustomTopic.setVisibility(View.GONE);
            else
                mActionBarCustomTopic.setVisibility(View.VISIBLE);
        }
    }

    private void refreshSelfAvatar() {
        // sanity check
        if (null != mAvatarImageView) {
 			VectorUtils.loadUserAvatar(this, mSession, mAvatarImageView, mSession.getMyUser());
        }
    }

    private void updateRoomHeaderAvatar() {
        VectorUtils.loadRoomAvatar(this, mSession, mActionBarHeaderRoomAvatar, mRoom);
    }

    public void insertInTextEditor(String text) {
        if (null != text) {
            if (TextUtils.isEmpty(mEditText.getText())) {
                mEditText.append(text + ": ");
            } else {
                mEditText.getText().insert(mEditText.getSelectionStart(), text);
            }
        }
    }

    //================================================================================
    // Notifications management
    //================================================================================

    private void refreshNotificationsArea() {
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

        mNotificationsArea.setVisibility(isAreaVisible? View.VISIBLE : View.INVISIBLE);

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
    }

    private void onRoomTypings() {
        mLatestTypingMessage = null;

        ArrayList<String> typingUsers = mRoom.getTypingUsers();

        if ((null != typingUsers) && (typingUsers.size() > 0)) {
            String myUserId = mSession.getMyUserId();

            // get the room member names
            ArrayList<String> names = new ArrayList<String>();

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
    // IRC command
    //================================================================================

    /**
     * check if the text message is an IRC command.
     * If it is an IRC command, it is executed
     * @param body message to be parsed
     * @return true if body defines an IRC command
     */
    private boolean manageIRCCommand(String body) {
        boolean isIRCCmd = false;

        // check if it has the IRC marker
        if ((null != body) && (body.startsWith("/"))) {
            final ApiCallback callback = new SimpleApiCallback<Void>(this) {
                @Override
                public void onMatrixError(MatrixError e) {
                    if (MatrixError.FORBIDDEN.equals(e.errcode)) {
                        Toast.makeText(VectorRoomActivity.this, e.error, Toast.LENGTH_LONG).show();
                    }
                }
            };

            if (body.startsWith(CMD_CHANGE_DISPLAY_NAME)) {
                isIRCCmd = true;

                String newDisplayname = body.substring(CMD_CHANGE_DISPLAY_NAME.length()).trim();

                if (newDisplayname.length() > 0) {
                    MyUser myUser = mSession.getMyUser();

                    myUser.updateDisplayName(newDisplayname, callback);
                }
            } else if (body.startsWith(CMD_EMOTE)) {
                isIRCCmd = true;

                String message = body.substring(CMD_EMOTE.length()).trim();

                if (message.length() > 0) {
                    mVectorMessageListFragment.sendEmote(message);
                }
            } else if (body.startsWith(CMD_JOIN_ROOM)) {
                isIRCCmd = true;

                String roomAlias = body.substring(CMD_JOIN_ROOM.length()).trim();

                if (roomAlias.length() > 0) {
                    mSession.joinRoom(roomAlias,new SimpleApiCallback<String>(this) {

                        @Override
                        public void onSuccess(String roomId) {
                            if (null != roomId) {
                                CommonActivityUtils.goToRoomPage(mSession, roomId, VectorRoomActivity.this, null);
                            }
                        }
                    });
                }
            } else if (body.startsWith(CMD_KICK_USER)) {
                isIRCCmd = true;

                String params = body.substring(CMD_KICK_USER.length()).trim();
                String[] paramsList = params.split(" ");

                String kickedUserID = paramsList[0];

                if (kickedUserID.length() > 0) {
                    mRoom.kick(kickedUserID, callback);
                }
            } else if (body.startsWith(CMD_BAN_USER)) {
                isIRCCmd = true;

                String params = body.substring(CMD_BAN_USER.length()).trim();
                String[] paramsList = params.split(" ");

                String bannedUserID = paramsList[0];
                String reason = params.substring(bannedUserID.length()).trim();

                if (bannedUserID.length() > 0) {
                    mRoom.ban(bannedUserID, reason, callback);
                }
            } else if (body.startsWith(CMD_UNBAN_USER)) {
                isIRCCmd = true;

                String params = body.substring(CMD_UNBAN_USER.length()).trim();
                String[] paramsList = params.split(" ");

                String unbannedUserID = paramsList[0];

                if (unbannedUserID.length() > 0) {
                    mRoom.unban(unbannedUserID, callback);
                }
            } else if (body.startsWith(CMD_SET_USER_POWER_LEVEL)) {
                isIRCCmd = true;

                String params = body.substring(CMD_SET_USER_POWER_LEVEL.length()).trim();
                String[] paramsList = params.split(" ");

                String userID = paramsList[0];
                String powerLevelsAsString  = params.substring(userID.length()).trim();

                try {
                    if ((userID.length() > 0) && (powerLevelsAsString.length() > 0)) {
                        mRoom.updateUserPowerLevels(userID, Integer.parseInt(powerLevelsAsString), callback);
                    }
                } catch(Exception e){
                    Log.e(LOG_TAG, "mRoom.updateUserPowerLevels " + e.getMessage());
                }
            } else if (body.startsWith(CMD_RESET_USER_POWER_LEVEL)) {
                isIRCCmd = true;

                String params = body.substring(CMD_RESET_USER_POWER_LEVEL.length()).trim();
                String[] paramsList = params.split(" ");

                String userID = paramsList[0];

                if (userID.length() > 0) {
                    mRoom.updateUserPowerLevels(userID, 0, callback);
                }
            }
        }

        return isIRCCmd;
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
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        // sanity check
        if(null == actionBar){
            return;
        }

        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayOptions(android.support.v7.app.ActionBar.DISPLAY_SHOW_CUSTOM | android.support.v7.app.ActionBar.DISPLAY_SHOW_HOME | android.support.v7.app.ActionBar.DISPLAY_HOME_AS_UP);

        // create the custom layout
        android.support.v7.app.ActionBar.LayoutParams layout = new android.support.v7.app.ActionBar.LayoutParams(ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.MATCH_PARENT);
        View customLayout =  getLayoutInflater().inflate(R.layout.vector_message_action_bar_custo_layout, null);
        actionBar.setCustomView(customLayout, layout);

        // binding the widgets of the custom view
        mActionBarCustomTitle = (TextView)findViewById(R.id.room_action_bar_title);
        mActionBarCustomTopic = (TextView)findViewById(R.id.room_action_bar_topic);
        mActionBarCustomArrowImageView = (ImageView)findViewById(R.id.open_chat_header_arrow);
        mIsKeyboardDisplayed = false;


        // custom header
        View headerTextsContainer = customLayout.findViewById(R.id.header_texts_container);
        View openChatHeaderArrow = customLayout.findViewById(R.id.open_chat_header_arrow);

        // add click listener on custom action bar to display/hide the header view
        openChatHeaderArrow.setOnClickListener(new View.OnClickListener() {
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
                Intent intent = new Intent(VectorRoomActivity.this, VectorRoomDetailsActivity.class);
                intent.putExtra(VectorRoomDetailsActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
                intent.putExtra(VectorRoomDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                VectorRoomActivity.this.startActivity(intent);
            }
        });

        // add touch listener on the header view itself
        if (null != mRoomHeaderView) {
            mRoomHeaderView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    Intent intent = new Intent(VectorRoomActivity.this, VectorRoomDetailsActivity.class);
                    intent.putExtra(VectorRoomDetailsActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
                    intent.putExtra(VectorRoomDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                    VectorRoomActivity.this.startActivity(intent);
                    return true;
                }
            });
        }

    }

    /**
     * Set the title value in the action bar and in the
     * room header layout
     */
    private void setTitle(){
        if((null != mSession) && (null != mRoom)) {
            String titleToApply = VectorUtils.getRoomDisplayname(this, mSession, mRoom);

            // set action bar title
            if (null != mActionBarCustomTitle) {
                mActionBarCustomTitle.setText(titleToApply);
            } else {
                setTitle(titleToApply);
            }
            // set title in the room header (no matter if not displayed)
            if (null != mActionBarHeaderRoomName)
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
        mActionBarHeaderRoomName.setText(VectorUtils.getRoomDisplayname(this, mSession, mRoom));

        // update topic and members status
        updateRoomHeaderTopic();
        updateRoomHeaderMembersStatus();
    }

    private void updateRoomHeaderTopic() {
        if((null != mActionBarCustomTopic) && (null != mRoom)) {
            String value = mRoom.getTopic();

            // if topic value is empty, just hide the topic TextView
            if (TextUtils.isEmpty(value)) {
                mActionBarHeaderRoomTopic.setVisibility(View.GONE);
            } else {
                mActionBarHeaderRoomTopic.setVisibility(View.VISIBLE);
                mActionBarHeaderRoomTopic.setText(value);
            }
        }
    }

    private void updateRoomHeaderMembersStatus() {
        if ((null != mActionBarHeaderActiveMembers) && (null != mRoom)) {
            // refresh only if the action bar is hidden
            if (mActionBarCustomTitle.getVisibility() == View.GONE) {

                // update the members status: "active members"/"members"
                int joinedMembersCount = 0;
                int activeMembersCount = 0;

                Collection<RoomMember> members = mRoom.getMembers();

                for (RoomMember member : members) {
                    if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
                        joinedMembersCount++;

                        User user = mSession.getDataHandler().getStore().getUser(member.getUserId());

                        if ((null != user) && user.isActive()) {
                            activeMembersCount++;
                        }
                    }
                }

                String value = getString(R.string.room_header_active_members, activeMembersCount, joinedMembersCount);
                mActionBarHeaderActiveMembers.setText(value);
            }
        }
    }

    /**
     * Show or hide the action bar header view according to aIsHeaderViewDisplayed
     * @param aIsHeaderViewDisplayed true to show the header view, false to hide
     */
    private void enableActionBarHeader(boolean aIsHeaderViewDisplayed){

        if(SHOW_ACTION_BAR_HEADER == aIsHeaderViewDisplayed){
            if(true == mIsKeyboardDisplayed) {
                Log.i(LOG_TAG, "## enableActionBarHeader(): action bar header canceled (keyboard is displayed)");
                return;
            }

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

        } else {
            // hide the room header only if it is displayed
            if(View.VISIBLE== mRoomHeaderView.getVisibility()) {
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
            }
        }
    }

    private void enableKeyboardShownListener(boolean aIsListenerEnabled){
        final View vectorActivityRoomView = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);//findViewById(R.id.vector_room_root_layout);

        if(null == mKeyboardListener) {
            mKeyboardListener = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    int rootHeight = vectorActivityRoomView.getRootView().getHeight();
                    int height =  vectorActivityRoomView.getHeight();
                    int heightDiff = rootHeight - height;
                    if (heightDiff > KEYBOARD_THRESHOLD_VIEW_SIZE)
                        mIsKeyboardDisplayed = true;
                    else
                        mIsKeyboardDisplayed = false;
                }
            };
        }

        if(aIsListenerEnabled)
            vectorActivityRoomView.getViewTreeObserver().addOnGlobalLayoutListener(mKeyboardListener);
        else
            vectorActivityRoomView.getViewTreeObserver().removeOnGlobalLayoutListener(mKeyboardListener);
    }
}


