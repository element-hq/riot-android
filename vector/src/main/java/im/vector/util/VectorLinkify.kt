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
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.text.util.Linkify
import android.widget.TextView

/**
 * Better support for auto link than the default implementation
 */
fun TextView.vectorCustomLinkify() {

    val currentSpan = SpannableString.valueOf(text)

    //Use the framework first, the found span can then be manipulated if needed
    LinkifyCompat.addLinks(currentSpan, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)

    //we might want to modify some matches
    val createdSpans = ArrayList<LinkSpec>()
    currentSpan.forEachSpanIndexed { _, urlSpan, start, end ->
        currentSpan.removeSpan(urlSpan)
        //check trailing space
        if (end < currentSpan.length - 1 && currentSpan[end] == '/') {
            //modify the span to include the slash
            val spec = LinkSpec(URLSpan(urlSpan.url + "/"), start, end + 1)
            createdSpans.add(spec)
            return@forEachSpanIndexed
        }
        //Try to do something for ending ) issues/3020
        if (currentSpan[end - 1] == ')') {
            var lbehind = end - 2
            var isFullyContained = 1
            while (lbehind > start) {
                val char = currentSpan[lbehind]
                if (char == '(') isFullyContained -= 1
                if (char == ')') isFullyContained += 1
                lbehind--
            }
            if (isFullyContained != 0) {
                //In this case we will return false to match, and manually add span if we want?
                val span = URLSpan(currentSpan.substring(start, end - 1))
                val spec = LinkSpec(span, start, end - 1)
                createdSpans.add(spec)
                return@forEachSpanIndexed
            }
        }

        createdSpans.add(LinkSpec(URLSpan(urlSpan.url), start, end))
    }

    for (spec in createdSpans) {
        currentSpan.setSpan(spec.span, spec.start, spec.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

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

    addLinkMovementMethod()
}

private data class LinkSpec(val span: URLSpan,
                            val start: Int,
                            val end: Int)

//Exclude short match that don't have geo: prefix, e.g do not highlight things like 1,2
private val geoMatchFilter = Linkify.MatchFilter { s, start, end ->
    if (s[start] != 'g') { //doesn't start with geo:
        return@MatchFilter end - start > 12
    }
    return@MatchFilter true
}

private inline fun Spannable.forEachSpanIndexed(action: (index: Int, urlSpan: URLSpan, start: Int, end: Int) -> Unit) {
    getSpans(0, length, URLSpan::class.java)
            .forEachIndexed { index, urlSpan ->
                val start = getSpanStart(urlSpan)
                val end = getSpanEnd(urlSpan)
                action.invoke(index, urlSpan, start, end)
            }
}

private fun TextView.addLinkMovementMethod() {
    val m = movementMethod

    if (m == null || m !is LinkMovementMethod) {
        if (linksClickable) {
            movementMethod = LinkMovementMethod.getInstance()
        }
    }
}
