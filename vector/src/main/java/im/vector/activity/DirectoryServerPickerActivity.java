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
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Filter;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PublicRoomsResponse;
import org.matrix.androidsdk.rest.model.ThirdPartyProtocol;
import org.matrix.androidsdk.rest.model.ThirdPartyProtocolInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.DirectoryServerAdapter;
import im.vector.util.DirectoryServerData;

public class DirectoryServerPickerActivity extends AppCompatActivity implements DirectoryServerAdapter.OnSelectDirectoryServerListener, SearchView.OnQueryTextListener {
    private static final String LOG_TAG = "DirServerPickerAct";

    public static final String EXTRA_SESSION_ID = "EXTRA_SESSION_ID";
    public static final String EXTRA_OUT_DIRECTORY_SERVER_DATA = "EXTRA_OUT_DIRECTORY_SERVER_DATA";

    private MXSession mSession;
    private RecyclerView mDirectorySourceRecyclerView;
    private DirectoryServerAdapter mDirectoryServerAdapter;
    private SearchView mSearchView;
    private View mLoadingView;

     /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static Intent getIntent(final Context context, final String sessionId) {
        final Intent intent = new Intent(context, DirectoryServerPickerActivity.class);
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        return intent;
    }

    /*
    * *********************************************************************************************
    * Activity lifecycle
    * *********************************************************************************************
    */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_directory_server_picker);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowHomeEnabled(true);
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        final Intent intent = getIntent();
        String sessionId = intent.getStringExtra(EXTRA_SESSION_ID);

        if (null != sessionId) {
            mSession = Matrix.getInstance(this).getSession(sessionId);
        }

        // should never happen
        if (null == mSession) {
            this.finish();
            return;
        }

        initViews();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_directory_server_picker, menu);

        final MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            mSearchView = (SearchView) MenuItemCompat.getActionView(searchItem);
            mSearchView.setMaxWidth(Integer.MAX_VALUE);
            mSearchView.setSubmitButtonEnabled(false);
            mSearchView.setQueryHint(getString(R.string.search_hint));
            mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            mSearchView.setOnQueryTextListener(this);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSearchView != null) {
            mSearchView.setOnQueryTextListener(null);
        }
    }

    /**
     * Refresh the directory servers list.
     */
    private void refreshDirectoryServersList() {
        mLoadingView.setVisibility(View.VISIBLE);

        mSession.getEventsApiClient().getThirdPartyServerProtocols(new ApiCallback<Map<String, ThirdPartyProtocol>>() {
            private void onDone(List<DirectoryServerData> list) {
                mLoadingView.setVisibility(View.GONE);
                // all the connected network
                list.add(0, new DirectoryServerData(null, "matrix.org", null, null, true));

                if (!list.isEmpty()) {
                    list.add(1, new DirectoryServerData(null, "Matrix", null, null, false));
                }

                // if the user uses his own home server
                if (!mSession.getMyUserId().endsWith(":matrix.org")) {
                    String server = mSession.getMyUserId().substring(mSession.getMyUserId().indexOf(":") + 1);
                    list.add(new DirectoryServerData(server, server, null, null, false));
                }

                mDirectoryServerAdapter.updateDirectoryServersList(list);
            }

            @Override
            public void onSuccess(Map<String, ThirdPartyProtocol> protocols) {
                List<DirectoryServerData> list = new ArrayList<>();

                for (String key : protocols.keySet()) {
                    ThirdPartyProtocol protocol = protocols.get(key);

                    for (ThirdPartyProtocolInstance instance : protocol.instances) {
                        list.add(new DirectoryServerData(null, instance.desc, instance.icon, instance.instanceId, false));
                    }
                }

                onDone(list);
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## refreshDirectoryServersList() : " + e.getMessage());
                onDone(new ArrayList<DirectoryServerData>());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## onMatrixError() : " + e.getMessage());
                onDone(new ArrayList<DirectoryServerData>());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## onUnexpectedError() : " + e.getMessage());
                onDone(new ArrayList<DirectoryServerData>());
            }
        });
    }

    /*
    * *********************************************************************************************
    * UI
    * *********************************************************************************************
    */

    private void initViews() {
        mDirectorySourceRecyclerView = (RecyclerView) findViewById(R.id.directory_server_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mDirectorySourceRecyclerView.setLayoutManager(layoutManager);
        mDirectoryServerAdapter = new DirectoryServerAdapter(new ArrayList<DirectoryServerData>(), this);
        mDirectorySourceRecyclerView.setAdapter(mDirectoryServerAdapter);

        mLoadingView = findViewById(R.id.directory_server_loading);
        refreshDirectoryServersList();
    }

    private void filterServersList(final String pattern) {
        mDirectoryServerAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
            }
        });
    }

    /*
    * *********************************************************************************************
    * Listener
    * *********************************************************************************************
    */

    @Override
    public void onSelectDirectoryServer(final DirectoryServerData directoryServerData) {
        // test if the server exists
        if (!TextUtils.isEmpty(mSearchView.getQuery()) && TextUtils.equals(directoryServerData.getServerUrl(), mSearchView.getQuery())) {
            mLoadingView.setVisibility(View.VISIBLE);

            mSession.getEventsApiClient().getPublicRoomsCount(directoryServerData.getServerUrl(), new ApiCallback<Integer>() {
                        @Override
                        public void onSuccess(Integer count) {
                            Intent intent = new Intent();
                            intent.putExtra(EXTRA_OUT_DIRECTORY_SERVER_DATA, directoryServerData);
                            setResult(RESULT_OK, intent);
                            finish();
                        }

                        private void onError(String error) {
                            Log.e(LOG_TAG, "## onSelectDirectoryServer() failed " + error);
                            mLoadingView.setVisibility(View.GONE);
                            Toast.makeText(DirectoryServerPickerActivity.this, R.string.select_directory_fail_to_retrieve_server, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            onError(e.getMessage());
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            onError(e.getMessage());
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            onError(e.getMessage());
                        }
                    });
        } else {
            Intent intent = new Intent();
            intent.putExtra(EXTRA_OUT_DIRECTORY_SERVER_DATA, directoryServerData);
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        filterServersList(newText);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return true;
    }

}