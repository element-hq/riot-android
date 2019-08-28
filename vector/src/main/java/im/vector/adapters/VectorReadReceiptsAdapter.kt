/*
 * Copyright 2018 New Vector Ltd
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

package im.vector.adapters

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.ButterKnife
import im.vector.R
import im.vector.util.VectorUtils
import im.vector.util.copyToClipboard
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.rest.model.ReceiptData

/**
 * An adapter which can display read receipts
 */
class VectorReadReceiptsAdapter(private val mContext: Context,
                                private val mSession: MXSession,
                                private val mRoom: Room,
                                private val list: ArrayList<ReceiptData>,
                                private val listener: VectorReadReceiptsAdapterListener) :
        androidx.recyclerview.widget.RecyclerView.Adapter<VectorReadReceiptsAdapter.ReadReceiptViewHolder>() {

    interface VectorReadReceiptsAdapterListener {
        fun onMemberClicked(userId: String)
    }

    private val mLayoutInflater = LayoutInflater.from(mContext)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReadReceiptViewHolder {
        var view = mLayoutInflater.inflate(R.layout.adapter_item_read_receipt, parent, false)
        return ReadReceiptViewHolder(view)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: ReadReceiptViewHolder, position: Int) {
        val receipt = list[position]
        val member = mRoom.getMember(receipt.userId)

        // Avatar
        if (member == null) {
            VectorUtils.loadUserAvatar(mContext, mSession, holder.imageView, null, receipt.userId, receipt.userId)
        } else {
            VectorUtils.loadRoomMemberAvatar(mContext, mSession, holder.imageView, member)
        }

        // User name
        holder.userNameTextView.let {
            // if the room member is not known, display his user id.
            if (member == null) {
                it.text = receipt.userId
            } else {
                it.text = member.name
            }

            it.setOnLongClickListener { v ->
                copyToClipboard(mContext, it.text)
                true
            }

            // Also add on click listener, else it is not handled (it should...)
            it.setOnClickListener { _ ->
                if (null != member) {
                    listener.onMemberClicked(member.userId)
                }
            }
        }

        // Timestamp
        holder.tsTextView.let {
            val ts = AdapterUtils.tsToString(mContext, receipt.originServerTs, false)

            val body = SpannableStringBuilder(mContext.getString(R.string.read_receipt) + " : " + ts)
            body.setSpan(StyleSpan(Typeface.BOLD),
                    0, mContext.getString(R.string.read_receipt).length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            it.text = body

            it.setOnLongClickListener { _ ->
                copyToClipboard(mContext, ts)
                true
            }

            // Also add on click listener, else it is not handled (it should...)
            it.setOnClickListener { _ ->
                if (null != member) {
                    listener.onMemberClicked(member.userId)
                }
            }
        }

        holder.view.setOnClickListener {
            if (null != member) {
                listener.onMemberClicked(member.userId)
            }
        }
    }

    class ReadReceiptViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        @BindView(R.id.read_receipt_user_avatar)
        lateinit var imageView: ImageView

        @BindView(R.id.read_receipt_user_name)
        lateinit var userNameTextView: TextView

        @BindView(R.id.read_receipt_ts)
        lateinit var tsTextView: TextView

        init {
            ButterKnife.bind(this, view)
        }
    }
}
