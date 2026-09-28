# Development

Ruyue contains a native Java Android client and a Python standard-library HTTP service backed by SQLite. Both participants connect to the same service. The [API contract](api.md) defines the shared state and permissions; the [architecture](architecture.md) describes storage and consistency.

## Toolchain

| Component | Requirement |
| --- | --- |
| Python | 3.11 or later; no third-party Python runtime dependencies |
| Java | JDK 17 |
| Android SDK | Platform 36, SDK Build-Tools, Platform-Tools; an emulator is optional for manual use |
| Android device | API 26 or later |
| Gradle | Use `android/gradlew` or `android/gradlew.bat`; wrapper version 8.14.5 |
| Android Gradle Plugin | 8.13.2, declared in `android/build.gradle` |

Set `JAVA_HOME` to your JDK 17 installation and `ANDROID_HOME` to your SDK. Add the SDK's `platform-tools` directory to `PATH` for `adb`. Install the required SDK packages and accept their licenses through Android Studio or the Android SDK tools. The initial Gradle build needs network access to download the wrapper distribution and dependencies. Use offline mode only after they are cached.

These commands assume the repository root and a `python` command for Python 3.11+. Use `python3` instead where appropriate. An optional virtual environment is fine, but there is no package installation step for the backend.

## Run the backend

```sh
python -m server --host 127.0.0.1 --port 8765 --database .local/pact/pact.sqlite3
```

The service creates its database directory as needed and persists state across restarts. It listens only on loopback by default. `GET http://127.0.0.1:8765/health` should return `{"status":"ok","apiVersion":1}`. Stop it with Ctrl+C.

The database includes account, relationship, agreement, and media data. Keep `.local/` private and ignored. Use a different database path for automated tests or disposable demonstrations. The service refuses unknown SQLite schema versions instead of silently rewriting them; future migrations need their own tested recovery path.

Command-line options are available with:

```sh
python -m server --help
```

The example environment file is documentation, not an automatically loaded configuration file. Server host, port, and database settings come from the command line.

## Build Android

The default Windows helper runs Debug unit tests, the Debug build, and lint:

```powershell
.\scripts\android.ps1
```

It accepts `-JavaHome`, `-AndroidHome`, `-Tasks`, and `-Offline`. If your shell already exports `JAVA_HOME` and `ANDROID_HOME`, no path arguments are needed. You can also call the wrapper directly:

```powershell
.\android\gradlew.bat -p android :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

On macOS or Linux:

```sh
sh android/gradlew -p android :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Use `sh` to run the wrapper even if the executable permission was not preserved by your checkout. Do not substitute a globally installed Gradle version.

The result is `android/app/build/outputs/apk/debug/app-debug.apk`. The current source version is 1.2.3 / version code 6. The namespace is `cn.zhiya.pact`; Debug uses the separate application ID `cn.zhiya.pact.preview`. Build outputs and `android/local.properties` stay out of Git.

## Connect the app locally

List devices, then use an explicit device serial for installation and port forwarding:

```sh
adb devices
adb -s "<device-serial>" install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s "<device-serial>" reverse tcp:8765 tcp:8765
```

In the app, open **开发连接设置** and enter `http://127.0.0.1:8765`. This entry is available on the login screen and, in Debug builds, through account settings. Register a synthetic account. For shared mode, use a different account on a second device and join the invitation from the **我们** page.

`adb reverse` connects a device port to the computer running `adb`; it works only while that local connection exists. The standard Android emulator can alternatively use `http://10.0.2.2:8765` to reach the host. Debug cleartext exceptions are restricted to local addresses. This is not an internet deployment procedure.

The visible navigation labels are **日常、约定、奖励、我们**. Their accessibility descriptions remain **首页、约定、奖励、关系** for UI automation. Relationship scope is selected through **我们 → 每一段我们 → 查看范围 → 应用范围** when there is more than one relationship or an existing scope selection. Canceling that dialog does not apply the tentative choice.

Do not uninstall an existing app or clear its data merely to make an installation or test succeed. For scripted interaction tests, use the dedicated environments described in [testing](testing.md).

## Configuration and release boundary

| Build environment variable | Purpose |
| --- | --- |
| `PACT_API_BASE_URL` | API origin embedded in the app; required to be HTTPS for Release builds |
| `ZHIYA_SIGNING_STORE_FILE` | Path to a production signing keystore outside the repository |
| `ZHIYA_SIGNING_STORE_PASSWORD` | Keystore password supplied to the build process |
| `ZHIYA_SIGNING_KEY_ALIAS` | Signing key alias |
| `ZHIYA_SIGNING_KEY_PASSWORD` | Signing key password supplied to the build process |

The signing variable names match the existing Gradle configuration. They are not needed for the Debug preview. Never place real values in a committed file or public build log. [.env.example](../.env.example) lists the supported names without values.

Release builds reject an absent or invalid HTTPS endpoint and missing signing configuration. Those build checks do not make the backend production-ready. Hosted operation still requires a reviewed deployment, account recovery, backup and restore procedures, privacy and retention decisions, and operational support. The repository supplies no production API deployment or managed account service. See [SECURITY.md](../SECURITY.md).

The [project website](https://chenzhiyong1994.github.io/ruyue/) is a static showcase, separate from the backend. No APK release is distributed as part of the initial source publication.

## Repository map

- `android/`: app, native UI instrumentation, and Java unit tests.
- `server/`: HTTP adapter, domain service, authentication, and SQLite persistence.
- `tests/`: service, contract, and HTTP tests.
- `scripts/`: local validation, native scenarios, and optional local packaging.
- `docs/`: requirements, API, architecture, usage, and verification instructions.
- `site/`: public project page and screenshots containing synthetic data.

Follow [CONTRIBUTING.md](../CONTRIBUTING.md) and [testing](testing.md) for changes. The public source does not include runtime databases, credential-bearing fixtures, private planning records, or internal iteration reports.
