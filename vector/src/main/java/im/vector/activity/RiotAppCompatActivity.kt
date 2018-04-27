/*
 * Copyright 2015 OpenMarket Ltd
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

package im.vector.activity

import android.content.Context
import android.support.annotation.CallSuper
import android.support.v7.app.AppCompatActivity
import android.view.View
import androidx.core.view.isVisible

import im.vector.VectorApp
import org.matrix.androidsdk.util.Log

/**
 * Parent class for all Activities in Vector application
 */
abstract class RiotAppCompatActivity : AppCompatActivity() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(VectorApp.getLocalisedContext(base))
    }

    @CallSuper
    override fun onResume() {
        super.onResume()

        Log.event(Log.EventTag.NAVIGATION, "onResume Activity " + this.javaClass.simpleName)
    }

    //==============================================================================================
    // Handle loading view (also called waiting view or spinner view)
    //==============================================================================================

    var waitingView: View? = null

    /**
     * Tells if the waiting view is currently displayed
     *
     * @return true if the waiting view is displayed
     */
    fun isWaitingViewVisible() = waitingView?.isVisible == true

    /**
     * Show the waiting view
     */
    fun showWaitingView() {
        waitingView?.isVisible = true
    }

    /**
     * Hide the waiting view
     */
    fun hideWaitingView() {
        waitingView?.isVisible = false
    }
}
