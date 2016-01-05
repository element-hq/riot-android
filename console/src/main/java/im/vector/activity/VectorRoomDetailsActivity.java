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
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.DataSetObserver;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.util.ArrayList;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.adapters.AdapterUtils;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorAddParticipantsAdapter;
import im.vector.contacts.Contact;

public class VectorRoomDetailsActivity extends MXCActionBarActivity {
    private static final String LOG_TAG = "VectorAddActivity";

    // exclude the room ID
    public static final String EXTRA_ROOM_ID = "VectorRoomDetailsActivity.EXTRA_ROOM_ID";



    private MXSession mSession;
    private String mRoomId;
    private Room mRoom;
    private MXMediasCache mxMediasCache;

    // define the selection section
    private int mSelectedSection = -1;

    // indexed by mSelectedSection
    private RelativeLayout mFragmentsLayout;
    private LinearLayout mTabsLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_room_details);

        mFragmentsLayout = (RelativeLayout)findViewById(R.id.room_details_fragments);
        mTabsLayout = (LinearLayout)findViewById(R.id.selection_tabs);

        for(int index = 0; index < mTabsLayout.getChildCount(); index++) {
            final LinearLayout sublayout = (LinearLayout)mTabsLayout.getChildAt(index);

            sublayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onSelectedTab(mTabsLayout.indexOfChild(v));
                }
            });
        }

        // force to hide the fragments
        // else they are displayed even if they are hidden in the layout
        for(int index = 0; index < mFragmentsLayout.getChildCount(); index++) {
            mFragmentsLayout.getChildAt(index).setVisibility(View.GONE);
        }

        onSelectedTab(0);
    }

    /**
     * Toggle a tab.
     * @param index the toggled tab.
     * @param isSelected true if the tabs is selected.
     */
    private void toggleTab(int index, boolean isSelected) {
        if (index >= 0) {
            LinearLayout subLayout = (LinearLayout)mTabsLayout.getChildAt(index);

            TextView textView = (TextView)subLayout.getChildAt(0);
            textView.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);

            View underlineView = subLayout.getChildAt(1);
            underlineView.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void onSelectedTab(int index) {
        if (index != mSelectedSection) {

            // hide the previous one
            if (mSelectedSection >= 0) {
                toggleTab(mSelectedSection, false);
                mFragmentsLayout.getChildAt(mSelectedSection).setVisibility(View.GONE);
            }

            mSelectedSection = index;
            toggleTab(mSelectedSection, true);
            mFragmentsLayout.getChildAt(mSelectedSection).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}
