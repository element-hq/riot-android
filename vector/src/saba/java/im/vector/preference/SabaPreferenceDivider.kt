package im.vector.preference

import android.content.Context
import android.support.v7.preference.Preference
import android.util.AttributeSet
import im.vector.R

class SabaPreferenceDivider @JvmOverloads constructor(context: Context,
                                                      attrs: AttributeSet? = null,
                                                      defStyleAttr: Int = 0,
                                                      defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        layoutResource = R.layout.vector_preference_divider
        setVisible(false)
    }
}