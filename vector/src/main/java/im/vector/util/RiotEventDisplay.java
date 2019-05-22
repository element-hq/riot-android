/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
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

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.EventDisplay;
import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.interfaces.HtmlToolbox;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import im.vector.Matrix;
import im.vector.R;
import im.vector.VectorApp;
import im.vector.widgets.WidgetContent;
import im.vector.widgets.WidgetsManager;

public class RiotEventDisplay extends EventDisplay {
    private static final String LOG_TAG = RiotEventDisplay.class.getSimpleName();

    private static final Map<String, Event> mClosingWidgetEventByStateKey = new HashMap<>();

    // constructor
    public RiotEventDisplay(Context context, HtmlToolbox htmlToolbox) {
        super(context, htmlToolbox);
    }

    // constructor
    public RiotEventDisplay(Context context) {
        super(context);
    }

    /**
     * Stringify the linked event.
     *
     * @param displayNameColor the display name highlighted color.
     * @return The text or null if it isn't possible.
     */
    @Override
    public CharSequence getTextualDisplay(Integer displayNameColor, Event event, RoomState roomState) {
        CharSequence text = null;

        try {
            if (TextUtils.equals(event.getType(), WidgetsManager.WIDGET_EVENT_TYPE)) {
                JsonObject content = event.getContentAsJsonObject();

                EventContent eventContent = JsonUtils.toEventContent(event.getContentAsJsonObject());
                EventContent prevEventContent = event.getPrevContent();
                String senderDisplayName = senderDisplayNameForEvent(event, eventContent, prevEventContent, roomState);

                if (0 == content.entrySet().size()) {
                    Event closingWidgetEvent = mClosingWidgetEventByStateKey.get(event.stateKey);

                    if (null == closingWidgetEvent) {
                        List<Event> widgetEvents = roomState.getStateEvents(new HashSet<>(Arrays.asList(WidgetsManager.WIDGET_EVENT_TYPE)));

                        for (Event widgetEvent : widgetEvents) {
                            if (TextUtils.equals(widgetEvent.stateKey, event.stateKey) && !widgetEvent.getContentAsJsonObject().entrySet().isEmpty()) {
                                closingWidgetEvent = widgetEvent;
                                break;
                            }
                        }

                        if (null != closingWidgetEvent) {
                            mClosingWidgetEventByStateKey.put(event.stateKey, closingWidgetEvent);
                        }
                    }

                    String type = (null != closingWidgetEvent) ?
                            WidgetContent.toWidgetContent(closingWidgetEvent.getContentAsJsonObject()).getHumanName() : "undefined";
                    text = mContext.getString(R.string.event_formatter_widget_removed, type, senderDisplayName);
                } else {
                    String type = WidgetContent.toWidgetContent(event.getContentAsJsonObject()).getHumanName();
                    text = mContext.getString(R.string.event_formatter_widget_added, type, senderDisplayName);
                }
            } else {
                text = super.getTextualDisplay(displayNameColor, event, roomState);
            }
            if (event.getCryptoError() != null) {
                final MXSession session = Matrix.getInstance(mContext).getDefaultSession();
                VectorApp.getInstance()
                        .getDecryptionFailureTracker()
                        .reportUnableToDecryptError(event, roomState, session.getMyUserId());
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "getTextualDisplay() " + e.getMessage(), e);
        }

        return text;
    }
}
