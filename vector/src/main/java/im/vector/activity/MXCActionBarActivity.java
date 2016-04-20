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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;

import im.vector.MyPresenceManager;
import im.vector.VectorApp;
import im.vector.Matrix;
import im.vector.R;

import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * extends ActionBarActivity to manage the rageshake
 */
public class MXCActionBarActivity extends ActionBarActivity {
    public static final String TAG_FRAGMENT_ACCOUNT_SELECTION_DIALOG = "ActionBarActivity.TAG_FRAGMENT_ACCOUNT_SELECTION_DIALOG";
    public static final String EXTRA_MATRIX_ID = "MXCActionBarActivity.EXTRA_MATRIX_ID";

    protected MXSession mSession = null;
    protected Room mRoom = null;

    private boolean hasCorruptedStore(Activity activity) {
        boolean hasCorruptedStore = false;
        ArrayList<MXSession> sessions = Matrix.getMXSessions(activity);

        if (null != sessions) {
            for (MXSession session : sessions) {
                if (session.isAlive()) {
                    hasCorruptedStore |= session.getDataHandler().getStore().isCorrupted();
                }
            }
        }
        return hasCorruptedStore;
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (hasCorruptedStore(this)) {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    CommonActivityUtils.logout(MXCActionBarActivity.this);
                }
            });
        }
    }
    
    /**
     * Return the used MXSession from an intent.
     * @param intent
     * @return the MXsession if it exists.
     */
    protected MXSession getSession(Intent intent) {
        String matrixId = null;

        if (intent.hasExtra(EXTRA_MATRIX_ID)) {
            matrixId = intent.getStringExtra(EXTRA_MATRIX_ID);
        }

        return Matrix.getInstance(getApplicationContext()).getSession(matrixId);
    }

    public MXSession getSession() {
        return mSession;
    }

    public Room getRoom() {
        return mRoom;
    }

    // add left sliding menu
    protected DrawerLayout mDrawerLayout;
    protected ListView mDrawerList;
    protected ActionBarDrawerToggle mDrawerToggle;

    protected int mSelectedSlidingMenuIndex = -1;

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);

        // create a "lollipop like " animation
        // not sure it is the save animation curve
        // appcompat does not support (it does nothing)
        //
        // ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(...
        // ActivityCompat.startActivity(activity, new Intent(activity, DetailActivity.class),  options.toBundle());

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            this.overridePendingTransition(R.anim.anim_slide_in_bottom, R.anim.anim_slide_nothing);
        } else {
            // the animation is enabled in the theme
        }
    }

    @Override
    public void finish() {
        super.finish();

        // create a "lollipop like " animation
        // not sure it is the save animation curve
        //
        // ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(...
        // ActivityCompat.startActivity(activity, new Intent(activity, DetailActivity.class),  options.toBundle());
       if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            this.overridePendingTransition(R.anim.anim_slide_nothing, R.anim.anim_slide_out_bottom);
        } else {
            // the animation is enabled in the theme
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_MENU) && (null == getSupportActionBar())) {
            // This is to fix a bug in the v7 support lib. If there is no options menu and you hit MENU, it will crash with a
            // NPE @ android.support.v7.app.ActionBarImplICS.getThemedContext(ActionBarImplICS.java:274)
            // This can safely be removed if we add in menu options on this screen
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        VectorApp.setCurrentActivity(null);
        Matrix.removeSessionErrorListener(this);

        // close any opened dialog
        FragmentManager fm = getSupportFragmentManager();
        java.util.List<android.support.v4.app.Fragment> fragments = fm.getFragments();

        if (null != fragments) {
            for (Fragment fragment : fragments) {
                if (fragment instanceof DialogFragment) {
                    ((DialogFragment) fragment).dismissAllowingStateLoss();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        VectorApp.getInstance().getOnActivityDestroyedListener().fire(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        VectorApp.setCurrentActivity(this);
        Matrix.setSessionErrorListener(this);

        // the online presence must be displayed ASAP.
        if ((null != Matrix.getInstance(this)) && (null != Matrix.getInstance(this).getSessions())) {
            MyPresenceManager.createPresenceManager(this, Matrix.getInstance(this).getSessions());
            MyPresenceManager.advertiseAllOnline();
        }
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if ((null != mDrawerToggle) && mDrawerToggle.onOptionsItemSelected(item)) {
            dismissKeyboard(this);
            return true;
        }

        if (item.getItemId() == android.R.id.home) {
            // pop the activity to avoid creating a new instance of the parent activity
            this.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        // display the menu icon with the text
        if (((featureId == Window.FEATURE_ACTION_BAR) || ((featureId == Window.FEATURE_OPTIONS_PANEL))) && menu != null){
            if(menu.getClass().getSimpleName().equals("MenuBuilder")){
                try{
                    Method m = menu.getClass().getDeclaredMethod(
                            "setOptionalIconsVisible", Boolean.TYPE);
                    m.setAccessible(true);
                    m.invoke(menu, true);
                }
                catch(NoSuchMethodException e){
                    //Log.e(TAG, "onMenuOpened", e);
                }
                catch(Exception e){
                    throw new RuntimeException(e);
                }
            }
        }
        return super.onMenuOpened(featureId, menu);
    }

    /**
     * Dismiss the soft keyboard if one view in the activity has the focus.
     * @param activity the activity
     */
    public static void dismissKeyboard(Activity activity) {
        // hide the soft keyboard
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager inputManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (null != mDrawerToggle) {
            // Sync the toggle state after onRestoreInstanceState has occurred.
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (null != mDrawerToggle) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    /**
     * Run the dedicated sliding menu action
     * @param position selected menu entry
     */
    protected void selectDrawItem(int position) {
    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView parent, View view, int position, long id) {
            // wait that the activity sliding animation is done
            // before performing the dedicated task
            // 1- because it triggers weird UI effect
            // 2- the application used to crash when logging out because adapter was refreshed with a null MXSession.
            mSelectedSlidingMenuIndex = position;
            mDrawerLayout.closeDrawer(mDrawerList);

            // the drawer button does not replace the home button.
            // trigger the
            if (null == mDrawerToggle) {
                mDrawerLayout.postDelayed(new Runnable() {
                                              @Override
                                              public void run() {
                                                  selectDrawItem(mSelectedSlidingMenuIndex);
                                                  mSelectedSlidingMenuIndex = -1;
                                              }
                                          }, 300);
            }
        }
    }
}
