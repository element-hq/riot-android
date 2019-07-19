Changes in Riot 0.9.3 (2019-XX-XX)
===================================================

MatrixSdk:
 - Upgrade MatrixSdk to version 0.X.Y.
 - Changelog: https://github.com/matrix-org/matrix-android-sdk/releases/tag/v0.X.Y

Features:
 -

Improvements:
 -

Other changes:
 -

Bugfix:
 -

Translations:
 -

Build:
 - Include native libraries for 64 bits processors.


Changes in Riot 0.9.2 (2019-07-18)
===================================================

MatrixSdk:
 - Upgrade MatrixSdk to version 0.9.24.
 - Changelog: https://github.com/matrix-org/matrix-android-sdk/releases/tag/v0.9.24

Improvements:
 - Room upgrade: Use the `server_name` parameter when joining the new room (#3204)

Other changes:
 - Piwik SDK has been replaced by Matomo SDK (#3163)

Bugfix:
 - Fix / Illegal States exceptions when starting event stream service X
 - Fix / Keys Backup can be setup twice #9510
 - Fix / Infinite logout screen when token invalidated
 - Fix / Export keys not possible when no network (airplane)
 - Fix / crash in logout success
 - Fix / Crash when session store is null in event stream #3158

Build:
 - Upgrade gradle version from 4.10.1 to 5.4.1
 - Ensure MatrixSDK library is downloaded from the jitpack repository

Changes in Riot 0.9.1 (2019-05-03)
===================================================

MatrixSdk:
 - Upgrade MatrixSdk to version 0.9.23.
 - Changelog: https://github.com/matrix-org/matrix-android-sdk/releases/tag/v0.9.23

Features:
 - E2E: SAS Verification

Improvements:
 - Use heads-up alert UX for key-share and key-verification requests

Other changes:
 - Olm lib is now only a dependency of Matrix Sdk
 - Matrix SDK library is now built and hosted by Jitpack (https://jitpack.io/#matrix-org/matrix-android-sdk/) (matrix-org/matrix-android-sdk#241)

Bugfix:
 - Fix mistake in Arabic translation (#3129)

Changes in Riot 0.9.00 (2019-04-23)
===================================================

/!\ This version is the first version published with app id "im.vector.app".

Changes in Riot 0.8.99 (2019-04-23)
===================================================

/!\ This version is the last version published with app id "im.vector.alpha". It contains a screen which introduce the new application "im.vector.app"
/!\ This release contains security related bugfixes, users should upgrade asap

MatrixSdk:
 - Upgrade MatrixSdk to version 0.9.22.
 - Changelog: https://github.com/matrix-org/matrix-android-sdk/releases/tag/v0.9.22

Other changes:
 - Remove Amplitude tracker and Calendars permissions added by Jitsi lib (jitsi/jitsi-meet#4068, jitsi/jitsi-meet#4080)
 - Exclude code of Firebase analytics (#2481)

Bugfix:
 - Fix / Illegal States exceptions when starting event stream service X
 - Security Fix / Remove obsolete and buggy ContentProvider which could allow a malicious local app to compromise account data. Many thanks to Julien Thomas (twitter.com/@julien_thomas) from Protektoid Project (https://protektoid.com) for identifying this and responsibly disclosing it!

Build:
 - Exclude Firebase analytics code (#2481)


Changes in Riot 0.8.29 (2019-04-04)
===================================================

MatrixSdk:
 - Upgrade MatrixSdk to version 0.9.20.
 - Changelog: https://github.com/matrix-org/matrix-android-sdk/releases/tag/v0.9.20

Improvements:
 - Fix crash on Jitsi conference by upgrading the lib to version 1.21.0 (#2412)
 - Finally upgrade Jitsi lib to version 2.0.0 (https://github.com/jitsi/jitsi-meet/releases/tag/android-sdk-2.0.0)

Changes in Riot 0.8.28 (2019-04-01)
===================================================

Bugfix:
 - Ensure EventStreamServiceX call startForeground(), even if there is no session, and do not simulate push in this case

Changes in Riot 0.8.27 (2019-04-01)
===================================================

Improvements:
 - Deprecate EventStreamService, replaced by EventStreamServiceX and CallService (#2782, #3065)

Other changes:
 - Scalar URL: Use prod urls in Riot mobile apps (#3077)

Changes in Riot 0.8.26 (2019-03-25)
===================================================

MatrixSdk:
 - Upgrade MatrixSdk to version 0.9.19.
 - Changelog: https://github.com/matrix-org/matrix-android-sdk/releases/tag/v0.9.19

Features:
 - Notification rework: inline reply/mark as read actions, one notification per room (#3068 and others)

Other changes:
 - Disable usage of library ShortcutBadger on device running API 26+

Bugfix:
 - Fix expand and collapse color (#3035)
 - Fix LED not flashing on noisy messages

Changes in Riot 0.8.25 (2019-03-13)
===================================================

Improvements:
 - Add option to choose default media source (#2763)
 - Add option to choose default photo compression (#2763)
 - Add option to disable camera shutter sound
 - Auto-refresh scalar token when a 403 error is detected (#3051)
 - Open each links with the browser in a new Tab (#381)

Translations:
 - New partial translations in Bengali-India

Changes in Riot 0.8.24 (2019-03-07)
===================================================

MatrixSdk:
 - Upgrade MatrixSdk to version 0.9.18.
 - Changelog: https://github.com/matrix-org/matrix-android-sdk/releases/tag/v0.9.18

Features:
 - Implement server config discovery - .well-known support (#2982)
 - Implement login with SSO (#3025)

Improvements:
 - Improve UX when restoring e2e keys (#2999)
 - Add option to send messages with enter button (#1070)
 - MediaViewer: display image in high quality and improve max zoom for big file (#2967)
 - Hide e2e keys management section in settings if crypto is disabled
 - Display message with formatted_body but with empty body (#2989)
 - Get full Credentials data from Fallback login (#3006)

Other changes:
 - Change color of links (#2987)
 - Change color of HomeSection badge (#2987)

Bugfix:
 - Fix crash in settings when cryptography is disabled (#2991)
 - Fix Claims of display names being linkified (#2975)
 - Fix Riot breaks links if message contains numbers (#2891)
 - Fix geo: URIs are treated as phone numbers (#2464)
 - Fix Some text in messages are converted to maps link and should not (#2350)
 - Fix Numbers are too much linkified (#1140)
 - Fix Highlight geo: URIs (#1329)
 - Fix Odd linkification bug with trailing slash (#865)
 - Fix issue on joining conference call wording in some languages (#2112)

Changes in Riot 0.8.23 (2019-02-21)
===================================================

MatrixSdk:
 - Upgrade MatrixSdk to version 0.9.17.

Features:
 - key backup: Trust on Decrypt (#2921)
 - key backup: new recover method detected (#2926)

Improvements:
 - keys backup: Setup screen UX improvement
 - keys backup: Sign Out flow improvement
 - Improved button styles (states, ripple effect)
 - Show direct chat section in user details only for other users, not self
 - Sender name colors in rooms

Other changes:
 - Remove beta e2e warning (#2946)

Bugfix:
 - Fix warning "Attribute value must be constant" in VectorHomeActivity
 - Fix key backup banner doesn't go away after you have restored from backup. (#2943)
 - Fix issue with registration on some HomeServer (#2985)

Changes in Riot 0.8.22-beta (2019-02-01)
===================================================

MatrixSdk:
 - Upgrade MatrixSdk to version 0.9.16.

Features:
 - keys backup: Implement setup screen (#2883)
 - keys backup: Display a warning on new sign out screen (#2885)
 - keys backup: recover screen (#2887)
 - keys backup: Add a dedicated section to settings (#2884)
 - keys backup: Add a banner on Home to setup or recover backup (#2884)

Improvements:
 - Make Change Password Settings More User friendly (#2898)
 - Support Split-screen mode (#1832)
 - Enable auto focus when taking picture with the camera (#2831)
 - Better wording in notification for video call (#1421)
 - Improve widget banner (#2129)
 - Icon for Oreo (#2169)
 - Notification reliability and Messaging Style, with inlined reply (#2823, #1016).
 - Notification settings re-organization, added bing rule troubleshoot
 - Kotlin Code Improvement in VectorSettingsPreferencesFragment.kt
 - Remove redundant !! , Replace it with null safe operators in VectorSettingsPreferencesFragment.kt
 - `Redact` has been renamed to `Remove` to match riot/web (#2871)
 - Remove long click download action in MediaViewer (#2882)

Other changes:
 - Update of Light and Dark themes (#2710)
 - Restore the crash report dialog after a crash
 - New application icon! (#2905)

Bugfix:
 - Fix No Visual Difference is setting if disabled (#2929)
 - Fix crash when taking picture for user avatar on old device (#2818)
 - Fix crash when adding background to image (#2828)
 - LED notifications are not working (#2512)
 - FCM Troubleshoot screen crash in some cases (#2846)
 - Fix login button issue (#1568)
 - Fix issue with registration when an email is provided (#2852)
 - Fix issues with Tombstone events (#2866 && #2867)
 - Fix crash on BugReportActivity if previous Activity is destroyed (#2876)
 - Key share request does not go away when user select "verify" (#2781)
 - Fix crash when entering the settings due to missing push rules (#2893)

Changes in Riot 0.8.21 (2019-01-02)
===================================================

MatrixSdk:
 - Upgrade MatrixSdk to version 0.9.15.

Improvements:
 - Show userId below display name in member detail screen (#2756)
 - Clicking on a user and a room avatar opens a new screen with animation to view the avatar in full screen, with zoom capabilities (#2455)
 - Added Troubleshoot Notification settings page
 - Add badge to indicate number of group invitations on the Home Screen (#1923)

Other changes:
 - Update README.md and CONTRIBUTING.md (#2795)

Bugfix:
 - Correct issue during signup when a 3PID error would let the signup flow spin forever
 - Defensive code for notifications issues + check play services as per FCM recommendation (#2266)
 - No notification on f-droid when device enters sleep mode (#2789)
 - Added ShortcutBadger missing permissions for some devices
 - Fix many little UI/UX issues (#2769)
 - Fix crash opening the setting screen (#2793)
 - Allow popup on IntegrationManagerActivity's WebView because it's require to add Slack integration (#2768)
 - Fix crash on Android ViewPager (#2786)
 - Fix avatar icon characters being a little bit offset to right.
 - Fix Stopping Loading View after Upload of User Avatar (#2801)
 - Fix no display of image without `info` (#2666)
 - Fix permission request failure. It was actually not necessary to request overlay permission (#2680)

Changes in Riot 0.8.20 (2018-12-13)
===================================================

MatrixSdk:
 - Upgrade to version 0.9.14.

Improvements:
 - Remove double negations from settings and update descriptions (#2723)
 - Handle missing or bad parameter in slash command
 - Support specifying kick and ban message (#2164)
 - Add image transparency and fix issues with gifs in the media viewer (#2731)
 - Upgrade olm-sdk.aar from version 2.3.0 to version 3.0.0
 - Migration to the Preference v7 support
 - Make User Agreement part of the registration flow (#2442)
 - Fix several color issue on Status theme and prepare rework some styles.

Bugfix:
 - Use same "Call Anyway" string from iOS (#2695)
 - Improve `/markdown` command (#2673)
 - Display thumbnail for encrypted files without a remote thumbnail (#2734)

Changes in Riot 0.8.19 (2018-11-06)
===================================================

MatrixSdk:
 - Upgrade to version 0.9.13.

Features:
 - Enable Lazy Loading by default, if the hs supports it
 - Add RTL support (#2376, #2271)

Improvements:
 - improve UI for VectorMediaPickerActivity and InviteMembersActivity (#2610)
 - Ability to crop profile picture before setting (#2598)
 - Add a setting of the room's info area visibility.

Other changes:
 - F-Droid version: restart event stream on application upgrade (#2105)
 - Locales management has been moved to a dedicated file

Bugfix:
 - Status.im backgrounds, header, buttons, and missing items (#2672)
 - Fix Permalinks and registration issue (#2689)
 - Mention from read receipts list doesn't work (#656)
 - Fix issue when scrolling file list in room details (#2702)
 - Align switch camera button to parent in landscape mode (#2704)

Build:
 - Better build.gradle file (#2302)

Changes in Riot 0.8.18 (2018-10-18)
===================================================

MatrixSdk:
 - Upgrade to version 0.9.12.

Features:
 - Status.im theme

Improvements:
 - Use LocalBroadcastManager when applicable (#2595)
 - Menu version copies version number to clipboard (#2570)
 - Tapping on profile picture in sidebar opens settings page (#2597)
 - Ask for Camera permission only when the user want to change the room avatar (#2575)

Other changes:
 - Room display name is now computed by the Matrix SDK

Bugfix:
 - When exporting E2E keys, it isn't clear that you are creating a new password (#2626)
 - Can't change room directory server (#2611)
 - Reply get's lost when moving app in background and back (#2581)
 - Android 8: crash on device Boot (#2615)
 - Avoid creation of Gson object (#2608)
 - Inline code breaks in reply messages (#2531)
 - Reduce size of clickable read-receipts area (#655)
 - Fix issue of html rendering in emote message (#2652)

Translations:
 - Fix issue with indonesian translations. This language is now available.

Changes in Riot 0.8.17 (2018-10-10)
===================================================

MatrixSdk:
 - Upgrade to version 0.9.11.

Bugfix:
 - Fix issue on loading cache, and so avoid initial sync on each application startup.

Changes in Riot 0.8.16 (2018-10-08)
===================================================

MatrixSdk:
 - Upgrade to version 0.9.10.

Features:
 - Manage blue banner case of server quota notices (#2547)

Improvements:
 - Minor changes to toolbar style and other UI elements (#2529)
 - Improvements to dialogs, video messages, and the previewer activity (#2583)
 - Add a way to enable local file encryption on the SDK (disabled by default)

Other changes:
 - Sonar analysis has been configured (#2203)

Bugfix:
 - Fix crash when opening file with external application (#2573)
 - Fix issue on settings: unable to rename current device if it has no name (#2174)
 - Allow anyone to add local alias and to try to delete local alias (#1033)
 - Fix issue on "Resend all" action (#2569)
 - Fix messages vanishing when resending them (#2508)
 - Remove delay for / completion (#2576)

Changes in Riot 0.8.15 (2018-08-30)
===================================================

MatrixSdk:
 - Upgrade to version 0.9.9.

Improvements:
 - Improve intent to open document (#2544)
 - Avoid useless dialog for permission (#2331)
 - Improve wording when exporting keys (#2289)

Other changes:
 - Upgrade lib libphonenumber from v8.0.1 to 8.9.12
 - Upgrade Google firebase libs

Bugfix:
 - Handle `\/` at the beginning of a message to send a message starting with `/` (#658)
 - Escape nicknames starting with a forward slash `/` in mentions (#2146)
 - Improve management of Push feature
 - MatrixError mResourceLimitExceededError is now managed in MxDataHandler (vector-im/riot-android#2547 point 2)

Changes in Riot 0.8.14 (2018-08-27)
===================================================

MatrixSdk:
 - Upgrade to version 0.9.8.

Features:
 - Manage server quota notices (#2440)

Improvements:
 - Do not ask permission to write external storage at startup (#2483)
 - Update settings icon and transparent logo for notifications and navigation drawer (#2492)
 - URL previews are no longer requested from the server when displaying URL previews is disabled (PR #2514)
 - Fix some plural and puzzle strings, and remove other unused ones (#2444)
 - Manage System Alerts in a dedicated section

Other changes:
 - Upgrade olm-sdk.aar from version 2.2.2 to version 2.3.0
 - move PieFractionView from the SDK to the client (#2525)

Bugfix:
 - Fix media sharing (#2530)
 - Fix notification sound issue in settings (#2524)
 - Disable app icon badge for "listen for event" notification (#2104)

Changes in Riot 0.8.13 (2018-08-09)
===================================================

Features:
 - Resurrect performance metrics (#2391)
 - Telemetry to report incidence of UISIs (#2330)
 - Add a previewer for previewing media before sending it into the room (#1742|#2445)
 - Implements ReplyTo feature (#2390)
 - Add auto completion for slash commands (#2384)
 - Support Room Versioning (#2441)

Improvements:
 - Update matrix-sdk.aar lib (v0.9.7).
 - Piwik: Update the way how stats are reported (#2402)
 - Improve BugReport screen: display a preview of the screenshot (#2318)
 - In the settings, move theme settings just below "language" (#2439)
 - Improve the display of the sources of the message in the dialog (#2348)
 - Improve the display of the buttons and the reason in the room preview (#2352)
 - In the flair section on settings, notify the user when he has no flair (#2430)
 - Improve GDPR consent webview management (#2491)
 - Support external keyboard to send messages for recent devices (#220, #1279)
 - When user ignores or un-ignores someone, notify that the app will restart (#2437)

Other changes:
 - Remove dependency to `android-gif-drawable` lib and use Glide to animate logo on Splashscreen (#2421)
 - Keep only Room.getState() method and remove Room.getLiveState() because they are similar (matrix-org/matrix-android-sdk#310)

Bugfix:
 - Fix issue on incoming call screen when "Do not disturb mode" is active (#2417)
 - Fix issue when selecting sound for notifications in the settings
 - Fix issue when changing device name in the settings (#2416)
 - Fix issue on verifying device, update the wording of the description message (#1067)
 - Messages with code blocks show other HTML as plain text (#2280)
 - Message with <p> was sometimes not properly formatted (#2275)
 - Fix notification issue when Riot is not started (#2451)
 - Fix Unable to add Matrix apps (#2466)
 - Riot auto joined a public room (#2472)
 - Remove last traces of Firebase analytics (#2481)
 - code blocks are escaped and therefore hard readable (#2484)
 - Restore the navigation of the back button in the public rooms preview header (#2473)
 - Fix issue on preference screen: device lists was not displayed (#2409)
 - Ensure notification has a title (#2242)

Changes in Riot 0.8.12 (2018-07-06)
===================================================

Bugfix:
 - Fix issue on vanished favorite and low priority room (#2413)

Changes in Riot 0.8.11 (2018-07-03)
===================================================

Features:
 - Re-request keys manually for encrypted events (#2319)
 - Add option to send voice message to a room, using a third application to record message.
   To enable in the Labs settings (PR #1762)

Improvements:
 - Update matrix-sdk.aar lib (v0.9.6).
 - New Floating Action Menu in Home screen (PR #2335)
 - Add spacing to device keys (#2314)
 - use apply() instead of commit() to save shared prefs (#2231)
 - Do not ring if "Do Not Disturb" is active (#1072)
 - Manage the "consent not given" error when declining a room invite

Other changes:
 - Remove "Matrix application" activation from the Lab section in the settings (#2341)

Bugfix:
 - Remove black borders on 18:9 phone (#2063)
 - Auto dismiss the join/reject room notification when user select an action (#2354)
 - Fix some crashes reported by the PlayStore (#2380, #2382, #2383, #2395)
 - Fix issues in UrlPreviews (#2312)

Translations:
 - Galician thanks to Miguel Branco

Build:
 - Add script to check code quality
 - Travis will now check if CHANGES.rst has been modified for each PR

Changes in Riot 0.8.10 (2018-01-06)
===================================================

Improvements:
 * Update matrix-sdk.aar lib (v0.9.5).
 * GDPR compliance:
    * Account deactivation is now managed natively in a dedicated screen

Features:
 * Send stickers to a Room

Bug Fix:
 * Gif do not play anymore (#2168)

Changes in Riot 0.8.9 (2018-05-25)
===================================================

Improvements:
 * Update matrix-sdk.aar lib (v0.9.4).
 * GDPR compliance:
    * Manage M_CONSENT_NOT_GIVEN matrix error
    * Sending analytics is now opt-in
    * Possibility to deactivate account (redirected to the web client for the moment)
 * Reply to feature: display only

Bug Fix:
 * Background sync cannot be enabled on F-Droid Riot app (#2196)

Build:
 * Kotlin is enabled on the project
 * Travis CI has been enabled to build PRs

Note:
 * Sending stickers is not enabled yet

Changes in Riot 0.8.8 (2018-05-13)
===================================================

Bug Fix:
 * Background sync cannot be enabled on F-Droid Riot app (#2196)

Changes in Riot 0.8.7 (2018-04-25)
===================================================

Improvements:
 * Disable sending analytics by default on the F-Droid version

Bug Fix:
 * Fix issue on Sticker rendering (#2175)
 * Fix infinite loader issue (#2178)

Changes in Riot 0.8.6 (2018-04-20)
===================================================

Features:
 * Render stickers in the timeline (#2097).

Improvements:
 * Update matrix-sdk.aar lib (v0.9.3).
 * Notifications: make them user friendly again (#2130).
 * Add Notification privacy screen (PR #2152).
 * Hide "Show devices list" for local contacts who are not matrix users (#2153).
 * Login Activity: Code cleaning.

Bug Fix:
 * Tapping on a room pill should not automatically join it (#2098).
 * Notifications: Make the notification for messages no more sticky (PR #2148).

Build:
 * Update to SDK 27.

Changes in Riot 0.8.5 (2018-03-31)
===================================================

Improvements:
 * Update matrix-sdk.aar lib (v0.9.2).
 * Make state event redaction handling gentler with homeserver (#2117).

Changes in Riot 0.8.3 (2018-03-16)
===================================================

Improvements:
 * Login screen : open keyboard form email.
 * Matrix Apps: Enable them by default (#2022).

Bug Fix:
 * User Settings: background sync setting stays disabled (#2075).
 * Room: Events with unexpected timestamps get stuck at the bottom of the history (#2081).

Changes in Riot 0.8.2 (2018-03-14)
===================================================

Improvements:
 * Update matrix-sdk.aar lib (v0.9.1).
 * User Settings: Add a setting to Re-enable rageshake (#1971).
 * User Settings: Add a setting "Keep detailed notifications" in Google Play build (#2051).
 * Docs: Create a doc for notifications to answer to #2044.
 * Room prewiew: Make room aliases in topic clickable (#1985).
 * Code: Tidy codebase, thanks to @kaiyou (PR #1784).
 * Label bunches of actionable room items for screen readers, thanks to @ndarilek  (PR #1976).

Bug Fix:
 * Notifications: Complaints that the "Synchronizing" notification appears too often (#2012).
 * Notifications Privacy: Riot should never pass events content to GCM (#2051).
 * File uploads with file name containing a path (matrix-org/matrix-android-sdk#228), thanks to @christarazi (PR #2019).
 * Fix some plural messages (#1922), thanks to @SafaAlfulaij (PR #1934).

Translations:
  * Bulgarian, added thanks to @rbozhkova.

Changes in Riot 0.8.1 (2018-02-15)
===================================================

Improvements:
 * Update matrix-sdk.aar lib (v0.9.0).

Bug Fix:
 * URL Preview: We should have it for m.notice too (PR 1975).

Changes in Riot 0.8.00-beta (2018-02-02)
===================================================

Features:

  * Add a new tab to list the user's communities (vector-im/riot-meta/#114).
  * Add new screens to display the community details, edition is not supported yet (vector-im/riot-meta/#115, vector-im/riot-meta/#116, vector-im/riot-meta/#117).
  * Room Settings: handle the related communities in order to show flair for them.
  * User Settings: Let the user enable his community flair in rooms configured to show it.
  * Add the url preview feature (PR #1929).

Improvements:

  * Support the 4 states for the room notification level (all messages (noisy), all messages, mention only, mute).
  * Add the avatar to the pills displayed in room history (PR #1917).
  * Set the push server URLs as a resource string (PR #1908).
  * Improve duplicate events detection (#1907).
  * Vibrate when long pressing on an user name / avatar to copy his/her name in the edit text.
  * Improve the notifications management.

Bugfixes:

  * #1903: Weird room layout.
  * #1896: Copy source code of a message.
  * #1821, #1850: Improve the text sharing.
  * #1920: Phone vibrates when mentioning someone.

Changes in Riot 0.7.09 (2018-01-16)
===================================================

Improvements:

  * Update to the latest JITSI libs
  * Add some scripts to build the required libs.

Bugfixes:

  * #1859 : After a user redacted their own join event from HQ, Android DoSes us with /context requests.

Changes in Riot 0.7.08 (2018-01-12)
===================================================

Bugfixes:

 * Fix the account creation

Changes in Riot 0.7.07 (2018-01-03)
===================================================

Bugfixes:

 * Improve piwik management.
 * fix #1802 : Expected status header not present (until we update OkHttp to 3.X)
 * fix widget management

Changes in Riot 0.7.06 (2017-12-06)
===================================================

Features:

 * Update the global notification rules UI to have tree states (off, on, noisy) instead of a toogle (on, off).

Improvements:

 * Move the bug report dialog to an activity.
 * Remove Google Analytics.

Bugfixes:

 * Fix many issues reported by GA.
 * Improve the notification management on android 8 devices when the application is in battery optimisation mode.
 * Fix some invalid avatars while using the autocompletion text.

Changes in Riot 0.7.05 (2017-11-28)
===================================================

Features:

 * Add a settings to use the native camera application instead of the in-app one.
 * Add piwik.
 * Display pills(without avatar) on room history.

Improvements:

 * Improve the notfications on android 8 devices.

Bugfixes:

 * Fix many issues reported by GA.
 * Fix the notification sound management on Android 8 devices.
 * #1700 : Jump to first unread message didn't jump anywhere, just stayed at the same position where it was before, although there are more unread messages
 * #1772 : unrecognised / commands shouldn't be relayed to the room.


Changes in Riot 0.7.04 (2017-11-15)
===================================================

Features:

 * Add the e2e share keys.

Improvements:

 * Add external keyboard functionality (to send messages).
 * Refactor the call UI : the incoming call screen is removed.
 * Refactor the call management (and fix the audio path issues).
 * Update the android tools to the latest ones.
 * Add a dummy splash screen when a logout is in progress

Bugfixes:

 * Fix many issues reported by GA.
 * Fix a battery draining issue after ending a video call.
 * #119 : Notifications: implement @room notifications on mobile
 * #208 : Attached image: `thumbnail_info` and `thumbnail_url` must be moved in `content.info` dictionary
 * #1296 : Application crashes while swiping medias
 * #1684 : Camera viewfinder rotation is broken (regression).
 * #1685 : app sends notifications even when i told it not to.
 * #1715 : Eats battery after video call
 * #1725 : app crashes while triggering a notification.

Changes in Riot 0.7.03 (2017-10-05)
===================================================

Improvements:
 * Reduce the initial sync times
 * Manage voice Jitsi call

Bugfixes:
 * #1641 : Language selector should be localized
 * #1643 : Put Riot service in the foreground until the initial sync is done
 * #1644 : Pin rooms with missed notifs and unread msg by default on the home page

Changes in Riot 0.7.02 (2017-10-03)
===================================================

Features:
 * Add black theme.
 * Add widgets management.
 * Update the third party call lib.
 * Add notification ringtone selection.

Bugfixes:
 * Fix many issues reported by Google analytics.
 * #1574 : Rotating the device when uploading photos still has a small bug
 * #1579 : Unexpected behaviour while clicking in the settings entry (android 8)
 * #1588 : i can not set profile picture when i click on profile picture it return to setting menu (android 8)
 * #1592 : Client unable to connect on server after certificate update
 * #1613 : Phone rings for ever
 * #1616 : Sometimes Riot notifications reappear after being dismissed without being read
 * #1622 : picked up call but continued vibrating, connection couldn't be established
 * #1623 : checkboxes are not properly managed in the settings screen (android 8)
 * #1634 : sent message duplicated in ui including read receipts

Changes in Riot 0.7.01 (2017-09-04)
===================================================

Features:
 * Add dark theme.
 * Add the 12/24 hours settings.

Improvements:
 * [Fdroid] Improve the sync when the application is backgrounded.
 * Update the call notification priority to be displayed on the lock screen.
 * Use the default incoming ring tone if the storage permission was not granted.

Bugfixes:
 * Fix many issues reported by Google analytics.
 * Fix e2e export silent failure when the storage permission was not granted.
 * Fix crashes when too many asynctasks were launched.
 * Fix the notification sounds.
 * Restore the video call video when the application is put in background and in foreground.
 * Fix the audio call resuming
 * Fix the broken incoming video call
 * #1467 : Rotating the device while an image is uploading inserts the image twice.
 * #1475 : messages composed with only one number are displayed as if they were emojis
 * #1503 : Do not enlarge non-emoji.
 * #1510 : Rotating the device while the camera activity is running closes it
 * #1514 : 'Enable background sync' is viewable on fdroid build preference does not have an effect
 * #1532 : [custom hs] high battery draining issue
 * #1537 : cannot update the profile image
 * #1548 : Unable to decrypt: encryption not enabled
 * #1554 : Turn screen on for 3 seconds not working

Changes in Riot 0.7.00 (2017-08-01)
===================================================

Features:
 * Add member events merge.
 * Add new UI settings (hide/show some UI items, change the text size).
 * Add a beta data save mode.
 * Add a medias timelife i.e the medias are kept in storage for a specfied period.
 * Add new user search.

Improvements:
 * Add more languages.
 * Reduce the storage use.

Bugfixes:
 * Fix many crashes reported by rageshake or GA.
 * #1455 : Click on a matrix id does not open the member details activity if it is not a known user.

Changes in Riot 0.6.14 (2017-07-25)
===================================================

Bugfixes:
 * Remove server catchup patch (i.e the sync requests were triggered until getting something). It used to drain battery on small accounts.
 * Fix application resume edge cases (fdroid only)

Changes in Riot 0.6.13 (2017-07-03)
===================================================

Features:
 * Add new home UI
 * Add the read markers management

Bugfixes:
 * Fix many issues reported by GA.
 * #1308 : E2E new devices dialog disappears if screen is turned off by timeout : it does not reappear at next sent event.
 * #1330 : Using the name completion as the first item of the message should add a colon (:)
 * #1331 : The Events service is not properly restarted in some race conditions
 * #1340 : sync is stuck after the application has been killed in background

Changes in Riot 0.6.12 (2017-06-12)
=======================================================

Bugfixes:
 * #1302 : No room / few rooms are displayed an application update / first launch.

Changes in Riot 0.6.11 (2017-06-08)
===================================================

Bugfixes:
 * #1291 : don't receive anymore notifications after updating to the 0.6.10 version
 * #1292 : No more room after updating the application on 0.6.10 and killing it during the loading Unregisteer the GCM token before registrating the FCM one.

Changes in Riot 0.6.10 (2017-05-30)
===================================================

Features:
 * Add some lanagues supports
 * Add auto-complete text editor.
 * Use FCM instead of GCM.

Improvements:
 * Add a new notification design.
 * Offer to send a bug report when the application crashes.
 * Use the new bug report API.

Bugfixes:

 * Fix many issues reported by GA.
 * #1041 : matrix.to links are broken.
 * #1052 : People tab in room details: 'you' displayed instead of your displayname/matrix id.
 * #1053 : 'I have verified my mail' button is missing
 * #1077 : Highlight phone numbers, email addresses, etc.
 * #1093 : Cannot decrypt attachments on Android 4.2.X
 * #1118 : show syncing throbber in room view
 * #1186 : Infinite back pagination whereas the app is in background
 * Fix some cryptography issues.

Changes in Riot 0.6.9 (2017-03-15)
===================================================

Features:
 * Add MSISDN support for authentication, registration and member search.
 * Add encryption keys import / export.
 * Add unknown devices management.

Improvements:
 * Improve bug report management.
 * Reduce application loading time.
 * Add application / SDK version in the user agent
 * Add audio attachments support

Bugfixes:
 * Fix many encryption issues.
 * Fix several issues reported by GA.
 * #814 : Sending or sharing .txt files fails silently.
 * #908 : Don't close the contactPicker after selecting a member.
 * #909 : Spelling/grammar: «Show Devices List» should be: «Show Device List.
 * #913 : Mirrored thumbnails when sending pictures taken with front-facing camera.
 * #918 : Handle forgotten password verification link properly.
 * #923 : local contact section should be collapsable even when no search is started.
 * #909 : Retry schedule is too aggressive for arbitrary endpoints.
 * #931 : Settings: move the Devices section after the Cryptography section.
 * #932 : Rooms details: can't open a txt file from the FILES tab of an e2e room.
 * #933 : Search from recents: strange behaviour in the differents tab.
 * #934 : Search from recents: no results displayed if device is turned landscape then portrait.
 * #940 : The quick reply popup and compose box are unnecessarily small
 * #941 : Usability: The compose window activation area is deceptively small.
 * #949 : e2e and auth keys should be blacklisted from google backup somehow.
 * #950 : Unknown devices: 2 press on blacklist button are needed.
 * #952 : Launch a call in a e2e and 1:1 room with unknown devices make the call fail
 * #953 : Crash trying to send a message in e2e room with unknown devices.
 * #954 : Language: "Report Bug Report"
 * #955 : New Rageshake: no feedback or progress indication at all
 * #957 : Voice Calling turns off screen erroneously
 * #964 : 'Messages not sent due to unknown devices ...' is cropped in the notification area.
 * #980 : Not an admin in a group --> "enable encryption" should not be displayed
 * #984 : «Clear Cache» also erases my settings
 * #989 : it sometimes takes several presses of the send button to get the message out
 * #1010 : Room members Search with a new account displays "too many contacts" in the known section whereas there is no joined room
 * #1011 : [e2e devices deletion] : write the user password once and allow to delete several devices
 * #1012 : Close a member details activity should return to the calling activity
 * #1013 : Voip: call canceled when switching from call layout and pending call view

Changes in Riot 0.6.8 (2017-01-27)
===================================================

Improvements:
 * The members list activity design has been improved.
 * Add some google analytics stats.
 * Trigger the email lookup on demand to save data connection use.
 * Improve the settings screens to have the material design for the device with API < 21.

Bugfixes:
 * Fix crypto backward compatibility issue (< 0.6.4).
 * Fix an invite contacts permission request loop if it was not granted (room members invitation screen).
 * #878 : Room activity : the very long member name overlaps the time
 * #636 : Log in button is not enabled when internet connection comes back.
 * #891 : Infinite contacts permission request dialog if it is rejected
 * #894 : matrix user id regex does not allow underscore in the name.

Changes in Vector 0.6.7 (2017-01-23)
===================================================

Improvements:
 * The room invitation activity design has been improved.

Bugfixes:
 * Fix a crash when a contact with a thumbnail was invited.
 * The users were not saved after a login.
 * Fix several issues reported by Google Analytics.
 * #868 : Add Leave Room Confirmation.

Changes in Vector 0.6.6 (2017-01-17)
===================================================

Improvements:
 * Improve the camera activity management.
 * Improve the e2e management.
 * Improve the people invitation activity.

Bugfixes:
 * Fix several issues reported by Google Analytics.
 * #791 : [UI bug] Room encryption slider remains on after rejecting the popup window by clicking outside of it.
 * #806 : Please remove End-to-End Encryption toggle from user settings.
 * #807 : /mefoo is turned into /me foo.
 * #816 : Custom server URL bug.
 * #821 : Room creation with a matrix user from the contacts list creates several empty rooms.
 * #841 : Infinite call ringing.
 * #842 : rageshake should prompt you to enter an explicit problem report before trying to send a report.
 * #851 : fix_device_verify_not_displayed

Changes in Vector 0.6.5 (2016-12-19)
===================================================

Improvements:
 * Reduce the messages encryption time.
 * Display a lock icon for the encrypted rooms (recents page).
 * Video call: the local preview is displayed at the bottom left.
 * Improve the splashscreen (reduce the animated gif time and add a spinner)
 * Display an alert when the crypto store is corrupted to let the user chooses if he wants to logout.

Bugfixes:
 * Fix several issues reported by GA.
 * Do not enable the proximity sensor when the voice call is not established
 * Fix several call issues with the Samsung devices (when the screen is turned off).
 * #783 : Riot doesn't handle volume settings properly
 * #784 : Voip: Problem when call is hung up while callee goes in room view.
 * #786 : Method to disable markdown is unclear.
 * #787 : overlay buttons shouldn't self-hide when on voice calls

Changes in Vector 0.6.4 (2016-12-13)
===================================================

Features:
 * #757 : Add devices list member details.

Improvements:
 * Improve the encryption management.
 * The application should be ready faster.

Bugfixes:
 * Fix many issues reported by GA.
 * Fix many memory leaks.
 * #374 : Check if Event.unsigned.age can be used to detect if the event is still valid.
 * #657 : It's too easy to accidentally ignore someone
 * #661 : Turn the screen off during a call when the proximity sensor says phone near head
 * #675 : Handle user link correctly
 * #687 : User adress instead of display name in call event
 * #723 : Cancelling download of encrypted image does not work
 * #706 : [Direct Message] Direct chats list from member profile doesn't show all the direct chats
 * #708 : vertical offset into recents list is not preserved
 * #749 : Layout broken with RTL languages
 * #754 : Memory leak when opening a room
 * #760 : Stacked room pages when going back and forth between Call layout and Room layout
 * #774 : Bug report / rageshake does not get user consent before sharing potentially personal data
 * #776 : Add a dialog to confirm the message redaction


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


=======================================================
+        TEMPLATE WHEN PREPARING A NEW RELEASE        +
=======================================================


Changes in Riot 0.9.XX (2019-XX-XX)
===================================================

MatrixSdk:
 - Upgrade MatrixSdk to version 0.X.Y.
 - Changelog: https://github.com/matrix-org/matrix-android-sdk/releases/tag/v0.X.Y

Features:
 -

Improvements:
 -

Other changes:
 -

Bugfix:
 -

Translations:
 -

Build:
 -

