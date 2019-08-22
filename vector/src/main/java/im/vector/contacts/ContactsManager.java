/*
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.contacts;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import im.vector.Matrix;
import im.vector.VectorApp;
import im.vector.util.PhoneNumberUtils;

/**
 * Manage the local contacts
 */
public class ContactsManager implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String LOG_TAG = ContactsManager.class.getSimpleName();

    /**
     * Contacts update listener
     */
    public interface ContactsManagerListener {
        /**
         * Called when the contacts list have been refreshed
         */
        void onRefresh();

        /**
         * Call when some contact PIDs have been retrieved
         */
        void onPIDsUpdate();

        /**
         * Called when an user presence has been updated
         */
        void onContactPresenceUpdate(Contact contact, String matrixId);

        /**
         * Called when the Terms of the Identity server has not being accepted
         */
        void onIdentityServerTermsNotSigned(String token);
    }

    // singleton
    private static ContactsManager mInstance = null;

    // the contacts list snapshot
    private List<Contact> mContactsList = null;

    // the listeners
    private final List<ContactsManagerListener> mListeners = new ArrayList<>();

    // a contacts population is in progress
    private boolean mIsPopulating = false;

    // set to true when there is a pending
    private boolean mIsRetrievingPids = false;
    private boolean mArePidsRetrieved = false;

    // Trigger another PIDs retrieval when there is a valid data connection.
    private boolean mRetryPIDsRetrievalOnConnect = false;

    // the application context
    private final Context mContext;

    /**
     * Network events listener
     */
    private final IMXNetworkEventListener mNetworkConnectivityReceiver = new IMXNetworkEventListener() {
        @Override
        public void onNetworkConnectionUpdate(boolean isConnected) {
            if (isConnected && mRetryPIDsRetrievalOnConnect) {
                retrievePids();
            }
        }
    };

    // retriever listener
    private final PIDsRetriever.PIDsRetrieverListener mPIDsRetrieverListener = new PIDsRetriever.PIDsRetrieverListener() {
        /**
         * Warn the listeners about a contact information update.
         * @param contact the contact
         * @param mxid the mxid
         */
        private void onContactPresenceUpdate(Contact contact, Contact.MXID mxid) {
            for (ContactsManagerListener listener : mListeners) {
                try {
                    listener.onContactPresenceUpdate(contact, mxid.mMatrixId);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "onContactPresenceUpdate failed " + e.getMessage(), e);
                }
            }
        }

        /**
         * Warn that some PIDs have been retrieved from the contacts data.
         */
        private void onPIDsUpdate() {
            for (ContactsManagerListener listener : mListeners) {
                try {
                    listener.onPIDsUpdate();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "onPIDsUpdate failed " + e.getMessage(), e);
                }
            }
        }

        @Override
        public void onFailure(String accountId) {
            // ignore the current response because the request has been cancelled
            if (!mIsRetrievingPids) {
                Log.d(LOG_TAG, "## Retrieve a PIDS success whereas it is not expected");
                return;
            }

            mIsRetrievingPids = false;
            mArePidsRetrieved = false;
            mRetryPIDsRetrievalOnConnect = true;
            Log.d(LOG_TAG, "## fail to retrieve the PIDs");

            // warn that the current request failed.
            // Thus, if the listeners display a spinner (or so), it should be hidden.
            onPIDsUpdate();
        }

        @Override
        public void onIdentityServerTermsNotSigned(String token) {
            for (ContactsManagerListener listener : mListeners) {
                try {
                    listener.onIdentityServerTermsNotSigned(token);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "onTermsNotSigned failed " + e.getMessage(), e);
                }
            }
        }

        @Override
        public void onSuccess(final String accountId) {
            // ignore the current response because the request has been cancelled
            if (!mIsRetrievingPids) {
                Log.d(LOG_TAG, "## Retrieve a PIDS success whereas it is not expected");
                return;
            }

            Log.d(LOG_TAG, "## Retrieve IPDs successfully");

            mRetryPIDsRetrievalOnConnect = false;
            mIsRetrievingPids = false;
            mArePidsRetrieved = true;

            // warn that the contacts list have been updated
            onPIDsUpdate();

            // privacy
            // Log.d(LOG_TAG, "onPIDsRetrieved : the contact " + contact + " retrieves its 3PIds.");

            MXSession session = Matrix.getInstance(VectorApp.getInstance().getApplicationContext()).getSession(accountId);

            if ((null != session) && session.isAlive() && (null != mContactsList)) {
                for (final Contact contact : mContactsList) {
                    Set<String> medias = contact.getMatrixIdMediums();

                    for (String media : medias) {
                        final Contact.MXID mxid = contact.getMXID(media);
                        mxid.mUser = session.getDataHandler().getUser(mxid.mMatrixId);

                        // if the user is not known, get its presence
                        if (null == mxid.mUser) {
                            session.getPresenceApiClient().getPresence(mxid.mMatrixId, new ApiCallback<User>() {
                                @Override
                                public void onSuccess(User user) {
                                    Log.d(LOG_TAG, "retrieve the presence of " + mxid.mMatrixId + " :" + user);
                                    mxid.mUser = user;
                                    onContactPresenceUpdate(contact, mxid);
                                }

                                private void onError(String errorMessage) {
                                    Log.e(LOG_TAG, "cannot retrieve the presence of " + mxid.mMatrixId + " :" + errorMessage);
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
                }
            }
        }
    };

    /**
     * @return the static instance
     */
    public static ContactsManager getInstance() {
        if (null == mInstance) {
            mInstance = new ContactsManager();
        }

        return mInstance;
    }

    /**
     * Constructor
     */
    private ContactsManager() {
        mContext = VectorApp.getInstance().getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(mContext).registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Provides an unique identifier of the contacts snapshot
     *
     * @return an unique identifier
     */
    public int getLocalContactsSnapshotSession() {
        if (null != mContactsList) {
            return mContactsList.hashCode();
        } else {
            return 0;
        }
    }

    /**
     * Refresh the local contacts list snapshot.
     *
     * @return a local contacts list snapshot.
     */
    public Collection<Contact> getLocalContactsSnapshot() {
        return mContactsList;
    }

    /**
     * Tell if the contacts snapshot list is ready
     *
     * @return true if the contacts snapshot list is ready
     */
    public boolean didPopulateLocalContacts() {
        boolean res;
        boolean isPopulating;

        synchronized (LOG_TAG) {
            res = (null != mContactsList);
            isPopulating = mIsPopulating;
        }

        if (!res && !isPopulating) {
            refreshLocalContactsSnapshot();
        }

        return res;
    }

    /**
     * reset
     */
    public void reset() {
        mListeners.clear();
        clearSnapshot();
    }

    /**
     * Clear the current snapshot
     */
    public void clearSnapshot() {
        synchronized (LOG_TAG) {
            mContactsList = null;
        }

        MXSession defaultSession = Matrix.getInstance(VectorApp.getInstance()).getDefaultSession();

        if (null != defaultSession) {
            defaultSession.getNetworkConnectivityReceiver().removeEventListener(mNetworkConnectivityReceiver);
        }
    }

    /**
     * Update the contacts with the new country codes.
     */
    private void onCountryCodeUpdate() {
        synchronized (LOG_TAG) {
            if (null != mContactsList) {
                for (Contact contact : mContactsList) {
                    contact.onCountryCodeUpdate();
                }
            }
        }

        // the PIDs will be refreshed the next time
        // anyone will require them.
        mIsRetrievingPids = false;
        mArePidsRetrieved = false;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (TextUtils.equals(key, PhoneNumberUtils.COUNTRY_CODE_PREF_KEY)) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onCountryCodeUpdate();
                }
            });
        }
    }

    /**
     * Add a listener.
     *
     * @param listener the listener to add.
     */
    public void addListener(ContactsManagerListener listener) {
        if (null != listener) {
            mListeners.add(listener);
        }
    }

    /**
     * Remove a listener.
     *
     * @param listener the listener to remove.
     */
    public void removeListener(ContactsManagerListener listener) {
        if (null != listener) {
            mListeners.remove(listener);
        }
    }

    /**
     * Tells if the contacts PIDs have been retrieved
     *
     * @return true if the PIDs have been retrieved.
     */
    public boolean arePIDsRetrieved() {
        return mArePidsRetrieved;
    }

    /**
     * Retrieve the contacts PIDs
     */
    public void retrievePids() {
        Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mIsRetrievingPids) {
                    Log.d(LOG_TAG, "## retrievePids() : already in progress");
                } else if (mArePidsRetrieved) {
                    Log.d(LOG_TAG, "## retrievePids() : already done");
                } else {
                    Log.d(LOG_TAG, "## retrievePids() : Start search");
                    mIsRetrievingPids = true;
                    PIDsRetriever.getInstance().retrieveMatrixIds(VectorApp.getInstance(), mContactsList, false);
                }
            }
        });
    }

    /**
     * List the local contacts.
     */
    public void refreshLocalContactsSnapshot() {
        boolean isPopulating;

        synchronized (LOG_TAG) {
            isPopulating = mIsPopulating;
        }

        // test if there is a population is in progress
        if (isPopulating) {
            return;
        }

        synchronized (LOG_TAG) {
            mIsPopulating = true;
        }

        // refresh the contacts list in background
        Thread t = new Thread(new Runnable() {
            public void run() {
                long t0 = System.currentTimeMillis();
                ContentResolver cr = mContext.getContentResolver();
                Map<String, Contact> dict = new HashMap<>();

                // test if the user allows to access to the contact
                if (isContactBookAccessAllowed()) {
                    Log.d(LOG_TAG, "## refreshLocalContactsSnapshot() starts");

                    // get the names
                    Cursor namesCur = null;

                    try {
                        namesCur = cr.query(ContactsContract.Data.CONTENT_URI,
                                new String[]{ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                                        ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID,
                                        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
                                },
                                ContactsContract.Data.MIMETYPE + " = ?",
                                new String[]{ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}, null);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Contact names query Msg=" + e.getMessage(), e);
                    }

                    if (namesCur != null) {
                        try {
                            while (namesCur.moveToNext()) {
                                String displayName = namesCur.getString(namesCur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
                                String contactId = namesCur.getString(namesCur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID));
                                String thumbnailUri
                                        = namesCur.getString(namesCur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.PHOTO_THUMBNAIL_URI));

                                if (null != contactId) {
                                    Contact contact = dict.get(contactId);

                                    if (null == contact) {
                                        contact = new Contact(contactId);
                                        dict.put(contactId, contact);
                                    }

                                    if (null != displayName) {
                                        contact.setDisplayName(displayName);
                                    }

                                    if (null != thumbnailUri) {
                                        contact.setThumbnailUri(thumbnailUri);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Contact names query2 Msg=" + e.getMessage(), e);
                        }

                        namesCur.close();
                    }

                    // get the phonenumbers
                    Cursor phonesCur = null;

                    try {
                        phonesCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
                                        ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                                },
                                null, null, null);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Phone numbers query Msg=" + e.getMessage(), e);
                    }

                    if (null != phonesCur) {
                        try {
                            while (phonesCur.moveToNext()) {
                                final String pn = phonesCur.getString(phonesCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                                final String pnE164 = phonesCur.getString(phonesCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER));

                                if (!TextUtils.isEmpty(pn)) {
                                    String contactId = phonesCur.getString(phonesCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID));

                                    if (null != contactId) {
                                        Contact contact = dict.get(contactId);
                                        if (null == contact) {
                                            contact = new Contact(contactId);
                                            dict.put(contactId, contact);
                                        }

                                        contact.addPhoneNumber(pn, pnE164);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Phone numbers query2 Msg=" + e.getMessage(), e);
                        }

                        phonesCur.close();
                    }

                    // get the emails
                    Cursor emailsCur = null;

                    try {
                        emailsCur = cr.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                                new String[]{ContactsContract.CommonDataKinds.Email.DATA, // actual email
                                        ContactsContract.CommonDataKinds.Email.CONTACT_ID},
                                null, null, null);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Emails query Msg=" + e.getMessage(), e);
                    }

                    if (emailsCur != null) {
                        try {
                            while (emailsCur.moveToNext()) {
                                String email = emailsCur.getString(emailsCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA));
                                if (!TextUtils.isEmpty(email)) {
                                    String contactId = emailsCur.getString(emailsCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID));

                                    if (null != contactId) {
                                        Contact contact = dict.get(contactId);
                                        if (null == contact) {
                                            contact = new Contact(contactId);
                                            dict.put(contactId, contact);
                                        }

                                        contact.addEmailAdress(email);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## refreshLocalContactsSnapshot(): Exception - Emails query2 Msg=" + e.getMessage(), e);
                        }

                        emailsCur.close();
                    }
                } else {
                    Log.d(LOG_TAG, "## refreshLocalContactsSnapshot() : permission to read contacts is not granted");
                }

                synchronized (LOG_TAG) {
                    mContactsList = new ArrayList<>(dict.values());
                    mIsPopulating = false;
                }

                // race condition reported by GA.
                if (null == mContactsList) {
                    Log.d(LOG_TAG, "## ## refreshLocalContactsSnapshot() : the contacts list has been cleared while processing it");
                    mIsRetrievingPids = false;
                    mArePidsRetrieved = false;
                    return;
                }

                long delta = System.currentTimeMillis() - t0;

                Log.d(LOG_TAG, "## refreshLocalContactsSnapshot(): retrieve " + mContactsList.size() + " contacts in " + delta + " ms");

                // define the PIDs listener
                PIDsRetriever.getInstance().setPIDsRetrieverListener(mPIDsRetrieverListener);

                // trigger a PIDs retrieval
                // add a network listener to ensure that the PIDS will be retrieved asap a valid network will be found.
                MXSession defaultSession = Matrix.getInstance(VectorApp.getInstance()).getDefaultSession();
                if (null != defaultSession) {
                    defaultSession.getNetworkConnectivityReceiver().addEventListener(mNetworkConnectivityReceiver);

                    // reset the PIDs retriever statuses
                    mIsRetrievingPids = false;
                    mArePidsRetrieved = false;

                    // the PIDs retrieval is done on demand.
                }

                Handler handler = new Handler(Looper.getMainLooper());

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        for (ContactsManagerListener listener : mListeners) {
                            try {
                                listener.onRefresh();
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "refreshLocalContactsSnapshot : onRefresh failed" + e.getMessage(), e);
                            }
                        }
                    }
                });
            }
        });

        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    //================================================================================
    // Contacts book management (for android < M devices)
    //================================================================================
    public static final String CONTACTS_BOOK_ACCESS_KEY = "CONTACT_BOOK_ACCESS_KEY";

    /**
     * Tells if the contacts book access has been requested.
     * For android > M devices, it only tells if the permission has been granted.
     *
     * @return true it was requested once
     */
    public boolean isContactBookAccessRequested() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_CONTACTS));
        } else {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
            return preferences.contains(CONTACTS_BOOK_ACCESS_KEY);
        }
    }

    /**
     * Update the contacts book access.
     *
     * @param isAllowed true to allowed the contacts book access.
     */
    public void setIsContactBookAccessAllowed(boolean isAllowed) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            PreferenceManager.getDefaultSharedPreferences(mContext)
                    .edit()
                    .putBoolean(CONTACTS_BOOK_ACCESS_KEY, isAllowed)
                    .apply();
        }
        mIsRetrievingPids = false;
        mArePidsRetrieved = false;
    }

    /**
     * Tells if the contacts book access has been granted
     *
     * @return true if it was granted.
     */
    public boolean isContactBookAccessAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_CONTACTS));
        } else {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
            return preferences.getBoolean(CONTACTS_BOOK_ACCESS_KEY, false);
        }
    }
}

