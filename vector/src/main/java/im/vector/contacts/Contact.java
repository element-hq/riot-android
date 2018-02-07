/*
 * Copyright 2016 OpenMarket Ltd
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
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;

import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import im.vector.VectorApp;
import im.vector.util.PhoneNumberUtils;

/**
 * A simple contact class
 */
public class Contact implements java.io.Serializable {

    private static final String LOG_TAG = Contact.class.getSimpleName();

    // a contact field (like email)
    // is linked to a matrix id/
    public static class MXID implements java.io.Serializable {
        // the MXSession identifier
        public final String mAccountId;

        // the related Matrix ID
        public final String mMatrixId;

        // the user description
        public User mUser;

        /**
         * Constructor
         *
         * @param matrixId  the matrix id
         * @param accountId the account id
         */
        public MXID(String matrixId, String accountId) {
            mMatrixId = (null == matrixId) ? "" : matrixId;
            mAccountId = accountId;
            mUser = null;
        }
    }

    /**
     * Defines a contact phone number.
     */
    public static class PhoneNumber implements java.io.Serializable {
        // Genuine phone number (given by contact cursor)
        public final String mRawPhoneNumber;

        // Genuine E164 phone number (given by contact cursor) without "+"
        // May be null
        public final String mE164PhoneNumber;

        // MSISDN format (E164 phone number without "+")
        // Same value as mE164PhoneNumber if not null or deduced from the current country code if mE164PhoneNumber is null
        public String mMsisdnPhoneNumber;

        // Without space, parenthesis
        public final String mCleanedPhoneNumber;

        /**
         * Constructor
         *
         * @param rawPhoneNumber  the genuine phone number
         * @param e164PhoneNumber the genuine E164 phone number
         */
        public PhoneNumber(String rawPhoneNumber, String e164PhoneNumber) {
            mRawPhoneNumber = rawPhoneNumber;
            // without space, parenthesis
            mCleanedPhoneNumber = rawPhoneNumber.replaceAll("[\\D]", "");

            if (!TextUtils.isEmpty(e164PhoneNumber)) {
                if (e164PhoneNumber.startsWith("+")) {
                    e164PhoneNumber = e164PhoneNumber.substring(1);
                }
                mE164PhoneNumber = e164PhoneNumber;
                mMsisdnPhoneNumber = e164PhoneNumber;
            } else {
                mE164PhoneNumber = null;
                // Attempt to deduce msisdn format using current country code
                refreshE164PhoneNumber();
            }
        }

        /**
         * Refresh the deduced e164 phone number.
         */
        public void refreshE164PhoneNumber() {
            if (TextUtils.isEmpty(mE164PhoneNumber)) {
                // Attempt to deduce E164 format using the new country code
                mMsisdnPhoneNumber = PhoneNumberUtils.getE164format(VectorApp.getInstance(), mRawPhoneNumber);
                if (TextUtils.isEmpty(mMsisdnPhoneNumber)) {
                    mMsisdnPhoneNumber = mCleanedPhoneNumber;
                }
            }
            Log.d(LOG_TAG, "## refreshE164PhoneNumber " + mMsisdnPhoneNumber);
        }

        /**
         * Check if the phone number starts by the given prefix
         *
         * @param prefix
         * @return true if matching found
         */
        public boolean startsWith(final String prefix) {
            return mRawPhoneNumber.startsWith(prefix) || (mE164PhoneNumber != null && mE164PhoneNumber.startsWith(prefix))
                    || mMsisdnPhoneNumber.startsWith(prefix) || mCleanedPhoneNumber.startsWith(prefix);
        }
    }

    // the contact ID
    private String mContactId = "";

    // the contact display name
    private String mDisplayName = "";
    // the thumbnail uri
    private String mThumbnailUri;
    // the thumbnail image
    private transient Bitmap mThumbnail;

    // phone numbers list
    private final ArrayList<PhoneNumber> mPhoneNumbers = new ArrayList<>();

    // emails list
    private final ArrayList<String> mEmails = new ArrayList<>();

    // MXID by medium (email or phone number)
    private final Map<String, MXID> mMXIDsByElement = new HashMap<>();

    /**
     * Constructor
     *
     * @param contactId the contact id.
     */
    public Contact(String contactId) {
        if (null != contactId) {
            mContactId = contactId;
        } else {
            mContactId = "" + System.currentTimeMillis();
        }
    }

    // emails list

    /**
     * @return the emails list.
     */
    public List<String> getEmails() {
        return mEmails;
    }

    /**
     * Add an email address to the list.
     *
     * @param anEmailAddress the email address to add
     */
    public void addEmailAdress(String anEmailAddress) {
        if (mEmails.indexOf(anEmailAddress) < 0) {
            mEmails.add(anEmailAddress);

            // test if the email address also matches to a matrix ID
            MXID mxid = PIDsRetriever.getInstance().getMXID(anEmailAddress);

            if (null != mxid) {
                mMXIDsByElement.put(anEmailAddress, mxid);
            }
        }
    }

    /**
     * @return the phone numbers list.
     */
    public List<PhoneNumber> getPhonenumbers() {
        return mPhoneNumbers;
    }

    /**
     * Add a phone number address to the list.
     *
     * @param aPn     the phone number to add
     * @param aPnE164 the E164 phone number to add
     */
    public void addPhoneNumber(String aPn, String aPnE164) {
        // sanity check
        if (!TextUtils.isEmpty(aPn)) {
            final PhoneNumber pn = new PhoneNumber(aPn, aPnE164);
            mPhoneNumbers.add(pn);

            // test if the phone number also matches to a matrix ID
            MXID mxid = PIDsRetriever.getInstance().getMXID(pn.mMsisdnPhoneNumber);
            if (null != mxid) {
                mMXIDsByElement.put(pn.mMsisdnPhoneNumber, mxid);
            }
        }
    }

    /**
     * Update the contacts with the new country code.
     */
    public void onCountryCodeUpdate() {
        if (null != mPhoneNumbers) {
            for (PhoneNumber pn : mPhoneNumbers) {
                pn.refreshE164PhoneNumber();
            }
        }
    }

    /**
     * Defines a thumbnail URI.
     *
     * @return the thumbnail uri.
     */
    public String getThumbnailUri() {
        return mThumbnailUri;
    }

    /**
     * Defines a new thumbnail uri.
     *
     * @param aThumbnailUri the new thumbnail ur.
     */
    public void setThumbnailUri(String aThumbnailUri) {
        mThumbnailUri = aThumbnailUri;
    }

    /**
     * Refresh the matched matrix from each emails / phonenumber
     */
    public void refreshMatridIds() {
        mMXIDsByElement.clear();

        PIDsRetriever pidRetriever = PIDsRetriever.getInstance();

        for (String email : getEmails()) {
            Contact.MXID mxid = pidRetriever.getMXID(email);

            if (null != mxid) {
                put(email, mxid);
            }
        }

        for (PhoneNumber pn : getPhonenumbers()) {
            Contact.MXID mxid = pidRetriever.getMXID(pn.mMsisdnPhoneNumber);

            if (null != mxid) {
                put(pn.mMsisdnPhoneNumber, mxid);
            }
        }
    }

    /**
     * Defines a matrix identifier for a dedicated medim
     *
     * @param medium the medium
     * @param mxid   the matrixId
     */
    public void put(String medium, MXID mxid) {
        if ((null != medium) && (null != mxid) && !TextUtils.isEmpty(mxid.mMatrixId)) {
            mMXIDsByElement.put(medium, mxid);
        }
    }

    /**
     * Tell if one field contains the pattern
     *
     * @param pattern the pattern to find
     * @return true if it is found.
     */
    public boolean contains(String pattern) {
        // empty pattern -> cannot match
        if (TextUtils.isEmpty(pattern)) {
            return false;
        }

        boolean matched = false;

        if (!TextUtils.isEmpty(mDisplayName)) {
            matched = (mDisplayName.toLowerCase(VectorApp.getApplicationLocale()).contains(pattern));
        }

        if (!matched) {
            for (String email : mEmails) {
                matched |= email.toLowerCase(VectorApp.getApplicationLocale()).contains(pattern);
            }
        }

        if (!matched) {
            for (PhoneNumber pn : mPhoneNumbers) {
                matched |= pn.mMsisdnPhoneNumber.toLowerCase(VectorApp.getApplicationLocale()).contains(pattern)
                        || pn.mRawPhoneNumber.toLowerCase(VectorApp.getApplicationLocale()).contains(pattern)
                        || (pn.mE164PhoneNumber != null && pn.mE164PhoneNumber.toLowerCase(VectorApp.getApplicationLocale()).contains(pattern));
            }
        }

        return matched;
    }

    /**
     * Tell whether a matrix id or an email / phonenumber has the provided prefix.
     *
     * @param prefix the prefix
     * @return true if one item matched
     */
    public boolean startsWith(String prefix) {
        // empty pattern -> cannot match
        if (TextUtils.isEmpty(prefix)) {
            return false;
        }

        ArrayList<MXID> matchedMatrixIds = new ArrayList<>();

        for (String email : mEmails) {
            if (email.startsWith(prefix)) {
                return true;
            }

            if ((null != mMXIDsByElement) && mMXIDsByElement.containsKey(email)) {
                matchedMatrixIds.add(mMXIDsByElement.get(email));
            }
        }

        // Remove the "+" and spaces from the prefix if there is any
        String cleanPrefix = prefix.replaceAll("\\s", "");
        if (cleanPrefix.startsWith("+")) {
            cleanPrefix = cleanPrefix.substring(1);
        }
        for (PhoneNumber pn : mPhoneNumbers) {
            if (pn.startsWith(cleanPrefix)) {
                return true;
            }

            if ((null != mMXIDsByElement) && mMXIDsByElement.containsKey(pn.mMsisdnPhoneNumber)) {
                matchedMatrixIds.add(mMXIDsByElement.get(pn.mMsisdnPhoneNumber));
            }
        }

        for (MXID mxid : matchedMatrixIds) {
            if ((null != mxid.mMatrixId) && mxid.mMatrixId.startsWith("@" + prefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return the medias set which could match to a matrix Id.
     */
    public Set<String> getMatrixIdMediums() {
        return mMXIDsByElement != null ? mMXIDsByElement.keySet() : Collections.<String>emptySet();
    }

    /**
     * Retrieve a MXID from an identifier
     *
     * @param media the media
     * @return the matched MXID if it exists.
     */
    public MXID getMXID(String media) {
        if (!TextUtils.isEmpty(media)) {
            return mMXIDsByElement.get(media);
        }

        return null;
    }

    /**
     * Set the display name.
     *
     * @param displayName the new display name.
     */
    public void setDisplayName(String displayName) {
        mDisplayName = displayName;
    }

    /**
     * @return teh display name
     */
    public String getDisplayName() {
        String res = mDisplayName;

        if (TextUtils.isEmpty(res)) {
            for (String email : mEmails) {
                if (!TextUtils.isEmpty(email)) {
                    return email;
                }
            }
        }

        if (TextUtils.isEmpty(res)) {
            for (PhoneNumber pn : mPhoneNumbers) {
                return pn.mRawPhoneNumber;
            }
        }

        return res;
    }

    /**
     * @return the contact id
     */
    public String getContactId() {
        return mContactId;
    }

    /**
     * Return the contact thumbnail bitmap.
     *
     * @param context the context.
     * @return the contact thumbnail bitmap.
     */
    public Bitmap getThumbnail(Context context) {
        if ((null == mThumbnail) && (null != mThumbnailUri)) {
            try {
                mThumbnail = MediaStore.Images.Media.getBitmap(context.getContentResolver(), Uri.parse(mThumbnailUri));
            } catch (Exception e) {
                Log.e(LOG_TAG, "getThumbnail " + e.getLocalizedMessage());
            }
        }

        return mThumbnail;
    }
}

