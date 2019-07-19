/*
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.notifications

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.support.annotation.StringRes
import android.support.v4.app.*
import android.support.v4.content.ContextCompat
import android.text.TextUtils
import im.vector.BuildConfig
import im.vector.R
import im.vector.activity.JoinRoomActivity
import im.vector.activity.LockScreenActivity
import im.vector.activity.VectorHomeActivity
import im.vector.activity.VectorRoomActivity
import im.vector.receiver.NotificationBroadcastReceiver
import im.vector.util.PreferencesManager
import im.vector.util.startNotificationChannelSettingsIntent
import org.matrix.androidsdk.core.Log
import java.util.*


fun supportNotificationChannels() = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

/**
 * Util class for creating notifications.
 */
object NotificationUtils {
    private val LOG_TAG = NotificationUtils::class.java.simpleName

    /* ==========================================================================================
     * IDs for notifications
     * ========================================================================================== */

    /**
     * Identifier of the foreground notification used to keep the application alive
     * when it runs in background.
     * This notification, which is not removable by the end user, displays what
     * the application is doing while in background.
     */
    const val NOTIFICATION_ID_FOREGROUND_SERVICE = 61

    /* ==========================================================================================
     * IDs for actions
     * ========================================================================================== */

    private const val JOIN_ACTION = "${BuildConfig.APPLICATION_ID}.NotificationActions.JOIN_ACTION"
    private const val REJECT_ACTION = "${BuildConfig.APPLICATION_ID}.NotificationActions.REJECT_ACTION"
    private const val QUICK_LAUNCH_ACTION = "${BuildConfig.APPLICATION_ID}.NotificationActions.QUICK_LAUNCH_ACTION"
    const val MARK_ROOM_READ_ACTION = "${BuildConfig.APPLICATION_ID}.NotificationActions.MARK_ROOM_READ_ACTION"
    const val SMART_REPLY_ACTION = "${BuildConfig.APPLICATION_ID}.NotificationActions.SMART_REPLY_ACTION"
    const val DISMISS_SUMMARY_ACTION = "${BuildConfig.APPLICATION_ID}.NotificationActions.DISMISS_SUMMARY_ACTION"
    const val DISMISS_ROOM_NOTIF_ACTION = "${BuildConfig.APPLICATION_ID}.NotificationActions.DISMISS_ROOM_NOTIF_ACTION"
    private const val TAP_TO_VIEW_ACTION = "${BuildConfig.APPLICATION_ID}.NotificationActions.TAP_TO_VIEW_ACTION"

    /* ==========================================================================================
     * IDs for channels
     * ========================================================================================== */

    // on devices >= android O, we need to define a channel for each notifications
    private const val LISTENING_FOR_EVENTS_NOTIFICATION_CHANNEL_ID = "LISTEN_FOR_EVENTS_NOTIFICATION_CHANNEL_ID"

    private const val NOISY_NOTIFICATION_CHANNEL_ID = "DEFAULT_NOISY_NOTIFICATION_CHANNEL_ID"

    private const val SILENT_NOTIFICATION_CHANNEL_ID = "DEFAULT_SILENT_NOTIFICATION_CHANNEL_ID_V2"
    private const val CALL_NOTIFICATION_CHANNEL_ID = "CALL_NOTIFICATION_CHANNEL_ID_V2"

    /* ==========================================================================================
     * Channel names
     * ========================================================================================== */

    /**
     * Create notification channels.
     *
     * @param context the context
     */
    @TargetApi(Build.VERSION_CODES.O)
    fun createNotificationChannels(context: Context) {
        if (!supportNotificationChannels()) {
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val accentColor = ContextCompat.getColor(context, R.color.notification_accent_color)

        //Migration - the noisy channel was deleted and recreated when sound preference was changed (id was DEFAULT_NOISY_NOTIFICATION_CHANNEL_ID_BASE
        // + currentTimeMillis).
        //Now the sound can only be change directly in system settings, so for app upgrading we are deleting this former channel
        //Starting from this version the channel will not be dynamic
        for (channel in notificationManager.notificationChannels) {
            val channelId = channel.id
            val legacyBaseName = "DEFAULT_NOISY_NOTIFICATION_CHANNEL_ID_BASE"
            if (channelId.startsWith(legacyBaseName)) {
                notificationManager.deleteNotificationChannel(channelId)
            }
        }
        //Migration - Remove deprecated channels
        for (channelId in listOf("DEFAULT_SILENT_NOTIFICATION_CHANNEL_ID", "CALL_NOTIFICATION_CHANNEL_ID")) {
            notificationManager.getNotificationChannel(channelId)?.let {
                notificationManager.deleteNotificationChannel(channelId)
            }
        }

        /**
         * Default notification importance: shows everywhere, makes noise, but does not visually
         * intrude.
         */
        notificationManager.createNotificationChannel(NotificationChannel(NOISY_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.notification_noisy_notifications),
                NotificationManager.IMPORTANCE_DEFAULT)
                .apply {
                    description = context.getString(R.string.notification_noisy_notifications)
                    enableVibration(true)
                    enableLights(true)
                    lightColor = accentColor
                })

        /**
         * Low notification importance: shows everywhere, but is not intrusive.
         */
        notificationManager.createNotificationChannel(NotificationChannel(SILENT_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.notification_silent_notifications),
                NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = context.getString(R.string.notification_silent_notifications)
                    setSound(null, null)
                    enableLights(true)
                    lightColor = accentColor
                })

        notificationManager.createNotificationChannel(NotificationChannel(LISTENING_FOR_EVENTS_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.notification_listening_for_events),
                NotificationManager.IMPORTANCE_MIN)
                .apply {
                    description = context.getString(R.string.notification_listening_for_events)
                    setSound(null, null)
                    setShowBadge(false)
                })

        notificationManager.createNotificationChannel(NotificationChannel(CALL_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.call),
                NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = context.getString(R.string.call)
                    setSound(null, null)
                    enableLights(true)
                    lightColor = accentColor
                })
    }

    /**
     * Build a polling thread listener notification
     *
     * @param context       Android context
     * @param subTitleResId subtitle string resource Id of the notification
     * @return the polling thread listener notification
     */
    @SuppressLint("NewApi")
    fun buildForegroundServiceNotification(context: Context, @StringRes subTitleResId: Int): Notification {
        // build the pending intent go to the home screen if this is clicked.
        val i = Intent(context, VectorHomeActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pi = PendingIntent.getActivity(context, 0, i, 0)

        val accentColor = ContextCompat.getColor(context, R.color.notification_accent_color)

        val builder = NotificationCompat.Builder(context, LISTENING_FOR_EVENTS_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(context.getString(subTitleResId))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setSmallIcon(R.drawable.logo_transparent)
                .setProgress(0, 0, true)
                .setColor(accentColor)
                .setContentIntent(pi)

        // hide the notification from the status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.priority = NotificationCompat.PRIORITY_MIN
        }

        val notification = builder.build()

        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // some devices crash if this field is not set
            // even if it is deprecated

            // setLatestEventInfo() is deprecated on Android M, so we try to use
            // reflection at runtime, to avoid compiler error: "Cannot resolve method.."
            try {
                val deprecatedMethod = notification.javaClass
                        .getMethod("setLatestEventInfo",
                                Context::class.java,
                                CharSequence::class.java,
                                CharSequence::class.java,
                                PendingIntent::class.java)
                deprecatedMethod.invoke(notification, context, context.getString(R.string.riot_app_name), context.getString(subTitleResId), pi)
            } catch (ex: Exception) {
                Log.e(LOG_TAG, "## buildNotification(): Exception - setLatestEventInfo() Msg=" + ex.message, ex)
            }

        }
        return notification
    }

    /**
     * Build an incoming call notification.
     * This notification starts the VectorHomeActivity which is in charge of centralizing the incoming call flow.
     *
     * @param context  the context.
     * @param isVideo  true if this is a video call, false for voice call
     * @param roomName the room name in which the call is pending.
     * @param matrixId the matrix id
     * @param callId   the call id.
     * @return the call notification.
     */
    @SuppressLint("NewApi")
    fun buildIncomingCallNotification(context: Context,
                                      isVideo: Boolean,
                                      roomName: String,
                                      matrixId: String,
                                      callId: String): Notification {
        val accentColor = ContextCompat.getColor(context, R.color.notification_accent_color)

        val builder = NotificationCompat.Builder(context, CALL_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(ensureTitleNotEmpty(context, roomName))
                .apply {
                    if (isVideo) {
                        setContentText(context.getString(R.string.incoming_video_call))
                    } else {
                        setContentText(context.getString(R.string.incoming_voice_call))
                    }
                }
                .setSmallIcon(R.drawable.incoming_call_notification_transparent)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setLights(accentColor, 500, 500)

        //Compat: Display the incoming call notification on the lock screen
        builder.priority = NotificationCompat.PRIORITY_MAX

        // clear the activity stack to home activity
        val intent = Intent(context, VectorHomeActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(VectorHomeActivity.EXTRA_CALL_SESSION_ID, matrixId)
                .putExtra(VectorHomeActivity.EXTRA_CALL_ID, callId)

        // Recreate the back stack
        val stackBuilder = TaskStackBuilder.create(context)
                .addParentStack(VectorHomeActivity::class.java)
                .addNextIntent(intent)


        // android 4.3 issue
        // use a generator for the private requestCode.
        // When using 0, the intent is not created/launched when the user taps on the notification.
        //
        val pendingIntent = stackBuilder.getPendingIntent(Random().nextInt(1000), PendingIntent.FLAG_UPDATE_CURRENT)

        builder.setContentIntent(pendingIntent)

        return builder.build()
    }

    /**
     * Build a pending call notification
     *
     * @param context  the context.
     * @param isVideo  true if this is a video call, false for voice call
     * @param roomName the room name in which the call is pending.
     * @param roomId   the room Id
     * @param matrixId the matrix id
     * @param callId   the call id.
     * @return the call notification.
     */
    @SuppressLint("NewApi")
    fun buildPendingCallNotification(context: Context,
                                     isVideo: Boolean,
                                     roomName: String,
                                     roomId: String,
                                     matrixId: String,
                                     callId: String): Notification {

        val builder = NotificationCompat.Builder(context, CALL_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(ensureTitleNotEmpty(context, roomName))
                .apply {
                    if (isVideo) {
                        setContentText(context.getString(R.string.video_call_in_progress))
                    } else {
                        setContentText(context.getString(R.string.call_in_progress))
                    }
                }
                .setSmallIcon(R.drawable.incoming_call_notification_transparent)
                .setCategory(NotificationCompat.CATEGORY_CALL)

        // Display the pending call notification on the lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.priority = NotificationCompat.PRIORITY_MAX
        }

        // Build the pending intent for when the notification is clicked
        val roomIntent = Intent(context, VectorRoomActivity::class.java)
                .putExtra(VectorRoomActivity.EXTRA_ROOM_ID, roomId)
                .putExtra(VectorRoomActivity.EXTRA_MATRIX_ID, matrixId)
                .putExtra(VectorRoomActivity.EXTRA_START_CALL_ID, callId)

        // Recreate the back stack
        val stackBuilder = TaskStackBuilder.create(context)
                .addParentStack(VectorRoomActivity::class.java)
                .addNextIntent(roomIntent)

        // android 4.3 issue
        // use a generator for the private requestCode.
        // When using 0, the intent is not created/launched when the user taps on the notification.
        //
        val pendingIntent = stackBuilder.getPendingIntent(Random().nextInt(1000), PendingIntent.FLAG_UPDATE_CURRENT)

        builder.setContentIntent(pendingIntent)

        return builder.build()
    }

    /**
     * Build a temporary (because service will be stopped just after) notification for the CallService, when a call is ended
     */
    fun buildCallEndedNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CALL_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.call_ended))
                .setSmallIcon(R.drawable.ic_material_call_end_grey)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .build()
    }

    /**
     * Build a notification for a Room
     */
    fun buildMessagesListNotification(context: Context,
                                      messageStyle: NotificationCompat.MessagingStyle,
                                      roomInfo: RoomEventGroupInfo,
                                      largeIcon: Bitmap?,
                                      lastMessageTimestamp: Long,
                                      senderDisplayNameForReplyCompat: String?): Notification? {

        val accentColor = ContextCompat.getColor(context, R.color.notification_accent_color)
        // Build the pending intent for when the notification is clicked
        val openRoomIntent = buildOpenRoomIntent(context, roomInfo.roomId)
        val smallIcon = if (roomInfo.shouldBing) R.drawable.icon_notif_important else R.drawable.logo_transparent

        val channelID = if (roomInfo.shouldBing) NOISY_NOTIFICATION_CHANNEL_ID else SILENT_NOTIFICATION_CHANNEL_ID
        return NotificationCompat.Builder(context, channelID)
                .setWhen(lastMessageTimestamp)
                // MESSAGING_STYLE sets title and content for API 16 and above devices.
                .setStyle(messageStyle)

                // A category allows groups of notifications to be ranked and filtered – per user or system settings.
                // For example, alarm notifications should display before promo notifications, or message from known contact
                // that can be displayed in not disturb mode if white listed (the later will need compat28.x)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)

                // Title for API < 16 devices.
                .setContentTitle(roomInfo.roomDisplayName)
                // Content for API < 16 devices.
                .setContentText(context.getString(R.string.notification_new_messages))

                // Number of new notifications for API <24 (M and below) devices.
                .setSubText(context
                        .resources
                        .getQuantityString(R.plurals.room_new_messages_notification, messageStyle.messages.size, messageStyle.messages.size)
                )

                // Auto-bundling is enabled for 4 or more notifications on API 24+ (N+)
                // devices and all Wear devices. But we want a custom grouping, so we specify the groupID
                // TODO Group should be current user display name
                .setGroup(context.getString(R.string.riot_app_name))

                //In order to avoid notification making sound twice (due to the summary notification)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)

                .setSmallIcon(smallIcon)

                // Set primary color (important for Wear 2.0 Notifications).
                .setColor(accentColor)

                // Sets priority for 25 and below. For 26 and above, 'priority' is deprecated for
                // 'importance' which is set in the NotificationChannel. The integers representing
                // 'priority' are different from 'importance', so make sure you don't mix them.
                .apply {
                    priority = NotificationCompat.PRIORITY_DEFAULT
                    if (roomInfo.shouldBing) {
                        //Compat
                        PreferencesManager.getNotificationRingTone(context)?.let {
                            setSound(it)
                        }
                        setLights(accentColor, 500, 500)
                    } else {
                        priority = NotificationCompat.PRIORITY_LOW
                    }

                    //Add actions and notification intents
                    // Mark room as read
                    val markRoomReadIntent = Intent(context, NotificationBroadcastReceiver::class.java)
                    markRoomReadIntent.action = MARK_ROOM_READ_ACTION
                    markRoomReadIntent.data = Uri.parse("foobar://${roomInfo.roomId}")
                    markRoomReadIntent.putExtra(NotificationBroadcastReceiver.KEY_ROOM_ID, roomInfo.roomId)
                    val markRoomReadPendingIntent = PendingIntent.getBroadcast(context, System.currentTimeMillis().toInt(), markRoomReadIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT)

                    addAction(NotificationCompat.Action(
                            R.drawable.ic_material_done_all_white,
                            context.getString(R.string.action_mark_room_read),
                            markRoomReadPendingIntent))

                    // Quick reply
                    if (!roomInfo.hasSmartReplyError) {
                        buildQuickReplyIntent(context, roomInfo.roomId, senderDisplayNameForReplyCompat)?.let { replyPendingIntent ->
                            val remoteInput = RemoteInput.Builder(NotificationBroadcastReceiver.KEY_TEXT_REPLY)
                                    .setLabel(context.getString(R.string.action_quick_reply))
                                    .build()
                            NotificationCompat.Action.Builder(R.drawable.vector_notification_quick_reply,
                                    context.getString(R.string.action_quick_reply), replyPendingIntent)
                                    .addRemoteInput(remoteInput)
                                    .build()?.let {
                                        addAction(it)
                                    }
                        }
                    }

                    if (openRoomIntent != null) {
                        setContentIntent(openRoomIntent)
                    }

                    if (largeIcon != null) {
                        setLargeIcon(largeIcon)
                    }

                    val intent = Intent(context, NotificationBroadcastReceiver::class.java)
                    intent.putExtra(NotificationBroadcastReceiver.KEY_ROOM_ID, roomInfo.roomId)
                    intent.action = DISMISS_ROOM_NOTIF_ACTION
                    val pendingIntent = PendingIntent.getBroadcast(context.applicationContext,
                            System.currentTimeMillis().toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT)
                    setDeleteIntent(pendingIntent)
                }
                .build()
    }


    fun buildSimpleEventNotification(context: Context, simpleNotifiableEvent: NotifiableEvent, largeIcon: Bitmap?, matrixId: String): Notification? {
        val accentColor = ContextCompat.getColor(context, R.color.notification_accent_color)
        // Build the pending intent for when the notification is clicked
        val smallIcon = if (simpleNotifiableEvent.noisy) R.drawable.icon_notif_important else R.drawable.logo_transparent

        val channelID = if (simpleNotifiableEvent.noisy) NOISY_NOTIFICATION_CHANNEL_ID else SILENT_NOTIFICATION_CHANNEL_ID

        return NotificationCompat.Builder(context, channelID)
                .setContentTitle(context.getString(R.string.riot_app_name))
                .setContentText(simpleNotifiableEvent.description)
                .setGroup(context.getString(R.string.riot_app_name))
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setSmallIcon(smallIcon)
                .setColor(accentColor)
                .apply {
                    if (simpleNotifiableEvent is InviteNotifiableEvent) {
                        val roomId = simpleNotifiableEvent.roomId
                        // offer to type a quick reject button
                        val rejectIntent = JoinRoomActivity.getRejectRoomIntent(context, roomId, matrixId)

                        // the action must be unique else the parameters are ignored
                        rejectIntent.action = REJECT_ACTION
                        rejectIntent.data = Uri.parse("foobar://$roomId&$matrixId")
                        addAction(
                                R.drawable.vector_notification_reject_invitation,
                                context.getString(R.string.reject),
                                PendingIntent.getActivity(context, System.currentTimeMillis().toInt(), rejectIntent, 0))

                        // offer to type a quick accept button
                        val joinIntent = JoinRoomActivity.getJoinRoomIntent(context, roomId, matrixId)

                        // the action must be unique else the parameters are ignored
                        joinIntent.action = JOIN_ACTION
                        joinIntent.data = Uri.parse("foobar://$roomId&$matrixId")
                        addAction(
                                R.drawable.vector_notification_accept_invitation,
                                context.getString(R.string.join),
                                PendingIntent.getActivity(context, 0, joinIntent, 0))

                    } else {
                        setAutoCancel(true)
                    }

                    val contentIntent = Intent(context, VectorHomeActivity::class.java)
                    contentIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    //pending intent get reused by system, this will mess up the extra params, so put unique info to avoid that
                    contentIntent.data = Uri.parse("foobar://" + simpleNotifiableEvent.eventId)
                    setContentIntent(PendingIntent.getActivity(context, 0, contentIntent, 0))

                    if (largeIcon != null) {
                        setLargeIcon(largeIcon)
                    }

                    if (simpleNotifiableEvent.noisy) {
                        //Compat
                        priority = NotificationCompat.PRIORITY_DEFAULT
                        PreferencesManager.getNotificationRingTone(context)?.let {
                            setSound(it)
                        }
                        setLights(accentColor, 500, 500)
                    } else {
                        priority = NotificationCompat.PRIORITY_LOW
                    }
                    setAutoCancel(true)
                }
                .build()
    }

    private fun buildOpenRoomIntent(context: Context, roomId: String): PendingIntent? {
        val roomIntentTap = Intent(context, VectorRoomActivity::class.java)
        roomIntentTap.putExtra(VectorRoomActivity.EXTRA_ROOM_ID, roomId)
        roomIntentTap.action = TAP_TO_VIEW_ACTION
        //pending intent get reused by system, this will mess up the extra params, so put unique info to avoid that
        roomIntentTap.data = Uri.parse("foobar://openRoom?$roomId")

        // Recreate the back stack
        return TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(Intent(context, VectorHomeActivity::class.java))
                .addNextIntent(roomIntentTap)
                .getPendingIntent(System.currentTimeMillis().toInt(), PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun buildOpenHomePendingIntentForSummary(context: Context): PendingIntent {
        val intent = Intent(context, VectorHomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.putExtra(VectorHomeActivity.EXTRA_CLEAR_EXISTING_NOTIFICATION, true)
        intent.data = Uri.parse("foobar://tapSummary")
        return PendingIntent.getActivity(context, Random().nextInt(1000), intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /*
        Direct reply is new in Android N, and Android already handles the UI, so the right pending intent
        here will ideally be a Service/IntentService (for a long running background task) or a BroadcastReceiver,
         which runs on the UI thread. It also works without unlocking, making the process really fluid for the user.
        However, for Android devices running Marshmallow and below (API level 23 and below),
        it will be more appropriate to use an activity. Since you have to provide your own UI.
     */
    private fun buildQuickReplyIntent(context: Context, roomId: String, senderName: String?): PendingIntent? {
        val intent: Intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent = Intent(context, NotificationBroadcastReceiver::class.java)
            intent.action = SMART_REPLY_ACTION
            intent.data = Uri.parse("foobar://$roomId")
            intent.putExtra(NotificationBroadcastReceiver.KEY_ROOM_ID, roomId)
            return PendingIntent.getBroadcast(context, System.currentTimeMillis().toInt(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            if (!LockScreenActivity.isDisplayingALockScreenActivity()) {
                // start your activity for Android M and below
                val quickReplyIntent = Intent(context, LockScreenActivity::class.java)
                quickReplyIntent.putExtra(LockScreenActivity.EXTRA_ROOM_ID, roomId)
                quickReplyIntent.putExtra(LockScreenActivity.EXTRA_SENDER_NAME, senderName ?: "")

                // the action must be unique else the parameters are ignored
                quickReplyIntent.action = QUICK_LAUNCH_ACTION
                quickReplyIntent.data = Uri.parse("foobar://$roomId")
                return PendingIntent.getActivity(context, 0, quickReplyIntent, 0)
            }
        }
        return null
    }

    //// Number of new notifications for API <24 (M and below) devices.
    /**
     * Build the summary notification
     */
    fun buildSummaryListNotification(context: Context,
                                     style: NotificationCompat.Style,
                                     compatSummary: String,
                                     noisy: Boolean,
                                     lastMessageTimestamp: Long): Notification? {
        val accentColor = ContextCompat.getColor(context, R.color.notification_accent_color)
        val smallIcon = if (noisy) R.drawable.icon_notif_important else R.drawable.logo_transparent

        return NotificationCompat.Builder(context, if (noisy) NOISY_NOTIFICATION_CHANNEL_ID else SILENT_NOTIFICATION_CHANNEL_ID)
                // used in compat < N, after summary is built based on child notifications
                .setWhen(lastMessageTimestamp)
                .setStyle(style)
                .setContentTitle(context.getString(R.string.riot_app_name))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setSmallIcon(smallIcon)
                //set content text to support devices running API level < 24
                .setContentText(compatSummary)
                .setGroup(context.getString(R.string.riot_app_name))
                //set this notification as the summary for the group
                .setGroupSummary(true)
                .setColor(accentColor)
                .apply {
                    if (noisy) {
                        //Compat
                        priority = NotificationCompat.PRIORITY_DEFAULT
                        PreferencesManager.getNotificationRingTone(context)?.let {
                            setSound(it)
                        }
                        setLights(accentColor, 500, 500)
                    } else {
                        //compat
                        priority = NotificationCompat.PRIORITY_LOW
                    }
                }
                .setContentIntent(buildOpenHomePendingIntentForSummary(context))
                .setDeleteIntent(getDismissSummaryPendingIntent(context))
                .build()

    }

    private fun getDismissSummaryPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NotificationBroadcastReceiver::class.java)
        intent.action = DISMISS_SUMMARY_ACTION
        intent.data = Uri.parse("foobar://deleteSummary")
        return PendingIntent.getBroadcast(context.applicationContext,
                0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun showNotificationMessage(context: Context, tag: String?, id: Int, notification: Notification) {
        with(NotificationManagerCompat.from(context)) {
            notify(tag, id, notification)
        }
    }

    fun cancelNotificationMessage(context: Context, tag: String?, id: Int) {
        NotificationManagerCompat.from(context)
                .cancel(tag, id)
    }

    /**
     * Cancel the foreground notification service
     */
    fun cancelNotificationForegroundService(context: Context) {
        NotificationManagerCompat.from(context)
                .cancel(NOTIFICATION_ID_FOREGROUND_SERVICE)
    }

    /**
     * Cancel all the notification
     */
    fun cancelAllNotifications(context: Context) {
        // Keep this try catch (reported by GA)
        try {
            NotificationManagerCompat.from(context)
                    .cancelAll()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "## cancelAllNotifications() failed " + e.message, e)
        }
    }

    /**
     * Return true it the user has enabled the do not disturb mode
     */
    fun isDoNotDisturbModeOn(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }

        // We cannot use NotificationManagerCompat here.
        val setting = (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).currentInterruptionFilter

        return setting == NotificationManager.INTERRUPTION_FILTER_NONE
                || setting == NotificationManager.INTERRUPTION_FILTER_ALARMS
    }

    private fun ensureTitleNotEmpty(context: Context, title: String?): CharSequence {
        if (TextUtils.isEmpty(title)) {
            return context.getString(R.string.app_name)
        }

        return title!!
    }

    fun openSystemSettingsForSilentCategory(fragment: Fragment) {
        startNotificationChannelSettingsIntent(fragment, SILENT_NOTIFICATION_CHANNEL_ID)
    }

    fun openSystemSettingsForNoisyCategory(fragment: Fragment) {
        startNotificationChannelSettingsIntent(fragment, NOISY_NOTIFICATION_CHANNEL_ID)
    }


    fun openSystemSettingsForCallCategory(fragment: Fragment) {
        startNotificationChannelSettingsIntent(fragment, CALL_NOTIFICATION_CHANNEL_ID)
    }
}
