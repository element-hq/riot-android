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
import android.text.TextUtils;

import org.matrix.androidsdk.rest.model.RoomMember;

import java.util.Comparator;

import im.vector.contacts.Contact;


public class ParticipantAdapterItem {
    // displayed info
    public String mDisplayName;
    public String mAvatarUrl;

    // user id
    public String mUserId;

    // the data is extracted either from a room member or a contact
    public RoomMember mRoomMember;
    public Contact mContact;

    public ParticipantAdapterItem(RoomMember member) {
        mDisplayName = member.getName();
        mAvatarUrl = member.avatarUrl;
        mUserId = member.getUserId();

        mRoomMember = member;
        mContact = null;
    }

    public ParticipantAdapterItem(Contact contact) {
    }

    public ParticipantAdapterItem(String displayName, String avatarUrl, String userId) {
        mDisplayName = displayName;
        mAvatarUrl = avatarUrl;
        mUserId = userId;
    }

    // Comparator to order members alphabetically
    public static Comparator<ParticipantAdapterItem> alphaComparator = new Comparator<ParticipantAdapterItem>() {
        @Override
        public int compare(ParticipantAdapterItem part1, ParticipantAdapterItem part2) {
            String lhs = part1.mDisplayName;
            String rhs = part2.mDisplayName;

            if (lhs == null) {
                return -1;
            }
            else if (rhs == null) {
                return 1;
            }
            if (lhs.startsWith("@")) {
                lhs = lhs.substring(1);
            }
            if (rhs.startsWith("@")) {
                rhs = rhs.substring(1);
            }
            return String.CASE_INSENSITIVE_ORDER.compare(lhs, rhs);
        }
    };

    /**
     * Test if a room memmber matches with a pattern.
     * The check is done with the displayname and the userId.
     * @param aPattern the pattern to search.
     * @return true if it matches.
     */
    public boolean matchWith(String aPattern) {
        if (TextUtils.isEmpty(aPattern) || TextUtils.isEmpty(aPattern.trim())) {
            return false;
        }
        String regEx = "(?i:.*" + aPattern.trim() + ".*)";
        boolean res = false;

        if (!TextUtils.isEmpty(mDisplayName)) {
            res = mDisplayName.matches(regEx);
        }

        if (!res && (null != mRoomMember)) {
            res = mRoomMember.matchWith(aPattern);
        }

        if (!res && (null != mContact)) {
            res = mContact.matchWithPattern(aPattern);
        }

        return res;
    }
}
