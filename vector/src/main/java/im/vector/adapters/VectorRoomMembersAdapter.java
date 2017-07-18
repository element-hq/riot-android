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
import android.support.annotation.ColorInt;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.adapters.RoomMembersAdapter;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;

import im.vector.R;
import im.vector.util.ThemeUtils;

import java.util.HashMap;

/**
 * An adapter which can display room information.
 */
public class VectorRoomMembersAdapter extends RoomMembersAdapter {

    public VectorRoomMembersAdapter(Context context, HomeserverConnectionConfig hsConfig, int layoutResourceId, RoomState roomState, MXMediasCache mediasCache, HashMap<String, String> membershipStrings) {
        super(context, hsConfig, layoutResourceId, roomState, mediasCache, membershipStrings);
    }

    public @ColorInt  int lastSeenTextColor() {
        return ThemeUtils.getColor(mContext, R.attr.member_list_last_seen_text);
    }

    public @ColorInt  int presenceOnlineColor() {
        return ThemeUtils.getColor(mContext, R.attr.presence_online);
    }

    public @ColorInt  int presenceOfflineColor() {
        return ThemeUtils.getColor(mContext, R.attr.presence_offline);
    }

    public @ColorInt int presenceUnavailableColor() {
        return ThemeUtils.getColor(mContext, R.attr.presence_unavailable);
    }
}
