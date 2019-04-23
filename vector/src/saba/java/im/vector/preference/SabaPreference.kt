package im.vector.preference

import android.content.Context
import android.util.AttributeSet


class SabaPreference: VectorPreference {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    init {
        setVisible(false)
    }
}