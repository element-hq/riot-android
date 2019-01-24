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
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import butterknife.BindView
import im.vector.Matrix
import im.vector.R
import im.vector.activity.MXCActionBarActivity
import im.vector.activity.VectorAppCompatActivity
import im.vector.fragments.VectorBaseFragment
import org.matrix.androidsdk.crypto.keysbackup.MegolmBackupCreationInfo
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.callback.SuccessErrorCallback
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.rest.model.keys.KeysVersion
import org.matrix.androidsdk.util.Log
import java.lang.Exception

class KeybackupSetupStep3Fragment : VectorBaseFragment() {

    override fun getLayoutResId() = R.layout.keybackup_setup_step3_fragment

    @BindView(R.id.keybackupsetup_step3_copy_button)
    lateinit var mCopyButton: Button


    @BindView(R.id.keybackupsetup_step3_button)
    lateinit var mFinishButton: Button

    @BindView(R.id.keybackup_recovery_key_text)
    lateinit var mRecoveryKeyTextView: TextView

    @BindView(R.id.keybackup_recovery_key_spinner)
    lateinit var mSpinner: ProgressBar

    companion object {
        fun newInstance() = KeybackupSetupStep3Fragment()
        private val LOG_TAG = KeybackupSetupStep3Fragment::class.java.name
    }

    private lateinit var viewModel: KeybackupSetupSharedViewModel

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = activity?.run {
            ViewModelProviders.of(this).get(KeybackupSetupSharedViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        val session = (activity as? MXCActionBarActivity)?.session
                ?: Matrix.getInstance(context)?.getSession(null)

        session?.let { mxSession ->
            mxSession.crypto?.keysBackup?.prepareKeysBackupVersion(viewModel.passphrase.value!!, object : SuccessErrorCallback<MegolmBackupCreationInfo> {
                override fun onSuccess(info: MegolmBackupCreationInfo) {
                    viewModel.recoveryKey.value = info.recoveryKey
                    viewModel.megolmBackupCreationInfo = info
                }

                override fun onUnexpectedError(e: Exception?) {
                    activity?.let {
                        AlertDialog.Builder(it)
                                .setTitle(R.string.unknown_error)
                                .setMessage(e?.localizedMessage)
                                .setPositiveButton(R.string.ok) { _, _ ->
                                    //nop
                                    activity?.onBackPressed()
                                }
                                .show()
                    }

                }
            })
        }

        viewModel.recoveryKey.observe(this, Observer { newValue ->
            if (newValue == null || newValue.isEmpty()) {
                mSpinner.visibility = View.VISIBLE
                mSpinner.animate()
                mRecoveryKeyTextView.text = null
            } else {
                mSpinner.visibility = View.GONE
                mRecoveryKeyTextView.text = newValue
            }
        })

        mCopyButton.setOnClickListener {
            val recoveryKey = viewModel.recoveryKey.value
            if (recoveryKey != null) {
                val share = Intent(android.content.Intent.ACTION_SEND)
                share.type = "text/plain"
                share.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                // Add data to the intent, the receiving app will decide
                // what to do with it.
                share.putExtra(Intent.EXTRA_SUBJECT, context?.getString(R.string.recovery_key))
                share.putExtra(Intent.EXTRA_TEXT, recoveryKey)

                startActivity(Intent.createChooser(share, context?.getString(R.string.keybackup_setup_step3_share_intent_chooser_title)))
                viewModel.copyHasBeenMade = true
            }
        }

        mFinishButton.setOnClickListener {
            if (viewModel.megolmBackupCreationInfo == null) {
                //nothing
            } else if (viewModel.copyHasBeenMade) {
                val session = (activity as? MXCActionBarActivity)?.session
                        ?: Matrix.getInstance(context)?.getSession(null)
                val keysBackup = session?.crypto?.keysBackup
                if (keysBackup != null) {
                    viewModel.isCreatingBackupVersion.value = true
                    keysBackup.createKeyBackupVersion(viewModel.megolmBackupCreationInfo!!, object : ApiCallback<KeysVersion> {

                        override fun onSuccess(info: KeysVersion?) {
                            viewModel.isCreatingBackupVersion.value = false
                            activity?.finish()
                        }

                        override fun onUnexpectedError(e: Exception?) {
                            Log.e(LOG_TAG, "## createKeyBackupVersion ${e?.localizedMessage}")
                            viewModel.isCreatingBackupVersion.value = false
                            activity?.run {
                                runOnUiThread {
                                    AlertDialog.Builder(this)
                                            .setTitle(getString(R.string.unexpected_error))
                                            .setMessage(e?.localizedMessage)
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show()
                                }
                            }
                        }

                        override fun onNetworkError(e: Exception?) {
                            Log.e(LOG_TAG, "## createKeyBackupVersion ${e?.localizedMessage}")
                            viewModel.isCreatingBackupVersion.value = false
                            activity?.run {
                                runOnUiThread {
                                    AlertDialog.Builder(this)
                                            .setTitle(getString(R.string.network_error))
                                            .setMessage(e?.localizedMessage)
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show()
                                }
                            }
                        }

                        override fun onMatrixError(e: MatrixError?) {
                            Log.e(LOG_TAG, "## createKeyBackupVersion ${e?.mReason}")
                            viewModel.isCreatingBackupVersion.value = false
                            activity?.run {
                                runOnUiThread {
                                    AlertDialog.Builder(this)
                                            .setTitle(getString(R.string.matrix_error))
                                            .setMessage(e?.mReason)
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show()
                                }
                            }
                        }
                    })
                }
            } else {
                Toast.makeText(context, R.string.keybackup_setup_step3_please_make_copy, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.isCreatingBackupVersion.observe(this, Observer { newValue ->
            val isLoading = newValue ?: false
            if (isLoading) {
                (activity as? VectorAppCompatActivity)?.showWaitingView()
            } else {
                (activity as? VectorAppCompatActivity)?.hideWaitingView()
            }
        })
    }

}
