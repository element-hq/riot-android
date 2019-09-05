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

import android.content.Context
import android.view.View
import com.airbnb.epoxy.TypedEpoxyController
import im.vector.R

class IdentityServerChooserController(val context: Context) : TypedEpoxyController<IdentityServerChooseState>() {

    override fun buildModels(data: IdentityServerChooseState?) {

        data?.let { state ->

            state.list.distinct().forEach {
                settingsImageItem {
                    id(it)
                    title(it)
                    endIconResourceId(if (state.selected == it) R.drawable.unit_test_ok else R.drawable.unit_test)
                    itemClickListener(View.OnClickListener {

                    })
                }
            }

            settingsImageItem {
                id("none")
                title(context.getString(R.string.none))
                endIconResourceId(if (state.selected == null) R.drawable.unit_test_ok else R.drawable.unit_test)
            }

            settingsTextButtonItem {
                id("add")
                title(" ")
                buttonTitleId(R.string.add_identity_server)
                buttonIndeterminate(false)
            }

        }
    }
}