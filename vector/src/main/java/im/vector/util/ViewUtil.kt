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

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.view.children
import com.binaryfork.spanny.Spanny
import im.vector.ui.themes.ThemeUtils

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
fun ViewGroup.findAllTextInputLayout(): List<com.google.android.material.textfield.TextInputLayout> {
    val res = ArrayList<com.google.android.material.textfield.TextInputLayout>()

    children.forEach {
        if (it is com.google.android.material.textfield.TextInputLayout) {
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
fun autoResetTextInputLayoutErrors(textInputLayouts: List<com.google.android.material.textfield.TextInputLayout>) {
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
fun resetTextInputLayoutErrors(textInputLayouts: List<com.google.android.material.textfield.TextInputLayout>) {
    textInputLayouts.forEach { it.error = null }
}


/**
 * Tint all drawables of a TextView.
 *
 * Note: this method has no effect on API < 23. Please also set the android:drawableTint attribute in the layout or in the style
 */
@Suppress("LocalVariableName")
fun TextView.tintDrawableCompat(@AttrRes colorAttribute: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        // No op
        return
    }

    val drs: Array<out Drawable?> = compoundDrawables

    val drawableLeft__ = drs[0]?.let { ThemeUtils.tintDrawable(context, it, colorAttribute) }
    val drawableTop___ = drs[1]?.let { ThemeUtils.tintDrawable(context, it, colorAttribute) }
    val drawableRight_ = drs[2]?.let { ThemeUtils.tintDrawable(context, it, colorAttribute) }
    val drawableBottom = drs[3]?.let { ThemeUtils.tintDrawable(context, it, colorAttribute) }

    setCompoundDrawablesWithIntrinsicBounds(drawableLeft__, drawableTop___, drawableRight_, drawableBottom)
}

/**
 * Set text with a colored part
 */
fun TextView.setTextWithColoredPart(@StringRes fullTextRes: Int,
                                    @StringRes colorTextRes: Int,
                                    @AttrRes colorAttribute: Int) {
    val coloredPart = resources.getString(colorTextRes)
    val fullText = resources.getString(fullTextRes, coloredPart)

    val accentColor = ThemeUtils.getColor(context, colorAttribute)

    // Color colored part
    text = Spanny(fullText).apply { findAndSpan(coloredPart) { ForegroundColorSpan(accentColor) } }
}

/**
 * Apply a rounded (sides) rectangle as a background to the view.
 *
 * @param backgroundColor background colour
 */
fun View?.setRoundBackground(@ColorInt backgroundColor: Int) {
    if (this != null) {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            setColor(backgroundColor)
        }
    }
}
