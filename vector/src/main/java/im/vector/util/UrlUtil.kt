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

import java.net.MalformedURLException
import java.net.URL

/**
 * Schemes
 */
private const val HTTP_SCHEME = "http://"
const val HTTPS_SCHEME = "https://"

/**
 * Remove the http schemes from the URl passed in parameter
 *
 * @param aUrl URL to be parsed
 * @return the URL with the scheme removed
 */
fun removeUrlScheme(aUrl: String?): String? {
    var urlRetValue = aUrl

    if (null != aUrl) {
        // remove URL scheme
        if (aUrl.startsWith(HTTP_SCHEME)) {
            urlRetValue = aUrl.substring(HTTP_SCHEME.length)
        } else if (aUrl.startsWith(HTTPS_SCHEME)) {
            urlRetValue = aUrl.substring(HTTPS_SCHEME.length)
        }
    }

    return urlRetValue
}

fun extractDomain(aUrl: String?): String? {
    try {
        return aUrl?.let {  URL(it).host }
    } catch (e : MalformedURLException) {
        return null
    }
}