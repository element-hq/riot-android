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
package im.vector.fragments.keysbackuprestore

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.support.design.widget.TextInputLayout
import android.text.Editable
import android.text.SpannableString
import android.text.style.ClickableSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.text.set
import butterknife.BindView
import butterknife.OnClick
import butterknife.OnTextChanged
import im.vector.R
import im.vector.fragments.VectorBaseFragment
import org.matrix.androidsdk.MXSession

class KeysBackupRestoreFromKeyFragment : VectorBaseFragment() {

    companion object {
        fun newInstance() = KeysBackupRestoreFromKeyFragment()
    }

    override fun getLayoutResId() = R.layout.fragment_keys_backup_restore_from_key

    private lateinit var viewModel: KeysBackupRestoreFromKeyViewModel

    private var mInteractionListener: KeysBackupRestoreFromKeyFragment.InteractionListener? = null

    @BindView(R.id.keys_backup_key_enter_til)
    lateinit var mKeyInputLayout: TextInputLayout
    @BindView(R.id.keys_restore_key_enter_edittext)
    lateinit var mKeyTextEdit: EditText

    @BindView(R.id.keys_restore_key_help_with_link)
    lateinit var helperTextWithLink: TextView

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(KeysBackupRestoreFromKeyViewModel::class.java)

        helperTextWithLink.text = spannableStringForHelperText(context!!)

        helperTextWithLink.setOnClickListener {
            mInteractionListener?.didSelectCreateNewRecoveryCode()
        }

        viewModel.isRestoring.observe(this, Observer {
            val isLoading = it ?: false
            if (isLoading) mInteractionListener!!.setShowWaitingView(context?.getString(R.string.keys_backup_restoring_waiting_message)) else mInteractionListener!!.setHideWaitingView()
        })

        mKeyTextEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onNext()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        viewModel.recoveryCodeErrorText.observe(this, Observer { newValue ->
            mKeyInputLayout.error = newValue
        })

    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is KeysBackupRestoreFromKeyFragment.InteractionListener) {
            mInteractionListener = context
        }
    }

    override fun onDetach() {
        super.onDetach()
        mInteractionListener = null
    }

    @OnTextChanged(R.id.keys_restore_key_enter_edittext)
    fun onPassphraseTextEditChange(s: Editable?) {
        s?.toString()?.let { viewModel.updateCode(it) }
    }


    @OnClick(R.id.keys_restore_button)
    fun onNext() {
        val value = viewModel.recoveryCode.value
        if (value.isNullOrBlank()) {
            viewModel.recoveryCodeErrorText.value = context?.getString(R.string.keys_backup_recovery_code_empty_error_message)
        } else {
            viewModel.recoverKeys(context!!, mInteractionListener!!.getSession(), mInteractionListener!!.getKeysVersion())
        }
    }


    //privates

    private fun spannableStringForHelperText(context: Context): SpannableString {
        val tapableText = context.getString(R.string.keys_backup_restore_setup_recovery_key)
        val helperText = context.getString(R.string.keys_backup_restore_with_key_helper_with_link, tapableText)

        val spanString = SpannableString(helperText)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View?) {

            }
        }
        val start = helperText.indexOf(tapableText)
        val end = start + tapableText.length
        spanString[start, end] = clickableSpan
        return spanString
    }

    interface InteractionListener {
        fun didSelectCreateNewRecoveryCode()
        fun getSession(): MXSession
        fun getKeysVersion(): String
        fun setShowWaitingView(status: String?)
        fun setHideWaitingView()
    }
}