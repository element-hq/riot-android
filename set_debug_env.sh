echo remove the SDK lib
rm -f vector/libs/matrix-sdk.aar
rm -rf vector/build

echo remove sdk folder
rm -rf ../matrix-android-sdk

echo clone the git folder
git clone -b develop https://github.com/matrix-org/matrix-android-sdk ../matrix-android-sdk

echo replace step 1
sed -i '' -e 's/\/\/include/include/' settings.gradle || true
sed -i '' -e 's/\/\/project/project/' settings.gradle || true

echo replace step 2
sed -i '' -e "s/implementation(name: 'matrix/\/\/implementation(name: 'matrix/" vector/build.gradle || true
sed -i '' -e "s/\/\/implementation project(':matrix-/implementation project(':matrix-/" vector/build.gradle || true
