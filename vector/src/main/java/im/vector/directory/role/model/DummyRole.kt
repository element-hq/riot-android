package im.vector.directory.role.model

import android.os.Parcelable
import android.text.SpannableStringBuilder
import android.text.Spanned
import im.vector.directory.RoundedBackgroundSpan
import kotlinx.android.parcel.Parcelize


@Parcelize
data class DummyRole(val id: String, val officialName: String, val secondaryName: String, val avatarUrl: String?, val roles: ArrayList<Role>, val speciality: ArrayList<Speciality>, val location: ArrayList<DummyLocation>) : Parcelable {
    var expanded = false
    var type: Int = 1
}

@Parcelize
data class Role(val id: String, val name: String, val category: String): Parcelable {
    fun getSpannableStringBuilder(spanTextBackgroundColor: Int, spanTextColor: Int, textSize: Float): SpannableStringBuilder {
        val stringBuilder = SpannableStringBuilder()
        stringBuilder.append("Role: ").append(name).append(", ").append("Category").append(": ").append(category).append(" ")
        //stringBuilder.setSpan(StyleSpan(Typeface.BOLD), 0, "Role: ".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        //stringBuilder.setSpan(StyleSpan(Typeface.BOLD), "Role".plus(": ").plus(name).plus(", ").length, "Role".plus(": ").plus(name).plus(", ").plus("Category").length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val tagSpan = RoundedBackgroundSpan(spanTextBackgroundColor, spanTextColor, textSize)
        stringBuilder.setSpan(tagSpan, 0, stringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return stringBuilder
    }
}

@Parcelize
data class Speciality(val id: String, val name: String): Parcelable{
     fun getSpannableStringBuilder(spanTextBackgroundColor: Int, spanTextColor: Int, textSize: Float): SpannableStringBuilder {
        val stringBuilder = SpannableStringBuilder()
        stringBuilder.append("Speciality").append(": ").append(name).append(" ")
        val tagSpan = RoundedBackgroundSpan(spanTextBackgroundColor, spanTextColor, textSize)
        stringBuilder.setSpan(tagSpan, 0, stringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return stringBuilder
    }
}

@Parcelize
data class DummyLocation(val id: String, val name: String): Parcelable{
     fun getSpannableStringBuilder(spanTextBackgroundColor: Int, spanTextColor: Int, textSize: Float): SpannableStringBuilder {
        val stringBuilder = SpannableStringBuilder()
        stringBuilder.append("Location").append(": ").append(name).append(" ")
        val tagSpan = RoundedBackgroundSpan(spanTextBackgroundColor, spanTextColor, textSize)
        stringBuilder.setSpan(tagSpan, 0, stringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return stringBuilder
    }
}