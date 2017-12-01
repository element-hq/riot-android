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
import android.text.TextUtils;
import android.util.AttributeSet;

import org.matrix.androidsdk.rest.model.bingrules.BingRule;

import im.vector.R;

public class BingRulePreference extends VectorCustomActionEditTextPreference {

    private BingRule mBingRule;

    // Sequences
    private CharSequence[] mRuleStatuses = null;

    // index in mRuleStatuses
    private static final int NOTIFICATION_OFF_INDEX = 0;
    private static final int NOTIFICATION_ON_INDEX = 1;
    private static final int NOTIFICATION_NOISY_INDEX = 2;

    public BingRulePreference(Context context) {
        super(context);
    }

    public BingRulePreference(Context context, int aTypeface) {
        super(context);
    }

    public BingRulePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BingRulePreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Update the bing rule.
     *
     * @param aBingRule
     */
    public void setBingRule(BingRule aBingRule) {
        mBingRule = aBingRule;
        refreshSummary();
    }

    /**
     * Refresh the summary
     */
    public void refreshSummary() {
        setSummary(getBingRuleStatuses()[getRuleStatusIndex()]);
    }

    /**
     * @return the bing rule status index
     */
    public int getRuleStatusIndex() {
        if ((null != mBingRule) && mBingRule.isEnabled) {
            if (mBingRule.shouldHighlight()) {
                return NOTIFICATION_NOISY_INDEX;
            } else {
                return NOTIFICATION_ON_INDEX;
            }
        }

        return NOTIFICATION_OFF_INDEX;
    }

    /**
     * @return the supported bing rule statuses
     */
    public CharSequence[] getBingRuleStatuses() {
        if (null == mRuleStatuses) {
            mRuleStatuses = new CharSequence[]{
                    getContext().getString(R.string.notification_off),
                    getContext().getString(R.string.notification_on),
                    getContext().getString(R.string.notification_noisy)};
        }

        return mRuleStatuses;
    }

    /**
     * Update the bing rule with the select items
     *
     * @param index index
     */
    public BingRule updateWithStatusIndex(int index) {
        if (null != mBingRule) {
            if (NOTIFICATION_OFF_INDEX == index) {
                mBingRule.isEnabled = false;
            } else {
                mBingRule.isEnabled = true;
                mBingRule.setNotify(true);
                mBingRule.setHighlight(NOTIFICATION_NOISY_INDEX == index);

                if (NOTIFICATION_NOISY_INDEX == index) {
                    mBingRule.setNotificationSound(TextUtils.equals(mBingRule.ruleId, BingRule.RULE_ID_CALL) ? BingRule.ACTION_VALUE_RING : BingRule.ACTION_VALUE_DEFAULT);
                }
            }
        }

        return mBingRule;
    }
}