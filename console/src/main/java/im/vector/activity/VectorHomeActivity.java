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

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.EventUtils;

import im.vector.Matrix;
import im.vector.MyPresenceManager;
import im.vector.R;
import im.vector.ViewedRoomTracker;
import im.vector.adapters.VectorRoomSummaryAdapter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * Displays the main screen of the app, with rooms the user has joined and the ability to create
 * new rooms.
 */
public class VectorHomeActivity extends MXCActionBarActivity implements VectorRoomSummaryAdapter.RoomEventListener {

    private static final String LOG_TAG = "VectorHomeActivity";

    public static final String EXTRA_JUMP_TO_ROOM_ID = "org.matrix.console.VectorHomeActivity.EXTRA_JUMP_TO_ROOM_ID";
    public static final String EXTRA_JUMP_MATRIX_ID = "org.matrix.console.VectorHomeActivity.EXTRA_JUMP_MATRIX_ID";
    public static final String EXTRA_ROOM_INTENT = "org.matrix.console.VectorHomeActivity.EXTRA_ROOM_INTENT";

    private boolean mIsPaused = false;

    private ExpandableListView mMyRoomList = null;
    // switch to a room activity
    private String mAutomaticallyOpenedRoomId = null;
    private String mAutomaticallyOpenedMatrixId = null;
    private Intent mOpenedRoomIntent = null;

    // set to true to force refresh when an events chunk has been processed.
    private boolean refreshOnChunkEnd = false;

    private HashMap<MXSession, MXEventListener> mListenersBySession = new HashMap<MXSession, MXEventListener>();
    private HashMap<MXSession, MXCallsManager.MXCallsManagerListener> mCallListenersBySession = new HashMap<MXSession, MXCallsManager.MXCallsManagerListener>();

    private VectorRoomSummaryAdapter mAdapter;
    private View mWaitingView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_home);

        mWaitingView = findViewById(R.id.listView_spinner_views);

        mSession = Matrix.getInstance(this).getDefaultSession();

        // get the ExpandableListView widget
        mMyRoomList = (ExpandableListView) findViewById(R.id.listView_myRooms);
        // the chevron is managed in the header view
        mMyRoomList.setGroupIndicator(null);
        // create the adapter
        mAdapter = new VectorRoomSummaryAdapter(this, mSession, R.layout.adapter_item_vector_recent_room, R.layout.adapter_item_vector_recent_header, this);

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

        mMyRoomList.setAdapter(mAdapter);

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

        // set MX listeners for each session
        for(MXSession session : sessions) {
            addSessionListener(session);
        }

        // Set rooms click listener:
        // - reset the unread count
        // - start the corresponding room activity
        mMyRoomList.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v,
                                        int groupPosition, int childPosition, long id) {
                RoomSummary roomSummary = mAdapter.getRoomSummaryAt(groupPosition, childPosition);
                MXSession session = Matrix.getInstance(VectorHomeActivity.this).getSession(roomSummary.getMatrixId());

                String roomId = roomSummary.getRoomId();
                Room room = session.getDataHandler().getRoom(roomId);
                // cannot join a leaving room
                if ((null == room) || room.isLeaving()) {
                    roomId = null;
                }

                // update the unread messages count
                if (mAdapter.resetUnreadCount(groupPosition, childPosition)) {
                    session.getDataHandler().getStore().flushSummary(roomSummary);
                }

                // launch corresponding room activity
                if (null != roomId){
                    CommonActivityUtils.goToRoomPage(session, roomId, VectorHomeActivity.this, null);
                }

                // click is handled
                return true;
            }
        });
    }

    /**
     * Add a MXEventListener to the session listeners.
     * @param session the sessions.
     */
    private void addSessionListener(final MXSession session) {
        removeSessionListener(session);

        MXEventListener listener = new MXEventListener() {
            private boolean mInitialSyncComplete = false;

            @Override
            public void onInitialSyncComplete() {
                Log.d(LOG_TAG,"## onInitialSyncComplete()");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mInitialSyncComplete = true;
                        mAdapter.notifyDataSetChanged();

                        mMyRoomList.post(new Runnable() {
                            @Override
                            public void run() {
                                // expand all
                                int groupCount = mMyRoomList.getExpandableListAdapter().getGroupCount();
                                for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
                                    mMyRoomList.expandGroup(groupIndex);
                                }
                            }
                        });
                    }
                });
            }

            @Override
            public void onLiveEventsChunkProcessed() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(LOG_TAG, "onLiveEventsChunkProcessed");
                        if (!mIsPaused && refreshOnChunkEnd) {
                            mAdapter.notifyDataSetChanged();
                        }

                        refreshOnChunkEnd = false;
                    }
                });
            }

            @Override
            public void onLiveEvent(final Event event, final RoomState roomState) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        // refresh the UI at the end of the next events chunk
                        refreshOnChunkEnd = ((event.roomId != null) && RoomSummary.isSupportedEvent(event)) ||
                                Event.EVENT_TYPE_TAGS.equals(event.type) ||
                                Event.EVENT_TYPE_REDACTION.equals(event.type) ||
                                Event.EVENT_TYPE_RECEIPT.equals(event.type) ||
                                Event.EVENT_TYPE_STATE_ROOM_AVATAR.equals(event.type);

                        // highlight notified messages
                        // the SDK only highlighted invitation messages
                        // it lets the application chooses the behaviour.
                        ViewedRoomTracker rTracker = ViewedRoomTracker.getInstance();
                        String viewedRoomId = rTracker.getViewedRoomId();
                        String fromMatrixId = rTracker.getMatrixId();
                        MXSession session = Matrix.getInstance(VectorHomeActivity.this).getDefaultSession();
                        String matrixId = session.getCredentials().userId;

                        // If we're not currently viewing this room or not sent by myself, increment the unread count
                        if ((!event.roomId.equals(viewedRoomId) || !matrixId.equals(fromMatrixId))  && !event.getSender().equals(matrixId)) {
                            RoomSummary summary = session.getDataHandler().getStore().getSummary(event.roomId);
                            if (null != summary) {
                                summary.setHighlighted(summary.isHighlighted() || EventUtils.shouldHighlight(session, event));
                            }
                        }
                    }
                });
            }

            @Override
            public void onReceiptEvent(String roomId, List<String> senderIds) {
                // refresh only if the current user read some messages (to update the unread messages counters)
                refreshOnChunkEnd = (senderIds.indexOf(session.getCredentials().userId) >= 0);
            }

            @Override
            public void onRoomTagEvent(String roomId) {
                refreshOnChunkEnd = true;
            }

            /**
             * These methods trigger an UI refresh asap because the user could have created / joined / left a room
             * but the server events echos are not yet received.
             *
             */
            private void onForceRefresh() {
                if (mInitialSyncComplete) {
                    VectorHomeActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }

            @Override
            public void onStoreReady() {
                onForceRefresh();
            }

            @Override
            public void onLeaveRoom(final String roomId) {
                onForceRefresh();
            }

            @Override
            public void onNewRoom(String roomId) {
                onForceRefresh();
            }

            @Override
            public void onJoinRoom(String roomId) {
                onForceRefresh();
            }
        };

        session.getDataHandler().addListener(listener);
        mListenersBySession.put(session, listener);
    }

    /**
     * Remove the MXEventListener to the session listeners.
     * @param session the sessions.
     */
    private void removeSessionListener(final MXSession session) {
        if (mListenersBySession.containsKey(session)) {
            session.getDataHandler().removeListener(mListenersBySession.get(session));
            mListenersBySession.remove(session);
        }

        if (mCallListenersBySession.containsKey(session)) {
            session.mCallsManager.removeListener(mCallListenersBySession.get(session));
            mCallListenersBySession.remove(session);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Collection<MXSession> sessions = Matrix.getInstance(this).getSessions();

        for(MXSession session : sessions) {
            removeSessionListener(session);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsPaused = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyPresenceManager.createPresenceManager(this, Matrix.getInstance(this).getSessions());
        MyPresenceManager.advertiseAllOnline();
        mIsPaused = false;

        // some unsent messages could have been added
        // it does not trigger any live event.
        // So, it is safer to sort the messages when debackgrounding
        //mAdapter.sortSummaries();
        mAdapter.notifyDataSetChanged();

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
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean retCode = true;

        switch(item.getItemId()) {
            case R.id.ic_action_global_settings:
                // launch the settings activity
                final Intent settingsIntent = new Intent(VectorHomeActivity.this, SettingsActivity.class);
                VectorHomeActivity.this.startActivity(settingsIntent);
                break;

            // search in rooms content
            case R.id.ic_action_search_room:
                // launch the "search in rooms" activity
                final Intent searchIntent = new Intent(VectorHomeActivity.this, VectorSearchesActivity.class);
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
    private void hideWaitingView() {
        mWaitingView.setVisibility(View.GONE);
    }

    public void onJoinRoom(MXSession session, String roomId) {
        CommonActivityUtils.goToRoomPage(session, roomId, VectorHomeActivity.this, null);
    }

    public void onRejectInvitation(MXSession session, String roomId) {
        Room room = session.getDataHandler().getRoom(roomId);

        if (null != room) {
            showWaitingView();

            room.leave(new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    hideWaitingView();
                }

                private void onError() {
                    // TODO display a message ?
                    hideWaitingView();
                }

                @Override
                public void onNetworkError(Exception e) {
                    onError();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onError();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onError();
                }
            });
        }
    }
}
