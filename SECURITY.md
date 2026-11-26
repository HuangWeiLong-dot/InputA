# Security Policy

## Supported Versions

Security fixes target `main` and, when warranted, the latest tagged release. Older releases are not guaranteed to receive fixes.

InputA is an Android client with no project-owned backend. This policy covers the application source, build configuration, GitHub workflows, and release artifacts published by this repository.

## Reporting a Vulnerability

Please report suspected vulnerabilities privately to [3571676852@qq.com](mailto:3571676852@qq.com) with the subject:

```text
[InputA Security] short summary
```

Do not open a public issue or pull request for an unpatched vulnerability. Email is not encrypted, so do not include secrets, real account data, or sensitive artifacts in the initial message. If confidential material is necessary, request a secure transfer channel first.

Include as much of the following as you can:

- Affected version, `versionCode`, commit, and build type (`Debug` or `Release`).
- Device model, Android version, and API level.
- Affected component or screen.
- Reproduction steps, proof of concept, and expected versus actual behavior.
- Security impact, such as credential exposure, unauthorized access, data loss, or signing compromise.
- Sanitized logs, screenshots, or recordings.
- Any suggested mitigation or fix.

If you discover an exposed credential or signing secret, stop using it and rotate it before reporting. Do not send the secret itself.

## What to Expect

The maintainer will make a reasonable effort to:

- Acknowledge the report within 3 business days.
- Provide an initial assessment and next steps within 7 calendar days.
- Share a status update at least every 14 days while the report remains active.

These are response targets, not guarantees of a fix or resolution date. There is currently no paid bug bounty program. Credit will be given in advisories or release notes only with the reporter's permission.

Please allow reasonable time for a fix before public disclosure. The maintainer may publish a security advisory, release note, or patched release after coordinating with the reporter.

## Scope

In scope:

- Authentication, session storage, and credential handling in the Android app.
- Leakage of LingQ tokens, passwords, AI API keys, or other user secrets.
- Insecure network behavior, including authorization headers or cookies sent to unintended hosts.
- Local data exposure, unsafe persistence, or destructive behavior affecting user data.
- Build, signing, dependency, or CI weaknesses that could compromise source or release artifacts.

Out of scope:

- Vulnerabilities in LingQ, user-configured AI providers, Android, or other third-party services. Report those to the responsible vendor; notify the maintainer if an InputA integration makes the issue exploitable here.
- Issues that require an already compromised device, disabled OS security controls, or unauthorized access to a user's accounts.
- Social engineering, physical attacks, or denial-of-service testing.

Only test systems and accounts you own or are explicitly authorized to assess. Do not access other users' data, attack LingQ or third-party production services, or perform intrusive load testing.

## Security Design Requirements

Contributors must preserve these repository requirements:

- LingQ tokens and AI credentials remain encrypted and are never logged.
- Passwords are used only to request a token and are not persisted.
- Authorization headers and cookies are scoped to their intended hosts.
- Local word state is not written back to LingQ.
- `keystore.properties`, keystores, `local.properties`, signed distribution artifacts, and real credentials are never committed or attached to issues.
- Pull request CI uses debug builds and read-only repository permissions; release signing configuration remains local-only.

See [CONTRIBUTING.md](CONTRIBUTING.md#7-security-and-secrets) for the full contributor security requirements.
