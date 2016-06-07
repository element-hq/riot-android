/*
 * Copyright 2014 OpenMarket Ltd
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PublicRoom;

import im.vector.Matrix;
import im.vector.MyPresenceManager;
import im.vector.PublicRoomsManager;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.fragments.VectorRecentsListFragment;
import im.vector.receiver.VectorUniversalLinkReceiver;
import im.vector.services.EventStreamService;
import im.vector.util.RageShake;
import im.vector.util.VectorUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays the main screen of the app, with rooms the user has joined and the ability to create
 * new rooms.
 */
public class VectorHomeActivity extends AppCompatActivity implements VectorRecentsListFragment.IVectorRecentsScrollEventListener{

    private static final String LOG_TAG = "VectorHomeActivity";

    // shared instance
    // only one instance of VectorHomeActivity should be used.
    private static VectorHomeActivity sharedInstance = null;

    public static final String EXTRA_JUMP_TO_ROOM_PARAMS = "VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS";

    // there are two ways to open an external link
    // 1- EXTRA_UNIVERSAL_LINK_URI : the link is opened asap there is an events check processed (application is launched when clicking on the link)
    // 2- EXTRA_JUMP_TO_UNIVERSAL_LINK : do not wait that an events chunck is processed.
    public static final String EXTRA_JUMP_TO_UNIVERSAL_LINK = "VectorHomeActivity.EXTRA_JUMP_TO_UNIVERSAL_LINK";
    public static final String EXTRA_WAITING_VIEW_STATUS = "VectorHomeActivity.EXTRA_WAITING_VIEW_STATUS";

    // call management
    // the home activity is launched to start a call.
    public static final String EXTRA_CALL_SESSION_ID = "VectorHomeActivity.EXTRA_CALL_SESSION_ID";
    public static final String EXTRA_CALL_ID = "VectorHomeActivity.EXTRA_CALL_ID";

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

    private View mWaitingView = null;

    private Timer mRoomCreationViewTimer = null;
    private View mRoomCreationView = null;

    private MXEventListener mEventsListener;
    private MXEventListener mLiveEventListener;

    private VectorRecentsListFragment mRecentsListFragment;

    // call listener
    private MenuItem mCallMenuItem = null;

    private AlertDialog.Builder mUseGAAlert;

    // when a member is banned, the session must be reloaded
    public static boolean mClearCacheRequired = false;

    // sliding menu management
    private NavigationView mNavigationView = null;
    private int mSlidingMenuIndex = -1;

    private android.support.v7.widget.Toolbar mToolbar;
    private MXSession mSession;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;

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

        sharedInstance = this;

        mWaitingView = findViewById(R.id.listView_spinner_views);

        // use a toolbar instead of the actionbar
        mToolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.home_toolbar);
        this.setSupportActionBar(mToolbar);
        mToolbar.setTitle(R.string.title_activity_home);
        this.setTitle(R.string.title_activity_home);

        mRoomCreationView = findViewById(R.id.listView_create_room_view);

        mRoomCreationView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mWaitingView.setVisibility(View.VISIBLE);

                mSession.createRoom(null, null, RoomState.VISIBILITY_PRIVATE, null, new SimpleApiCallback<String>(VectorHomeActivity.this) {
                    @Override
                    public void onSuccess(final String roomId) {
                        mWaitingView.post(new Runnable() {
                            @Override
                            public void run() {
                                mWaitingView.setVisibility(View.GONE);

                                HashMap<String, Object> params = new HashMap<String, Object>();
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
        });

        mSession = Matrix.getInstance(this).getDefaultSession();

        // process intent parameters
        final Intent intent = getIntent();

        if (intent.hasExtra(EXTRA_CALL_SESSION_ID) && intent.hasExtra(EXTRA_CALL_ID)) {
            startCall(intent.getStringExtra(EXTRA_CALL_SESSION_ID), intent.getStringExtra(EXTRA_CALL_ID));
        }

        // the activity could be started with a spinner
        // because there is a pending action (like universallink processing)
        if (intent.getBooleanExtra(EXTRA_WAITING_VIEW_STATUS, VectorHomeActivity.WAITING_VIEW_STOP)) {
            showWaitingView();
        } else {
            stopWaitingView();
        }

        mAutomaticallyOpenedRoomParams = (Map<String, Object>)intent.getSerializableExtra(EXTRA_JUMP_TO_ROOM_PARAMS);
        mUniversalLinkToOpen = intent.getParcelableExtra(EXTRA_JUMP_TO_UNIVERSAL_LINK);

        // the home activity has been launched with an universal link
        if (intent.hasExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI)) {
            Log.d(LOG_TAG, "Has an universal link");

            final Uri uri = intent.getParcelableExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);
            intent.removeExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);

            // detect the room could be opened without waiting the next sync
            HashMap<String, String> params = VectorUniversalLinkReceiver.parseUniversalLink(uri);

            if ((null != params) && params.containsKey(VectorUniversalLinkReceiver.ULINK_ROOM_ID_KEY)) {
                Log.d(LOG_TAG, "Has a valid universal link");

                final String roomIdOrAlias = params.get(VectorUniversalLinkReceiver.ULINK_ROOM_ID_KEY);

                // it is a room ID ?
                if (roomIdOrAlias.startsWith("!")) {

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
                } else {
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
            }  else {
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
            public void onLiveEventsChunkProcessed() {
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
        PublicRoomsManager.refresh(null);
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
        if(null != mBrdRcvStopWaitingView) {
            stopWaitingView();
            unregisterReceiver(mBrdRcvStopWaitingView);
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

        VectorApp.setCurrentActivity(null);
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
        };

        if (mSession.isAlive()) {
            mSession.getDataHandler().addListener(mEventsListener);
        }

        VectorApp.setCurrentActivity(this);

        mRoomCreationView.setVisibility(View.VISIBLE);

        refreshSlidingMenu();

        manageCallButton();

        // check if the GA accepts to send crash reports.
        // do not display this alert if there is an universal link management
        if (null == VectorApp.getInstance().useGA(this) && (null == mUseGAAlert) && (null == mUniversalLinkToOpen) && (null == mAutomaticallyOpenedRoomParams)) {
            mUseGAAlert = new AlertDialog.Builder(this);

            mUseGAAlert.setMessage(getApplicationContext().getString(R.string.ga_use_alert_message)).setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (null != VectorApp.getInstance()) {
                        mUseGAAlert = null;
                        VectorApp.getInstance().setUseGA(VectorHomeActivity.this, true);
                    }
                }
            }).setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (null != VectorApp.getInstance()) {
                        mUseGAAlert = null;
                        VectorApp.getInstance().setUseGA(VectorHomeActivity.this, false);
                    }
                }
            }).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        mAutomaticallyOpenedRoomParams = (Map<String, Object>)intent.getSerializableExtra(EXTRA_JUMP_TO_ROOM_PARAMS);
        mUniversalLinkToOpen = intent.getParcelableExtra(EXTRA_JUMP_TO_UNIVERSAL_LINK);

        // start waiting view
        if(intent.getBooleanExtra(EXTRA_WAITING_VIEW_STATUS, VectorHomeActivity.WAITING_VIEW_STOP)) {
            showWaitingView();
        } else {
            stopWaitingView();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // the application is in a weird state
        if (CommonActivityUtils.shouldRestartApp(this)) {
            return false;
        }

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.vector_home, menu);

        mCallMenuItem = menu.findItem(R.id.ic_action_resume_call);
        manageCallButton();

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
                // launch the "search in rooms" activity
                final Intent searchIntent = new Intent(VectorHomeActivity.this, VectorUnifiedSearchActivity.class);
                VectorHomeActivity.this.startActivity(searchIntent);
                break;

            // search in rooms content
            case R.id.ic_action_mark_all_as_read:
                markAllMessagesAsRead();
                break;

            case R.id.ic_action_resume_call:
                IMXCall call = CallViewActivity.getActiveCall();
                if (null != call) {
                    final Intent intent = new Intent(VectorHomeActivity.this, CallViewActivity.class);
                    intent.putExtra(CallViewActivity.EXTRA_MATRIX_ID, call.getSession().getCredentials().userId);
                    intent.putExtra(CallViewActivity.EXTRA_CALL_ID, call.getCallId());

                    VectorHomeActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            VectorHomeActivity.this.startActivity(intent);
                        }
                    });
                }
                break;

            default:
                // not handled item, return the super class implementation value
                retCode = super.onOptionsItemSelected(item);
                break;
        }
        return retCode;
    }

    // RoomEventListener
    private void showWaitingView() {
        if(null != mWaitingView) {
            mWaitingView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Send a read receipt for each room
     */
    private void markAllMessagesAsRead() {
        // flush the summaries
        ArrayList<MXSession> sessions = new ArrayList<MXSession>(Matrix.getMXSessions(this));

        for (int index = 0; index < sessions.size(); index++) {
            MXSession session = sessions.get(index);

            IMXStore store = session.getDataHandler().getStore();

            ArrayList<RoomSummary> summaries = new ArrayList<RoomSummary>(store.getSummaries());

            for(RoomSummary summary : summaries) {
                summary.setHighlighted(false);

                Room room = store.getRoom(summary.getRoomId());

                if (null != room) {
                    room.sendReadReceipt();
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

    public void stopWaitingView(){
        if(null != mWaitingView){
            mWaitingView.setVisibility(View.GONE);
        }
    }

    //==============================================================================================================
    // Sliding menu management
    //==============================================================================================================

    private void refreshSlidingMenu() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        // use a dedicated view
        mNavigationView = (NavigationView) findViewById(R.id.navigation_view);

        mDrawerToggle = new ActionBarDrawerToggle(
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
                        RageShake.getInstance().sendBugReport();
                        break;
                    }

                    case R.id.sliding_menu_logout: {
                        VectorHomeActivity.this.showWaitingView();
                        CommonActivityUtils.logout(VectorHomeActivity.this);
                        break;
                    }

                    case R.id.sliding_menu_terms: {
                        VectorUtils.displayLicenses(VectorHomeActivity.this);
                        break;
                    }

                    case R.id.sliding_menu_privacy_policy: {
                        VectorUtils.displayPrivacyPolicy(VectorHomeActivity.this);
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

        mNavigationView.setNavigationItemSelectedListener(listener);
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        // display the home and title button
        if (null != getSupportActionBar()) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(getResources().getDrawable(R.drawable.ic_material_menu_white));
        }

        Menu menuNav = mNavigationView.getMenu();
        MenuItem aboutMenuItem = menuNav.findItem(R.id.sliding_menu_version);

        if (null != aboutMenuItem) {
            String version = this.getString(R.string.room_sliding_menu_version) + " " + VectorUtils.getApplicationVersion(this);
            aboutMenuItem.setTitle(version);
        }

        // init the main menu
        TextView displaynameTextView = (TextView)  mNavigationView.findViewById(R.id.home_menu_main_displayname);

        if (null != displaynameTextView) {
            displaynameTextView.setText(mSession.getMyUser().displayname);
        }

        TextView userIdTextView = (TextView) mNavigationView.findViewById(R.id.home_menu_main_matrix_id);
        if (null != userIdTextView) {
            userIdTextView.setText(mSession.getMyUserId());
        }

        ImageView mainAvatarView = (ImageView)mNavigationView.findViewById(R.id.home_menu_main_avatar);

        if (null != mainAvatarView) {
            VectorUtils.loadUserAvatar(this, mSession, mainAvatarView, mSession.getMyUser());
        }
    }

    /**
     *
     */
    private void hideRoomCreationViewWithDelay() {
        synchronized (this) {
            if (null != mRoomCreationViewTimer) {
                mRoomCreationViewTimer.cancel();
            }

            mRoomCreationView.setVisibility(View.GONE);

            mRoomCreationViewTimer = new Timer();
            mRoomCreationViewTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (this) {
                        mRoomCreationViewTimer.cancel();
                        mRoomCreationViewTimer = null;
                    }

                    VectorHomeActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mRoomCreationView.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }, 1000);
        }
    }

    // warn the user scrolls up
    public void onRecentsListScrollUp() {
        hideRoomCreationViewWithDelay();
    }

    // warn when the user scrolls downs
    public void onRecentsListScrollDown() {
        hideRoomCreationViewWithDelay();
    }

    // warn when the list content can be fully displayed without scrolling
    public void onRecentsListFitsScreen() {
        if (mRoomCreationView.getVisibility() != View.VISIBLE) {
            mRoomCreationView.setVisibility(View.VISIBLE);
        }
    }

    //==============================================================================================================
    // VOIP call management
    //==============================================================================================================

    /**
     * Start a call with a session Id and a call Id
     * @param sessionId the session Id
     * @param callId teh call Id
     */
    public void startCall(String sessionId, String callId) {
        // sanity checks
        if ((null != sessionId) && (null != callId)) {
            // display the call activity only if the application is in background.
            if (isScreenOn()) {
                final Intent intent = new Intent(VectorHomeActivity.this, CallViewActivity.class);

                intent.putExtra(CallViewActivity.EXTRA_MATRIX_ID, sessionId);
                intent.putExtra(CallViewActivity.EXTRA_CALL_ID, callId);

                VectorHomeActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        VectorHomeActivity.this.startActivity(intent);
                    }
                });
            }
        }
    }

    /**
     * End of call management.
     * @param call the ended call/
     */
    public void onCallEnd(IMXCall call) {
        if (null != call) {
            String callId = call.getCallId();
            // either the callview has been put in background
            // or the ringing started because of a notified call in lockscreen (the callview was never created)
            final boolean isActiveCall = CallViewActivity.isBackgroundedCallId(callId) ||
                    (!mSession.mCallsManager.hasActiveCalls() && IMXCall.CALL_STATE_CREATED.equals(call.getCallState()));

            VectorHomeActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isActiveCall) {
                        // suspend the app if required
                        VectorApp.getInstance().onCallEnd();
                        // hide the call button in the menu bar
                        VectorHomeActivity.this.manageCallButton();
                        // clear call in progress notification
                        EventStreamService.getInstance().checkDisplayedNotification();
                        // and play a lovely sound
                        CallViewActivity.startEndCallSound(VectorHomeActivity.this);
                    }
                }
            });
        }
    }

    @SuppressLint("NewApi")
    /**
     * Tell if the screen is turned on
     */
    private boolean isScreenOn() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            return powerManager.isInteractive();
        } else {
            return powerManager.isScreenOn();
        }
    }

    /**
     * Display or hide the the call button.
     * it is used to resume a call.
     */
    private void manageCallButton() {
        if (null != mCallMenuItem) {
            mCallMenuItem.setVisible(CallViewActivity.getActiveCall() != null);
        }
    }
}
