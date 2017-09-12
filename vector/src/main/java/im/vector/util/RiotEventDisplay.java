/*
 * Copyright 2016 OpenMarket Ltd
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
package im.vector.util;

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;

import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import im.vector.R;

import im.vector.widgets.WidgetManager;

public class RiotEventDisplay extends EventDisplay {
    private static final String LOG_TAG = "RiotEventDisplay";

    // constructor
    public RiotEventDisplay(Context context, Event event, RoomState roomState) {
        super(context, event, roomState);
    }

    /**
     * Stringify the linked event.
     *
     * @param displayNameColor the display name highlighted color.
     * @return The text or null if it isn't possible.
     */
    public CharSequence getTextualDisplay(Integer displayNameColor) {
        CharSequence text = null;

        try {
            if (TextUtils.equals(mEvent.getType(), WidgetManager.WIDGET_EVENT_TYPE)) {
                JsonObject content = mEvent.getContentAsJsonObject();

                EventContent eventContent = JsonUtils.toEventContent(mEvent.getContentAsJsonObject());
                EventContent prevEventContent = mEvent.getPrevContent();
                String senderDisplayName = senderDisplayNameForEvent(mEvent, eventContent, prevEventContent, mRoomState);

                if (0 == content.entrySet().size()) {
                    List<Event> widgetEvents = mRoomState.getStateEvents(new HashSet<>(Arrays.asList(WidgetManager.WIDGET_EVENT_TYPE)));

                    for (Event widgetEvent : widgetEvents) {
                        if (TextUtils.equals(widgetEvent.stateKey, mEvent.stateKey)) {
                            String type = widgetEvent.getContentAsJsonObject().get("type").getAsString();

                            if (TextUtils.equals(type, WidgetManager.WIDGET_TYPE_JITSI)) {
                                text = mContext.getString(R.string.event_formatter_jitsi_widget_removed, senderDisplayName);
                                break;
                            }
                        }
                    }
                } else {
                    String type = mEvent.getContentAsJsonObject().get("type").getAsString();

                    if (TextUtils.equals(type, WidgetManager.WIDGET_TYPE_JITSI)) {
                        text = mContext.getString(R.string.event_formatter_jitsi_widget_added, senderDisplayName);
                    }
                }
            } else {
                text = super.getTextualDisplay(displayNameColor);
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "getTextualDisplay() " + e.getMessage());
        }

        return text;
    }
}
