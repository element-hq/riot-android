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
import android.os.AsyncTask;
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
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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
import org.matrix.androidsdk.util.EventUtils;
import org.matrix.androidsdk.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import im.vector.ViewedRoomTracker;
import im.vector.adapters.HomeRoomAdapter;
import im.vector.fragments.AbsHomeFragment;
import im.vector.fragments.FavouritesFragment;
import im.vector.fragments.HomeFragment;
import im.vector.fragments.PeopleFragment;
import im.vector.fragments.RoomsFragment;
import im.vector.ga.GAHelper;
import im.vector.receiver.VectorUniversalLinkReceiver;
import im.vector.services.EventStreamService;
import im.vector.util.BugReporter;
import im.vector.util.RoomUtils;
import im.vector.util.VectorCallSoundManager;
import im.vector.util.VectorUtils;
import im.vector.view.EmptyViewItemDecoration;
import im.vector.view.SimpleDividerItemDecoration;
import im.vector.view.UnreadCounterBadgeView;
import im.vector.view.VectorPendingCallView;

/**
 * Displays the main screen of the app, with rooms the user has joined and the ability to create
 * new rooms.
 */
public class HistoricalRoomsActivity extends AppCompatActivity implements SearchView.OnQueryTextListener, HomeRoomAdapter.OnSelectRoomListener, RoomUtils.HistoricalRoomActionListener {

    private static final String LOG_TAG = HistoricalRoomsActivity.class.getSimpleName();
    @BindView(R.id.historical_recycler_view)
    RecyclerView mHistoricalRecyclerView;

    @BindView(R.id.historical_no_results)
    TextView mHistoricalPlaceHolder;

    @BindView(R.id.historical_toolbar)
    Toolbar mToolbar;

    @BindView(R.id.historical_sync_in_progress)
    View mSyncInProgressView;

    // rooms management
    private HomeRoomAdapter mHistoricalAdapter;

    private List<AsyncTask> mSortingAsyncTasks = new ArrayList<>();


    private MXSession mSession;

    /*
     * *********************************************************************************************
     * Activity lifecycle
     * *********************************************************************************************
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historical);
        ButterKnife.bind(this);

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        if (CommonActivityUtils.isGoingToSplash(this)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen");
            return;
        }

        // Toolbar
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mSession = Matrix.getInstance(this).getDefaultSession();

        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);

        mHistoricalRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mHistoricalRecyclerView.setHasFixedSize(true);
        mHistoricalRecyclerView.setNestedScrollingEnabled(false);

        mHistoricalAdapter = new HomeRoomAdapter(this, R.layout.adapter_item_room_view, this, null, null);

        mHistoricalRecyclerView.addItemDecoration(new SimpleDividerItemDecoration(this, DividerItemDecoration.VERTICAL, margin));
        mHistoricalRecyclerView.addItemDecoration(new EmptyViewItemDecoration(this, DividerItemDecoration.VERTICAL, 40, 16, 14));

        mHistoricalRecyclerView.setAdapter(mHistoricalAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        MXDataHandler dataHandler = mSession.getDataHandler();

        if (!dataHandler.areLeftRoomsSynced()) {
            mSyncInProgressView.setVisibility(View.VISIBLE);
            dataHandler.retrieveLeftRooms(new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            initHistoricalRoomsData();
                        }
                    });
                }

                @Override
                public void onNetworkError(Exception e) {
                    onNoHistoricalRooms();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onNoHistoricalRooms();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onNoHistoricalRooms();
                }
            });
        } else {
            initHistoricalRoomsData();
        }
    }

    private void onNoHistoricalRooms() {
        mSyncInProgressView.setVisibility(View.GONE);
        mHistoricalAdapter.setRooms(new ArrayList<Room>());
    }

    /**
     * Init history rooms data
     */
    private void initHistoricalRoomsData() {
        mSyncInProgressView.setVisibility(View.GONE);
        final List<Room> historicalRooms = new ArrayList<>(mSession.getDataHandler().getLeftRooms());
        for (Iterator<Room> iterator = historicalRooms.iterator(); iterator.hasNext(); ) {
            final Room room = iterator.next();
            if (room.isConferenceUserRoom()) {
                iterator.remove();
            }
        }

        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (!isCancelled()) {
                    Collections.sort(historicalRooms, RoomUtils.getHistoricalRoomsComparator(mSession, false));
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void args) {
                mHistoricalAdapter.setRooms(historicalRooms);
            }
        };
        mSortingAsyncTasks.add(task);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
    public void onStop() {
        super.onStop();

        // Cancel running async tasks to prevent memory leaks
        for (AsyncTask asyncTask : mSortingAsyncTasks) {
            asyncTask.cancel(true);
        }
    }

    /*
     * *********************************************************************************************
     * User action management
     * *********************************************************************************************
     */
    @Override
    public boolean onQueryTextChange(String newText) {
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return true;
    }

     /*
     * *********************************************************************************************
     * Listeners
     * *********************************************************************************************
     */

    /**
     * Handle the end of any request : hide loading wheel and display error message if there is any
     *
     * @param errorMessage
     */
    protected void onRequestDone(final String errorMessage) {
        if (!this.isFinishing()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
            //        mActivity.stopWaitingView();
                    if (!TextUtils.isEmpty(errorMessage)) {
                        Toast.makeText(HistoricalRoomsActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }


    @Override
    public void onSelectRoom(Room room, int position) {
        //mActivity.showWaitingView();
        CommonActivityUtils.previewRoom(this, mSession, room.getRoomId(), "", new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                onRequestDone(null);
            }

            @Override
            public void onNetworkError(Exception e) {
                onRequestDone(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onRequestDone(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onRequestDone(e.getLocalizedMessage());
            }
        });
    }

    @Override
    public void onLongClickRoom(View v, Room room, int position) {
        RoomUtils.displayHistoricalRoomMenu(this, mSession, room, v, this);
    }

    @Override
    public void onForgotRoom(Room room) {
        //mActivity.showWaitingView();
        room.forget(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                initHistoricalRoomsData();
            }

            @Override
            public void onNetworkError(Exception e) {
                onRequestDone(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onRequestDone(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onRequestDone(e.getLocalizedMessage());
            }
        });
    }
}
