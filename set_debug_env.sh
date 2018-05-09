echo remove the SDK lib
rm -f vector/libs/matrix-sdk.aar
rm -rf vector/build

echo remove sdk folder
rm -rf ../matrix-android-sdk

echo clone the git folder
git clone -b develop https://github.com/matrix-org/matrix-android-sdk ../matrix-android-sdk

./compile_with_sdk_project.sh
