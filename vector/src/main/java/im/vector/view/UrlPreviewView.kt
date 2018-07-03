/* 
 * Copyright 2014 OpenMarket Ltd
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
import android.preference.PreferenceManager
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.rest.model.URLPreview
import org.matrix.androidsdk.util.Log

import java.util.HashSet

import im.vector.R
import im.vector.VectorApp

/**
 *
 */
class UrlPreviewView : LinearLayout {

    // private item views
    private var mImageView: ImageView? = null
    private var mTitleTextView: TextView? = null
    private var mDescriptionTextView: TextView? = null
    private var mCloseView: View? = null

    // dismissed when clicking on mCloseView
    private var mIsDismissed = false

    private var mUID: String? = null

    /**
     * constructors
     */
    constructor(context: Context) : super(context) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initView()
    }

    /**
     * Common initialisation method.
     */
    private fun initView() {
        View.inflate(context, R.layout.url_preview_view, this)
        mImageView = findViewById(R.id.url_preview_image_view)
        mTitleTextView = findViewById(R.id.url_preview_title_text_view)
        mDescriptionTextView = findViewById(R.id.url_preview_description_text_view)
        mCloseView = findViewById(R.id.url_preview_hide_image_view)

        mCloseView!!.setOnClickListener {
            mIsDismissed = true
            visibility = View.GONE

            mDismissedUrlsPreviews!!.add(mUID)

            PreferenceManager.getDefaultSharedPreferences(VectorApp.getInstance())
                    .edit()
                    .putStringSet(DISMISSED_URL_PREVIEWS_PREF_KEY, mDismissedUrlsPreviews)
                    .apply()
        }
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
            session.mediasCache.loadAvatarThumbnail(session.homeServerConfig,
                    mImageView, preview.thumbnailURL, context.resources.getDimensionPixelSize(R.dimen.profile_avatar_size))

            if (null != preview.requestedURL && null != preview.title) {
                mTitleTextView!!.text = Html.fromHtml("<a href=\"" + preview.requestedURL + "\">" + preview.title + "</a>")
            } else if (null != preview.title) {
                mTitleTextView!!.text = preview.title
            } else {
                mTitleTextView!!.text = preview.requestedURL
            }
            mTitleTextView!!.movementMethod = LinkMovementMethod.getInstance()

            mDescriptionTextView!!.text = preview.description

            mUID = uid
        }
    }

    companion object {
        private val LOG_TAG = UrlPreviewView::class.java.simpleName

        // save
        private var mDismissedUrlsPreviews: MutableSet<String>? = null

        private val DISMISSED_URL_PREVIEWS_PREF_KEY = "DISMISSED_URL_PREVIEWS_PREF_KEY"

        /**
         * Tells if the URL preview defines by uid has been dismissed.
         *
         * @param uid the url preview id
         * @return true if it has been dismissed
         */
        fun didUrlPreviewDismiss(uid: String): Boolean {
            if (null == mDismissedUrlsPreviews) {
                mDismissedUrlsPreviews = HashSet(PreferenceManager.getDefaultSharedPreferences(VectorApp.getInstance())
                        .getStringSet(DISMISSED_URL_PREVIEWS_PREF_KEY, HashSet())!!)
            }

            return mDismissedUrlsPreviews!!.contains(uid)
        }
    }
}
