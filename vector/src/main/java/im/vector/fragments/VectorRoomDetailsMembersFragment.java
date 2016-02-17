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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import im.vector.VectorApp;
import im.vector.R;
import im.vector.activity.MXCActionBarActivity;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorAddParticipantsAdapter;

import java.util.ArrayList;

public class VectorRoomDetailsMembersFragment extends Fragment {
    private static final String LOG_TAG = "VectorRoomDetailsMembers";

    // class members
    private MXSession mSession;
    private Room mRoom;

    // fragment items
    private View mProgressView;
    private VectorAddParticipantsAdapter mAdapter;

    private boolean mIsMultiSelectionMode;
    private MenuItem mRemoveMembersItem;
    private MenuItem mSwitchDeletionItem;

    private final MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                        mAdapter.listOtherMembers();
                        mAdapter.refresh();
                    }
                }
            });
        }
    };

    // top view
    private View mViewHierarchy;

    public static VectorRoomDetailsMembersFragment newInstance() {
        return new VectorRoomDetailsMembersFragment();
    }

    @Override
    public void onPause() {
        super.onPause();

        // sanity check
        if (null != mRoom) {
            mRoom.removeEventListener(mEventListener);
        }

        if (mIsMultiSelectionMode) {
            toggleMultiSelectionMode();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // sanity check
        if (null != mRoom) {
            mRoom.addEventListener(mEventListener);
        }

        // sanity check
        if (null != mAdapter) {
            mAdapter.listOtherMembers();
            mAdapter.refresh();
        }

        refreshMenuEntries();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mViewHierarchy = inflater.inflate(R.layout.fragment_vector_add_participants, container, false);

        Activity activity = getActivity();

        if (activity instanceof MXCActionBarActivity) {
            MXCActionBarActivity anActivity = (MXCActionBarActivity) activity;
            mRoom = anActivity.getRoom();
            mSession = anActivity.getSession();

            finalizeInit();
        }

        setHasOptionsMenu(true);

        return mViewHierarchy;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getActivity().getMenuInflater().inflate(R.menu.vector_room_details_add_people, menu);

        mRemoveMembersItem = menu.findItem(R.id.ic_action_room_details_delete);
        mSwitchDeletionItem = menu.findItem(R.id.ic_action_room_details_edition_mode);

        refreshMenuEntries();
    }

    /**
     * Trap the back key event.
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
     * Refresh the menu entries according to the edition mode
     */
    private void refreshMenuEntries() {
        if (null != mRemoveMembersItem) {
            mRemoveMembersItem.setVisible(mIsMultiSelectionMode);
        }

        if (null != mSwitchDeletionItem) {
            mSwitchDeletionItem.setVisible(!mIsMultiSelectionMode);
        }
    }

    /**
     * Update the activity title
     *
     * @param title
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
        mRemoveMembersItem.setEnabled(true);
        mSwitchDeletionItem.setEnabled(true);

        setActivityTitle(this.getResources().getString(R.string.room_details_title));
    }

    /**
     * Enable / disable the multiselection mode
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
     * @param userids the user ids list
     * @param index the start index
     */
    private void kickUsers(final  ArrayList<String> userids, final int index) {
        if (index >= userids.size()) {
            mProgressView.setVisibility(View.GONE);

            if (mIsMultiSelectionMode) {
                toggleMultiSelectionMode();
                resetActivityTitle();
            }
            return;
        }

        mRemoveMembersItem.setEnabled(false);
        mSwitchDeletionItem.setEnabled(false);

        mProgressView.setVisibility(View.VISIBLE);

        mRoom.kick(userids.get(index), new ApiCallback<Void>() {
                    private void kickNext() {
                        kickUsers(userids, index + 1);
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
                    public void onMatrixError(MatrixError e) {
                        kickNext();
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
        } else if (id ==  R.id.ic_action_room_details_edition_mode) {
            toggleMultiSelectionMode();
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Finalize the fragment initialization.
     */
    private void finalizeInit() {
        MXMediasCache mxMediasCache = mSession.getMediasCache();

        mProgressView = mViewHierarchy.findViewById(R.id.add_participants_progress_view);
        ListView participantsListView = (ListView)mViewHierarchy.findViewById(R.id.room_details_members_list);
        mAdapter = new VectorAddParticipantsAdapter(getActivity(), R.layout.adapter_item_vector_add_participants, mSession, (null != mRoom) ? mRoom.getRoomId() : null, false, mxMediasCache);
        participantsListView.setAdapter(mAdapter);

        participantsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!TextUtils.isEmpty(mAdapter.getSearchedPattern())) {
                    ParticipantAdapterItem participant = mAdapter.getItem(position);

                    if (null != mRoom) {
                        final ArrayList<String> userIDs = new ArrayList<String>();
                        userIDs.add(participant.mUserId);

                        mProgressView.setVisibility(View.VISIBLE);

                        mRoom.invite(userIDs, new SimpleApiCallback<Void>(getActivity()) {
                            @Override
                            public void onSuccess(Void info) {
                                mProgressView.setVisibility(View.GONE);
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                mProgressView.setVisibility(View.GONE);
                            }

                            @Override
                            public void onMatrixError(final MatrixError e) {
                                mProgressView.setVisibility(View.GONE);
                            }

                            @Override
                            public void onUnexpectedError(final Exception e) {
                                mProgressView.setVisibility(View.GONE);
                            }
                        });
                    } else {
                        mAdapter.addParticipant(participant);
                    }
                }
            }
        });

        mAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
            }
        });

        mAdapter.setOnParticipantsListener(new VectorAddParticipantsAdapter.OnParticipantsListener() {

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
                if (null == mRoom) {
                    mAdapter.removeParticipant(participantItem);
                } else {
                    String text = getActivity().getString(R.string.room_participants_remove_prompt_msg, participantItem.mDisplayName);

                    // The user is trying to leave with unsaved changes. Warn about that
                    new AlertDialog.Builder(VectorApp.getCurrentActivity())
                            .setTitle(R.string.room_participants_remove_prompt_title)
                            .setMessage(text)
                            .setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();

                                    ArrayList<String> userIds = new ArrayList<String>();
                                    userIds.add(participantItem.mUserId);

                                    kickUsers(userIds, 0);
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
            }

            @Override
            public void onLeaveClick() {
                // The user is trying to leave with unsaved changes. Warn about that
                new AlertDialog.Builder(VectorApp.getCurrentActivity())
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
                                        mProgressView.setVisibility(View.GONE);
                                        // display something
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        mProgressView.setVisibility(View.GONE);
                                        // display something
                                    }

                                    @Override
                                    public void onMatrixError(MatrixError e) {
                                        mProgressView.setVisibility(View.GONE);
                                    }

                                    @Override
                                    public void onUnexpectedError(Exception e) {
                                        mProgressView.setVisibility(View.GONE);
                                    }
                                });
                                getActivity().finish();
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
        });
    }

    /**
     * @return the participant User Ids except oneself.
     */
    public ArrayList<String> getUserIdsList() {
        return mAdapter.getUserIdsist();
    }
}
