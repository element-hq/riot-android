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

package im.vector.fragments.keysbackup.setup

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import butterknife.BindView
import butterknife.OnClick
import im.vector.R
import im.vector.fragments.VectorBaseFragment
import im.vector.ui.arch.LiveEvent

class KeysBackupSetupStep1Fragment : VectorBaseFragment() {

    companion object {
        fun newInstance() = KeysBackupSetupStep1Fragment()
    }

    override fun getLayoutResId() = R.layout.fragment_keys_backup_setup_step1

    private lateinit var viewModel: KeysBackupSetupSharedViewModel

    @BindView(R.id.keys_backup_setup_step1_advanced)
    lateinit var advancedOptionText: TextView


    @BindView(R.id.keys_backup_setup_step1_manualExport)
    lateinit var manualExportButton: Button


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(KeysBackupSetupSharedViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        viewModel.showManualExport.observe(this, Observer {
            val showOption = it ?: false
            //Can't use isVisible because the kotlin compiler will crash with  Back-end (JVM) Internal error: wrong code generated
            advancedOptionText.visibility = if (showOption) View.VISIBLE else View.GONE
            manualExportButton.visibility = if (showOption) View.VISIBLE else View.GONE
        })

    }

    @OnClick(R.id.keys_backup_setup_step1_button)
    fun onButtonClick() {
        viewModel.navigateEvent.value = LiveEvent(KeysBackupSetupSharedViewModel.NAVIGATE_TO_STEP_2)
    }

    @OnClick(R.id.keys_backup_setup_step1_manualExport)
    fun onManualExportClick() {
        viewModel.navigateEvent.value = LiveEvent(KeysBackupSetupSharedViewModel.NAVIGATE_MANUAL_EXPORT)
    }
}
