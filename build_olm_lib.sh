cd ..
rm -rf olm
git clone http://git.matrix.org/git/olm.git/
cd olm/android
echo ndk.dir=$ANDROID_HOME/ndk-bundle > local.properties
./gradlew assembleRelease

cd ../../riot-android
cp ../olm/android/olm-sdk/build/outputs/aar/olm-sdk-release-2.2.2.aar  vector/libs/olm-sdk.aar


