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
package im.vector.fragments.keybackupsetup

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.TextInputLayout
import android.support.transition.TransitionManager
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import butterknife.BindView
import butterknife.OnClick
import com.nulabinc.zxcvbn.Zxcvbn
import im.vector.R
import im.vector.fragments.VectorBaseFragment
import im.vector.settings.VectorLocale
import im.vector.ui.PasswordStrengthBar


class KeybackupSetupStep2Fragment : VectorBaseFragment() {

    override fun getLayoutResId() = R.layout.keybackup_setup_step2_fragment

    @BindView(R.id.keybackup_root)
    lateinit var rootGroup: ViewGroup

    @BindView(R.id.keybackup_passphrase_enter_edittext)
    lateinit var mPassphraseTextEdit: EditText

    @BindView(R.id.keybackup_passphrase_enter_til)
    lateinit var mPassphraseInputLayout: TextInputLayout

    @BindView(R.id.keybackup_passphrase_confirm_edittext)
    lateinit var mPassphraseConfirmTextEdit: EditText

    @BindView(R.id.keybackup_passphrase_confirm_til)
    lateinit var mPassphraseConfirmInputLayout: TextInputLayout

    @BindView(R.id.keybackup_passphrase_security_progress)
    lateinit var mPassphraseProgressLevel: PasswordStrengthBar

    private val zxcvbn = Zxcvbn()

    private val mConfirmPassphraseTextWatcher by lazy {
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.confirmPassphrase.value = mPassphraseConfirmTextEdit.text.toString()
                viewModel.confirmPassphraseError.value = -1
            }

            override fun afterTextChanged(s: Editable?) {

            }
        }
    }

    private val mPassphraseTextWatcher by lazy {
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.passphrase.value = mPassphraseTextEdit.text.toString()
                viewModel.confirmPassphraseError.value = -1
            }

            override fun afterTextChanged(s: Editable?) {

            }
        }
    }

    private lateinit var viewModel: KeybackupSetupSharedViewModel

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(KeybackupSetupSharedViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        bindViewToViewModel()
    }

    private fun bindViewToViewModel() {

        viewModel.passwordStrength.observe(this, Observer { strength ->
            if (strength == null) {
                mPassphraseProgressLevel.strength = -1
                mPassphraseInputLayout.error = null
            } else {
                val score = strength.score
                mPassphraseProgressLevel.strength = score

                if (score in 1..2) {
                    val warning = strength.feedback?.getWarning(VectorLocale.applicationLocale)
                    if (warning != null) {
                        mPassphraseInputLayout.error = warning
                    }

                    val suggestions = strength.feedback?.suggestions
                    if (suggestions != null) {
                        mPassphraseInputLayout.error = suggestions.firstOrNull()
                    }

                } else {
                    mPassphraseInputLayout.error = null
                }

            }
        })

        viewModel.passphrase.observe(this, Observer<String> { newValue ->
            if (TextUtils.isEmpty(newValue)) {
                viewModel.passwordStrength.value = null
            } else {
                AsyncTask.execute {
                    val strength = zxcvbn.measure(newValue)
                    activity?.runOnUiThread {
                        viewModel.passwordStrength.value = strength
                    }
                }
            }

        })

        mPassphraseTextEdit.setText(viewModel.passphrase.value)
        mPassphraseTextEdit.addTextChangedListener(mPassphraseTextWatcher)

        mPassphraseConfirmTextEdit.setText(viewModel.confirmPassphrase.value)
        mPassphraseConfirmTextEdit.addTextChangedListener(mConfirmPassphraseTextWatcher)

        viewModel.showPasswordMode.observe(this, Observer {
            val shouldBeVisible = it ?: false
            if (shouldBeVisible) {
                mPassphraseTextEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                mPassphraseConfirmTextEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                mPassphraseTextEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                mPassphraseConfirmTextEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            mPassphraseTextEdit.setSelection(viewModel.passphrase.value?.length ?: 0)
        })

        viewModel.confirmPassphraseError.observe(this, Observer {
            val resId = it ?: -1
            TransitionManager.beginDelayedTransition(rootGroup)
            if (it == -1) {
                mPassphraseConfirmInputLayout.error = null
            } else {
                mPassphraseConfirmInputLayout.error = context?.getString(resId)
            }
        })

        mPassphraseConfirmTextEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doNext()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
    }

    @OnClick(R.id.keybackup_view_show_password)
    fun toggleVisibilityMode() {
        viewModel.showPasswordMode.value = !(viewModel.showPasswordMode.value ?: false)
    }

    @OnClick(R.id.keybackupsetup_step2_button)
    fun doNext() {
        when {
            TextUtils.isEmpty(viewModel.passphrase.value) -> {
                mPassphraseInputLayout.error = context?.getString(R.string.keybackup_setup_step2_passphrase_empty)
            }
            viewModel.passphrase.value != viewModel.confirmPassphrase.value -> {
                viewModel.confirmPassphraseError.value = R.string.keybackup_setup_step2_passphrase_no_match
            }
            viewModel.passwordStrength.value?.score ?: 0 < 3 -> {
                mPassphraseInputLayout.error = context?.getString(R.string.keybackup_setup_step2_passphrase_too_weak)
            }
            else -> {
                viewModel.recoveryKey.value = null
                viewModel.megolmBackupCreationInfo = null
                this.activity?.supportFragmentManager?.beginTransaction()?.apply {
                    replace(R.id.container, KeybackupSetupStep3Fragment.newInstance())
                    addToBackStack(null)
                    commit()
                }
            }
        }
    }


    companion object {
        fun newInstance() = KeybackupSetupStep2Fragment()
    }

}
