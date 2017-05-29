#!/bin/bash
rm *.apk
./gradlew clean 

./gradlew lintAppRelease assembleAppMatrixorg
./gradlew lintAppfdroidRelease assembleAppfdroidMatrixorg

#cp app/build/outputs/apk/app-alpha-matrixorg.apk ./alpha.apk
cp vector/build/outputs/apk/vector-app-matrixorg.apk ./riotGooglePlay.apk
cp vector/build/outputs/apk/vector-appfdroid-matrixorg.apk ./riotFDroid.apk