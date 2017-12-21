#!/bin/bash
cd ..

echo remove sdk folder
rm -rf matrix-android-sdk

echo clone the git folder
git clone -b master https://github.com/matrix-org/matrix-android-sdk 

cd matrix-android-sdk
./gradlew clean assembleRelease

cd ../riot-android
rm -f vector/libs/matrix-sdk.aar 
cp ../matrix-android-sdk/matrix-sdk/build/outputs/aar/matrix-sdk-release-*.aar vector/libs/matrix-sdk.aar 

rm *.apk
./gradlew clean 

./gradlew lintAppRelease assembleAppMatrixorg
./gradlew lintAppfdroidRelease assembleAppfdroidMatrixorg

#cp app/build/outputs/apk/app-alpha-matrixorg.apk ./alpha.apk
cp vector/build/outputs/apk/vector-app-matrixorg.apk ./riotGooglePlay.apk
cp vector/build/outputs/apk/vector-appfdroid-matrixorg.apk ./riotFDroid.apk