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

package org.matrix.console.contacts;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A simple contact class
 */
public class Contact {
    public static class MXID {
        public String mMatrixId;
        public String mAccountId;

        public MXID(String matrixId, String accountId) {
            mMatrixId = matrixId;
            mAccountId = accountId;
        }
    }

    public String mContactId;
    public String mDisplayName;
    private String mUpperCaseDisplayName = "";
    private String mLowerCaseDisplayName = "";
    public String mThumbnailUri;
    public Bitmap mThumbnail = null;

    public ArrayList<String>mPhoneNumbers = new ArrayList<String>();
    public ArrayList<String>mEmails = new ArrayList<String>();
    private HashMap<String, MXID> mMXIDsByElement = null;

    /**
     * Check if some matrix IDs are linked to emails
     * @return true if some matrix IDs have been retrieved
     */
    public boolean hasMatridIds(Context context) {
        Boolean localUpdateOnly = (null != mMXIDsByElement);

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

    // assume that the search is performed on all the existing contacts
    // so apply upper / lower case only once
    static String mCurrentPattern = "";
    static String mUpperCasePattern = "";
    static String mLowerCasePattern = "";

    /**
     * test if some fields match with the pattern
     * @param pattern
     * @return
     */
    public boolean matchWithPattern(String pattern) {
        // no pattern -> true
        if (TextUtils.isEmpty(pattern)) {
            mCurrentPattern = "";
            mUpperCasePattern = "";
            mLowerCasePattern = "";
        }

        // no display name
        if (TextUtils.isEmpty(mDisplayName)) {
            return false;
        }

        if (TextUtils.isEmpty(mUpperCaseDisplayName)) {
            mUpperCaseDisplayName = mDisplayName.toLowerCase();
            mLowerCaseDisplayName = mDisplayName.toUpperCase();
        }

        if (!pattern.equals(mCurrentPattern)) {
            mCurrentPattern = pattern;
            mUpperCasePattern = pattern.toUpperCase();
            mLowerCasePattern = pattern.toLowerCase();
        }

        return (mUpperCaseDisplayName.indexOf(mUpperCasePattern) >= 0) || (mLowerCaseDisplayName.indexOf(mUpperCasePattern) >= 0);
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
}

