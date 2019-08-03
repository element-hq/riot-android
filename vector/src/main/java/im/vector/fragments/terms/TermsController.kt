package im.vector.fragments.terms

import android.view.View
import com.airbnb.epoxy.TypedEpoxyController

class TermsController(private val listener: TermsController.Listener) : TypedEpoxyController<List<Term>>() {

    override fun buildModels(data: List<Term>?) {
        data?.forEach { term ->
            terms {
                id(term.url)
                name(term.name)
                description(term.description)
                checked(term.accepted)


                clickListener(View.OnClickListener { listener.review(term) })
                checkChangeListener { _, isChecked ->
                    listener.setChecked(term, isChecked)
                }
            }
        }
    }

    interface Listener {
        fun setChecked(term: Term, isChecked: Boolean)
        fun review(term: Term)
    }
}