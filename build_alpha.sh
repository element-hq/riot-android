#!/bin/bash
rm *.apk
./gradlew clean build
#cp app/build/outputs/apk/app-alpha-matrixorg.apk ./alpha.apk
cp vector/build/outputs/apk/vector-app-matrixorg.apk ./vectorAppGooglePlay.apk
cp vector/build/outputs/apk/vector-appFDroid-matrixorg.apk ./vectorAppFDroid.apk