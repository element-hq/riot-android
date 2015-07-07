
Changes in Console 0.4.3 (2015-07-07)
===================================================

Improvements:
 * Add the presence information in the room and in the room member description activities.


Bug fixes:
 * Update to 0.4.2 used to display an empty history list. It was required to clear the application cache.


Changes in Console 0.4.2 (2015-07-06)
===================================================

Improvements:
 * Improve the multi-servers accounts management.
 * Account thumbnail : use the gallery thumbnail when available.
 * Display the server error messages when available.
 * Do not save anymore the image into the gallery. The user has to use the “save” action.
 * Use the gallery filename for the "filename" post param

Features:
 * Add the notification settings page.
 * Add images slider when tapping on an image.

Bug fixes:
 * SYAND-91 : server is not federating - endless load of public room list.
 * Fix a crash on contact sort. In some cases, the contactId was null.
 * The pagination could have been broken after restarting the application. The user has to clear the application cached (settings page).
 * Image capture crashed on nexus 6 (android 5.1.1).
 * Crash when listing the room members whereas the list was updated.
 * Crash while leaving the room member page.

Changes in Console 0.4.1 (2015-06-30)
===================================================
Improvements:
 * Removed the SEND_SMS permission as it isn't necessary and it adds permissions that cause problems (See issue 24)

Bug fixes:
 * Fixed a problem with emotes not being displayed properly on the recents screen
 * Fixed a problem with a fragment not being restarted properly

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
	

