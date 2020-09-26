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

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import butterknife.BindView
import butterknife.OnClick
import com.airbnb.epoxy.EpoxyRecyclerView
import im.vector.BuildConfig
import im.vector.R
import im.vector.fragments.VectorBaseFragment
import im.vector.util.openUrlInExternalBrowser
import im.vector.util.state.MxAsync
import org.matrix.androidsdk.features.terms.TermsManager

class AcceptTermsFragment : VectorBaseFragment(), TermsController.Listener {


    lateinit var viewModel: AcceptTermsViewModel

    override fun getLayoutResId(): Int = R.layout.fragment_accept_terms

    private lateinit var termsController: TermsController

    @BindView(R.id.terms_recycler_view)
    lateinit var termsList: EpoxyRecyclerView

    @BindView(R.id.terms_bottom_accept)
    lateinit var acceptButton: Button

    @BindView(R.id.waitOverlay)
    lateinit var waitingModalOverlay: ViewGroup

    @BindView(R.id.termsBottomBar)
    lateinit var bottomBar: ViewGroup

    override fun onActivityCreated(savedInstanceState: Bundle?) {

        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProviders.of(requireActivity()).get(AcceptTermsViewModel::class.java)

        val description = when (viewModel.termsArgs.type) {
            TermsManager.ServiceType.IdentityService    -> getString(R.string.terms_description_for_identity_server)
            TermsManager.ServiceType.IntegrationManager -> getString(R.string.terms_description_for_integration_manager)
        }

        termsController = TermsController(description, this)
        termsList.setController(termsController)

        viewModel.loadTerms(getString(R.string.resources_language))

        viewModel.termsList.observe(this, Observer { terms ->
            when (terms) {
                is MxAsync.Loading -> {
                    bottomBar.isVisible = false
                    waitingModalOverlay.isVisible = true
                }
                is MxAsync.Error   -> {
                    waitingModalOverlay.isVisible = false
                    terms.stringResId.let { stringRes ->
                        AlertDialog.Builder(requireActivity())
                                .setMessage(stringRes)
                                .setPositiveButton(R.string.ok) { dialog, which ->
                                    activity?.finish()
                                }
                                .show()
                    }
                }
                is MxAsync.Success -> {
                    updateState(terms.value)
                    waitingModalOverlay.isVisible = false
                    bottomBar.isVisible = true
                    acceptButton.isEnabled = terms.value.all { it.accepted }
                }
                else               -> {
                    waitingModalOverlay.isVisible = false
                }
            }
        })

        viewModel.acceptTerms.observe(this, Observer { request ->
            when (request) {
                is MxAsync.Loading -> {
                    waitingModalOverlay.isVisible = true
                }
                is MxAsync.Error   -> {
                    waitingModalOverlay.isVisible = false
                    request.stringResId.let { stringRes ->
                        AlertDialog.Builder(requireActivity())
                                .setMessage(stringRes)
                                .setPositiveButton(R.string.ok, null)
                                .show()
                    }
                }
                is MxAsync.Success -> {
                    waitingModalOverlay.isVisible = false
                    activity?.setResult(Activity.RESULT_OK)
                    activity?.finish()
                }
                else               -> {
                    waitingModalOverlay.isVisible = false
                }

            }
        })
    }

    private fun updateState(terms: List<Term>) {
        termsController.setData(terms)
    }

    companion object {
        fun newInstance(): AcceptTermsFragment {
            return AcceptTermsFragment()
        }
    }


    override fun setChecked(term: Term, isChecked: Boolean) {
        viewModel.markTermAsAccepted(term.url, isChecked)
    }

    override fun review(term: Term) {
        openUrlInExternalBrowser(this.requireContext(), term.url)
    }

    @OnClick(R.id.terms_bottom_accept)
    fun onAcceptButton() {
        viewModel.acceptTerms()
    }


    @OnClick(R.id.terms_bottom_decline)
    fun onDeclineButton() {
        activity?.finish()
    }

    fun initialize(fragmentActivity: FragmentActivity) {
        viewModel = ViewModelProviders.of(fragmentActivity).get(AcceptTermsViewModel::class.java)

        val description = when (viewModel.termsArgs.type) {
            TermsManager.ServiceType.IdentityService    -> getString(R.string.terms_description_for_identity_server)
            TermsManager.ServiceType.IntegrationManager -> getString(R.string.terms_description_for_integration_manager)
        }

        termsController = TermsController(description, this)
        termsList.setController(termsController)
        termsController
        viewModel.loadTerms(getString(R.string.resources_language))

        viewModel.termsList.observe(this, Observer { terms ->
            when (terms) {
                is MxAsync.Loading -> {
                    Log.v("MxAsync: ", "is loading")
                }
                is MxAsync.Error   -> {
                    Log.v("MxAsync: ", "Error in AcceptTermsFragment.kt in viewModel.termsList")
                }
                is MxAsync.Success -> {
                    updateState(terms.value)
                    for (term in terms.value) setChecked(term, true)
                    viewModel.acceptTerms()
                }
            }
        })

        viewModel.acceptTerms.observe(this, Observer { request ->
            when (request) {
                is MxAsync.Loading -> {
                }
                is MxAsync.Error   -> {
                    Log.v("MxAsync: ", "Error in AcceptTermsFragment.kt in viewModel.acceptTerms")
                }
                is MxAsync.Success -> {
                    Log.v("MxAsync: ", "Success")
                }
            }
        })

        return
    }

    fun initialize(activity: AppCompatActivity) {
        viewModel = ViewModelProviders.of(activity).get(AcceptTermsViewModel::class.java)

        val description = when (viewModel.termsArgs.type) {
            TermsManager.ServiceType.IdentityService    -> getString(R.string.terms_description_for_identity_server)
            TermsManager.ServiceType.IntegrationManager -> getString(R.string.terms_description_for_integration_manager)
        }

        termsController = TermsController(description, this)
        termsList.setController(termsController)
        termsController
        viewModel.loadTerms(getString(R.string.resources_language))

        viewModel.termsList.observe(this, Observer { terms ->
            when (terms) {
                is MxAsync.Loading -> {
                    Log.v("MxAsync: ", "is loading")
                }
                is MxAsync.Error   -> {
                    Log.v("MxAsync: ", "Error in AcceptTermsFragment.kt in viewModel.termsList")
                }
                is MxAsync.Success -> {
                    updateState(terms.value)
                    for (term in terms.value) setChecked(term, true)
                    viewModel.acceptTerms()
                }
            }
        })

        viewModel.acceptTerms.observe(this, Observer { request ->
            when (request) {
                is MxAsync.Loading -> {
                }
                is MxAsync.Error   -> {
                    Log.v("MxAsync: ", "Error in AcceptTermsFragment.kt in viewModel.acceptTerms")
                }
                is MxAsync.Success -> {
                    Log.v("MxAsync: ", "Success")
                }
            }
        })

        return
    }
}