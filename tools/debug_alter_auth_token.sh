#!/usr/bin/env bash

adb shell am broadcast -a im.vector.receiver.DEBUG_ACTION_ALTER_AUTH_TOKEN
