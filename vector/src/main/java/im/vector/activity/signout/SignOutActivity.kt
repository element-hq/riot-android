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

package im.vector.activity.signout

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.DialogInterface
import android.support.design.widget.TextInputEditText
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import butterknife.BindView
import butterknife.OnClick
import im.vector.R
import im.vector.activity.CommonActivityUtils
import im.vector.activity.KeysBackupManageActivity
import im.vector.activity.MXCActionBarActivity
import im.vector.ui.themes.ThemeUtils
import im.vector.util.PERMISSIONS_FOR_WRITING_FILES
import im.vector.util.PERMISSION_REQUEST_CODE
import im.vector.util.allGranted
import im.vector.util.checkPermissions
import org.matrix.androidsdk.crypto.keysbackup.KeysBackupStateManager
import org.matrix.androidsdk.rest.callback.SimpleApiCallback

class SignOutActivity : MXCActionBarActivity() {
    private lateinit var viewModel: SignOutViewModel

    @BindView(R.id.sign_out_backup_status)
    lateinit var backupStatus: TextView

    @BindView(R.id.sign_out_backup_status_icon)
    lateinit var backupStatusIcon: ImageView

    @BindView(R.id.sign_out_backup_status_progress)
    lateinit var backupStatusProgress: View

    @BindView(R.id.sign_out_backup_start)
    lateinit var startBackup: View

    @BindView(R.id.sign_out_export_file_group)
    lateinit var exportViews: View

    @BindView(R.id.sign_out_sign_out_info)
    lateinit var signOutInfo: TextView

    @BindView(R.id.sign_out_sign_out)
    lateinit var signOut: View

    override fun getLayoutRes() = R.layout.activity_sign_out

    override fun getTitleRes() = R.string.title_activity_sign_out

    override fun initUiAndData() {
        super.initUiAndData()

        configureToolbar()
        waitingView = findViewById(R.id.waiting_view)

        mSession = getSession(intent)

        if (mSession == null) {
            finish()
        }

        viewModel = ViewModelProviders.of(this).get(SignOutViewModel::class.java)

        bindViewToViewModel()

        viewModel.init(mSession)
    }

    private fun bindViewToViewModel() {
        viewModel.keysBackupState.observe(this, Observer {
            when (it) {
                KeysBackupStateManager.KeysBackupState.ReadyToBackUp -> {
                    backupStatus.setText(R.string.sign_out_activity_backup_status_up_to_date)
                    backupStatusIcon.setImageResource(R.drawable.unit_test_ok)
                    backupStatusIcon.isVisible = true
                    backupStatusProgress.isVisible = false
                    startBackup.isVisible = false
                    exportViews.isVisible = false
                }
                KeysBackupStateManager.KeysBackupState.BackingUp,
                KeysBackupStateManager.KeysBackupState.WillBackUp -> {
                    backupStatus.setText(R.string.sign_out_activity_backup_status_backuping)
                    backupStatusIcon.isVisible = false
                    backupStatusProgress.isVisible = true
                    startBackup.isVisible = false
                    exportViews.isVisible = false
                }
                else -> {
                    backupStatus.setText(R.string.sign_out_activity_backup_status_no_backup)
                    backupStatusIcon.setImageResource(R.drawable.unit_test_ko)
                    backupStatusIcon.isVisible = true
                    backupStatusProgress.isVisible = false
                    startBackup.isVisible = true
                    exportViews.isVisible = true
                }
            }

            updateSignOutSection()
        })

        viewModel.keysExportedToFile.observe(this, Observer { updateSignOutSection() })
    }

    private fun updateSignOutSection() {
        if (canSignOutSafely()) {
            signOutInfo.setText(R.string.sign_out_activity_sign_out_ok)
            signOut.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorAccent))
        } else {
            signOutInfo.setText(R.string.sign_out_activity_sign_out_warning)
            signOut.setBackgroundColor(ContextCompat.getColor(this, R.color.vector_warning_color))
        }
    }

    /**
     * Return true if user has backed up its keys or has exported keys to a file
     */
    private fun canSignOutSafely(): Boolean {
        return viewModel.keysBackupState.value == KeysBackupStateManager.KeysBackupState.ReadyToBackUp
                || viewModel.keysExportedToFile.value == true
    }

    @OnClick(R.id.sign_out_backup_start)
    fun startBackup() {
        startActivity(KeysBackupManageActivity.intent(this, mSession.myUserId))
    }

    @OnClick(R.id.sign_out_sign_out)
    fun signOut() {
        if (canSignOutSafely()) {
            // User has backed up its keys or has exported keys to a file, allow sign out
            doSignOut()
            return
        }

        // Display a last warning
        AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_warning)
                .setMessage(R.string.sign_out_activity_sign_out_anyway_dialog_content)
                .setPositiveButton(R.string.sign_out_activity_sign_out_anyway_dialog_action
                ) { _, _ ->
                    doSignOut()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
                // Red positive button
                .getButton(DialogInterface.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.vector_warning_color))
    }

    private fun doSignOut() {
        showWaitingView()

        CommonActivityUtils.logout(this)
    }

    /**
     * Manage the e2e keys export.
     */
    @OnClick(R.id.sign_out_export_file)
    fun exportKeysToFile() {
        if (checkPermissions(PERMISSIONS_FOR_WRITING_FILES, this, PERMISSION_REQUEST_CODE)) {
            doExportKeysToFile()
        }
    }

    private fun doExportKeysToFile() {
        val dialogLayout = layoutInflater.inflate(R.layout.dialog_export_e2e_keys, null)
        val builder = AlertDialog.Builder(this)
                .setTitle(R.string.encryption_export_room_keys)
                .setView(dialogLayout)

        val passPhrase1EditText = dialogLayout.findViewById<TextInputEditText>(R.id.dialog_e2e_keys_passphrase_edit_text)
        val passPhrase2EditText = dialogLayout.findViewById<TextInputEditText>(R.id.dialog_e2e_keys_confirm_passphrase_edit_text)
        val exportButton = dialogLayout.findViewById<Button>(R.id.dialog_e2e_keys_export_button)
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                exportButton.isEnabled = !TextUtils.isEmpty(passPhrase1EditText.text) && TextUtils.equals(passPhrase1EditText.text, passPhrase2EditText.text)
            }

            override fun afterTextChanged(s: Editable) {

            }
        }

        passPhrase1EditText.addTextChangedListener(textWatcher)
        passPhrase2EditText.addTextChangedListener(textWatcher)

        val exportDialog = builder.show()

        exportButton.setOnClickListener {
            showWaitingView()

            CommonActivityUtils.exportKeys(mSession, passPhrase1EditText.text.toString(), object : SimpleApiCallback<String>(this) {

                override fun onSuccess(filename: String) {
                    hideWaitingView()

                    viewModel.keysExportedToFile.value = true

                    AlertDialog.Builder(this@SignOutActivity)
                            .setMessage(getString(R.string.encryption_export_saved_as, filename))
                            .setCancelable(false)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                }
            })

            exportDialog.dismiss()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (allGranted(grantResults)) {
            if (requestCode == PERMISSION_REQUEST_CODE) {
                doExportKeysToFile()
            }
        }
    }
}
