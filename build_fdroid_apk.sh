
rm -rf vector/libs
mkdir vector/libs

sh build_jitsi_libs.sh
sh build_matrix_sdk_lib.sh
sh build_olm_lib.sh

./gradlew lintAppfdroidRelease assembleAppfdroidRelease

