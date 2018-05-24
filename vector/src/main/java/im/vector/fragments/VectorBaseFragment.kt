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

package im.vector.fragments

import android.content.Context
import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.v4.app.Fragment
import android.view.View
import butterknife.ButterKnife
import butterknife.Unbinder
import im.vector.activity.RiotAppCompatActivity
import org.matrix.androidsdk.util.Log


/**
 * Parent class for all Fragment in Vector application
 */
open class VectorBaseFragment : Fragment() {

    // Butterknife unbinder
    private var mUnBinder: Unbinder? = null

    protected var riotActivity: RiotAppCompatActivity? = null

    /* ==========================================================================================
     * Life cycle
     * ========================================================================================== */

    @CallSuper
    override fun onResume() {
        super.onResume()

        Log.event(Log.EventTag.NAVIGATION, "onResume Fragment " + this.javaClass.simpleName)
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mUnBinder = ButterKnife.bind(this, view)
    }

    @CallSuper
    override fun onDestroyView() {
        super.onDestroyView()
        mUnBinder?.unbind()
        mUnBinder = null
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        riotActivity = context as RiotAppCompatActivity
    }

    override fun onDetach() {
        super.onDetach()

        riotActivity = null
    }
}
