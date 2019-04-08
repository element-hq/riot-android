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
package im.vector

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.view.View
import com.tapadoo.alerter.Alerter
import com.tapadoo.alerter.OnHideAlertListener
import im.vector.activity.ShortCodeDeviceVerificationActivity
import java.lang.ref.WeakReference

/**
 * Responsible of displaying important popup alerts on top of the screen.
 * Alerts are stacked and will be displayed sequentially
 */
object PopupAlertManager {


    private var weakCurrentActivity: WeakReference<Activity>? = null
    private var currentAlerter: VectorAlert? = null

    private val alertFilo = ArrayList<VectorAlert>()


    fun postVectorAlert(alert: VectorAlert) {
        synchronized(alertFilo) {
            alertFilo.add(alert)
        }
        displayNextIfPossible()
    }

    fun cancelAlert(uid: String) {
        synchronized(alertFilo) {
            alertFilo.listIterator().apply {
                while (this.hasNext()) {
                    val next = this.next()
                    if (next.uid == uid) {
                        this.remove()
                    }
                }
            }
        }

        //it could also be the current one
        if (currentAlerter?.uid == uid) {
            Alerter.hide()
            currentIsDismmissed()
        }
    }


    fun onNewActivityDisplayed(activity: Activity) {
        //we want to remove existing popup on previous activity and display it on new one
        if (currentAlerter != null) {
            weakCurrentActivity?.get()?.let {
                Alerter.clearCurrent(it)
            }
        }

        if (shouldIgnoreActivity(activity)) {
            return
        }

        weakCurrentActivity = WeakReference(activity)

        if (currentAlerter != null) {
            currentAlerter?.let { showAlert(it, activity) }
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                displayNextIfPossible()
            }, 2000)
        }
    }

    fun shouldIgnoreActivity(activity: Activity): Boolean {
        if (activity is ShortCodeDeviceVerificationActivity) {
            return true
        }
        return false
    }


    private fun displayNextIfPossible() {
        val currentActivity = weakCurrentActivity?.get()
        if (Alerter.isShowing || currentActivity == null) {
            //will retry later
            return
        }
        val next: VectorAlert?
        synchronized(alertFilo) {
            next = alertFilo.firstOrNull()
            if (next != null) alertFilo.remove(next)
        }
        currentAlerter = next
        next?.let { showAlert(it, currentActivity) }
    }

    private fun showAlert(alert: VectorAlert, activity: Activity) {
        alert.weakCurrentActivity = WeakReference(activity)
        Alerter.create(activity)
                .setTitle(alert.title)
                .setText(alert.description)
                .apply {
                    alert.iconId?.let {
                        setIcon(it)
                    }
                    alert.actions.forEach { action ->
                        addButton(action.title, R.style.AlerterButton, View.OnClickListener {
                            if (action.autoClose) {
                                currentIsDismmissed()
                                Alerter.hide()
                            }
                            try {
                                action.action.run()
                            } catch (e: Exception) {

                            }

                        })
                    }
                    setOnClickListener(View.OnClickListener { _ ->
                        currentIsDismmissed()
                        Alerter.hide()
                        alert.contentAction?.run()
                    })

                }
                .setOnHideListener(OnHideAlertListener {
                    //called when dissmissed on swipe
                    alert.dissmissedAction?.run()
                    currentIsDismmissed()
                })
                .enableSwipeToDismiss()
                .enableInfiniteDuration(true)
                .setBackgroundColorRes(alert.colorRes ?: R.color.notification_accent_color)
                .show()
    }

    fun currentIsDismmissed() {
        //current alert has been hidden
        currentAlerter = null
        Handler(Looper.getMainLooper()).postDelayed({
            displayNextIfPossible()
        }, 500)
    }

    class VectorAlert(val uid: String, val title: String, val description: String, @DrawableRes val iconId: Int?) {

        data class Button(val title: String, val action: Runnable, val autoClose: Boolean)

        //will be set by manager, and accessible by actions at runtime
        var weakCurrentActivity: WeakReference<Activity>? = null

        val actions = ArrayList<Button>()

        var contentAction: Runnable? = null
        var dissmissedAction: Runnable? = null

        fun addButton(title: String, action: Runnable, autoClose: Boolean = true) {
            actions.add(Button(title, action, autoClose))
        }

        @ColorRes
        var colorRes: Int? = null
    }
}