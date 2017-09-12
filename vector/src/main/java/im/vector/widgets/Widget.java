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

package im.vector.widgets;

import android.content.pm.PackageInstaller;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.Event;

import java.util.HashMap;
import java.util.Map;

public class Widget {
    private static final String LOG_TAG = "Widget";

    // JSON parser
    private static final Gson mGson = new Gson();

    private String mWidgetId;
    private Event mWidgetEvent;
    private MXSession mSession;

    private String mType;
    private String mUrl;
    private String mName;
    private Map<String, Object> mData;

    /**
     * Constructor
     *
     * @param session     the session
     * @param widgetEvent the widget event
     */
    public Widget(MXSession session, Event widgetEvent) throws Exception {
        if (!TextUtils.equals(widgetEvent.type, WidgetManager.WIDGET_EVENT_TYPE)) {
            throw new Exception("unsupported event type " + widgetEvent.type);
        }

        mWidgetId = widgetEvent.stateKey;
        mWidgetEvent = widgetEvent;
        mSession = session;

        JsonObject contentAsJson = widgetEvent.getContentAsJsonObject();

        if (null != contentAsJson.get("type")) {
            mType = contentAsJson.get("type").getAsString();
        } else {
            mType = null;
        }

        if (null != contentAsJson.get("url")) {
            mUrl = contentAsJson.get("url").getAsString();
        } else {
            mUrl = null;
        }

        if (null != contentAsJson.get("name")) {
            mName = contentAsJson.get("name").getAsString();
        } else {
            mName = null;
        }

        if (null != contentAsJson.get("data")) {
            mData = new HashMap<>();
            mData = (Map<String, Object>) mGson.fromJson(contentAsJson.get("data"), mData.getClass());
        } else {
            mData = null;
        }

        if (null != mUrl) {
            // Format the url string with user data
            mUrl = mUrl.replace("$matrix_user_id", mSession.getMyUserId());

            String displayName = mSession.getMyUser().displayname;
            mUrl = mUrl.replace("$matrix_display_name", (null != displayName) ? displayName : mSession.getMyUserId());

            String avatarUrl = mSession.getMyUser().getAvatarUrl();
            mUrl = mUrl.replace("$matrix_avatar_url", (null != avatarUrl) ? avatarUrl : "");
        }
    }

    /**
     * Tells if the widget is active
     *
     * @return true if the widget is active
     */
    public boolean isActive() {
        return (null != mType) && (null != mUrl);
    }

    /**
     * Getters
     */
    public String getWidgetId() {
        return mWidgetId;
    }

    public Event getWidgetEvent() {
        return mWidgetEvent;
    }

    public String getRoomId() {
        return mWidgetEvent.roomId;
    }

    public MXSession getSession() {
        return mSession;
    }

    public String getType() {
        return mType;
    }

    public String getUrl() {
        return mUrl;
    }

    public String getName() {
        return mName;
    }

    public Map<String, Object> getData() {
        return mData;
    }

    @Override
    public String toString() {
        return "<Widget: " + this + "p> id: " + getWidgetId() + " - type: " + getType() + " - name: " + getName() + " - url: " + getUrl();
    }
}
