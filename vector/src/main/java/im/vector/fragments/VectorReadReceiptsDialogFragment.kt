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

package im.vector.fragments

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import im.vector.Matrix
import im.vector.R
import im.vector.adapters.VectorReadReceiptsAdapter
import im.vector.extensions.withArgs
import im.vector.fragments.base.VectorBaseDialogFragment
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.SimpleApiCallback
import org.matrix.androidsdk.rest.model.RoomMember
import java.util.*

/**
 * A dialog fragment showing the read receipts for an event
 */
class VectorReadReceiptsDialogFragment : VectorBaseDialogFragment<VectorReadReceiptsDialogFragment.VectorReadReceiptsDialogFragmentListener>(),
        VectorReadReceiptsAdapter.VectorReadReceiptsAdapterListener {
    private lateinit var mAdapter: VectorReadReceiptsAdapter

    interface VectorReadReceiptsDialogFragmentListener : VectorReadReceiptsAdapter.VectorReadReceiptsAdapterListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mxSession = Matrix.getInstance(context).getSession(arguments!!.getString(ARG_SESSION_ID))

        val roomId = arguments!!.getString(ARG_ROOM_ID)
        val eventId = arguments!!.getString(ARG_EVENT_ID)

        // sanity check
        if (mxSession == null || TextUtils.isEmpty(roomId) || TextUtils.isEmpty(eventId)) {
            Log.e(LOG_TAG, "## onCreate() : invalid parameters")
            dismiss()
            return
        }

        val room = mxSession.dataHandler.getRoom(roomId)

        mAdapter = VectorReadReceiptsAdapter(context!!,
                mxSession,
                room,
                ArrayList(mxSession.dataHandler.store!!.getEventReceipts(roomId, eventId, true, true)),
                this)

        // Ensure all the members are loaded (ignore error)
        room.getMembersAsync(object : SimpleApiCallback<List<RoomMember>>() {
            override fun onSuccess(info: List<RoomMember>) {
                if (isAdded) {
                    mAdapter.notifyDataSetChanged()
                }
            }
        })
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val d = super.onCreateDialog(savedInstanceState)
        // FIXME The title is not displayed
        d.setTitle(R.string.read_receipts_list)
        return d
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return (inflater.inflate(R.layout.fragment_dialog_member_list, container, false) as androidx.recyclerview.widget.RecyclerView)
                .apply {
                    layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
                    adapter = mAdapter
                }
    }

    override fun onMemberClicked(userId: String) {
        listener?.onMemberClicked(userId)
    }

    companion object {
        private val LOG_TAG = VectorPublicRoomsListFragment::class.java.simpleName

        private const val ARG_ROOM_ID = "VectorReadReceiptsDialogFragment.ARG_ROOM_ID"
        private const val ARG_EVENT_ID = "VectorReadReceiptsDialogFragment.ARG_EVENT_ID"
        private const val ARG_SESSION_ID = "VectorReadReceiptsDialogFragment.ARG_SESSION_ID"

        fun newInstance(userId: String, roomId: String, eventId: String): VectorReadReceiptsDialogFragment {
            return VectorReadReceiptsDialogFragment().withArgs {
                putString(ARG_SESSION_ID, userId)
                putString(ARG_ROOM_ID, roomId)
                putString(ARG_EVENT_ID, eventId)
            }
        }
    }
}
