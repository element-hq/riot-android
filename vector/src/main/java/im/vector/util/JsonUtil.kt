/*
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

package im.vector.util

import com.google.gson.reflect.TypeToken
import org.matrix.androidsdk.util.JsonUtils
import org.matrix.androidsdk.util.Log
import java.util.*


/**
 * Convert an object to a map
 *
 * @param `object` the object to convert
 * @return the event as a map
 */
fun Any.toJsonMap(): Map<String, Any>? {
    val gson = JsonUtils.getGson(false)
    var objectAsMap: Map<String, Any>? = null

    try {
        val stringifiedEvent = gson.toJson(this)
        objectAsMap = gson.fromJson<Map<String, Any>>(stringifiedEvent, object : TypeToken<HashMap<String, Any>>() {

        }.type)
    } catch (e: Exception) {
        Log.e("TAG", "## Any.toJsonMap() failed " + e.message)
    }

    return objectAsMap
}