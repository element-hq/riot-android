package im.vector.notifications

/**
 * Data class to old information about a group of notification for a room
 */
data class RoomEventGroupInfo(
        val roomId: String
) {
    var roomDisplayName: String = ""
    var roomAvatarPath: String? = null
    var hasNewEvent: Boolean = false //An event in the list has not yet been display
    var shouldBing: Boolean = false //true if at least one on the not yet displayed event is noisy
    var customSound: String? = null
    var hasSmartReplyError = false
}