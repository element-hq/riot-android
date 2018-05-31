#!/bin/bash

echo updir
cd ..

echo remove sdk folder
rm -rf matrix-android-sdk

echo clone the git folder
git clone -b master https://github.com/matrix-org/matrix-android-sdk 

cd matrix-android-sdk 

./gradlew clean assembleRelease

cd ../riot-android
cp ../matrix-android-sdk/matrix-sdk/build/outputs/aar/matrix-sdk-release-*.aar vector/libs/matrix-sdk.aar 

