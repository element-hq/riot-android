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

package im.vector.webview

import org.matrix.androidsdk.core.Log

private const val TAG = "DefaultWebViewEventListener"

/**
 * This class is the default implementation of WebViewEventListener.
 * It can be used with delegation pattern
 */

class DefaultWebViewEventListener : WebViewEventListener {

    override fun pageWillStart(url: String) {
        Log.v(TAG, "On page will start: $url")
    }

    override fun onPageStarted(url: String) {
        Log.d(TAG, "On page started: $url")
    }

    override fun onPageFinished(url: String) {
        Log.d(TAG, "On page finished: $url")
    }

    override fun onPageError(url: String, errorCode: Int, description: String) {
        Log.e(TAG, "On received error: $url - errorCode: $errorCode - message: $description")
    }

    override fun shouldOverrideUrlLoading(url: String): Boolean {
        Log.v(TAG, "Should override url: $url")
        return false
    }
}