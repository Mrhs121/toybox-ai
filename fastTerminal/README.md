# FastTerminal

FastTerminal is an Android SSH terminal focused on external keyboard and mouse use.

Current highlights:

- Bottom navigation with Home, Terminal, Settings, Keys, About panels.
- Home panel shows recent and saved connections as compact chips — tap to connect.
- Multi-tab terminal with `Ctrl+T/W/Left/Right` shortcuts.
- Physical `Esc` is always sent to the terminal and will not trigger Android back/exit.
- Mouse left drag selects terminal text; right click shows paste near the pointer.
- Multiple SSH connections can be saved locally, edited, deleted, and connected.
- SFTP file browser with upload, download, and folder management.
- Nerd Font (JetBrainsMono) bundled for icon rendering.
- SSH sessions stay alive when the app goes to the background through a foreground service.
- The terminal UI is optimized for tablet / desktop-style Android use.

Build requirements:

- JDK 17
- Android SDK with `platforms;android-35` and `build-tools;35.0.0`
- A `local.properties` file with `sdk.dir=/path/to/your/android/sdk`

Build debug APK:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) GRADLE_USER_HOME=$PWD/.gradle-home ./gradlew :app:assembleDebug
```

Install to device:

```bash
./android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug APK output:

- `app/build/outputs/apk/debug/app-debug.apk`
