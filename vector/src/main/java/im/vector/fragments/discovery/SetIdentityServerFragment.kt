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
import android.os.Parcelable
import android.text.Editable
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import butterknife.BindView
import butterknife.OnTextChanged
import com.airbnb.mvrx.MvRx
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import im.vector.R
import im.vector.activity.MXCActionBarActivity
import im.vector.activity.ReviewTermsActivity
import im.vector.activity.util.TERMS_REQUEST_CODE
import im.vector.extensions.withArgs
import im.vector.fragments.VectorBaseMvRxFragment
import kotlinx.android.parcel.Parcelize
import org.matrix.androidsdk.features.terms.TermsManager

@Parcelize
data class SetIdentityServerFragmentArgs(
        var matrixId: String,
        var serverName: String? = null
) : Parcelable


class SetIdentityServerFragment : VectorBaseMvRxFragment() {

    override fun getLayoutResId() = R.layout.fragment_set_identity_server

    override fun getMenuRes() = R.menu.menu_phone_number_addition

    @BindView(R.id.discovery_identity_server_enter_til)
    lateinit var mKeyInputLayout: com.google.android.material.textfield.TextInputLayout

    @BindView(R.id.discovery_identity_server_enter_edittext)
    lateinit var mKeyTextEdit: EditText

    @BindView(R.id.discovery_identity_server_loading)
    lateinit var mProgressBar: ProgressBar


    private val viewModel by fragmentViewModel(SetIdentityServerViewModel::class)

    lateinit var sharedViewModel: DiscoverySharedViewModel

    override fun invalidate() = withState(viewModel) { state ->
        if (state.isVerifyingServer) {
            mKeyTextEdit.isEnabled = false
            mProgressBar.isVisible = true
        } else {
            mKeyTextEdit.isEnabled = true
            mProgressBar.isVisible = false
        }
        val newText = state.newIdentityServer ?: ""
        if (!newText.equals(mKeyTextEdit.text.toString())) {
            mKeyTextEdit.setText(newText)
        }
        mKeyInputLayout.error = state.errorMessageId?.let { getString(it) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_add_phone_number -> {
                withState(viewModel) { state ->
                    if (!state.isVerifyingServer) {
                        viewModel.doChangeServerName()
                    }
                }
                return true
            }
            else                         -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        sharedViewModel = ViewModelProviders.of(requireActivity()).get(DiscoverySharedViewModel::class.java)

        mKeyTextEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                withState(viewModel) { state ->
                    if (!state.isVerifyingServer) {
                        viewModel.doChangeServerName()
                    }
                }
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }


        viewModel.navigateEvent.observe(this, Observer {
            it.getContentIfNotHandled()?.let { event ->

                when (event) {
                    is NavigateEvent.NoTerms       -> {
                        AlertDialog.Builder(requireActivity())
                                .setTitle(R.string.settings_discovery_no_terms_title)
                                .setMessage(R.string.settings_discovery_no_terms)
                                .setPositiveButton(R.string._continue) { dialog, which ->
                                    processIdentityServerChange()
                                }
                                .setNegativeButton(R.string.cancel, null)
                                .show()
                    }

                    is NavigateEvent.TermsAccepted -> {
                        processIdentityServerChange()
                    }

                    is NavigateEvent.ShowTerms     -> {
                        ReviewTermsActivity.intent(requireContext(),
                                TermsManager.ServiceType.IdentityService,
                                SetIdentityServerViewModel.sanitatizeBaseURL(event.newIdentityServer),
                                null).also {
                            startActivityForResult(it, TERMS_REQUEST_CODE)
                        }
                    }
                    else                           -> {
                    }

                }
            }
        })

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == TERMS_REQUEST_CODE) {
            if (Activity.RESULT_OK == resultCode) {
                processIdentityServerChange()
            } else {
                //add some error?
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun processIdentityServerChange() {
        withState(viewModel) { state ->
            if (state.newIdentityServer != null) {
                sharedViewModel.requestChangeToIdentityServer(state.newIdentityServer)
                requireFragmentManager().popBackStack()
            }
        }
    }

    @OnTextChanged(R.id.discovery_identity_server_enter_edittext)
    fun onTextEditChange(s: Editable?) {
        s?.toString()?.let { viewModel.updateServerName(it) }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MXCActionBarActivity)?.supportActionBar?.setTitle(R.string.identity_server)
    }

    companion object {
        fun newInstance(matrixId: String, existingServer: String?) = SetIdentityServerFragment()
                .withArgs {
                    putParcelable(MvRx.KEY_ARG, SetIdentityServerFragmentArgs(matrixId, existingServer))
                }
    }
}