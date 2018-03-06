This document aims to describe how Riot-Android displays notifications to the end user. It also clarifies notifications and background settings in the app.

# Notification implementations

Riot Android code can manage notifications in two modes:

## GCM

Based on Google Cloud Messaging, the user's homeserver notifies [sygnal](https://github.com/matrix-org/sygnal), the matrix push server, that notifies GCM that notifies the Android matrix client app.

`Homeserver ----> Sygnal ----> GCM ----> Riot`

Then, according to the [background sync](#background-synchronisation--enable-background-sync) setting, the app will show directly the notification or will do a sync with the user's HS.


  - Background sync enabled

GCM only wakes up the app. The app syncs with the user's homeserver to get message content to display in the notification.

```
Homeserver ----> Sygnal ----> GCM ----> Riot
                                        (Sync) ----> Homeserver
                                               <---- 
                                        Display notification
```

 
  - Background sync disabled

```
Homeserver ----> Sygnal ----> GCM ----> Riot
                                        Display notification                               
```



## Fallback mode

When the device fails to register on GCM or when GCM is disabled in the application build (F-Droid), the app needs to fetch data in background so that it can display notifications to the end user when required.
This mode requires that the user enables [background sync](#background-synchronisation--enable-background-sync) setting is enabled. Else, the application cannot detect events to notify.


```
                                       Riot
                                       (Periodic syncs) ----> Homeserver
                                                        <---- 
                                       Display notification
```


# Application Settings

## Notifications > Enable notifications for this account
 
Configure Sygnal to send or not notifications to all user devices. 

## Notifications > Enable notifications for this device

Disable notifications locally. The push server will continue to send notifications to the device but this one will ignore them.

## Background synchronisation > Enable background sync

The behavior of this setting is not the same in GCM and in fallback mode.

### GCM
This setting will configure Sygnal to send more or less data through GCM.
If enabled, the push server sends a GCM notification to the app containing the room id and the event id.
The app needs then to do a background synchronisation with the server to retrieve data. Rhe app will build the notification from this data.

PRO: Only meta data goes through GCM. The app decrypts messages in e2e rooms

CON: Use more network and battery. Notifications take more time to appear.


If disabled, the push server sends a GCM notification to the app containing the room id, the event id and the **event content** so that the application can display a notification directly from GCM data.

PRO: network and battery efficient.

CON: contents of events in non encrypted room go through GCM. 


### Fallback mode

This mode requires this setting to enables to generate notifications.



## LABS > Data save mode

**TODO**: What is it. Does it impact notifications?



---
WIP
---


# Android 8
Android added limitations on the way app can run in background

*To improve the user experience, Android 8.0 (API level 26) imposes limitations on what apps can do while running in the background* (https://developer.android.com/about/versions/oreo/background.html)

## How it is solved in Riot
Double notifications!

# Permissions

- Android settings > Riot >  ...> run at startup

Is there a permission to run in bg or is it this one ^?
