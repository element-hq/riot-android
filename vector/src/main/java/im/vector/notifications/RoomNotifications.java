/*
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

package im.vector.notifications;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.util.Comparator;

/**
 * RoomNotifications
 */
public class RoomNotifications implements Parcelable {
    private static final String LOG_TAG = RoomNotifications.class.getSimpleName();

    String mRoomId = "";
    String mRoomName = "";
    String mMessageHeader = "";
    CharSequence mMessagesSummary = "";
    long mLatestEventTs = -1;

    String mSenderName = "";
    int mUnreadMessagesCount = -1;

    /**
     * NotificationDisplay comparator
     */
    static final Comparator<RoomNotifications> mRoomNotificationsComparator = new Comparator<RoomNotifications>() {
        @Override
        public int compare(RoomNotifications lhs, RoomNotifications rhs) {
            long t0 = lhs.mLatestEventTs;
            long t1 = rhs.mLatestEventTs;

            if (t0 > t1) {
                return -1;
            } else if (t0 < t1) {
                return +1;
            }
            return 0;
        }
    };

    // empty constructor
    public RoomNotifications() {
    }

    /*
     * *********************************************************************************************
     * Parcelable
     * *********************************************************************************************
     */
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mRoomId);
        out.writeString(mRoomName);
        out.writeString(mMessageHeader);
        TextUtils.writeToParcel(mMessagesSummary, out, 0);
        out.writeLong(mLatestEventTs);

        out.writeString(mSenderName);
        out.writeInt(mUnreadMessagesCount);
    }

    /**
     * Creator from a parcel
     *
     * @param in the parcel
     */
    private RoomNotifications(Parcel in) {
        mRoomId = in.readString();
        mRoomName = in.readString();
        mMessageHeader = in.readString();
        mMessagesSummary = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        mLatestEventTs = in.readLong();
        mSenderName = in.readString();
        mUnreadMessagesCount = in.readInt();
    }

    public final static Parcelable.Creator<RoomNotifications> CREATOR
            = new Parcelable.Creator<RoomNotifications>() {

        public RoomNotifications createFromParcel(Parcel p) {
            return new RoomNotifications(p);
        }

        public RoomNotifications[] newArray(int size) {
            return new RoomNotifications[size];
        }
    };
}
