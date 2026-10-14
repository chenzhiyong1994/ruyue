# Testing

Ruyue is an unfinished experimental prototype, with development paused as of September 30, 2026. This guide describes checks for the 1.0.0 / version code 13 source snapshot and independent forks. Test availability is not a promise of maintenance or proof of production readiness.

Use the supported toolchain in [development](development.md). Product behavior is defined by the stable [PA-001–PA-009 acceptance criteria](acceptance.md) and [API contract](api.md). Run the narrow checks relevant to a change; do not treat a successful build as proof that a two-person workflow works.

## Repository and backend checks

From the repository root:

```sh
python scripts/validate_project.py
python -m unittest discover -s tests -p 'test_*.py' -v
git diff --check
```

Use `python3` if needed. Python tests use temporary data rather than your development database. They cover service behavior, independent contract expectations, and real HTTP requests, including account and media isolation, role permissions, expiry, negotiation, reward settlement, idempotency, and persistence. Settlement coverage includes the giver's `give` action directly reaching `received`, legacy `offered` views, unchanged historical retry receipts, and rejection of new `receive` / `dispute` actions.

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
| `verify_pair.py` | Sets up synthetic accounts and a shared relationship through the API, then performs login and every agreement mutation through native UI: creation, acceptance, progress, photos, review, and final fulfillment by the giver. Both accounts read the resulting authoritative state; no recipient receipt step is performed. |
| `verify_resilience.py` | Requires a completed matching pair fixture and the same logged-in accounts. Checks 320dp / 1.5× text, a failed creation POST with retained input, one agreement created after recovery, and restart persistence; restores display settings and port forwarding. |
| `verify_ruyue_creation.py` | Uses the pair accounts for three-step previews and page/system back behavior, including narrow screens and large text. It does not publish or save the form; before/after business state is compared. |
| `verify_scenarios.py` | Runs last: logs out recognized synthetic sessions and creates fresh accounts through UI. Covers invitations, drafts, negotiation, cancellation, parent proxy provenance, and shared relationship scope across pages. API use is read-only observation. |

The relationship-scope path is **关系 → 查看范围 → 筛选关系 → 应用范围**. Tests retain checks that other relationships are excluded, not just that the selected record is visible. Creating an agreement uses the separate **所属关系** field. Pair fixtures carry `settlementMode=issuer_final`; fixtures from the old receipt-confirmation flow must not be reused for this contract.

Only the pair script supports `--resume`, and only with the original fixture, service, and devices. It refuses to silently repeat a stage with a potentially partial mutation. Other scripts require a fresh evidence directory. Diagnose a failed stage and inspect its saved state before starting another run; never replay writes blindly or clear a device to bypass a failure.

Fixtures contain synthetic passwords and session tokens. Do not commit or publish the raw evidence directory. Review screenshots and redact any sensitive content before sharing. Automated layout checks inspect visible text width, but screenshots still need visual review; the `layout-negative-control` action confirms that deliberately overflowing text is detected.

## Inspect the changed behavior

For changes affecting the current flow, check that:

- The five main visible states are **待接受 → 待完成 → 待验收 → 待兑现 → 已兑现**. Only the authorized giver can settle a pending reward; there is no second receipt action.
- Agreement details contain one **进度记录** section. Creation and acceptance are present, and notes or photos do not appear twice. The independent **留下说明** action is absent.
- Legacy `offered` rewards display as fulfilled without rewriting stored history, versions, timestamps, or previously successful idempotency receipts.
- Multiple relationships, long titles, cumulative progress, three-step back navigation, keyboard use, and narrow screens retain understandable boundaries and reachable actions.
- Login and true empty states remain usable. Use dedicated synthetic accounts to exercise them instead of erasing existing records. The bundled decorative illustrations are not interactive controls.

## Evidence limits

Run the checks on the checkout and environment you are evaluating, and report the actual commit, app and tool versions, assertions, screenshots, and uncovered paths. A command listed here or a passing unit test does not establish that the native device suites were run. Public screenshots use synthetic data and do not establish real-user acceptance, production safety, or coverage of every supported Android version.

Do not publish raw account fixtures or database contents as evidence. CI logs, when available for a specific commit, only establish the jobs they actually contain. No future CI execution, issue response, or test maintenance is guaranteed while development is paused.

For a bug report, include the smallest reproduction, expected and observed behavior, device/API and app version, and sanitized logs. For a vulnerability, follow [SECURITY.md](../SECURITY.md).
