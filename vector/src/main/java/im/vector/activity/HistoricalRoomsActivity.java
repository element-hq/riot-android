/*
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

import android.app.SearchManager;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.AbsAdapter;
import im.vector.adapters.HomeRoomAdapter;
import im.vector.util.RoomUtils;
import im.vector.util.ThemeUtils;
import im.vector.view.EmptyViewItemDecoration;
import im.vector.view.SimpleDividerItemDecoration;

/**
 * Displays the historical rooms list
 */
public class HistoricalRoomsActivity extends RiotAppCompatActivity implements SearchView.OnQueryTextListener, HomeRoomAdapter.OnSelectRoomListener, AbsAdapter.MoreRoomActionListener, RoomUtils.HistoricalRoomActionListener {
    private static final String LOG_TAG = HistoricalRoomsActivity.class.getSimpleName();

    @BindView(R.id.search_view)
    SearchView mSearchView;

    @BindView(R.id.historical_recycler_view)
    RecyclerView mHistoricalRecyclerView;

    @BindView(R.id.historical_no_results)
    TextView mHistoricalPlaceHolder;

    @BindView(R.id.historical_toolbar)
    Toolbar mToolbar;

    @BindView(R.id.historical_waiting_view)
    View waitingView;

    // historical adapter
    private HomeRoomAdapter mHistoricalAdapter;

    // pending tasks
    private final List<AsyncTask> mSortingAsyncTasks = new ArrayList<>();

    // sessions
    private MXSession mSession;

    /*
     * *********************************************************************************************
     * Activity lifecycle
     * *********************************************************************************************
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // required to have the right translated title
        setTitle(R.string.title_activity_historical);
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

        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHistorical();
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
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {
        // Toolbar
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mSession = Matrix.getInstance(this).getDefaultSession();

        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);

        mHistoricalRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mHistoricalRecyclerView.setHasFixedSize(true);
        mHistoricalRecyclerView.setNestedScrollingEnabled(false);
        mHistoricalRecyclerView.addItemDecoration(new SimpleDividerItemDecoration(this, DividerItemDecoration.VERTICAL, margin));
        mHistoricalRecyclerView.addItemDecoration(new EmptyViewItemDecoration(this, DividerItemDecoration.VERTICAL, 40, 16, 14));

        mHistoricalAdapter = new HomeRoomAdapter(this, R.layout.adapter_item_room_view, this, null, this);
        mHistoricalRecyclerView.setAdapter(mHistoricalAdapter);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        // Remove unwanted left margin
        LinearLayout searchEditFrame = mSearchView.findViewById(R.id.search_edit_frame);
        if (searchEditFrame != null) {
            ViewGroup.MarginLayoutParams searchEditFrameParams = (ViewGroup.MarginLayoutParams) searchEditFrame.getLayoutParams();
            searchEditFrameParams.leftMargin = 0;
            searchEditFrame.setLayoutParams(searchEditFrameParams);
        }
        ImageView searchIcon = mSearchView.findViewById(R.id.search_mag_icon);
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
        mSearchView.setQueryHint(getString(R.string.historical_placeholder));

        SearchView.SearchAutoComplete searchAutoComplete = mSearchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);
        searchAutoComplete.setHintTextColor(ThemeUtils.getColor(this, R.attr.default_text_hint_color));
    }

    /*
     * *********************************************************************************************
     * historical management
     * *********************************************************************************************
     */

    private void refreshHistorical() {
        MXDataHandler dataHandler = mSession.getDataHandler();

        if (!dataHandler.areLeftRoomsSynced()) {
            mHistoricalAdapter.setRooms(new ArrayList<Room>());
            showWaitingView();
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
        } else {
            initHistoricalRoomsData();
        }
    }

    /**
     * Init history rooms data
     */
    private void initHistoricalRoomsData() {
        stopWaitingView();
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
                    try {
                        Collections.sort(historicalRooms, RoomUtils.getHistoricalRoomsComparator(mSession, false));
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## initHistoricalRoomsData() : sort failed " + e.getMessage());
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void args) {
                mHistoricalAdapter.setRooms(historicalRooms);
            }
        };

        try {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            mSortingAsyncTasks.add(task);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## initHistoricalRoomsData() failed " + e.getMessage());
            task.cancel(true);
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
        final String filter = newText;

        if (mSession.getDataHandler().areLeftRoomsSynced()) {
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
                    String currentFilter = queryText;

                    // display if the pattern matched
                    if (TextUtils.equals(currentFilter, filter)) {
                        mHistoricalAdapter.getFilter().filter(filter, new Filter.FilterListener() {
                            @Override
                            public void onFilterComplete(int count) {
                                mHistoricalRecyclerView.scrollToPosition(0);
                                mHistoricalPlaceHolder.setVisibility((0 != count) ? View.GONE : View.VISIBLE);
                            }
                        });
                    }
                }
            }, 500);
        }
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
     * @param errorMessage the localized error message
     */
    private void onRequestDone(final String errorMessage) {
        if (!this.isFinishing()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stopWaitingView();
                    if (!TextUtils.isEmpty(errorMessage)) {
                        Toast.makeText(HistoricalRoomsActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    @Override
    public void onSelectRoom(Room room, int position) {
        showWaitingView();
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
    public void onMoreActionClick(View itemView, Room room) {
        RoomUtils.displayHistoricalRoomMenu(this, mSession, room, itemView, this);
    }

    @Override
    public void onForgotRoom(Room room) {
        showWaitingView();

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
