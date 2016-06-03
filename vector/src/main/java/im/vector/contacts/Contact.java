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

package im.vector.contacts;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;

import org.matrix.androidsdk.rest.model.User;
import org.w3c.dom.Text;
import org.w3c.dom.UserDataHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * A simple contact class
 */
public class Contact {
    public static class MXID {
        // the MXSession identifier
        public String mAccountId;

        // the related Matrix ID
        public String mMatrixId;

        // the user description
        public User mUser;

        public MXID(String matrixId, String accountId) {
            mMatrixId = matrixId;
            mAccountId = accountId;
            mUser = null;
        }
    }

    public  String mContactId = "";
    private String mDisplayName;
    public String mThumbnailUri = null;
    private Bitmap mThumbnail = null;

    public ArrayList<String>mPhoneNumbers = new ArrayList<String>();
    public ArrayList<String>mEmails = new ArrayList<String>();
    private HashMap<String, MXID> mMXIDsByElement = null;

    public Contact(String contactId) {
        if (null != contactId) {
            mContactId = contactId;
        } else {
            mContactId = "" + System.currentTimeMillis();
        }
    }

    /**
     * Check if some matrix IDs are linked to emails
     * @return true if some matrix IDs have been retrieved
     */
    public boolean hasMatridIds(Context context) {
        boolean localUpdateOnly = (null != mMXIDsByElement);

        // the PIDs are not yet retrieved
        if (null == mMXIDsByElement) {
            mMXIDsByElement = new HashMap<String, MXID>();
        }

        if (couldContainMatridIds()) {
            PIDsRetriever.getIntance().retrieveMatrixIds(context, this, localUpdateOnly);
        }

        return (mMXIDsByElement.size() != 0);
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
    public boolean couldContainMatridIds() {
        return (0 != mEmails.size());
    }

    /**
     * test if some fields match with the pattern
     * @param pattern
     * @return
     */
    public boolean matchWithPattern(String pattern) {
        // empty pattern -> cannot match
        if (TextUtils.isEmpty(pattern)) {
            return false;
        }

        boolean matched = false;

        if (!TextUtils.isEmpty(mDisplayName)) {
            matched = (mDisplayName.toLowerCase().indexOf(pattern) >= 0);
        }

        if (!matched) {
            for(String email : mEmails) {
                matched |= email.toLowerCase().indexOf(pattern) >= 0;
            }
        }

        return matched;
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
        return mMXIDsByElement.keySet();
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

    public void setDisplayName(String displayName) {
        mDisplayName = displayName;
    }

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
     * Return the contact thumbnail bitmap.
     * @param context the context.
     * @return the contact thumbnail bitmap.
     */
    public Bitmap getThumbnail(Context context) {

        if ((null == mThumbnail) && (null != mThumbnailUri)) {
            try {
                mThumbnail = MediaStore.Images.Media.getBitmap(context.getContentResolver(), Uri.parse(mThumbnailUri));
            } catch (Exception e) {
            }
        }

        return mThumbnail;
    }
}

