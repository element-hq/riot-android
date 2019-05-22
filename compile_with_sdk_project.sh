#!/usr/bin/env bash

# Use -i.bak because sed command is not the same depending on OS
# See https://stackoverflow.com/questions/5694228/sed-in-place-flag-that-works-both-on-mac-bsd-and-linux

echo "replace step 1"
sed -i.bak "s/^\/\/include ':matrix-sdk/include ':matrix-sdk/" ./settings.gradle || true
sed -i.bak "s/^\/\/project(':matrix-sdk/project(':matrix-sdk/" ./settings.gradle || true

echo "replace step 2"
sed -i.bak "s/^    implementation 'com.github.matrix-org:matrix-android-sdk/    \/\/implementation 'com.github.matrix-org:matrix-android-sdk/" ./vector/build.gradle || true
sed -i.bak "s/^    \/\/implementation project(':matrix-/    implementation project(':matrix-/" ./vector/build.gradle || true

# Delete the created files
rm ./settings.gradle.bak
rm ./vector/build.gradle.bak

echo "please sync project with gradle files"
