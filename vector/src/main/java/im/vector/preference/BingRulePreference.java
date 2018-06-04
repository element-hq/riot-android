/*
 * Copyright 2016 OpenMarket Ltd
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
     * @return the selected bing rule
     */
    public BingRule getRule() {
        return mBingRule;
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
        if (null != mBingRule) {
            if (TextUtils.equals(mBingRule.ruleId, BingRule.RULE_ID_SUPPRESS_BOTS_NOTIFICATIONS)) {
                if (mBingRule.shouldNotNotify()) {
                    if (mBingRule.isEnabled) {
                        return NOTIFICATION_OFF_INDEX;
                    } else {
                        return NOTIFICATION_ON_INDEX;
                    }
                } else if (mBingRule.shouldNotify()) {
                    return NOTIFICATION_NOISY_INDEX;
                }
            }

            if (mBingRule.isEnabled) {
                if (mBingRule.shouldNotNotify()) {
                    return NOTIFICATION_OFF_INDEX;
                } else if (null != mBingRule.getNotificationSound()) {
                    return NOTIFICATION_NOISY_INDEX;
                } else {
                    return NOTIFICATION_ON_INDEX;
                }
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
     * Create a bing rule with the updated required at index.
     *
     * @param index index
     * @return a bing rule with the updated flags / null if there is no update
     */
    public BingRule createRule(int index) {
        BingRule rule = null;

        if ((null != mBingRule) && (index != getRuleStatusIndex())) {
            rule = new BingRule(mBingRule);

            if (TextUtils.equals(rule.ruleId, BingRule.RULE_ID_SUPPRESS_BOTS_NOTIFICATIONS)) {
                if (NOTIFICATION_OFF_INDEX == index) {
                    rule.isEnabled = true;
                    rule.setNotify(false);
                } else if (NOTIFICATION_ON_INDEX == index) {
                    rule.isEnabled = false;
                    rule.setNotify(false);
                } else if (NOTIFICATION_NOISY_INDEX == index) {
                    rule.isEnabled = true;
                    rule.setNotify(true);
                    rule.setNotificationSound(BingRule.ACTION_VALUE_DEFAULT);
                }

                return rule;
            }


            if (NOTIFICATION_OFF_INDEX == index) {
                if (TextUtils.equals(mBingRule.kind, BingRule.KIND_UNDERRIDE) || TextUtils.equals(rule.ruleId, BingRule.RULE_ID_SUPPRESS_BOTS_NOTIFICATIONS)) {
                    rule.setNotify(false);
                } else {
                    rule.isEnabled = false;
                }
            } else {
                rule.isEnabled = true;
                rule.setNotify(true);
                rule.setHighlight(!TextUtils.equals(mBingRule.kind, BingRule.KIND_UNDERRIDE)
                        && !TextUtils.equals(rule.ruleId, BingRule.RULE_ID_INVITE_ME)
                        && (NOTIFICATION_NOISY_INDEX == index));
                if (NOTIFICATION_NOISY_INDEX == index) {
                    rule.setNotificationSound(TextUtils.equals(rule.ruleId, BingRule.RULE_ID_CALL) ?
                            BingRule.ACTION_VALUE_RING : BingRule.ACTION_VALUE_DEFAULT);
                } else {
                    rule.removeNotificationSound();
                }
            }
        }

        return rule;
    }
}