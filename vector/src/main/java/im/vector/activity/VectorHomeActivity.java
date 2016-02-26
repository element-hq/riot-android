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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.listeners.MXEventListener;

import im.vector.Matrix;
import im.vector.MyPresenceManager;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.fragments.VectorRecentsListFragment;
import im.vector.services.EventStreamService;
import im.vector.util.RageShake;
import im.vector.util.VectorUtils;

import java.util.Collection;

/**
 * Displays the main screen of the app, with rooms the user has joined and the ability to create
 * new rooms.
 */
public class VectorHomeActivity extends AppCompatActivity {

    private static final String LOG_TAG = "VectorHomeActivity";

    public static final String EXTRA_JUMP_TO_ROOM_ID = "VectorHomeActivity.EXTRA_JUMP_TO_ROOM_ID";
    public static final String EXTRA_JUMP_MATRIX_ID = "VectorHomeActivity.EXTRA_JUMP_MATRIX_ID";
    public static final String EXTRA_ROOM_INTENT = "VectorHomeActivity.EXTRA_ROOM_INTENT";

    private static final String TAG_FRAGMENT_RECENTS_LIST = "VectorHomeActivity.TAG_FRAGMENT_RECENTS_LIST";


    // switch to a room activity
    private String mAutomaticallyOpenedRoomId = null;
    private String mAutomaticallyOpenedMatrixId = null;
    private Intent mOpenedRoomIntent = null;

    private View mWaitingView = null;
    private View mRoomCreationView = null;

    private MXEventListener mEventsListener;

    private VectorRecentsListFragment mRecentsListFragment;

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
                // pop to the home activity
                Intent intent = new Intent(VectorHomeActivity.this, VectorRoomCreationActivity.class);
                VectorHomeActivity.this.startActivity(intent);
            }
        });

        mSession = Matrix.getInstance(this).getDefaultSession();

        // process intent parameters
        final Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_JUMP_TO_ROOM_ID)) {
            mAutomaticallyOpenedRoomId = intent.getStringExtra(EXTRA_JUMP_TO_ROOM_ID);
        }

        if (intent.hasExtra(EXTRA_JUMP_MATRIX_ID)) {
            mAutomaticallyOpenedMatrixId = intent.getStringExtra(EXTRA_JUMP_MATRIX_ID);
        }

        if (intent.hasExtra(EXTRA_ROOM_INTENT)) {
            mOpenedRoomIntent = intent.getParcelableExtra(EXTRA_ROOM_INTENT);
        }

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
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onLiveEventsChunkProcessed() {
                EventStreamService.checkDisplayedNotification();
            }
        };

        mSession.getDataHandler().addListener(eventListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSession.isActive()) {
            mSession.getDataHandler().removeListener(mEventsListener);
        }

        VectorApp.setCurrentActivity(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyPresenceManager.createPresenceManager(this, Matrix.getInstance(this).getSessions());
        MyPresenceManager.advertiseAllOnline();

        if (null != mAutomaticallyOpenedRoomId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    CommonActivityUtils.goToRoomPage(mAutomaticallyOpenedMatrixId, VectorHomeActivity.this.mAutomaticallyOpenedRoomId, VectorHomeActivity.this, mOpenedRoomIntent);
                    VectorHomeActivity.this.mAutomaticallyOpenedRoomId = null;
                    VectorHomeActivity.this.mAutomaticallyOpenedMatrixId = null;
                    VectorHomeActivity.this.mOpenedRoomIntent = null;
                }
            });
        }

        mEventsListener = new MXEventListener() {
            @Override
            public void onAccountInfoUpdate(MyUser myUser) {
                refreshSlidingMenu();
            }
        };

        mSession.getDataHandler().addListener(mEventsListener);

        VectorApp.setCurrentActivity(this);

        refreshSlidingMenu();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.hasExtra(EXTRA_JUMP_TO_ROOM_ID)) {
            mAutomaticallyOpenedRoomId = intent.getStringExtra(EXTRA_JUMP_TO_ROOM_ID);
        }

        if (intent.hasExtra(EXTRA_JUMP_MATRIX_ID)) {
            mAutomaticallyOpenedMatrixId = intent.getStringExtra(EXTRA_JUMP_MATRIX_ID);
        }

        if (intent.hasExtra(EXTRA_ROOM_INTENT)) {
            mOpenedRoomIntent = intent.getParcelableExtra(EXTRA_ROOM_INTENT);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
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
                // launch the "search in rooms" activity
                final Intent searchIntent = new Intent(VectorHomeActivity.this, VectorUnifiedSearchActivity.class);
                VectorHomeActivity.this.startActivity(searchIntent);
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
        displaynameTextView.setText(mSession.getMyUser().displayname);

        TextView userIdTextView = (TextView) mNavigationView.findViewById(R.id.home_menu_main_matrix_id);
        userIdTextView.setText(mSession.getMyUserId());

        ImageView mainAvatarView = (ImageView)mNavigationView.findViewById(R.id.home_menu_main_avatar);
        VectorUtils.loadUserAvatar(this, mSession, mainAvatarView, mSession.getMyUser());
    }
}
