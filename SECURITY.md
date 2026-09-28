# Security policy

Ruyue is a development preview. There is no production security support window or guaranteed response time. Please report issues against the current `main` branch and include the commit or app version you examined.

## Report a vulnerability privately

Use [GitHub private vulnerability reporting](https://github.com/chenzhiyong1994/ruyue/security/advisories/new). Include the affected component, expected and observed behavior, and a minimal reproduction using synthetic data. Describe the impact and any workaround you found.

Do not include real account passwords, live session tokens, signing keys, child photos, family records, or a copy of a private database. Redact device identifiers and unrelated personal information. Do not test against other people's accounts or services.

If the private reporting entry is unavailable, open a public issue only to request a private contact channel. Do not post the vulnerability details, credentials, or exploit data there.

## Preview boundaries

- The bundled HTTP server is intended for local development. It binds to loopback by default; `adb reverse` is a local device connection.
- Public deployment requires HTTPS, suitable hosting, access controls, operational monitoring, backup and recovery, and a separate security review. A reverse proxy alone is not a production-readiness claim.
- Release builds require an HTTPS API URL and externally supplied signing credentials. The repository does not contain production signing material or a hosted account service.
- Account recovery, managed backups, background push notifications, and production operations are not provided by this preview.
- `.local/` and `artifacts/` may contain sensitive runtime or test data and must remain outside public commits. Ignoring a file does not remove it from existing Git history.

For local configuration, see [development](docs/development.md). Permission and state rules are defined in the [API contract](docs/api.md).
