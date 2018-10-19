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
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
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
                                private val listener: VectorReadReceiptsAdapterListener) :
        ArrayAdapter<ReceiptData>(mContext, 0) {

    interface VectorReadReceiptsAdapterListener {
        fun onMemberClicked(userId: String)
    }

    private val mLayoutInflater = LayoutInflater.from(mContext)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder

        if (convertView != null) {
            view = convertView
            holder = convertView.tag as ViewHolder
        } else {
            view = mLayoutInflater.inflate(R.layout.adapter_item_read_receipt, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        }

        val receipt = getItem(position)

        val member = mRoom.getMember(receipt.userId)

        // if the room member is not known, display his user id.
        if (null == member) {
            holder.userNameTextView.text = receipt.userId
            VectorUtils.loadUserAvatar(mContext, mSession, holder.imageView, null, receipt.userId, receipt.userId)
        } else {
            holder.userNameTextView.text = member.name
            VectorUtils.loadRoomMemberAvatar(mContext, mSession, holder.imageView, member)
        }

        val ts = AdapterUtils.tsToString(mContext, receipt.originServerTs, false)

        val body = SpannableStringBuilder(mContext.getString(R.string.read_receipt) + " : " + ts)
        body.setSpan(StyleSpan(Typeface.BOLD),
                0, mContext.getString(R.string.read_receipt).length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        holder.tsTextView.text = body

        holder.userNameTextView.setOnLongClickListener {
            copyToClipboard(mContext, holder.userNameTextView.text)
            true
        }

        holder.tsTextView.setOnLongClickListener {
            copyToClipboard(mContext, ts)
            true
        }

        view.setOnClickListener {
            if (null != member) {
                listener.onMemberClicked(member.userId)
            }
        }

        return view
    }

    class ViewHolder(view: View) {
        @BindView(R.id.accountAdapter_name)
        lateinit var userNameTextView: TextView

        @BindView(R.id.avatar_img_vector)
        lateinit var imageView: ImageView

        @BindView(R.id.read_receipt_ts)
        lateinit var tsTextView: TextView

        init {
            ButterKnife.bind(view)
        }
    }
}
