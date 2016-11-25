Changes in Vector 0.6.3 (2016-11-24)
===================================================

Bugfixes:
 * Reduce the memory use to avoid oom crashes.
 * The requests did not work anymore with HTTP v2 servers
 * The application data were not properly cleared after a "clear cache"
 * The device information was not refreshed if the device was not yet known

Changes in Vector 0.6.2 (2016-11-23)
===================================================

Features:
 * Attchments encryption v2
 * libolm update
 
Improvements:
 * Add try/catch blocks to avoid application crashes when oom

Bugfixes:
 * #680 Unsupported TLS protocol version
 * #712 Improve adding member from search/invite page
 * #730 Crypto : we should be able to block the user account other devices
 * #731 Crypto : Some device informations are not displayed whereas the messages can be decrypted
 * #739 [e2e] Ringtone from call is different according to the encryption state of the room
 * #742 Unable to send messages in #megolm since build 810: Network error

Changes in Vector 0.6.1 (2016-11-21)
===================================================

Features:
 * Add the current device informations in the global settings
 
Improvements:
 * Reduce the number of lags / application not responding 

Changes in Vector 0.6.0 (2016-11-18)
===================================================
 
Features:
 * Encryption (beta feature).
 
Bugfixes:
 * GA issues
 * #503 : register users without email verification
 * #521 : Search: Unable to submit query if hardware keyboard is active  
 * #528 : The emotes are not properly displayed on notifications
 * #531 : The application badge should be updated even if the device is offline.
 * #536 : The room preview does not always display the right member info
 * #539 : Quoting a msg overrides what I already typed
 * #540 : All the store data is lost if there is an OOM error while saving it 
 * #542 : Camera permission managements in the room settings
 * #546 : Invite a left user doesn't display his displayname
 * #547 : Add public rooms pagination 
 * #549 : Quoting : displays "null" on membership events 
 * #558 : global search : the back pagination does not work anymore.
 * #560 : vector.im/{beta,staging,develop} and riot.im/{app,staging,develop} permalinks should work as well as matrix.to ones
 * #561 : URLs containing $s aren't linkified correctly 
 * #562 : Some redacted events were restored at next application launch
 * #563 : Crash after opening third party notices when the device is turned vertically then horizontaly 
 * #564 : The room search should contain the file search too.
 * #568 : Preview on invitation : the arrow to go down is displayed when device is turned 
 * #571 : Room photos don't appear in Browse Directory
 * #579 : Room photo : no placeholder for one special room in the browse directory 
 * #582 : Permalinks to users are broken 
 * #583 : We should only intercept https://matrix.to links we recognise
 * #587 : Leave room too hidden 
 * #589 : Login as email is case sensistive
 * #592 : Improve members list display 
 * #590 : Email validation token is sent even to invalid emails
 * #595 : Underscores have to be escaped with double backslash to prevent markdown parsing 
 * #601 : Viewing mubot images in fullscreen shows black screen 
 * #602 : The 1:1 room avatar must be the other member avatar if no room avatar was set 
 * #608 : Add reject / accept button on the notification when it is a room invitation notification  
 * #611 : Remove display name event is blank 
 * #612 : F-Droid develop does not display commit ID after the version string in the main menu
 * #617 : Back button in the search from a room view leads to the rooms list
 * #700 : Fix [VoIP] video buttons still active in full screen 
 * #715 : [Register flow] Register with a mail address fails
 

Changes in Vector 0.5.2 (2016-09-20)
===================================================

Bugfixes:
 * The notification icons were not displayed on some devices.

Changes in Vector 0.5.1 (2016-09-19)
===================================================

Bugfixes:
 * Restore applicationId "im.vector.alpha" as application Id.
 

Changes in Vector 0.5.0 (2016-09-19)
===================================================

Bugfixes:
 * #489 : The incoming call activity is not always displayed
 * #490 : Start a call conference and stop it asap don't stop it
 * #493 : Voip caller : the ringtone should be played in the earspeakers instead of the loud speakers 
 * #495 : add_missing_camera_permission_requests
 * #497 : The speaker is turned on when placing a Voice call
 * #501 : [VoIP] crash in caller side when a started video call is stopped asap
 * #502 : Some infinite ringing issues
 * #505 : Account creation : tapping on register button does nothing after customizing the IS
 * #506 : Registration failure : the registration is not restored in error cases
 * #518 : Fix calls headset issues 
 * #519 : During room preview, we should replace 'decline' by 'cancel'
 * #525 : can we have a larger area of action around the send button?
 * The recents were not refreshed after triggering a "read all".

Changes in Vector 0.4.1 (2016-09-13)
===================================================

Improvements:
 * #288 : Search in the Add member to a room page : contact with matrix emails should be merged
 * #438 : Add contacts access any android
 * #444 : Strip ' (IRC)' when autocompleting
 * Room creation : restore the room creation with members selection before really creating the room.
 * Login page : replace the expand button by a checkbox.
 * Improve the call avatar when receiving a call
 
Features:
 * #423 : Intercept matrix.to URLs within the app
 
Bugfixes:
 * Fix crash in caller side when the callee did not answer
 * #251 : refuse to create a new room if there is already one in progress (like the IOS client)
 * #378 : Context menu should have option to quote a message
 * #384 : Tap on avatar in Member Info page to zoom to view avatar full page 
 * #386 : Sender picture missing in notification
 * #389 / #390 : [VoIP] start call icon must be always displayed
 * #391 : Fix login/password kept after logout
 * #392 : Add "Audio focus" implementation
 * #395 : VoIP call button should disappear from composer area when you start typing
 * #396 : Displayed name should be consistent for all events.
 * #397 : Generated avatar should be consistent for all events
 * #404 : The message displayed in a room when a 3pid invited user has registered is not clear 
 * #406 : Chat screen: New message(s) notification
 * #407 : Chat screen: The read receipts from the conference user should be ignored 
 * #413 : The typing area uses the fullscreen when the user is not allowed to post
 * #415 : Room Settings: some addresses are missing
 * #417 : Room settings - Addresses: Display the context menu on tap instead of long press 
 * #418 : Vector shouldn't expose Directory when trying to scroll past the bottom of the room list
 * #431 : Call screen : speaker and mute icons should be available asap the activity is launched
 * #435 : trim leading/trailing space when setting display names 
 * #439 : add markdown support for emotes
 * #445 : Unable to join federated rooms with Android app
 * #451 : sharing a website from chrome send an invalid jpg image instead of sending the url
 * #454 : Let users join confs as voice or video 
 * #463 : Searching for a display name including a space doesn't find it 
 * #465 : Chat screen: disable auto scroll to bottom on keyboard presentation
 * #473 : Huge text messages are not rendered on some android devices 
 
 
Changes in Vector 0.4.0 (2016-08-12)
===================================================

Improvements:
 * Media upload/download UI

Features:
 * Add conference call
 * #311 : Chat screen: Add "view source" option on the selected event 
 * #314 : Support rageshake reporting via Vector (as opposed to email) 
 * #316 : Confirmation prompt before opping someone to same power level as per web
 * #347 : Display the banned users
 * #350 : Room name and memebers searches are dynamically refreshed  
 
Bugfixes:
 * #289 : Improve the camera selfie mode
 * #290 : Redacting membership events should immediately reset the displayname & avatar of room members
 * #299 : We should show a list of ignored users in user settings somewhere.
 * #302 : Impossible to scroll in User list.
 * #320 : Sanitise the logs to remove private data.
 * #323 : The room and the recents activites header are sometimes blank
 * #326 : Settings page : the switch values are sometimes updated while scrolling in the page
 * #330 : some medias are not downloadable
 * #334 : Quick replay on invitations to room
 * #343 : Incoming calls should put the application in foreground
 * #352 : some rooms are not displayed in the recents when the 10 last messages are redacted ones after performing an initial sync
 * #353 : Forwarded item is sent several times when the device is rotated
 * #358 : Update the event not found message when clicking on permalink
 * #359 : Redacting a video during sending goes wrong
 * #360 : If you try 'share to vector' from another app and share to a room, it should let you edit before sending 
 * #362 : Add option to disable the permanent notification when background sync is on.
 * #364 : Profile changes shouldn't reorder the room list 
 * #367 : Settings entries are not fully displayed.
 * Fdroid version : the synchronization was not resumed asap when a delay timer was set.
 * Some permission requirements were not properly requested.
 * Several crashes reported by Google Analytics.

Changes in Vector 0.3.4 (2016-07-18)
===================================================

Improvements:
 * #291 : Room settings: the first created alias should be defined as the main address by default.
 * Imporve the low memory management.

Bugfixes:
 * #293 : The markdown rendering is mangled for backtick blocks.
 * #294 : Messages: switch decline and preview buttons on invites enhancement.
 * #297 : Redact avatar / name update event should remove them from the room history.
 * #307 : Red FAB for room creation should fade in/out.
 * #309 : Send button is too small.
 * #310 : Room header view seems to ignore the first tap.
 * #318 : Some member avatars are wrong.
 * Fix an infinite loop when third party registration fails.
 * Always display the permalink action. (even if the hs is not matrix.org).
 * Fix some flickering settings buttons.
 * Fix several GA crashes.
 
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
	

