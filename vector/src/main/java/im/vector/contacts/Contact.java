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

package im.vector.contacts;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * A simple contact class
 */
public class Contact implements java.io.Serializable {

    private static final String LOG_TAG = "Contact";

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
         * @param matrixId the matrix id
         * @param accountId the account id
         */
        public MXID(String matrixId, String accountId) {
            mMatrixId = (null == matrixId) ? "" : matrixId;
            mAccountId = accountId;
            mUser = null;
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
    private final ArrayList<String> mPhoneNumbers = new ArrayList<>();

    // emails list
    private final ArrayList<String> mEmails = new ArrayList<>();

    // MXID by email address
    private HashMap<String, MXID> mMXIDsByElement = new HashMap<>();

    /**
     * Constructor
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
     * @param anEmailAddress the email address to add
     */
    public void addEmailAdress(String anEmailAddress) {
        if (mEmails.indexOf(anEmailAddress) < 0) {
            mEmails.add(anEmailAddress);

            // test if the email address also matches to a matrix ID
            MXID mxid =  PIDsRetriever.getInstance().getMXID(anEmailAddress);

            if (null != mxid) {
                mMXIDsByElement.put(anEmailAddress, mxid);
            }
        }
    }

    /**
     * @return the phone numbers list.
     */
    public List<String> getPhonenumbers() {
        return mPhoneNumbers;
    }

    /**
     * Add a phone number address to the list.
     * @param aPhonenumber the phone number to add
     */
    public void addPhonenumber(String aPhonenumber) {
        if (mPhoneNumbers.indexOf(aPhonenumber) < 0) {
            mPhoneNumbers.add(aPhonenumber);
        }
    }

    /**
     * Defines a thumbnail URI.
     * @return the thumbnail uri.
     */
    public String getThumbnailUri() {
        return mThumbnailUri;
    }

    /**
     * Defines a new thumbnail uri.
     * @param aThumbnailUri the new thumbnail ur.
     */
    public void setThumbnailUri(String aThumbnailUri) {
        mThumbnailUri = aThumbnailUri;
    }

    /**
     * Refresh the matched matrix from each emails
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
    }

    /**
     * Defines a matrix identifier for a dedicated pattern
     * @param email the pattern
     * @param mxid the matrixId
     */
    public void put(String email, MXID mxid) {
        if ((null != email) && (null != mxid) && !TextUtils.isEmpty(mxid.mMatrixId)) {
            mMXIDsByElement.put(email, mxid);
        }
    }

    /**
     * Check if the contact could contain some matrix Ids
     * @return true if the contact could contain some matrix IDs
     */
    private boolean couldContainMatridIds() {
        return (0 != mEmails.size());
    }

    /**
     * Tell if one field contains the pattern
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
            matched = (mDisplayName.toLowerCase().contains(pattern));
        }

        if (!matched) {
            for(String email : mEmails) {
                matched |= email.toLowerCase().contains(pattern);
            }
        }

        return matched;
    }

    /**
     * Tell whether a matrix id/email has the provided prefix.
     * @param prefix the prefix
     * @return true if one item matched
     */
    public boolean startsWith(String prefix) {
        // empty pattern -> cannot match
        if (TextUtils.isEmpty(prefix)) {
            return false;
        }

        for(String email : mEmails) {
            if (email.startsWith(prefix)) {
                return true;
            }

            if ((null != mMXIDsByElement) && mMXIDsByElement.containsKey(email)) {
                MXID mxid = mMXIDsByElement.get(email);

                if ((null != mxid.mMatrixId) && mxid.mMatrixId.startsWith("@" + prefix)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * test if some fields match with the reg ex.
     * @param aRegEx the reg ex.
     * @return true if it matches
     */
    public boolean matchWithRegEx(String aRegEx) {
        // empty pattern -> cannot match
        if (TextUtils.isEmpty(aRegEx)) {
            return false;
        }

        boolean matched = false;

        if (!TextUtils.isEmpty(mDisplayName)) {
            matched = mDisplayName.matches(aRegEx);
        }

        if (!matched) {
            for(String email : mEmails) {
                matched |= email.matches(aRegEx);
            }
        }

        return matched;
    }

    /**
     * @return the medias set which could match to a matrix Id.
     */
    public Set<String> getMatrixIdMedias() {
        return mMXIDsByElement != null ? mMXIDsByElement.keySet() : Collections.<String>emptySet();
    }

    /**
     * Retrieve a MXID from an identifier
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
     * Returns the first retrieved matrix ID.
     * @return the first retrieved matrix ID.
     */
    public MXID getFirstMatrixId() {
        if (mMXIDsByElement.size() != 0) {
            return mMXIDsByElement.values().iterator().next();
        } else {
            return null;
        }
    }

    /**
     * Set the display name.
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
            for(String email : mEmails) {
                if (!TextUtils.isEmpty(email)) {
                    return email;
                }
            }
        }

        if (TextUtils.isEmpty(res)) {
            for(String pn : mPhoneNumbers) {
                if (!TextUtils.isEmpty(pn)) {
                    return pn;
                }
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

