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
package im.vector.fragments.roomwidgets

import android.content.DialogInterface
import android.os.Build
import android.os.Parcelable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BulletSpan
import android.widget.ImageView
import android.widget.TextView
import butterknife.BindView
import butterknife.OnClick
import com.airbnb.mvrx.MvRx
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import im.vector.R
import im.vector.extensions.withArgs
import im.vector.fragments.VectorBaseBottomSheetDialogFragment
import im.vector.util.VectorUtils
import im.vector.widgets.Widget
import kotlinx.android.parcel.Parcelize

class RoomWidgetPermissionBottomSheet : VectorBaseBottomSheetDialogFragment() {

    override fun getLayoutResId(): Int = R.layout.bottom_sheet_room_widget_permission

    private val viewModel: RoomWidgetPermissionViewModel by fragmentViewModel()

    @BindView(R.id.bottom_sheet_widget_permission_shared_info)
    lateinit var sharedInfoTextView: TextView

    @BindView(R.id.bottom_sheet_widget_permission_owner_id)
    lateinit var authorIdText: TextView

    @BindView(R.id.bottom_sheet_widget_permission_owner_display_name)
    lateinit var authorNameText: TextView

    @BindView(R.id.bottom_sheet_widget_permission_owner_avatar)
    lateinit var authorAvatarView: ImageView
    
    var onFinish: ((Boolean) -> Unit)? = null

    override fun invalidate() = withState(viewModel) { state ->

        authorIdText.text = state.authorId
        authorNameText.text = state.authorName ?: ""
        VectorUtils.loadUserAvatar(requireContext(), viewModel.session, authorAvatarView,
                state.authorAvatarUrl, state.authorId, state.authorName)

        val domain = state.widgetDomain ?: ""
        val infoBuilder = SpannableStringBuilder()
                .append(getString(
                        R.string.room_widget_permission_webview_shared_info_title
                                .takeIf { state.isWebviewWidget }
                                ?: R.string.room_widget_permission_shared_info_title,
                        "'$domain'"))
        infoBuilder.append("\n")

        state.permissionsList?.forEach {
            infoBuilder.append("\n")
            val bulletPoint = getString(it)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                infoBuilder.append(bulletPoint, BulletSpan(resources.getDimension(R.dimen.quote_gap).toInt()), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                val start = infoBuilder.length
                infoBuilder.append(bulletPoint)
                infoBuilder.setSpan(
                        BulletSpan(resources.getDimension(R.dimen.quote_gap).toInt()),
                        start,
                        bulletPoint.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        infoBuilder.append("\n")

        sharedInfoTextView.text = infoBuilder
    }

    @OnClick(R.id.bottom_sheet_widget_permission_decline_button)
    fun doDecline() {
        viewModel.blockWidget()
        //optimistic dismiss
        dismiss()
        onFinish?.invoke(false)
    }

    @OnClick(R.id.bottom_sheet_widget_permission_continue_button)
    fun doAccept() {
        viewModel.allowWidget()
        onFinish?.invoke(true)
        //optimistic dismiss
        dismiss()

    }

    override fun onCancel(dialog: DialogInterface?) {
        super.onCancel(dialog)
        onFinish?.invoke(false)
    }

    @Parcelize
    data class FragArgs(
            val widget: Widget,
            val mxId: String
    ) : Parcelable


    companion object {

        fun newInstance(matrixId: String, widget: Widget) = RoomWidgetPermissionBottomSheet().withArgs {
            putParcelable(MvRx.KEY_ARG, FragArgs(widget, matrixId))
        }

    }
}