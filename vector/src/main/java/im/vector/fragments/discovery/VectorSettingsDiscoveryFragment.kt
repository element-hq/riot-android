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
import android.view.View
import androidx.core.view.isVisible
import butterknife.BindView
import com.airbnb.epoxy.EpoxyRecyclerView
import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.MvRx
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import im.vector.R
import im.vector.activity.MXCActionBarActivity
import im.vector.extensions.withArgs
import im.vector.fragments.VectorBaseMvRxFragment


class VectorSettingsDiscoveryFragment : VectorBaseMvRxFragment(), SettingsDiscoveryController.InteractionListener {


    override fun getLayoutResId() = R.layout.fragment_simple_epoxy

    private val viewModel by fragmentViewModel(DiscoverySettingsViewModel::class)

    private lateinit var controller: SettingsDiscoveryController


    private var mLoadingView: View? = null

    @BindView(R.id.epoxyRecyclerView)
    lateinit var epoxyRecyclerView: EpoxyRecyclerView

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        controller = SettingsDiscoveryController(requireContext(), this).also {
            epoxyRecyclerView.setController(it)
        }

        mLoadingView = requireActivity().findViewById(R.id.vector_settings_spinner_views)

    }

    override fun invalidate() = withState(viewModel) { state ->
        mLoadingView?.isVisible = state.modalLoadingState is Loading

        controller.setData(state)
    }

    override fun onResume() {
        super.onResume()
        (activity as? MXCActionBarActivity)?.supportActionBar?.setTitle(R.string.settings_discovery_category)
        viewModel.startListenToIdentityManager()
    }

    override fun onPause() {
        mLoadingView?.isVisible = false
        super.onPause()
        viewModel.stopListenToIdentityManager()
    }

    override fun onSelectIdentityServer() = withState(viewModel) { state ->
        IdentityServerChooseBottomSheet.newInstance(listOf("vector.im"), state.identityServer.invoke()).show(requireActivity().supportFragmentManager, "IS")
    }

    override fun onTapRevokeEmail(email: String) {
        viewModel.revokeEmail(email)
    }

    override fun onTapShareEmail(email: String) {
        viewModel.shareEmail(email)
    }

    override fun onTapRevokePN(pn: String) {
        viewModel.revokePN(pn)
    }

    override fun onTapSharePN(pn: String) {
        viewModel.sharePN(pn)
    }
    override fun onSetIdentityServer(server: String?) {
        viewModel.changeIdentityServer(server)
    }


    companion object {
        fun newInstance(matrixId: String) = VectorSettingsDiscoveryFragment()
                .withArgs {
                    putString(MvRx.KEY_ARG, matrixId)
                }

    }

}