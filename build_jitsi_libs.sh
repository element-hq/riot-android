cd ..
rm -rf jitsi-meet
git clone -b master https://github.com/jitsi/jitsi-meet
cd jitsi-meet
npm install
make
cd android
./gradlew assembleRelease
cd ..

react-native bundle --platform android --dev false --entry-file index.android.js --bundle-output index.android.bundle --assets-dest android/app/src/main/res/

cd ../riot-android

cp ../jitsi-meet/android/sdk/build/outputs/aar/sdk-release.aar vector/libs/jitsi-sdk.aar
cp ../jitsi-meet/node_modules/react-native-background-timer/android/build/outputs/aar/react-native-background-timer-release.aar vector/libs/react-native-background-timer.aar
cp ../jitsi-meet/node_modules/react-native-fetch-blob/android/build/outputs/aar/react-native-fetch-blob-release.aar vector/libs/react-native-fetch-blob.aar
cp ../jitsi-meet/node_modules/react-native-immersive/android/build/outputs/aar/react-native-immersive-release.aar vector/libs/react-native-immersive.aar
cp ../jitsi-meet/node_modules/react-native-keep-awake/android/build/outputs/aar/react-native-keep-awake-release.aar vector/libs/react-native-keep-awake.aar 
cp ../jitsi-meet/node_modules/react-native-vector-icons/android/build/outputs/aar/react-native-vector-icons-release.aar vector/libs/react-native-vector-icons.aar
cp ../jitsi-meet/node_modules/react-native-webrtc/android/build/outputs/aar/react-native-webrtc-release.aar vector/libs/react-native-webrtc.aar
cp ../jitsi-meet/node_modules/react-native-locale-detector/android/build/outputs/aar/react-native-locale-detector-release.aar vector/libs/react-native-locale-detector.aar
cp ../jitsi-meet/node_modules/react-native/android/com/facebook/react/react-native/0.50.4/react-native-0.50.4.aar vector/libs/react-native.aar

cp ../jitsi-meet/node_modules/react-native-vector-icons/Fonts/*.ttf vector/src/main/assets/fonts/
cp ../jitsi-meet/index.android.bundle vector/src/main/assets/

