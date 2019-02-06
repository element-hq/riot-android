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

import android.support.design.widget.TextInputLayout
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import androidx.core.view.children

/**
 * The view is visible
 */
const val UTILS_OPACITY_FULL = 1f
/**
 * The view is half dimmed
 */
const val UTILS_OPACITY_HALF = 0.5f
/**
 * The view is hidden
 */
const val UTILS_OPACITY_NONE = 0f

/**
 * Find all TextInputLayout in a ViewGroup and in all its descendants
 */
fun ViewGroup.findAllTextInputLayout(): List<TextInputLayout> {
    val res = ArrayList<TextInputLayout>()

    children.forEach {
        if (it is TextInputLayout) {
            res.add(it)
        } else if (it is ViewGroup) {
            // Recursive call
            res.addAll(it.findAllTextInputLayout())
        }
    }

    return res
}

/**
 * Add a text change listener to all TextInputEditText to reset error on its TextInputLayout when the text is changed
 */
fun autoResetTextInputLayoutErrors(textInputLayouts: List<TextInputLayout>) {
    textInputLayouts.forEach {
        it.editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                // Reset the error
                it.error = null
            }
        })
    }
}

/**
 * Reset error for all TextInputLayout
 */
fun resetTextInputLayoutErrors(textInputLayouts: List<TextInputLayout>) {
    textInputLayouts.forEach { it.error = null }
}
