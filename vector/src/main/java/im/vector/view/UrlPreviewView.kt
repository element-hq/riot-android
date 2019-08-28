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
package im.vector.view

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.transition.TransitionManager
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import im.vector.R
import im.vector.VectorApp
import im.vector.ui.animation.VectorTransitionSet
import im.vector.util.openUrlInExternalBrowser
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.rest.model.URLPreview
import java.util.*

/**
 * View to display a UrlPreview object
 */
class UrlPreviewView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // private item views
    @BindView(R.id.url_preview_image_view)
    lateinit var mImageView: ImageView

    @BindView(R.id.url_preview_title_text_view)
    lateinit var mTitleTextView: TextView

    @BindView(R.id.url_preview_description_text_view)
    lateinit var mDescriptionTextView: TextView

    // dismissed when clicking on CloseView
    private var mIsDismissed = false

    private var mUID: String? = null

    init {
        View.inflate(context, R.layout.url_preview_view, this)

        ButterKnife.bind(this)
    }

    /**
     * Set the URL preview.
     *
     * @param context the context
     * @param session the session
     * @param preview the url preview
     * @param uid     unique identifier of this preview
     */
    fun setUrlPreview(context: Context, session: MXSession, preview: URLPreview?, uid: String) {
        Log.d(LOG_TAG, "## setUrlPreview " + this)

        if (null == preview || mIsDismissed || didUrlPreviewDismiss(uid) || !session.isURLPreviewEnabled) {
            visibility = View.GONE
        } else {
            visibility = View.VISIBLE
            session.mediaCache.loadAvatarThumbnail(session.homeServerConfig,
                    mImageView, preview.thumbnailURL, context.resources.getDimensionPixelSize(R.dimen.profile_avatar_size))

            mTitleTextView.let {
                if (null != preview.requestedURL && null != preview.title) {
                    it.text = SpannableString(preview.title)
                            .apply {
                                setSpan(object : ClickableSpan() {
                                    override fun onClick(widget: View?) {
                                        openUrlInExternalBrowser(context, preview.requestedURL)
                                    }
                                }, 0, preview.title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                } else if (null != preview.title) {
                    // No link in this case
                    it.text = preview.title
                } else {
                    it.text = SpannableString(preview.requestedURL)
                            .apply {
                                setSpan(object : ClickableSpan() {
                                    override fun onClick(widget: View?) {
                                        openUrlInExternalBrowser(context, preview.requestedURL)
                                    }
                                }, 0, preview.requestedURL.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                }

                it.movementMethod = LinkMovementMethod.getInstance()
            }

            mDescriptionTextView.let {
                if (TextUtils.isEmpty(preview.description)) {
                    it.visibility = View.GONE
                } else {
                    it.visibility = View.VISIBLE
                    it.text = preview.description
                }
            }

            mUID = uid

            if (preview.requestedURL != null) {
                mDescriptionTextView.setOnClickListener { openUrlInExternalBrowser(context, preview.requestedURL) }
                mImageView.setOnClickListener { openUrlInExternalBrowser(context, preview.requestedURL) }
            } else {
                mDescriptionTextView.isClickable = false
                mImageView.isClickable = false
            }
        }
    }

    /* ==========================================================================================
     * UI Event
     * ========================================================================================== */

    @OnClick(R.id.url_preview_hide_image_view)
    internal fun closeUrlPreview() {
        // Parent is a LinearLayout
        val parent = parent as ViewGroup
        TransitionManager.beginDelayedTransition(parent, VectorTransitionSet().apply {
            appearWithAlpha(this@UrlPreviewView)
        })

        mIsDismissed = true

        parent.removeView(this)

        sDismissedUrlsPreviews.add(mUID)

        PreferenceManager.getDefaultSharedPreferences(VectorApp.getInstance())
                .edit {
                    putStringSet(DISMISSED_URL_PREVIEWS_PREF_KEY, sDismissedUrlsPreviews)
                }
    }

    /* ==========================================================================================
     * Companion
     * ========================================================================================== */

    companion object {
        private val LOG_TAG = UrlPreviewView::class.java.simpleName

        // save
        private val sDismissedUrlsPreviews by lazy {
            HashSet(PreferenceManager.getDefaultSharedPreferences(VectorApp.getInstance())
                    .getStringSet(DISMISSED_URL_PREVIEWS_PREF_KEY, HashSet()))
        }

        private const val DISMISSED_URL_PREVIEWS_PREF_KEY = "DISMISSED_URL_PREVIEWS_PREF_KEY"

        /**
         * Tells if the URL preview defines by uid has been dismissed.
         *
         * @param uid the url preview id
         * @return true if it has been dismissed
         */
        fun didUrlPreviewDismiss(uid: String): Boolean {
            return sDismissedUrlsPreviews.contains(uid)
        }
    }
}
