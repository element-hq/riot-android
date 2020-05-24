/*
 * Copyright 2014 OpenMarket Ltd
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

package im.vector.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
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
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.PreferenceManager;

import com.getbase.floatingactionbutton.AddFloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

import org.jetbrains.annotations.NotNull;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.core.BingRulesManager;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.MXPatterns;
import org.matrix.androidsdk.core.PermalinkUtils;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.crypto.keysbackup.KeysBackupStateManager;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import butterknife.BindView;
import butterknife.OnClick;
import im.vector.BuildConfig;
import im.vector.Matrix;
import im.vector.MyPresenceManager;
import im.vector.callback.OnRecoveryKeyListener;
import im.vector.PublicRoomsManager;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.extensions.ViewExtensionsKt;
import im.vector.features.logout.ProposeLogout;
import im.vector.fragments.AbsHomeFragment;
import im.vector.fragments.FavouritesFragment;
import im.vector.fragments.GroupsFragment;
import im.vector.fragments.HomeFragment;
import im.vector.fragments.PeopleFragment;
import im.vector.fragments.RoomsFragment;
import im.vector.fragments.keysbackup.setup.KeysBackupSetupSharedViewModel;
import im.vector.fragments.signout.SignOutBottomSheetDialogFragment;
import im.vector.fragments.signout.SignOutViewModel;
import im.vector.keymanager.KeyManager;
import im.vector.push.PushManager;
import im.vector.receiver.VectorUniversalLinkReceiver;
import im.vector.services.EventStreamServiceX;
import im.vector.sharedpreferences.BatnaSharedPreferences;
import im.vector.tools.VectorUncaughtExceptionHandler;
import im.vector.ui.arch.LiveEvent;
import im.vector.ui.themes.ActivityOtherThemes;
import im.vector.ui.themes.ThemeUtils;
import im.vector.util.BugReporter;
import im.vector.util.CallsManager;
import im.vector.util.HomeRoomsViewModel;
import im.vector.util.PreferencesManager;
import im.vector.util.RoomUtils;
import im.vector.util.SystemUtilsKt;
import im.vector.util.VectorUtils;
import im.vector.view.KeysBackupBanner;
import im.vector.view.UnreadCounterBadgeView;
import im.vector.view.VectorPendingCallView;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Displays the main screen of the app, with rooms the user has joined and the ability to create
 * new rooms.
 */
public class VectorHomeActivity extends VectorAppCompatActivity implements SearchView.OnQueryTextListener,
        KeysBackupBanner.Delegate {

    private static final String LOG_TAG = VectorHomeActivity.class.getSimpleName();

    // shared instance
    // only one instance of VectorHomeActivity should be used.
    private static VectorHomeActivity sharedInstance = null;

    public static final String EXTRA_JUMP_TO_ROOM_PARAMS = "VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS";

    // jump to a member details sheet
    public static final String EXTRA_MEMBER_ID = "VectorHomeActivity.EXTRA_MEMBER_ID";

    // jump to a group details sheet
    public static final String EXTRA_GROUP_ID = "VectorHomeActivity.EXTRA_GROUP_ID";

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

    public static final String EXTRA_CLEAR_EXISTING_NOTIFICATION = "VectorHomeActivity.EXTRA_CLEAR_EXISTING_NOTIFICATION";

    // the home activity is launched in shared files mode
    // i.e the user tries to send several files with VECTOR
    public static final String EXTRA_SHARED_INTENT_PARAMS = "VectorHomeActivity.EXTRA_SHARED_INTENT_PARAMS";

    private static final boolean WAITING_VIEW_STOP = false;
    public static final boolean WAITING_VIEW_START = true;

    public static final String BROADCAST_ACTION_STOP_WAITING_VIEW = "im.vector.activity.ACTION_STOP_WAITING_VIEW";

    private static final String TAG_FRAGMENT_HOME = "TAG_FRAGMENT_HOME";
    private static final String TAG_FRAGMENT_FAVOURITES = "TAG_FRAGMENT_FAVOURITES";
    private static final String TAG_FRAGMENT_PEOPLE = "TAG_FRAGMENT_PEOPLE";
    private static final String TAG_FRAGMENT_ROOMS = "TAG_FRAGMENT_ROOMS";
    private static final String TAG_FRAGMENT_GROUPS = "TAG_FRAGMENT_GROUPS";

    // Key used to restore the proper fragment after orientation change
    private static final String CURRENT_MENU_ID = "CURRENT_MENU_ID";

    // switch to a room activity
    private Map<String, Object> mAutomaticallyOpenedRoomParams = null;

    private Uri mUniversalLinkToOpen = null;

    private String mMemberIdToOpen = null;

    private String mGroupIdToOpen = null;

    @BindView(R.id.home_keys_backup_banner)
    KeysBackupBanner mKeysBackupBanner;

    @BindView(R.id.floating_action_menu)
    FloatingActionsMenu mFloatingActionsMenu;

    @BindView(R.id.fab_expand_menu_button)
    AddFloatingActionButton mFabMain;

    @BindView(R.id.button_create_room)
    FloatingActionButton mFabCreateRoom;

    @BindView(R.id.button_join_room)
    FloatingActionButton mFabJoinRoom;

    // mFloatingActionButton is hidden for 1s when there is scroll. This Runnable will show it again
    private Runnable mShowFloatingActionButtonRunnable;

    private MXEventListener mEventsListener;

    // sliding menu management
    private int mSlidingMenuIndex = -1;

    private MXSession mSession;

    private HomeRoomsViewModel mRoomsViewModel;

    @BindView(R.id.home_toolbar)
    Toolbar mToolbar;
    @BindView(R.id.drawer_layout)
    DrawerLayout mDrawerLayout;
    @BindView(R.id.bottom_navigation)
    BottomNavigationView mBottomNavigationView;

    @BindView(R.id.navigation_view)
    NavigationView navigationView;

    // calls
    @BindView(R.id.listView_pending_callview)
    VectorPendingCallView mVectorPendingCallView;

    @BindView(R.id.home_recents_sync_in_progress)
    ProgressBar mSyncInProgressView;

    @BindView(R.id.home_search_view)
    SearchView mSearchView;

    // a shared files intent is waiting the store init
    private Intent mSharedFilesIntent = null;

    private final BroadcastReceiver mBrdRcvStopWaitingView = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            hideWaitingView();
        }
    };

    private FragmentManager mFragmentManager;

    // The current item selected (bottom navigation)
    private int mCurrentMenuId;

    // the current displayed fragment
    private String mCurrentFragmentTag;

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
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @NotNull
    @Override
    public ActivityOtherThemes getOtherThemes() {
        return ActivityOtherThemes.Home.INSTANCE;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_home;
    }

    @Override
    public void initUiAndData() {
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

        // Waiting View
        setWaitingView(findViewById(R.id.listView_spinner_views));

        sharedInstance = this;

        setupNavigation();

        initSlidingMenu();

        mSession = Matrix.getInstance(this).getDefaultSession();
        mRoomsViewModel = new HomeRoomsViewModel(mSession);
        // track if the application update
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int version = preferences.getInt(PreferencesManager.VERSION_BUILD, 0);

        new ProposeLogout(mSession, this).process();

        if (version != BuildConfig.VERSION_CODE) {
            Log.d(LOG_TAG, "The application has been updated from version " + version + " to version " + BuildConfig.VERSION_CODE);

            // TODO add some dedicated actions here

            preferences.edit()
                    .putInt(PreferencesManager.VERSION_BUILD, BuildConfig.VERSION_CODE)
                    .apply();
        }

        // Use the SignOutViewModel, it observe the keys backup state and this is what we need here
        SignOutViewModel model = ViewModelProviders.of(this).get(SignOutViewModel.class);

        model.init(mSession);

        model.getKeysBackupState().observe(this, keysBackupState -> {
            if (keysBackupState == null) {
                mKeysBackupBanner.render(KeysBackupBanner.State.Hidden.INSTANCE, false);
            } else {
                switch (keysBackupState) {
                    case Disabled:
                        if (BuildConfig.IS_SABA) {
                            KeysBackupStateManager.KeysBackupState keyBackupState = mSession.getCrypto().getKeysBackup().getState();
                            if (keyBackupState == KeysBackupStateManager.KeysBackupState.Disabled) {
                                KeyManager.getKeyBackup(this, mSession);
                            }
                        }
                        mKeysBackupBanner.render(new KeysBackupBanner.State.Setup(model.getNumberOfKeysToBackup()), false);
                        break;
                    case NotTrusted:
                    case WrongBackUpVersion:
                        // In this case, getCurrentBackupVersion() should not return ""
                        mKeysBackupBanner.render(new KeysBackupBanner.State.Recover(model.getCurrentBackupVersion()), false);
                        break;
                    case WillBackUp:
                    case BackingUp:
                        mKeysBackupBanner.render(KeysBackupBanner.State.BackingUp.INSTANCE, false);
                        break;
                    case ReadyToBackUp:
                        if (model.canRestoreKeys()) {
                            mKeysBackupBanner.render(new KeysBackupBanner.State.Update(model.getCurrentBackupVersion()), false);
                        } else {
                            mKeysBackupBanner.render(KeysBackupBanner.State.Hidden.INSTANCE, false);
                        }
                        break;
                    default:
                        mKeysBackupBanner.render(KeysBackupBanner.State.Hidden.INSTANCE, false);
                        break;
                }
            }
        });

        mKeysBackupBanner.setDelegate(this);

        // Check whether the user has agreed to the use of analytics tracking

        if (!PreferencesManager.didAskToUseAnalytics(this)) {
            promptForAnalyticsTracking();
        }

        // process intent parameters
        final Intent intent = getIntent();

        if (intent.hasExtra(EXTRA_CLEAR_EXISTING_NOTIFICATION)) {
            VectorApp.getInstance().getNotificationDrawerManager().clearAllEvents();
            intent.removeExtra(EXTRA_CLEAR_EXISTING_NOTIFICATION);
        }

        if (!isFirstCreation()) {
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
            intent.removeExtra(EXTRA_GROUP_ID);
            intent.removeExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);
        } else {

            if (intent.hasExtra(EXTRA_CALL_SESSION_ID) && intent.hasExtra(EXTRA_CALL_ID)) {
                startCall(intent.getStringExtra(EXTRA_CALL_SESSION_ID),
                        intent.getStringExtra(EXTRA_CALL_ID),
                        (MXUsersDevicesMap<MXDeviceInfo>) intent.getSerializableExtra(EXTRA_CALL_UNKNOWN_DEVICES));
                intent.removeExtra(EXTRA_CALL_SESSION_ID);
                intent.removeExtra(EXTRA_CALL_ID);
                intent.removeExtra(EXTRA_CALL_UNKNOWN_DEVICES);
            }

            // the activity could be started with a spinner
            // because there is a pending action (like universalLink processing)
            if (intent.getBooleanExtra(EXTRA_WAITING_VIEW_STATUS, WAITING_VIEW_STOP)) {
                showWaitingView();
            } else {
                hideWaitingView();
            }
            intent.removeExtra(EXTRA_WAITING_VIEW_STATUS);

            mAutomaticallyOpenedRoomParams = (Map<String, Object>) intent.getSerializableExtra(EXTRA_JUMP_TO_ROOM_PARAMS);
            intent.removeExtra(EXTRA_JUMP_TO_ROOM_PARAMS);

            mUniversalLinkToOpen = intent.getParcelableExtra(EXTRA_JUMP_TO_UNIVERSAL_LINK);
            intent.removeExtra(EXTRA_JUMP_TO_UNIVERSAL_LINK);

            mMemberIdToOpen = intent.getStringExtra(EXTRA_MEMBER_ID);
            intent.removeExtra(EXTRA_MEMBER_ID);

            mGroupIdToOpen = intent.getStringExtra(EXTRA_GROUP_ID);
            intent.removeExtra(EXTRA_GROUP_ID);

            // the home activity has been launched with an universal link
            if (intent.hasExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI)) {
                Log.d(LOG_TAG, "Has an universal link");

                final Uri uri = intent.getParcelableExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);
                intent.removeExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);

                // detect the room could be opened without waiting the next sync
                Map<String, String> params = VectorUniversalLinkReceiver.parseUniversalLink(uri);

                if ((null != params) && params.containsKey(PermalinkUtils.ULINK_ROOM_ID_OR_ALIAS_KEY)) {
                    Log.d(LOG_TAG, "Has a valid universal link");

                    final String roomIdOrAlias = params.get(PermalinkUtils.ULINK_ROOM_ID_OR_ALIAS_KEY);

                    // it is a room ID ?
                    if (MXPatterns.isRoomId(roomIdOrAlias)) {
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
                    } else if (MXPatterns.isRoomAlias(roomIdOrAlias)) {
                        Log.d(LOG_TAG, "Has a valid universal link of the room Alias " + roomIdOrAlias);

                        showWaitingView();

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
                    runOnUiThread(new Runnable() {
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
                finish();
                return;
            }
        }

        // Open default tab
        tabsGotoConversations();

        // initialize the public rooms list
        PublicRoomsManager.getInstance().setSession(mSession);
        PublicRoomsManager.getInstance().refreshPublicRoomsCount(null);

        initViews();
    }

    /**
     * Display the Floating Action Menu if it is required
     */
    private void showFloatingActionMenuIfRequired() {
        if ((mCurrentMenuId == R.id.bottom_action_favourites) || (mCurrentMenuId == R.id.bottom_action_groups)) {
            concealFloatingActionMenu();
        } else {
            revealFloatingActionMenu();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyPresenceManager.createPresenceManager(this, Matrix.getInstance(this).getSessions());
        MyPresenceManager.advertiseAllOnline();

        VectorApp.getInstance().getNotificationDrawerManager().homeActivityDidResume(mSession != null ? mSession.getMyUserId() : null);

        // Broadcast receiver to stop waiting screen
        registerReceiver(mBrdRcvStopWaitingView, new IntentFilter(BROADCAST_ACTION_STOP_WAITING_VIEW));

        Intent intent = getIntent();

        if (null != mAutomaticallyOpenedRoomParams) {
            CommonActivityUtils.goToRoomPage(VectorHomeActivity.this, mSession, mAutomaticallyOpenedRoomParams);
            mAutomaticallyOpenedRoomParams = null;
        }

        // jump to an external link
        if (null != mUniversalLinkToOpen) {
            intent.putExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI, mUniversalLinkToOpen);

            new Handler(getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    processIntentUniversalLink();
                    mUniversalLinkToOpen = null;
                }
            }, 100);
        }

        if (mSession.isAlive()) {
            addEventsListener();
        }

        showFloatingActionMenuIfRequired();

        refreshSlidingMenu();

        mVectorPendingCallView.checkPendingCall();

        if (VectorUncaughtExceptionHandler.INSTANCE.didAppCrash(this)) {
            VectorUncaughtExceptionHandler.INSTANCE.clearAppCrashStatus(this);

            // crash reported by a rage shake
            if (!BuildConfig.IS_SABA) {
                try {
                    new AlertDialog.Builder(this)
                            .setMessage(R.string.send_bug_report_app_crashed)
                            .setCancelable(false)
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    BugReporter.sendBugReport();
                                }
                            })
                            .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    BugReporter.deleteCrashFile(VectorHomeActivity.this);
                                }
                            })
                            .show();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## onResume() : appCrashedAlert failed " + e.getMessage(), e);
                }
            }
        }

        if (null != mMemberIdToOpen) {
            Intent startRoomInfoIntent = new Intent(VectorHomeActivity.this, VectorMemberDetailsActivity.class);
            startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, mMemberIdToOpen);
            startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
            startActivity(startRoomInfoIntent);
            mMemberIdToOpen = null;
        }

        if (null != mGroupIdToOpen) {
            Intent groupIntent = new Intent(VectorHomeActivity.this, VectorGroupDetailsActivity.class);
            groupIntent.putExtra(VectorGroupDetailsActivity.EXTRA_GROUP_ID, mGroupIdToOpen);
            groupIntent.putExtra(VectorGroupDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
            startActivity(groupIntent);
            mGroupIdToOpen = null;
        }

        // https://github.com/vector-im/vector-android/issues/323
        // the tool bar color is not restored on some devices.
        TypedValue vectorActionBarColor = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorBackground, vectorActionBarColor, true);
        mToolbar.setBackgroundResource(vectorActionBarColor.resourceId);

        checkDeviceId();

        mSyncInProgressView.setVisibility(VectorApp.isSessionSyncing(mSession) ? View.VISIBLE : View.GONE);

        maybeDisplayCryptoCorruption();

        addBadgeEventsListener();

        checkNotificationPrivacySetting();

        //Force remote backup state update to update the banner if needed
        ViewModelProviders.of(this).get(SignOutViewModel.class).refreshRemoteStateIfNeeded();
    }

    /**
     * Ask the user to choose a notification privacy policy.
     */
    private void checkNotificationPrivacySetting() {


        final PushManager pushManager = Matrix.getInstance(VectorHomeActivity.this).getPushManager();

        if (pushManager.useFcm()) {
            if (!PreferencesManager.didMigrateToNotificationRework(this)) {
                PreferencesManager.setDidMigrateToNotificationRework(this);
                //By default we want to move users to NORMAL privacy, but if they were in reduced privacy we let them as is
                boolean backgroundSyncAllowed = pushManager.isBackgroundSyncAllowed();
                boolean contentSendingAllowed = pushManager.isContentSendingAllowed();

                if (contentSendingAllowed && !backgroundSyncAllowed) {
                    //former reduced, so stick with it (call to enforce)
                    pushManager.setNotificationPrivacy(PushManager.NotificationPrivacy.REDUCED, null);
                } else {
                    // default force to normal
                    pushManager.setNotificationPrivacy(PushManager.NotificationPrivacy.NORMAL, null);
                }

            }
        } else {

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                // The "Run in background" permission exists from android 6
                return;
            }

            /*
            if (pushManager.isBackgroundSyncAllowed() && !PreferencesManager.didAskUserToIgnoreBatteryOptimizations(this)) {
                PreferencesManager.setDidAskUserToIgnoreBatteryOptimizations(this);

                if (!SystemUtilsKt.isIgnoringBatteryOptimizations(this)) {
                    new AlertDialog.Builder(this)
                            .setCancelable(false)
                            .setTitle(R.string.startup_notification_fdroid_battery_optim_title)
                            .setMessage(R.string.startup_notification_fdroid_battery_optim_message)
                            .setPositiveButton(R.string.startup_notification_fdroid_battery_optim_button_grant, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Log.d(LOG_TAG, "checkNotificationPrivacySetting: user wants to grant the IgnoreBatteryOptimizations permission");

                                    // Request the battery optimization cancellation to the user
                                    SystemUtilsKt.requestDisablingBatteryOptimization(VectorHomeActivity.this,
                                            null,
                                            RequestCodesKt.BATTERY_OPTIMIZATION_FDROID_REQUEST_CODE);
                                }
                            })
                            .show();
                }
            }
            */
        }
    }

    /**
     * Display a dialog to let the user chooses if he would like to use analytics tracking
     */
    private void promptForAnalyticsTracking() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.settings_opt_in_of_analytics_prompt)
                .setPositiveButton(R.string.settings_opt_in_of_analytics_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setAnalyticsAuthorization(true);
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setAnalyticsAuthorization(false);
                    }
                })
                .show().dismiss();
    }

    private void setAnalyticsAuthorization(boolean useAnalytics) {
        PreferencesManager.setUseAnalytics(this, useAnalytics);
        PreferencesManager.setDidAskToUseAnalytics(this);
    }

    @Override
    public int getMenuRes() {
        return -1;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // the application is in a weird state
        if (CommonActivityUtils.shouldRestartApp(this)) {
            return false;
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ic_action_global_search:
                gotoGlobalSearch();
                return true;
            case R.id.ic_action_historical:
                startActivity(new Intent(this, HistoricalRoomsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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

        if (mFloatingActionsMenu.isExpanded()) {
            mFloatingActionsMenu.collapse();
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
        hideWaitingView();
        try {
            unregisterReceiver(mBrdRcvStopWaitingView);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onPause() : unregisterReceiver fails " + e.getMessage(), e);
        }

        if (mSession.isAlive()) {
            removeEventsListener();
        }

        if (mShowFloatingActionButtonRunnable != null && mFloatingActionsMenu != null) {
            mFloatingActionsMenu.removeCallbacks(mShowFloatingActionButtonRunnable);
            mShowFloatingActionButtonRunnable = null;
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

        mGroupIdToOpen = intent.getStringExtra(EXTRA_GROUP_ID);
        intent.removeExtra(EXTRA_GROUP_ID);

        // start waiting view
        if (intent.getBooleanExtra(EXTRA_WAITING_VIEW_STATUS, VectorHomeActivity.WAITING_VIEW_STOP)) {
            showWaitingView();
        } else {
            hideWaitingView();
        }
        intent.removeExtra(EXTRA_WAITING_VIEW_STATUS);

        if (intent.hasExtra(EXTRA_CLEAR_EXISTING_NOTIFICATION)) {
            VectorApp.getInstance().getNotificationDrawerManager().clearAllEvents();
            intent.removeExtra(EXTRA_CLEAR_EXISTING_NOTIFICATION);
        }

    }

    /**
     * @return
     */
    public HomeRoomsViewModel getRoomsViewModel() {
        return mRoomsViewModel;
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
            case R.id.bottom_action_groups:
                Log.d(LOG_TAG, "onNavigationItemSelected GROUPS");
                fragment = mFragmentManager.findFragmentByTag(TAG_FRAGMENT_GROUPS);
                if (fragment == null) {
                    fragment = GroupsFragment.newInstance();
                }
                mCurrentFragmentTag = TAG_FRAGMENT_GROUPS;
                mSearchView.setQueryHint(getString(R.string.home_filter_placeholder_groups));
                break;
        }

        if (mShowFloatingActionButtonRunnable != null && mFloatingActionsMenu != null) {
            mFloatingActionsMenu.removeCallbacks(mShowFloatingActionButtonRunnable);
            mShowFloatingActionButtonRunnable = null;
        }

        // hide waiting view
        hideWaitingView();

        mCurrentMenuId = item.getItemId();

        showFloatingActionMenuIfRequired();

        if (fragment != null) {
            resetFilter();
            try {
                mFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, fragment, mCurrentFragmentTag)
                        .addToBackStack(mCurrentFragmentTag)
                        .commit();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## updateSelectedFragment() failed : " + e.getMessage(), e);
            }
        }
    }

    /**
     * Update UI colors to match the selected tab
     *
     * @param primaryColor    the primary color
     * @param secondaryColor  the secondary color. If -1, primary color will be used
     * @param fabColor        the FAB color. If equals to -1, the FAB color will not be updated
     * @param fabPressedColor the pressed FAB color
     */
    public void updateTabStyle(final int primaryColor,
                               final int secondaryColor,
                               final int fabColor,
                               final int fabPressedColor) {
        // Apply primary color
        mToolbar.setBackgroundColor(primaryColor);
        mVectorPendingCallView.updateBackgroundColor(primaryColor);
        mSyncInProgressView.setBackgroundColor(primaryColor);

        // Apply secondary color
        int _secondaryColor = secondaryColor;
        if (_secondaryColor == -1) {
            _secondaryColor = primaryColor;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mSyncInProgressView.setIndeterminateTintList(ColorStateList.valueOf(_secondaryColor));
        } else {
            mSyncInProgressView.getIndeterminateDrawable().setColorFilter(
                    _secondaryColor, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(_secondaryColor);
        }

        // FAB button
        if (fabColor != -1) {
            Class menuClass = FloatingActionsMenu.class;
            try {
                Field normal = menuClass.getDeclaredField("mAddButtonColorNormal");
                normal.setAccessible(true);
                Field pressed = menuClass.getDeclaredField("mAddButtonColorPressed");
                pressed.setAccessible(true);

                normal.set(mFloatingActionsMenu, fabColor);
                pressed.set(mFloatingActionsMenu, fabPressedColor);

                mFabMain.setColorNormal(fabColor);
                mFabMain.setColorPressed(fabPressedColor);
            } catch (Exception ignored) {

            }

            mFabJoinRoom.setColorNormal(fabColor);
            mFabJoinRoom.setColorPressed(fabPressedColor);
            mFabCreateRoom.setColorNormal(fabColor);
            mFabCreateRoom.setColorPressed(fabPressedColor);
        }

        // Set color of toolbar search view
        EditText edit = mSearchView.findViewById(com.google.android.material.R.id.search_src_text);
        edit.setTextColor(ThemeUtils.INSTANCE.getColor(this, R.attr.vctr_toolbar_primary_text_color));
        edit.setHintTextColor(ThemeUtils.INSTANCE.getColor(this, R.attr.vctr_primary_hint_text_color));
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

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            startActivity(intent);
                        }
                    });
                }
            }
        });

        addUnreadBadges();

        // init the search view
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        // Remove unwanted left margin
        ViewExtensionsKt.withoutLeftMargin(mSearchView);

        mToolbar.setContentInsetStartWithNavigation(0);

        mSearchView.setMaxWidth(Integer.MAX_VALUE);
        mSearchView.setSubmitButtonEnabled(false);
        mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        mSearchView.setIconifiedByDefault(false);
        mSearchView.setOnQueryTextListener(this);

        // Set here background of labels, cause we cannot set attr color in drawable on API < 21
        Class menuClass = FloatingActionsMenu.class;
        try {
            Field fabLabelStyle = menuClass.getDeclaredField("mLabelsStyle");
            fabLabelStyle.setAccessible(true);
            fabLabelStyle.set(mFloatingActionsMenu, ThemeUtils.INSTANCE.getResourceId(this, R.style.Floating_Actions_Menu));

            Method createLabels = menuClass.getDeclaredMethod("createLabels");
            createLabels.setAccessible(true);
            createLabels.invoke(mFloatingActionsMenu);
        } catch (Exception ignored) {

        }

        mFabCreateRoom.setIconDrawable(ThemeUtils.INSTANCE.tintDrawableWithColor(
                ContextCompat.getDrawable(this, R.drawable.ic_add_white),
                ContextCompat.getColor(this, android.R.color.white)
        ));

        mFabJoinRoom.setIconDrawable(ThemeUtils.INSTANCE.tintDrawableWithColor(
                ContextCompat.getDrawable(this, R.drawable.riot_tab_rooms),
                ContextCompat.getColor(this, android.R.color.white)
        ));
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
            case R.id.bottom_action_groups:
                fragment = mFragmentManager.findFragmentByTag(TAG_FRAGMENT_GROUPS);
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
     * Display an alert to warn the user that some crypto data is corrupted.
     */
    private void maybeDisplayCryptoCorruption() {
        if ((null != mSession) && (null != mSession.getCrypto()) && mSession.getCrypto().isCorrupted()) {
            final String isFirstCryptoAlertKey = "isFirstCryptoAlertKey";

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

            if (preferences.getBoolean(isFirstCryptoAlertKey, true)) {
                preferences
                        .edit()
                        .putBoolean(isFirstCryptoAlertKey, false)
                        .apply();

                new AlertDialog.Builder(this)
                        .setMessage(R.string.e2e_need_log_in_again)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.action_sign_out,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        CommonActivityUtils.logout(VectorApp.getCurrentActivity());
                                    }
                                })
                        .show();
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

    private void revealFloatingActionMenu() {
    }

    private void concealFloatingActionMenu() {
        if (null != mFloatingActionsMenu) {
            mFloatingActionsMenu.collapse();
            ViewPropertyAnimator animator = mFabMain.animate().scaleX(0).scaleY(0).alpha(0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    if (mFloatingActionsMenu != null) {
                        mFloatingActionsMenu.setVisibility(View.GONE);
                    }
                }
            });
            animator.start();
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
                if (null != mFloatingActionsMenu) {
                    if (mShowFloatingActionButtonRunnable == null) {
                        // Avoid repeated calls.
                        concealFloatingActionMenu();
                        mShowFloatingActionButtonRunnable = new Runnable() {
                            @Override
                            public void run() {
                                mShowFloatingActionButtonRunnable = null;
                                showFloatingActionMenuIfRequired();
                            }
                        };
                    } else {
                        mFloatingActionsMenu.removeCallbacks(mShowFloatingActionButtonRunnable);
                    }

                    try {
                        mFloatingActionsMenu.postDelayed(mShowFloatingActionButtonRunnable, 1000);
                    } catch (Throwable throwable) {
                        Log.e(LOG_TAG, "failed to postDelayed " + throwable.getMessage(), throwable);

                        if (mShowFloatingActionButtonRunnable != null && mFloatingActionsMenu != null) {
                            mFloatingActionsMenu.removeCallbacks(mShowFloatingActionButtonRunnable);
                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showFloatingActionMenuIfRequired();
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
    public View getFloatingActionButton() {
        return mFabMain;
    }

    /**
     * Create a room and open the dedicated activity
     */
    private void createRoom() {
        showWaitingView();
        mSession.createRoom(new SimpleApiCallback<String>(VectorHomeActivity.this) {
            @Override
            public void onSuccess(final String roomId) {
                mToolbar.post(new Runnable() {
                    @Override
                    public void run() {
                        hideWaitingView();

                        Map<String, Object> params = new HashMap<>();
                        params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                        params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
                        params.put(VectorRoomActivity.EXTRA_EXPAND_ROOM_HEADER, true);
                        CommonActivityUtils.goToRoomPage(VectorHomeActivity.this, mSession, params);
                    }
                });
            }

            private void onError(final String message) {
                mToolbar.post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != message) {
                            Toast.makeText(VectorHomeActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                        hideWaitingView();
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(final MatrixError e) {
                if (MatrixError.M_CONSENT_NOT_GIVEN.equals(e.errcode)) {
                    getConsentNotGivenHelper().displayDialog(e);
                } else {
                    onError(e.getLocalizedMessage());
                }
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
        View dialogView = inflater.inflate(R.layout.dialog_join_room_by_id, null);

        final EditText textInput = dialogView.findViewById(R.id.join_room_edit_text);
        textInput.setTextColor(ThemeUtils.INSTANCE.getColor(this, android.R.attr.textColorTertiary));

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

        // set dialog layout
        alertDialogBuilder
                .setTitle(R.string.room_recents_join_room_title)
                .setView(dialogView);

        // set dialog message
        AlertDialog alertDialog = alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(R.string.join,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                showWaitingView();

                                String text = textInput.getText().toString().trim();

                                mSession.joinRoom(text, new ApiCallback<String>() {
                                    @Override
                                    public void onSuccess(String roomId) {
                                        hideWaitingView();

                                        Map<String, Object> params = new HashMap<>();
                                        params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                                        params.put(VectorRoomActivity.EXTRA_ROOM_ID, roomId);
                                        CommonActivityUtils.goToRoomPage(VectorHomeActivity.this, mSession, params);
                                    }

                                    private void onError(final String message) {
                                        mToolbar.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (null != message) {
                                                    Toast.makeText(VectorHomeActivity.this, message, Toast.LENGTH_LONG).show();
                                                }
                                                hideWaitingView();
                                            }
                                        });
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        onError(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onMatrixError(final MatrixError e) {
                                        if (MatrixError.M_CONSENT_NOT_GIVEN.equals(e.errcode)) {
                                            getConsentNotGivenHelper().displayDialog(e);
                                        } else {
                                            onError(e.getLocalizedMessage());
                                        }
                                    }

                                    @Override
                                    public void onUnexpectedError(final Exception e) {
                                        onError(e.getLocalizedMessage());
                                    }
                                });
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();

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
                    joinButton.setEnabled(MXPatterns.isRoomId(text) || MXPatterns.isRoomAlias(text));
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

    @NonNull
    public List<Room> getRoomInvitations() {
        List<Room> directChatInvitations = new ArrayList<>();
        List<Room> roomInvitations = new ArrayList<>();

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
                        directChatInvitations.add(room);
                    } else {
                        roomInvitations.add(room);
                    }
                }
            }
        }

        // the invitations are sorted from the oldest to the more recent one
        Comparator<Room> invitationComparator = RoomUtils.getRoomsDateComparator(mSession, true);
        Collections.sort(directChatInvitations, invitationComparator);
        Collections.sort(roomInvitations, invitationComparator);

        List<Room> roomInvites = new ArrayList<>();
        switch (mCurrentMenuId) {
            case R.id.bottom_action_people:
                roomInvites.addAll(directChatInvitations);
                break;
            case R.id.bottom_action_rooms:
                roomInvites.addAll(roomInvitations);
                break;
            default:
                roomInvites.addAll(directChatInvitations);
                roomInvites.addAll(roomInvitations);
                Collections.sort(roomInvites, invitationComparator);
                break;
        }

        return roomInvites;
    }

    public void onPreviewRoom(MXSession session, String roomId) {
        String roomAlias = null;
        String roomName = null;

        Room room = session.getDataHandler().getRoom(roomId);
        if ((null != room) && (null != room.getState())) {
            roomAlias = room.getState().getCanonicalAlias();
            roomName = room.getRoomDisplayName(this);
        }

        final RoomPreviewData roomPreviewData = new RoomPreviewData(mSession, roomId, null, roomAlias, null);
        roomPreviewData.setRoomName(roomName);
        CommonActivityUtils.previewRoom(this, roomPreviewData);
    }

    /**
     * Create the room forget / leave im.vector.callback
     *
     * @param roomId            the room id
     * @param onSuccessCallback the success im.vector.callback
     * @return the asynchronous im.vector.callback
     */
    private ApiCallback<Void> createForgetLeaveCallback(final String roomId, final ApiCallback<Void> onSuccessCallback) {
        return new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // clear any pending notification for this room
                VectorApp.getInstance().getNotificationDrawerManager().clearMessageEventOfRoom(roomId);
                hideWaitingView();

                if (null != onSuccessCallback) {
                    onSuccessCallback.onSuccess(null);
                }
            }

            private void onError(final String message) {
                hideWaitingView();
                Toast.makeText(VectorHomeActivity.this, message, Toast.LENGTH_LONG).show();
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
        };
    }

    /**
     * Trigger the room forget
     *
     * @param roomId            the room id
     * @param onSuccessCallback the success asynchronous im.vector.callback
     */
    public void onForgetRoom(final String roomId, final ApiCallback<Void> onSuccessCallback) {
        Room room = mSession.getDataHandler().getRoom(roomId);

        if (null != room) {
            showWaitingView();
            room.forget(createForgetLeaveCallback(roomId, onSuccessCallback));
        }
    }

    /**
     * Trigger the room leave / invitation reject.
     *
     * @param roomId            the room id
     * @param onSuccessCallback the success asynchronous im.vector.callback
     */
    public void onRejectInvitation(final String roomId, final ApiCallback<Void> onSuccessCallback) {
        Room room = mSession.getDataHandler().getRoom(roomId);

        if (null != room) {
            showWaitingView();
            room.leave(createForgetLeaveCallback(roomId, onSuccessCallback));
        }
    }

    /*
     * *********************************************************************************************
     * Sliding menu management
     * *********************************************************************************************
     */

    private void initSlidingMenu() {
        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(
                /* host Activity */
                this,
                /* DrawerLayout object */
                mDrawerLayout,
                mToolbar,
                /* "open drawer" description */
                R.string.action_open,
                /* "close drawer" description */
                R.string.action_close) {

            @Override
            public void onDrawerClosed(View view) {
                switch (mSlidingMenuIndex) {
                    case R.id.sliding_menu_messages: {
                        tabsGotoConversations();
                        break;
                    }

                    case R.id.sliding_menu_groups: {
                        tabsGotoGroups();
                        break;
                    }

                    case R.id.sliding_menu_settings: {
                        // launch the settings activity
                        startActivity(VectorSettingsActivity.getIntent(VectorHomeActivity.this, mSession.getMyUserId()));
                        break;
                    }

                    case R.id.sliding_menu_send_bug_report: {
                        BugReporter.sendBugReport();
                        break;
                    }

                    case R.id.sliding_menu_exit: {
                        EventStreamServiceX.Companion.onApplicationStopped(VectorHomeActivity.this);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                finish();
                                System.exit(0);
                            }
                        });

                        break;
                    }

                    case R.id.sliding_menu_sign_out: {
                        signOut(true);
                        break;
                    }

                    case R.id.sliding_menu_version: {
                        SystemUtilsKt.copyToClipboard(VectorHomeActivity.this,
                                getString(R.string.room_sliding_menu_version_x, VectorUtils.getApplicationVersion(VectorHomeActivity.this)));
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

                    case R.id.sliding_menu_debug: {
                        // This menu item is only displayed in debug build
                        startActivity(new Intent(VectorHomeActivity.this, DebugMenuActivity.class));
                        break;
                    }

                    // Saba modification: show AboutSabaActivity instead of default about pages
                    case R.id.about_saba: {
                        startActivity(new Intent(VectorHomeActivity.this, AboutSabaActivity.class));
                        break;
                    }
                }

                mSlidingMenuIndex = -1;
            }

            @Override
            public void onDrawerOpened(View drawerView) {
            }
        };

        NavigationView.OnNavigationItemSelectedListener listener = new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                mDrawerLayout.closeDrawers();
                mSlidingMenuIndex = menuItem.getItemId();
                return true;
            }
        };

        navigationView.setNavigationItemSelectedListener(listener);
        mDrawerLayout.setDrawerListener(drawerToggle);

        // display the home and title button
        if (null != getSupportActionBar()) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(ContextCompat.getDrawable(this, R.drawable.ic_material_menu_white));
        }
    }

    public void signOut(boolean withConfirmationDialog) {
        if (SignOutViewModel.Companion.doYouNeedToBeDisplayed(mSession)) {
            SignOutBottomSheetDialogFragment signoutDialog = SignOutBottomSheetDialogFragment.Companion.newInstance(mSession.getMyUserId());
            signoutDialog.setOnSignOut(() -> {
                showWaitingView();
                CommonActivityUtils.logout(VectorHomeActivity.this);
            });
            signoutDialog.show(getSupportFragmentManager(), "SO");
        } else if (withConfirmationDialog) {
            // Display a simple confirmation dialog
            new AlertDialog.Builder(this)
                    .setTitle(R.string.action_sign_out)
                    .setMessage(R.string.action_sign_out_confirmation_simple)
                    .setPositiveButton(R.string.action_sign_out, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            showWaitingView();

                            CommonActivityUtils.logout(VectorHomeActivity.this);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            showWaitingView();

            CommonActivityUtils.logout(VectorHomeActivity.this);
        }
    }

    private void refreshSlidingMenu() {
        if (navigationView == null) {
            // Activity is not resumed
            return;
        }

        Menu menuNav = navigationView.getMenu();
        MenuItem aboutMenuItem = menuNav.findItem(R.id.sliding_menu_version);

        if (null != aboutMenuItem) {
            String version = getString(R.string.room_sliding_menu_version_x, VectorUtils.getApplicationVersion(this));
            aboutMenuItem.setTitle(version);
        }

        // init the main menu
        TextView displayNameTextView = navigationView.findViewById(R.id.home_menu_main_displayname);

        if (null != displayNameTextView) {
            displayNameTextView.setText(mSession.getMyUser().displayname);

            displayNameTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Open the settings
                    mSlidingMenuIndex = R.id.sliding_menu_settings;
                    mDrawerLayout.closeDrawers();
                }
            });
        }

        TextView userIdTextView = navigationView.findViewById(R.id.home_menu_main_matrix_id);
        if (null != userIdTextView) {
            userIdTextView.setText(mSession.getMyUserId());
        }

        ImageView mainAvatarView = navigationView.findViewById(R.id.home_menu_main_avatar);

        if (null != mainAvatarView) {
            VectorUtils.loadUserAvatar(this, mSession, mainAvatarView, mSession.getMyUser());

            mainAvatarView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Open the settings
                    mSlidingMenuIndex = R.id.sliding_menu_settings;
                    mDrawerLayout.closeDrawers();
                }
            });
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
                    startActivity(intent);
                }
            });
        }
    }

    //==============================================================================================================
    // Unread counter badges
    //==============================================================================================================

    // menu entry id -> Badge view
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
            mRefreshBadgeOnChunkEnd |= ((event.roomId != null) && RoomSummary.isSupportedEvent(event))
                    || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType)
                    || Event.EVENT_TYPE_REDACTION.equals(eventType)
                    || Event.EVENT_TYPE_TAGS.equals(eventType)
                    || Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(eventType);

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
     * Add the unread messages badges.
     */
    @SuppressLint("RestrictedApi")
    private void addUnreadBadges() {
        final float scale = getResources().getDisplayMetrics().density;
        int badgeOffsetX = (int) (18 * scale + 0.5f);
        int badgeOffsetY = (int) (7 * scale + 0.5f);

        int largeTextHeight = getResources().getDimensionPixelSize(com.google.android.material.R.dimen.design_bottom_navigation_active_text_size);

        for (int menuIndex = 0; menuIndex < mBottomNavigationView.getMenu().size(); menuIndex++) {
            try {
                int itemId = mBottomNavigationView.getMenu().getItem(menuIndex).getItemId();
                BottomNavigationItemView navigationItemView = mBottomNavigationView.findViewById(itemId);

                Field marginField = navigationItemView.getClass().getDeclaredField("defaultMargin");
                marginField.setAccessible(true);
                marginField.setInt(navigationItemView, marginField.getInt(navigationItemView) + (largeTextHeight / 2));
                marginField.setAccessible(false);

                Field shiftAmountField = navigationItemView.getClass().getDeclaredField("shiftAmount");
                shiftAmountField.setAccessible(true);
                shiftAmountField.setInt(navigationItemView, 0);
                shiftAmountField.setAccessible(false);

                navigationItemView.setChecked(navigationItemView.getItemData().isChecked());

                View iconView = navigationItemView.findViewById(R.id.icon);

                if (iconView.getParent() instanceof FrameLayout) {
                    UnreadCounterBadgeView badgeView = new UnreadCounterBadgeView(iconView.getContext());

                    // compute the new position
                    FrameLayout.LayoutParams iconViewLayoutParams = (FrameLayout.LayoutParams) iconView.getLayoutParams();
                    FrameLayout.LayoutParams badgeLayoutParams
                            = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                    badgeLayoutParams.setMargins(iconViewLayoutParams.leftMargin + badgeOffsetX,
                            iconViewLayoutParams.topMargin - badgeOffsetY,
                            iconViewLayoutParams.rightMargin,
                            iconViewLayoutParams.bottomMargin);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        badgeLayoutParams.setMarginStart(iconViewLayoutParams.leftMargin + badgeOffsetX);
                        badgeLayoutParams.setMarginEnd(iconViewLayoutParams.rightMargin);
                    }

                    badgeLayoutParams.gravity = iconViewLayoutParams.gravity;

                    ((FrameLayout) iconView.getParent()).addView(badgeView, badgeLayoutParams);
                    mBadgeViewByIndex.put(itemId, badgeView);
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## addUnreadBadges failed " + e.getMessage(), e);
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
        Map<Room, RoomSummary> roomSummaryByRoom = new HashMap<>();
        Set<String> directChatInvitations = new HashSet<>();

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
            int highlightCount = 0;
            int roomCount = 0;

            // use a map because contains is faster
            Set<String> filteredRoomIdsSet = new HashSet<>();

            if (id == R.id.bottom_action_favourites) {
                List<Room> favRooms = mSession.roomsWithTag(RoomTag.ROOM_TAG_FAVOURITE);

                for (Room room : favRooms) {
                    filteredRoomIdsSet.add(room.getRoomId());
                }
            } else if (id == R.id.bottom_action_people) {
                filteredRoomIdsSet.addAll(mSession.getDataHandler().getDirectChatRoomIdsList());
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
                Set<String> directChatRoomIds = new HashSet<>(mSession.getDataHandler().getDirectChatRoomIdsList());
                Set<String> lowPriorityRoomIds = new HashSet<>(mSession.roomIdsWithTag(RoomTag.ROOM_TAG_LOW_PRIORITY));

                directChatRoomIds.addAll(directChatInvitations);

                for (Room room : roomSummaryByRoom.keySet()) {
                    if (!room.isConferenceUserRoom() && // not a VOIP conference room
                            !directChatRoomIds.contains(room.getRoomId()) && // not a direct chat
                            !lowPriorityRoomIds.contains(room.getRoomId())) {
                        filteredRoomIdsSet.add(room.getRoomId());
                    }
                }
            } else if (id == R.id.bottom_action_groups) {
                // Display number of groups invitation in the badge of groups
                roomCount = mSession.getGroupsManager().getInvitedGroups().size();
            }

            // compute the badge value and its displays
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
            preferences
                    .edit()
                    .putBoolean(NO_DEVICE_ID_WARNING_KEY, false)
                    .apply();

            if (TextUtils.isEmpty(mSession.getCredentials().deviceId)) {
                new AlertDialog.Builder(VectorHomeActivity.this)
                        .setMessage(R.string.e2e_enabling_on_app_update)
                        .setPositiveButton(R.string.action_sign_out, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                CommonActivityUtils.logout(VectorHomeActivity.this);
                            }
                        })
                        .setNegativeButton(R.string.later, null)
                        .show();
            }
        }
    }

    /* ==========================================================================================
     * UI Event
     * ========================================================================================== */

    @OnClick(R.id.button_create_room)
    void fabMenuCreateRoom() {
        mFloatingActionsMenu.collapse();
        createRoom();
    }

    @OnClick(R.id.button_join_room)
    void fabMenuJoinRoom() {
        mFloatingActionsMenu.collapse();
        joinARoom();
    }

    //==============================================================================================================
    // Events listener
    //==============================================================================================================

    /**
     * Warn the displayed fragment about room data updates.
     */
    public void onRoomDataUpdated() {
        final HomeRoomsViewModel.Result result = mRoomsViewModel.update();
        final Fragment fragment = getSelectedFragment();
        if ((null != fragment) && (fragment instanceof AbsHomeFragment)) {
            ((AbsHomeFragment) fragment).onRoomResultUpdated(result);
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
                    onRoomDataUpdated();
                }
            }

            @Override
            public void onAccountInfoUpdate(MyUser myUser) {
                refreshSlidingMenu();
            }

            @Override
            public void onInitialSyncComplete(String toToken) {
                Log.d(LOG_TAG, "## onInitialSyncComplete()");
                onRoomDataUpdated();
            }

            @Override
            public void onLiveEventsChunkProcessed(String fromToken, String toToken) {
                if ((VectorApp.getCurrentActivity() == VectorHomeActivity.this) && mRefreshOnChunkEnd) {
                    onRoomDataUpdated();
                }

                mRefreshOnChunkEnd = false;
                mSyncInProgressView.setVisibility(View.GONE);

                // treat any pending URL link workflow, that was started previously
                processIntentUniversalLink();
            }

            @Override
            public void onLiveEvent(final Event event, final RoomState roomState) {
                String eventType = event.getType();

                // refresh the UI at the end of the next events chunk
                mRefreshOnChunkEnd |= ((event.roomId != null) && RoomSummary.isSupportedEvent(event))
                        || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType)
                        || Event.EVENT_TYPE_TAGS.equals(eventType)
                        || Event.EVENT_TYPE_REDACTION.equals(eventType)
                        || Event.EVENT_TYPE_RECEIPT.equals(eventType)
                        || Event.EVENT_TYPE_STATE_ROOM_AVATAR.equals(eventType)
                        || Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(eventType);
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

                if (null != mSharedFilesIntent) {
                    Log.d(LOG_TAG, "shared intent : the store is now ready, display sendFilesTo");
                    CommonActivityUtils.sendFilesTo(VectorHomeActivity.this, mSharedFilesIntent);
                    mSharedFilesIntent = null;
                }
            }

            @Override
            public void onLeaveRoom(final String roomId) {
                // clear any pending notification for this room
                VectorApp.getInstance().getNotificationDrawerManager().clearMessageEventOfRoom(roomId);
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
            public void onEventDecrypted(String roomId, String eventId) {
                RoomSummary summary = mSession.getDataHandler().getStore().getSummary(roomId);

                if (null != summary) {
                    // test if the latest event is refreshed
                    Event latestReceivedEvent = summary.getLatestReceivedEvent();
                    if ((null != latestReceivedEvent) && TextUtils.equals(latestReceivedEvent.eventId, eventId)) {
                        onRoomDataUpdated();
                    }
                }
            }

            @Override
            public void onNewGroupInvitation(String groupId) {
                // Refresh badge
                refreshUnreadBadges();
            }

            @Override
            public void onJoinGroup(String groupId) {
                // Refresh badge (invitation accepted)
                refreshUnreadBadges();
            }

            @Override
            public void onLeaveGroup(String groupId) {
                // Refresh badge (invitation rejected)
                refreshUnreadBadges();
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

    /* ==========================================================================================
     * KeysBackupBanner Listener
     * ========================================================================================== */

    @Override
    public void setupKeysBackup() {
        startActivity(KeysBackupSetupActivity.Companion.intent(this, mSession.getMyUserId(), false));
    }

    @Override
    public void recoverKeysBackup() {
        startActivity(KeysBackupManageActivity.Companion.intent(this, mSession.getMyUserId()));
    }

    /* ==========================================================================================
     * Main Callbacks
     * ========================================================================================== */
    @OnClick(R.id.button_start_chat)
    void toolbarButtonStartChat() {
        invitePeopleToNewRoom();
    }

    @OnClick(R.id.button_global_search)
    void toolbarButtonGlobalChat() {
        gotoGlobalSearch();
    }

    /* ==========================================================================================
     * Main Helper Methods
     * ========================================================================================== */
    /**
     * Open the room creation with inviting people.
     *  This method is used for starting a new 1-1 chat.
     */
    private void invitePeopleToNewRoom() {
        final Intent settingsIntent = new Intent(VectorHomeActivity.this, VectorRoomCreationActivity.class);
        settingsIntent.putExtra(MXCActionBarActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
        startActivity(settingsIntent);
    }

    private void gotoGlobalSearch() {
        final Intent searchIntent = new Intent(this, VectorUnifiedSearchActivity.class);
        if (mCurrentMenuId == R.id.bottom_action_people) {
            searchIntent.putExtra(VectorUnifiedSearchActivity.EXTRA_TAB_INDEX, VectorUnifiedSearchActivity.SEARCH_PEOPLE_TAB_POSITION);
        }
        startActivity(searchIntent);
    }

    private void tabsGotoConversations() {
        mBottomNavigationView.findViewById(R.id.bottom_action_people).performClick();
    }

    private void tabsGotoGroups() {
        mBottomNavigationView.findViewById(R.id.bottom_action_rooms).performClick();
    }
}
