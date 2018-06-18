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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

import butterknife.BindView;
import im.vector.Matrix;
import im.vector.R;
import im.vector.gcm.GcmRegistrationManager;
import kotlin.Pair;

/*
 * This activity allows the user to choose a notifications privacy policy.
 * The interest is to educate the user on the impacts of his choice of the type of notifications
 * on the privacy policy of his data.
 */
public class NotificationPrivacyActivity extends RiotAppCompatActivity  {

    private static final String LOG_TAG = NotificationPrivacyActivity.class.getSimpleName();

    @BindView(R.id.tv_apps_needs_permission)
    TextView tvNeedPermission;

    @BindView(R.id.tv_apps_no_permission)
    TextView tvNoPermission;

    @BindView(R.id.rly_normal_notification_privacy)
    View rlyNormalPrivacy;

    @BindView(R.id.rly_low_detail_notifications)
    View rlyLowDetailNotifications;

    @BindView(R.id.rly_reduced_privacy_notifications)
    View rlyReducedPrivacy;

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

    @NotNull
    @Override
    public Pair getOtherThemes() {
        return new Pair(R.style.CountryPickerTheme_Dark, R.style.CountryPickerTheme_Black);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_notification_privacy;
    }

    @Override
    public int getTitleRes() {
        return R.string.settings_notification_privacy;
    }

    @Override
    public void initUiAndData() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowHomeEnabled(true);
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        // The permission request is only necessary for devices os versions greater than API 23 (M)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            tvNeedPermission.setVisibility(View.VISIBLE);
            tvNoPermission.setVisibility(View.VISIBLE);
        } else{
            tvNeedPermission.setVisibility(View.GONE);
            tvNoPermission.setVisibility(View.GONE);
        }

        refreshNotificationPrivacy();

        rlyNormalPrivacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setNotificationPrivacy(NotificationPrivacyActivity.this, GcmRegistrationManager.NotificationPrivacy.NORMAL);
                refreshNotificationPrivacy();
            }
        });

        rlyLowDetailNotifications.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setNotificationPrivacy(NotificationPrivacyActivity.this, GcmRegistrationManager.NotificationPrivacy.LOW_DETAIL);
                refreshNotificationPrivacy();
            }
        });

        rlyReducedPrivacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setNotificationPrivacy(NotificationPrivacyActivity.this, GcmRegistrationManager.NotificationPrivacy.REDUCED);
                refreshNotificationPrivacy();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNotificationPrivacy();
    }

    private void refreshNotificationPrivacy() {
        GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(this).getSharedGCMRegistrationManager();

        switch (gcmRegistrationManager.getNotificationPrivacy()) {
            case REDUCED:
                rbPrivacyNormal.setChecked(false);
                rbPrivacyLowDetail.setChecked(false);
                rbPrivacyReduced.setChecked(true);
                break;
            case LOW_DETAIL:
                rbPrivacyNormal.setChecked(false);
                rbPrivacyLowDetail.setChecked(true);
                rbPrivacyReduced.setChecked(false);
                break;
            case NORMAL:
                rbPrivacyNormal.setChecked(true);
                rbPrivacyLowDetail.setChecked(false);
                rbPrivacyReduced.setChecked(false);
                break;
        }
    }

    /**
     * Set the new notification privacy setting.
     *
     * @param activity the activity from which to display the IgnoreBatteryOptimizations permission request dialog, if required
     * @param notificationPrivacy the new setting
     */
    static public void setNotificationPrivacy(Activity activity, GcmRegistrationManager.NotificationPrivacy notificationPrivacy) {
        GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(activity).getSharedGCMRegistrationManager();

        // first, set the new privacy setting
        gcmRegistrationManager.setNotificationPrivacy(notificationPrivacy);

        // for the "NORMAL" privacy, the app needs to be able to run in background
        // this requires the IgnoreBatteryOptimizations permission from android M
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && notificationPrivacy == GcmRegistrationManager.NotificationPrivacy.NORMAL) {
            // display the system dialog for granting this permission. If previously granted, the
            // system will not show it.
            // Note: If the user finally does not grant the permission, gcmRegistrationManager.isBackgroundSyncAllowed()
            // will return false and the notification privacy will fallback to "LOW_DETAIL".
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        }
    }

    /**
     * Get the displayed i18ned string for a notification privacy setting.
     * 
     * @param context
     * @param notificationPrivacy the setting to stringify
     * @return a string
     */
    static public String getNotificationPrivacyString(Context context, GcmRegistrationManager.NotificationPrivacy notificationPrivacy) {
        String notificationPrivacyString = null;

        switch (notificationPrivacy) {
            case REDUCED:
                notificationPrivacyString = context.getString(R.string.settings_notification_privacy_reduced);
                break;
            case LOW_DETAIL:
                notificationPrivacyString = context.getString(R.string.settings_notification_privacy_low_detail);
                break;
            case NORMAL:
                notificationPrivacyString = context.getString(R.string.settings_notification_privacy_normal);
                break;}

        return notificationPrivacyString;
    }
}
