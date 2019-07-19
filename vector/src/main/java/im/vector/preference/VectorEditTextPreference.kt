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

package im.vector.preference

import android.content.Context
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.widget.TextView
import im.vector.R
import org.matrix.androidsdk.core.Log

/**
 * Use this class to create an EditTextPreference form code and avoid a crash (see https://code.google.com/p/android/issues/detail?id=231576)
 */
class VectorEditTextPreference : EditTextPreference {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    init {
        dialogLayoutResource = R.layout.dialog_preference_edit_text
        isIconSpaceReserved = false
    }

    // No single line for title
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        // display the title in multi-line to avoid ellipsis.
        try {
            holder.itemView.findViewById<TextView>(android.R.id.title)?.setSingleLine(false)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "onBindView " + e.message, e)
        }

        super.onBindViewHolder(holder)
    }

    companion object {
        private val LOG_TAG = VectorEditTextPreference::class.java.simpleName
    }
}