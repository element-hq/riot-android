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
package im.vector.fragments.verification

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import butterknife.BindView
import butterknife.OnClick
import im.vector.R
import im.vector.fragments.VectorBaseFragment
import org.matrix.androidsdk.crypto.verification.SASVerificationTransaction

class SASVerificationEmojiFragment : VectorBaseFragment() {

    private lateinit var viewModel: SasVerificationViewModel

    companion object {
        fun newInstance() = SASVerificationEmojiFragment()
    }

    @BindView(R.id.emoji0)
    lateinit var emoji0View: ViewGroup
    @BindView(R.id.emoji1)
    lateinit var emoji1View: ViewGroup
    @BindView(R.id.emoji2)
    lateinit var emoji2View: ViewGroup
    @BindView(R.id.emoji3)
    lateinit var emoji3View: ViewGroup
    @BindView(R.id.emoji4)
    lateinit var emoji4View: ViewGroup
    @BindView(R.id.emoji5)
    lateinit var emoji5View: ViewGroup
    @BindView(R.id.emoji6)
    lateinit var emoji6View: ViewGroup


    override fun getLayoutResId() = R.layout.fragment_sas_verification_emoji

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = activity?.run {
            ViewModelProviders.of(this).get(SasVerificationViewModel::class.java)
        } ?: throw Exception("Invalid Activity")


        viewModel.transaction?.let {
            val emojicodes = it.getEmojiCodeRepresentation(it.shortCodeBytes!!, context)
            emojicodes?.forEachIndexed { index, emojiRepresentation ->
                when (index) {
                    0 -> {
                        emoji0View.findViewById<TextView>(R.id.item_emoji_tv)?.text = emojiRepresentation.emoji
                        emoji0View.findViewById<TextView>(R.id.item_emoji_name_tv)?.text = emojiRepresentation.name
                    }
                    1 -> {
                        emoji1View.findViewById<TextView>(R.id.item_emoji_tv)?.text = emojiRepresentation.emoji
                        emoji1View.findViewById<TextView>(R.id.item_emoji_name_tv)?.text = emojiRepresentation.name
                    }
                    2 -> {
                        emoji2View.findViewById<TextView>(R.id.item_emoji_tv)?.text = emojiRepresentation.emoji
                        emoji2View.findViewById<TextView>(R.id.item_emoji_name_tv)?.text = emojiRepresentation.name
                    }
                    3 -> {
                        emoji3View.findViewById<TextView>(R.id.item_emoji_tv)?.text = emojiRepresentation.emoji
                        emoji3View.findViewById<TextView>(R.id.item_emoji_name_tv)?.text = emojiRepresentation.name
                    }
                    4 -> {
                        emoji4View.findViewById<TextView>(R.id.item_emoji_tv)?.text = emojiRepresentation.emoji
                        emoji4View.findViewById<TextView>(R.id.item_emoji_name_tv)?.text = emojiRepresentation.name
                    }
                    5 -> {
                        emoji5View.findViewById<TextView>(R.id.item_emoji_tv)?.text = emojiRepresentation.emoji
                        emoji5View.findViewById<TextView>(R.id.item_emoji_name_tv)?.text = emojiRepresentation.name
                    }
                    6 -> {
                        emoji6View.findViewById<TextView>(R.id.item_emoji_tv)?.text = emojiRepresentation.emoji
                        emoji6View.findViewById<TextView>(R.id.item_emoji_name_tv)?.text = emojiRepresentation.name
                    }
                }

            }
        }


        viewModel.transactionState.observe(this, Observer {
            when (it) {
                SASVerificationTransaction.SASVerificationTxState.ShortCodeAccepted,
                SASVerificationTransaction.SASVerificationTxState.MacSent -> {
                    viewModel.loadingLiveEvent.value = R.string.sas_waiting_for_partner
                }
                SASVerificationTransaction.SASVerificationTxState.Verified -> {
                    viewModel.deviceIsVerified()
                }

            }
        })
    }


    @OnClick(R.id.sas_request_continue_button)
    fun didAccept() {
        viewModel.confirmEmojiSame()
    }

    @OnClick(R.id.sas_request_cancel_button)
    fun didCancel() {
        viewModel.cancelTransaction()
    }
}