package im.vector.directory.role.model

import android.text.SpannableStringBuilder
import android.text.Spanned
import im.vector.directory.RoundedBackgroundSpan

data class DummyRole(val id: String, val officialName: String, val secondaryName: String, val avatarUrl: String?, val roles: ArrayList<Role>, val speciality: ArrayList<Speciality>, val location: ArrayList<DummyLocation>) {
    var expanded = false
    var type: Int = 1
}

data class Role(val id: String, val name: String, val category: String) {
     public fun getSpannableStringBuilder(spanTextBackgroundColor: Int, spanTextColor: Int, textSize: Float): SpannableStringBuilder {
        val stringBuilder = SpannableStringBuilder()
        stringBuilder.append("Role").append(": ").append(name).append(", ").append("Category").append(": ").append(category).append(" ")
        val tagSpan = RoundedBackgroundSpan(spanTextBackgroundColor, spanTextColor, textSize)
        stringBuilder.setSpan(tagSpan, 0, stringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return stringBuilder
    }
}

data class Speciality(val id: String, val name: String){
     fun getSpannableStringBuilder(spanTextBackgroundColor: Int, spanTextColor: Int, textSize: Float): SpannableStringBuilder {
        val stringBuilder = SpannableStringBuilder()
        stringBuilder.append("Speciality").append(": ").append(name).append(" ")
        val tagSpan = RoundedBackgroundSpan(spanTextBackgroundColor, spanTextColor, textSize)
        stringBuilder.setSpan(tagSpan, 0, stringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return stringBuilder
    }
}

data class DummyLocation(val id: String, val name: String){
     fun getSpannableStringBuilder(spanTextBackgroundColor: Int, spanTextColor: Int, textSize: Float): SpannableStringBuilder {
        val stringBuilder = SpannableStringBuilder()
        stringBuilder.append("Location").append(": ").append(name).append(" ")
        val tagSpan = RoundedBackgroundSpan(spanTextBackgroundColor, spanTextColor, textSize)
        stringBuilder.setSpan(tagSpan, 0, stringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return stringBuilder
    }
}