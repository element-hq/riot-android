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

enum class AutoCompletionMode {
    USER_MODE,
    COMMAND_MODE;

    companion object {
        /**
         * It's important to start with " " to enter USER_MODE even if text starts with "/"
         */
        fun getWithText(text: String) = when {
            text.startsWith("@") || text.contains(" ") -> USER_MODE
            text.startsWith("/") -> COMMAND_MODE
            else -> USER_MODE
        }
    }
}