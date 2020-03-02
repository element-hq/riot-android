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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.airbnb.mvrx.MvRx
import com.airbnb.mvrx.args
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import com.google.android.material.snackbar.Snackbar
import im.vector.R
import im.vector.activity.MXCActionBarActivity
import im.vector.activity.ReviewTermsActivity
import im.vector.activity.util.TERMS_REQUEST_CODE
import im.vector.extensions.withArgs
import im.vector.fragments.VectorBaseMvRxFragment
import kotlinx.android.synthetic.main.fragment_simple_epoxy.*
import org.matrix.androidsdk.features.terms.TermsManager
import org.matrix.androidsdk.rest.model.pid.ThreePid


class VectorSettingsDiscoveryFragment : VectorBaseMvRxFragment(), SettingsDiscoveryController.InteractionListener {


    override fun getLayoutResId() = R.layout.fragment_simple_epoxy

    private val viewModel by fragmentViewModel(DiscoverySettingsViewModel::class)

    private lateinit var controller: SettingsDiscoveryController

    lateinit var sharedViewModel: DiscoverySharedViewModel

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        sharedViewModel = ViewModelProviders.of(requireActivity()).get(DiscoverySharedViewModel::class.java)

        controller = SettingsDiscoveryController(requireContext(), this).also {
            epoxyRecyclerView.setController(it)
        }

        sharedViewModel.navigateEvent.observe(this, Observer {
            if (it.peekContent().first == DiscoverySharedViewModel.NEW_IDENTITY_SERVER_SET_REQUEST) {
                viewModel.changeIdentityServer(it.peekContent().second)
            }
        })

        viewModel.errorLiveEvent.observe(this, Observer {
            it.getContentIfNotHandled()?.let { throwable ->
                Snackbar.make(coordinatorLayout, throwable.toString(), Snackbar.LENGTH_LONG).show()
            }
        })
    }

    override fun invalidate() = withState(viewModel) { state ->
        controller.setData(state)
    }

    override fun onResume() {
        super.onResume()
        (activity as? MXCActionBarActivity)?.supportActionBar?.setTitle(R.string.settings_discovery_category)

        //If some 3pids are pending, we can try to check if they have been verified here
        viewModel.refreshPendingEmailBindings()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == TERMS_REQUEST_CODE) {
            if (Activity.RESULT_OK == resultCode) {
                viewModel.refreshModel()
            } else {
                //add some error?
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onSelectIdentityServer() = withState(viewModel) { state ->
        if (state.termsNotSigned) {
            ReviewTermsActivity.intent(requireContext(),
                    TermsManager.ServiceType.IdentityService,
                    SetIdentityServerViewModel.sanitatizeBaseURL(state.identityServer() ?: ""),
                    null).also {
                startActivityForResult(it, TERMS_REQUEST_CODE)
            }
        }
    }

    override fun onTapRevokeEmail(email: String) {
        viewModel.revokeEmail(email)
    }

    override fun onTapShareEmail(email: String) {
        viewModel.shareEmail(email)
    }

    override fun checkEmailVerification(email: String, bind: Boolean) {
        viewModel.finalizeBind3pid(ThreePid.MEDIUM_EMAIL, email, bind)
    }

    override fun checkMsisdnVerification(msisdn: String, code: String, bind: Boolean) {
        viewModel.submitMsisdnToken(msisdn, code, bind)
    }

    override fun onTapRevokeMsisdn(msisdn: String) {
        viewModel.revokeMsisdn(msisdn)
    }

    override fun onTapShareMsisdn(msisdn: String) {
        viewModel.shareMsisdn(msisdn)
    }

    override fun onTapChangeIdentityServer() = withState(viewModel) { state ->
        //we should prompt if there are bound items with current is
        val pidList = ArrayList<PidInfo>().apply {
            state.emailList()?.let { addAll(it) }
            state.phoneNumbersList()?.let { addAll(it) }
        }

        val hasBoundIds = pidList.any { it.isShared() == PidInfo.SharedState.SHARED }

        if (hasBoundIds) {
            //we should prompt
            AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.change_identity_server)
                    .setMessage(getString(R.string.settings_discovery_disconnect_with_bound_pid, state.identityServer(), state.identityServer()))
                    .setPositiveButton(R.string._continue) { _, _ -> navigateToChangeIsFragment(state) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            Unit
        } else {
            navigateToChangeIsFragment(state)
        }
    }


    override fun onTapDisconnectIdentityServer() {
        //we should prompt if there are bound items with current is
        withState(viewModel) { state ->
            val pidList = ArrayList<PidInfo>().apply {
                state.emailList()?.let { addAll(it) }
                state.phoneNumbersList()?.let { addAll(it) }
            }

            val hasBoundIds = pidList.any { it.isShared() == PidInfo.SharedState.SHARED }

            if (hasBoundIds) {
                //we should prompt
                AlertDialog.Builder(requireActivity())
                        .setTitle(R.string.disconnect_identity_server)
                        .setMessage(getString(R.string.settings_discovery_disconnect_with_bound_pid, state.identityServer(), state.identityServer()))
                        .setPositiveButton(R.string._continue) { _, _ -> viewModel.changeIdentityServer(null) }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
            } else {
                viewModel.changeIdentityServer(null)
            }
        }
    }

    override fun onTapRetryToRetrieveBindings() {
        viewModel.retrieveBinding()
    }

    private fun navigateToChangeIsFragment(state: DiscoverySettingsState) {
        SetIdentityServerFragment.newInstance(args<String>().toString(), state.identityServer()).also {
            requireFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.anim_slide_in_bottom, R.anim.anim_slide_out_bottom, R.anim.anim_slide_in_bottom, R.anim.anim_slide_out_bottom)
                    .replace(R.id.vector_settings_page, it, getString(R.string.identity_server))
                    .addToBackStack(null)
                    .commit()
        }
    }

    companion object {
        fun newInstance(matrixId: String) = VectorSettingsDiscoveryFragment()
                .withArgs {
                    putString(MvRx.KEY_ARG, matrixId)
                }
    }
}
