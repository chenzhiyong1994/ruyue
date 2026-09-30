# Contributing to Ruyue

Ruyue is an unfinished experimental prototype. Development is paused as of September 30, 2026. Independent forks are welcome, but there is no commitment to review contributions, answer issues, ship releases, or resume development.

If you choose to open an issue or pull request, English and Simplified Chinese are both welcome. The app interface and shared product specifications are currently Chinese. Please keep submissions focused and usable without private context; a submission may remain unreviewed.

## Product and data boundaries

Read the [product scope](docs/prd.md), [API contract](docs/api.md), and [acceptance criteria](docs/acceptance.md) before changing a workflow. Preserve these rules unless your fork explicitly changes and documents the contract:

- The main flow is acceptance, completion, review, reward pending, and fulfilled. The reward giver's fulfillment action ends the flow; there is no second receipt confirmation.
- A single progress history includes creation, acceptance, notes, photos, and later actions. Do not restore a separate “leave a note” entry as an incidental UI change.
- Members cannot act as the other shared account. Parent proxy actions must identify the actual actor and their proxy source.
- Active-term changes need mutual agreement. Ending a relationship cannot erase an earned reward.
- The service controls permissions, state, time, and versions. A failed request must not appear successful in the UI.
- Update both sides and the shared contract for API changes; retain stable acceptance IDs. Keep relationship selection in its existing “我们” entry instead of adding a permanent bar to every page.

Payments, stranger marketplaces, automatic punishment, background push services, and AI adjudication are outside this snapshot's scope. Forks exploring new capabilities should describe their own behavior and maintenance status rather than imply upstream support.

## Checks

Follow [Development](docs/development.md) and [Testing](docs/testing.md). Use the checked-in Gradle Wrapper and the documented toolchain. Run checks appropriate to the changed behavior:

```sh
python scripts/validate_project.py
python -m unittest discover -s tests -p 'test_*.py' -v
git diff --check
```

Android changes need Debug unit tests, a build, lint, and affected native interactions. Inspect screenshots when changing layout. A documentation-only change does not require a device run. Preserve useful assertions; do not weaken checks merely to turn a failure green.

Use dedicated test devices and synthetic data. Do not clear, uninstall, or reset an existing app or emulator to make a test pass. Never commit databases, real family records, child photos, credentials, session tokens, signing material, local SDK paths, or raw test fixtures. Generated outputs and credential-bearing reports must remain ignored.

## Submissions and licensing

Describe the concrete problem, resulting behavior, and checks actually run. Include sanitized screenshots for visual changes and list any unverified paths. Do not include unrelated cleanup or generated builds. Report vulnerabilities using [SECURITY.md](SECURITY.md), without putting sensitive details in public issues.

Contributions are provided under the project's [MIT License](LICENSE). Preserve third-party notices and licenses in [NOTICE](NOTICE). Document the source and permission status of added assets; AI-generated artwork should include its provenance, following the existing [illustration record](docs/design/ruyue-illustrations.md).
