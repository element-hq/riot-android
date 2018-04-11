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

package im.vector.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;

public class NotificationPrivacyActivity extends RiotAppCompatActivity  {

    @BindView(R.id.tv_apps_needs_permission)
    TextView tvNeedPermission;



    /*
     ===============================================================================================
     Static methods
     ===============================================================================================
     */

    public static Intent getIntent(final Context context) {
        return new Intent(context, NotificationPrivacyActivity.class);
    }



     /*
     ===============================================================================================

     ===============================================================================================
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.settings_notification_privacy);
        setContentView(R.layout.activity_notification_privacy);
        ButterKnife.bind(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowHomeEnabled(true);
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            tvNeedPermission.setVisibility(View.VISIBLE);
        } else{
            tvNeedPermission.setVisibility(View.GONE);
        }
    }
}
