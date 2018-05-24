#!/bin/bash
rm *.apk
./gradlew clean 

./gradlew lintAppRelease assembleAppMatrixorg
./gradlew lintAppfdroidRelease assembleAppfdroidMatrixorg

#cp app/build/outputs/apk/app-alpha-matrixorg.apk ./alpha.apk
cp vector/build/outputs/apk/app/matrixorg/vector-app-matrixorg.apk ./riotGooglePlay.apk
cp vector/build/outputs/apk/appfdroid/matrixorg/vector-appfdroid-matrixorg.apk ./riotFDroid.apk