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

import android.text.Spannable
import android.text.SpannableString
import android.text.style.URLSpan
import android.widget.TextView
import androidx.core.text.toSpannable
import androidx.test.InstrumentationRegistry
import im.vector.util.vectorCustomLinkify
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomLinkifyTest {

    data class TestLinkMatch(
            val display: String,
            val protocol: String? = null,
            val url: String = "${protocol ?: ""}$display")

    @Test
    fun linkify_testLinkifySimpleURL() {
        actAndAssert(
                "this is an url with no protocol www.myserver.com",
                listOf(
                        TestLinkMatch("www.myserver.com", "http://")
                )
        )
    }

    @Test
    fun linkify_testLinkifyURLWithTrailingSlashAndTextAfter() {
        actAndAssert(
                "with trailing slash www.myserver.com/ | ",
                listOf(
                        TestLinkMatch("www.myserver.com/", "http://")
                )
        )
        actAndAssert(
                "with trailing slash www.myserver.com/",
                listOf(
                        TestLinkMatch("www.myserver.com/", "http://")
                )
        )
    }

    @Test
    fun linkify_testLinkifyMultipleUrls() {
        actAndAssert(
                "Look at %s and %s and tell me",
                listOf(
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
                listOf()
        )

        actAndAssert(
                "bla bla %s",
                listOf(
                        TestLinkMatch(base.format(ianaTopLevel), "http://")
                )
        )
    }

    @Test
    fun linkify_strictDoNotMatchSmallNumbersAsPn() {
        actAndAssert(
                "ksks9808",
                emptyList()
        )

        actAndAssert(
                "call me at %s",
                listOf(
                        TestLinkMatch("+44 207 123 1234", url = "tel:+442071231234")
                )
        )
    }

    @Test
    fun linkify_testGeoString() {
        actAndAssert(
                "I am here %s :)",
                listOf(
                        TestLinkMatch("37.786971,-122.399677;u=35", "geo:")
                )
        )

        //Do not match too small
        actAndAssert(
                "I am here 1,12 :)",
                emptyList()
        )
    }

    @Test
    fun linkify_2350_someMessagesConvertedToMap() {
        //Should not match
        actAndAssert(
                "synchrone peut tenir la route la",
                emptyList()
        )

    }

    @Test
    fun linkify_3020_roundBrackets() {
        actAndAssert(
                "in brackets like (help for Riot: https://about.riot.im/help) , the link is usable ",
                listOf(TestLinkMatch("https://about.riot.im/help"))
        )

//        actAndAssert(
//                "in brackets like (help for Riot: https://www.exemple/com/find(1)) , the link is usable ",
//                listOf(TestLinkMatch("https://www.exemple/com/find(1)"))
//        )

        actAndAssert(
                "https://www.exemple.com/test1)",
                listOf(TestLinkMatch("https://www.exemple.com/test1"))
        )

        actAndAssert(
                "(https://www.exemple.com/test(1))",
                listOf(TestLinkMatch("https://www.exemple.com/test(1)"))
        )
    }

    @Test
    fun linkify_Overlap() {
        // geo containing a phone number. Only the geo should be detected
        actAndAssert(
                "test overlap 0673728392,48.107864 geo + pn?",
                listOf(
                        TestLinkMatch("0673728392,48.107864", "geo:")
                )
        )
    }

    @Test
    fun linkify_Multiple() {
        actAndAssert(
                """
                   In brackets like (help for Riot: https://about.riot.im/help) , the link is usable,
                    But you can call +44 207 123 1234 and come to 37.786971,-122.399677;u=35 then
                    see if this mail jhon@riot.im is active but this should not 12345
                """.trimIndent(),
                listOf(
                        TestLinkMatch("https://about.riot.im/help"),
                        TestLinkMatch("+44 207 123 1234", url = "tel:+442071231234"),
                        TestLinkMatch("37.786971,-122.399677;u=35", "geo:"),
                        TestLinkMatch("jhon@riot.im", "mailto:")
                )
        )
    }

    @Test
    fun linkify_Mailto_trailingslash() {
        actAndAssert("mail me at test@foo.bar/or bar@foo.me/",
                listOf(
                        TestLinkMatch("test@foo.bar", "mailto:"),
                        TestLinkMatch("bar@foo.me", "mailto:")
                )
        )
    }

    @Test
    fun linkify_Mailto_include_protocol() {
        actAndAssert("mailto:test@foo.bar",
                listOf(
                        TestLinkMatch("mailto:test@foo.bar", url = "mailto:test@foo.bar")
                )
        )
    }


    @Test
    fun linkify_testKeepExistingSpans() {
        val text = "my matrix.org test"
        val spanString = SpannableString(text)
        val span = URLSpan("https://vector.im")
        val start = text.indexOf("matrix.org")
        spanString.setSpan(span, start, start + "matrix.org".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val textView = TextView(InstrumentationRegistry.getContext())
        textView.text = spanString

        textView.vectorCustomLinkify(keepExistingUrlSpan = true)

        val spannable = textView.text.toSpannable()
        assertEquals("Wrong number of span detected",
                1,
                spannable.getSpans(0, spannable.length, URLSpan::class.java).count())

        spannable.forEachSpanIndexed { index, urlSpan, start, end ->
            assertEquals("Incorrect Url", "https://vector.im", urlSpan.url)
            assertEquals("Match is not correct", "matrix.org", spannable.substring(start, end))
        }
    }

    private fun actAndAssert(format: String, matches: List<TestLinkMatch>) {
        // Arrange
        val textView = TextView(InstrumentationRegistry.getContext())
        val displays = matches.map { it.display }.toTypedArray()
        val testString = format.format(*displays)
        textView.text = testString

        // Act
        textView.vectorCustomLinkify()

        // Assert
        //We need to assert that URL span(s) have been added
        val spannable = textView.text.toSpannable()
        assertEquals("Wrong number of span detected for $format",
                matches.size,
                spannable.getSpans(0, testString.length, URLSpan::class.java).count())

        spannable.forEachSpanIndexed { index, urlSpan, start, end ->
            assertEquals("Incorrect Url", matches[index].url, urlSpan.url)
            assertEquals("Match is not correct", matches[index].display, spannable.substring(start, end))
        }
    }


    private inline fun Spannable.forEachSpanIndexed(action: (index: Int, urlSpan: URLSpan, start: Int, end: Int) -> Unit) {
        val spans = this.getSpans(0, length, URLSpan::class.java)
        spans.sortWith(Comparator { o1, o2 ->
            getSpanStart(o1) - getSpanStart(o2)
        })
        spans.forEachIndexed { index, urlSpan ->
            val start = getSpanStart(urlSpan)
            val end = getSpanEnd(urlSpan)
            action.invoke(index, urlSpan, start, end)
        }
        //for (element in this.span) action(element)
    }
}