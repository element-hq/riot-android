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

import java.util.ArrayList;
import java.util.List;

/**
 * RoomsNotifications
 */
public class RoomsNotifications implements Parcelable {
    private static final String LOG_TAG = RoomsNotifications.class.getSimpleName();

    // the session id
    public String mSessionId = "";

    // the notifications list
    public List<RoomNotifications> mRoomNotifications = new ArrayList<>();

    // messages list
    public List<CharSequence> mReversedMessagesList = new ArrayList<>();

    public RoomsNotifications() {
    }

    /*
     * *********************************************************************************************
     * Parcelable
     * *********************************************************************************************
     */

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mSessionId);

        RoomNotifications[] roomNotifications = new RoomNotifications[mRoomNotifications.size()];
        mRoomNotifications.toArray(roomNotifications);
        out.writeArray(roomNotifications);

        out.writeInt(mReversedMessagesList.size());
        for (CharSequence sequence : mReversedMessagesList) {
            TextUtils.writeToParcel(sequence, out, 0);
        }
    }

    /**
     * Constructor from the parcel.
     *
     * @param in the parcel
     */
    private RoomsNotifications(Parcel in) {
        mSessionId = in.readString();

        Object[] roomNotificationsAasVoid = in.readArray(RoomNotifications.class.getClassLoader());
        for (Object object : roomNotificationsAasVoid) {
            mRoomNotifications.add((RoomNotifications) object);
        }

        int count = in.readInt();
        mReversedMessagesList = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            mReversedMessagesList.add(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
        }
    }

    /**
     * Parcelable creator
     */
    public final static Parcelable.Creator<RoomsNotifications> CREATOR = new Parcelable.Creator<RoomsNotifications>() {
        public RoomsNotifications createFromParcel(Parcel p) {
            return new RoomsNotifications(p);
        }

        public RoomsNotifications[] newArray(int size) {
            return new RoomsNotifications[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    /*
     * *********************************************************************************************
     * Serialisation
     * *********************************************************************************************
    */

    /**
     * @return byte[] from the class
     */
    public byte[] marshall() {
        Parcel parcel = Parcel.obtain();
        writeToParcel(parcel, 0);
        byte[] bytes = parcel.marshall();
        parcel.recycle();
        return bytes;
    }

    /**
     * Create a RoomsNotifications instance from a bytes[].
     *
     * @param bytes the bytes array
     */
    public RoomsNotifications(byte[] bytes) {
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);

        RoomsNotifications roomsNotifications = RoomsNotifications.CREATOR.createFromParcel(parcel);
        mSessionId = roomsNotifications.mSessionId;
        mRoomNotifications = roomsNotifications.mRoomNotifications;
        mReversedMessagesList = roomsNotifications.mReversedMessagesList;

        parcel.recycle();
    }
}
