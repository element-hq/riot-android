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

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.ButterKnife
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import im.vector.R
import kotlinx.android.parcel.Parcelize

@Parcelize
data class ChooseISBottomSheetArgs(
        var isList: List<String>? = null,
        var selected: String? = null
) : Parcelable

class IdentityServerChooseBottomSheet : BottomSheetDialogFragment() {

    @BindView(R.id.bottom_sheet_list)
    lateinit var recyclerView: RecyclerView

    @BindView(R.id.bottomSheetTitle)
    lateinit var titleView: TextView


    lateinit var viewModel: IdentityServerViewModel


    private val epoxyController: IdentityServerChooserController  by lazy {
        IdentityServerChooserController(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_epoxy_list_with_title, container, false)
        ButterKnife.bind(this, view)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProviders.of(this).get(IdentityServerViewModel::class.java)

        arguments?.let {
            val state = viewModel.state.value ?: IdentityServerChooseState(emptyList(), null)
            (it.getParcelable(ARGS) as? ChooseISBottomSheetArgs)?.let { args ->
                viewModel.state.postValue(state.copy(list = args.isList
                        ?: emptyList(), selected = args.selected))
            }
        }

        LinearLayoutManager(context).also {
            recyclerView.layoutManager = it
        }
        recyclerView.adapter = epoxyController.adapter
        val dividerItemDecoration = DividerItemDecoration(requireContext(),
                LinearLayout.VERTICAL)
        recyclerView.addItemDecoration(dividerItemDecoration)
        titleView.text = context?.getString(R.string.choose_identity_server)

        viewModel.state.observe(this, Observer {
            epoxyController.setData(it)
        })
    }

    companion object {

        private const val ARGS = "ARGS"

        fun newInstance(candidates: List<String>, current: String?): IdentityServerChooseBottomSheet {
            val args = Bundle()
            val parcelableArgs = ChooseISBottomSheetArgs(candidates, current)
            args.putParcelable(ARGS, parcelableArgs)
            return IdentityServerChooseBottomSheet().apply { arguments = args }
        }
    }

}