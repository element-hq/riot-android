/*
 * Copyright 2016 OpenMarket Ltd
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

package im.vector.preference;

import android.content.Context;
import android.preference.EditTextPreference;
import android.preference.PreferenceScreen;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;

import im.vector.R;
import im.vector.util.VectorUtils;

public class UserAvatarPreference extends EditTextPreference {

    protected Context mContext;
    protected ImageView mAvatarView;
    protected MXSession mSession;
    private ProgressBar mLoadingProgressBar;

    public UserAvatarPreference(Context context) {
        super(context);
        mContext = context;
    }

    public UserAvatarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public UserAvatarPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        setWidgetLayoutResource(R.layout.vector_settings_round_avatar);
        View layout = super.onCreateView(parent);
        mAvatarView = (ImageView)layout.findViewById(R.id.avatar_img);
        mLoadingProgressBar = (ProgressBar)layout.findViewById(R.id.avatar_update_progress_bar);
        refreshAvatar();
        return layout;
    }

    public void refreshAvatar() {
        if ((null !=  mAvatarView) && (null != mSession)) {
            MyUser myUser = mSession.getMyUser();
            VectorUtils.loadUserAvatar(mContext, mSession, mAvatarView, myUser.getAvatarUrl(), myUser.user_id, myUser.displayname);
        }
    }

    public void enableProgressBar(boolean aIsProgressBarEnabled) {
        if (null !=  mLoadingProgressBar) {
            if(aIsProgressBarEnabled)
                mLoadingProgressBar.setVisibility(View.VISIBLE);
            else
                mLoadingProgressBar.setVisibility(View.GONE);
        }
    }

    /**
     * Retrieve the progress bar display status, returning true if
     * the progress par is visible or false if the progress bar is hidden.
     * @return true if the progress bar is visible, false otherwise
     */
    public boolean isProgressBarEnabled() {
        boolean retCode = false;
        if(null != mLoadingProgressBar){
            retCode = (mLoadingProgressBar.getVisibility()==View.VISIBLE);
        }
        return retCode;
    }

    public void setSession(MXSession session) {
        mSession = session;
        refreshAvatar();
    }

    public void performClick(PreferenceScreen preferenceScreen) {
        // call only the click listener
        if (getOnPreferenceClickListener() != null) {
            getOnPreferenceClickListener().onPreferenceClick(this);
        }
    }
}