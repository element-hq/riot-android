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
import android.content.Intent
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import im.vector.R
import im.vector.activity.VectorMemberDetailsActivity
import im.vector.util.VectorUtils
import im.vector.util.copyToClipboard
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.data.Room
import org.matrix.androidsdk.rest.model.ReceiptData

/**
 * An adapter which can display read receipts
 */
class VectorReadReceiptsAdapter(private val mContext: Context,
                                private val mLayoutResourceId: Int,
                                private val mSession: MXSession,
                                private val mRoom: Room) : ArrayAdapter<ReceiptData>(mContext, mLayoutResourceId) {
    private val mLayoutInflater: LayoutInflater = LayoutInflater.from(mContext)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: mLayoutInflater.inflate(mLayoutResourceId, parent, false)

        val receipt = getItem(position)

        val userNameTextView = view.findViewById<TextView>(R.id.accountAdapter_name)
        val imageView = view.findViewById<ImageView>(R.id.avatar_img_vector)
        val tsTextView = view.findViewById<TextView>(R.id.read_receipt_ts)

        val member = mRoom.getMember(receipt.userId)

        // if the room member is not known, display his user id.
        if (null == member) {
            userNameTextView.text = receipt.userId
            VectorUtils.loadUserAvatar(mContext, mSession, imageView, null, receipt.userId, receipt.userId)
        } else {
            userNameTextView.text = member.name
            VectorUtils.loadRoomMemberAvatar(mContext, mSession, imageView, member)
        }

        val ts = AdapterUtils.tsToString(mContext, receipt.originServerTs, false)

        val body = SpannableStringBuilder(mContext.getString(im.vector.R.string.read_receipt) + " : " + ts)
        body.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                0, mContext.getString(im.vector.R.string.read_receipt).length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        tsTextView.text = body

        userNameTextView.setOnLongClickListener {
            copyToClipboard(mContext, userNameTextView.text)
            true
        }

        tsTextView.setOnLongClickListener {
            copyToClipboard(mContext, ts)
            true
        }

        view.setOnClickListener {
            if (null != member) {
                val startRoomInfoIntent = Intent(mContext, VectorMemberDetailsActivity::class.java)
                startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MEMBER_ID, member.userId)
                startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_ROOM_ID, mRoom.roomId)
                startRoomInfoIntent.putExtra(VectorMemberDetailsActivity.EXTRA_MATRIX_ID, mSession.credentials.userId)
                mContext.startActivity(startRoomInfoIntent)
            }
        }

        return view
    }
}
