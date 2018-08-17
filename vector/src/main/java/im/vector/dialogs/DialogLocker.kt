package im.vector.dialogs

import android.app.AlertDialog
import android.os.Bundle
import im.vector.activity.interfaces.Restorable
import org.matrix.androidsdk.util.Log

private const val KEY_DIALOG_IS_DISPLAYED = "DialogLocker.KEY_DIALOG_IS_DISPLAYED"
private const val LOG_TAG = "DialogLocker"

class DialogLocker(savedInstanceState: Bundle?) : Restorable {

    private var isDialogDisplayed = savedInstanceState?.getBoolean(KEY_DIALOG_IS_DISPLAYED, false) == true

    fun unlock() {
        isDialogDisplayed = false
    }

    fun lock() {
        isDialogDisplayed = true
    }

    fun displayDialog(builder: () -> AlertDialog.Builder): AlertDialog? {
        return if (isDialogDisplayed) {
            Log.w(LOG_TAG, "Filtered dialog request")
            null
        } else {
            builder.invoke().create().apply {
                setOnShowListener { lock() }
                setOnCancelListener { unlock() }
                setOnDismissListener { unlock() }
                show()
            }
        }
    }


    override fun saveState(outState: Bundle) {
        outState.putBoolean(KEY_DIALOG_IS_DISPLAYED, isDialogDisplayed)
    }
}