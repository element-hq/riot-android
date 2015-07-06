/*
 * Copyright 2015 OpenMarket Ltd
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

package org.matrix.console.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;

import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.console.R;
import org.matrix.console.activity.CommonActivityUtils;
import org.matrix.console.adapters.AdapterUtils;
import org.matrix.console.adapters.ContactsListAdapter;
import org.matrix.console.contacts.Contact;
import org.matrix.console.contacts.ContactsManager;
import org.matrix.console.contacts.PIDsRetriever;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

/**
 * A dialog fragment showing the contacts list
 */
public class ContactsListDialogFragment extends DialogFragment implements PIDsRetriever.PIDsRetrieverListener, ContactsManager.ContactsManagerListener  {
    private static final String LOG_TAG = "ContactsListDialogFragment";

    private ListView mListView;
    private ContactsListAdapter mAdapter;

    private ArrayList<Contact> mLocalContacts;
    private ArrayList<Contact> mFilteredContacts;
    private boolean mDisplayOnlyMatrixUsers = false;
    private String mSearchPattern = "";

    public static ContactsListDialogFragment newInstance() {
        ContactsListDialogFragment f = new ContactsListDialogFragment();
        Bundle args = new Bundle();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        Collection<Contact> contacts = ContactsManager.getLocalContactsSnapshot(getActivity());

        if (contacts.size() != 0) {
            builder.setTitle(getString(R.string.contacts) + " (" + contacts.size() + ")");
        } else {
            builder.setTitle(getString(R.string.contacts));
        }

        View view = getActivity().getLayoutInflater().inflate(R.layout.fragment_contacts_list, null);
        builder.setView(view);
        initView(view);

        return builder.create();
    }

    // Comparator to order members alphabetically
    private Comparator<Contact> alphaComparator = new Comparator<Contact>() {
        @Override
        public int compare(Contact contact1, Contact contact2) {
            String displayname1 = (contact1.getDisplayName() == null)? contact1.mContactId : contact1.getDisplayName();
            String displayname2 = (contact2.getDisplayName() == null)? contact2.mContactId : contact2.getDisplayName();

            return String.CASE_INSENSITIVE_ORDER.compare(displayname1, displayname2);
        }
    };

    /**
     * Init the dialog view.
     * @param v the dialog view.
     */
    void initView(View v) {
        mListView = ((ListView)v.findViewById(R.id.listView_contacts));

        // get the local contacts
        mLocalContacts = new ArrayList<Contact>(ContactsManager.getLocalContactsSnapshot(getActivity()));

        mAdapter = new ContactsListAdapter(getActivity(), R.layout.adapter_item_contact);

        // sort them
        Collections.sort(mLocalContacts, alphaComparator);

        mListView.setFastScrollAlwaysVisible(true);
        mListView.setFastScrollEnabled(true);
        mListView.setAdapter(mAdapter);

        refreshAdapter();

        // a button could be added to filter the contacts to display only the matrix users
        // but the lookup method is too slow (1 address / request).
        // it could be enabled when a batch request will be implemented
        /*
        final Button button = (Button)v.findViewById(R.id.button_matrix_users);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                mDisplayOnlyMatrixUsers = !mDisplayOnlyMatrixUsers;

                if (mDisplayOnlyMatrixUsers) {
                    button.setBackgroundResource(R.drawable.matrix_user);
                } else {
                    button.setBackgroundResource(R.drawable.ic_menu_allfriends);
                }

                refreshAdapter();
            }
        });*/

        final EditText editText = (EditText)v.findViewById(R.id.editText_contactBox);

        editText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                ContactsListDialogFragment.this.mSearchPattern = s.toString();
                refreshAdapter();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        // tap on one of them
        // if he is a matrix, offer to start a chat
        // it he is not a matrix user, offer to invite him by email or SMS.
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final Contact contact = mAdapter.getItem(position);
                final Activity activity = ContactsListDialogFragment.this.getActivity();

                if (contact.hasMatridIds(ContactsListDialogFragment.this.getActivity())) {
                    final  Contact.MXID mxid = contact.getFirstMatrixId();
                    // The user is trying to leave with unsaved changes. Warn about that
                    new AlertDialog.Builder(activity)
                            .setMessage(activity.getText(R.string.chat_with) + " " + mxid.mMatrixId + " ?")
                            .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            CommonActivityUtils.goToOneToOneRoom(mxid.mAccountId,  mxid.mMatrixId, activity, new SimpleApiCallback<Void>(getActivity()) {
                                            });
                                        }
                                    });
                                    dialog.dismiss();
                                    // dismiss the member list
                                    ContactsListDialogFragment.this.dismiss();

                                }
                            })
                            .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                            .create()
                            .show();

                } else {
                    // invite the user
                    final ArrayList<String> choicesList = new ArrayList<String>();

                    if (AdapterUtils.canSendSms(activity)) {
                        choicesList.addAll(contact.mPhoneNumbers);
                    }

                    choicesList.addAll(contact.mEmails);

                    // something to offer
                    if (choicesList.size() > 0) {
                        final String[] labels = new String[choicesList.size()];

                        for(int index = 0; index < choicesList.size(); index++) {
                            labels[index] = choicesList.get(index);
                        }

                        new AlertDialog.Builder(activity)
                                .setItems(labels, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        String value = labels[which];

                                        // SMS ?
                                        if (contact.mPhoneNumbers.indexOf(value) >= 0) {
                                            AdapterUtils.launchSmsIntent(activity, value, activity.getString(R.string.invitation_message));
                                        } else {
                                            // emails
                                            AdapterUtils.launchEmailIntent(activity, value, activity.getString(R.string.invitation_message));
                                        }

                                        // dismiss the member list
                                        ContactsListDialogFragment.this.dismiss();
                                    }
                                }).setTitle(activity.getString(R.string.invite_this_user_to_use_matrix)).show();
                    }
                }
            }
        });
    }

    private void refreshAdapter() {

        // matrix users selection
        ArrayList<Contact> filteredContacts = mLocalContacts;

        if (mDisplayOnlyMatrixUsers || (mSearchPattern.length() != 0)) {
            filteredContacts = new ArrayList<Contact>();

            if (mDisplayOnlyMatrixUsers && !TextUtils.isEmpty(mSearchPattern)) {
                for (Contact contact : mLocalContacts) {
                    if (contact.hasMatridIds(getActivity()) && contact.matchWithPattern(mSearchPattern)) {
                        filteredContacts.add(contact);
                    }
                }
            } else if (!TextUtils.isEmpty(mSearchPattern)) {
                for (Contact contact : mLocalContacts) {

                    if (contact.matchWithPattern(mSearchPattern)) {
                        // trigger the matrixID retrieval.
                        contact.hasMatridIds(getActivity());
                        filteredContacts.add(contact);
                    }
                }
            } else if (mDisplayOnlyMatrixUsers) {
                for (Contact contact : mLocalContacts) {
                    if (contact.hasMatridIds(getActivity())) {
                        filteredContacts.add(contact);
                    }
                }
            }
        }

        // clear the list
        mAdapter.clear();

        // to replace with the new filtered list
        mFilteredContacts = filteredContacts;

        // display them
        mAdapter.addAll(mFilteredContacts);

        // refresh the title
        if (null != getDialog()) {
            if (mFilteredContacts.size() != 0) {
                getDialog().setTitle(getString(R.string.contacts) + " (" + mFilteredContacts.size() + ")");
            } else {
                getDialog().setTitle(getString(R.string.contacts));
            }
        }

        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onStart() {
        super.onStart();
        PIDsRetriever.getIntance().setPIDsRetrieverListener(this);
        ContactsManager.addListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        PIDsRetriever.getIntance().setPIDsRetrieverListener(null);
        ContactsManager.removeListener(this);
    }

    /**
     * Called when the contact PIDs are retrieved
     */
    @Override public void onPIDsRetrieved(String accountId, Contact contact, final boolean has3PIDs) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // refresh only if there are some updates
                if (has3PIDs) {
                    refreshAdapter();
                }
            }
        });
    }

    /**
     * Called when the contacts list have been
     */
    public void onRefresh() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLocalContacts = new ArrayList<Contact>(ContactsManager.getLocalContactsSnapshot(getActivity()));
                refreshAdapter();
            }
        });
    }
}

