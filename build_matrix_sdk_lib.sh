#!/bin/bash

#
# Copyright 2018 New Vector Ltd
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# exit on any error
set -e

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 GIT_BRANCH" >&2
  exit 1
fi

# which branch to build?
branch=$1

if [ -z "$branch" ]; then
   echo "Please specify the branch to build as a parameter"
   exit 1
fi

echo ${branch}

echo "Save current dir"
currentDir=`pwd`

echo "up dir"
cd ..

echo "remove sdk folder"
rm -rf matrix-android-sdk

echo "clone the matrix-android-sdk repository, and checkout ${branch} branch"
git clone -b ${branch} https://github.com/matrix-org/matrix-android-sdk

cd matrix-android-sdk

echo "Build matrix sdk from source"
./gradlew clean assembleRelease

cd ${currentDir}

echo "Copy freshly built matrix sdk to the libs folder"
# Ensure the lib is updated by removing the previous one
rm vector/libs/matrix-sdk.aar

cp ../matrix-android-sdk/matrix-sdk/build/outputs/aar/matrix-sdk-release-*.aar vector/libs/matrix-sdk.aar
