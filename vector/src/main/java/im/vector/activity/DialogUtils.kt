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
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import im.vector.R
import im.vector.extensions.showPassword

object DialogUtils {

    fun promptPassword(context: Context, errorText: String? = null, defaultPwd: String? = null,
                       done: (String) -> Unit,
                       cancel: (() -> Unit)? = null) {
        val view: ViewGroup = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_password, null) as ViewGroup

        val showPassword: ImageView = view.findViewById(R.id.confirm_password_show_passwords)
        val passwordTil: TextInputLayout = view.findViewById(R.id.confirm_password_til)
        val passwordText: TextInputEditText = view.findViewById(R.id.password_label)
        passwordText.setText(defaultPwd)

        var passwordShown = false

        showPassword.setOnClickListener {
            passwordShown = !passwordShown
            passwordText.showPassword(passwordShown)
            showPassword.setImageResource(if (passwordShown) R.drawable.ic_eye_closed_black else R.drawable.ic_eye_black)
        }

        passwordTil.error = errorText

        AlertDialog.Builder(context)
                .setView(view)
                .setPositiveButton(R.string._continue) { tv, _ ->
                    done(passwordText.text.toString())
                }
                .apply {
                    if (cancel != null) {
                        setNegativeButton(R.string.cancel) { _, _ ->
                            cancel()
                        }
                    }
                }

                .show()

    }
}