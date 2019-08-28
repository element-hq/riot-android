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
package im.vector.ui.epoxy

import android.widget.TextView
import androidx.annotation.StringRes
import butterknife.BindView
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.airbnb.epoxy.EpoxyModelWithHolder
import im.vector.R

/**
 * A generic list item header left aligned with notice color.
 */
@EpoxyModelClass(layout = R.layout.item_generic_header)
abstract class GenericItemHeader : EpoxyModelWithHolder<GenericItemHeader.Holder>() {

    @EpoxyAttribute
    var text: String? = null

    @EpoxyAttribute
    @StringRes
    var textID: Int? = null

    @EpoxyAttribute
    var textSizeSp: Float = 15f

    override fun bind(holder: Holder) {
        if (textID != null) {
            holder.textView.setText(textID!!)
        } else {
            holder.textView.text = text
        }
        holder.textView.textSize = textSizeSp
    }

    class Holder : BaseEpoxyHolder() {

        @BindView(R.id.itemGenericHeaderText)
        lateinit var textView: TextView
    }
}