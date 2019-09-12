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

import android.content.Context
import android.view.View
import com.airbnb.epoxy.TypedEpoxyController
import com.airbnb.mvrx.Fail
import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.Success
import com.google.i18n.phonenumbers.PhoneNumberUtil
import im.vector.R


class SettingsDiscoveryController(private val context: Context, private val interactionListener: InteractionListener?) : TypedEpoxyController<DiscoverySettingsState>() {

    override fun buildModels(data: DiscoverySettingsState?) {
        if (data == null) return

        buildIdentityServerSection(data)

        val hasIdentityServer = data.identityServer.invoke().isNullOrBlank().not()

        if (hasIdentityServer) {
            buildMailSection(data)
            buildPhoneNumberSection(data)
        }

    }

    private fun buildPhoneNumberSection(data: DiscoverySettingsState) {
        settingsSectionTitle {
            id("pns")
            titleResId(R.string.settings_discovery_pn_title)
        }


        when {
            data.phoneNumbersList is Loading -> {
                settingsLoadingItem {
                    id("phoneLoading")
                }
            }
            data.phoneNumbersList is Fail    -> {
                settingsInfoItem {
                    id("pnListError")
                    helperText((data.emailList as Fail).error.message)
                }
            }
            else                             -> {
                val phones = data.phoneNumbersList.invoke()!!
                if (phones.isEmpty()) {
                    settingsInfoItem {
                        id("no_pns")
                        helperText(context.getString(R.string.settings_discovery_no_pn))
                    }
                } else {
                    phones.forEach { piState ->
                        val phoneNumber = PhoneNumberUtil.getInstance()
                                .parse("+${piState.value}", null)
                                ?.let {
                                    PhoneNumberUtil.getInstance().format(it, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
                                }

                        settingsTextButtonItem {
                            id(piState.value)
                            title(phoneNumber)
                            when {
                                piState.isShared is Loading -> buttonIndeterminate(true)
                                piState.isShared is Fail    -> {
                                    checked(false) //TODO previous state?
                                    buttonType(SettingsTextButtonItem.ButtonType.SWITCH)
                                    switchChangeListener { b, checked ->
                                        if (checked) {
                                            interactionListener?.onTapSharePN(piState.value)
                                        } else {
                                            interactionListener?.onTapRevokePN(piState.value)
                                        }
                                    }
                                    infoMessage(piState.isShared.error.message)
                                }
                                piState.isShared is Success -> when (piState.isShared.invoke()) {
                                    PidInfo.SharedState.SHARED,
                                    PidInfo.SharedState.NOT_SHARED              -> {
                                        checked(piState.isShared.invoke() == PidInfo.SharedState.SHARED)
                                        buttonType(SettingsTextButtonItem.ButtonType.SWITCH)
                                        switchChangeListener { b, checked ->
                                            if (checked) {
                                                interactionListener?.onTapSharePN(piState.value)
                                            } else {
                                                interactionListener?.onTapRevokePN(piState.value)
                                            }
                                        }
                                    }
                                    PidInfo.SharedState.NOT_VERIFIED_FOR_BIND,
                                    PidInfo.SharedState.NOT_VERIFIED_FOR_UNBIND -> {
                                        buttonType(SettingsTextButtonItem.ButtonType.NORMAL)
                                        buttonTitle("")
                                    }
                                }
                            }
                        }
                        when (piState.isShared.invoke()) {
                            PidInfo.SharedState.NOT_VERIFIED_FOR_BIND,
                            PidInfo.SharedState.NOT_VERIFIED_FOR_UNBIND -> {
                                settingsItemText {
                                    id("tverif" + piState.value)
                                    descriptionText(context.getString(R.string.settings_text_message_sent, phoneNumber))
                                    interactionListener(object : SettingsItemText.Listener {
                                        override fun onValidate(code: String) {
                                            val bind = piState.isShared.invoke() == PidInfo.SharedState.NOT_VERIFIED_FOR_BIND
                                            interactionListener?.checkPNVerification(piState.value, code , bind)
                                        }
                                    })
                                }
                            }
                            else                                        -> {
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildMailSection(data: DiscoverySettingsState) {
        settingsSectionTitle {
            id("emails")
            titleResId(R.string.settings_discovery_emails_title)
        }
        when {
            data.emailList is Loading -> {
                settingsLoadingItem {
                    id("mailLoading")
                }
            }
            data.emailList is Error   -> {
                settingsInfoItem {
                    id("mailListError")
                    helperText((data.emailList as Fail).error.message)
                }
            }
            else                      -> {
                val emails = data.emailList.invoke()!!
                if (emails.isEmpty()) {
                    settingsInfoItem {
                        id("no_emails")
                        helperText(context.getString(R.string.settings_discovery_no_mails))
                    }
                } else {
                    emails.forEach { piState ->
                        settingsTextButtonItem {
                            id(piState.value)
                            title(piState.value)
                            if (piState.isShared is Loading) {
                                buttonIndeterminate(true)
                            } else if (piState.isShared is Fail) {
                                checked(false) //TODO previous state?
                                buttonType(SettingsTextButtonItem.ButtonType.NORMAL)
                                buttonTitle("")
                                infoMessage(piState.isShared.error.message)
                            } else {
                                when (piState.isShared.invoke()) {
                                    PidInfo.SharedState.SHARED,
                                    PidInfo.SharedState.NOT_SHARED              -> {
                                        checked(piState.isShared.invoke() == PidInfo.SharedState.SHARED)
                                        buttonType(SettingsTextButtonItem.ButtonType.SWITCH)
                                        switchChangeListener { b, checked ->
                                            if (checked) {
                                                interactionListener?.onTapShareEmail(piState.value)
                                            } else {
                                                interactionListener?.onTapRevokeEmail(piState.value)
                                            }
                                        }
                                    }
                                    PidInfo.SharedState.NOT_VERIFIED_FOR_BIND,
                                    PidInfo.SharedState.NOT_VERIFIED_FOR_UNBIND -> {
                                        buttonType(SettingsTextButtonItem.ButtonType.NORMAL)
                                        buttonTitleId(R.string._continue)
                                        infoMessageTintColorId(R.color.vector_info_color)
                                        infoMessageId(R.string.settings_discovery_confirm_mail)
                                        buttonClickListener(View.OnClickListener {
                                            val bind = piState.isShared.invoke() == PidInfo.SharedState.NOT_VERIFIED_FOR_BIND
                                            interactionListener?.checkEmailVerification(piState.value, bind)
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildIdentityServerSection(data: DiscoverySettingsState) {
        val identityServer = data.identityServer.invoke() ?: context.getString(R.string.none)

        settingsSectionTitle {
            id("idsTitle")
            titleResId(R.string.identity_server)
        }

        settingsItem {
            id("idServer")
            description(identityServer)
            itemClickListener(View.OnClickListener { interactionListener?.onSelectIdentityServer() })
        }

        settingsInfoItem {
            id("idServerFooter")
            helperText(context.getString(R.string.settings_discovery_identity_server_info, identityServer))
        }

        settingsButtonItem {
            id("change")
            buttonTitleId(R.string.action_change)
            buttonStyle(SettingsTextButtonItem.ButtonStyle.POSITIVE)
            buttonClickListener(View.OnClickListener {
                interactionListener?.onChangeIdentityServer()
            })
        }

        if (data.identityServer.invoke() != null) {
            settingsInfoItem {
                id("removeInfo")
                helperTextResId(R.string.settings_discovery_disconnect_identity_server_info)
            }
            settingsButtonItem {
                id("remove")
                buttonTitleId(R.string.disconnect)
                buttonStyle(SettingsTextButtonItem.ButtonStyle.DESCTRUCTIVE)
                buttonClickListener(View.OnClickListener {
                    interactionListener?.onSetIdentityServer(null)
                })
            }
        }
    }


    interface InteractionListener {
        fun onSelectIdentityServer()
        fun onTapRevokeEmail(email: String)
        fun onTapShareEmail(email: String)
        fun checkEmailVerification(email: String, bind: Boolean)
        fun checkPNVerification(msisdn: String, code: String,  bind: Boolean)
        fun onTapRevokePN(pn: String)
        fun onTapSharePN(pn: String)
        fun onSetIdentityServer(server: String?)
        fun onChangeIdentityServer()
    }
}

