/*
 * Copyright 2016 OpenMarket Ltd
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

package im.vector.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.MXCActionBarActivity;
import im.vector.activity.VectorMemberDetailsActivity;
import im.vector.activity.VectorRoomInviteMembersActivity;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorRoomDetailsMembersAdapter;
import im.vector.util.ThemeUtils;
import im.vector.util.VectorUtils;

public class VectorRoomDetailsMembersFragment extends Fragment {
    private static final String LOG_TAG = VectorRoomDetailsMembersFragment.class.getSimpleName();

    // activity request codes
    private static final int GET_MENTION_REQUEST_CODE = 666;
    private static final int INVITE_USER_REQUEST_CODE = 777;

    private static final boolean REFRESH_FORCED = true;
    private static final boolean REFRESH_NOT_FORCED = false;

    // class members
    private MXSession mSession;
    private Room mRoom;

    // fragment items
    private View mProgressView;
    private VectorRoomDetailsMembersAdapter mAdapter;
    private ExpandableListView mParticipantsListView;
    private HashMap<Integer, Boolean> mIsListViewGroupExpandedMap;

    private boolean mIsMultiSelectionMode;
    private MenuItem mRemoveMembersMenuItem;
    private MenuItem mSwitchDeletionMenuItem;

    // the UI handler to refresh the
    private Handler mUIHandler;

    // the member presences trigger refresh only after a delay
    // to avoid lags
    private Timer mRefreshTimer;
    private TimerTask mRefreshTimerTask;

    // list the up to date presence to avoid refreshing it twice
    private final List<String> mUpdatedPresenceUserIds = new ArrayList<>();

    // avoid dismissing the loading wheel when some new members are added
    private boolean mIsInvitingNewMembers;

    // global events listener
    private final MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String eventType = event.getType();

                    if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType) ||
                            Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(eventType) ||
                            Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS.equals(eventType)) {
                        refreshRoomMembersList(mPatternValue, REFRESH_FORCED);
                    }
                }
            });
        }

        @Override
        public void onRoomFlush(String roomId) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshRoomMembersList(mPatternValue, REFRESH_FORCED);
                }
            });
        }

        @Override
        public void onPresenceUpdate(final Event event, final User user) {
            if (null != getActivity()) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // test if the user is a member of the room
                        if (mAdapter.getUserIdsList().indexOf(user.user_id) >= 0) {
                            delayedUpdateRoomMembersDataModel();
                        }
                    }
                });
            }
        }
    };

    //  search result listener
    private final VectorRoomDetailsMembersAdapter.OnRoomMembersSearchListener mSearchListener = new VectorRoomDetailsMembersAdapter.OnRoomMembersSearchListener() {
        @Override
        public void onSearchEnd(final int aSearchCountResult, final boolean aIsSearchPerformed) {
            mParticipantsListView.post(new Runnable() {
                @Override
                public void run() {
                    // stop waiting wheel
                    if (!mIsInvitingNewMembers) {
                        mProgressView.setVisibility(View.GONE);
                    }

                    if (0 == aSearchCountResult) {
                        // no results found!
                        mSearchNoResultTextView.setVisibility(View.VISIBLE);
                    } else {
                        mSearchNoResultTextView.setVisibility(View.GONE);
                    }

                    if (TextUtils.isEmpty(mPatternValue)) {
                        // search result with no pattern filter
                        updateListExpandingState();
                    } else {
                        // search result
                        forceListInExpandingState();
                        mClearSearchImageView.setVisibility(View.VISIBLE); // restore state from inter switch tab
                    }

                    mParticipantsListView.post(new Runnable() {
                        @Override
                        public void run() {
                            // jump to the first item
                            mParticipantsListView.setSelection(0);
                        }
                    });
                }
            });
        }
    };

    // search text listener
    private final TextWatcher mTextWatcherListener = new TextWatcher() {
        @Override
        public void afterTextChanged(android.text.Editable s) {
            final String patternValue = mPatternToSearchEditText.getText().toString();

            if (TextUtils.isEmpty(patternValue)) {
                // search input is empty: restore a not filtered room members list
                mClearSearchImageView.setVisibility(View.INVISIBLE);
                mPatternValue = null;
                refreshRoomMembersList(mPatternValue, REFRESH_NOT_FORCED);
            } else {
                Timer timer = new Timer();
                // wait a little delay before refreshing the results.
                // it avoid UI lags when the user is typing.
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        String text = null;

                        // reported by GA
                        try {
                            text = mPatternToSearchEditText.getText().toString();
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## afterTextChanged() failed " + e.getMessage());
                        }

                        if (TextUtils.equals(text, patternValue) && (null != getActivity())) {
                            mPatternValue = text;

                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    refreshRoomMembersList(mPatternValue, REFRESH_NOT_FORCED);
                                }
                            });
                        }
                    }
                }, 100);

                mClearSearchImageView.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }
    };

    // matrix SDK actions callback
    private final ApiCallback<Void> mDefaultCallBack = new ApiCallback<Void>() {
        @Override
        public void onSuccess(Void info) {
            if (null != getActivity()) {
                getActivity().runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                mProgressView.setVisibility(View.GONE);
                            }
                        });
            }
        }

        public void onError(final String errorMessage) {
            if (null != getActivity()) {
                getActivity().runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                mProgressView.setVisibility(View.GONE);
                                Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_SHORT).show();
                            }
                        }
                );
            }
        }

        @Override
        public void onNetworkError(Exception e) {
            onError(e.getLocalizedMessage());
        }

        @Override
        public void onMatrixError(MatrixError e) {
            onError(e.getLocalizedMessage());
        }

        @Override
        public void onUnexpectedError(Exception e) {
            onError(e.getLocalizedMessage());
        }
    };


    // top view
    private View mViewHierarchy;
    private EditText mPatternToSearchEditText;
    private TextView mSearchNoResultTextView;
    private ImageView mClearSearchImageView;
    private String mPatternValue;
    private View mAddMembersFloatingActionButton;

    // create an instance of the fragment
    public static VectorRoomDetailsMembersFragment newInstance() {
        return new VectorRoomDetailsMembersFragment();
    }

    @SuppressLint("LongLogTag")
    @Override
    public void onPause() {
        super.onPause();
        Log.d("RoomDetailsMembersFragment", "## onPause()");

        if (null != mPatternToSearchEditText)
            mPatternToSearchEditText.removeTextChangedListener(mTextWatcherListener);

        // sanity check
        if (null != mRoom) {
            mRoom.removeEventListener(mEventListener);
        }

        if (mIsMultiSelectionMode) {
            toggleMultiSelectionMode();
        }

        if (null != mRefreshTimer) {
            mRefreshTimer.cancel();
            mRefreshTimer = null;
            mRefreshTimerTask = null;
        }
    }

    @Override
    public void onStop() {
        // sanity check, reported by GA
        if (null != getActivity()) {
            InputMethodManager inputMgr = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if ((null != inputMgr) && (null != mPatternToSearchEditText)) {
                inputMgr.hideSoftInputFromWindow(mPatternToSearchEditText.getApplicationWindowToken(), 0);
                mPatternToSearchEditText.clearFocus();
            }
        }

        super.onStop();
    }

    @SuppressLint("LongLogTag")
    @Override
    public void onResume() {
        super.onResume();
        Log.d("RoomDetailsMembersFragment", "## onResume()");

        // disable/enable search action according to search pattern
        if (null != mPatternToSearchEditText)
            mPatternToSearchEditText.addTextChangedListener(mTextWatcherListener);

        // sanity check
        if (null != mRoom) {
            mRoom.addEventListener(mEventListener);
        }

        // sanity check
        refreshRoomMembersList(mPatternValue, REFRESH_NOT_FORCED);

        // restore group expanding states
        updateListExpandingState();

        refreshMenuEntries();

        refreshMemberPresences();
    }

    @SuppressLint("LongLogTag")
    @Override
    public void onSaveInstanceState(Bundle aOutState) {
        super.onSaveInstanceState(aOutState);
        aOutState.putSerializable(CommonActivityUtils.KEY_GROUPS_EXPANDED_STATE, mIsListViewGroupExpandedMap);
        aOutState.putString(CommonActivityUtils.KEY_SEARCH_PATTERN, mPatternValue);
        Log.d("RoomDetailsMembersFragment", "## onSaveInstanceState()");
    }

    private void updateListExpandingState() {
        if (null != mParticipantsListView) {
            mParticipantsListView.post(new Runnable() {
                @Override
                public void run() {
                    int groupCount = mParticipantsListView.getExpandableListAdapter().getGroupCount();
                    Boolean isExpanded = CommonActivityUtils.GROUP_IS_EXPANDED;

                    for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {

                        if (null != mIsListViewGroupExpandedMap) {
                            isExpanded = mIsListViewGroupExpandedMap.get(groupIndex);
                        }

                        if ((null == isExpanded) || (CommonActivityUtils.GROUP_IS_EXPANDED == isExpanded)) {
                            mParticipantsListView.expandGroup(groupIndex);
                        } else {
                            mParticipantsListView.collapseGroup(groupIndex);
                        }
                    }
                }
            });
        }
    }

    /**
     * Expand all list groups.
     */
    private void forceListInExpandingState() {
        if (null != mParticipantsListView) {
            mParticipantsListView.post(new Runnable() {
                @Override
                public void run() {
                    int groupCount = mParticipantsListView.getExpandableListAdapter().getGroupCount();

                    for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
                        mParticipantsListView.expandGroup(groupIndex);
                    }
                }
            });
        }
    }

    @SuppressLint("LongLogTag")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mViewHierarchy = inflater.inflate(R.layout.fragment_vector_add_participants, container, false);

        Activity activity = getActivity();

        if (activity instanceof MXCActionBarActivity) {
            MXCActionBarActivity anActivity = (MXCActionBarActivity) activity;
            mRoom = anActivity.getRoom();
            mSession = anActivity.getSession();

            // GA issue
            if ((null != mSession) && mSession.isAlive()) {
                finalizeInit();
            } else {
                Log.e(LOG_TAG, "## onCreateView : the session is null -> kill the activity");
                if (null != getActivity()) {
                    getActivity().finish();
                }
            }
        }

        // life cycle management
        if (null == savedInstanceState) {
            if (null == mIsListViewGroupExpandedMap) {
                // inter tab switch keeps instance values, since fragment is not destroyed
                mIsListViewGroupExpandedMap = new HashMap<>();
            }
        } else {
            mIsListViewGroupExpandedMap = (HashMap<Integer, Boolean>) savedInstanceState.getSerializable(CommonActivityUtils.KEY_GROUPS_EXPANDED_STATE);
            mPatternValue = savedInstanceState.getString(CommonActivityUtils.KEY_SEARCH_PATTERN, null);
        }

        setHasOptionsMenu(true);

        mUIHandler = new Handler(Looper.getMainLooper());

        return mViewHierarchy;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // the application is in a weird state
        if (CommonActivityUtils.shouldRestartApp(getActivity())) {
            return;
        }

        // Inflate the menu; this adds items to the action bar if it is present.
        getActivity().getMenuInflater().inflate(R.menu.vector_room_details_add_people, menu);
        CommonActivityUtils.tintMenuIcons(menu, ThemeUtils.getColor(getContext(), R.attr.icon_tint_on_dark_action_bar_color));

        mRemoveMembersMenuItem = menu.findItem(R.id.ic_action_room_details_delete);
        mSwitchDeletionMenuItem = menu.findItem(R.id.ic_action_room_details_edition_mode);

        // after data model was refreshed, update the edit icon
        processEditionMode();

        refreshMenuEntries();
    }

    /**
     * Trap the back key event.
     *
     * @return true if the back key event is trapped.
     */
    public boolean onBackPressed() {
        if (mIsMultiSelectionMode) {
            toggleMultiSelectionMode();
            return true;
        }

        return false;
    }

    /**
     * Compute if the current user is admin of the room.
     *
     * @return true if user is admin, false otherwise
     */
    private boolean isUserAdmin() {
        boolean isAdmin = false;

        if ((null != mRoom) && (null != mSession)) {
            PowerLevels powerLevels;

            if (null != (powerLevels = mRoom.getLiveState().getPowerLevels())) {
                String userId = mSession.getMyUserId();
                isAdmin = (null != userId) && (powerLevels.getUserPowerLevel(userId) >= CommonActivityUtils.UTILS_POWER_LEVEL_ADMIN);
            }
        }
        return isAdmin;
    }

    /**
     * Determine if the edit icon must be displayed or not.
     * The edit icon must be hidden in the following cases:
     * <ul>
     * <li>the user is not admin </li>
     * <li>only one member is present in the room</li>
     * </ul>
     */
    private void processEditionMode() {
        boolean isEnabled;

        if (null != mSwitchDeletionMenuItem) {
            if (!isUserAdmin()) {
                isEnabled = false;
            } else isEnabled = 1 != mAdapter.getItemsCount();

            mSwitchDeletionMenuItem.setVisible(isEnabled);
            mSwitchDeletionMenuItem.setEnabled(isEnabled);
        }
    }


    /**
     * Trigger an UI refresh but it is only triggered after a delay
     * to avoid lags.
     */
    private void delayedUpdateRoomMembersDataModel() {
        if (null != mRefreshTimer) {
            mRefreshTimer.cancel();
            mRefreshTimer = null;
            mRefreshTimerTask = null;
        }

        try {
            mRefreshTimer = new Timer();
            mRefreshTimerTask = new TimerTask() {
                public void run() {
                    mUIHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (null != mRefreshTimer) {
                                mRefreshTimer.cancel();
                            }
                            mRefreshTimer = null;
                            mRefreshTimerTask = null;
                            mAdapter.updateRoomMembersDataModel(null);
                        }
                    });
                }
            };

            mRefreshTimer.schedule(mRefreshTimerTask, 1000);
        } catch (Throwable throwable) {
            Log.e(LOG_TAG, "## delayedUpdateRoomMembersDataModel() failed " + throwable.getMessage());

            if (null != mRefreshTimer) {
                mRefreshTimer.cancel();
                mRefreshTimer = null;
            }

            mRefreshTimerTask = null;
            mAdapter.updateRoomMembersDataModel(null);
        }
    }

    /**
     * Refresh the member presences of the displayed members
     * if they are not yet known.
     * It might happen if the client did not receive any presence from this user
     * because they did not change since the application launch.
     */
    private void refreshMemberPresences() {
        int firstPos = mParticipantsListView.getFirstVisiblePosition();
        int lastPos = mParticipantsListView.getLastVisiblePosition() + 20; // add a margin to refresh more
        int count = mParticipantsListView.getCount();

        for (int i = firstPos; (i <= lastPos) && (i < count); i++) {
            Object item = mParticipantsListView.getItemAtPosition(i);

            if (item instanceof ParticipantAdapterItem) {
                ParticipantAdapterItem participantAdapterItem = (ParticipantAdapterItem) item;

                // test if a request has been done
                if (mUpdatedPresenceUserIds.indexOf(participantAdapterItem.mUserId) < 0) {
                    mUpdatedPresenceUserIds.add(participantAdapterItem.mUserId);

                    VectorUtils.getUserOnlineStatus(getActivity(), mSession, participantAdapterItem.mUserId, new SimpleApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            mUIHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    delayedUpdateRoomMembersDataModel();
                                }
                            });
                        }
                    });

                }
            }
        }
    }

    /**
     * Refresh the menu entries according to the edition mode
     */
    private void refreshMenuEntries() {
        if (null != mRemoveMembersMenuItem) {
            mRemoveMembersMenuItem.setVisible(mIsMultiSelectionMode);

            // fix https://github.com/vector-im/vector-android/issues/196
            if ((null != mAddMembersFloatingActionButton)) {
                mAddMembersFloatingActionButton.setVisibility(mIsMultiSelectionMode ? View.GONE : View.VISIBLE);
            }
        }

        if ((null != mSwitchDeletionMenuItem) && (mSwitchDeletionMenuItem.isEnabled())) {
            mSwitchDeletionMenuItem.setVisible(!mIsMultiSelectionMode);
        }
    }

    /**
     * Update the activity title
     *
     * @param title the new title
     */
    private void setActivityTitle(String title) {
        if (null != ((AppCompatActivity) getActivity()).getSupportActionBar()) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(title);
        }
    }

    /**
     * Reset the activity title.
     */
    private void resetActivityTitle() {
        mRemoveMembersMenuItem.setEnabled(true);
        mSwitchDeletionMenuItem.setEnabled(true);

        setActivityTitle(this.getResources().getString(R.string.room_details_title));
    }

    /**
     * Enable / disable the multi selection mode
     */
    private void toggleMultiSelectionMode() {
        resetActivityTitle();
        mIsMultiSelectionMode = !mIsMultiSelectionMode;
        mAdapter.setMultiSelectionMode(mIsMultiSelectionMode);
        refreshMenuEntries();
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Kick an user Ids list
     *
     * @param userIds the user ids list
     * @param index   the start index
     */
    private void kickUsers(final List<String> userIds, final int index) {
        if (index >= userIds.size()) {
            // the kick requests are performed in a dedicated thread
            // so switch to the UI thread at the end.
            if (null != getActivity()) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgressView.setVisibility(View.GONE);

                        if (mIsMultiSelectionMode) {
                            toggleMultiSelectionMode();
                            resetActivityTitle();
                        }

                        // refresh the display
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }

            return;
        }

        mRemoveMembersMenuItem.setEnabled(false);
        mSwitchDeletionMenuItem.setEnabled(false);

        mProgressView.setVisibility(View.VISIBLE);

        mRoom.kick(userIds.get(index), new ApiCallback<Void>() {
                    private void kickNext() {
                        kickUsers(userIds, index + 1);
                    }

                    @Override
                    public void onSuccess(Void info) {
                        kickNext();
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        kickNext();
                    }

                    @Override
                    public void onMatrixError(final MatrixError e) {
                        kickNext();
                        if (null != getActivity()) {
                            Toast.makeText(getActivity(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        kickNext();
                    }
                }

        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.ic_action_room_details_delete) {
            kickUsers(mAdapter.getSelectedUserIds(), 0);
        } else if (id == R.id.ic_action_room_details_edition_mode) {
            toggleMultiSelectionMode();
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Finalize the fragment initialization.
     */
    private void finalizeInit() {
        MXMediasCache mxMediasCache = mSession.getMediasCache();

        mAddMembersFloatingActionButton = mViewHierarchy.findViewById(R.id.add_participants_create_view);

        mAddMembersFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // pop to the home activity
                Intent intent = new Intent(getActivity(), VectorRoomInviteMembersActivity.class);
                intent.putExtra(VectorRoomInviteMembersActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                intent.putExtra(VectorRoomInviteMembersActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
                intent.putExtra(VectorRoomInviteMembersActivity.EXTRA_ADD_CONFIRMATION_DIALOG, true);
                getActivity().startActivityForResult(intent, INVITE_USER_REQUEST_CODE);
            }
        });

        // search room members management
        mPatternToSearchEditText = mViewHierarchy.findViewById(R.id.search_value_edit_text);
        mClearSearchImageView = mViewHierarchy.findViewById(R.id.clear_search_icon_image_view);
        mSearchNoResultTextView = mViewHierarchy.findViewById(R.id.search_no_results_text_view);

        // add IME search action handler
        mPatternToSearchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((actionId == EditorInfo.IME_ACTION_SEARCH) || (actionId == EditorInfo.IME_ACTION_GO) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                    String previousPattern = mPatternValue;
                    mPatternValue = mPatternToSearchEditText.getText().toString();

                    if (TextUtils.isEmpty(mPatternValue.trim())) {
                        // Prevent empty patterns to be launched and restore previous valid pattern to properly manage inter tab switch
                        mPatternValue = previousPattern;
                    } else {
                        refreshRoomMembersList(mPatternValue, REFRESH_NOT_FORCED);
                    }
                    return true;
                }
                return false;
            }
        });

        mClearSearchImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // clear search pattern to restore no filtered room members list
                mPatternToSearchEditText.setText("");
                mPatternValue = null;
                refreshRoomMembersList(mPatternValue, REFRESH_NOT_FORCED);
                forceListInExpandingState();
            }
        });

        mProgressView = mViewHierarchy.findViewById(R.id.add_participants_progress_view);
        mParticipantsListView = mViewHierarchy.findViewById(R.id.room_details_members_exp_list_view);
        mAdapter = new VectorRoomDetailsMembersAdapter(getActivity(), R.layout.adapter_item_vector_add_participants, R.layout.adapter_item_vector_recent_header, mSession, mRoom.getRoomId(), mxMediasCache);
        mParticipantsListView.setAdapter(mAdapter);
        // the group indicator is managed in the adapter (group view creation)
        mParticipantsListView.setGroupIndicator(null);

        mParticipantsListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {

            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                refreshMemberPresences();
            }
        });

        // set all the listener handlers called from the adapter
        mAdapter.setOnParticipantsListener(new VectorRoomDetailsMembersAdapter.OnParticipantsListener() {
            @Override
            public void onClick(final ParticipantAdapterItem participantItem) {
                Intent memberDetailsIntent = new Intent(getActivity(), VectorMemberDetailsActivity.class);
                memberDetailsIntent.putExtra(VectorMemberDetailsActivity.EXTRA_ROOM_ID, mRoom.getRoomId());
                memberDetailsIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, participantItem.mUserId);
                memberDetailsIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                getActivity().startActivityForResult(memberDetailsIntent, GET_MENTION_REQUEST_CODE);
            }

            @Override
            public void onSelectUserId(String userId) {
                ArrayList<String> userIds = mAdapter.getSelectedUserIds();

                if (0 != userIds.size()) {
                    setActivityTitle(userIds.size() + " " + getActivity().getResources().getString(R.string.room_details_selected));
                } else {
                    resetActivityTitle();
                }
            }

            @Override
            public void onRemoveClick(final ParticipantAdapterItem participantItem) {
                String text = getActivity().getString(R.string.room_participants_remove_prompt_msg, participantItem.mDisplayName);

                // The user is trying to leave with unsaved changes. Warn about that
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.dialog_title_confirmation)
                        .setMessage(text)
                        .setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();

                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        kickUsers(Collections.singletonList(participantItem.mUserId), 0);
                                    }
                                });
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create()
                        .show();
            }

            @Override
            public void onLeaveClick() {
                // The user is trying to leave with unsaved changes. Warn about that
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.room_participants_leave_prompt_title)
                        .setMessage(getActivity().getString(R.string.room_participants_leave_prompt_msg))
                        .setPositiveButton(R.string.leave, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();

                                mProgressView.setVisibility(View.VISIBLE);

                                mRoom.leave(new ApiCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void info) {
                                        if (null != getActivity()) {
                                            getActivity().runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    getActivity().finish();
                                                }
                                            });
                                        }
                                    }

                                    private void onError(final String errorMessage) {
                                        if (null != getActivity()) {
                                            getActivity().runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mProgressView.setVisibility(View.GONE);
                                                    Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                        }
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        onError(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onMatrixError(MatrixError e) {
                                        onError(e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onUnexpectedError(Exception e) {
                                        onError(e.getLocalizedMessage());
                                    }
                                });

                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create()
                        .show();
            }

            @Override
            public void onGroupCollapsedNotif(int aGroupPosition) {
                if (null != mIsListViewGroupExpandedMap) {
                    mIsListViewGroupExpandedMap.put(aGroupPosition, CommonActivityUtils.GROUP_IS_COLLAPSED);
                }
            }

            @Override
            public void onGroupExpandedNotif(int aGroupPosition) {
                if (null != mIsListViewGroupExpandedMap) {
                    mIsListViewGroupExpandedMap.put(aGroupPosition, CommonActivityUtils.GROUP_IS_EXPANDED);
                }
            }
        });
    }


    /**
     * Perform a search request: the adapter is asked to filter the display according to
     * the search pattern.
     * The pattern to search is given in aSearchedPattern. To cancel the search, and display the
     * room members without any criteria, set aSearchedPattern to NO_PATTERN_FILTER.
     *
     * @param aSearchedPattern string to be searched
     * @param aIsRefreshForced true to force a refresh whatever the pattern value
     */
    @SuppressLint("LongLogTag")
    private void refreshRoomMembersList(final String aSearchedPattern, boolean aIsRefreshForced) {
        if (null != mAdapter) {
            // start waiting wheel during the search
            mProgressView.setVisibility(View.VISIBLE);
            mAdapter.setSearchedPattern(aSearchedPattern, mSearchListener, aIsRefreshForced);
        } else {
            Log.w(LOG_TAG, "## refreshRoomMembersList(): search failure - adapter not initialized");
        }

        // after data model was refreshed, update the edit icon
        processEditionMode();
    }

    /**
     * Invite an user Ids list.
     *
     * @param userIds the user IDs list
     */
    private void inviteUserIds(List<String> userIds) {
        mRoom.invite(userIds, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                mIsInvitingNewMembers = false;
                mDefaultCallBack.onSuccess(null);
            }

            @Override
            public void onNetworkError(Exception e) {
                mIsInvitingNewMembers = false;
                mDefaultCallBack.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mIsInvitingNewMembers = false;
                mDefaultCallBack.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mIsInvitingNewMembers = false;
                mDefaultCallBack.onUnexpectedError(e);
            }
        });
    }

    /**
     * Activity result
     *
     * @param requestCode the request code
     * @param resultCode  teh result code
     * @param data        the returned data
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == INVITE_USER_REQUEST_CODE) && (resultCode == Activity.RESULT_OK)) {
            final List<String> userIds = (List<String>) data.getSerializableExtra(VectorRoomInviteMembersActivity.EXTRA_OUT_SELECTED_USER_IDS);

            if ((null != userIds) && (userIds.size() > 0)) {
                inviteUserIds(userIds);
            }
        } else if ((requestCode == GET_MENTION_REQUEST_CODE) && (resultCode == Activity.RESULT_OK)) {
            final String mention = data.getStringExtra(VectorMemberDetailsActivity.RESULT_MENTION_ID);

            if (!TextUtils.isEmpty(mention) && (null != getActivity())) {
                // provide the mention name
                Intent intent = new Intent();
                intent.putExtra(VectorMemberDetailsActivity.RESULT_MENTION_ID, mention);
                getActivity().setResult(Activity.RESULT_OK, intent);
                getActivity().finish();
            }
        }
    }
}
