/*
 * Copyright 2019 New Vector Ltd
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

import android.os.Build
import android.text.util.Linkify
import android.widget.TextView

/**
 * Better support for auto link than the default implem
 */
fun vectorCustomLinkify(textView: TextView) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val protocols = arrayOf("http://", "https://", "rtsp://")
        val mailProtocols = arrayOf("mailto:")
        Linkify.addLinks(textView, Linkify.PHONE_NUMBERS)
        Linkify.addLinks(textView, VectorAutoLinkPatterns.instance.AUTOLINK_WEB_URL, protocols[0], protocols, urlMatchFilter, null)
        Linkify.addLinks(textView, VectorAutoLinkPatterns.instance.AUTOLINK_EMAIL, mailProtocols.first(), mailProtocols, null, null)
        Linkify.addLinks(textView, VectorAutoLinkPatterns.instance.GEO_URI, "geo:", arrayOf("geo:"), null, null)
        //order might be important (due to pruneOverlaps)
    } else {
        Linkify.addLinks(textView, Linkify.ALL)
    }
}

private val urlMatchFilter = Linkify.MatchFilter { s, start, end ->
    //    Log.d(TestLinkifyActivity::class.java.name, "FOO ${s.substring(start, end)}")
    if (start == 0) {
        return@MatchFilter true
    }
    //prevent turning the domain name in an email address into a web link.
    if (s[start - 1] == '@') {
        return@MatchFilter false
    }

    //prevent [whaoo.org] from being turned in link in foo://toto.[whaoo.org]
    // so we go back and if we found / before a white space don't highlight
    var lbehind = start - 1
    while (lbehind > 0) {
        val char = s[lbehind]
        if (char.isWhitespace()) return@MatchFilter true
        if (char == '/') return@MatchFilter false
        lbehind--
    }

    return@MatchFilter true

}

