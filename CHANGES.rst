
Changes in Console 0.4.0 (2015-06-19)
===================================================

Improvements:
 * Offer to resize images before sending them.
 * Add spinner view while processing the media attachments.
 * Add the “orientation” field management (image message).
 * Rotated image should fill the screen instead of being in the middle of black area.
 * Add a clear cache button in the settings page.
 * Add image & file long click management.
 * Dismiss the splash activity if there is no synchronizations to perform.	
 * PublicRoomsActivity does not exist anymore.
 * Close the homeactivity when adding a new account .
 * Leave the room page if the user leaves it from another client or he is banned.


Features:
 * Add GCM support (it can be enabled/disabled).
 * Add Google analytics support.
 * Add badges management.

Bug fixes:
 * Refresh the recents list when the members presences are refreshed.
 * Fix a weird UI effect when removing account or hiding the public rooms.
 * Nexus 7 2012 issue (kitkat) : The image mime type was not properly managed when selecting a picture.
 * The application crashed on some devices when rotating the device.
 * Disable the login button when there is a pending login request.
 * Trim the login fields.
 * Should fix SYAND-77 - Unread messages counter is not resetted.  
 * SYAND-80 : image uploading pie chart lies.
 * After a crash, the application is auto-restarted but the home page was not properly reinitialised.
 * SYAND-81 remove disconnect option -> the disconnect option is removed when the GCM is enabled.
 * SYAND-82 Room Info page UX.
 * SYAND-83 : restore the room name (only the hint part should have been updated).
 * SYAND-84 Switching between landscape and portrait should keep the state.
 * SYAND-86 : long tap on an image should offer to forward it.
 * The application disconnection did not restart the events streams at application start.


Changes in Console 0.3.0 (2015-06-02)
===================================================

 * creation : The matrix sample application is now in another git repository.

https://github.com/matrix-org/matrix-android-sdk : The matrix SDK
https://github.com/matrix-org/matrix-android-console : This application.
	

