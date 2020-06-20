Riot-Android [![Buildkite](https://badge.buildkite.com/5ae4f24dd485562a5b59a9f84d866e5eed3d100223423757f2.svg?branch=develop)](https://buildkite.com/matrix-dot-org/riot-android) [![Weblate](https://translate.riot.im/widgets/riot-android/-/svg-badge.svg)](https://translate.riot.im/engage/riot-android/?utm_source=widget) [![Android Matrix room #riot-android:matrix.org](https://img.shields.io/matrix/riot-android:matrix.org.svg?label=%23riot-android:matrix.org&logo=matrix&server_fqdn=matrix.org)](https://matrix.to/#/#riot-android:matrix.org) [![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=vector.android.riot&metric=alert_status)](https://sonarcloud.io/dashboard?id=vector.android.riot) [![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=vector.android.riot&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=vector.android.riot) [![Bugs](https://sonarcloud.io/api/project_badges/measure?project=vector.android.riot&metric=bugs)](https://sonarcloud.io/dashboard?id=vector.android.riot)
============

 Riot is an Android Matrix client. It is now deprecated and has been replaced by [Element Android](https://github.com/vector-im/element-android)

Important announcement
======================

### The core team is now only working on [Element Android](https://github.com/vector-im/element-android). **Element Android** is now published on the PlayStore as a replacement of Riot-Android. So the code from this project is not published by the core team to the PlayStore, and not published anymore on F-Droid store as well.

Contributing
============

Please contribute to [Element Android](https://github.com/vector-im/element-android) now!

Build instructions
==================

This client is a standard Android Studio project.

If you want to compile it in command line with gradle, go to the project directory:

Debug mode:

``` sh
./gradlew assembleDebug
```

Release mode:

``` sh
./gradlew assembleRelease
```

And it should build the project (you need to have the right Android SDKs)

Recompile the provided aar files until we have Gradle
======================================================

generate olm-sdk.aar
--------------------

``` sh
sh build_olm_lib.sh
```

generate matrix-sdk.aar
----------------------

``` sh
sh build_matrix_sdk_lib.sh
```

generate the other aar files
----------------------

``` sh
sh build_jitsi_libs.sh
```

compile the Matrix SDK with the riot-android project
----------------------

``` sh
sh set_debug_env.sh
```

Make your own flavour
=====================

Let says your application is named MyRiot : You have to create your own flavour.

Modify riot-android/vector/build.gradle
---------------------------------------

In "productFlavors" section, duplicate "app" group if you plan to use FCM or "appfdroid" if don't.

for example, with FCM, it would give

``` groovy
    appmyriot {
        applicationId "im.myriot"
        // use the version name
        versionCode rootProject.ext.versionCodeProp
        versionName rootProject.ext.versionNameProp
        buildConfigField "boolean", "ALLOW_FCM_USE", "true"
        buildConfigField "String", "SHORT_FLAVOR_DESCRIPTION", "\"F\""
        buildConfigField "String", "FLAVOR_DESCRIPTION", "\"FDroid\""
    }
```

- if you use FCM, duplicate appImplementation at the end of this file and replace appImplementation by appmyriotImplementation.
- if you don't, update the "if (!getGradle().getStartParameter().getTaskRequests().toString().contains("fdroid"))" to include your flavor.

Create your flavour directory
-----------------------------

- Copy riot-android/vector/src/app or appfroid if you use FCM or you don’t.
- Rename it to appmyriot.
- If you use FCM, you will need to generate your own google-services.json.

Customise your flavour
----------------------

- Open riot-android/vector/src/appmyriot/AndroidManifest.xml
- Change the application name to myRiot with "android:label="myRiot"" and "tools:replace="label"" in the application tag.
- Any other field can be customised by adding the resources in this directory classpath.
- Open Android Studio, select your flavour.
- Build and run the app : you made your first Riot app.

You will need to manage your own provider because "im.vector" is already used (look at VectorContentProvider to manage it).

Customise your application settings with a custom Google Play link
===================================================================

It is possible to set some default values to Riot with some extra parameters to the Google Play link.

- Use the https://developers.google.com/analytics/devguides/collection/android/v4/campaigns URL generator (at the bottom)
- Set "Campaign Content" with the extra parameters (e.g. is=http://my__is.org%26hs=http://my_hs.org). Please notice the usage of **%26** to escape the **&**
- Supported extra parameters:
   - is : identity server URL
   - hs : home server URL
- Generate the customised link
- The application may have to be installed from the Play store website (and not from the Play store application) for this feature to work properly.

FAQ
===

1. What is the minimum Android version supported?

    > the mininum SDK is 16 (Android 4.1)

2. Where the apk is generated?

	> Riot/build/outputs/apk
