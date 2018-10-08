/*
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

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.pid.ThirdPartyProtocol;
import org.matrix.androidsdk.rest.model.pid.ThirdPartyProtocolInstance;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.RoomDirectoryAdapter;
import im.vector.util.RoomDirectoryData;
import kotlin.Triple;

public class RoomDirectoryPickerActivity extends VectorAppCompatActivity implements RoomDirectoryAdapter.OnSelectRoomDirectoryListener {
    // LOG TAG
    private static final String LOG_TAG = RoomDirectoryPickerActivity.class.getSimpleName();

    private static final String EXTRA_SESSION_ID = "EXTRA_SESSION_ID";
    public static final String EXTRA_OUT_ROOM_DIRECTORY_DATA = "EXTRA_OUT_ROOM_DIRECTORY_DATA";

    private MXSession mSession;
    private RoomDirectoryAdapter mRoomDirectoryAdapter;

    /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static Intent getIntent(final Context context, final String sessionId) {
        final Intent intent = new Intent(context, RoomDirectoryPickerActivity.class);
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        return intent;
    }

    /*
     * *********************************************************************************************
     * Activity lifecycle
     * *********************************************************************************************
     */

    @NotNull
    @Override
    public Triple getOtherThemes() {
        return new Triple(R.style.DirectoryPickerTheme_Dark, R.style.DirectoryPickerTheme_Black, R.style.DirectoryPickerTheme_Status);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_room_directory_picker;
    }

    @Override
    public int getTitleRes() {
        return R.string.select_room_directory;
    }

    @Override
    public void initUiAndData() {
        setWaitingView(findViewById(R.id.room_directory_loading));

        Toolbar toolbar = findViewById(R.id.toolbar);
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
        if ((null == mSession) || !mSession.isAlive()) {
            finish();
            return;
        }

        initViews();
    }

    @Override
    public int getMenuRes() {
        return R.menu.menu_directory_server_picker;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add_custom_hs) {
            displayCustomDirectoryDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
     * Refresh the directory servers list.
     */
    private void refreshDirectoryServersList() {
        showWaitingView();

        mSession.getEventsApiClient().getThirdPartyServerProtocols(new ApiCallback<Map<String, ThirdPartyProtocol>>() {
            private void onDone(List<RoomDirectoryData> list) {
                hideWaitingView();
                String userHSName = mSession.getMyUserId().substring(mSession.getMyUserId().indexOf(":") + 1);

                List<String> hsNamesList = Arrays.asList(getResources().getStringArray(R.array.room_directory_servers));

                int insertionIndex = 0;

                // Add user's HS
                list.add(insertionIndex++, RoomDirectoryData.createIncludingAllNetworks(null, userHSName));

                // Add user's HS but for Matrix public rooms only
                if (!list.isEmpty()) {
                    list.add(insertionIndex++, RoomDirectoryData.getDefault());
                }

                // Add custom directory servers
                for (String hsName : hsNamesList) {
                    if (!TextUtils.equals(userHSName, hsName)) {
                        // Use the server name as a default display name
                        list.add(insertionIndex++, RoomDirectoryData.createIncludingAllNetworks(hsName, hsName));
                    }
                }

                mRoomDirectoryAdapter.updateDirectoryServersList(list);
            }

            @Override
            public void onSuccess(Map<String, ThirdPartyProtocol> protocols) {
                List<RoomDirectoryData> list = new ArrayList<>();

                for (String key : protocols.keySet()) {
                    ThirdPartyProtocol protocol = protocols.get(key);

                    for (ThirdPartyProtocolInstance instance : protocol.instances) {
                        list.add(new RoomDirectoryData(null, instance.desc, instance.icon, instance.instanceId, false));
                    }
                }

                onDone(list);
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## refreshDirectoryServersList() : " + e.getMessage(), e);
                onDone(new ArrayList<RoomDirectoryData>());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## onMatrixError() : " + e.getMessage());
                onDone(new ArrayList<RoomDirectoryData>());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## onUnexpectedError() : " + e.getMessage(), e);
                onDone(new ArrayList<RoomDirectoryData>());
            }
        });
    }

    /**
     * Display a dialog to enter a custom HS url.
     */
    private void displayCustomDirectoryDialog() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_directory_picker, null);
        alert.setView(dialogView);

        final EditText editText = dialogView.findViewById(R.id.directory_picker_edit_text);

        alert
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        final String serverUrl = editText.getText().toString().trim();

                        if (!TextUtils.isEmpty(serverUrl)) {
                            showWaitingView();
                            mSession.getEventsApiClient().getPublicRoomsCount(serverUrl, new ApiCallback<Integer>() {
                                @Override
                                public void onSuccess(Integer count) {
                                    Intent intent = new Intent();
                                    intent.putExtra(EXTRA_OUT_ROOM_DIRECTORY_DATA, new RoomDirectoryData(serverUrl, serverUrl, null, null, false));
                                    setResult(RESULT_OK, intent);
                                    finish();
                                }

                                private void onError(String error) {
                                    Log.e(LOG_TAG, "## onSelectDirectoryServer() failed " + error);
                                    hideWaitingView();
                                    Toast.makeText(RoomDirectoryPickerActivity.this, R.string.directory_server_fail_to_retrieve_server, Toast.LENGTH_LONG)
                                            .show();
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
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /*
     * *********************************************************************************************
     * UI
     * *********************************************************************************************
     */

    private void initViews() {
        RecyclerView roomDirectoryRecyclerView = findViewById(R.id.room_directory_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        roomDirectoryRecyclerView.setLayoutManager(layoutManager);
        mRoomDirectoryAdapter = new RoomDirectoryAdapter(new ArrayList<RoomDirectoryData>(), this);
        roomDirectoryRecyclerView.setAdapter(mRoomDirectoryAdapter);

        refreshDirectoryServersList();
    }

    /*
     * *********************************************************************************************
     * Listener
     * *********************************************************************************************
     */

    @Override
    public void onSelectRoomDirectory(final RoomDirectoryData directoryServerData) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_OUT_ROOM_DIRECTORY_DATA, directoryServerData);
        setResult(RESULT_OK, intent);
        finish();
    }
}
