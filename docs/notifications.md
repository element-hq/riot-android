This document aims to describe how Riot-Android displays notifications to the end user. It also clarifies notifications and background settings in the app.

# Notification implementations

Riot Android code can manage notifications in two modes:

## GCM

Based on Google Cloud Messaging, the user's homeserver notifies [sygnal](https://github.com/matrix-org/sygnal), the matrix push server, that notifies GCM that notifies the Android matrix client app.

`Homeserver ----> Sygnal ----> GCM ----> Riot`

Then, according to the [background sync](#background-synchronisation--enable-background-sync) setting, the app will show directly the notification or will do a sync with the user's HS.


  - Background sync enabled

GCM only wakes up the app. The app syncs with the user's homeserver to get the message content to display in the notification.

```
Homeserver ----> Sygnal ----> GCM ----> Riot
                                        (Sync) ----> Homeserver
                                               <---- 
                                        Display notification
```

 
  - Background sync disabled

The app displays a notification with the data provided with the GCM payload.
Check the [background sync disabled](#background-sync-disabled) section for more details about data going through the GCM infrastructure in this configuration.

```
Homeserver ----> Sygnal ----> GCM ----> Riot
                                        Display notification
```



## Fallback mode

When the device fails to register on GCM or when GCM is disabled in the application build (F-Droid), the app needs to fetch data in background so that it can display notifications to the end user when required.
This mode requires that the user enables the [background sync](#background-synchronisation--enable-background-sync) setting. Else, the application cannot detect events to notify.


```
                                       Riot
                                       (Periodic syncs) ----> Homeserver
                                                        <---- 
                                       Display notification
```

The inverval between periodic syncs with the homeserver is 66 seconds by default. It can be tuned thanks to [Delay between two sync requests](#background-synchronisation--delay-between-two-sync-requests-only-in-fallback-mode) and [Sync request timeout](#background-synchronisation--sync-request-timeout-only-in-fallback-mode) settings.


# Application Settings

## Notifications > Enable notifications for this account
 
Configure Sygnal to send or not notifications to all user devices. 

## Notifications > Enable notifications for this device

Disable notifications locally. The push server will continue to send notifications to the device but this one will ignore them.

## Background synchronisation > Enable background sync

This setting allows the app to do background syncs with the server. The way it affects the notifications management behavior is not the same in GCM and in fallback mode.

### GCM
This setting will configure Sygnal to send more or less data through GCM.

#### background sync enabled
If enabled, the push server sends a GCM notification to the app containing the room id and the event id.
The app then does a background synchronisation with the homeserver to retrieve data. The app will build the notification from this data.

<ins>PRO</ins>: Only meta data goes through GCM. The app decrypts messages in e2e rooms.

<ins>CON:</ins> Use more network and battery. Notifications take more time to appear.

#### background sync disabled
If disabled, the push server sends a GCM notification to the app containing the room id, the event id and the **event content** so that the application can display a notification directly from GCM data.

<ins>PRO</ins>: network and battery efficient.

<ins>CON</ins>: contents of events in non encrypted room go through GCM. 


### Fallback mode

This mode requires this setting enabled so that the application can do background syncs with the homeserver to detect events to notify to end user.


## Background synchronisation > Start on boot (only in fallback mode)

Allow the application to start the fallback background process when the device boots.

## Background synchronisation > Delay between two sync requests (only in fallback mode)

The interval in seconds between two background syncs with the homeserver in fallback mode. Default is 60s.

## Background synchronisation > Sync request timeout (only in fallback mode)

The time given to the homeserver to answer to a fallback background sync. Default is 6s.


# Background tasks in Android 8

Android 8 added limitations on the way app can run in background: *To improve the user experience, Android 8.0 (API level 26) imposes limitations on what apps can do while running in the background* (https://developer.android.com/about/versions/oreo/background.html).

On an Android 8 device, that gives only 15s to run in backgroumd.

This impacts the background behavior of the app:

- in GCM mode, the background synchronisation to fetch data to fill the notification can be interrupted.
- in fallback mode, the loop of background syncs can be broken. The user receive no more notifications.


## How it is solved in Riot

The way to solve it consists in displaying a persistent notification ([Notification.FLAG_NO_CLEAR](https://developer.android.com/reference/android/app/Notification.html#FLAG_NO_CLEAR)) when the application is doing background tasks.

This notification is persistent meaning that the user cannot remove it while it is displayed.

### GCM mode

In GCM mode, when [background sync](#background-synchronisation--enable-background-sync) is enabled, while the app syncs in background, it needs to display a persistent notification. The displayed message is then `Synchronising`.

Once the sync is complete, the notification is removed and a normal (ie, removable) notification showing messages is displayed.

### Fallback mode

In fallback mode, the application needs to run permanently in background to manage notifications. That means it must display a persistent notification forever. The displayed message is then `Listen for events`.

In order to display true messages notifications, the application can:

 - display a new normal (ie, removable) notification with those messages. But that creates a second notification and a second Riot icon in the Android notifications status bar.
 - replace the persistent `Listen for events` notification by a new persistent notification showing new messages. There is only one Riot notification but this one is unswipable. The notifications for new messages will be reset only when the user opens the app.
 
 Riot, as of time of writing, uses the 2nd approach.
