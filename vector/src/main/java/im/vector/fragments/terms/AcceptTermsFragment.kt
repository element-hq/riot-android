package im.vector.fragments.terms

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.widget.Button
import butterknife.BindView
import com.airbnb.epoxy.EpoxyRecyclerView
import im.vector.R
import im.vector.fragments.VectorBaseFragment
import im.vector.util.openUrlInExternalBrowser
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.rest.client.TermsRestClient
import org.matrix.androidsdk.rest.model.terms.TermsResponse
import java.lang.Exception

class AcceptTermsFragment : VectorBaseFragment(), TermsController.Listener {


    lateinit var viewModel: AcceptTermsViewModel

    override fun getLayoutResId(): Int = R.layout.fragment_accept_terms

    private val termsController = TermsController(this)

    @BindView(R.id.terms_recycler_view)
    lateinit var termsList: EpoxyRecyclerView

    @BindView(R.id.terms_bottom_accept)
    lateinit var acceptButton: Button

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        termsList.setController(termsController)

        viewModel = ViewModelProviders.of(this).get(AcceptTermsViewModel::class.java)

        //Tmp build mock model
        val client = TermsRestClient()
        client.get("https://termstest.matrix.org/_matrix/integrations/v1", object : ApiCallback<TermsResponse> {
            override fun onSuccess(info: TermsResponse?) {
                val terms = ArrayList<Term>()
                info?.getLocalizedPrivacyPolicies()?.let {
                    Log.e("FOO",it.localizedUrl)
                    terms.add(
                            Term(it.localizedUrl ?: "",
                                    it.localizedName ?: "",
                                    "Utiliser des robots, des passerelles, des widgets ou des packs de stickers")
                    )
                }
                info?.getLocalizedTermOfServices()?.let {
                    Log.e("FOO",it.localizedUrl)
                    terms.add(
                            Term(it.localizedUrl ?: "",
                                    it.localizedName ?: "",
                                    "Utiliser des robots, des passerelles, des widgets ou des packs de stickers")
                    )
                }
                viewModel.termsList.postValue(terms)
            }

            override fun onUnexpectedError(e: Exception?) {}

            override fun onNetworkError(e: Exception?) {}

            override fun onMatrixError(e: MatrixError?) {}

        })

//        val terms = listOf<Term>(
//                Term("https://example.org/somewhere/privacy-1.2-fr.html",
//                        "Gestionnaire d’intégrations",
//                        "Utiliser des robots, des passerelles, des widgets ou des packs de stickers\n" +
//                                "(Politique de confidentialité)"),
//
//                Term("https://example.org/somewhere/terms-2.0-fr.html",
//                        "Gestionnaire d’intégrations",
//                        "Utiliser des robots, des passerelles, des widgets ou des packs de stickers\n" +
//                                "(Conditions d'utilisation)")
//        )
//        viewModel.termsList.postValue(terms)


        viewModel.termsList.observe(this, Observer { terms ->
            if (terms != null) {
                updateState(terms)
                acceptButton.isEnabled = terms.all { it.accepted }
            }
        })
    }

    private fun updateState(terms: List<Term>) {
        termsController.setData(terms)
    }

    companion object {
        fun newInstance(): AcceptTermsFragment {
            return AcceptTermsFragment()
        }
    }

    override fun setChecked(term: Term, isChecked: Boolean) {
        viewModel.acceptTerm(term.url,isChecked)
    }

    override fun review(term: Term) {
        openUrlInExternalBrowser(this.requireContext(), term.url)
    }
}