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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import im.vector.Matrix;
import im.vector.R;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorParticipantsAdapter;
import im.vector.contacts.Contact;
import im.vector.contacts.ContactsManager;
import im.vector.util.VectorUtils;
import im.vector.view.VectorAutoCompleteTextView;

/**
 * This class provides a way to search other user to invite them in a dedicated room
 */
public class VectorRoomInviteMembersActivity extends VectorBaseSearchActivity {
    private static final String LOG_TAG = "VectorInviteMembersAct";

    // room identifier
    public static final String EXTRA_ROOM_ID = "VectorInviteMembersActivity.EXTRA_ROOM_ID";

    // participants to hide in the list
    public static final String EXTRA_HIDDEN_PARTICIPANT_ITEMS = "VectorInviteMembersActivity.EXTRA_HIDDEN_PARTICIPANT_ITEMS";

    // boolean : true displays a dialog to confirm the member selection
    public static final String EXTRA_ADD_CONFIRMATION_DIALOG = "VectorInviteMembersActivity.EXTRA_ADD_CONFIRMATION_DIALOG";

    // the selected user ids list
    public static final String EXTRA_OUT_SELECTED_USER_IDS = "VectorInviteMembersActivity.EXTRA_OUT_SELECTED_USER_IDS";

    // the selected participants list
    public static final String EXTRA_OUT_SELECTED_PARTICIPANT_ITEMS = "VectorInviteMembersActivity.EXTRA_OUT_SELECTED_PARTICIPANT_ITEMS";

    // account data
    private String mMatrixId;

    // main UI items
    private ExpandableListView mListView;

    // load
    private View mLoadingView;

    // participants list
    private List<ParticipantAdapterItem> mHiddenParticipantItems = new ArrayList<>();

    // adapter
    private VectorParticipantsAdapter mAdapter;

    // dummy link to open a dedicated dialog to invite members with matrix id, email or MSISDN
    private View mInviteByIdTextView;

    // tell if a confirmation dialog must be displayed to validate the user ids list
    private boolean mAddConfirmationDialog;

    // retrieve a matrix Id from an email
    private final ContactsManager.ContactsManagerListener mContactsListener = new ContactsManager.ContactsManagerListener() {
        @Override
        public void onRefresh() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onPatternUpdate(false);
                }
            });
        }

        @Override
        public void onContactPresenceUpdate(final Contact contact, final String matrixId) {
        }

        @Override
        public void onPIDsUpdate() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAdapter.onPIdsUpdate();
                }
            });
        }
    };

    // refresh the presence asap
    private final MXEventListener mEventsListener = new MXEventListener() {
        @Override
        public void onPresenceUpdate(final Event event, final User user) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Map<Integer, List<Integer>> visibleChildViews = VectorUtils.getVisibleChildViews(mListView, mAdapter);

                    for (Integer groupPosition : visibleChildViews.keySet()) {
                        List<Integer> childPositions = visibleChildViews.get(groupPosition);

                        for (Integer childPosition : childPositions) {
                            Object item = mAdapter.getChild(groupPosition, childPosition);

                            if (item instanceof ParticipantAdapterItem) {
                                ParticipantAdapterItem participantAdapterItem = (ParticipantAdapterItem) item;

                                if (TextUtils.equals(user.user_id, participantAdapterItem.mUserId)) {
                                    mAdapter.notifyDataSetChanged();
                                    break;
                                }
                            }
                        }
                    }
                }
            });
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (CommonActivityUtils.shouldRestartApp(this)) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
            return;
        }

        if (CommonActivityUtils.isGoingToSplash(this)) {
            Log.d(LOG_TAG, "onCreate : Going to splash screen");
            return;
        }

        Intent intent = getIntent();

        if (intent.hasExtra(EXTRA_MATRIX_ID)) {
            mMatrixId = intent.getStringExtra(EXTRA_MATRIX_ID);
        }

        // get current session
        mSession = Matrix.getInstance(getApplicationContext()).getSession(mMatrixId);
        if (null == mSession) {
            finish();
            return;
        }

        if (intent.hasExtra(EXTRA_HIDDEN_PARTICIPANT_ITEMS)) {
            mHiddenParticipantItems = (List<ParticipantAdapterItem>) intent.getSerializableExtra(EXTRA_HIDDEN_PARTICIPANT_ITEMS);
        }

        String roomId = intent.getStringExtra(EXTRA_ROOM_ID);

        if (null != roomId) {
            mRoom = mSession.getDataHandler().getStore().getRoom(roomId);
        }

        // tell if a confirmation dialog must be displayed.
        mAddConfirmationDialog = intent.getBooleanExtra(EXTRA_ADD_CONFIRMATION_DIALOG, false);

        //
        setContentView(R.layout.activity_vector_invite_members);

        // the user defines a
        if (null != mPatternToSearchEditText) {
            mPatternToSearchEditText.setHint(R.string.room_participants_invite_search_another_user);
        }

        mLoadingView = findViewById(R.id.search_in_progress_view);

        mListView = (ExpandableListView) findViewById(R.id.room_details_members_list);
        // the chevron is managed in the header view
        mListView.setGroupIndicator(null);

        mAdapter = new VectorParticipantsAdapter(this,
                R.layout.adapter_item_vector_add_participants,
                R.layout.adapter_item_vector_people_header,
                mSession, roomId, true);
        mAdapter.setHiddenParticipantItems(mHiddenParticipantItems);
        mListView.setAdapter(mAdapter);

        mListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                Object item = mAdapter.getChild(groupPosition, childPosition);

                if (item instanceof ParticipantAdapterItem && ((ParticipantAdapterItem) item).mIsValid) {
                    ParticipantAdapterItem participantAdapterItem = (ParticipantAdapterItem) item;
                    finish(new ArrayList<>(Arrays.asList(participantAdapterItem)));
                    return true;
                }
                return false;
            }
        });

        mInviteByIdTextView = findViewById(R.id.search_invite_by_id);
        mInviteByIdTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayInviteByUserId();
            }
        });

        // Check permission to access contacts
        CommonActivityUtils.checkPermissions(CommonActivityUtils.REQUEST_CODE_PERMISSION_MEMBERS_SEARCH, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSession.getDataHandler().addListener(mEventsListener);
        ContactsManager.getInstance().addListener(mContactsListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSession.getDataHandler().removeListener(mEventsListener);
        ContactsManager.getInstance().removeListener(mContactsListener);
    }

    @Override
    public void onRequestPermissionsResult(int aRequestCode, @NonNull String[] aPermissions, @NonNull int[] aGrantResults) {
        if (0 == aPermissions.length) {
            Log.e(LOG_TAG, "## onRequestPermissionsResult(): cancelled " + aRequestCode);
        } else if (aRequestCode == CommonActivityUtils.REQUEST_CODE_PERMISSION_MEMBERS_SEARCH) {
            if (PackageManager.PERMISSION_GRANTED == aGrantResults[0]) {
                Log.d(LOG_TAG, "## onRequestPermissionsResult(): READ_CONTACTS permission granted");
                ContactsManager.getInstance().refreshLocalContactsSnapshot();
                onPatternUpdate(false);
            } else {
                Log.d(LOG_TAG, "## onRequestPermissionsResult(): READ_CONTACTS permission not granted");
                CommonActivityUtils.displayToast(this, getString(R.string.missing_permissions_warning));
            }
        }
    }

    /**
     * The search pattern has been updated
     */
    @Override
    protected void onPatternUpdate(boolean isTypingUpdate) {
        String pattern = mPatternToSearchEditText.getText().toString();

        // display a spinner while the other room members are listed
        if (!mAdapter.isKnownMembersInitialized()) {
            mLoadingView.setVisibility(View.VISIBLE);
        }

        // wait that the local contacts are populated
        if (!ContactsManager.getInstance().didPopulateLocalContacts()) {
            Log.d(LOG_TAG, "## onPatternUpdate() : The local contacts are not yet populated");
            mAdapter.reset();
            mLoadingView.setVisibility(View.VISIBLE);
            return;
        }

        mAdapter.setSearchedPattern(pattern, null, new VectorParticipantsAdapter.OnParticipantsSearchListener() {
            @Override
            public void onSearchEnd(final int count) {
                mListView.post(new Runnable() {
                    @Override
                    public void run() {
                        mLoadingView.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    /**
     * Display a selection confirmation dialog.
     *
     * @param participantAdapterItems the selected participants
     */
    private void finish(final ArrayList<ParticipantAdapterItem> participantAdapterItems) {
        final List<String> hiddenUserIds = new ArrayList<>();
        final ArrayList<String> userIds = new ArrayList<>();
        final ArrayList<String> displayNames = new ArrayList<>();

        // list the hidden user Ids
        for(ParticipantAdapterItem item : mHiddenParticipantItems) {
            hiddenUserIds.add(item.mUserId);
        }

        // if a room is defined
        if (null != mRoom) {
            // the room members must not be added again
            Collection<RoomMember> members = mRoom.getLiveState().getDisplayableMembers();
            for (RoomMember member : members) {
                if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN) || TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_INVITE)) {
                    hiddenUserIds.add(member.getUserId());
                }
            }
        }

        // build the output lists
        for (ParticipantAdapterItem item : participantAdapterItems) {
            // check if the user id can be added
            if (!hiddenUserIds.contains(item.mUserId)) {
                userIds.add(item.mUserId);
                // display name
                if (MXSession.isUserId(item.mUserId)) {
                    User user = mSession.getDataHandler().getStore().getUser(item.mUserId);
                    if ((null != user) && !TextUtils.isEmpty(user.displayname)) {
                        displayNames.add(user.displayname);
                    } else {
                        displayNames.add(item.mUserId);
                    }
                } else {
                    displayNames.add(item.mUserId);
                }
            }
        }

        // a confirmation dialog has been requested
        if (mAddConfirmationDialog && (displayNames.size() > 0)) {
            android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(VectorRoomInviteMembersActivity.this);
            builder.setTitle(R.string.dialog_title_confirmation);

            String message = "";

            if (displayNames.size() == 1) {
                message = displayNames.get(0);
            } else {
                for (int i = 0; i < (displayNames.size() - 2); i++) {
                    message += displayNames.get(i) + ", ";
                }

                message += displayNames.get(displayNames.size() - 2) + " " + getText(R.string.and) + " " + displayNames.get(displayNames.size() - 1);
            }

            builder.setMessage(getString(R.string.room_participants_invite_prompt_msg, message));
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // returns the selected users
                    Intent intent = new Intent();
                    intent.putExtra(EXTRA_OUT_SELECTED_USER_IDS, userIds);
                    intent.putExtra(EXTRA_OUT_SELECTED_PARTICIPANT_ITEMS, participantAdapterItems);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            });

            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // nothing to do
                }
            });

            builder.show();
        } else {
            // returns the selected users
            Intent intent = new Intent();
            intent.putExtra(EXTRA_OUT_SELECTED_USER_IDS, userIds);
            intent.putExtra(EXTRA_OUT_SELECTED_PARTICIPANT_ITEMS, participantAdapterItems);
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    /**
     * Display the invitation dialog.
     */
    private void displayInviteByUserId() {
        View dialogLayout = getLayoutInflater().inflate(R.layout.dialog_invite_by_id, null);

        final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.people_search_invite_by_id_dialog_title);
        dialog.setView(dialogLayout);

        final VectorAutoCompleteTextView inviteTextView = (VectorAutoCompleteTextView) dialogLayout.findViewById(R.id.invite_by_id_edit_text);
        inviteTextView.initAutoCompletion(mSession);
        inviteTextView.setProvideMatrixIdOnly(true);

        dialog.setPositiveButton(R.string.invite, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // will be overridden to avoid dismissing the dialog while displaying the progress
            }
        });

        dialog.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        final AlertDialog inviteDialog = dialog.show();
        final Button inviteButton = inviteDialog.getButton(AlertDialog.BUTTON_POSITIVE);

        if (null != inviteButton) {
            inviteButton.setEnabled(false);

            inviteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String text = inviteTextView.getText().toString();
                    ArrayList<ParticipantAdapterItem> items = new ArrayList<>();
                    List<Pattern> patterns = Arrays.asList(MXSession.PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER, android.util.Patterns.EMAIL_ADDRESS);

                    for (Pattern pattern : patterns) {
                        Matcher matcher = pattern.matcher(text);
                        while (matcher.find()) {
                            try {
                                String userId = text.substring(matcher.start(0), matcher.end(0));
                                items.add(new ParticipantAdapterItem(userId, null, userId, true));
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## displayInviteByUserId() " + e.getMessage());
                            }
                        }
                    }

                    finish(items);

                    inviteDialog.dismiss();
                }
            });
        }

        inviteTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (null != inviteButton) {
                    String text = inviteTextView.getText().toString();

                    boolean containMXID = MXSession.PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER.matcher(text).find();
                    boolean containEmailAddress = android.util.Patterns.EMAIL_ADDRESS.matcher(text).find();

                    inviteButton.setEnabled(containMXID || containEmailAddress);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }
}
