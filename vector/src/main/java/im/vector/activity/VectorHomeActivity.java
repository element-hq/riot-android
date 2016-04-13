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
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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

import im.vector.Matrix;
import im.vector.MyPresenceManager;
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
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays the main screen of the app, with rooms the user has joined and the ability to create
 * new rooms.
 */
public class VectorHomeActivity extends AppCompatActivity implements VectorRecentsListFragment.IVectorRecentsScrollEventListener{

    private static final String LOG_TAG = "VectorHomeActivity";

    public static final String EXTRA_JUMP_TO_ROOM_PARAMS = "VectorHomeActivity.EXTRA_JUMP_TO_ROOM_PARAMS";

    public static final boolean IS_VOIP_ENABLED = true;

    private static final String TAG_FRAGMENT_RECENTS_LIST = "VectorHomeActivity.TAG_FRAGMENT_RECENTS_LIST";

    // switch to a room activity
    private Map<String, Object> mAutomaticallyOpenedRoomParams = null;

    private View mWaitingView = null;

    private Timer mRoomCreationViewTimer = null;
    private View mRoomCreationView = null;

    private MXEventListener mEventsListener;
    private MXEventListener mLiveEventListener;

    private VectorRecentsListFragment mRecentsListFragment;


    // call listener
    private MenuItem mCallMenuItem = null;

    private final MXCallsManager.MXCallsManagerListener mCallsManagerListener = new MXCallsManager.MXCallsManagerListener() {
        /**
         * Called when there is an incoming call within the room.
         */
        @Override
        public void onIncomingCall(final IMXCall call) {
            // can only manage one call instance.
            if (null == CallViewActivity.getActiveCall()) {
                // display the call activity only if the application is in background.
                if (VectorHomeActivity.this.isScreenOn()) {
                    // create the call object
                    if (null != call) {
                        final Intent intent = new Intent(VectorHomeActivity.this, CallViewActivity.class);

                        intent.putExtra(CallViewActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                        intent.putExtra(CallViewActivity.EXTRA_CALL_ID, call.getCallId());

                        VectorHomeActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                VectorHomeActivity.this.startActivity(intent);
                            }
                        });
                    }
                }
            } else {
                VectorHomeActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        call.hangup("busy");
                    }
                });
            }
        }

        /**
         * Called when a called has been hung up
         */
        @Override
        public void onCallHangUp(IMXCall call) {
            final Boolean isActiveCall = CallViewActivity.isBackgroundedCallId(call.getCallId());

            VectorHomeActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isActiveCall) {
                        VectorApp.getInstance().onCallEnd();
                        VectorHomeActivity.this.manageCallButton();

                        CallViewActivity.startEndCallSound(VectorHomeActivity.this);
                    }
                }
            });
        }
    };

    // sliding menu management
    private NavigationView mNavigationView = null;
    private int mSlidingMenuIndex = -1;

    private android.support.v7.widget.Toolbar mToolbar;
    private MXSession mSession;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_home);

        // use a toolbar instead of the actionbar
        mToolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.home_toolbar);
        this.setSupportActionBar(mToolbar);
        mToolbar.setTitle(R.string.title_activity_home);
        this.setTitle(R.string.title_activity_home);

        mWaitingView = findViewById(R.id.listView_spinner_views);
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

        mAutomaticallyOpenedRoomParams = (Map<String, Object>)intent.getSerializableExtra(EXTRA_JUMP_TO_ROOM_PARAMS);

        String action = intent.getAction();
        String type = intent.getType();

        // send files from external application
        if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    CommonActivityUtils.sendFilesTo(VectorHomeActivity.this, intent);
                }
            });
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
        // or deleted
        mLiveEventListener = new MXEventListener() {
            @Override
            public void onLiveEventsChunkProcessed() {
                EventStreamService.checkDisplayedNotification();

                // treat any pending URL link workflow, that was started previously
               processIntentUniversalLink();
            }
        };

        mSession.getDataHandler().addListener(mLiveEventListener);

        if (IS_VOIP_ENABLED) {
            mSession.mCallsManager.addListener(mCallsManagerListener);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSession.isAlive()) {
            mSession.getDataHandler().removeListener(mLiveEventListener);
            mSession.mCallsManager.removeListener(mCallsManagerListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSession.isAlive()) {
            mSession.getDataHandler().removeListener(mEventsListener);
        }

        if (null != mRoomCreationViewTimer) {
            mRoomCreationViewTimer.cancel();
            mRoomCreationViewTimer = null;
        }

        VectorApp.setCurrentActivity(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyPresenceManager.createPresenceManager(this, Matrix.getInstance(this).getSessions());
        MyPresenceManager.advertiseAllOnline();

        if (null != mAutomaticallyOpenedRoomParams) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    CommonActivityUtils.goToRoomPage(VectorHomeActivity.this, mAutomaticallyOpenedRoomParams);
                    mAutomaticallyOpenedRoomParams = null;
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
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.hasExtra(EXTRA_JUMP_TO_ROOM_PARAMS)) {
            mAutomaticallyOpenedRoomParams = (Map<String, Object>)intent.getSerializableExtra(EXTRA_JUMP_TO_ROOM_PARAMS);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
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
        mWaitingView.setVisibility(View.VISIBLE);
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
        if(null != (intent = getIntent())) {

            if (intent.hasExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI)) {
                Log.d("Home","## processIntentUniversalLink(): EXTRA_UNIVERSAL_LINK_URI present1");
                uri = intent.getParcelableExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);

                if (null != uri) {
                    Intent myBroadcastIntent = new Intent(VectorUniversalLinkReceiver.BROADCAST_ACTION_UNIVERSAL_LINK_RESUME);

                    myBroadcastIntent.putExtras(getIntent().getExtras());
                    myBroadcastIntent.putExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_SENDER_ID, VectorUniversalLinkReceiver.HOME_SENDER_ID);
                    sendBroadcast(myBroadcastIntent);

                    // use only once, remove since it has been used
                    intent.removeExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);
                    Log.d("Home", "## processIntentUniversalLink(): Broadcast BROADCAST_ACTION_UNIVERSAL_LINK_RESUME sent");
                }
            } else {
                Log.d("Home","## processIntentUniversalLink(): EXTRA_UNIVERSAL_LINK_URI not present");
            }
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
                        VectorUtils.displayLicense(VectorHomeActivity.this);
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
        if (null != mRoomCreationViewTimer) {
            mRoomCreationViewTimer.cancel();
        }

        mRoomCreationView.setVisibility(View.GONE);

        mRoomCreationViewTimer = new Timer();
        mRoomCreationViewTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mRoomCreationViewTimer.cancel();
                mRoomCreationViewTimer = null;

                VectorHomeActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mRoomCreationView.setVisibility(View.VISIBLE);
                    }
                });
            }
        }, 1000);
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

    @SuppressLint("NewApi")
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
