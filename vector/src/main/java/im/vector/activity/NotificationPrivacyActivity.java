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
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;

import butterknife.BindView;
import butterknife.OnClick;
import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.util.RequestCodesKt;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.util.SystemUtilsKt;
import kotlin.Pair;

/*
 * This activity allows the user to choose a notifications privacy policy.
 * The interest is to educate the user on the impacts of his choice of the type of notifications
 * on the privacy policy of his data.
 */
public class NotificationPrivacyActivity extends VectorAppCompatActivity {

    private static final String LOG_TAG = NotificationPrivacyActivity.class.getSimpleName();

    /* ==========================================================================================
     * UI
     * ========================================================================================== */

    @BindView(R.id.tv_apps_needs_permission)
    TextView tvNeedPermission;

    @BindView(R.id.tv_apps_no_permission)
    TextView tvNoPermission;

    @BindView(R.id.rb_normal_notification_privacy)
    RadioButton rbPrivacyNormal;

    @BindView(R.id.rb_notification_low_detail)
    RadioButton rbPrivacyLowDetail;

    @BindView(R.id.rb_notification_reduce_privacy)
    RadioButton rbPrivacyReduced;

    /* ==========================================================================================
     * LifeCycle
     * ========================================================================================== */

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
        configureToolbar();

        setWaitingView(findViewById(R.id.waiting_view));

        // The permission request is only necessary for devices os versions greater than API 23 (M)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tvNeedPermission.setVisibility(View.VISIBLE);
            tvNoPermission.setVisibility(View.VISIBLE);
        } else {
            tvNeedPermission.setVisibility(View.GONE);
            tvNoPermission.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        refreshNotificationPrivacy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == RequestCodesKt.BATTERY_OPTIMIZATION_REQUEST_CODE) {
                // Ok, NotificationPrivacy.NORMAL can be set
                doSetNotificationPrivacy(GcmRegistrationManager.NotificationPrivacy.NORMAL);
            }
        }
    }

    /* ==========================================================================================
     * UI Event
     * ========================================================================================== */

    @OnClick(R.id.rly_normal_notification_privacy)
    void onNormalClick() {
        updateNotificationPrivacy(GcmRegistrationManager.NotificationPrivacy.NORMAL);
    }

    @OnClick(R.id.rly_low_detail_notifications)
    void onLowDetailClick() {
        updateNotificationPrivacy(GcmRegistrationManager.NotificationPrivacy.LOW_DETAIL);
    }

    @OnClick(R.id.rly_reduced_privacy_notifications)
    void onReducedPrivacyClick() {
        updateNotificationPrivacy(GcmRegistrationManager.NotificationPrivacy.REDUCED);
    }

    /* ==========================================================================================
     * Private
     * ========================================================================================== */

    private void updateNotificationPrivacy(GcmRegistrationManager.NotificationPrivacy newNotificationPrivacy) {
        // for the "NORMAL" privacy, the app needs to be able to run in background
        // this requires the IgnoreBatteryOptimizations permission from android M
        if (newNotificationPrivacy == GcmRegistrationManager.NotificationPrivacy.NORMAL
                && !SystemUtilsKt.isIgnoringBatteryOptimizations(this)) {
            // Request the battery optimization cancellation to the user
            SystemUtilsKt.requestDisablingBatteryOptimization(this, RequestCodesKt.BATTERY_OPTIMIZATION_REQUEST_CODE);
        } else {
            doSetNotificationPrivacy(newNotificationPrivacy);
        }
    }

    private void refreshNotificationPrivacy() {
        GcmRegistrationManager.NotificationPrivacy notificationPrivacy = Matrix.getInstance(this)
                .getSharedGCMRegistrationManager()
                .getNotificationPrivacy();

        rbPrivacyNormal.setChecked(notificationPrivacy == GcmRegistrationManager.NotificationPrivacy.NORMAL);
        rbPrivacyLowDetail.setChecked(notificationPrivacy == GcmRegistrationManager.NotificationPrivacy.LOW_DETAIL);
        rbPrivacyReduced.setChecked(notificationPrivacy == GcmRegistrationManager.NotificationPrivacy.REDUCED);
    }

    private void doSetNotificationPrivacy(GcmRegistrationManager.NotificationPrivacy notificationPrivacy) {
        showWaitingView();

        // Set the new notification privacy
        Matrix.getInstance(this)
                .getSharedGCMRegistrationManager()
                .setNotificationPrivacy(notificationPrivacy, new SimpleApiCallback<Void>(this) {
                    @Override
                    public void onSuccess(Void info) {
                        hideWaitingView();

                        refreshNotificationPrivacy();
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        hideWaitingView();

                        super.onNetworkError(e);
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        hideWaitingView();

                        super.onMatrixError(e);
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        hideWaitingView();

                        super.onUnexpectedError(e);
                    }
                });
    }

    /* ==========================================================================================
     * Public static
     * ========================================================================================== */

    /**
     * Return an intent to start this Activity
     *
     * @param context Android context
     * @return an intent to start this Activity
     */
    public static Intent getIntent(final Context context) {
        return new Intent(context, NotificationPrivacyActivity.class);
    }

    /**
     * Get the displayed i18ned string for a notification privacy setting.
     *
     * @param context             Android context
     * @param notificationPrivacy the setting to stringify
     * @return a string
     */
    public static String getNotificationPrivacyString(Context context, GcmRegistrationManager.NotificationPrivacy notificationPrivacy) {
        int stringRes;

        switch (notificationPrivacy) {
            case REDUCED:
                stringRes = R.string.settings_notification_privacy_reduced;
                break;
            case LOW_DETAIL:
                stringRes = R.string.settings_notification_privacy_low_detail;
                break;
            case NORMAL:
            default:
                stringRes = R.string.settings_notification_privacy_normal;
                break;
        }

        return context.getString(stringRes);
    }
}
