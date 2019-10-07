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

package im.vector.fragments.base

import android.content.Context
import org.matrix.androidsdk.core.Log

/**
 * this class can be used as a parent class for DialogFragment to manager the listener
 */
abstract class VectorBaseDialogFragment<LISTENER> : androidx.fragment.app.DialogFragment() {

    protected var listener: LISTENER? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        // Cannot use (context is LISTENER)
        // for the moment, the listener is the Activity (and not the parent Fragment as it should)
        try {
            @Suppress("UNCHECKED_CAST")
            listener = context as LISTENER
        } catch (e: ClassCastException) {
            Log.w(LOG_TAG, "Parent Activity should implement the LISTENER interface")
        }
    }

    override fun onDetach() {
        super.onDetach()

        listener = null
    }

    companion object {
        private val LOG_TAG = VectorBaseDialogFragment::class.java.simpleName
    }
}