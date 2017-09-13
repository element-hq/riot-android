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

FAQ
===

1. What is the minimum android version supported?

    > the mininum SDK is 16 (android 4.1)

2. Where the apk is generated?

	> Riot/build/outputs/apk
