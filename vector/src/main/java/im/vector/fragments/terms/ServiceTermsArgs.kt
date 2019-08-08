package im.vector.fragments.terms

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.matrix.androidsdk.rest.client.TermsRestClient

@Parcelize
data class ServiceTermsArgs(
        val type: TermsRestClient.Companion.ServiceType,
        val baseURL: String
) : Parcelable
