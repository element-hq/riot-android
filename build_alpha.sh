#!/bin/bash
./gradlew clean build
#cp app/build/outputs/apk/app-alpha-matrixorg.apk ./alpha.apk
cp vector/build/outputs/apk/vector-alpha-matrixorg.apk ./alphaGooglePlay.apk
cp vector/build/outputs/apk/vector-alphaFDroid-matrixorg.apk ./alphaFDroid.apk