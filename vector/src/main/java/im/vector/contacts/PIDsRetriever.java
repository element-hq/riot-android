/*
 * Copyright 2015 OpenMarket Ltd
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
package im.vector.contacts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.pid.ThreePid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import im.vector.Matrix;
import im.vector.VectorApp;

/**
 * retrieve the contact matrix IDs
 */
public class PIDsRetriever {
    private static final String LOG_TAG = PIDsRetriever.class.getSimpleName();

    public interface PIDsRetrieverListener {
        /**
         * Called when the contacts PIDs are retrieved.
         */
        void onSuccess(String accountId);

        /**
         * Called the PIDs retrieval fails.
         */
        void onFailure(String accountId);
    }

    // current instance
    private static PIDsRetriever mPIDsRetriever = null;

    /**
     * @return the PIDsRetriever instance.
     */
    public static PIDsRetriever getInstance() {
        if (null == mPIDsRetriever) {
            mPIDsRetriever = new PIDsRetriever();
        }

        return mPIDsRetriever;
    }

    // MatrixID <-> medium
    private final HashMap<String, Contact.MXID> mMatrixIdsByMedium = new HashMap<>();

    // listeners list
    private PIDsRetrieverListener mListener = null;

    /**
     * Set the listener.
     *
     * @param listener the listener.
     */
    public void setPIDsRetrieverListener(PIDsRetrieverListener listener) {
        mListener = listener;
    }

    /**
     * Clear the email to matrix id conversion table
     */
    public void onAppBackgrounded() {
        mMatrixIdsByMedium.clear();
    }

    /**
     * reset
     */
    public void reset() {
        mMatrixIdsByMedium.clear();
        mListener = null;
    }

    /**
     * ce (email, phonenumber...)
     *
     * @param item the item to retrieve
     * @return the linked MXID if it exists
     */
    public Contact.MXID getMXID(String item) {
        Contact.MXID mxId = null;

        if ((null != item) && mMatrixIdsByMedium.containsKey(item)) {
            mxId = mMatrixIdsByMedium.get(item);

            // ensure that a valid matrix Id is set
            if (null == mxId.mMatrixId) {
                mxId = null;
            }
        }

        return mxId;
    }

    /**
     * Retrieve the matrix ids for a list of contacts with the local cache.
     *
     * @param contacts the contacts list
     * @return the medium addresses which are not cached.
     */
    private Set<String> retrieveMatrixIds(List<Contact> contacts) {
        Set<String> requestedMediums = new HashSet<>();

        for (Contact contact : contacts) {
            // check if the medium have only been checked
            // i.e. requested their match PID to the identity server.

            // email first
            for (String email : contact.getEmails()) {
                if (mMatrixIdsByMedium.containsKey(email)) {
                    Contact.MXID mxid = mMatrixIdsByMedium.get(email);

                    if (null != mxid) {
                        contact.put(email, mxid);
                    }
                } else {
                    requestedMediums.add(email);
                }
            }

            for (Contact.PhoneNumber pn : contact.getPhonenumbers()) {
                if (mMatrixIdsByMedium.containsKey(pn.mMsisdnPhoneNumber)) {
                    Contact.MXID mxid = mMatrixIdsByMedium.get(pn.mMsisdnPhoneNumber);

                    if (null != mxid) {
                        contact.put(pn.mMsisdnPhoneNumber, mxid);
                    }
                } else {
                    requestedMediums.add(pn.mMsisdnPhoneNumber);
                }
            }
        }

        // Make sure the set does not contain null value
        requestedMediums.remove(null);

        return requestedMediums;
    }

    /**
     * Retrieve the matrix IDs from the contact fields (only emails are supported by now).
     * Update the contact fields with the found Matrix Ids.
     * The update could require some remote requests : they are done only localUpdateOnly is false.
     *
     * @param context         the context.
     * @param contacts        the contacts list.
     * @param localUpdateOnly true to only support refresh from local information.
     * @return true if the matrix Ids have been retrieved
     */
    public void retrieveMatrixIds(final Context context, final List<Contact> contacts, final boolean localUpdateOnly) {
        Log.d(LOG_TAG, String.format(VectorApp.getApplicationLocale(), "retrieveMatrixIds starts for %d contacts", contacts == null ? 0 : contacts.size()));
        // sanity checks
        if ((null == contacts) || (0 == contacts.size())) {
            if (null != mListener) {
                Handler handler = new Handler(Looper.getMainLooper());

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        MXSession session = Matrix.getInstance(context.getApplicationContext()).getDefaultSession();

                        if (null != session) {
                            mListener.onSuccess(session.getMyUserId());
                        }
                    }
                });
            }

            return;
        }

        Set<String> missingMediums = retrieveMatrixIds(contacts);

        if (!localUpdateOnly && !missingMediums.isEmpty()) {
            Map<String, String> lookupMap = new HashMap<>();

            for (String medium : missingMediums) {
                if (medium != null) {
                    if (android.util.Patterns.EMAIL_ADDRESS.matcher(medium).matches()) {
                        lookupMap.put(medium, ThreePid.MEDIUM_EMAIL);
                    } else {
                        lookupMap.put(medium, ThreePid.MEDIUM_MSISDN);
                    }
                }
            }

            final List<String> fRequestedMediums = new ArrayList<>(lookupMap.keySet());
            final List<String> medias = new ArrayList<>(lookupMap.values());
            Collection<MXSession> sessions = Matrix.getInstance(context.getApplicationContext()).getSessions();

            for (MXSession session : sessions) {
                final String accountId = session.getCredentials().userId;

                session.lookup3Pids(fRequestedMediums, medias, new ApiCallback<List<String>>() {
                    @Override
                    public void onSuccess(final List<String> pids) {
                        Log.e(LOG_TAG, "lookup3Pids success " + pids.size());
                        // update the local cache
                        for (int index = 0; index < fRequestedMediums.size(); index++) {
                            String medium = fRequestedMediums.get(index);
                            String mxId = pids.get(index);

                            if (!TextUtils.isEmpty(mxId)) {
                                mMatrixIdsByMedium.put(medium, new Contact.MXID(mxId, accountId));
                            }
                        }

                        retrieveMatrixIds(contacts);

                        // warn the listener of the update
                        if (null != mListener) {
                            mListener.onSuccess(accountId);
                        }
                    }

                    /**
                     * Common error routine
                     * @param errorMessage the error message
                     */
                    private void onError(String errorMessage) {
                        Log.e(LOG_TAG, "## retrieveMatrixIds() : failed " + errorMessage);

                        if (null != mListener) {
                            mListener.onFailure(accountId);
                        }
                    }

                    // ignore the network errors
                    // will be checked again later
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

