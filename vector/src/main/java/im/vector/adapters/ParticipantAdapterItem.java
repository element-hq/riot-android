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

package im.vector.adapters;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;

import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;

import java.util.Comparator;

import im.vector.contacts.Contact;


public class ParticipantAdapterItem {
    // displayed info
    public String mDisplayName;
    public String mAvatarUrl;
    public Bitmap mAvatarBitmap;

    // user id
    public String mUserId;

    // the data is extracted either from a room member or a contact
    public RoomMember mRoomMember;
    public Contact mContact;

    // search fields
    private String mLowerCaseDisplayName;
    private String mLowerCaseMatrixId;

    private String mComparisonDisplayName;
    private static final String mTrimRegEx = "[_!~`@#$%^&*\\-+();:=\\{\\}\\[\\],.<>?]";

    // auto reference fields to speed up search
    public int mReferenceGroupPosition = -1;
    public int mReferenceChildPosition = -1;

    public ParticipantAdapterItem(RoomMember member) {
        mDisplayName = member.getName();
        mAvatarUrl = member.avatarUrl;
        mUserId = member.getUserId();

        mRoomMember = member;
        mContact = null;

        initSearchByPatternFields();
    }

    public ParticipantAdapterItem(User user) {
        mDisplayName = TextUtils.isEmpty(user.displayname) ? user.user_id : user.displayname;
        mUserId = user.user_id;
        mAvatarUrl = user.getAvatarUrl();
        initSearchByPatternFields();
    }

    public ParticipantAdapterItem(Contact contact, Context context) {
        mDisplayName = contact.getDisplayName();
        mAvatarBitmap = contact.getThumbnail(context);

        mUserId = null;
        mRoomMember = null;

        mContact = contact;

        initSearchByPatternFields();
    }

    public ParticipantAdapterItem(String displayName, String avatarUrl, String userId) {
        mDisplayName = displayName;
        mAvatarUrl = avatarUrl;
        mUserId = userId;

        initSearchByPatternFields();
    }

    /**
     * Init the search by pattern fields
     */
    private void initSearchByPatternFields() {
        if (!TextUtils.isEmpty(mDisplayName)) {
            mLowerCaseDisplayName = mDisplayName.toLowerCase();
        }

        if (!TextUtils.isEmpty(mUserId)) {

            int sepPos = mUserId.indexOf(":");

            if (sepPos > 0) {
                mLowerCaseMatrixId = mUserId.substring(0, sepPos).toLowerCase();
            }
        }
    }

    /**
     * @return a comparable displayname i.e. some characters are removed.
     */
    public String getComparisonDisplayName() {
        if (null == mComparisonDisplayName) {
            if (!TextUtils.isEmpty(mDisplayName)) {
                mComparisonDisplayName = mDisplayName;
            } else {
                mComparisonDisplayName = mUserId;
            }

            mComparisonDisplayName = mComparisonDisplayName.replaceAll(mTrimRegEx, "");

            if (null == mComparisonDisplayName) {
                mComparisonDisplayName = "";
            }
        }

        return mComparisonDisplayName;
    }

    // Comparator to order members alphabetically
    public static Comparator<ParticipantAdapterItem> alphaComparator = new Comparator<ParticipantAdapterItem>() {
        @Override
        public int compare(ParticipantAdapterItem part1, ParticipantAdapterItem part2) {
            String lhs = part1.getComparisonDisplayName();
            String rhs = part2.getComparisonDisplayName();

            if (lhs == null) {
                return -1;
            }
            else if (rhs == null) {
                return 1;
            }

            return String.CASE_INSENSITIVE_ORDER.compare(lhs, rhs);
        }
    };

    /**
     * Test if a room member matches with a pattern.
     * The check is done with the displayname and the userId.
     * @param aPattern the pattern to search.
     * @return true if it matches.
     */
    public boolean matchWithPattern(String aPattern) {
        if (TextUtils.isEmpty(aPattern)) {
            return false;
        }

        boolean res = false;

        if (/*!res &&*/ !TextUtils.isEmpty(mLowerCaseDisplayName)) {
            res = mLowerCaseDisplayName.indexOf(aPattern) > -1;
        }

        if (!res && !TextUtils.isEmpty(mLowerCaseMatrixId)) {
            res = mLowerCaseMatrixId.indexOf(aPattern) > -1;
        }

        // the room member class only checks the matrixId and the displayname
        // avoid testing twice
        /*if (!res && (null != mRoomMember)) {
            res = mRoomMember.matchWithPattern(aPattern);
        }*/

        if (!res && (null != mContact)) {
            res = mContact.matchWithPattern(aPattern);
        }

        return res;
    }

    /**
     * Test if a room member fields matches with a regex
     * The check is done with the displayname and the userId.
     * @param aRegEx the pattern to search.
     * @return true if it matches.
     */
    public boolean matchWithRegEx(String aRegEx) {

        if (TextUtils.isEmpty(aRegEx)) {
            return false;
        }

        boolean res = false;

        if (/*!res &&*/ !TextUtils.isEmpty(mDisplayName)) {
            res = mDisplayName.matches(aRegEx);
        }

        if (!res && !TextUtils.isEmpty(mUserId)) {
            res = mUserId.matches(aRegEx);
        }

        if (!res && (null != mRoomMember)) {
            res = mRoomMember.matchWithRegEx(aRegEx);
        }

        if (!res && (null != mContact)) {
            res = mContact.matchWithRegEx(aRegEx);
        }

        return res;
    }
}
