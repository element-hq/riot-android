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
package im.vector.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;

import java.util.HashMap;

import im.vector.Matrix;
import im.vector.R;
import im.vector.util.VectorUtils;

public class VectorSettingsActivity extends MXCActionBarActivity {

    private static final String LOG_TAG = "VectorSettingsActivity";

    private static final String PROFILE_PICTURE_KEY = "PROFILE_PICTURE_KEY";
    private static final String PROFILE_DISPLAYNAME_KEY = "PROFILE_DISPLAYNAME_KEY";
    private static final String PROFILE_PASSWORD_KEY = "PROFILE_PASSWORD_KEY";

    private static final String ENABLE_ALL_KEY = "ENABLE_ALL_KEY";
    private static final String CONTAINING_MY_USER_NAME_KEY = "CONTAINING_MY_USER_NAME_KEY";
    private static final String CONTAINING_MY_DISPLAY_NAME_KEY = "CONTAINING_MY_DISPLAY_NAME_KEY";
    private static final String SENT_TO_ME_KEY = "SENT_TO_ME_KEY";
    private static final String INVITED_TO_A_ROOM_KEY = "INVITED_TO_A_ROOM_KEY";
    private static final String JOINED_LEAVE_ROOM_KEY = "JOINED_LEAVE_ROOM_KEY";
    private static final String CALL_INVITATION_KEY = "CALL_INVITATION_KEY";

    // views by the previous keys
    private HashMap<String, View> mSettingsViews = new HashMap<String, View>();

    // updates values
    private HashMap<String, Object> mUpdatedValues = new HashMap<String, Object>();

    MXSession mSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (CommonActivityUtils.shouldRestartApp()) {
            Log.e(LOG_TAG, "Restart the application.");
            CommonActivityUtils.restartApp(this);
        }

        super.onCreate(savedInstanceState);

        mSession = Matrix.getInstance(this).getDefaultSession();

        setContentView(R.layout.activity_vector_settings);
        buildSettingsScreen(LayoutInflater.from(this), (LinearLayout) findViewById(R.id.settings_layout));

        refresh();
    }

    private void buildSettingsScreen(LayoutInflater inflater, LinearLayout layout) {
        TextView textView;

        // profile sections
        {
            View profileHeader = inflater.inflate(R.layout.vector_settings_header, null);
            textView = (TextView) profileHeader.findViewById(R.id.vector_settings_header_text);
            textView.setText(this.getString(R.string.settings_user_settings));
            layout.addView(profileHeader);
        }

        // avatar
        {
            View avartarView = inflater.inflate(R.layout.vector_settings_text_avatar, null);
            ImageView imageview = (ImageView)avartarView.findViewById(R.id.avatar_img);
            mSettingsViews.put(PROFILE_PICTURE_KEY, imageview);
            layout.addView(avartarView);
        }

        layout.addView(inflater.inflate(R.layout.vector_settings_sepator, null));

        // display name
        {
            View displayNameView = inflater.inflate(R.layout.vector_settings_double_texts, null);
            textView = (TextView) displayNameView.findViewById(R.id.vector_settings_large_text);
            textView.setText(this.getString(R.string.settings_display_name));
            textView = (TextView) displayNameView.findViewById(R.id.vector_settings_sub_text);
            mSettingsViews.put(PROFILE_DISPLAYNAME_KEY, textView);
            layout.addView(displayNameView);
        }

        layout.addView(inflater.inflate(R.layout.vector_settings_sepator, null));

        // change password
        {
            View passwordView = inflater.inflate(R.layout.vector_settings_double_texts, null);
            textView = (TextView) passwordView.findViewById(R.id.vector_settings_large_text);
            textView.setText(this.getString(R.string.settings_change_password));
            textView = (TextView) passwordView.findViewById(R.id.vector_settings_sub_text);
            textView.setText("*******");
            layout.addView(passwordView);
        }

        // notification sections
        {
            View profileHeader = inflater.inflate(R.layout.vector_settings_header_large, null);
            textView = (TextView) profileHeader.findViewById(R.id.vector_settings_header_text);
            textView.setText(this.getString(R.string.settings_notifications));
            layout.addView(profileHeader);
        }
    }

    private void refresh() {
        // avatar
        MyUser myUser = mSession.getMyUser();
        ImageView avatarImageView = (ImageView)mSettingsViews.get(PROFILE_PICTURE_KEY);
        VectorUtils.setMemberAvatar(avatarImageView, mSession.getMyUser().userId, mSession.getMyUser().displayname);

        String roomAvatarUrl = myUser.avatarUrl;

        if (null != roomAvatarUrl) {
            int size = getResources().getDimensionPixelSize(org.matrix.androidsdk.R.dimen.chat_avatar_size);
            mSession.getMediasCache().loadAvatarThumbnail(mSession.getHomeserverConfig(), avatarImageView, roomAvatarUrl, size);
        }

        // display name
        {
            TextView texview = (TextView)mSettingsViews.get(PROFILE_DISPLAYNAME_KEY);
            texview.setText(myUser.displayname);
        }
    }
}
