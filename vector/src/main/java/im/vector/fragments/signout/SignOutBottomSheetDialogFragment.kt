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

package im.vector.fragments.signout

import android.app.Activity
import android.app.Dialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.BottomSheetDialog
import android.support.design.widget.BottomSheetDialogFragment
import android.support.transition.TransitionManager
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import androidx.core.widget.toast
import butterknife.BindView
import butterknife.ButterKnife
import im.vector.Matrix
import im.vector.R
import im.vector.activity.KeysBackupManageActivity
import im.vector.activity.KeysBackupSetupActivity
import im.vector.activity.MXCActionBarActivity
import im.vector.extensions.withArgs
import org.matrix.androidsdk.crypto.keysbackup.KeysBackupStateManager


class SignOutBottomSheetDialogFragment : BottomSheetDialogFragment() {


    @BindView(R.id.bottom_sheet_signout_warning_text)
    lateinit var sheetTitle: TextView

    @BindView(R.id.bottom_sheet_signout_backingup_status_group)
    lateinit var backingUpStatusGroup: ViewGroup

    @BindView(R.id.keys_backup_setup)
    lateinit var setupClickableView: View

    @BindView(R.id.keys_backup_activate)
    lateinit var activateClickableView: View

    @BindView(R.id.keys_backup_dont_want)
    lateinit var dontWantClickableView: View

    @BindView(R.id.bottom_sheet_signout_icon_progress_bar)
    lateinit var backupProgress: ProgressBar

    @BindView(R.id.bottom_sheet_signout_icon)
    lateinit var backupCompleteImage: ImageView

    @BindView(R.id.bottom_sheet_backup_status_text)
    lateinit var backupStatusTex: TextView

    @BindView(R.id.bottom_sheet_signout_button)
    lateinit var signoutClickableView: View


    @BindView(R.id.root_layout)
    lateinit var rootLayout: ViewGroup


    var onSignOut: Runnable? = null

    companion object {
        fun newInstance(matrixId: String) = SignOutBottomSheetDialogFragment()
                .withArgs {
                    putString(MXCActionBarActivity.EXTRA_MATRIX_ID, matrixId)
                }

        private const val EXPORT_REQ = 0
    }

    init {
        isCancelable = true
    }

    private lateinit var viewModel: SignOutViewModel

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProviders.of(this).get(SignOutViewModel::class.java)
        Matrix.getInstance(context).getSession(getExtraMatrixID()).let {
            viewModel.init(it)
        }

        setupClickableView.setOnClickListener {
            context?.let { context ->
                startActivityForResult(KeysBackupSetupActivity.intent(context, getExtraMatrixID(), true), EXPORT_REQ)
            }
        }

        activateClickableView.setOnClickListener {
            context?.let { context ->
                startActivity(KeysBackupManageActivity.intent(context, getExtraMatrixID()))
            }
        }

        signoutClickableView.setOnClickListener {
            this.onSignOut?.run()
        }

        dontWantClickableView.setOnClickListener { _ ->
            context?.let {
                AlertDialog.Builder(it)
                        .setTitle(R.string.are_you_sure)
                        .setMessage(R.string.sign_out_bottom_sheet_will_lose_secure_messages)
                        .setPositiveButton(R.string.backup) { _, _ ->
                            when (viewModel.keysBackupState.value) {
                                KeysBackupStateManager.KeysBackupState.NotTrusted -> {
                                    context?.let { context ->
                                        startActivity(KeysBackupManageActivity.intent(context, getExtraMatrixID()))
                                    }
                                }
                                KeysBackupStateManager.KeysBackupState.Disabled -> {
                                    context?.let { context ->
                                        startActivityForResult(KeysBackupSetupActivity.intent(context, getExtraMatrixID(), true), EXPORT_REQ)
                                    }
                                }
                                KeysBackupStateManager.KeysBackupState.BackingUp,
                                KeysBackupStateManager.KeysBackupState.WillBackUp -> {
                                    //keys are already backing up please wait
                                    context?.toast(R.string.keys_backup_is_not_finished_please_wait)
                                }
                                else -> {
                                    //nop
                                }
                            }
                        }
                        .setNegativeButton(R.string.action_sign_out) { _, _ ->
                            onSignOut?.run()
                        }
                        .show()
            }

        }

        viewModel.keysExportedToFile.observe(this, Observer {
            val hasExportedToFile = it ?: false
            if (hasExportedToFile) {
                //We can allow to sign out

                sheetTitle.text = getString(R.string.action_sign_out_confirmation_simple)

                signoutClickableView.isVisible = true
                dontWantClickableView.isVisible = false
                setupClickableView.isVisible = false
                activateClickableView.isVisible = false
                backingUpStatusGroup.isVisible = false
            }
        })

        viewModel.keysBackupState.observe(this, Observer {
            if (viewModel.keysExportedToFile.value == true) {
                //ignore this
                return@Observer
            }
            TransitionManager.beginDelayedTransition(rootLayout)
            when (it) {
                KeysBackupStateManager.KeysBackupState.ReadyToBackUp -> {
                    signoutClickableView.isVisible = true
                    dontWantClickableView.isVisible = false
                    setupClickableView.isVisible = false
                    activateClickableView.isVisible = false
                    backingUpStatusGroup.isVisible = true

                    backupProgress.isVisible = false
                    backupCompleteImage.isVisible = true
                    backupStatusTex.text = getString(R.string.keys_backup_info_keys_all_backup_up)

                    sheetTitle.text = getString(R.string.action_sign_out_confirmation_simple)
                }
                KeysBackupStateManager.KeysBackupState.BackingUp,
                KeysBackupStateManager.KeysBackupState.WillBackUp -> {
                    backingUpStatusGroup.isVisible = true
                    sheetTitle.text = getString(R.string.sign_out_bottom_sheet_warning_backing_up)
                    dontWantClickableView.isVisible = true
                    setupClickableView.isVisible = false
                    activateClickableView.isVisible = false

                    backupProgress.isVisible = true
                    backupCompleteImage.isVisible = false
                    backupStatusTex.text = getString(R.string.sign_out_bottom_sheet_backing_up_keys)

                }
                KeysBackupStateManager.KeysBackupState.NotTrusted -> {
                    backingUpStatusGroup.isVisible = false
                    dontWantClickableView.isVisible = true
                    setupClickableView.isVisible = false
                    activateClickableView.isVisible = true
                    sheetTitle.text = getString(R.string.sign_out_bottom_sheet_warning_backup_not_active)
                }
                else -> {
                    backingUpStatusGroup.isVisible = false
                    dontWantClickableView.isVisible = true
                    setupClickableView.isVisible = true
                    activateClickableView.isVisible = false
                    sheetTitle.text = getString(R.string.sign_out_bottom_sheet_warning_no_backup)
                }
            }

//            updateSignOutSection()
        })

    }

    private fun getExtraMatrixID() = arguments?.getString(MXCActionBarActivity.EXTRA_MATRIX_ID)
            ?: ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_logout_and_backup, container, false)
        ButterKnife.bind(this, view)
        return view
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        //We want to force the bottom sheet initial state to expanded
        (dialog as? BottomSheetDialog)?.let { bottomSheetDialog ->
            bottomSheetDialog.setOnShowListener { dialog ->
                val d = dialog as BottomSheetDialog
                (d.findViewById<View>(android.support.design.R.id.design_bottom_sheet) as? FrameLayout)?.let {
                    BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
        return dialog
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == EXPORT_REQ) {
                val manualExportDone = data?.getBooleanExtra(KeysBackupSetupActivity.MANUAL_EXPORT, false)
                viewModel.keysExportedToFile.value = manualExportDone
            }
        }
    }

}