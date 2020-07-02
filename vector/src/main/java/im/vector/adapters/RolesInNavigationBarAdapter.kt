package im.vector.adapters

import android.graphics.Color
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import im.vector.R
import im.vector.adapters.model.UserRole


class RolesInNavigationBarAdapter :
        RecyclerView.Adapter<RolesInNavigationBarAdapter.RoleViewHolder>() {
    private val roles = mutableListOf<UserRole>()

    class RoleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var mRoleName: TextView? = null
        private var mIndicator: ImageView? = null

        init {
            mRoleName = itemView.findViewById(R.id.role_name)
            mIndicator = itemView.findViewById(R.id.indicator)
        }

        fun bind(role: UserRole) {
            mRoleName?.text = role.roleName

            mIndicator?.apply {
                if (role.active)
                    setColorFilter(ContextCompat.getColor(context, android.R.color.holo_green_dark), PorterDuff.Mode.SRC_IN)
                else
                    setColorFilter(Color.LTGRAY, PorterDuff.Mode.SRC_IN)
            }

        }
    }

    fun setData(roles: MutableList<UserRole>) {
        this.roles.clear()
        this.roles.addAll(roles)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): RolesInNavigationBarAdapter.RoleViewHolder {
        // create a new view
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_roles_nav_bar, parent, false)
        // set the view's size, margins, paddings and layout parameters

        return RoleViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: RoleViewHolder, position: Int) {
        holder.bind(roles[position])
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = roles.size
}