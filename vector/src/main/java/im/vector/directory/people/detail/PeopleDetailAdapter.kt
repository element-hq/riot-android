package im.vector.directory.people.detail

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import im.vector.Matrix
import im.vector.R
import im.vector.directory.role.model.Role
import org.matrix.androidsdk.MXSession


class PeopleDetailAdapter(val context: Context) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val roles = mutableListOf<Role>()
    private val TYPE_EMAIL = 1
    private val TYPE_PHONE = 2
    private val TYPE_ROLE = 3

    var mSession: MXSession? = null

    init {
        mSession = Matrix.getInstance(context).defaultSession
    }

    inner class RoleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    }

    fun setData(roles: MutableList<Role>) {
        this.roles.clear()
        this.roles.addAll(roles)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): RecyclerView.ViewHolder {
        // create a new view
        return when (viewType) {
            else -> RoleViewHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_role_detail_category1, parent, false))
        }
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (roles[position].type) {

        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (roles[position].type) {

            else -> TYPE_ROLE
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = roles.size
}