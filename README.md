Vector
=======

Vector is an Android Matrix client.

It uses https://github.com/matrix-org/matrix-android-sdk


Build instructions
==================

This client is a standard android studio project.

If you want to compile it in command line with gradle, go to the project directory:

Debug mode:

`./gradlew assembleDebug`

Release mode:

`./gradlew assembleRelease`

And it should build the project (you need to have the right android SDKs)

FAQ
===

1. What is the minimum android version supported?

    > the mininum SDK is 16 (android 4.1)

2. Where the apk is generated?

	> vector/build/outputs/apk