package im.vector.preference

import android.content.Context
import android.support.v7.preference.PreferenceCategory
import android.util.AttributeSet

class SabaPreferenceCategory : PreferenceCategory {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        setVisible(false)
    }
}