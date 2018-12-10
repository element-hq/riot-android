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

import android.widget.CheckBox
import android.widget.TextView
import butterknife.BindView
import im.vector.R
import im.vector.ui.epoxy.BaseEpoxyHolder

class PolicyHolder : BaseEpoxyHolder() {

    @BindView(R.id.adapter_item_policy_checkbox)
    lateinit var checkbox: CheckBox

    @BindView(R.id.adapter_item_policy_title)
    lateinit var title: TextView

}