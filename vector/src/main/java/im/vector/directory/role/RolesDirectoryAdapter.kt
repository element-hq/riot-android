package im.vector.directory.role

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import im.vector.Matrix
import im.vector.R
import im.vector.directory.role.model.Role
import im.vector.util.VectorUtils
import im.vector.view.VectorCircularImageView
import org.matrix.androidsdk.MXSession


class RolesDirectoryAdapter(val context: Context) :
        RecyclerView.Adapter<RolesDirectoryAdapter.RoleViewHolder>() {
    private val roles = mutableListOf<Role>()
    var mSession: MXSession? = null

    init {
        mSession = Matrix.getInstance(context).defaultSession
    }

    class RoleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var avatar: VectorCircularImageView? = null
        private var expandableIcon: ImageView? = null
        private var officialName: TextView? = null
        private var secondaryName: TextView? = null
        private var description: TextView? = null

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
            if(role.expanded){
                description?.visibility = View.VISIBLE
            }else{
                description?.visibility = View.GONE
            }
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
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = roles.size
}