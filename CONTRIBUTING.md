# Important Announcement

The core team is now working mainly on [RiotX](https://github.com/vector-im/riotX-android). New contributions (PR, issues) are still welcome, but be aware that this codebase will be replaced in the future by the RiotX implementation.

# Contributing code to Matrix

Please read https://github.com/matrix-org/synapse/blob/master/CONTRIBUTING.rst

Android support can be found in this [![Android Matrix room #riot-android:matrix.org](https://img.shields.io/matrix/riot-android:matrix.org.svg?label=%23riot-android:matrix.org)](https://matrix.to/#/#riot-android:matrix.org) room.

# Specific rules for Matrix Android projects

## Android Studio settings

Please set the "hard wrap" setting of Android Studio to 160 chars, this is the setting we use internally to format the source code (Menu `Settings/Editor/Code Style` then `Hard wrap at`).

## Compilation

Riot Android uses by default the Matrix Android SDK library (file `matrix-sdk.aar`).
At each release, this library is updated.
Between two releases, the Riot code may not compile due to evolution of the library API.
To compile against the source of the Matrix Android library, please clone the project [AndroidMatrixSdk](https://github.com/matrix-org/matrix-android-sdk)
 and run the following command:

> ./compile_with_sdk_project.sh

## I want to help translating Riot

If you want to fix an issue with an English string, please submit a PR.
If you want to fix an issue in other languages, or add a missing translation, or even add a new language, please use [Weblate](https://translate.riot.im/projects/riot-android/).

## I want to submit a PR to fix an issue

Please check if a corresponding issue exists. If yes, please let us know in a comment that you're working on it.
If an issue does not exist yet, it may be relevant to open a new issue and let us know that you're implementing it.

### Kotlin

Please write every new classes in Kotlin. You can also convert existing Java classes (limited to classes impacted by the PR) to Kotlin (Android Studio has a tool to do this), but if you do so, please do atomic commit of the conversion, to ensure there is no other change, it will facilitate code review.
Also please check that everything works fine after Kotlin conversion, especially regarding nullity check.

### CHANGES.rst

Please add a line to the top of the file `CHANGES.rst` describing your change.

### Code quality

Make sure the following commands execute without any error:

> ./tools/check/check_code_quality.sh
> ./gradlew lintAppRelease

### Unit tests

Make sure the following commands execute without any error:

> ./gradlew testAppReleaseUnitTest

### Tests

Riot is currently supported on Android Jelly Bean (API 16+): please test your change on an Android device (or Android emulator) running with API 16. Many issues can happen (including crashes) on older devices.
Also, if possible, please test your change on a real device. Testing on Android emulator may not be sufficient.

### Internationalisation

When adding new string resources, please only add new entries in file `value/strings.xml`. Translations will be added later by the community of translators with a specific tool named [Weblate](https://translate.riot.im/projects/riot-android/).
Do not hesitate to use plurals when appropriate.

### Layout

When adding or editing layouts, make sure the layout will render correctly if device uses a RTL (Right To Left) language.
You can check this in the layout editor preview by selecting any RTL language (ex: Arabic).

Also please check that the colors are ok for all the current themes of Riot. Please use `?attr` instead of `@color` to reference colors in the layout. You can check this in the layout editor preview by selecting all the main themes (`AppTheme.Status`, `AppTheme.Dark`, etc.).

### Authors

Feel free to add an entry in file AUTHORS.rst

## Thanks

Thanks for contributing to Matrix projects!
