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

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.adapters.RoomMembersAdapter;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;

import im.vector.R;

import java.util.HashMap;

/**
 * An adapter which can display room information.
 */
public class VectorRoomMembersAdapter extends RoomMembersAdapter {

    public VectorRoomMembersAdapter(Context context, HomeserverConnectionConfig hsConfig, int layoutResourceId, RoomState roomState, MXMediasCache mediasCache, HashMap<String, String> membershipStrings) {
        super(context, hsConfig, layoutResourceId, roomState, mediasCache, membershipStrings);
    }

    public int lastSeenTextColor() {
        return mContext.getResources().getColor(R.color.member_list_last_seen_text);
    }

    public int presenceOnlineColor() {
        return mContext.getResources().getColor(R.color.presence_online);
    }

    public int presenceOfflineColor() {
        return mContext.getResources().getColor(R.color.presence_offline);
    }

    public int presenceUnavailableColor() {
        return mContext.getResources().getColor(R.color.presence_unavailable);
    }
}
