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
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.internal.BottomNavigationItemView;
import android.support.design.internal.BottomNavigationMenuView;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.Matrix;
import im.vector.MyPresenceManager;
import im.vector.PublicRoomsManager;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.fragments.AbsHomeFragment;
import im.vector.fragments.FavouritesFragment;
import im.vector.fragments.HomeFragment;
import im.vector.fragments.PeopleFragment;
import im.vector.fragments.RoomsFragment;
import im.vector.receiver.VectorUniversalLinkReceiver;
import im.vector.services.EventStreamService;
import im.vector.util.BugReporter;
import im.vector.util.CallsManager;
import im.vector.util.PreferencesManager;
import im.vector.util.RoomUtils;
import im.vector.util.ThemeUtils;
import im.vector.util.VectorUtils;
import im.vector.view.UnreadCounterBadgeView;
import im.vector.view.VectorPendingCallView;

/**
 * Displays the main screen of the app, with rooms the user has joined and the ability to create
 * new rooms.
 */
public class VectorHomeActivity extends RiotAppCompatActivity implements SearchView.OnQueryTextListener {

    private static final String LOG_TAG = VectorHomeActivity.class.getSimpleName();

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

    private static final String TAG_FRAGMENT_HOME = "TAG_FRAGMENT_HOME";
    private static final String TAG_FRAGMENT_FAVOURITES = "TAG_FRAGMENT_FAVOURITES";
    private static final String TAG_FRAGMENT_PEOPLE = "TAG_FRAGMENT_PEOPLE";
    private static final String TAG_FRAGMENT_ROOMS = "TAG_FRAGMENT_ROOMS";

    // Key used to restore the proper fragment after orientation change
    private static final String CURRENT_MENU_ID = "CURRENT_MENU_ID";

    // switch to a room activity
    private Map<String, Object> mAutomaticallyOpenedRoomParams = null;

    private Uri mUniversalLinkToOpen = null;

    private String mMemberIdToOpen = null;

    @BindView(R.id.listView_spinner_views)
    View mWaitingView;

    @BindView(R.id.floating_action_button)
    FloatingActionButton mFloatingActionButton;

    // mFloatingActionButton is hidden for 1s when there is scroll
    private Timer mFloatingActionButtonTimer;

    private MXEventListener mEventsListener;
    private MXEventListener mLiveEventListener;

    private AlertDialog.Builder mUseGAAlert;

    // when a member is banned, the session must be reloaded
    public static boolean mClearCacheRequired = false;

    // sliding menu management
    private int mSlidingMenuIndex = -1;

    private MXSession mSession;

    @BindView(R.id.home_toolbar)
    Toolbar mToolbar;
    @BindView(R.id.drawer_layout)
    DrawerLayout mDrawerLayout;
    @BindView(R.id.bottom_navigation)
    BottomNavigationView mBottomNavigationView;

    // calls
    @BindView(R.id.listView_pending_callview)
    VectorPendingCallView mVectorPendingCallView;

    @BindView(R.id.home_recents_sync_in_progress)
    ProgressBar mSyncInProgressView;

    @BindView(R.id.search_view)
    SearchView mSearchView;

    private boolean mStorePermissionCheck = false;

    // a shared files intent is waiting the store init
    private Intent mSharedFilesIntent = null;

    private final BroadcastReceiver mBrdRcvStopWaitingView = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopWaitingView();
        }
    };

    private FragmentManager mFragmentManager;

    // The current item selected (bottom navigation)
    private int mCurrentMenuId;

    // the current displayed fragment
    private String mCurrentFragmentTag;

    private List<Room> mDirectChatInvitations;
    private List<Room> mRoomInvitations;

    // floating action bar dialog
    private AlertDialog mFabDialog;

     /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    /**
     * @return the current instance
     */
    public static VectorHomeActivity getInstance() {
        return sharedInstance;
    }

    /*
     * *********************************************************************************************
     * Activity lifecycle
     * *********************************************************************************************
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);
        ButterKnife.bind(this);

        mFragmentManager = getSupportFragmentManager();

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

        setupNavigation();

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

        if (null != savedInstanceState) {
            // fix issue #1276
            // if there is a saved instance, it means that onSaveInstanceState has been called.
            // theses parameters must only be used once.
            // The activity might have been created after being killed by android while the application is in background
            intent.removeExtra(EXTRA_SHARED_INTENT_PARAMS);
            intent.removeExtra(EXTRA_CALL_SESSION_ID);
            intent.removeExtra(EXTRA_CALL_ID);
            intent.removeExtra(EXTRA_CALL_UNKNOWN_DEVICES);
            intent.removeExtra(EXTRA_WAITING_VIEW_STATUS);
            intent.removeExtra(EXTRA_JUMP_TO_UNIVERSAL_LINK);
            intent.removeExtra(EXTRA_JUMP_TO_ROOM_PARAMS);
            intent.removeExtra(EXTRA_MEMBER_ID);
            intent.removeExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);
        } else {

            if (intent.hasExtra(EXTRA_CALL_SESSION_ID) && intent.hasExtra(EXTRA_CALL_ID)) {
                startCall(intent.getStringExtra(EXTRA_CALL_SESSION_ID), intent.getStringExtra(EXTRA_CALL_ID), (MXUsersDevicesMap<MXDeviceInfo>) intent.getSerializableExtra(EXTRA_CALL_UNKNOWN_DEVICES));
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
                    } else if (MXSession.isRoomAlias(roomIdOrAlias)) {
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
                Log.d(LOG_TAG, "Has shared intent");

                if (mSession.getDataHandler().getStore().isReady()) {
                    this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(LOG_TAG, "shared intent : The store is ready -> display sendFilesTo");
                            CommonActivityUtils.sendFilesTo(VectorHomeActivity.this, sharedFilesIntent);
                        }
                    });
                } else {
                    Log.d(LOG_TAG, "shared intent : Wait that the store is ready");
                    mSharedFilesIntent = sharedFilesIntent;
                }

                // ensure that it should be called once
                intent.removeExtra(EXTRA_SHARED_INTENT_PARAMS);
            }
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

        final View selectedMenu;
        if (savedInstanceState != null) {
            selectedMenu = mBottomNavigationView.findViewById(savedInstanceState.getInt(CURRENT_MENU_ID, R.id.bottom_action_home));
        } else {
            selectedMenu = mBottomNavigationView.findViewById(R.id.bottom_action_home);
        }
        if (selectedMenu != null) {
            selectedMenu.performClick();
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
                            Log.d(LOG_TAG, "shared intent : the store is now ready, display sendFilesTo");
                            CommonActivityUtils.sendFilesTo(VectorHomeActivity.this, mSharedFilesIntent);
                            mSharedFilesIntent = null;
                        }
                    });
                }
            }
        };

        mSession.getDataHandler().addListener(mLiveEventListener);

        // initialize the public rooms list
        PublicRoomsManager.getInstance().setSession(mSession);
        PublicRoomsManager.getInstance().refreshPublicRoomsCount(null);

        initViews();
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

        if (mSession.isAlive()) {
            addEventsListener();
        }

        if (null != mFloatingActionButton) {
            if (mCurrentMenuId == R.id.bottom_action_favourites) {
                mFloatingActionButton.setVisibility(View.GONE);
            } else {
                mFloatingActionButton.show();
            }
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
        if (null == PreferencesManager.useGA(this) && (null == mUseGAAlert) && (null == mUniversalLinkToOpen) && (null == mAutomaticallyOpenedRoomParams)) {
            mUseGAAlert = new AlertDialog.Builder(this);

            mUseGAAlert.setMessage(getApplicationContext().getString(R.string.ga_use_alert_message)).setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (null != VectorApp.getInstance()) {
                        mUseGAAlert = null;
                        PreferencesManager.setUseGA(VectorHomeActivity.this, true);
                    }
                }
            }).setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (null != VectorApp.getInstance()) {
                        mUseGAAlert = null;
                        PreferencesManager.setUseGA(VectorHomeActivity.this, false);
                    }
                }
            }).show();
        }

        if ((null != VectorApp.getInstance()) && VectorApp.getInstance().didAppCrash()) {
            // crash reported by a rage shake
            try {
                final AlertDialog.Builder appCrashedAlert = new AlertDialog.Builder(this);
                appCrashedAlert.setMessage(getApplicationContext().getString(R.string.send_bug_report_app_crashed)).setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BugReporter.sendBugReport();
                    }
                }).setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BugReporter.deleteCrashFile(VectorHomeActivity.this);
                    }
                }).show();

                VectorApp.getInstance().clearAppCrashStatus();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## onResume() : appCrashedAlert failed " + e.getMessage());
            }
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
        TypedValue vectorActionBarColor = new TypedValue();
        this.getTheme().resolveAttribute(R.attr.riot_primary_background_color, vectorActionBarColor, true);
        mToolbar.setBackgroundResource(vectorActionBarColor.resourceId);

        checkDeviceId();

        mSyncInProgressView.setVisibility(VectorApp.isSessionSyncing(mSession) ? View.VISIBLE : View.GONE);

        displayCryptoCorruption();

        addBadgeEventsListener();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // the application is in a weird state
        if (CommonActivityUtils.shouldRestartApp(this)) {
            return false;
        }

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.vector_home, menu);
        CommonActivityUtils.tintMenuIcons(menu, ThemeUtils.getColor(this, R.attr.icon_tint_on_dark_action_bar_color));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean retCode = true;

        switch (item.getItemId()) {
            // search in rooms content
            case R.id.ic_action_global_search:
                final Intent searchIntent = new Intent(this, VectorUnifiedSearchActivity.class);

                if (R.id.bottom_action_people == mCurrentMenuId) {
                    searchIntent.putExtra(VectorUnifiedSearchActivity.EXTRA_TAB_INDEX, VectorUnifiedSearchActivity.SEARCH_PEOPLE_TAB_POSITION);
                }

                startActivity(searchIntent);
                break;

            // search in rooms content
            case R.id.ic_action_historical:
                startActivity(new Intent(this, HistoricalRoomsActivity.class));
                break;
            case R.id.ic_action_mark_all_as_read:
                // Will be handle by fragments
                retCode = false;
                break;
            default:
                // not handled item, return the super class implementation value
                retCode = super.onOptionsItemSelected(item);
                break;
        }
        return retCode;
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerVisible(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
            return;
        }

        if (!TextUtils.isEmpty(mSearchView.getQuery().toString())) {
            mSearchView.setQuery("", true);
            return;
        }

        // Clear backstack
        mFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(CURRENT_MENU_ID, mCurrentMenuId);
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
            removeEventsListener();
        }

        synchronized (this) {
            if (null != mFloatingActionButtonTimer) {
                mFloatingActionButtonTimer.cancel();
                mFloatingActionButtonTimer = null;
            }
        }

        if (mFabDialog != null) {
            // Prevent leak after orientation changed
            mFabDialog.dismiss();
            mFabDialog = null;
        }

        removeBadgeEventsListener();
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        mAutomaticallyOpenedRoomParams = (Map<String, Object>) intent.getSerializableExtra(EXTRA_JUMP_TO_ROOM_PARAMS);
        intent.removeExtra(EXTRA_JUMP_TO_ROOM_PARAMS);

        mUniversalLinkToOpen = intent.getParcelableExtra(EXTRA_JUMP_TO_UNIVERSAL_LINK);
        intent.removeExtra(EXTRA_JUMP_TO_UNIVERSAL_LINK);

        mMemberIdToOpen = intent.getStringExtra(EXTRA_MEMBER_ID);
        intent.removeExtra(EXTRA_MEMBER_ID);


        // start waiting view
        if (intent.getBooleanExtra(EXTRA_WAITING_VIEW_STATUS, VectorHomeActivity.WAITING_VIEW_STOP)) {
            showWaitingView();
        } else {
            stopWaitingView();
        }
        intent.removeExtra(EXTRA_WAITING_VIEW_STATUS);

    }

    @Override
    public void onRequestPermissionsResult(int aRequestCode, @NonNull String[] aPermissions, @NonNull int[] aGrantResults) {
        super.onRequestPermissionsResult(aRequestCode, aPermissions, aGrantResults);
        if (0 == aPermissions.length) {
            Log.e(LOG_TAG, "## onRequestPermissionsResult(): cancelled " + aRequestCode);
        } else if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_HOME_ACTIVITY) {
            Log.w(LOG_TAG, "## onRequestPermissionsResult(): REQUEST_CODE_PERMISSION_HOME_ACTIVITY");
        } else {
            Log.e(LOG_TAG, "## onRequestPermissionsResult(): unknown RequestCode = " + aRequestCode);
        }
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    /**
     * Setup navigation components of the screen (toolbar, etc.)
     */
    private void setupNavigation() {
        // Toolbar
        setSupportActionBar(mToolbar);

        // Bottom navigation view
        mBottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                updateSelectedFragment(item);
                return true;
            }
        });
    }

    /**
     * Update the displayed fragment according to the selected menu
     *
     * @param item menu item selected by the user
     */
    private void updateSelectedFragment(final MenuItem item) {
        if (mCurrentMenuId == item.getItemId()) {
            return;
        }

        Fragment fragment = null;

        switch (item.getItemId()) {
            case R.id.bottom_action_home:
                Log.d(LOG_TAG, "onNavigationItemSelected HOME");
                fragment = mFragmentManager.findFragmentByTag(TAG_FRAGMENT_HOME);
                if (fragment == null) {
                    fragment = HomeFragment.newInstance();
                }
                mCurrentFragmentTag = TAG_FRAGMENT_HOME;
                mSearchView.setQueryHint(getString(R.string.home_filter_placeholder_home));
                break;
            case R.id.bottom_action_favourites:
                Log.d(LOG_TAG, "onNavigationItemSelected FAVOURITES");
                fragment = mFragmentManager.findFragmentByTag(TAG_FRAGMENT_FAVOURITES);
                if (fragment == null) {
                    fragment = FavouritesFragment.newInstance();
                }
                mCurrentFragmentTag = TAG_FRAGMENT_FAVOURITES;
                mSearchView.setQueryHint(getString(R.string.home_filter_placeholder_favorites));
                break;
            case R.id.bottom_action_people:
                Log.d(LOG_TAG, "onNavigationItemSelected PEOPLE");
                fragment = mFragmentManager.findFragmentByTag(TAG_FRAGMENT_PEOPLE);
                if (fragment == null) {
                    fragment = PeopleFragment.newInstance();
                }
                mCurrentFragmentTag = TAG_FRAGMENT_PEOPLE;
                mSearchView.setQueryHint(getString(R.string.home_filter_placeholder_people));
                break;
            case R.id.bottom_action_rooms:
                Log.d(LOG_TAG, "onNavigationItemSelected ROOMS");
                fragment = mFragmentManager.findFragmentByTag(TAG_FRAGMENT_ROOMS);
                if (fragment == null) {
                    fragment = RoomsFragment.newInstance();
                }
                mCurrentFragmentTag = TAG_FRAGMENT_ROOMS;
                mSearchView.setQueryHint(getString(R.string.home_filter_placeholder_rooms));
                break;
        }

        synchronized (this) {
            if (null != mFloatingActionButtonTimer) {
                mFloatingActionButtonTimer.cancel();
                mFloatingActionButtonTimer = null;
            }
            mFloatingActionButton.show();
        }

        // clear waiting view
        stopWaitingView();

        // don't display the fab for the favorites tab
        mFloatingActionButton.setVisibility((item.getItemId() != R.id.bottom_action_favourites) ? View.VISIBLE : View.GONE);

        mCurrentMenuId = item.getItemId();

        if (fragment != null) {
            resetFilter();
            try {
                mFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, fragment, mCurrentFragmentTag)
                        .addToBackStack(mCurrentFragmentTag)
                        .commit();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## updateSelectedFragment() failed : " + e.getMessage());
            }
        }
    }
    /**
     * Update UI colors to match the selected tab
     *
     * @param primaryColor
     * @param secondaryColor
     */
    public void updateTabStyle(final int primaryColor, final int secondaryColor) {
        mToolbar.setBackgroundColor(primaryColor);
        mFloatingActionButton.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
        mVectorPendingCallView.updateBackgroundColor(primaryColor);
        mSyncInProgressView.setBackgroundColor(primaryColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mSyncInProgressView.setIndeterminateTintList(ColorStateList.valueOf(secondaryColor));
        } else {
            mSyncInProgressView.getIndeterminateDrawable().setColorFilter(
                    secondaryColor, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        mFloatingActionButton.setRippleColor(secondaryColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(secondaryColor);
        }

        // Set color of toolbar search view
        EditText edit = (EditText) mSearchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);
        edit.setTextColor(ThemeUtils.getColor(this, R.attr.primary_text_color));
        edit.setHintTextColor(ThemeUtils.getColor(this, R.attr.primary_hint_text_color));
    }

    /**
     * Init views
     */
    private void initViews() {
        mVectorPendingCallView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IMXCall call = CallsManager.getSharedInstance().getActiveCall();
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

        addUnreadBadges();

		// init the search view
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        // Remove unwanted left margin
        LinearLayout searchEditFrame = (LinearLayout) mSearchView.findViewById(R.id.search_edit_frame);
        if (searchEditFrame != null) {
            ViewGroup.MarginLayoutParams searchEditFrameParams = (ViewGroup.MarginLayoutParams) searchEditFrame.getLayoutParams();
            searchEditFrameParams.leftMargin = 0;
            searchEditFrame.setLayoutParams(searchEditFrameParams);
        }
        ImageView searchIcon = (ImageView) mSearchView.findViewById(R.id.search_mag_icon);
        if (searchIcon != null) {
            ViewGroup.MarginLayoutParams searchIconParams = (ViewGroup.MarginLayoutParams) searchIcon.getLayoutParams();
            searchIconParams.leftMargin = 0;
            searchIcon.setLayoutParams(searchIconParams);
        }
        mToolbar.setContentInsetStartWithNavigation(0);

        mSearchView.setMaxWidth(Integer.MAX_VALUE);
        mSearchView.setSubmitButtonEnabled(false);
        mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        mSearchView.setIconifiedByDefault(false);
        mSearchView.setOnQueryTextListener(this);

        if (null != mFloatingActionButton) {
            mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onFloatingButtonClick();
                }
            });
        }
    }

    /**
     * Reset the filter
     */
    private void resetFilter() {
        mSearchView.setQuery("", false);
        mSearchView.clearFocus();
        hideKeyboard();
    }

    /**
     * SHow teh waiting view
     */
    public void showWaitingView() {
        if (null != mWaitingView) {
            mWaitingView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hide the waiting view
     */
    public void stopWaitingView() {
        if (null != mWaitingView) {
            mWaitingView.setVisibility(View.GONE);
        }
    }

    /**
     * Tells if the waiting view is currently displayed
     *
     * @return true if the waiting view is displayed
     */
    public boolean isWaitingViewVisible() {
        return (null != mWaitingView) && (View.VISIBLE == mWaitingView.getVisibility());
    }

    /**
     * Hide the keyboard
     */
    private void hideKeyboard() {
        final View view = getCurrentFocus();
        if (view != null) {
            final InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /*
     * *********************************************************************************************
     * User action management
     * *********************************************************************************************
     */
    @Override
    public boolean onQueryTextChange(String newText) {
        // compute an unique pattern
        final String filter = newText + "-" + mCurrentMenuId;

        // wait before really triggering the search
        // else a search is triggered for each new character
        // eg "matt" triggers
        // 1 - search for m
        // 2 - search for ma
        // 3 - search for mat
        // 4 - search for matt
        // whereas only one search should have been triggered
        // else it might trigger some lags evenif the search is done in a background thread
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                String queryText = mSearchView.getQuery().toString();
                String currentFilter = queryText + "-" + mCurrentMenuId;

                // display if the pattern matched
                if (TextUtils.equals(currentFilter, filter)) {
                    applyFilter(queryText);
                }
            }
        }, 500);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return true;
    }

    /**
     * Provides the selected fragment.
     *
     * @return the displayed fragment
     */
    private Fragment getSelectedFragment() {
        Fragment fragment = null;
        switch (mCurrentMenuId) {
            case R.id.bottom_action_home:
                fragment = mFragmentManager.findFragmentByTag(TAG_FRAGMENT_HOME);
                break;
            case R.id.bottom_action_favourites:
                fragment = mFragmentManager.findFragmentByTag(TAG_FRAGMENT_FAVOURITES);
                break;
            case R.id.bottom_action_people:
                fragment = mFragmentManager.findFragmentByTag(TAG_FRAGMENT_PEOPLE);
                break;
            case R.id.bottom_action_rooms:
                fragment = mFragmentManager.findFragmentByTag(TAG_FRAGMENT_ROOMS);
                break;
        }

        return fragment;
    }

    /**
     * Communicate the search pattern to the currently displayed fragment
     * Note: fragments will handle the search using @{@link android.widget.Filter} which means
     * asynchronous filtering operations
     *
     * @param pattern
     */
    private void applyFilter(final String pattern) {
        Fragment fragment = getSelectedFragment();

        if (fragment instanceof AbsHomeFragment) {
            ((AbsHomeFragment) fragment).applyFilter(pattern.trim());
        }

        //TODO add listener to know when filtering is done and dismiss the keyboard
    }

    /**
     * Handle a universal link intent
     *
     * @param intent
     */
    private void handleUniversalLink(final Intent intent) {
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
            } else if (MXSession.isRoomAlias(roomIdOrAlias)) {
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

    /**
     * Process the content of the current intent to detect universal link data.
     * If data present, it means that the app was started through an URL link, but due
     * to the App was not initialized properly, it has been required to re start the App.
     * <p>
     * To indicate the App has finished its Login/Splash/Home flow, a resume action
     * is sent to the receiver.
     */
    private void processIntentUniversalLink() {
        Intent intent;
        Uri uri;

        if (null != (intent = getIntent())) {
            if (intent.hasExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI)) {
                Log.d(LOG_TAG, "## processIntentUniversalLink(): EXTRA_UNIVERSAL_LINK_URI present1");
                uri = intent.getParcelableExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);

                if (null != uri) {
                    // since android O
                    // set the class to avoid having "Background execution not allowed"
                    Intent myBroadcastIntent = new Intent(VectorApp.getInstance(), VectorUniversalLinkReceiver.class);
                    myBroadcastIntent.setAction(VectorUniversalLinkReceiver.BROADCAST_ACTION_UNIVERSAL_LINK_RESUME);
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

    /*
     * *********************************************************************************************
     * Floating button management
     * *********************************************************************************************
     */

    private void onFloatingButtonClick() {
        // ignore any action if there is a pending one
        if (!isWaitingViewVisible()) {
            CharSequence items[] = new CharSequence[]{getString(R.string.room_recents_start_chat), getString(R.string.room_recents_create_room), getString(R.string.room_recents_join_room)};
            mFabDialog = new AlertDialog.Builder(this)
                    .setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int n) {
                            d.cancel();
                            if (0 == n) {
                                invitePeopleToNewRoom();
                            } else if (1 == n) {
                                createRoom();
                            } else {
                                joinARoom();
                            }
                        }
                    })
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            invitePeopleToNewRoom();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    /**
     * Hide the floating action button for 1 second
     *
     * @param fragmentTag the calling fragment tag
     */
    public void hideFloatingActionButton(String fragmentTag) {
        synchronized (this) {
            // check if the calling fragment is the current one
            // during the fragment switch, the unplugged one might call this method
            // before the new one is plugged.
            // for example, if the switch is performed while the current list is scrolling.
            if (TextUtils.equals(mCurrentFragmentTag, fragmentTag)) {
                if (null != mFloatingActionButtonTimer) {
                    mFloatingActionButtonTimer.cancel();
                }

                if (null != mFloatingActionButton) {
                    mFloatingActionButton.hide();

                    try {
                        mFloatingActionButtonTimer = new Timer();
                        mFloatingActionButtonTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                synchronized (this) {
                                    if (null != mFloatingActionButtonTimer) {
                                        mFloatingActionButtonTimer.cancel();
                                        mFloatingActionButtonTimer = null;
                                    }
                                }
                                VectorHomeActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (null != mFloatingActionButton) {
                                            mFloatingActionButton.show();
                                        }
                                    }
                                });
                            }
                        }, 1000);
                    } catch (Throwable throwable) {
                        Log.e(LOG_TAG, "failed to init mFloatingActionButtonTimer " + throwable.getMessage());

                        if (null != mFloatingActionButtonTimer) {
                            mFloatingActionButtonTimer.cancel();
                            mFloatingActionButtonTimer = null;
                        }

                        VectorHomeActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (null != mFloatingActionButton) {
                                    mFloatingActionButton.show();
                                }
                            }
                        });

                    }
                }
            }
        }
    }

    /**
     * Getter for the floating action button
     *
     * @return fab view
     */
    public FloatingActionButton getFloatingActionButton() {
        return mFloatingActionButton;
    }

    /**
     * Open the room creation with inviting people.
     */
    private void invitePeopleToNewRoom() {
        final Intent settingsIntent = new Intent(VectorHomeActivity.this, VectorRoomCreationActivity.class);
        settingsIntent.putExtra(MXCActionBarActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
        startActivity(settingsIntent);
    }

    /**
     * Create a room and open the dedicated activity
     */
    private void createRoom() {
        showWaitingView();
        mSession.createRoom(new SimpleApiCallback<String>(VectorHomeActivity.this) {
            @Override
            public void onSuccess(final String roomId) {
                mWaitingView.post(new Runnable() {
                    @Override
                    public void run() {
                        stopWaitingView();

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
                        stopWaitingView();
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
     * Offer to join a room by alias or Id
     */
    private void joinARoom() {
        LayoutInflater inflater = LayoutInflater.from(this);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        View dialogView = inflater.inflate(R.layout.dialog_join_room_by_id, null);
        alertDialogBuilder.setView(dialogView);

        final EditText textInput = (EditText) dialogView.findViewById(R.id.join_room_edit_text);
        textInput.setTextColor(ThemeUtils.getColor(this, R.attr.riot_primary_text_color));

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(R.string.join,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                showWaitingView();

                                String text = textInput.getText().toString().trim();

                                mSession.joinRoom(text, new ApiCallback<String>() {
                                    @Override
                                    public void onSuccess(String roomId) {
                                        stopWaitingView();

                                        HashMap<String, Object> params = new HashMap<>();
                                        params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                                        params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
                                        CommonActivityUtils.goToRoomPage(VectorHomeActivity.this, mSession, params);
                                    }

                                    private void onError(final String message) {
                                        mWaitingView.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (null != message) {
                                                    Toast.makeText(VectorHomeActivity.this, message, Toast.LENGTH_LONG).show();
                                                }
                                                stopWaitingView();
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

        final Button joinButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);

        if (null != joinButton) {
            joinButton.setEnabled(false);
            textInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String text = textInput.getText().toString().trim();
                    joinButton.setEnabled(MXSession.isRoomId(text) || MXSession.isRoomAlias(text));
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }
    }

    /*
     * *********************************************************************************************
     * Room invitation management
     * *********************************************************************************************
     */

    public List<Room> getRoomInvitations() {
        if (mRoomInvitations == null) {
            mRoomInvitations = new ArrayList<>();
        } else {
            mRoomInvitations.clear();
        }
        if (mDirectChatInvitations == null) {
            mDirectChatInvitations = new ArrayList<>();
        } else {
            mDirectChatInvitations.clear();
        }

        if (null == mSession.getDataHandler().getStore()) {
            Log.e(LOG_TAG, "## getRoomInvitations() : null store");
            return new ArrayList<>();
        }

        Collection<RoomSummary> roomSummaries = mSession.getDataHandler().getStore().getSummaries();
        for (RoomSummary roomSummary : roomSummaries) {
            // reported by rageshake
            // i don't see how it is possible to have a null roomSummary
            if (null != roomSummary) {
                String roomSummaryId = roomSummary.getRoomId();
                Room room = mSession.getDataHandler().getStore().getRoom(roomSummaryId);

                // check if the room exists
                // the user conference rooms are not displayed.
                if (room != null && !room.isConferenceUserRoom() && room.isInvited()) {
                    if (room.isDirectChatInvitation()) {
                        mDirectChatInvitations.add(room);
                    } else {
                        mRoomInvitations.add(room);
                    }
                }
            }
        }

        // the invitations are sorted from the oldest to the more recent one
        Comparator<Room> invitationComparator = RoomUtils.getRoomsDateComparator(mSession, true);
        Collections.sort(mDirectChatInvitations, invitationComparator);
        Collections.sort(mRoomInvitations, invitationComparator);

        List<Room> roomInvites = new ArrayList<>();
        switch (mCurrentMenuId) {
            case R.id.bottom_action_people:
                roomInvites.addAll(mDirectChatInvitations);
                break;
            case R.id.bottom_action_rooms:
                roomInvites.addAll(mRoomInvitations);
                break;
            default:
                roomInvites.addAll(mDirectChatInvitations);
                roomInvites.addAll(mRoomInvitations);
                Collections.sort(roomInvites, invitationComparator);
                break;
        }

        return roomInvites;
    }

    public void onPreviewRoom(MXSession session, String roomId) {
        String roomAlias = null;

        Room room = session.getDataHandler().getRoom(roomId);
        if ((null != room) && (null != room.getLiveState())) {
            roomAlias = room.getLiveState().getAlias();
        }

        final RoomPreviewData roomPreviewData = new RoomPreviewData(mSession, roomId, null, roomAlias, null);
        CommonActivityUtils.previewRoom(this, roomPreviewData);
    }

    public void onRejectInvitation(final MXSession session, final String roomId) {
        Room room = session.getDataHandler().getRoom(roomId);

        if (null != room) {
            showWaitingView();

            room.leave(new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // clear any pending notification for this room
                            EventStreamService.cancelNotificationsForRoomId(mSession.getMyUserId(), roomId);
                            stopWaitingView();
                        }
                    });
                }

                private void onError(final String message) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopWaitingView();
                            Toast.makeText(VectorHomeActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
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

    /*
     * *********************************************************************************************
     * Sliding menu management
     * *********************************************************************************************
     */

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

                        // A crash has been reported by GA
                        try {
                            // show it
                            alertDialog.show();
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## exportKeysAndSignOut() failed " + e.getMessage());
                        }
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
        // use a dedicated view
        NavigationView navigationView = (NavigationView) findViewById(R.id.navigation_view);

        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                mToolbar,
                R.string.action_open,  /* "open drawer" description */
                R.string.action_close  /* "close drawer" description */
        ) {

            public void onDrawerClosed(View view) {
                switch (VectorHomeActivity.this.mSlidingMenuIndex) {
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
            getSupportActionBar().setHomeAsUpIndicator(CommonActivityUtils.tintDrawable(this, ContextCompat.getDrawable(this, R.drawable.ic_material_menu_white), R.attr.primary_control_color));
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

        ImageView mainAvatarView = (ImageView) navigationView.findViewById(R.id.home_menu_main_avatar);

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

    //==============================================================================================================
    // VOIP call management
    //==============================================================================================================

    /**
     * Start a call with a session Id and a call Id
     *
     * @param sessionId      the session Id
     * @param callId         the call Id
     * @param unknownDevices the unknown e2e devices
     */
    public void startCall(String sessionId, String callId, MXUsersDevicesMap<MXDeviceInfo> unknownDevices) {
        // sanity checks
        if ((null != sessionId) && (null != callId)) {
            final Intent intent = new Intent(VectorHomeActivity.this, VectorCallViewActivity.class);

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

    //==============================================================================================================
    // Unread counter badges
    //==============================================================================================================

    // Badge view <-> menu entry id
    private final Map<Integer, UnreadCounterBadgeView> mBadgeViewByIndex = new HashMap<>();

    // events listener to track required refresh
    private final MXEventListener mBadgeEventsListener = new MXEventListener() {
        private boolean mRefreshBadgeOnChunkEnd = false;

        @Override
        public void onLiveEventsChunkProcessed(String fromToken, String toToken) {
            if (mRefreshBadgeOnChunkEnd) {
                refreshUnreadBadges();
                mRefreshBadgeOnChunkEnd = false;
            }
        }

        @Override
        public void onLiveEvent(final Event event, final RoomState roomState) {
            String eventType = event.getType();

            // refresh the UI at the end of the next events chunk
            mRefreshBadgeOnChunkEnd |= ((event.roomId != null) && RoomSummary.isSupportedEvent(event)) ||
                    Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType) ||
                    Event.EVENT_TYPE_REDACTION.equals(eventType) ||
                    Event.EVENT_TYPE_TAGS.equals(eventType) ||
                    Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(eventType);

        }

        @Override
        public void onReceiptEvent(String roomId, List<String> senderIds) {
            // refresh only if the current user read some messages (to update the unread messages counters)
            mRefreshBadgeOnChunkEnd |= (senderIds.indexOf(mSession.getCredentials().userId) >= 0);
        }

        @Override
        public void onLeaveRoom(final String roomId) {
            mRefreshBadgeOnChunkEnd = true;
        }

        @Override
        public void onNewRoom(String roomId) {
            mRefreshBadgeOnChunkEnd = true;
        }

        @Override
        public void onJoinRoom(String roomId) {
            mRefreshBadgeOnChunkEnd = true;
        }

        @Override
        public void onDirectMessageChatRoomsListUpdate() {
            mRefreshBadgeOnChunkEnd = true;
        }

        @Override
        public void onRoomTagEvent(String roomId) {
            mRefreshBadgeOnChunkEnd = true;
        }
    };

    /**
     * Add the badge events listener
     */
    private void addBadgeEventsListener() {
        mSession.getDataHandler().addListener(mBadgeEventsListener);
        refreshUnreadBadges();
    }

    /**
     * Remove the badge events listener
     */
    private void removeBadgeEventsListener() {
        mSession.getDataHandler().removeListener(mBadgeEventsListener);
    }

    /**
     * Remove the BottomNavigationView menu shift
     */
    private void removeMenuShiftMode() {
        int childCount = mBottomNavigationView.getChildCount();

        for (int i = 0; i < childCount; i++) {
            if (mBottomNavigationView.getChildAt(i) instanceof BottomNavigationMenuView) {
                BottomNavigationMenuView bottomNavigationMenuView = (BottomNavigationMenuView) mBottomNavigationView.getChildAt(i);

                try {
                    Field shiftingMode = bottomNavigationMenuView.getClass().getDeclaredField("mShiftingMode");
                    shiftingMode.setAccessible(true);
                    shiftingMode.setBoolean(bottomNavigationMenuView, false);
                    shiftingMode.setAccessible(false);

                } catch (Exception e) {
                    Log.e(LOG_TAG, "## removeMenuShiftMode failed " + e.getMessage());
                }
            }
        }
    }

    /**
     * Add the unread messages badges.
     */
    private void addUnreadBadges() {
        final float scale = getResources().getDisplayMetrics().density;
        int badgeOffsetX = (int) (18 * scale + 0.5f);
        int badgeOffsetY = (int) (7 * scale + 0.5f);

        removeMenuShiftMode();

        int largeTextHeight = getResources().getDimensionPixelSize(android.support.design.R.dimen.design_bottom_navigation_active_text_size);

        for (int menuIndex = 0; menuIndex < mBottomNavigationView.getMenu().size(); menuIndex++) {
            try {
                int itemId = mBottomNavigationView.getMenu().getItem(menuIndex).getItemId();
                BottomNavigationItemView navigationItemView = (BottomNavigationItemView) mBottomNavigationView.findViewById(itemId);

                navigationItemView.setShiftingMode(false);

                Field marginField = navigationItemView.getClass().getDeclaredField("mDefaultMargin");
                marginField.setAccessible(true);
                marginField.setInt(navigationItemView, marginField.getInt(navigationItemView) + (largeTextHeight / 2));
                marginField.setAccessible(false);

                Field shiftAmountField = navigationItemView.getClass().getDeclaredField("mShiftAmount");
                shiftAmountField.setAccessible(true);
                shiftAmountField.setInt(navigationItemView, 0);
                shiftAmountField.setAccessible(false);

                navigationItemView.setChecked(navigationItemView.getItemData().isChecked());

                View iconView = navigationItemView.findViewById(R.id.icon);

                if (iconView.getParent() instanceof FrameLayout) {
                    UnreadCounterBadgeView badgeView = new UnreadCounterBadgeView(iconView.getContext());

                    // compute the new position
                    FrameLayout.LayoutParams iconViewLayoutParams = (FrameLayout.LayoutParams) iconView.getLayoutParams();
                    FrameLayout.LayoutParams badgeLayoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                    badgeLayoutParams.setMargins(iconViewLayoutParams.leftMargin + badgeOffsetX, iconViewLayoutParams.topMargin - badgeOffsetY, iconViewLayoutParams.rightMargin, iconViewLayoutParams.bottomMargin);
                    badgeLayoutParams.gravity = iconViewLayoutParams.gravity;

                    ((FrameLayout) iconView.getParent()).addView(badgeView, badgeLayoutParams);
                    mBadgeViewByIndex.put(itemId, badgeView);
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## addUnreadBadges failed " + e.getMessage());
            }
        }

        refreshUnreadBadges();
    }

    /**
     * Refresh the badges
     */
    public void refreshUnreadBadges() {
        MXDataHandler dataHandler = mSession.getDataHandler();
        // fix a crash reported by GA
        if (null == dataHandler) {
            return;
        }

        IMXStore store = dataHandler.getStore();
        // fix a crash reported by GA
        if (null == store) {
            return;
        }

        BingRulesManager bingRulesManager = dataHandler.getBingRulesManager();
        Collection<RoomSummary> summaries2 = store.getSummaries();
        HashMap<Room, RoomSummary> roomSummaryByRoom = new HashMap<>();
        HashSet<String> directChatInvitations = new HashSet<>();

        for (RoomSummary summary : summaries2) {
            Room room = store.getRoom(summary.getRoomId());

            if (null != room) {
                roomSummaryByRoom.put(room, summary);

                if (!room.isConferenceUserRoom() && room.isInvited() && room.isDirectChatInvitation()) {
                    directChatInvitations.add(room.getRoomId());
                }
            }
        }

        Set<Integer> menuIndexes = new HashSet<>(mBadgeViewByIndex.keySet());

        // the badges are not anymore displayed on the home tab
        menuIndexes.remove(R.id.bottom_action_home);

        for (Integer id : menuIndexes) {
            // use a map because contains is faster
            HashSet<String> filteredRoomIdsSet = new HashSet<>();

            if (id == R.id.bottom_action_favourites) {
                List<Room> favRooms = mSession.roomsWithTag(RoomTag.ROOM_TAG_FAVOURITE);

                for (Room room : favRooms) {
                    filteredRoomIdsSet.add(room.getRoomId());
                }
            } else if (id == R.id.bottom_action_people) {
                filteredRoomIdsSet.addAll(mSession.getDirectChatRoomIdsList());
                // Add direct chat invitations
                for (Room room : roomSummaryByRoom.keySet()) {
                    if (room.isDirectChatInvitation() && !room.isConferenceUserRoom()) {
                        filteredRoomIdsSet.add(room.getRoomId());
                    }
                }

                // remove the low priority rooms
                List<Room> lowPriorRooms = mSession.roomsWithTag(RoomTag.ROOM_TAG_LOW_PRIORITY);
                for (Room room : lowPriorRooms) {
                    filteredRoomIdsSet.remove(room.getRoomId());
                }
            } else if (id == R.id.bottom_action_rooms) {
                HashSet<String> directChatRoomIds = new HashSet<>(mSession.getDirectChatRoomIdsList());
                HashSet<String> lowPriorityRoomIds = new HashSet<>(mSession.roomIdsWithTag(RoomTag.ROOM_TAG_LOW_PRIORITY));

                directChatRoomIds.addAll(directChatInvitations);

                for (Room room : roomSummaryByRoom.keySet()) {
                    if (!room.isConferenceUserRoom() && // not a VOIP conference room
                            !directChatRoomIds.contains(room.getRoomId()) && // not a direct chat
                            !lowPriorityRoomIds.contains(room.getRoomId())) {
                        filteredRoomIdsSet.add(room.getRoomId());
                    }
                }
            }

            // compute the badge value and its displays
            int highlightCount = 0;
            int roomCount = 0;

            for (String roomId : filteredRoomIdsSet) {
                Room room = store.getRoom(roomId);

                if (null != room) {
                    highlightCount += room.getHighlightCount();

                    if (room.isInvited()) {
                        roomCount++;
                    } else {
                        int notificationCount = room.getNotificationCount();

                        if (bingRulesManager.isRoomMentionOnly(roomId)) {
                            notificationCount = room.getHighlightCount();
                        }

                        if (notificationCount > 0) {
                            roomCount++;
                        }
                    }
                }
            }

            int status = (0 != highlightCount) ? UnreadCounterBadgeView.HIGHLIGHTED :
                    ((0 != roomCount) ? UnreadCounterBadgeView.NOTIFIED : UnreadCounterBadgeView.DEFAULT);

            if (id == R.id.bottom_action_favourites) {
                mBadgeViewByIndex.get(id).updateText((roomCount > 0) ? "\u2022" : "", status);
            } else {
                mBadgeViewByIndex.get(id).updateCounter(roomCount, status);
            }
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

    //==============================================================================================================
    // Events listener
    //==============================================================================================================

    /**
     * Warn the displayed fragment about summary updates.
     */
    public void dispatchOnSummariesUpdate() {
        Fragment fragment = getSelectedFragment();

        if ((null != fragment) && (fragment instanceof AbsHomeFragment)) {
            ((AbsHomeFragment) fragment).onSummariesUpdate();
        }
    }

    /**
     * Add a MXEventListener to the session listeners.
     */
    private void addEventsListener() {
        mEventsListener = new MXEventListener() {
            // set to true when a refresh must be triggered
            private boolean mRefreshOnChunkEnd = false;

            private void onForceRefresh() {
                if (View.VISIBLE != mSyncInProgressView.getVisibility()) {
                    dispatchOnSummariesUpdate();
                }
            }

            @Override
            public void onAccountInfoUpdate(MyUser myUser) {
                refreshSlidingMenu();
            }

            @Override
            public void onInitialSyncComplete(String toToken) {
                Log.d(LOG_TAG, "## onInitialSyncComplete()");
                dispatchOnSummariesUpdate();
            }

            @Override
            public void onLiveEventsChunkProcessed(String fromToken, String toToken) {
                if ((VectorApp.getCurrentActivity() == VectorHomeActivity.this) && mRefreshOnChunkEnd) {
                    dispatchOnSummariesUpdate();
                }

                mRefreshOnChunkEnd = false;

                mSyncInProgressView.setVisibility(View.GONE);
            }

            @Override
            public void onLiveEvent(final Event event, final RoomState roomState) {
                String eventType = event.getType();

                // refresh the UI at the end of the next events chunk
                mRefreshOnChunkEnd |= ((event.roomId != null) && RoomSummary.isSupportedEvent(event)) ||
                        Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType) ||
                        Event.EVENT_TYPE_TAGS.equals(eventType) ||
                        Event.EVENT_TYPE_REDACTION.equals(eventType) ||
                        Event.EVENT_TYPE_RECEIPT.equals(eventType) ||
                        Event.EVENT_TYPE_STATE_ROOM_AVATAR.equals(eventType) ||
                        Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(eventType);
            }

            @Override
            public void onReceiptEvent(String roomId, List<String> senderIds) {
                // refresh only if the current user read some messages (to update the unread messages counters)
                mRefreshOnChunkEnd |= (senderIds.indexOf(mSession.getCredentials().userId) >= 0);
            }

            @Override
            public void onRoomTagEvent(String roomId) {
                mRefreshOnChunkEnd = true;
            }

            @Override
            public void onStoreReady() {
                onForceRefresh();
            }

            @Override
            public void onLeaveRoom(final String roomId) {
                // clear any pending notification for this room
                EventStreamService.cancelNotificationsForRoomId(mSession.getMyUserId(), roomId);
                onForceRefresh();
            }

            @Override
            public void onNewRoom(String roomId) {
                onForceRefresh();
            }

            @Override
            public void onJoinRoom(String roomId) {
                onForceRefresh();
            }

            @Override
            public void onDirectMessageChatRoomsListUpdate() {
                mRefreshOnChunkEnd = true;
            }

            @Override
            public void onEventDecrypted(Event event) {
                RoomSummary summary = mSession.getDataHandler().getStore().getSummary(event.roomId);

                if (null != summary) {
                    // test if the latest event is refreshed
                    Event latestReceivedEvent = summary.getLatestReceivedEvent();
                    if ((null != latestReceivedEvent) && TextUtils.equals(latestReceivedEvent.eventId, event.eventId)) {
                        dispatchOnSummariesUpdate();
                    }
                }
            }
        };

        mSession.getDataHandler().addListener(mEventsListener);
    }

    /**
     * Remove the MXEventListener to the session listeners.
     */
    private void removeEventsListener() {
        if (mSession.isAlive()) {
            mSession.getDataHandler().removeListener(mEventsListener);
        }
    }
}
