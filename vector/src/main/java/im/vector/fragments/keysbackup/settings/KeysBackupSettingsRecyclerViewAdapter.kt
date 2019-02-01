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

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.view.isGone
import androidx.core.view.isVisible
import butterknife.BindView
import butterknife.ButterKnife
import im.vector.R
import im.vector.ui.list.GenericItemViewHolder
import im.vector.ui.list.GenericRecyclerViewItem
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.crypto.keysbackup.KeysBackupStateManager
import org.matrix.androidsdk.crypto.keysbackup.KeysBackupVersionTrust
import org.matrix.androidsdk.crypto.keysbackup.KeysBackupVersionTrustSignature

class KeysBackupSettingsRecyclerViewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {


    var context: Context? = null

    private var infoList: List<GenericRecyclerViewItem> = ArrayList()
    private var isBackupAlreadySetup = false
    var adapterListener: AdapterListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            GenericItemViewHolder.resId -> GenericItemViewHolder(inflater.inflate(viewType, parent, false))
            else -> FooterViewHolder(inflater.inflate(viewType, parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < infoList.size) {
            GenericItemViewHolder.resId
        } else {
            R.layout.item_keys_backup_settings_button_footer
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position < infoList.size) {
            (holder as? GenericItemViewHolder)?.bind(infoList[position])
        } else if (holder is FooterViewHolder) {
            if (!isBackupAlreadySetup) {
                holder.button2.isGone = true
                holder.button1.setText(R.string.keys_backup_settings_setup_button)
                holder.button1.isVisible = true
                holder.button1.setOnClickListener {
                    adapterListener?.didSelectSetupMessageRecovery()
                }
            } else {
                holder.button1.setText(R.string.keys_backup_settings_restore_backup_button)
                holder.button1.isVisible = true
                holder.button1.setOnClickListener {
                    adapterListener?.didSelectRestoreMessageRecovery()
                }
                holder.button2.setText(R.string.keys_backup_settings_delete_backup_button)
                holder.button2.isVisible = true
                holder.button2.setOnClickListener {
                    adapterListener?.didSelectDeleteSetupMessageRecovery()
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return infoList.size + 1 /*footer*/
    }


    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        context = recyclerView.context
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        context = null
    }

    fun updateWithTrust(session: MXSession, keyBackupVersionTrust: KeysBackupVersionTrust?) {

        val keyBackupState = session.crypto?.keysBackup?.state
        val keyVersionResult = session.crypto?.keysBackup?.mKeysBackupVersion


        val infos = ArrayList<GenericRecyclerViewItem>()
        var itemSummary: GenericRecyclerViewItem? = null

        when (keyBackupState) {
            KeysBackupStateManager.KeysBackupState.Unknown,
            KeysBackupStateManager.KeysBackupState.CheckingBackUpOnHomeserver -> {
                //In this cases recycler view is hidden any way
                //so do nothing
            }
            KeysBackupStateManager.KeysBackupState.Disabled -> {
                itemSummary = GenericRecyclerViewItem(context?.getString(R.string.keys_backup_settings_status_not_setup)
                        ?: "",
                        style = GenericRecyclerViewItem.STYLE.BIG_TEXT)
                infos.add(itemSummary)
                isBackupAlreadySetup = false
            }
            KeysBackupStateManager.KeysBackupState.WrongBackUpVersion,
            KeysBackupStateManager.KeysBackupState.NotTrusted,
            KeysBackupStateManager.KeysBackupState.Enabling -> {
                itemSummary = GenericRecyclerViewItem(context?.getString(R.string.keys_backup_settings_status_ko)
                        ?: "", style = GenericRecyclerViewItem.STYLE.BIG_TEXT)
                itemSummary.description = keyBackupState.toString()
                itemSummary.endIconResourceId = R.drawable.unit_test_ko
                infos.add(itemSummary)
                isBackupAlreadySetup = true
            }
            KeysBackupStateManager.KeysBackupState.ReadyToBackUp,
            KeysBackupStateManager.KeysBackupState.WillBackUp,
            KeysBackupStateManager.KeysBackupState.BackingUp -> {
                itemSummary = GenericRecyclerViewItem(context?.getString(R.string.keys_backup_settings_status_ok)
                        ?: "", style = GenericRecyclerViewItem.STYLE.BIG_TEXT)
                itemSummary.endIconResourceId = R.drawable.unit_test_ok
                infos.add(itemSummary)
                isBackupAlreadySetup = true
            }
        }

        if (keyBackupVersionTrust != null) {

            //Add infos
            infos.add(GenericRecyclerViewItem("Version", keyVersionResult?.version
                    ?: ""))
            infos.add(GenericRecyclerViewItem("Algorithm", keyVersionResult?.algorithm
                    ?: ""))

            keyBackupVersionTrust.signatures.forEach {
                val signatureInfo = GenericRecyclerViewItem("Signature")
                val isDeviceKnown = it.device != null
                val isDeviceVerified = it.device?.isVerified ?: false
                val isSignatureValid = it.valid
                val deviceId: String = it.deviceId ?: ""

                if (!isDeviceKnown) {
                    signatureInfo.description = context?.getString(R.string.keys_backup_settings_signature_from_unknown_device, deviceId)
                    signatureInfo.endIconResourceId = R.drawable.e2e_warning
                    itemSummary?.description = context?.getString(R.string.keys_backup_settings_unverifiable_device)
                } else {
                    if (isSignatureValid) {
                        if (session.credentials.deviceId == it.deviceId) {
                            signatureInfo.description = context?.getString(R.string.keys_backup_settings_valid_signature_from_this_device)
                            signatureInfo.endIconResourceId = R.drawable.e2e_verified
                        } else {
                            if (isDeviceVerified) {
                                signatureInfo.description = context?.getString(R.string.keys_backup_settings_valid_signature_from_verified_device, deviceId)
                                signatureInfo.endIconResourceId = R.drawable.e2e_verified
                            } else {
                                signatureInfo.description = context?.getString(R.string.keys_backup_settings_valid_signature_from_unverified_device, deviceId)
                                signatureInfo.endIconResourceId = R.drawable.e2e_warning
                                val action = getVerifySignatureAction(it)
                                signatureInfo.buttonAction = action
                                itemSummary?.description = context?.getString(R.string.keys_backup_settings_verify_device_now, it.device!!.displayName())
                            }
                        }
                    } else {
                        //Invalid signature
                        signatureInfo.endIconResourceId = R.drawable.e2e_warning
                        if (isDeviceVerified) {
                            signatureInfo.description = context?.getString(R.string.keys_backup_settings_invalid_signature_from_verified_device, deviceId)
                        } else {
                            signatureInfo.description = context?.getString(R.string.keys_backup_settings_invalid_signature_from_unverified_device, deviceId)
                        }
                    }
                }

                infos.add(signatureInfo)
            } //and for each

        }
        infoList = infos
        notifyDataSetChanged()
    }

    private fun getVerifySignatureAction(signature: KeysBackupVersionTrustSignature): GenericRecyclerViewItem.Action {
        val action = GenericRecyclerViewItem.Action("Verify")
        action.perform = Runnable { adapterListener?.displayDeviceVerificationDialog(signature) }
        return action
    }

    class FooterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            ButterKnife.bind(this, itemView)
        }

        @BindView(R.id.keys_backup_settings_footer_button1)
        lateinit var button1: Button

        @BindView(R.id.keys_backup_settings_footer_button2)
        lateinit var button2: Button

        fun bind() {

        }
    }

    interface AdapterListener {
        fun didSelectSetupMessageRecovery()
        fun didSelectRestoreMessageRecovery()
        fun didSelectDeleteSetupMessageRecovery()
        fun displayDeviceVerificationDialog(signature: KeysBackupVersionTrustSignature)
    }

}