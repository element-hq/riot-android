package im.vector.analytics

import android.content.Context
import im.vector.BuildConfig
import im.vector.util.PreferencesManager
import org.matrix.androidsdk.util.Log

class AppAnalytics(private val context: Context, private vararg val analytics: Analytics) : Analytics {

    override fun trackScreen(screen: String, title: String?) {
        if (!BuildConfig.DEBUG && PreferencesManager.useAnalytics(context)) {
            analytics.forEach {
                it.trackScreen(screen, title)
            }
        } else {
            Log.d("Analytics - screen", screen)
        }
    }

    override fun visitVariable(index: Int, name: String, value: String) {
        if (!BuildConfig.DEBUG && PreferencesManager.useAnalytics(context)) {
            analytics.forEach {
                it.visitVariable(index, name, value)
            }
        } else {
            Log.d("Analytics - visit variable", "Variable $name at index $index: $value")
        }
    }

    override fun trackEvent(event: TrackingEvent) {
        if (!BuildConfig.DEBUG && PreferencesManager.useAnalytics(context)) {
            analytics.forEach {
                it.trackEvent(event)
            }
        } else {
            Log.d("Analytics - event", event.toString())
        }
    }

    override fun forceDispatch() {
        analytics.forEach {
            it.forceDispatch()
        }
    }

}