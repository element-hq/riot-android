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
package im.vector.fragments.keysbackup.settings

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import butterknife.BindView
import im.vector.R
import im.vector.activity.KeysBackupRestoreActivity
import im.vector.activity.KeysBackupSetupActivity
import im.vector.activity.util.WaitingViewData
import im.vector.fragments.VectorBaseFragment
import org.matrix.androidsdk.crypto.keysbackup.KeysBackupStateManager

class KeysBackupSettingsFragment : VectorBaseFragment(),
        KeysBackupSettingsRecyclerViewAdapter.AdapterListener {


    companion object {
        fun newInstance() = KeysBackupSettingsFragment()
    }

    override fun getLayoutResId() = R.layout.fragment_keys_backup_settings

    private lateinit var viewModel: KeysBackupSettingsViewModel

    @BindView(R.id.keys_backup_settings_recycler_view)
    lateinit var recyclerView: RecyclerView

    private var recyclerViewAdapter: KeysBackupSettingsRecyclerViewAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val layoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = layoutManager

        recyclerViewAdapter = KeysBackupSettingsRecyclerViewAdapter(activity!!)
        recyclerView.adapter = recyclerViewAdapter
        recyclerViewAdapter?.adapterListener = this


        viewModel = activity?.run {
            ViewModelProviders.of(this).get(KeysBackupSettingsViewModel::class.java)
        } ?: throw Exception("Invalid Activity")


        viewModel.keyBackupState.observe(this, Observer { keysBackupState ->
            if (keysBackupState == null) {
                //Cannot happen?
                viewModel.keyVersionTrust.value = null
            } else {
                when (keysBackupState) {
                    KeysBackupStateManager.KeysBackupState.Unknown,
                    KeysBackupStateManager.KeysBackupState.CheckingBackUpOnHomeserver -> {
                        viewModel.loadingEvent.value = WaitingViewData(context!!.getString(R.string.keys_backup_settings_checking_backup_state))
                    }
                    else -> {
                        viewModel.loadingEvent.value = null
                        //All this cases will be manage by looking at the backup trust object
                        viewModel.session?.crypto?.keysBackup?.mKeysBackupVersion?.let {
                            viewModel.getKeysBackupTrust(it)
                        } ?: run {
                            viewModel.keyVersionTrust.value = null
                        }
                    }
                }
            }

            // Update the adapter for each state change
            viewModel.session?.let { session ->
                recyclerViewAdapter?.updateWithTrust(session, viewModel.keyVersionTrust.value)
            }
        })

        viewModel.keyVersionTrust.observe(this, Observer {
            viewModel.session?.let { session ->
                recyclerViewAdapter?.updateWithTrust(session, it)
            }
        })
    }

    override fun didSelectSetupMessageRecovery() {
        context?.let {
            startActivity(KeysBackupSetupActivity.intent(it, viewModel.session?.myUserId
                    ?: "", false))
        }
    }

    override fun didSelectRestoreMessageRecovery() {
        context?.let {
            startActivity(KeysBackupRestoreActivity.intent(it, viewModel.session?.myUserId ?: ""))
        }
    }

    override fun didSelectDeleteSetupMessageRecovery() {
        activity?.let {
            AlertDialog.Builder(it)
                    .setTitle(R.string.keys_backup_settings_delete_confirm_title)
                    .setMessage(R.string.keys_backup_settings_delete_confirm_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.keys_backup_settings_delete_confirm_title) { _, _ ->
                        viewModel.deleteCurrentBackup(it)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setCancelable(true)
                    .show()
        }
    }

}