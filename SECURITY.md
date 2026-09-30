# Security policy

Ruyue is an unfinished experimental prototype. Development is paused as of September 30, 2026. No version has a maintained security support window, and there is no guarantee of acknowledgement, investigation, a fix, or a release. The source is provided under the [MIT License](LICENSE), without warranty. Do not rely on this snapshot as a production service.

## Reporting a vulnerability

If GitHub private vulnerability reporting is enabled, use the [private reporting form](https://github.com/chenzhiyong1994/ruyue/security/advisories/new). Include the commit or app version, affected component, impact, and a minimal reproduction using synthetic data.

If that entry is unavailable, you may open a public issue asking only whether a private contact channel is available. Do not publish vulnerability details there; a response is not guaranteed. This repository does not offer an incident-response service or bug bounty.

Never include real passwords, active tokens, signing keys, child photos, family records, or a private database. Redact personal information and device identifiers. Test only environments and accounts you control or are authorized to assess.

## Local preview boundaries

- The Python HTTP server is for local development and binds to loopback by default. `adb reverse` connects a local device; it does not provide remote hosting or transport security.
- Debug builds use development signing and permit limited local cleartext connections. Release build checks require an HTTPS API URL and externally supplied signing credentials, but passing those checks does not establish production readiness.
- The snapshot provides no hosted account service, account recovery, managed backup and restore, background push service, operational monitoring, or continuing security maintenance.
- SQLite contains account, relationship, agreement, event, and media data. Use synthetic records for evaluation and keep runtime data private.
- `.local/`, `artifacts/`, local configuration, and signing files must remain outside public commits. A Git ignore rule does not remove content already present in history.
- The GitHub Pages site is a static showcase, separate from the API. It does not store app accounts or run the backend.

Anyone choosing to operate a fork beyond local testing is responsible for its own threat assessment, HTTPS deployment, access controls, backup and recovery, data retention, and security support. The original project makes no claim that this list is a complete production checklist.

See [Development](docs/development.md), the [API contract](docs/api.md), and [Architecture](docs/architecture.md) for the implemented boundaries.
