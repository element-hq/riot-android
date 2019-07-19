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

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.core.animation.doOnEnd
import im.vector.R
import im.vector.ui.themes.ThemeUtils
import org.matrix.androidsdk.core.Log


/**
 * create a Preference with a dedicated click/long click methods.
 * It also allow the title to be displayed on several lines
 */
open class VectorPreference : Preference {

    var mTypeface = Typeface.NORMAL

    // long press listener
    /**
     * Returns the callback to be invoked when this Preference is long clicked.
     *
     * @return The callback to be invoked.
     */
    /**
     * Sets the callback to be invoked when this Preference is long clicked.
     *
     * @param onPreferenceLongClickListener The callback to be invoked.
     */
    var onPreferenceLongClickListener: OnPreferenceLongClickListener? = null

    /**
     * Interface definition for a callback to be invoked when a preference is
     * long clicked.
     */
    interface OnPreferenceLongClickListener {
        /**
         * Called when a Preference has been clicked.
         *
         * @param preference The Preference that was clicked.
         * @return True if the click was handled.
         */
        fun onPreferenceLongClick(preference: Preference): Boolean
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    init {
        isIconSpaceReserved = false
    }

    var isHighlighted = false
        set(value) {
            field = value
            notifyChanged()
        }

    var currentHighlightAnimator: Animator? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        val itemView = holder.itemView
        addClickListeners(itemView)

        // display the title in multi-line to avoid ellipsis.
        try {
            val title = itemView.findViewById<TextView>(android.R.id.title)
            val summary = itemView.findViewById<TextView>(android.R.id.summary)
            if (title != null) {
                title.setSingleLine(false)
                title.setTypeface(null, mTypeface)
            }

            if (title !== summary) {
                summary.setTypeface(null, mTypeface)
            }

            //cancel existing animation (find a way to resume if happens during anim?)
            currentHighlightAnimator?.cancel()
            if (isHighlighted) {
                val colorFrom = Color.TRANSPARENT
                val colorTo = ThemeUtils.getColor(itemView.context, R.attr.colorAccent)
                currentHighlightAnimator = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
                    duration = 250 // milliseconds
                    addUpdateListener { animator ->
                        itemView?.setBackgroundColor(animator.animatedValue as Int)
                    }
                    doOnEnd {
                        currentHighlightAnimator = ValueAnimator.ofObject(ArgbEvaluator(), colorTo, colorFrom).apply {
                            duration = 250 // milliseconds
                            addUpdateListener { animator ->
                                itemView?.setBackgroundColor(animator.animatedValue as Int)
                            }
                            doOnEnd {
                                isHighlighted = false
                            }
                            start()
                        }
                    }
                    startDelay = 200
                    start()
                }
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT)
            }

        } catch (e: Exception) {
            Log.e(LOG_TAG, "onBindView " + e.message, e)
        }

        super.onBindViewHolder(holder)
    }

    /**
     * @param view
     */
    private fun addClickListeners(view: View) {
        view.setOnLongClickListener {
            if (null != onPreferenceLongClickListener) {
                onPreferenceLongClickListener!!.onPreferenceLongClick(this@VectorPreference)
            } else false
        }

        view.setOnClickListener {
            // call only the click listener
            if (onPreferenceClickListener != null) {
                onPreferenceClickListener.onPreferenceClick(this@VectorPreference)
            }
        }
    }

    companion object {
        private val LOG_TAG = VectorPreference::class.java.simpleName
    }
}