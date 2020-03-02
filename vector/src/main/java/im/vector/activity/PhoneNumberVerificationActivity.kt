/*
 * Copyright 2017 Vector Creations Ltd
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

package im.vector.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import im.vector.Matrix
import im.vector.R
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.JsonUtils
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.rest.client.LoginRestClient
import org.matrix.androidsdk.rest.model.SuccessResult
import org.matrix.androidsdk.rest.model.login.AuthParams
import org.matrix.androidsdk.rest.model.login.AuthParamsLoginPassword
import org.matrix.androidsdk.rest.model.pid.ThreePid

class PhoneNumberVerificationActivity : VectorAppCompatActivity(), TextView.OnEditorActionListener, TextWatcher {

    private var mPhoneNumberCode: TextInputEditText? = null
    private var mPhoneNumberCodeLayout: TextInputLayout? = null

    private var mSession: MXSession? = null
    private var mThreePid: ThreePid? = null

    // True when a phone number token is submitted
    // Used to prevent user to submit several times in a row
    private var mIsSubmittingToken: Boolean = false

    /*
     * *********************************************************************************************
     * Activity lifecycle
     * *********************************************************************************************
     */

    override fun getLayoutRes(): Int {
        return R.layout.activity_phone_number_verification
    }

    override fun getTitleRes(): Int {
        return R.string.settings_phone_number_verification
    }

    override fun initUiAndData() {
        configureToolbar()

        mPhoneNumberCode = findViewById(R.id.phone_number_code_value)
        mPhoneNumberCodeLayout = findViewById(R.id.phone_number_code)
        waitingView = findViewById(R.id.loading_view)

        val intent = intent
        mSession = Matrix.getInstance(this)!!.getSession(intent.getStringExtra(EXTRA_MATRIX_ID))

        if (null == mSession || !mSession!!.isAlive) {
            finish()
            return
        }

        mThreePid = intent.getParcelableExtra(EXTRA_PID)

        mPhoneNumberCode!!.addTextChangedListener(this)
        mPhoneNumberCode!!.setOnEditorActionListener(this)
    }

    override fun onResume() {
        super.onResume()
        mIsSubmittingToken = false
    }

    override fun getMenuRes(): Int {
        return R.menu.menu_phone_number_verification
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_verify_phone_number -> {
                submitCode()
                return true
            }
            else                            -> return super.onOptionsItemSelected(item)
        }
    }

    /*
     * *********************************************************************************************
     * Utils
     * *********************************************************************************************
     */

    /**
     * Submit code (token) to attach phone number to account
     */
    private fun submitCode() {
        if (!mIsSubmittingToken) {
            mIsSubmittingToken = true
            if (TextUtils.isEmpty(mPhoneNumberCode!!.text)) {
                mPhoneNumberCodeLayout!!.isErrorEnabled = true
                mPhoneNumberCodeLayout!!.error = getString(R.string.settings_phone_number_verification_error_empty_code)
            } else {
                showWaitingView()
                mSession!!.identityServerManager.submitValidationToken(mThreePid!!, mPhoneNumberCode!!.text!!.toString(),
                        object : ApiCallback<SuccessResult> {
                            override fun onSuccess(result: SuccessResult) {
                                if (result.success) {
                                    // the validation of mail ownership succeed, just resume the registration flow
                                    // next step: just register
                                    Log.e(LOG_TAG, "## submitPhoneNumberValidationToken(): onSuccess() - registerAfterEmailValidations() started")
                                    registerAfterPhoneNumberValidation(mThreePid, null)
                                } else {
                                    Log.e(LOG_TAG, "## submitPhoneNumberValidationToken(): onSuccess() - failed (success=false)")
                                    onSubmitCodeError(getString(R.string.settings_phone_number_verification_error))
                                }
                            }

                            override fun onNetworkError(e: Exception) {
                                onSubmitCodeError(e.localizedMessage)
                            }

                            override fun onMatrixError(e: MatrixError) {
                                onSubmitCodeError(e.localizedMessage)
                            }

                            override fun onUnexpectedError(e: Exception) {
                                onSubmitCodeError(e.localizedMessage)
                            }
                        })
            }

        }
    }

    private fun registerAfterPhoneNumberValidation(pid: ThreePid?, auth: AuthParams?) {
        mSession!!.identityServerManager.finalize3pidAddSession(pid!!, auth, object : ApiCallback<Void?> {
            override fun onSuccess(info: Void?) {
                val intent = Intent()
                setResult(Activity.RESULT_OK, intent)
                finish()
            }

            override fun onNetworkError(e: Exception) {
                onSubmitCodeError(e.localizedMessage)
            }

            override fun onMatrixError(e: MatrixError) {
                if (e.mStatus == 401 && e.mErrorBodyAsString.isNullOrBlank().not()) {
                    val flow = JsonUtils.toRegistrationFlowResponse(e.mErrorBodyAsString)
                    if (flow != null) {
                        val supportsLoginPassword = flow.flows.any { it.stages == listOf(LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD) }
                        if (supportsLoginPassword) {
                            //we prompt for it

                            val invalidPassError = getString(R.string.login_error_forbidden)
                                    .takeIf { e.errcode == MatrixError.FORBIDDEN }

                            DialogUtils.promptPassword(this@PhoneNumberVerificationActivity,
                                    invalidPassError,
                                    (auth as? AuthParamsLoginPassword)?.let { auth.password },
                                    { password ->
                                        val authParams = AuthParamsLoginPassword().apply {
                                            this.user = mSession?.myUserId
                                            this.password = password
                                        }
                                        registerAfterPhoneNumberValidation(pid, authParams)
                                    },
                                    {
                                        onSubmitCodeError(getString(R.string.settings_add_3pid_authentication_needed))
                                    })
                        } else {
                            //you can only do that on mobile
                            AlertDialog.Builder(this@PhoneNumberVerificationActivity)
                                    .setTitle(R.string.dialog_title_error)
                                    .setMessage(R.string.settings_add_3pid_flow_not_supported)
                                    .setPositiveButton(R.string._continue) { _, _ ->
                                        hideWaitingView()
                                    }
                                    .show()

                        }

                    } else {
                        onSubmitCodeError(e.localizedMessage)
                    }
                } else {
                    onSubmitCodeError(e.localizedMessage)
                }

            }

            override fun onUnexpectedError(e: Exception) {
                onSubmitCodeError(e.localizedMessage)
            }
        })
    }

    private fun onSubmitCodeError(errorMessage: String) {
        mIsSubmittingToken = false
        hideWaitingView()
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    /*
     * *********************************************************************************************
     * Listeners
     * *********************************************************************************************
     */

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean {
        if (actionId == EditorInfo.IME_ACTION_DONE && !isFinishing) {
            submitCode()
            return true
        }
        return false
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {

    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

    }

    override fun afterTextChanged(s: Editable) {
        if (mPhoneNumberCodeLayout!!.error != null) {
            mPhoneNumberCodeLayout!!.error = null
            mPhoneNumberCodeLayout!!.isErrorEnabled = false
        }
    }

    companion object {

        private val LOG_TAG = PhoneNumberVerificationActivity::class.java.simpleName

        private val EXTRA_MATRIX_ID = "EXTRA_MATRIX_ID"
        private val EXTRA_PID = "EXTRA_PID"

        /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

        fun getIntent(context: Context, sessionId: String, pid: ThreePid): Intent {
            val intent = Intent(context, PhoneNumberVerificationActivity::class.java)
            intent.putExtra(EXTRA_MATRIX_ID, sessionId)
            intent.putExtra(EXTRA_PID, pid)
            return intent
        }
    }
}
