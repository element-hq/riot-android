package im.vector.directory.role

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import im.vector.Matrix
import im.vector.R
import im.vector.directory.RoundedBackgroundSpan
import im.vector.directory.role.model.Role
import im.vector.ui.themes.ThemeUtils.getColor
import im.vector.util.VectorUtils
import im.vector.view.VectorCircularImageView
import org.matrix.androidsdk.MXSession


class RolesDirectoryAdapter(val context: Context, private val onClickListener: RoleClickListener) :
        RecyclerView.Adapter<RolesDirectoryAdapter.RoleViewHolder>() {
    private val roles = mutableListOf<Role>()
    var mSession: MXSession? = null
    var textSize: Float = 0.0F
    var spanTextBackgroundColor: Int
    var spanTextColor: Int

    init {
        mSession = Matrix.getInstance(context).defaultSession
        textSize = 12 * context.resources.displayMetrics.scaledDensity // sp to px
        spanTextBackgroundColor = getColor(context, R.attr.vctr_text_spanable_text_background_color)
        spanTextColor = getColor(context, R.attr.vctr_text_reverse_color)
    }

    inner class RoleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var avatar: VectorCircularImageView? = null
        var expandableIcon: ImageView? = null
        var officialName: TextView? = null
        var secondaryName: TextView? = null
        var description: TextView? = null

        init {
            avatar = itemView.findViewById(R.id.avatar)
            expandableIcon = itemView.findViewById(R.id.expandableIcon)
            officialName = itemView.findViewById(R.id.officialName)
            secondaryName = itemView.findViewById(R.id.secondaryName)
            description = itemView.findViewById(R.id.description)
        }

        fun bind(context: Context, session: MXSession?, role: Role) {
            VectorUtils.loadRoomAvatar(context, session, avatar, role)
            officialName?.text = role.officialName
            secondaryName?.text = role.secondaryName
            if (role.expanded) {
                description?.visibility = View.VISIBLE
            } else {
                description?.visibility = View.GONE
            }
            description?.text =
                    getStringArraysAsString("Role", role.roles, textSize).append(getStringArraysAsString("Category", role.category, textSize))
                            .append(getStringArraysAsString("Speciality", role.speciality, textSize)).append(getStringArraysAsString("Location", role.location, textSize))
        }

        private fun getStringArraysAsString(title: String, strings: ArrayList<String>, textSize: Float): SpannableStringBuilder {
            val stringBuilder = SpannableStringBuilder()
            var start = 0

            for (string in strings) {
                stringBuilder.append(title).append(": ").append(string).append(" ")
                var spanLength = title.plus(": ").plus(string).length
                val tagSpan = RoundedBackgroundSpan(spanTextBackgroundColor, spanTextColor, textSize)
                stringBuilder.setSpan(tagSpan, start, spanLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                start += title.plus(": ").plus(string).plus(" ").length
            }
            return stringBuilder
        }
    }

    fun setData(roles: MutableList<Role>) {
        this.roles.clear()
        this.roles.addAll(roles)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): RoleViewHolder {
        // create a new view
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_directory_role, parent, false)
        // set the view's size, margins, paddings and layout parameters

        return RoleViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: RoleViewHolder, position: Int) {
        holder.bind(context, mSession, roles[position])
        holder.expandableIcon?.setOnClickListener {
            roles[position].expanded = !roles[position].expanded
            notifyItemChanged(position)
        }
        holder.itemView.setOnClickListener {
            onClickListener.onRoleClick(roles[position])
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = roles.size
}

interface RoleClickListener {
    fun onRoleClick(role: Role)
}