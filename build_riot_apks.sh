#!/bin/bash

# Clean all existing APK
rm *.apk

# Clean
./gradlew clean

# Run Lint and build the APK for the PlayStore
./gradlew lintAppMatrixorg assembleAppMatrixorg --stacktrace

# Run Lint and build the APK for FDroid
./gradlew lintAppfdroidMatrixorg assembleAppfdroidMatrixorg --stacktrace

# Copy (and rename) the built APKs to the root folder
cp vector/build/outputs/apk/app/matrixorg/vector-app-matrixorg.apk ./riotGooglePlay.apk
cp vector/build/outputs/apk/appfdroid/matrixorg/vector-appfdroid-matrixorg.apk ./riotFDroid.apk