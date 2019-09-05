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
import com.google.i18n.phonenumbers.PhoneNumberUtil
import im.vector.R
import im.vector.util.state.MxAsync


class SettingsDiscoveryController(private val context: Context, private val interactionListener: InteractionListener?) : TypedEpoxyController<DiscoverySettingsState>() {

    override fun buildModels(data: DiscoverySettingsState?) {
        if (data == null) return

        val identityServer = data.identityServer.invoke() ?: context.getString(R.string.none)


        val hasIdentityServer = data.identityServer.invoke().isNullOrBlank().not()

        if (hasIdentityServer) {
            settingsSectionTitle {
                id("emails")
                titleResId(R.string.settings_discovery_emails_title)
            }
            when {
                data.emailList is Loading -> {

                }
                data.emailList is Error   -> {

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
                                when (piState.isShared.invoke()) {
                                    true  -> {
                                        buttonIndeterminate(false)
                                        buttonTitleId(R.string.revoke)
                                        buttonStyle(SettingsTextButtonItem.ButtonStyle.DESCTRUCTIVE)
                                        buttonClickListener(View.OnClickListener {
                                            interactionListener?.onTapRevokeEmail(piState.value)
                                        })
                                    }
                                    false -> {
                                        buttonIndeterminate(false)
                                        buttonTitleId(R.string.share)
                                        buttonStyle(SettingsTextButtonItem.ButtonStyle.POSITIVE)
                                        buttonClickListener(View.OnClickListener {
                                            interactionListener?.onTapShareEmail(piState.value)
                                        })
                                    }
                                    null  -> {
                                        buttonIndeterminate(true)
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                    //Todo
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
                            settingsTextButtonItem {
                                id(piState.value)
                                val phoneNumber = PhoneNumberUtil.getInstance().parse("+${piState.value}", null)
                                title(PhoneNumberUtil.getInstance().format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL))
                                when (piState.isShared.invoke()) {
                                    true  -> {
                                        buttonIndeterminate(false)
                                        buttonTitleId(R.string.revoke)
                                        buttonStyle(SettingsTextButtonItem.ButtonStyle.DESCTRUCTIVE)
                                        buttonClickListener(View.OnClickListener {
                                            interactionListener?.onTapRevokePN(piState.value)
                                        })
                                    }
                                    false -> {
                                        buttonIndeterminate(false)
                                        buttonTitleId(R.string.share)
                                        buttonStyle(SettingsTextButtonItem.ButtonStyle.POSITIVE)
                                        buttonClickListener(View.OnClickListener {
                                            interactionListener?.onTapSharePN(piState.value)
                                        })
                                    }
                                    null  -> {
                                        buttonIndeterminate(true)
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }

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
        fun onTapRevokePN(pn: String)
        fun onTapSharePN(pn: String)
        fun onSetIdentityServer(server: String?)
    }
}

