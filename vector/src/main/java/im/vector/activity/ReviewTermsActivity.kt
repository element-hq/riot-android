/*
 * Copyright 2019 New Vector Ltd
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
package im.vector.activity

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModelProviders
import im.vector.R
import im.vector.fragments.terms.AcceptTermsFragment
import im.vector.fragments.terms.AcceptTermsViewModel
import im.vector.fragments.terms.ServiceTermsArgs
import org.matrix.androidsdk.features.terms.TermsManager


class ReviewTermsActivity : SimpleFragmentActivity() {

    override fun initUiAndData() {
        super.initUiAndData()
        if (supportFragmentManager.fragments.isEmpty()) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, AcceptTermsFragment.newInstance())
                    .commitNow()
        }

        val viewModel = ViewModelProviders.of(this).get(AcceptTermsViewModel::class.java)
        viewModel.termsArgs = intent.getParcelableExtra(EXTRA_INFO)

        mSession = getSession(intent)

        viewModel.initSession(session)
    }

    companion object {

        private const val EXTRA_INFO = "EXTRA_INFO"

        fun intent(context: Context, serviceType: TermsManager.ServiceType, baseUrl: String, token: String): Intent {
            return Intent(context, ReviewTermsActivity::class.java).also {
                it.putExtra(EXTRA_INFO, ServiceTermsArgs(serviceType, baseUrl, token))
            }
        }
    }
}