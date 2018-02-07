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

import org.matrix.androidsdk.rest.model.bingrules.BingRule;

/**
 * Define a notified event
 * i.e the matched bing rules
 */
public class NotifiedEvent {
    public final BingRule mBingRule;
    public final String mRoomId;
    public final String mEventId;
    public final long mOriginServerTs;

    public NotifiedEvent(String roomId, String eventId, BingRule bingRule, long originServerTs) {
        mRoomId = roomId;
        mEventId = eventId;
        mBingRule = bingRule;
        mOriginServerTs = originServerTs;
    }
}
