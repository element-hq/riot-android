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
package im.vector.fragments.troubleshoot

import android.support.v4.app.Fragment
import im.vector.R
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.BingRulesManager
import org.matrix.androidsdk.rest.model.bingrules.BingRule

/**
 * Check that the main pushRule (RULE_ID_DISABLE_ALL) is correctly setup
 */
class TestAccountSettings(val fragment: Fragment, val session: MXSession?) : TroubleshootTest(R.string.settings_troubleshoot_test_account_settings_title) {

    override fun perform() {
        val defaultRule = session?.dataHandler?.bingRulesManager?.pushRules()?.findDefaultRule(BingRule.RULE_ID_DISABLE_ALL)
        if (defaultRule != null) {
            if (!defaultRule.isEnabled) {
                description = fragment.getString(R.string.settings_troubleshoot_test_account_settings_success)
                quickFix = null
                status = TestStatus.SUCCESS
            } else {
                description = fragment.getString(R.string.settings_troubleshoot_test_account_settings_failed)
                quickFix = object : TroubleshootQuickFix(R.string.settings_troubleshoot_test_account_settings_quickfix) {
                    override fun doFix() {
                        if (manager?.diagStatus == TestStatus.RUNNING) return //wait before all is finished
                        session?.dataHandler?.bingRulesManager?.updateEnableRuleStatus(defaultRule, !defaultRule.isEnabled,
                                object : BingRulesManager.onBingRuleUpdateListener {

                                    override fun onBingRuleUpdateSuccess() {
                                        manager?.retry()
                                    }

                                    override fun onBingRuleUpdateFailure(errorMessage: String) {
                                        manager?.retry()
                                    }
                                })
                    }
                }
                status = TestStatus.FAILED
            }
        } else {
            //should not happen?
            status = TestStatus.FAILED
        }
    }
}