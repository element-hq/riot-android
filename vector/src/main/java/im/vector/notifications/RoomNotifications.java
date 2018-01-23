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

/**
 * RoomNotifications
 */
public class RoomNotifications implements Parcelable {
    private static final String LOG_TAG = RoomNotifications.class.getSimpleName();

    public String mRoomId = "";
    public String mRoomAlias = "";
    public String mRoomName = "";
    public String mMessageHeader = "";
    public CharSequence mLatestMessage = "";
    public long mEventTs = -1;

    public String mSenderName = "";
    public boolean mIsInvited = false;

    public int mUnreadMessagesCount = -1;

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
        out.writeString(mRoomAlias);
        out.writeString(mRoomName);
        out.writeString(mMessageHeader);
        TextUtils.writeToParcel(mLatestMessage, out, 0);
        out.writeLong(mEventTs);

        out.writeString(mSenderName);
        out.writeInt(mIsInvited ? 0 : 1);
        out.writeInt(mUnreadMessagesCount);
    }

    /**
     * Creator from a parcel
     *
     * @param in the parcel
     */
    private RoomNotifications(Parcel in) {
        mRoomId = in.readString();
        mRoomAlias = in.readString();
        mRoomName = in.readString();
        mMessageHeader = in.readString();
        mLatestMessage = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        mEventTs = in.readLong();
        mSenderName = in.readString();
        mIsInvited = (0 == in.readInt()) ? false : true;
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
