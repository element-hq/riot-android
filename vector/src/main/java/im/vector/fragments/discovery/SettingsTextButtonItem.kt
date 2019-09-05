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
package im.vector.fragments.discovery

import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import butterknife.BindView
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.airbnb.epoxy.EpoxyModelWithHolder
import im.vector.R
import im.vector.ui.epoxy.BaseEpoxyHolder
import im.vector.ui.util.setTextOrHide


@EpoxyModelClass(layout = R.layout.item_settings_button_single_line)
abstract class SettingsTextButtonItem : EpoxyModelWithHolder<SettingsTextButtonItem.Holder>() {

    enum class ButtonStyle {
        POSITIVE,
        DESCTRUCTIVE
    }

    @EpoxyAttribute
    var title: String? = null

    @EpoxyAttribute
    @StringRes
    var titleResId: Int? = null

    @EpoxyAttribute
    var buttonTitle: String? = null

    @EpoxyAttribute
    @StringRes
    var buttonTitleId: Int? = null

    @EpoxyAttribute
    var buttonStyle: ButtonStyle = ButtonStyle.POSITIVE

    @EpoxyAttribute
    var buttonIndeterminate: Boolean = false


    @EpoxyAttribute
    var buttonClickListener: View.OnClickListener? = null

    override fun bind(holder: Holder) {
        super.bind(holder)

        if (titleResId != null) {
            holder.textView.setText(titleResId!!)
        } else {
            holder.textView.setTextOrHide(title, hideWhenBlank = false)
        }

        if (buttonTitleId != null) {
            holder.button.setText(buttonTitleId!!)
        } else {
            holder.button.setTextOrHide(buttonTitle)
        }

        if (buttonIndeterminate) {
            holder.spinner.isVisible = true
            holder.button.isInvisible = true
            holder.button.setOnClickListener(null)
        } else {
            holder.spinner.isVisible = false
            holder.button.isVisible = true
            holder.button.setOnClickListener(buttonClickListener)
        }



        when (buttonStyle) {
            ButtonStyle.POSITIVE     -> {
                holder.button.setTextColor(ContextCompat.getColor(holder.main.context,R.color.vector_success_color))
            }
            ButtonStyle.DESCTRUCTIVE -> {
                holder.button.setTextColor(ContextCompat.getColor(holder.main.context,R.color.vector_error_color))
            }
        }

        holder.button.setOnClickListener(buttonClickListener)
    }

    class Holder : BaseEpoxyHolder() {

        @BindView(R.id.settings_item_text)
        lateinit var textView: TextView

        @BindView(R.id.settings_item_button)
        lateinit var button: Button

        @BindView(R.id.settings_item_button_spinner)
        lateinit var spinner: ProgressBar
    }
}