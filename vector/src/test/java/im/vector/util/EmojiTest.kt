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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.JVM)
class EmojiTest {
    @Test
    fun Emoji_null_false() {
        assertFalse(containsOnlyEmojis(null))
    }

    @Test
    fun Emoji_empty_false() {
        assertFalse(containsOnlyEmojis(""))
    }

    @Test
    fun Emoji_letter_false() {
        assertFalse(containsOnlyEmojis("a"))
    }

    @Test
    fun Emoji_text_false() {
        assertFalse(containsOnlyEmojis("This is a long text"))
    }

    @Test
    fun Emoji_space_false() {
        assertFalse(containsOnlyEmojis(" "))
    }

    @Test
    fun Emoji_emoji_true() {
        assertTrue(containsOnlyEmojis("\uD83D\uDE03")) // 😃
    }

    @Test
    fun Emoji_emojiUtf8_true() {
        assertTrue(containsOnlyEmojis("😃"))
    }

    @Test
    fun Emoji_emojiMulitple_true() {
        assertTrue(containsOnlyEmojis("😃😃"))
        assertTrue(containsOnlyEmojis("😃😃😃"))
        assertTrue(containsOnlyEmojis("😃😃😃😃😃"))
    }

    @Test
    fun Emoji_emojiLetter_false() {
        // Letter before
        assertFalse(containsOnlyEmojis("a\uD83D\uDE03"))
        assertFalse(containsOnlyEmojis("a😃"))

        // Letter after
        assertFalse(containsOnlyEmojis("\uD83D\uDE03a"))
        assertFalse(containsOnlyEmojis("😃a"))

        // Letters around
        assertFalse(containsOnlyEmojis("a\uD83D\uDE03b"))
        assertFalse(containsOnlyEmojis("a😃b"))
    }

    @Test
    fun Emoji_emojiSpace_false() {
        // Space before
        assertFalse(containsOnlyEmojis(" \uD83D\uDE03"))
        assertFalse(containsOnlyEmojis(" 😃"))

        // Space after
        assertFalse(containsOnlyEmojis("\uD83D\uDE03 "))
        assertFalse(containsOnlyEmojis("😃 "))

        // Spaces around
        assertFalse(containsOnlyEmojis(" \uD83D\uDE03 "))
        assertFalse(containsOnlyEmojis(" 😃 "))
    }
}