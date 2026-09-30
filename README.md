# Ruyue · 如约

[简体中文](README.zh-CN.md) · [Project page](https://chenzhiyong1994.github.io/ruyue/) · [Development](docs/development.md) · [Testing](docs/testing.md)

**An unfinished, experimental Android prototype. Development is paused as of September 30, 2026.** This is a source-code snapshot for learning, experimentation, and independent forks. There is no commitment to continued development, maintenance, issue or pull-request responses, security fixes, or production readiness.

Ruyue helps people who know each other manage private agreements: define a goal and a reward, accept the terms, record progress, review completion, and mark the reward fulfilled. The interface is currently Simplified Chinese. The source snapshot is **1.4.3 / version code 12**.

The repository includes the Android client and a backend you run locally. It provides no hosted account service or downloadable APK release; build the development preview from source.

## What it does

```text
Awaiting acceptance → In progress → Awaiting review → Reward pending → Fulfilled
待接受              待完成         待验收             待兑现           已兑现
```

The reward giver marks the reward fulfilled to close the agreement. The recipient does not confirm receipt a second time. Task completion and reward fulfillment remain separate states.

- **Private relationships:** invitations connect two accounts, with records and permissions scoped to each relationship.
- **Clear terms:** one-time or cumulative goals, optional deadlines, a reward, and a three-step creation flow. Changes to active terms require agreement.
- **One progress history:** creation, acceptance, progress notes and photos, submission, review, and fulfillment appear together. There is no separate “leave a note” action.
- **Explicit parent proxy mode:** a parent can record for a child without an account. The history identifies the actual actor and proxy source.
- **Shared state:** the service controls permissions, versions, time, and transitions. Ending a relationship does not erase an earned reward.

There is no stranger marketplace, payment processing, points store, automatic punishment, background push service, or AI adjudication.

## Screenshots

Native Android screens using synthetic demonstration data.

| Daily | Agreements | Rewards |
| --- | --- | --- |
| ![Daily view grouped by relationship](site/assets/home.png) | ![Agreements grouped by relationship and status](site/assets/agreements.png) | ![Rewards and fulfillment status](site/assets/rewards.png) |

The two decorative still-life illustrations were generated with AI and are bundled locally. Their source, prompts, and usage are documented in [Illustration provenance](docs/design/ruyue-illustrations.md).

## Run locally

Prepare **Python 3.11+**, **JDK 17**, and an Android SDK with **Platform 36**, SDK Build-Tools, and Platform-Tools. Set `JAVA_HOME` and `ANDROID_HOME`, and add `adb` to `PATH`. The app supports **Android 8.0 / API 26+**. Use the included Gradle Wrapper; the first build requires internet access to download dependencies.

```sh
git clone https://github.com/chenzhiyong1994/ruyue.git
cd ruyue
python -m server --host 127.0.0.1 --port 8765 --database .local/pact/pact.sqlite3
```

Use `python3` if appropriate for your system. The backend uses only the Python standard library. Leave it running and build in another terminal from the repository root.

**Windows PowerShell:**

```powershell
.\scripts\android.ps1
```

**macOS / Linux:**

```sh
sh android/gradlew -p android :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Install the generated Debug APK on an explicitly selected test device. Replace `<device-serial>` with its value from `adb devices`:

```sh
adb -s "<device-serial>" install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s "<device-serial>" reverse tcp:8765 tcp:8765
```

Open **开发连接设置** (development connection settings), enter `http://127.0.0.1:8765`, and register a synthetic test account. Use a second device and a different account connected to the same backend to try the shared workflow. The Debug package is `cn.zhiya.pact.preview`. Do not uninstall or clear an existing app to force an installation.

## Architecture and limits

The native Java client talks to a Python standard-library HTTP service, which stores accounts, relationships, agreements, events, and media in SQLite. The server is authoritative; both participants need the same backend. The [project page](https://chenzhiyong1994.github.io/ruyue/) is a static showcase and does not run the API.

This prototype does not provide production hosting, account recovery, managed backups, operational support, or a maintained security support window. Debug signing and a successful local build are not production-readiness guarantees. Use synthetic data for evaluation. See [Security](SECURITY.md) before considering any deployment beyond local testing.

| Document | Contents |
| --- | --- |
| [Development](docs/development.md) | Toolchain, local service, Android build and configuration |
| [Testing](docs/testing.md) | Unit, HTTP, Android and isolated native checks |
| [User guide](docs/user-guide.md) | App walkthrough, in Chinese |
| [Product scope](docs/prd.md) | Goals and product boundaries, in Chinese |
| [API contract](docs/api.md) | Permissions and state transitions, in Chinese |
| [Architecture](docs/architecture.md) | Storage and consistency, in Chinese |
| [Acceptance criteria](docs/acceptance.md) | PA-001–PA-009 checks, in Chinese |

## Forks and license

Independent forks are welcome. Issues and pull requests may remain unanswered while development is paused; submitting one does not create a maintenance commitment. See [Contributing](CONTRIBUTING.md).

Ruyue is licensed under the [MIT License](LICENSE), without warranty. Third-party components retain their own licenses; see [NOTICE](NOTICE).
