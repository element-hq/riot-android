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

import android.support.v4.text.util.LinkifyCompat
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.text.util.Linkify
import android.widget.TextView

/**
 * Better support for auto link than the default implem
 */
fun TextView.vectorCustomLinkify() {

    val protocols = arrayOf("http://", "https://", "rtsp://")
    val mailProtocols = arrayOf("mailto:")

    val currentSpan = SpannableString.valueOf(text)
    LinkifyCompat.addLinks(currentSpan, VectorAutoLinkPatterns.AUTOLINK_WEB_URL, protocols[0], protocols, urlMatchFilter, null)
    LinkifyCompat.addLinks(currentSpan, VectorAutoLinkPatterns.AUTOLINK_EMAIL, mailProtocols.first(), mailProtocols, null, null)
    LinkifyCompat.addLinks(currentSpan, VectorAutoLinkPatterns.GEO_URI, "geo:", arrayOf("geo:"), geoMatchFilter, null)

    //this is a bit hacky but for phone numbers we use the framework but it's too lenient
    val spannable = SpannableString(text) //must be a new one
    Linkify.addLinks(spannable, Linkify.PHONE_NUMBERS)
    val pnSpan = spannable.getSpans(0, spannable.length, URLSpan::class.java)
    for (span in pnSpan) {
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        if (end - start > 6) { //Do not match under 7 digit
            currentSpan.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    //maybe need to prune overlaps? tried to make some but didn't find

    text = currentSpan
    addLinkMovementMethod(this)

}

private val urlMatchFilter = Linkify.MatchFilter { s, start, _ ->
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

//Exclude short match that don't have geo: prefix, e.g do not highlight things like 1,2
private val geoMatchFilter = Linkify.MatchFilter { s, start, end ->
    if (s[start] != 'g') { //doesn't start with geo:
        return@MatchFilter end - start > 12
    }
    return@MatchFilter true
}

private fun addLinkMovementMethod(t: TextView) {
    val m = t.movementMethod

    if (m == null || m !is LinkMovementMethod) {
        if (t.linksClickable) {
            t.movementMethod = LinkMovementMethod.getInstance()
        }
    }
}
