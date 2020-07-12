package im.vector.directory.role.model

data class Role(val id: String, val officialName: String, val secondaryName: String, val avatarUrl: String?, val roles: ArrayList<String>, val category: ArrayList<String>, val speciality: ArrayList<String>, val location: ArrayList<String>) {
    var expanded = false
    var type: Int = 1
}