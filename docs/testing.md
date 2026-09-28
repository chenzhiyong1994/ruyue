# Testing

Use the supported toolchain in [development](development.md). Product behavior is defined by the stable [PA-001–PA-009 acceptance criteria](acceptance.md) and [API contract](api.md). Run the narrow checks relevant to a change; do not treat a successful build as proof that a two-person workflow works.

## Repository and backend checks

From the repository root:

```sh
python scripts/validate_project.py
python -m unittest discover -s tests -p 'test_*.py' -v
git diff --check
```

Use `python3` if needed. Python tests use temporary data rather than your development database. They cover service behavior, independent contract expectations, and real HTTP requests, including account and media isolation, role permissions, expiry, negotiation, reward settlement, idempotency, and persistence.

The repository validator checks public documentation and asset references. It does not prove the absence of credentials in Git history or replace a security review.

## Android unit tests, build, and lint

Windows PowerShell:

```powershell
.\scripts\android.ps1
```

macOS / Linux:

```sh
sh android/gradlew -p android :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Lint warnings are treated as errors by the project. Review the failure instead of suppressing a check simply to make a build pass. Relevant output is under `android/app/build/` and remains ignored.

## Native interaction checks

These scripts drive actual Android screens. They are intended for two dedicated test AVDs whose names start with `pact_`, not a personal phone or an emulator containing user data. The scripts enforce this isolation before setup writes. Use a separate local service database and an empty evidence directory for each run.

Build the instrumentation APK alongside the app:

```powershell
.\android\gradlew.bat -p android :app:assembleDebug :app:assembleDebugAndroidTest
```

```sh
sh android/gradlew -p android :app:assembleDebug :app:assembleDebugAndroidTest
```

Install both APKs on each explicitly selected test device:

```sh
adb -s "<test-device-serial>" install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s "<test-device-serial>" install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Start a separate backend, using a new database filename if this path already has test data:

```sh
python -m server --host 127.0.0.1 --port 8766 --database .local/pact/interaction-tests.sqlite3
```

Replace both serial placeholders and `<path-to-adb>` below. Pass the actual `adb` executable path explicitly on every platform. The scripts configure local `adb reverse` connections and write reports under ignored `artifacts/pact/` directories.

```sh
python scripts/verify_pair.py --serial-a "<serial-a>" --serial-b "<serial-b>" --adb "<path-to-adb>" --api http://127.0.0.1:8766 --evidence-dir artifacts/pact/local-check/pair
python scripts/verify_resilience.py --fixture artifacts/pact/local-check/pair/fixture.json --adb "<path-to-adb>" --evidence-dir artifacts/pact/local-check/resilience
python scripts/verify_ruyue_creation.py --fixture artifacts/pact/local-check/pair/fixture.json --adb "<path-to-adb>" --evidence-dir artifacts/pact/local-check/creation
python scripts/verify_scenarios.py --serial-a "<serial-a>" --serial-b "<serial-b>" --adb "<path-to-adb>" --api http://127.0.0.1:8766 --evidence-dir artifacts/pact/local-check/scenarios
```

Run them in that order:

| Script | What it checks |
| --- | --- |
| `verify_pair.py` | Sets up synthetic accounts and a shared relationship through the API, then performs login and every agreement mutation through native UI: creation, acceptance, progress, photos, review, give, dispute, and receipt. Both accounts read the resulting authoritative state. |
| `verify_resilience.py` | Requires a completed matching pair fixture and the same logged-in accounts. Checks 320dp / 1.5× text, failed POST behavior, recovery, and restart persistence; restores display settings and port forwarding. |
| `verify_ruyue_creation.py` | Uses the pair accounts for three-step previews and page/system back behavior, including narrow screens and large text. It does not publish or save the form; before/after business state is compared. |
| `verify_scenarios.py` | Runs last: logs out recognized synthetic sessions and creates fresh accounts through UI. Covers invitations, drafts, negotiation, cancellation, parent proxy provenance, and shared relationship scope across pages. API use is read-only observation. |

The relationship-scope path is **关系 → 查看范围 → 筛选关系 → 应用范围**. Tests retain checks that other relationships are excluded, not just that the selected record is visible. Creating an agreement uses the separate **所属关系** field.

Only the pair script supports `--resume`, and only with the original fixture, service, and devices. It refuses to silently repeat a stage with a potentially partial mutation. Other scripts require a fresh evidence directory. Diagnose a failed stage and inspect its saved state before starting another run; never replay writes blindly or clear a device to bypass a failure.

Fixtures contain synthetic passwords and session tokens. Do not commit or publish the raw evidence directory. Review screenshots and redact any sensitive content before sharing. Automated layout checks inspect visible text width, but screenshots still need visual review; the `layout-negative-control` action confirms that deliberately overflowing text is detected.

## Existing evidence and its limits

The application source baseline has prior local evidence for 33 Python tests, five Android unit tests, Debug builds and lint, and native workflows on two isolated API 36 emulators. Additional checks covered three-step creation and back navigation, 320dp width with 1.5× text, offline failure behavior, and relationship scope. These are synthetic local results, not real-user research, production acceptance, or proof that every supported Android version has been exercised.

Raw account fixtures and internal execution reports are not part of this public source distribution. Re-run the commands for the checkout and environment you are evaluating, and report the actual commit, tool versions, checks, and limitations. In particular, static or unit checks of the public packaging do not imply that device scripts were rerun.

For a bug report, include the smallest reproduction, expected and observed behavior, device/API and app version, and sanitized logs. For a vulnerability, follow [SECURITY.md](../SECURITY.md).
