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
package im.vector.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import butterknife.BindView
import butterknife.ButterKnife
import im.vector.R

/*
A password strength bar custom widget
 Strength is an Integer
 # -1 No strength
 # 0 Weak
 # 1 Fair
 # 2 Good
 # 3 Strong
 */
class PasswordStrengthBar(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {


    @BindView(R.id.password_strength_bar_1)
    lateinit var bar1: View

    @BindView(R.id.password_strength_bar_2)
    lateinit var bar2: View

    @BindView(R.id.password_strength_bar_3)
    lateinit var bar3: View

    @BindView(R.id.password_strength_bar_4)
    lateinit var bar4: View

    var strength = 0
        set(value) {
            if (value < 0) {
                bar1.setBackgroundColor(backgroundColor)
                bar2.setBackgroundColor(backgroundColor)
                bar3.setBackgroundColor(backgroundColor)
                bar4.setBackgroundColor(backgroundColor)
            } else if (value == 0) {
                bar1.setBackgroundColor(colors[0])
                bar2.setBackgroundColor(backgroundColor)
                bar3.setBackgroundColor(backgroundColor)
                bar4.setBackgroundColor(backgroundColor)
            } else if (value == 1) {
                bar1.setBackgroundColor(colors[1])
                bar2.setBackgroundColor(colors[1])
                bar3.setBackgroundColor(backgroundColor)
                bar4.setBackgroundColor(backgroundColor)
            } else if (value == 2) {
                bar1.setBackgroundColor(colors[2])
                bar2.setBackgroundColor(colors[2])
                bar3.setBackgroundColor(colors[2])
                bar4.setBackgroundColor(backgroundColor)
            } else {
                bar1.setBackgroundColor(colors[3])
                bar2.setBackgroundColor(colors[3])
                bar3.setBackgroundColor(colors[3])
                bar4.setBackgroundColor(colors[3])
            }
        }

    init {
        LayoutInflater.from(context)
                .inflate(R.layout.password_strength_bar, this, true)
        orientation = HORIZONTAL
        ButterKnife.bind(this)
        strength = -1
    }

    companion object {
        private val colors = listOf<Int>(
                Color.parseColor("#f56679"),
                Color.parseColor("#ffc666"),
                Color.parseColor("#f8e71c"),
                Color.parseColor("#7ac9a1"))
        private val backgroundColor = Color.parseColor("#9e9e9e")
    }
}