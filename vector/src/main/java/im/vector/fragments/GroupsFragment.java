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

package im.vector.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.MXPatterns;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.groups.GroupsManager;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.group.Group;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import im.vector.R;
import im.vector.activity.VectorGroupDetailsActivity;
import im.vector.adapters.AbsAdapter;
import im.vector.adapters.GroupAdapter;
import im.vector.ui.themes.ThemeUtils;
import im.vector.util.SystemUtilsKt;
import im.vector.view.EmptyViewItemDecoration;
import im.vector.view.SimpleDividerItemDecoration;

public class GroupsFragment extends AbsHomeFragment {
    private static final String LOG_TAG = GroupsFragment.class.getSimpleName();

    @BindView(R.id.recyclerview)
    RecyclerView mRecycler;

    // groups management
    private GroupAdapter mAdapter;
    private GroupsManager mGroupsManager;

    // rooms list
    private final List<Group> mJoinedGroups = new ArrayList<>();
    private final List<Group> mInvitedGroups = new ArrayList<>();

    // refresh when there is a group event
    private final MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onNewGroupInvitation(String groupId) {
            refreshGroups();
        }

        @Override
        public void onJoinGroup(String groupId) {
            refreshGroups();
        }

        @Override
        public void onLeaveGroup(String groupId) {
            refreshGroups();
        }
    };

    /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static GroupsFragment newInstance() {
        return new GroupsFragment();
    }

    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_groups;
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mGroupsManager = mSession.getGroupsManager();
        mPrimaryColor = ThemeUtils.INSTANCE.getColor(getActivity(), R.attr.vctr_tab_home);
        mSecondaryColor = ThemeUtils.INSTANCE.getColor(getActivity(), R.attr.vctr_tab_home_secondary);

        mFabColor = ContextCompat.getColor(getActivity(), R.color.tab_groups);
        mFabPressedColor = ContextCompat.getColor(getActivity(), R.color.tab_groups_secondary);

        initViews();

        mAdapter.onFilterDone(mCurrentFilter);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSession.getDataHandler().addListener(mEventListener);
        mRecycler.addOnScrollListener(mScrollListener);
        refreshGroupsAndProfiles();
    }

    @Override
    public void onPause() {
        super.onPause();
        mSession.getDataHandler().removeListener(mEventListener);
        mRecycler.removeOnScrollListener(mScrollListener);
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected List<Room> getRooms() {
        return new ArrayList<>();
    }

    @Override
    protected void onFilter(String pattern, final OnFilterListener listener) {
        mAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                Log.i(LOG_TAG, "onFilterComplete " + count);
                if (listener != null) {
                    listener.onFilterDone(count);
                }
            }
        });
    }

    @Override
    protected void onResetFilter() {
        mAdapter.getFilter().filter("", new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                Log.i(LOG_TAG, "onResetFilter " + count);
            }
        });
    }

    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {
        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);
        mRecycler.setLayoutManager(new LinearLayoutManager(getActivity(), RecyclerView.VERTICAL, false));
        mRecycler.addItemDecoration(new SimpleDividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, margin));
        mRecycler.addItemDecoration(new EmptyViewItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, 40, 16, 14));

        mAdapter = new GroupAdapter(getActivity(), new GroupAdapter.OnGroupSelectItemListener() {
            @Override
            public void onSelectItem(final Group group, final int position) {
                // display it
                Intent intent = new Intent(getActivity(), VectorGroupDetailsActivity.class);
                intent.putExtra(VectorGroupDetailsActivity.EXTRA_GROUP_ID, group.getGroupId());
                intent.putExtra(VectorGroupDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                startActivity(intent);
            }

            @Override
            public boolean onLongPressItem(Group item, int position) {
                SystemUtilsKt.copyToClipboard(getActivity(), item.getGroupId());
                return true;
            }
        }, new AbsAdapter.GroupInvitationListener() {
            @Override
            public void onJoinGroup(MXSession session, String groupId) {
                mActivity.showWaitingView();
                mGroupsManager.joinGroup(groupId, new ApiCallback<Void>() {

                    private void onDone(String errorMessage) {
                        if ((null != errorMessage) && (null != getActivity())) {
                            Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_SHORT).show();
                        }
                        mActivity.hideWaitingView();
                    }

                    @Override
                    public void onSuccess(Void info) {
                        onDone(null);
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        onDone(e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        onDone(e.getLocalizedMessage());
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        onDone(e.getLocalizedMessage());
                    }
                });
            }

            @Override
            public void onRejectInvitation(MXSession session, String groupId) {
                leaveOrReject(groupId);
            }
        }, new AbsAdapter.MoreGroupActionListener() {
            @Override
            public void onMoreActionClick(View itemView, Group group) {
                displayGroupPopupMenu(group, itemView);
            }
        });

        mRecycler.setAdapter(mAdapter);
    }

    /**
     * Refresh the groups list
     */
    private void refreshGroups() {
        mJoinedGroups.clear();
        mJoinedGroups.addAll(mGroupsManager.getJoinedGroups());
        mAdapter.setGroups(mJoinedGroups);

        mInvitedGroups.clear();
        mInvitedGroups.addAll(mGroupsManager.getInvitedGroups());
        mAdapter.setInvitedGroups(mInvitedGroups);
    }

    /**
     * refresh the groups list and their profiles.
     */
    private void refreshGroupsAndProfiles() {
        refreshGroups();
        mSession.getGroupsManager().refreshGroupProfiles(new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                if ((null != mActivity) && !mActivity.isFinishing()) {
                    mAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    /**
     * Leave or reject a group invitation.
     *
     * @param groupId the group Id
     */
    private void leaveOrReject(String groupId) {
        mActivity.showWaitingView();
        mGroupsManager.leaveGroup(groupId, new ApiCallback<Void>() {

            private void onDone(String errorMessage) {
                if ((null != errorMessage) && (null != getActivity())) {
                    Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_SHORT).show();
                }
                mActivity.hideWaitingView();
            }

            @Override
            public void onSuccess(Void info) {
                onDone(null);
            }

            @Override
            public void onNetworkError(Exception e) {
                onDone(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onDone(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onDone(e.getLocalizedMessage());
            }
        });
    }

    @SuppressLint("NewApi")
    private void displayGroupPopupMenu(final Group group, final View actionView) {
        final Context context = getActivity();
        final PopupMenu popup;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            popup = new PopupMenu(context, actionView, Gravity.END);
        } else {
            popup = new PopupMenu(context, actionView);
        }
        popup.getMenuInflater().inflate(R.menu.vector_home_group_settings, popup.getMenu());
        ThemeUtils.INSTANCE.tintMenuIcons(popup.getMenu(), ThemeUtils.INSTANCE.getColor(context, R.attr.vctr_settings_icon_tint_color));


        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.ic_action_select_remove_group: {
                        leaveOrReject(group.getGroupId());
                        break;
                    }
                }
                return false;
            }
        });


        // force to display the icon
        try {
            Field[] fields = popup.getClass().getDeclaredFields();
            for (Field field : fields) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(popup);
                    Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
                    Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                    setForceIcons.invoke(menuPopupHelper, true);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## displayGroupPopupMenu() : failed " + e.getMessage(), e);
        }

        popup.show();
    }

    @Override
    public boolean onFabClick() {
        View dialogView = getActivity().getLayoutInflater().inflate(R.layout.dialog_create_group, null);

        final EditText nameEditText = dialogView.findViewById(R.id.community_name_edit_text);
        final EditText idEditText = dialogView.findViewById(R.id.community_id_edit_text);
        final String hostName = mSession.getHomeServerConfig().getHomeserverUri().getHost();
        TextView hsNameView = dialogView.findViewById(R.id.community_hs_name_text_view);
        hsNameView.setText(hostName);

        AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setView(dialogView)
                .setCancelable(false)
                .setTitle(R.string.create_community)
                .setPositiveButton(R.string.create, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        String localPart = idEditText.getText().toString().trim();
                        String groupName = nameEditText.getText().toString().trim();

                        mActivity.showWaitingView();

                        mGroupsManager.createGroup(localPart, groupName, new ApiCallback<String>() {
                            private void onDone(String errorMessage) {
                                if (null != getActivity()) {
                                    if (null != errorMessage) {
                                        Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
                                    }

                                    mActivity.hideWaitingView();

                                    refreshGroups();
                                }
                            }

                            @Override
                            public void onSuccess(String groupId) {
                                onDone(null);
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                onDone(e.getLocalizedMessage());
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                onDone(e.getLocalizedMessage());
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                onDone(e.getLocalizedMessage());
                            }
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();

        final Button createButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);

        idEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                createButton.setEnabled(MXPatterns.isGroupId("+" + idEditText.getText().toString().trim() + ":" + hostName));
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        createButton.setEnabled(false);
        return true;
    }
}
