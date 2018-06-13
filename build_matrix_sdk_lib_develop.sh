#!/bin/bash

echo "Save current dir"
currentDir=`pwd`

echo "up dir"
cd ..

echo "remove sdk folder"
rm -rf matrix-android-sdk

echo "clone the matrix-android-sdk repository, and checkout develop branch"
git clone -b develop https://github.com/matrix-org/matrix-android-sdk

cd matrix-android-sdk

echo "Build matrix sdk from source"
./gradlew clean assembleRelease

cd ${currentDir}

echo "Copy freshly built matrix sdk to the libs folder"
# Ensure the lib is updated by removing the previous one
rm vector/libs/matrix-sdk.aar

cp ../matrix-android-sdk/matrix-sdk/build/outputs/aar/matrix-sdk-release-*.aar vector/libs/matrix-sdk.aar
