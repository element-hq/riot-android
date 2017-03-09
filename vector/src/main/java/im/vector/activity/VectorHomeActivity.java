/*
 * Copyright 2014 OpenMarket Ltd
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

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import im.vector.Matrix;
import im.vector.MyPresenceManager;
import im.vector.PublicRoomsManager;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.fragments.VectorRecentsListFragment;
import im.vector.ga.GAHelper;
import im.vector.receiver.VectorUniversalLinkReceiver;
import im.vector.services.EventStreamService;
import im.vector.util.BugReporter;
import im.vector.util.VectorCallSoundManager;
import im.vector.util.VectorUtils;
import im.vector.view.VectorPendingCallView;

/**
 * Displays the main screen of the app, with rooms the user has joined and the ability to create
 * new rooms.
 */
public class VectorHomeActivity extends AppCompatActivity implements VectorRecentsListFragment.IVectorRecentsScrollEventListener {

    private static final String LOG_TAG = "VectorHomeActivity";

    // shared instance
    // only one instance of VectorHomeActivity should be used.
    private static VectorHomeActivity sharedInstance = null;

    public static final String EXTRA_JUMP_TO_ROOM_PARAMS = "VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS";

    // jump to a member details sheet
    public static final String EXTRA_MEMBER_ID = "VectorHomeActivity.EXTRA_MEMBER_ID";

    // there are two ways to open an external link
    // 1- EXTRA_UNIVERSAL_LINK_URI : the link is opened as soon there is an event check processed (application is launched when clicking on the URI link)
    // 2- EXTRA_JUMP_TO_UNIVERSAL_LINK : do not wait that an event chunk is processed.
    public static final String EXTRA_JUMP_TO_UNIVERSAL_LINK = "VectorHomeActivity.EXTRA_JUMP_TO_UNIVERSAL_LINK";
    public static final String EXTRA_WAITING_VIEW_STATUS = "VectorHomeActivity.EXTRA_WAITING_VIEW_STATUS";

    // call management
    // the home activity is launched to start a call.
    public static final String EXTRA_CALL_SESSION_ID = "VectorHomeActivity.EXTRA_CALL_SESSION_ID";
    public static final String EXTRA_CALL_ID = "VectorHomeActivity.EXTRA_CALL_ID";
    public static final String EXTRA_CALL_UNKNOWN_DEVICES = "VectorHomeActivity.EXTRA_CALL_UNKNOWN_DEVICES";

    // the home activity is launched in shared files mode
    // i.e the user tries to send several files with VECTOR
    public static final String EXTRA_SHARED_INTENT_PARAMS = "VectorHomeActivity.EXTRA_SHARED_INTENT_PARAMS";

    public static final boolean WAITING_VIEW_STOP = false;
    public static final boolean WAITING_VIEW_START = true;

    public static final String BROADCAST_ACTION_STOP_WAITING_VIEW = "im.vector.activity.ACTION_STOP_WAITING_VIEW";

    private static final String TAG_FRAGMENT_RECENTS_LIST = "VectorHomeActivity.TAG_FRAGMENT_RECENTS_LIST";

    // switch to a room activity
    private Map<String, Object> mAutomaticallyOpenedRoomParams = null;

    private Uri mUniversalLinkToOpen = null;

    private String mMemberIdToOpen = null;

    private View mWaitingView = null;

    private Timer mRoomCreationViewTimer = null;
    private FloatingActionButton mRoomCreationFab;

    // the public rooms are displayed when the user overscroll after 0.5s
    private long mOverscrollStartTime = -1;

    private MXEventListener mEventsListener;
    private MXEventListener mLiveEventListener;

    private VectorRecentsListFragment mRecentsListFragment;

    private AlertDialog.Builder mUseGAAlert;

    // when a member is banned, the session must be reloaded
    public static boolean mClearCacheRequired = false;

    // sliding menu management
    private int mSlidingMenuIndex = -1;

    private android.support.v7.widget.Toolbar mToolbar;
    private MXSession mSession;
    private DrawerLayout mDrawerLayout;
    private Iterator mReadReceiptSessionListIterator;
    private Iterator mReadReceiptSummaryListIterator;
    private IMXStore mReadReceiptStore;

    // calls
    private VectorPendingCallView mVectorPendingCallView;

    private View mSyncInProgressView;

    private boolean mStorePermissionCheck = false;

    // manage the previous first displayed item
    private static int mScrollToIndex = -1;

    private final ApiCallback<Void> mSendReceiptCallback = new ApiCallback<Void>() {
        @Override
        public void onSuccess(Void info) {
            Log.d(LOG_TAG, "## onSuccess() - mSendReceiptCallback");
            stopWaitingView();
            markAllMessagesAsRead();
        }

        private void onError() {
            stopWaitingView();
            mReadReceiptSessionListIterator = null;
            mReadReceiptSummaryListIterator = null;
            mReadReceiptStore = null;
        }

        @Override
        public void onNetworkError(Exception e) {
            Log.d(LOG_TAG, "## onNetworkError() - mSendReceiptCallback: Exception Msg=" + e.getLocalizedMessage());
            onError();
        }

        @Override
        public void onMatrixError(MatrixError e) {
            Log.d(LOG_TAG, "## onMatrixError() - mSendReceiptCallback: Exception Msg=" + e.getLocalizedMessage());
            onError();
        }

        @Override
        public void onUnexpectedError(Exception e) {
            Log.d(LOG_TAG, "## onUnexpectedError() - mSendReceiptCallback: Exception Msg=" + e.getLocalizedMessage());
            onError();
        }
    };

    // a shared files intent is waiting the store init
    private Intent mSharedFilesIntent = null;

    private final BroadcastReceiver mBrdRcvStopWaitingView = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopWaitingView();
        }
    };

    /**
     * @return the current instance
     */
    public static VectorHomeActivity getInstance() {
        return sharedInstance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_home);

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        if (CommonActivityUtils.isGoingToSplash(this)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen");
            return;
        }

        sharedInstance = this;

        mWaitingView = findViewById(R.id.listView_spinner_views);
        mVectorPendingCallView = (VectorPendingCallView) findViewById(R.id.listView_pending_callview);
        mSyncInProgressView =  findViewById(R.id.home_recents_sync_in_progress);

        mVectorPendingCallView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IMXCall call = VectorCallViewActivity.getActiveCall();
                if (null != call) {
                    final Intent intent = new Intent(VectorHomeActivity.this, VectorCallViewActivity.class);
                    intent.putExtra(VectorCallViewActivity.EXTRA_MATRIX_ID, call.getSession().getCredentials().userId);
                    intent.putExtra(VectorCallViewActivity.EXTRA_CALL_ID, call.getCallId());

                    VectorHomeActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            VectorHomeActivity.this.startActivity(intent);
                        }
                    });
                }
            }
        });

        // use a toolbar instead of the actionbar
        mToolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.home_toolbar);
        this.setSupportActionBar(mToolbar);
        mToolbar.setTitle(R.string.title_activity_home);
        this.setTitle(R.string.title_activity_home);

        mRoomCreationFab = (FloatingActionButton) findViewById(R.id.listView_create_room_view);

        mRoomCreationFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ignore any action if there is a pending one
                if (View.VISIBLE != mWaitingView.getVisibility()) {
                    Context context = VectorHomeActivity.this;

                    AlertDialog.Builder dialog = new AlertDialog.Builder(context);
                    CharSequence items[] = new CharSequence[]{context.getString(R.string.room_recents_start_chat), context.getString(R.string.room_recents_create_room)};
                    dialog.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int n) {
                            d.cancel();
                            if (0 == n) {
                                invitePeopleToNewRoom();
                            } else {
                                createRoom();
                            }
                        }
                    });

                    dialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            invitePeopleToNewRoom();
                        }
                    });

                    dialog.setNegativeButton(R.string.cancel, null);
                    dialog.show();
                }
            }
        });

        mSession = Matrix.getInstance(this).getDefaultSession();

        // track if the application update
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int version = preferences.getInt("VERSION_BUILD", 0);

        if (version != VectorApp.VERSION_BUILD) {
            Log.d(LOG_TAG, "The application has been updated from version " + version + " to version " + VectorApp.VERSION_BUILD);

            // TODO add some dedicated actions here

            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("VERSION_BUILD", VectorApp.VERSION_BUILD);
            editor.commit();
        }

        // process intent parameters
        final Intent intent = getIntent();

        if (intent.hasExtra(EXTRA_CALL_SESSION_ID) && intent.hasExtra(EXTRA_CALL_ID)) {
            startCall(intent.getStringExtra(EXTRA_CALL_SESSION_ID), intent.getStringExtra(EXTRA_CALL_ID), (MXUsersDevicesMap<MXDeviceInfo>)intent.getSerializableExtra(EXTRA_CALL_UNKNOWN_DEVICES));
            intent.removeExtra(EXTRA_CALL_SESSION_ID);
            intent.removeExtra(EXTRA_CALL_ID);
            intent.removeExtra(EXTRA_CALL_UNKNOWN_DEVICES);
        }

        // the activity could be started with a spinner
        // because there is a pending action (like universalLink processing)
        if (intent.getBooleanExtra(EXTRA_WAITING_VIEW_STATUS, WAITING_VIEW_STOP)) {
            showWaitingView();
        } else {
            stopWaitingView();
        }
        intent.removeExtra(EXTRA_WAITING_VIEW_STATUS);

        mAutomaticallyOpenedRoomParams = (Map<String, Object>) intent.getSerializableExtra(EXTRA_JUMP_TO_ROOM_PARAMS);
        intent.removeExtra(EXTRA_JUMP_TO_ROOM_PARAMS);

        mUniversalLinkToOpen = intent.getParcelableExtra(EXTRA_JUMP_TO_UNIVERSAL_LINK);
        intent.removeExtra(EXTRA_JUMP_TO_UNIVERSAL_LINK);

        mMemberIdToOpen = intent.getStringExtra(EXTRA_MEMBER_ID);
        intent.removeExtra(EXTRA_MEMBER_ID);

        // the home activity has been launched with an universal link
        if (intent.hasExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI)) {
            Log.d(LOG_TAG, "Has an universal link");

            final Uri uri = intent.getParcelableExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);
            intent.removeExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);

            // detect the room could be opened without waiting the next sync
            HashMap<String, String> params = VectorUniversalLinkReceiver.parseUniversalLink(uri);

            if ((null != params) && params.containsKey(VectorUniversalLinkReceiver.ULINK_ROOM_ID_OR_ALIAS_KEY)) {
                Log.d(LOG_TAG, "Has a valid universal link");

                final String roomIdOrAlias = params.get(VectorUniversalLinkReceiver.ULINK_ROOM_ID_OR_ALIAS_KEY);

                // it is a room ID ?
                if (MXSession.isRoomId(roomIdOrAlias)) {
                    Log.d(LOG_TAG, "Has a valid universal link to the room ID " + roomIdOrAlias);
                    Room room = mSession.getDataHandler().getRoom(roomIdOrAlias, false);

                    if (null != room) {
                        Log.d(LOG_TAG, "Has a valid universal link to a known room");
                        // open the room asap
                        mUniversalLinkToOpen = uri;
                    } else {
                        Log.d(LOG_TAG, "Has a valid universal link but the room is not yet known");
                        // wait the next sync
                        intent.putExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI, uri);
                    }
                } else if (MXSession.isRoomAlias(roomIdOrAlias)){
                    Log.d(LOG_TAG, "Has a valid universal link of the room Alias " + roomIdOrAlias);

                    // it is a room alias
                    // convert the room alias to room Id
                    mSession.getDataHandler().roomIdByAlias(roomIdOrAlias, new SimpleApiCallback<String>() {
                        @Override
                        public void onSuccess(String roomId) {
                            Log.d(LOG_TAG, "Retrieve the room ID " + roomId);

                            getIntent().putExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI, uri);

                            // the room exists, opens it
                            if (null != mSession.getDataHandler().getRoom(roomId, false)) {
                                Log.d(LOG_TAG, "Find the room from room ID : process it");
                                processIntentUniversalLink();
                            } else {
                                Log.d(LOG_TAG, "Don't know the room");
                            }
                        }
                    });
                }
            }
        } else {
            Log.d(LOG_TAG, "create with no universal link");
        }

        if (intent.hasExtra(EXTRA_SHARED_INTENT_PARAMS)) {
            final Intent sharedFilesIntent = intent.getParcelableExtra(EXTRA_SHARED_INTENT_PARAMS);

            if (mSession.getDataHandler().getStore().isReady()) {
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        CommonActivityUtils.sendFilesTo(VectorHomeActivity.this, sharedFilesIntent);
                    }
                });
            } else {
                mSharedFilesIntent = sharedFilesIntent;
            }

            // ensure that it should be called once
            intent.removeExtra(EXTRA_SHARED_INTENT_PARAMS);
        }

        // check if  there is some valid session
        // the home activity could be relaunched after an application crash
        // so, reload the sessions before displaying the history
        Collection<MXSession> sessions = Matrix.getMXSessions(VectorHomeActivity.this);
        if (sessions.size() == 0) {
            Log.e(LOG_TAG, "Weird : onCreate : no session");

            if (null != Matrix.getInstance(this).getDefaultSession()) {
                Log.e(LOG_TAG, "No loaded session : reload them");
                // start splash activity and stop here
                startActivity(new Intent(VectorHomeActivity.this, SplashActivity.class));
                VectorHomeActivity.this.finish();
                return;
            }
        }

        FragmentManager fm = getSupportFragmentManager();
        mRecentsListFragment = (VectorRecentsListFragment) fm.findFragmentByTag(TAG_FRAGMENT_RECENTS_LIST);

        if (mRecentsListFragment == null) {
            // this fragment displays messages and handles all message logic
            //String matrixId, int layoutResId)

            mRecentsListFragment = VectorRecentsListFragment.newInstance(mSession.getCredentials().userId, R.layout.fragment_vector_recents_list);
            fm.beginTransaction().add(R.id.home_recents_list_anchor, mRecentsListFragment, TAG_FRAGMENT_RECENTS_LIST).commit();
        }

        // clear the notification if they are not anymore valid
        // i.e the event has been read from another client
        // or deleted it
        // + other actions which require a background listener
        mLiveEventListener = new MXEventListener() {
            @Override
            public void onLiveEventsChunkProcessed(String fromToken, String toToken) {
                // treat any pending URL link workflow, that was started previously
                processIntentUniversalLink();

                if (mClearCacheRequired) {
                    mClearCacheRequired = false;
                    Matrix.getInstance(VectorHomeActivity.this).reloadSessions(VectorHomeActivity.this);
                }
            }

            @Override
            public void onStoreReady() {
                if (null != mSharedFilesIntent) {
                    VectorHomeActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            CommonActivityUtils.sendFilesTo(VectorHomeActivity.this, mSharedFilesIntent);
                            mSharedFilesIntent = null;
                        }
                    });
                }
            }
        };

        mSession.getDataHandler().addListener(mLiveEventListener);

        // initialize the public rooms list
        PublicRoomsManager.setSession(mSession);
        PublicRoomsManager.refreshPublicRoomsCount(null);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        CommonActivityUtils.onLowMemory(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        CommonActivityUtils.onTrimMemory(this, level);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);

        if (mSession.isAlive()) {
            mScrollToIndex = mRecentsListFragment.getFirstVisiblePosition();
        }
    }

    @Override
    public void finish() {
        super.finish();
        mScrollToIndex = -1;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // release the static instance if it is the current implementation
        if (sharedInstance == this) {
            sharedInstance = null;
        }

        // GA issue : mSession was null
        if ((null != mSession) && mSession.isAlive()) {
            mSession.getDataHandler().removeListener(mLiveEventListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister Broadcast receiver
        stopWaitingView();
        try {
            unregisterReceiver(mBrdRcvStopWaitingView);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onPause() : unregisterReceiver fails " + e.getMessage());
        }

        if (mSession.isAlive()) {
            mSession.getDataHandler().removeListener(mEventsListener);
        }

        synchronized (this) {
            if (null != mRoomCreationViewTimer) {
                mRoomCreationViewTimer.cancel();
                mRoomCreationViewTimer = null;
            }
        }

        mRecentsListFragment.setIsDirectoryDisplayed(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyPresenceManager.createPresenceManager(this, Matrix.getInstance(this).getSessions());
        MyPresenceManager.advertiseAllOnline();

        // Broadcast receiver to stop waiting screen
        registerReceiver(mBrdRcvStopWaitingView, new IntentFilter(BROADCAST_ACTION_STOP_WAITING_VIEW));

        Intent intent = getIntent();

        if (null != mAutomaticallyOpenedRoomParams) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    CommonActivityUtils.goToRoomPage(VectorHomeActivity.this, mAutomaticallyOpenedRoomParams);
                    mAutomaticallyOpenedRoomParams = null;
                }
            });
        }

        // jump to an external link
        if (null != mUniversalLinkToOpen) {
            intent.putExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI, mUniversalLinkToOpen);
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    processIntentUniversalLink();
                    mUniversalLinkToOpen = null;
                }
            });
        }

        mEventsListener = new MXEventListener() {
            @Override
            public void onAccountInfoUpdate(MyUser myUser) {
                refreshSlidingMenu();
            }

            @Override
            public void onLiveEventsChunkProcessed(String fromToken, String toToken) {
                mSyncInProgressView.setVisibility(View.GONE);
            }
        };

        if (mSession.isAlive()) {
            mSession.getDataHandler().addListener(mEventsListener);
        }

        if (null != mRoomCreationFab) {
            mRoomCreationFab.show();
        }

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshSlidingMenu();
            }
        });

        mVectorPendingCallView.checkPendingCall();

        // check if the GA accepts to send crash reports.
        // do not display this alert if there is an universal link management
        if (null == GAHelper.useGA(this) && (null == mUseGAAlert) && (null == mUniversalLinkToOpen) && (null == mAutomaticallyOpenedRoomParams)) {
            mUseGAAlert = new AlertDialog.Builder(this);

            mUseGAAlert.setMessage(getApplicationContext().getString(R.string.ga_use_alert_message)).setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (null != VectorApp.getInstance()) {
                        mUseGAAlert = null;
                        GAHelper.setUseGA(VectorHomeActivity.this, true);
                    }
                }
            }).setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (null != VectorApp.getInstance()) {
                        mUseGAAlert = null;
                        GAHelper.setUseGA(VectorHomeActivity.this, false);
                    }
                }
            }).show();
        }

        if (!mStorePermissionCheck) {
            mStorePermissionCheck = true;
            CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_HOME_ACTIVITY, this);
        }

        if (null != mMemberIdToOpen) {
            Intent startRoomInfoIntent = new Intent(VectorHomeActivity.this, VectorMemberDetailsActivity.class);
            startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, mMemberIdToOpen);
            startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
            startActivity(startRoomInfoIntent);
            mMemberIdToOpen = null;
        }

        // https://github.com/vector-im/vector-android/issues/323
        // the tool bar color is not restored on some devices.
        mToolbar.setBackgroundResource(R.color.vector_actionbar_background);

        checkDeviceId();

        if (-1 != mScrollToIndex) {
            mRecentsListFragment.setFirstVisiblePosition(mScrollToIndex);
            // reinit in the fragment scrolllistener
            //mScrollToIndex = -1;
        }

        mSyncInProgressView.setVisibility(VectorApp.isSessionSyncing(mSession) ? View.VISIBLE : View.GONE);

        displayCryptoCorruption();
    }

    /**
     * Display an alert to warn the user that some crypto data is corrupted.
     */
    private void displayCryptoCorruption() {
        if ((null != mSession) && (null != mSession.getCrypto()) && mSession.getCrypto().isCorrupted()) {
            final String isFirstCryptoAlertKey = "isFirstCryptoAlertKey";

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

            if (preferences.getBoolean(isFirstCryptoAlertKey, true)) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(isFirstCryptoAlertKey, false);
                editor.commit();

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setMessage(getString(R.string.e2e_need_log_in_again));

                // set dialog message
                alertDialogBuilder
                        .setCancelable(true)
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        CommonActivityUtils.logout(VectorApp.getCurrentActivity(), true);
                                    }
                                });
                // create alert dialog
                AlertDialog alertDialog = alertDialogBuilder.create();
                // show it
                alertDialog.show();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        mAutomaticallyOpenedRoomParams = (Map<String, Object>)intent.getSerializableExtra(EXTRA_JUMP_TO_ROOM_PARAMS);
        intent.removeExtra(EXTRA_JUMP_TO_ROOM_PARAMS);

        mUniversalLinkToOpen = intent.getParcelableExtra(EXTRA_JUMP_TO_UNIVERSAL_LINK);
        intent.removeExtra(EXTRA_JUMP_TO_UNIVERSAL_LINK);

        mMemberIdToOpen = intent.getStringExtra(EXTRA_MEMBER_ID);
        intent.removeExtra(EXTRA_MEMBER_ID);


        // start waiting view
        if(intent.getBooleanExtra(EXTRA_WAITING_VIEW_STATUS, VectorHomeActivity.WAITING_VIEW_STOP)) {
            showWaitingView();
        } else {
            stopWaitingView();
        }
        intent.removeExtra(EXTRA_WAITING_VIEW_STATUS);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // the application is in a weird state
        if (CommonActivityUtils.shouldRestartApp(this)) {
            return false;
        }

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.vector_home, menu);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerVisible(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean retCode = true;

        switch(item.getItemId()) {
            // search in rooms content
            case R.id.ic_action_search_room:
                final Intent searchIntent = new Intent(VectorHomeActivity.this, VectorUnifiedSearchActivity.class);
                VectorHomeActivity.this.startActivity(searchIntent);
                break;

            // search in rooms content
            case R.id.ic_action_mark_all_as_read:
                if(markAllMessagesAsReadWhenOffline()) {
                    // update badge unread count in case device is offline
                    CommonActivityUtils.specificUpdateBadgeUnreadCount(mSession, getApplicationContext());
                } else {
                    markAllMessagesAsRead();
                }
                break;

            default:
                // not handled item, return the super class implementation value
                retCode = super.onOptionsItemSelected(item);
                break;
        }
        return retCode;
    }


    @Override
    public void onRequestPermissionsResult(int aRequestCode,@NonNull String[] aPermissions, @NonNull int[] aGrantResults) {
        if (0 == aPermissions.length) {
            Log.e(LOG_TAG, "## onRequestPermissionsResult(): cancelled " + aRequestCode);
        } else if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_HOME_ACTIVITY) {
            Log.w(LOG_TAG, "## onRequestPermissionsResult(): REQUEST_CODE_PERMISSION_HOME_ACTIVITY");
        } else {
            Log.e(LOG_TAG, "## onRequestPermissionsResult(): unknown RequestCode = " + aRequestCode);
        }
    }

    // RoomEventListener
    private void showWaitingView() {
        if(null != mWaitingView) {
            mWaitingView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Open the room creation with inviting people.
     */
    private void invitePeopleToNewRoom() {
        final Intent settingsIntent = new Intent(VectorHomeActivity.this, VectorRoomCreationActivity.class);
        settingsIntent.putExtra(MXCActionBarActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
        VectorHomeActivity.this.startActivity(settingsIntent);
    }

    /**
     * Create a room and open the dedicated activity
     */
    private void createRoom() {
        mWaitingView.setVisibility(View.VISIBLE);
        mSession.createRoom(new SimpleApiCallback<String>(VectorHomeActivity.this) {
            @Override
            public void onSuccess(final String roomId) {
                mWaitingView.post(new Runnable() {
                    @Override
                    public void run() {
                        mWaitingView.setVisibility(View.GONE);

                        HashMap<String, Object> params = new HashMap<>();
                        params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                        params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
                        params.put(VectorRoomActivity.EXTRA_EXPAND_ROOM_HEADER, true);
                        CommonActivityUtils.goToRoomPage(VectorHomeActivity.this, mSession, params);
                    }
                });
            }


            private void onError(final String message) {
                mWaitingView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != message) {
                            Toast.makeText(VectorHomeActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                        mWaitingView.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(final MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                onError(e.getLocalizedMessage());
            }
        });
    }

    /**
     * Send a read receipt for each room.
     * Recursive method to serialize read receipts processing.
     * Sessions and summaries are all parsed through iterators.
     * This method is based on the call back {@link #mSendReceiptCallback} that
     * will stop the downloading spinner screen after each read receipt be sent.
     * See also {@link #sendReadReceipt()}.
     */
    private void markAllMessagesAsRead() {
        Log.d(LOG_TAG, "## markAllMessagesAsRead(): IN");

        // sanity check
        if(null == mReadReceiptSessionListIterator) {
            ArrayList<MXSession> sessionsList = new ArrayList<>(Matrix.getMXSessions(this));
            mReadReceiptSessionListIterator = sessionsList.iterator();
        }

        // 1 - init summary iterator
        if (null == mReadReceiptSummaryListIterator) {
            if (mReadReceiptSessionListIterator.hasNext()) {
                MXSession session = (MXSession) mReadReceiptSessionListIterator.next();

                if (null != session) {
                    // test if the session is still alive i.e the account has not been logged out
                    if (session.isAlive()) {
                        mReadReceiptStore = session.getDataHandler().getStore();
                        ArrayList<RoomSummary> summaries = new ArrayList<>(mReadReceiptStore.getSummaries());
                        mReadReceiptSummaryListIterator = summaries.iterator();

                        if (mReadReceiptSummaryListIterator.hasNext()) {
                            sendReadReceipt();
                        }

                    } else {
                        markAllMessagesAsRead();
                    }
                }
            } else {
                // no more sessions: session list is empty, reset used fields.
                Log.d(LOG_TAG, "## markAllMessagesAsRead(): no more sessions - end");
                mReadReceiptSessionListIterator = null;
                mReadReceiptSummaryListIterator = null;
                mRecentsListFragment.refresh();
            }
            // 2 - loop on next summary
        } else if (mReadReceiptSummaryListIterator.hasNext()) {
            sendReadReceipt();
        } else {
            //re start processing to loop on he next session
            Log.d(LOG_TAG, "## markAllMessagesAsRead(): no more summaries");
            mReadReceiptSummaryListIterator = null;
            markAllMessagesAsRead();
        }
    }

    /**
     * Send a read receipt for the last message of the room, when the device is offline.
     * @return true if operation was performed, false otherwise
     */
    private boolean markAllMessagesAsReadWhenOffline() {
        boolean isOperationDone = false;

        if(!Matrix.getInstance(this).isConnected()) {
            ArrayList<MXSession> sessionsList = new ArrayList<>(Matrix.getMXSessions(this));

            if (null == sessionsList) {
                Log.w(LOG_TAG, "## markAllMessagesAsReadWhenOffline(): invalid session list");
            } else {
                for (MXSession session : sessionsList) {
                    if (null != session) {
                        // test if the session is still alive i.e the account has not been logged out
                        ArrayList<Room> roomCompleteList = new ArrayList<>(session.getDataHandler().getStore().getRooms());

                        if(null != roomCompleteList) {
                            // for each room send the receipt for the latest message
                            for (Room room : roomCompleteList) {
                                isOperationDone |= room.sendReadReceipt(null);
                            }
                        }
                    }
                }
            }
        }

        if(isOperationDone) {
            // update the room badges
            mRecentsListFragment.refresh();
        }

        return isOperationDone;
    }

    /**
     * Send a read receipt and manage the spinner screen.
     * If the read receipt is effective the waiting spinner is started,
     * otherwise {@link #markAllMessagesAsRead()} is resumed to go through
     * all the summary iterator.
     */
    private void sendReadReceipt(){
        if((null != mReadReceiptSummaryListIterator) && (null != mReadReceiptStore)) {
            RoomSummary summary = (RoomSummary) mReadReceiptSummaryListIterator.next();

            if (null != summary) {
                summary.setHighlighted(false);
                Room room = mReadReceiptStore.getRoom(summary.getRoomId());
                if (null != room) {
                    if(room.sendReadReceipt(mSendReceiptCallback)) {
                        // send receipt has been sent, start the spinner waiting screen
                        showWaitingView();
                    } else {
                        // the read receipt has not been sent, just go to the next iteration
                        markAllMessagesAsRead();
                    }
                }
            }
        }
    }

    /**
     * Process the content of the current intent to detect universal link data.
     * If data present, it means that the app was started through an URL link, but due
     * to the App was not initialized properly, it has been required to re start the App.
     *
     * To indicate the App has finished its Login/Splash/Home flow, a resume action
     * is sent to the receiver.
     */
    private void processIntentUniversalLink() {
        Intent intent;
        Uri uri;

        if (null != (intent = getIntent())) {
            if (intent.hasExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI)) {
                Log.d(LOG_TAG,"## processIntentUniversalLink(): EXTRA_UNIVERSAL_LINK_URI present1");
                uri = intent.getParcelableExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);

                if (null != uri) {
                    Intent myBroadcastIntent = new Intent(VectorUniversalLinkReceiver.BROADCAST_ACTION_UNIVERSAL_LINK_RESUME);

                    myBroadcastIntent.putExtras(getIntent().getExtras());
                    myBroadcastIntent.putExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_SENDER_ID, VectorUniversalLinkReceiver.HOME_SENDER_ID);
                    sendBroadcast(myBroadcastIntent);

                    showWaitingView();

                    // use only once, remove since it has been used
                    intent.removeExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);
                    Log.d(LOG_TAG, "## processIntentUniversalLink(): Broadcast BROADCAST_ACTION_UNIVERSAL_LINK_RESUME sent");
                }
            }
        }
    }

    /**
     * Hide the waiting view?
     */
    private void stopWaitingView(){
        if(null != mWaitingView){
            mWaitingView.setVisibility(View.GONE);
        }
    }

    //==============================================================================================================
    // Sliding menu management
    //==============================================================================================================

    /**
     * Manage the e2e keys export.
     */
    private void exportKeysAndSignOut() {
        View dialogLayout = getLayoutInflater().inflate(R.layout.dialog_export_e2e_keys, null);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.encryption_export_room_keys);
        dialog.setView(dialogLayout);

        final TextInputEditText passPhrase1EditText = (TextInputEditText) dialogLayout.findViewById(R.id.dialog_e2e_keys_passphrase_edit_text);
        final TextInputEditText passPhrase2EditText = (TextInputEditText) dialogLayout.findViewById(R.id.dialog_e2e_keys_confirm_passphrase_edit_text);
        final Button exportButton = (Button) dialogLayout.findViewById(R.id.dialog_e2e_keys_export_button);
        final TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                exportButton.setEnabled(!TextUtils.isEmpty(passPhrase1EditText.getText()) && TextUtils.equals(passPhrase1EditText.getText(), passPhrase2EditText.getText()));
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        };

        passPhrase1EditText.addTextChangedListener(textWatcher);
        passPhrase2EditText.addTextChangedListener(textWatcher);

        exportButton.setEnabled(false);

        final AlertDialog exportDialog = dialog.show();

        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showWaitingView();

                CommonActivityUtils.exportKeys(mSession, passPhrase1EditText.getText().toString(), new ApiCallback<String>() {
                    private void onDone(String message) {
                        stopWaitingView();
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(VectorHomeActivity.this);
                        alertDialogBuilder.setMessage(message);

                        // set dialog message
                        alertDialogBuilder
                                .setCancelable(false)
                                .setPositiveButton(R.string.action_sign_out,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                VectorHomeActivity.this.showWaitingView();
                                                CommonActivityUtils.logout(VectorHomeActivity.this);
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

                    @Override
                    public void onSuccess(String filename) {
                        onDone(VectorHomeActivity.this.getString(R.string.encryption_export_saved_as, filename));
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

                exportDialog.dismiss();
            }
        });
    }

    private void refreshSlidingMenu() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        // use a dedicated view
        NavigationView navigationView = (NavigationView) findViewById(R.id.navigation_view);

        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                mToolbar,
                R.string.action_open,  /* "open drawer" description */
                R.string.action_close  /* "close drawer" description */
        )
        {

            public void onDrawerClosed(View view) {
                switch (VectorHomeActivity.this.mSlidingMenuIndex){
                    case R.id.sliding_menu_settings: {
                        // launch the settings activity
                        final Intent settingsIntent = new Intent(VectorHomeActivity.this, VectorSettingsActivity.class);
                        settingsIntent.putExtra(MXCActionBarActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                        VectorHomeActivity.this.startActivity(settingsIntent);
                        break;
                    }

                    case R.id.sliding_menu_send_bug_report: {
                        BugReporter.sendBugReport();
                        break;
                    }

                    case R.id.sliding_menu_sign_out: {
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(VectorHomeActivity.this);
                        alertDialogBuilder.setMessage(getString(R.string.action_sign_out_confirmation));

                        // set dialog message
                        alertDialogBuilder
                                .setCancelable(false)
                                .setPositiveButton(R.string.action_sign_out,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                VectorHomeActivity.this.showWaitingView();
                                                CommonActivityUtils.logout(VectorHomeActivity.this);
                                            }
                                        })
                                .setNeutralButton(R.string.encryption_export_export, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.cancel();

                                        VectorHomeActivity.this.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                exportKeysAndSignOut();
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

                        break;
                    }

                    case R.id.sliding_copyright_terms: {
                        VectorUtils.displayAppCopyright();
                        break;
                    }

                    case R.id.sliding_menu_app_tac: {
                        VectorUtils.displayAppTac();
                        break;
                    }

                    case R.id.sliding_menu_privacy_policy: {
                        VectorUtils.displayAppPrivacyPolicy();
                        break;
                    }

                    case R.id.sliding_menu_third_party_notices: {
                        VectorUtils.displayThirdPartyLicenses();
                        break;
                    }
                }

                VectorHomeActivity.this.mSlidingMenuIndex = -1;
            }

            public void onDrawerOpened(View drawerView) {
            }
        };

        NavigationView.OnNavigationItemSelectedListener listener = new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                mDrawerLayout.closeDrawers();
                VectorHomeActivity.this.mSlidingMenuIndex = menuItem.getItemId();
                return true;
            }
        };

        navigationView.setNavigationItemSelectedListener(listener);
        mDrawerLayout.setDrawerListener(drawerToggle);

        // display the home and title button
        if (null != getSupportActionBar()) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(getResources().getDrawable(R.drawable.ic_material_menu_white));
        }

        Menu menuNav = navigationView.getMenu();
        MenuItem aboutMenuItem = menuNav.findItem(R.id.sliding_menu_version);

        if (null != aboutMenuItem) {
            String version = this.getString(R.string.room_sliding_menu_version) + " " + VectorUtils.getApplicationVersion(this);
            aboutMenuItem.setTitle(version);
        }

        // init the main menu
        TextView displayNameTextView = (TextView) navigationView.findViewById(R.id.home_menu_main_displayname);

        if (null != displayNameTextView) {
            displayNameTextView.setText(mSession.getMyUser().displayname);
        }

        TextView userIdTextView = (TextView) navigationView.findViewById(R.id.home_menu_main_matrix_id);
        if (null != userIdTextView) {
            userIdTextView.setText(mSession.getMyUserId());
        }

        ImageView mainAvatarView = (ImageView)navigationView.findViewById(R.id.home_menu_main_avatar);

        if (null != mainAvatarView) {
            VectorUtils.loadUserAvatar(this, mSession, mainAvatarView, mSession.getMyUser());
        } else {
            // on Android M, the mNavigationView is not loaded at launch
            // so launch asap it is rendered.
            navigationView.post(new Runnable() {
                @Override
                public void run() {
                    refreshSlidingMenu();
                }
            });
        }
    }

    /**
     * Hide the (+) button for 1 second.
     */
    private void hideRoomCreationViewWithDelay() {
        // the recents list scrolls after restoring
        if (-1 != mScrollToIndex) {
            mScrollToIndex = -1;
            return;
        }
        synchronized (this) {
            if (null != mRoomCreationViewTimer) {
                mRoomCreationViewTimer.cancel();
            }

            if (null != mRoomCreationFab) {
                mRoomCreationFab.hide();
            }

            mRoomCreationViewTimer = new Timer();
            mRoomCreationViewTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (this) {
                        if (null != mRoomCreationViewTimer) {
                            mRoomCreationViewTimer.cancel();
                            mRoomCreationViewTimer = null;
                        }
                    }

                    VectorHomeActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (null != mRoomCreationFab) {
                                mRoomCreationFab.show();
                            }
                        }
                    });
                }
            }, 1000);
        }
    }

    // display the directory group if the user overscrolls for about 0.5 s
    @Override
    public void onRecentsListOverScrollUp() {
        if (!mRecentsListFragment.isDirectoryGroupDisplayed()) {
            if (-1 == mOverscrollStartTime) {
                mOverscrollStartTime = System.currentTimeMillis();
            } else if ((System.currentTimeMillis() - mOverscrollStartTime) > 500) {
                mRecentsListFragment.setIsDirectoryDisplayed(true);
            }
        }
    }

    // warn the user scrolls up
    @Override
    public void onRecentsListScrollUp() {
        // reset overscroll timer
        mOverscrollStartTime = -1;
        // hide the (+) button for a short time
        hideRoomCreationViewWithDelay();
    }

    // warn when the user scrolls downs
    @Override
    public void onRecentsListScrollDown() {
        // reset overscroll timer
        mOverscrollStartTime = -1;
        // hide the (+) button for a short time
        hideRoomCreationViewWithDelay();
    }

    // warn when the list content can be fully displayed without scrolling
    @Override
    public void onRecentsListFitsScreen() {
        if ((null != mRoomCreationFab) && !mRoomCreationFab.isShown()) {
            mRoomCreationFab.show();
        }
    }

    //==============================================================================================================
    // VOIP call management
    //==============================================================================================================

    /**
     * Start a call with a session Id and a call Id
     * @param sessionId the session Id
     * @param callId the call Id
     * @param unknownDevices the unknown e2e devices
     */
    public void startCall(String sessionId, String callId, MXUsersDevicesMap<MXDeviceInfo> unknownDevices) {
        // sanity checks
        if ((null != sessionId) && (null != callId)) {
            final Intent intent = new Intent(VectorHomeActivity.this, InComingCallActivity.class);

            intent.putExtra(VectorCallViewActivity.EXTRA_MATRIX_ID, sessionId);
            intent.putExtra(VectorCallViewActivity.EXTRA_CALL_ID, callId);

            if (null != unknownDevices) {
                intent.putExtra(VectorCallViewActivity.EXTRA_UNKNOWN_DEVICES, unknownDevices);
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    VectorHomeActivity.this.startActivity(intent);
                }
            });
        }
    }

    /**
     * End of call management.
     * @param call the ended call/
     */
    public void onCallEnd(IMXCall call) {
        if (null != call) {
            String callId = call.getCallId();
            // either the call view has been put in background
            // or the ringing started because of a notified call in lockscreen (the callview was never created)
            final boolean isActiveCall = VectorCallViewActivity.isBackgroundedCallId(callId) ||
                    (!mSession.mCallsManager.hasActiveCalls() && IMXCall.CALL_STATE_CREATED.equals(call.getCallState()));

            VectorHomeActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isActiveCall) {
                        // suspend the app if required
                        VectorApp.getInstance().onCallEnd();
                        // hide the view
                        mVectorPendingCallView.checkPendingCall();
                        // clear call in progress notification
                        EventStreamService.checkDisplayedNotification();
                        // and play a lovely sound
                        VectorCallSoundManager.startEndCallSound();
                    }
                }
            });
        }
    }

    //==============================================================================================================
    // encryption
    //==============================================================================================================

    private static final String NO_DEVICE_ID_WARNING_KEY = "NO_DEVICE_ID_WARNING_KEY";

    /**
     * In case of the app update for the e2e encryption, the app starts with no device id provided by the homeserver.
     * Ask the user to login again in order to enable e2e. Ask it once
     */
    private void checkDeviceId() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (preferences.getBoolean(NO_DEVICE_ID_WARNING_KEY, true)) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(NO_DEVICE_ID_WARNING_KEY, false);
            editor.commit();

            if (TextUtils.isEmpty(mSession.getCredentials().deviceId)) {
                new AlertDialog.Builder(VectorApp.getCurrentActivity())
                        .setMessage(R.string.e2e_enabling_on_app_update)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                CommonActivityUtils.logout(VectorHomeActivity.this, true);
                            }
                        })
                        .setNegativeButton(R.string.later, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create()
                        .show();
            }
        }
    }
}
