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
package im.vector.ui.animation

import android.view.Gravity
import android.view.View
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionSet

class VectorTransitionSet : TransitionSet() {

    init {
        // Change bounds for every Views
        addTransition(ChangeBounds())
    }

    private val slideStart by lazy { Slide(Gravity.START).apply { addTransition(this) } }

    private val sliderEnd by lazy { Slide(Gravity.END).apply { addTransition(this) } }

    private val slideBottom by lazy { Slide(Gravity.BOTTOM).apply { addTransition(this) } }

    private val slideTop by lazy { Slide(Gravity.TOP).apply { addTransition(this) } }

    private val alpha by lazy { Fade().apply { addTransition(this) } }

    fun appearFromTop(view: View) {
        slideTop.addTarget(view)
    }

    fun appearFromBottom(view: View) {
        slideBottom.addTarget(view)
    }

    fun appearFromStart(view: View) {
        slideStart.addTarget(view)
    }

    fun appearFromEnd(view: View) {
        sliderEnd.addTarget(view)
    }

    fun appearWithAlpha(view: View) {
        alpha.addTarget(view)
    }
}