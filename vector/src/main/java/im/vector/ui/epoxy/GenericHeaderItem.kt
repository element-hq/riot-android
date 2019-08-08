package im.vector.ui.epoxy

import android.support.annotation.StringRes
import android.widget.TextView
import butterknife.BindView
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.airbnb.epoxy.EpoxyModelWithHolder
import im.vector.R

/**
 * A generic list item header left aligned with notice color.
 */
@EpoxyModelClass(layout = R.layout.item_generic_header)
abstract class GenericItemHeader : EpoxyModelWithHolder<GenericItemHeader.Holder>() {

    @EpoxyAttribute
    var text: String? = null

    @EpoxyAttribute
    @StringRes
    var textID: Int? = null

    @EpoxyAttribute
    var textSizeSp: Float = 15f

    override fun bind(holder: Holder) {
        if (textID != null) {
            holder.textView.setText(textID!!)
        } else {
            holder.textView.text = text
        }
        holder.textView.textSize = textSizeSp
    }

    class Holder : BaseEpoxyHolder() {

        @BindView(R.id.itemGenericHeaderText)
        lateinit var textView: TextView
    }
}