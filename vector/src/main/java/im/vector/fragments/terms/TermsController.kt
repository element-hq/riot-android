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
package im.vector.fragments.terms

import android.view.View
import com.airbnb.epoxy.TypedEpoxyController
import im.vector.R
import im.vector.ui.epoxy.genericItemHeader

class TermsController(private val itemDescription: String,
                      private val listener: Listener) : TypedEpoxyController<List<Term>>() {

    override fun buildModels(data: List<Term>?) {
        data?.let {
            genericItemHeader {
                id("header")
                textID(R.string.widget_integration_review_terms)
            }
            it.forEach { term ->
                terms {
                    id(term.url)
                    name(term.name)
                    description(itemDescription)
                    checked(term.accepted)

                    clickListener(View.OnClickListener { listener.review(term) })
                    checkChangeListener { _, isChecked ->
                        listener.setChecked(term, isChecked)
                    }
                }
            }
        }
        //TODO error mgmt
    }

    interface Listener {
        fun setChecked(term: Term, isChecked: Boolean)
        fun review(term: Term)
    }
}