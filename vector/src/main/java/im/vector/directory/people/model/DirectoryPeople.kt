package im.vector.directory.people.model

data class DirectoryPeople(val id: String, val officialName: String, val jobTitle: String, val avatarUrl: String?, val organisations: ArrayList<String>, val businessUnits: ArrayList<String>)