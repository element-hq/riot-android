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

import android.app.assist.AssistStructure;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

import org.matrix.androidsdk.util.Log;

import butterknife.BindView;
import butterknife.ButterKnife;
import im.vector.R;

public class NotificationPrivacyActivity extends RiotAppCompatActivity  {

    private static final String LOG_TAG = NotificationPrivacyActivity.class.getSimpleName();
    public static AssistStructure.ViewNode radioButtonPrivacyNormal;

    @BindView(R.id.tv_apps_needs_permission)
    TextView tvNeedPermission;

    @BindView(R.id.rb_normal_notification_privacy)
    RadioButton rbPrivacyNormal;

    @BindView(R.id.rb_notification_low_detail)
    RadioButton rbPrivacyLowDetail;

    @BindView(R.id.rb_notification_reduce_privacy)
    RadioButton rbPrivacyReduced;

    @BindView(R.id.tv_normal_notification_privacy)
    TextView tvPrivacyNormal;

    @BindView(R.id.tv_notification_low_detail)
    TextView tvPrivacyLowDetail;

    @BindView(R.id.tv_notification_reduce_privacy)
    TextView tvPrivacyReduced;

    public static Intent getIntent(final Context context) {
        return new Intent(context, NotificationPrivacyActivity.class);
    }

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

        // The permission request is only necessary for devices os versions greater than API 23 (M)
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            tvNeedPermission.setVisibility(View.VISIBLE);
        } else{
            tvNeedPermission.setVisibility(View.GONE);
        }

        rbPrivacyNormal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rbPrivacyNormal.setChecked(true);
                rbPrivacyLowDetail.setChecked(false);
                rbPrivacyReduced.setChecked(false);

                rbPrivacyNormal.isChecked();
                Log.d(LOG_TAG, "RadioButton NotificationPrivacyNormal is selected");

                //TODO
            }
        });

        rbPrivacyLowDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rbPrivacyNormal.setChecked(false);
                rbPrivacyLowDetail.setChecked(true);
                rbPrivacyReduced.setChecked(false);

                rbPrivacyLowDetail.isChecked();
                Log.d(LOG_TAG, "RadioButton NotificationPrivacyLowDetail is selected");

                // TODO
            }
        });

        rbPrivacyReduced.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rbPrivacyNormal.setChecked(false);
                rbPrivacyLowDetail.setChecked(false);
                rbPrivacyReduced.setChecked(true);

                rbPrivacyReduced.isChecked();
                Log.d(LOG_TAG, "RadioButton NotificationPrivacyReduced is selected");

                //TODO
            }
        });
    }


}
