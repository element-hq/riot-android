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
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import butterknife.BindView
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.airbnb.epoxy.EpoxyModelWithHolder
import im.vector.R
import im.vector.ui.epoxy.BaseEpoxyHolder
import im.vector.ui.themes.ThemeUtils
import im.vector.ui.util.setTextOrHide


@EpoxyModelClass(layout = R.layout.item_settings_helper_info)
abstract class SettingsInfoItem : EpoxyModelWithHolder<SettingsInfoItem.Holder>() {

    @EpoxyAttribute
    var helperText: String? = null

    @EpoxyAttribute
    @StringRes
    var helperTextResId: Int? = null


    @EpoxyAttribute
    var itemClickListener: View.OnClickListener? = null

    @EpoxyAttribute
    @DrawableRes
    var compoundDrawable: Int = R.drawable.vector_warning_red

    @EpoxyAttribute
    var showCompoundDrawable: Boolean = false

    override fun bind(holder: Holder) {
        super.bind(holder)

        if (helperTextResId != null) {
            holder.text.setText(helperTextResId!!)
        } else {
            holder.text.setTextOrHide(helperText)
        }

        holder.main.setOnClickListener(itemClickListener)

        if (showCompoundDrawable) {
            ContextCompat.getDrawable(holder.main.context, compoundDrawable)?.apply {
                holder.text.setCompoundDrawablesWithIntrinsicBounds(this, null, null, null)
                holder.text.compoundDrawablePadding = 4
            }
        } else {
            holder.text.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
        }
    }

    class Holder : BaseEpoxyHolder() {
        @BindView(R.id.settings_helper_text)
        lateinit var text: TextView
    }
}