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
import android.os.Bundle;
import android.preference.EditTextPreference;
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

    Context mContext;
    ImageView mAvatarView;
    MXSession mSession;
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
        mAvatarView = layout.findViewById(R.id.avatar_img);
        mLoadingProgressBar = layout.findViewById(R.id.avatar_update_progress_bar);
        refreshAvatar();
        return layout;
    }

    public void refreshAvatar() {
        if ((null != mAvatarView) && (null != mSession)) {
            MyUser myUser = mSession.getMyUser();
            VectorUtils.loadUserAvatar(mContext, mSession, mAvatarView, myUser.getAvatarUrl(), myUser.user_id, myUser.displayname);
        }
    }

    public void setSession(MXSession session) {
        mSession = session;
        refreshAvatar();
    }

    @Override
    protected void showDialog(Bundle state) {
        // do nothing
    }
}