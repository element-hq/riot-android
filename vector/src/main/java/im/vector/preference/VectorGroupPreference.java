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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.rest.model.group.Group;

import im.vector.R;
import im.vector.util.VectorUtils;

public class VectorGroupPreference extends VectorSwitchPreference {
    private static final String LOG_TAG = VectorGroupPreference.class.getSimpleName();

    private Context mContext;
    private ImageView mAvatarView;

    private Group mGroup;
    private MXSession mSession;

    /**
     * Construct a new SwitchPreference with the given style options.
     *
     * @param context  The Context that will style this preference
     * @param attrs    Style attributes that differ from the default
     * @param defStyle Theme attribute defining the default style options
     */
    public VectorGroupPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    /**
     * Construct a new SwitchPreference with the given style options.
     *
     * @param context The Context that will style this preference
     * @param attrs   Style attributes that differ from the default
     */
    public VectorGroupPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public VectorGroupPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    /**
     * Construct a new SwitchPreference with default style options.
     *
     * @param context The Context that will style this preference
     */
    public VectorGroupPreference(Context context) {
        super(context, null);
        init(context);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View createdView = super.onCreateView(parent);

        try {
            // insert the group avatar to the left
            final ImageView iconView = createdView.findViewById(android.R.id.icon);

            ViewParent iconViewParent = iconView.getParent();

            while (null != iconViewParent.getParent()) {
                iconViewParent = iconViewParent.getParent();
            }

            LayoutInflater inflater = LayoutInflater.from(mContext);
            FrameLayout layout = (FrameLayout) inflater.inflate(R.layout.vector_settings_round_group_avatar, null, false);
            mAvatarView = layout.findViewById(R.id.avatar_img);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            layout.setLayoutParams(params);
            ((LinearLayout)iconViewParent).addView(layout, 0);

            refreshAvatar();
        } catch (Exception e) {
            mAvatarView = null;
        }

        return createdView;
    }

    /**
     * Init the group information
     *
     * @param group the group
     * @param session the session
     */
    public void setGroup(Group group, MXSession session) {
        mGroup = group;
        mSession = session;

        refreshAvatar();
    }

    /**
     * Refresh the avatar
     */
    public void refreshAvatar() {
        if ((null != mAvatarView) && (null != mSession) && (null != mGroup)) {
            VectorUtils.loadGroupAvatar(mContext, mSession, mAvatarView ,mGroup);
        }
    }

    /**
     * Common init method.
     *
     * @param context the context
     */
    private void init(Context context) {
        // Force the use of SwitchCompat component
        setWidgetLayoutResource(R.layout.preference_switch_layout);
        mContext = context;
    }
}