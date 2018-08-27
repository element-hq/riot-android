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

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCompletionModeTest {
    @Test
    fun userMode_empty() {
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText(""))
    }

    @Test
    fun userMode_classic() {
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText("Hello test"))
    }

    @Test
    fun userMode_slash() {
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText("Hello /"))
    }

    @Test
    fun userMode_at() {
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText("Hello @"))
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText("Hello @b"))
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText("Hello @be"))
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText("Hello @ben"))
    }

    @Test
    fun userMode_withCommand() {
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText("/invite "))
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText("/invite b"))
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText("/invite be"))
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText("/invite ben"))
    }

    @Test
    fun userMode_withCommand_at() {
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText("/invite @"))
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText("/invite @b"))
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText("/invite @be"))
        assertEquals(AutoCompletionMode.USER_MODE, AutoCompletionMode.getWithText("/invite @ben"))
    }

    @Test
    fun commandMode_empty() {
        assertEquals(AutoCompletionMode.COMMAND_MODE, AutoCompletionMode.getWithText("/"))
    }

    @Test
    fun commandMode_notEmpty() {
        assertEquals(AutoCompletionMode.COMMAND_MODE, AutoCompletionMode.getWithText("/m"))
        assertEquals(AutoCompletionMode.COMMAND_MODE, AutoCompletionMode.getWithText("/me"))
    }
}