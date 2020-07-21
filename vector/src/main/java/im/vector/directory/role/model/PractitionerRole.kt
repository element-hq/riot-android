package im.vector.directory.role.model

data class PractitionerRole(
        val active: Boolean,
        val code: List<Code>,
        val id: String,
        val identifier: List<Identifier>,
        val location: List<Location>,
        val meta: Meta,
        val organization: Organization,
        val period: Period,
        val resourceType: String,
        val telecom: List<Telecom>
)