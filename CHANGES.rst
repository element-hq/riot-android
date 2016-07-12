Changes in Vector 0.3.3 (2016-07-11)
===================================================

Improvements:
 * #248 : Update room members search sort.
 * #249 : Fix some lint errors.
 * The android permissions are only requested in the right fragment/activity.
 * The image compression dialog is only requested once when an images batch is sent.
 * Update gradle to 1.5.0

Features:
 * Add the room aliases management in the room settings page. 
 
Bugfixes:
 * #177 / 245 : Click on a room invitation notification should open the room preview.
 * #237 : Sending several images in one time should offer compression for each 
 * #239 : Display notifications when GCM is enabled and background synd is disabled.
 * #253 : Add copy in any room message
 * #203 / 257 : Login page buttons disabled when no network.
 * #261 : The app should not display <img> from HTML formatted_body.
 * #262 : Improve device notification settings 
 * #263 : redactions shouldn't hide auth events (eg bans) from the timeline. they should only hide the human readable bits of content.
 * #268 : Add 'leave' button to room settings.
 * #271 : Accepting an invite does not get full scrollback.
 * #272 : MD swallows leading #'s even if there are less than 3.
 * #278 : Add exclamation badge in invitation cell
 * Display leave room when displaying the account member details activity when no room is defined.
 * In some cases, the filename was not properly retrieved.
 * fix several GA crashes.
 
Changes in Vector 0.3.2 (2016-06-21)
===================================================

Improvements:
 * When GCM is not available, 
 * Display the call events in the room history.
 * Display a thick green line in permalink display mode.
 * RoomActivity : tap on the room avatar open the medias picker and update the room avatar.

Features:
 * Add android M support
 * Add a selfie mode in the medias picker.
 * The client uses two flavors (google play and F-droid).
 * The background sync can be disabled.
 * The sync timeout is configurable when GCM is not available
 * A sleep between sync can be defined when GCM is not available
 
Bugfixes:
 * Fix issue #206 : There is no space between some avatars (unexpected avatar)
 * Fix issue #197 : Room members : the Pen menu icon should be hidden if the user is alone in the room or is not administrator 
 * Fix issue #212 : Sharing from some apps to Vector not working
 * Fix issue #196 : Room members in edition mode : the Add button should be hidden
 * Fix issue #214 : the Pen menu icon should be hidden if the user is alone in the room or is not administrator
 * Fix issue #215 : Improve medias management
 * Fix issue #216 : Fix add button room details
 * Fix issue #192 : "Notification targets" (global settings) entry should not be displayed if it is empty
 * Fix issue #209 : The avatar of invited users are not displayed in the details member activity if he did not joined any other room
 * Fix issue #186 : Start chat with a member should use the latest room instead of the first found one
 * Fix issue #167 : Heavy battery drain.
 * Fix issue #172 : Messages: Add Directory section at the top on scroll down.
 * Fix issue #231 : /invite support, and any other missing slash commands.
 * The device used to ring forever when a call was received when the device was locked and answered from another client.
 * Fix several GA issues
 
Changes in Vector 0.3.1 (2016-06-07)
===================================================

Bugfixes:
 * issue #156 Option to autocomplete nicknames from their member info page 
 * issue #195 Joining a room by alias fails
 * The inviter avatar was the invited one.
 * issue #188 Universal link failed if App removed from task stack
 * issue #187 ZE550kl / integrated camera application : taking a photo with the front camera does nothing
 * issue #184 the user account informations are sometimes corrupted 
 * issue #185 Add member : should not offer to join by matrix id if the user already in the members list 
 * Shared files from external applications : the rooms list was empty when the application was not launched.
 * issue #191 The push rules on the webclient don't match to the android ones 
 * issue #179 Avoid "unknown" presence	
 * issue #180 Some invited emails are stuck (invitation from a non matrix user)
 * Clear the notications wwhen the client is logged out	
 * issue #194 Public room preview : some public rooms have no display name

Changes in Vector 0.3.0 (2016-06-03)
===================================================

Improvements:
 * The clients used to restart when debackgrounding.
 * Add unread counters in the home activity
 * Add more account information in the settings page.
 * Display the pushers list in the settings page.
 * Room header (moved up, content...)
 * Display the "directory" group when the recents are empty to avoid having an empty screen

Features:
 * Add ignore members feature
 * Add room preview before joining a room.
 * Share a media from an external application. 

Bugfixes:
 * Fix several crashes reported by GA.
 * Fix issue #125 : If you specify a custom homeserver, the app should remember what it is
 * Fix issue #134 : Messages: missed notifs and unread msgs in the room list
 * Fix issue A photo taken in landscape is sent in portrait when the device orientation is locked in portrait
 * Fix issue #93 : The image quality dialog is lost after rotating the device
 * Fix issue #140 : read receipts list : the avatars are sometimes wrong
 * Fix issue #153 : Room screen: display edit menu on long press on message
 * Fix issue #132 : make the link clickable in the room topic
 * Fix issue #154 : Is it possible to define tintColor on scroll view?
 * Fix issue #101 : The 3PID presences are not supported
 * Fix issue #144 : Image scaling algorithm choice could use some work
 * Fix issue #130 : Make incoming calls work https://vector.im/develop/#/room/!cURbafjkfsMDVwdRDQ:matrix.org/$146333991475ZJgGm:matrix.freelock.com
 * Some notifications were stuck.
 * The member presences were not refreshed in real time.
 * Fix issue #171 : Remove the 'optional' in the email registration field
 * The room avatar and displayed were not always refreshed when updating with the client. 

Changes in Vector 0.2.0 (2016-04-14)
===================================================

 * First official release.
	

