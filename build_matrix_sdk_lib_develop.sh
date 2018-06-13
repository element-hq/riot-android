#!/bin/bash

echo updir
cd ..

echo remove sdk folder
rm -rf matrix-android-sdk

echo clone the git folder
git clone -b develop https://github.com/matrix-org/matrix-android-sdk

cd matrix-android-sdk 

./gradlew clean assembleRelease

cd ../riot-android

# Ensure the lib is updated by removing the previous one
rm vector/libs/matrix-sdk.aar

cp ../matrix-android-sdk/matrix-sdk/build/outputs/aar/matrix-sdk-release-*.aar vector/libs/matrix-sdk.aar 

