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

package im.vector.preference;

import android.content.Context;
import android.util.AttributeSet;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;

import im.vector.util.VectorUtils;

/**
 * Specialized class to target a Room avatar preference.
 * Based don the avatar preference class it redefines refreshAvatar() and
 * add the new method  setConfiguration().
 */
public class RoomAvatarPreference extends UserAvatarPreference {

    private Room mRoom;

    public RoomAvatarPreference(Context context) {
        super(context);
        mContext = context;
    }

    public RoomAvatarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public RoomAvatarPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    @Override
    public void refreshAvatar() {
        if ((null !=  mAvatarView) && (null != mRoom)) {
            VectorUtils.loadRoomAvatar(mContext, mSession, mAvatarView, mRoom);
        }
    }

    public void setConfiguration(MXSession aSession, Room aRoom) {
        mSession = aSession;
        mRoom = aRoom;
        refreshAvatar();
    }

}