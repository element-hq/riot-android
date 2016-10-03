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
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.KeyEvent;

import com.liuguangqiang.swipeback.SwipeBackActivity;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;

import im.vector.MyPresenceManager;
import im.vector.VectorApp;
import im.vector.Matrix;

import java.util.ArrayList;

public class MXSwipeActivity extends SwipeBackActivity {
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
                    CommonActivityUtils.logout(MXSwipeActivity.this);
                }
            });
        }
    }
    
    /**
     * Return the used MXSession from an intent.
     * @param intent the intent
     * @return the MXSession if it exists.
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
        Matrix.setSessionErrorListener(this);

        // the online presence must be displayed ASAP.
        if ((null != Matrix.getInstance(this)) && (null != Matrix.getInstance(this).getSessions())) {
            MyPresenceManager.createPresenceManager(this, Matrix.getInstance(this).getSessions());
            MyPresenceManager.advertiseAllOnline();
        }
    }
}
