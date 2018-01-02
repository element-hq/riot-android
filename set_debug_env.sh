echo remove the SDK lib
rm -f vector/libs/matrix-sdk.aar
rm -rf vector/build

echo remove sdk folder
rm -rf ../matrix-android-sdk

echo clone the git folder
git clone -b develop https://github.com/matrix-org/matrix-android-sdk ../matrix-android-sdk

echo replace step 1
sed -i '' -e 's/\/\/include/include/' settings.gradle
sed -i '' -e 's/\/\/project/project/' settings.gradle

echo replace step 2
sed -i '' -e "s/compile(name: 'matrix/\/\/compile(name: 'matrix/" vector/build.gradle
sed -i '' -e "s/\/\/compile project(':matrix-/compile project(':matrix-/" vector/build.gradle
