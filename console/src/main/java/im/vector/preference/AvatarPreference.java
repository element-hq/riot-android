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

import org.matrix.androidsdk.MXSession;

import im.vector.R;
import im.vector.util.VectorUtils;

public class AvatarPreference extends EditTextPreference {

    Context mContext;
    ImageView mAvatarView;
    MXSession mSession;

    public AvatarPreference(Context context) {
        super(context);
        mContext = context;
    }

    public AvatarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public AvatarPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    protected View onCreateView(ViewGroup parent) {
        setWidgetLayoutResource(R.layout.vector_settings_round_avatar);
        View layout = super.onCreateView(parent);
        mAvatarView = (ImageView)layout.findViewById(R.id.avatar_img);
        refreshAvatar();
        return layout;
    }

    public void refreshAvatar() {
        if ((null !=  mAvatarView) && (null != mSession)) {
            VectorUtils.setMemberAvatar(mAvatarView, mSession.getMyUser().userId, mSession.getMyUser().displayname);
            mSession.getMediasCache().loadAvatarThumbnail(mSession.getHomeserverConfig(), mAvatarView, mSession.getMyUser().avatarUrl, mContext.getResources().getDimensionPixelSize(R.dimen.profile_avatar_size));
        }
    }

    public void setSession(MXSession session) {
        mSession = session;
        refreshAvatar();
    }

    public void performClick(PreferenceScreen preferenceScreen) {
        // call only the click listener
        if (getOnPreferenceClickListener() != null && getOnPreferenceClickListener().onPreferenceClick(this)) {
            getOnPreferenceClickListener().onPreferenceClick(this);
        }
    }
}