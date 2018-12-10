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

package im.vector.activity.policies

import android.view.View
import android.widget.CompoundButton
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.airbnb.epoxy.EpoxyModelWithHolder
import im.vector.R

@EpoxyModelClass(layout = R.layout.adapter_item_policy)
abstract class PolicyModel : EpoxyModelWithHolder<PolicyHolder>() {
    @EpoxyAttribute
    var checked: Boolean = false

    @EpoxyAttribute
    var title: String? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var checkChangeListener: CompoundButton.OnCheckedChangeListener? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var clickListener: View.OnClickListener? = null

    override fun bind(holder: PolicyHolder) {
        holder.let {
            it.checkbox.isChecked = checked
            it.checkbox.setOnCheckedChangeListener(checkChangeListener)
            it.title.text = title
            it.main.setOnClickListener(clickListener)
        }
    }

    // Ensure checkbox behaves as expected (remove the listener)
    override fun unbind(holder: PolicyHolder) {
        super.unbind(holder)
        holder.checkbox.setOnCheckedChangeListener(null)
    }
}