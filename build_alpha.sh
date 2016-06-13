#!/bin/bash
rm *.apk
./gradlew clean build
#cp app/build/outputs/apk/app-alpha-matrixorg.apk ./alpha.apk
cp vector/build/outputs/apk/vector-vectorApp-matrixorg.apk ./vectorAppGooglePlay.apk
cp vector/build/outputs/apk/vector-vectorAppFDroid-matrixorg.apk ./vectorAppFDroid.apk