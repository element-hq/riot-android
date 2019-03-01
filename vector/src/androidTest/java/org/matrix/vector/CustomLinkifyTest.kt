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
package org.matrix.vector

import android.support.test.InstrumentationRegistry
import android.text.Spannable
import android.text.style.URLSpan
import android.widget.TextView
import androidx.core.text.toSpannable
import im.vector.util.vectorCustomLinkify
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomLinkifyTest {

    val mContext = InstrumentationRegistry.getContext()

    data class TestLinkMatch(
            val display: String,
            val protocol: String? = null,
            val url: String = "${protocol ?: ""}$display")

    @Test
    fun linkify_testLinkifySimpleURL() {
        actAndAssert(
                "this is an url with no protocol %s",
                arrayOf(
                        TestLinkMatch("www.myserver.com", "http://")
                )
        )
    }

    @Test
    fun linkify_testLinkifyURLWithTrailingSlashAndTextAfter() {

        actAndAssert(
                "with trailing slash %s | ",
                arrayOf(
                        TestLinkMatch("www.myserver.com", "http://")
                )
        )

    }

    @Test
    fun linkify_testLinkifyMultipleUrls() {
        actAndAssert(
                "Look at %s and %s and tell me",
                arrayOf(
                        TestLinkMatch("www.myserver.com", "http://"),
                        TestLinkMatch("https://www.otherdomain.foo/ohh?er=32%2B20")
                )
        )
    }

    @Test
    fun linkify_strictDomainNameWhenNoProtocol() {

        val strangeTopLevel = "strangetoplevel"
        val ianaTopLevel = "ninja"
        val base = "foo.ansible.%s/xoxys.matrix#2c0b65eb"

        actAndAssert(
                "bla bla ${base.format(strangeTopLevel)}",
                arrayOf()
        )

        actAndAssert(
                "bla bla %s",
                arrayOf(
                        TestLinkMatch(base.format(ianaTopLevel), "http://")
                )
        )
    }

    @Test
    fun linkify_strictDoNotMatchSmallNumbersAsPn() {

        actAndAssert(
                "ksks9808",
                arrayOf()
        )

        actAndAssert(
                "call me at %s",
                arrayOf(
                        TestLinkMatch("+44 207 123 1234", url = "tel:+442071231234")
                )
        )
    }

    @Test
    fun linkify_testGeoString() {

        actAndAssert(
                "I am here %s :)",
                arrayOf(
                        TestLinkMatch("37.786971,-122.399677;u=35", "geo:")
                )
        )

        //Do not match too small
        actAndAssert(
                "I am here 1,12 :)",
                arrayOf()
        )

    }

    @Test
    fun linkify_2350_someMessagesConvertedToMap() {

        //Should not match
        actAndAssert(
                "synchrone peut tenir la route la",
                arrayOf()
        )

    }

    fun actAndAssert(format: String, matches: Array<TestLinkMatch>) {
        val textView = TextView(mContext)
        val displays = (matches.map { it.display }).toTypedArray()
        val testString = format.format(*displays)
        textView.text = testString
        textView.vectorCustomLinkify()

        //We need to assert that a URL span ha been added
        val spannable = textView.text.toSpannable()
        assertEquals(matches.size, spannable.getSpans(0, testString.length, URLSpan::class.java).count())

        spannable.forEachSpanIndexed { index, urlSpan, start, end ->
            assertEquals("Incorrect Url", matches[index].url, urlSpan.url)
            assertEquals("Match is not correct", matches[index].display, spannable.substring(start, end))
        }
    }


    private inline fun Spannable.forEachSpanIndexed(action: (index: Int, urlSpan: URLSpan, start: Int, end: Int) -> Unit): Unit {
        this.getSpans(0, length, URLSpan::class.java).forEachIndexed { index, urlSpan ->
            val start = getSpanStart(urlSpan)
            val end = getSpanEnd(urlSpan)
            action.invoke(index, urlSpan, start, end)
        }
        //for (element in this.span) action(element)
    }

}