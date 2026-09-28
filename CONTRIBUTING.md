# Contributing to Ruyue

Thank you for helping improve Ruyue. Issues and pull requests may be written in English or Simplified Chinese. The app currently has a Chinese interface; the product and API specifications are in Chinese.

## Before changing a workflow

Read the [product scope](docs/prd.md), [API contract](docs/api.md), and [acceptance criteria](docs/acceptance.md). Keep a change focused on a concrete problem. For a new product capability or a breaking contract change, describe the proposal in an issue before implementing it.

Preserve these rules:

- Accepting an agreement, approving completion, giving a reward, and confirming receipt are distinct actions.
- Relationship members cannot act as the other account. Parent proxy actions must remain explicitly attributed.
- Changes to active terms need mutual agreement. Ending a relationship cannot erase an earned reward.
- The service is authoritative for permissions, state, time, and versions. A failed request must not appear successful in the UI.
- Keep stable contract and acceptance IDs. Update both client and server documentation when changing a shared contract.
- Keep relationship scope in the existing “我们” page entry; do not add a permanent global relationship bar above every page.

Stranger marketplaces, payments, automatic punishment, background push services, and AI adjudication are outside the current preview scope.

## Development and checks

Follow [development setup](docs/development.md) and [testing](docs/testing.md). Use the checked-in Gradle Wrapper and the supported toolchain. Do not commit generated builds, SDK paths, databases, credentials, signing files, or device fixtures.

Run the checks relevant to your change:

```sh
python scripts/validate_project.py
python -m unittest discover -s tests -p 'test_*.py' -v
git diff --check
```

For Android changes, run Debug unit tests, build, and lint using the commands in the development guide. Verify affected native interactions and inspect screenshots where layout changed. A small documentation edit does not need a device run. Do not remove assertions or relax thresholds to hide a failure.

Use synthetic accounts and dedicated test data. Never clear, uninstall, or reset someone else's app data or emulator to make a test pass. Reports under `artifacts/` can contain test credentials and are intentionally ignored; share only reviewed, redacted excerpts.

## Pull requests

Explain the problem, the resulting behavior, and the checks you actually ran. Include sanitized screenshots for visual changes and state any unverified behavior. Keep unrelated cleanup and generated artifacts out of the change. Public issues and pull requests must not contain real family records, child photos, passwords, session tokens, or private database contents.

Report vulnerabilities through [SECURITY.md](SECURITY.md), not a public issue containing exploit details.

Contributions are provided under the project's [MIT License](LICENSE). Keep third-party notices and licenses intact; the Gradle Wrapper has its own license listed in [NOTICE](NOTICE).
