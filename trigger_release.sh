#!/bin/bash

echo updir
cd ..

echo remove sdk folder
rm -rf matrix-android-sdk_build_release

echo clone the git folder
git clone -b master https://github.com/matrix-org/matrix-android-sdk matrix-android-sdk_build_release

cd matrix-android-sdk_build_release

./gradlew clean assembleRelease
./gradlew releaseDocs
git commit -m "auto-generated docs from script" docs
git push --porcelain --progress --recurse-submodules=check origin refs/heads/master:refs/heads/master

cd ../riot-android
rm -f vector/libs/matrix-sdk.aar 
cp ../matrix-android-sdk_build_release/matrix-sdk/build/outputs/aar/matrix-sdk-release-*.aar vector/libs/matrix-sdk.aar 

git commit -m "auto-generated from script" vector/libs/matrix-sdk.aar 
git push --porcelain --progress --recurse-submodules=check origin refs/heads/master:refs/heads/master

rm -rf matrix-android-sdk_build_release

