/* 
 * Copyright 2014 OpenMarket Ltd
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
package im.vector.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.URLPreview;
import org.matrix.androidsdk.util.Log;

import java.util.HashSet;

import im.vector.R;
import im.vector.VectorApp;
import im.vector.util.PreferencesManager;

/**
 *
 */
public class UrlPreviewView extends LinearLayout {
    private static final String LOG_TAG = UrlPreviewView.class.getSimpleName();

    // private item views
    private ImageView mImageView;
    private TextView mTitleTextView;
    private TextView mDescriptionTextView;
    private View mCloseView;

    // dismissed when clicking on mCloseView
    private boolean mIsDismissed = false;

    private String mUID = null;

    // save
    private static HashSet<String> mDismissedUrlsPreviews = null;

    private static final String DISMISSED_URL_PREVIEWS_PREF_KEY = "DISMISSED_URL_PREVIEWS_PREF_KEY";

    /**
     * constructors
     **/
    public UrlPreviewView(Context context) {
        super(context);
        initView();
    }

    public UrlPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public UrlPreviewView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    /**
     * Common initialisation method.
     */
    private void initView() {
        View.inflate(getContext(), R.layout.url_preview_view, this);
        mImageView = findViewById(R.id.url_preview_image_view);
        mTitleTextView = findViewById(R.id.url_preview_title_text_view);
        mDescriptionTextView = findViewById(R.id.url_preview_description_text_view);
        mCloseView = findViewById(R.id.url_preview_hide_image_view);

        mCloseView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsDismissed = true;
                UrlPreviewView.this.setVisibility(View.GONE);

                mDismissedUrlsPreviews.add(mUID);

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(VectorApp.getInstance());
                SharedPreferences.Editor editor = preferences.edit();

                editor.putStringSet(DISMISSED_URL_PREVIEWS_PREF_KEY, mDismissedUrlsPreviews);
                editor.commit();
            }
        });
    }

    /**
     * Tells if the URL preview defines by uid has been dismissed.
     *
     * @param uid the url preview id
     * @return true if it has been dismissed
     */
    public static boolean didUrlPreviewDismiss(String uid) {
        if (null == mDismissedUrlsPreviews) {
            mDismissedUrlsPreviews = new HashSet<>(PreferenceManager.getDefaultSharedPreferences(VectorApp.getInstance()).getStringSet(DISMISSED_URL_PREVIEWS_PREF_KEY, new HashSet<String>()));
        }

        return mDismissedUrlsPreviews.contains(uid);
    }

    /**
     * Set the URL preview.
     *
     * @param context the context
     * @param session the session
     * @param preview the url preview
     * @param uid     unique identifier of this preview
     */
    public void setUrlPreview(Context context, MXSession session, URLPreview preview, String uid) {
        Log.d(LOG_TAG, "## setUrlPreview " + this);

        if ((null == preview) || mIsDismissed || didUrlPreviewDismiss(uid) || !session.isURLPreviewEnabled()) {
            setVisibility(View.GONE);
        } else {
            setVisibility(View.VISIBLE);
            session.getMediasCache().loadAvatarThumbnail(session.getHomeServerConfig(), mImageView, preview.getThumbnailURL(), context.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size));

            if ((null != preview.getRequestedURL()) && (null != preview.getTitle())) {
                mTitleTextView.setText(Html.fromHtml("<a href=\"" + preview.getRequestedURL() + "\">" + preview.getTitle() + "</a>"));
            } else if (null != preview.getTitle()) {
                mTitleTextView.setText(preview.getTitle());
            } else {
                mTitleTextView.setText(preview.getRequestedURL());
            }
            mTitleTextView.setMovementMethod(LinkMovementMethod.getInstance());

            mDescriptionTextView.setText(preview.getDescription());

            mUID = uid;
        }
    }
}
