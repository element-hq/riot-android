Riot
=======

 Riot is an Android Matrix client.
  		  
 [<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" alt="Get it on Google Play" height="60">](https://play.google.com/store/apps/details?id=im.vector.alpha&hl=en&utm_source=global_co&utm_medium=prtnr&utm_content=Mar2515&utm_campaign=PartBadge&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1)	
   
 [<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="60">](https://f-droid.org/app/im.vector.alpha)
 

Build instructions
==================

This client is a standard android studio project.

If you want to compile it in command line with gradle, go to the project directory:

Debug mode:

`./gradlew assembleDebug`

Release mode:

`./gradlew assembleRelease`

And it should build the project (you need to have the right android SDKs)

Jitsi integration
==================
How to build JitsiMeet libs:
- clone https://github.com/jitsi/jitsi-meet
- build jitsi-meet following instruction at https://github.com/jitsi/jitsi-meet#building-the-sources
- build it specifically for android using https://github.com/jitsi/jitsi-meet/blob/master/doc/mobile.md#android
- generate the bundle file
    react-native bundle --platform android --dev false --entry-file index.android.js --bundle-output android/app/src/main/assets/index.android.bundle --assets-dest android/app/src/main/res/
- copy "index.android.bundle" in your project assets folder
- copy fonts/jitsi.ttf into <your_project>/assets/font
- copy node_modules/react-native-vector-icons/Fonts/* into <your_project>/assets/font
- build the jitsi android project (gradlew assembleRelease in the "android" folder)
- copy the react-... aar to the libs folder (see build.gradle to have a list of them)


Make your own flavour
=====================

Let says your application is named MyRiot : You have to create your own flavour.

- Modify riot-android/vector/build.gradle

In "productFlavors" section, duplicate "app" group if you plan to use GCM/FCM or "appfdroid" if don't.

for example, with GCM, it would give
appmyriot {
    applicationId "im.myriot"
    // use the version name
    versionCode rootProject.ext.versionCodeProp
    versionName rootProject.ext.versionNameProp
    resValue "string", "allow_gcm_use", "true"
    resValue "string", "allow_ga_use", "true"
    resValue "string", "short_flavor_description", "G"
    resValue "string", "flavor_description", "GooglePlay"
}

if you use GCM, duplicate appCompile at the end of this file and replace appCompile by appmyriotCompile.
if you don't, update the "if (!getGradle().getStartParameter().getTaskRequests().toString().contains("fdroid"))" to include your flavor.

- Create your flavour directory

Copy riot-android/vector/src/app or appfroid if you use GCM or you don’t.
Rename it to appmyriot.
If you use GCM, you will need to generate your own google-services.json.

- Customise your flavour

Open riot-android/vector/src/appmyriot/AndroidManifest.xml
Comment the provider section.
Change the application name to myRiot with "android:label="myRiot""
Any other field can be customised by adding the resources in this directory classpath.
Open Android studio, select your flavour.
Build and run the app : you made your first Riot app.

You will need to manage your own provider because "im.vector" is already used (look at VectorContentProvider to manage it).

FAQ
===

1. What is the minimum android version supported?

    > the mininum SDK is 16 (android 4.1)

2. Where the apk is generated?

	> Riot/build/outputs/apk
