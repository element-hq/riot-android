package im.vector.directory.people.model

import android.os.Parcelable
import android.text.SpannableStringBuilder
import android.text.Spanned
import im.vector.directory.RoundedBackgroundSpan
import kotlinx.android.parcel.Parcelize

@Parcelize
data class DirectoryPeople(val id: String, val officialName: String, val jobTitle: String, val avatarUrl: String?, val organisations: String, val businessUnits: String) : Parcelable {
    fun getSpannableStringBuilder(spanTextBackgroundColor: Int, spanTextColor: Int, textSize: Float, title: String, value: String): SpannableStringBuilder {
        val stringBuilder = SpannableStringBuilder()
        stringBuilder.append(title).append(": ").append(value).append(" ")
        val tagSpan = RoundedBackgroundSpan(spanTextBackgroundColor, spanTextColor, textSize)
        stringBuilder.setSpan(tagSpan, 0, stringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return stringBuilder
    }
}