# fastTerminal

`fastTerminal` is a lightweight Android SSH terminal app focused on external keyboard and mouse workflows.

## Features

- SSH password login with interactive shell over `xterm-256color`
- Physical `Esc` is always sent to the terminal instead of closing the app
- External mouse left-button drag selects terminal text like a desktop terminal
- External mouse right-button opens a paste menu near the click position
- Touch and hardware keyboard font scaling support
- Connection form collapses after connect to keep the terminal area clean

## Project Layout

- `app/`: Android application module
- `gradle/`, `gradlew`, `gradlew.bat`: Gradle wrapper

## Build Requirements

- macOS, Linux, or Windows
- JDK 17
- Android SDK with:
  - `platform-tools`
  - `platforms;android-35`
  - `build-tools;35.0.0`

## Build Guide

1. Create `local.properties` in this directory:

```properties
sdk.dir=/absolute/path/to/your/Android/sdk
```

2. Build the debug APK:

```bash
cd fastTerminal
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug
```

3. The APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

4. Optional: install it with `adb`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Prebuilt APK

If you just want to try the app, use the bundled APK in this directory:

- `fastTerminal-debug.apk`

## Notes

- Current authentication is password-only.
- Host key verification still needs hardening before production use.
- `local.properties` is intentionally ignored and should stay local.
