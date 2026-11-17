# TV Emulator

Android app prototype. Pick an installed app from the library, including apps
that advertise an Android TV launcher entry. The app keeps the selected
component and opens a 16:9 preview screen.

Version 0.1 does not include an Android TV system image or a guest runtime.
The preview explains this and disables the remote. Selecting a title does not
launch it in the phone's regular Android session.

## Build

Use JDK 17, Gradle 8.9, and Android SDK Platform 35.

```sh
gradle :app:assembleDebug
```

GitHub Actions uses two files supplied separately. Commit both under
`.github/workflows/`, then upload the source ZIP at the repository root.
The build workflow runs the reusable Apply job first. It checks out Apply's
resulting commit and publishes the debug APK as an Actions artifact.
