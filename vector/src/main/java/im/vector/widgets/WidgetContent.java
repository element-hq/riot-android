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

import android.text.TextUtils;

import com.google.gson.JsonElement;

import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;

import java.io.Serializable;
import java.util.Map;

public class WidgetContent implements Serializable {
    private static final String LOG_TAG = "WidgetContent";

    // widget URL
    public String url;

    // wiget type
    public String type;

    // widget data
    public Map<String, Object> data;

    // widget "human name"
    public String name;

    // widget id
    public String id;

    // creator id
    public String creatorUserId;

    /**
     * @return the human name
     */
    public String getHumanName() {
        if (!TextUtils.isEmpty(name)) {
            return name + " widget";
        } else if (!TextUtils.isEmpty(type)) {
            if (type.contains("widget")) {
                return type;
            } else if (null != id) {
                return type + " " + id;
            } else {
                return type + " widget";
            }
        } else {
            return "Widget " + id;
        }
    }

    /**
     * Convert a json object into a WidgetContent instance
     * @param jsonObject
     * @return
     */
    public static WidgetContent toWidgetContent(JsonElement jsonObject) {
        try {
            return JsonUtils.getGson(false).fromJson(jsonObject, WidgetContent.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toWidgetContent() : failed " + e.getMessage());
        }

        return new WidgetContent();
    }
}

