<!--
PR title: <type>(<scope>): <summary>
Types: feat, fix, refactor, docs, test, perf, build, ci, chore
Scopes: android, data, reader, explore, review, vocabulary, settings, design
Omit the scope for repository-wide changes. Use a lowercase imperative summary with no trailing period, roughly 72 characters or fewer.
Read CONTRIBUTING.md before submitting. Check only items you actually verified; explain N/A items in Test plan or Risks and compatibility.
-->

## Summary

<!-- Describe the problem and the intended result. -->

Related issue: <!-- For example, Refs #12. Delete this line if there is no related issue. -->

## Changes

<!-- List the important implementation, documentation, or configuration changes. -->

## Test plan

<!-- Record the exact commands run, device or emulator, Android version, manual steps, and results. Do not write only "tests passed". -->

```text

```

| Check | Environment | Result |
| --- | --- | --- |
| `./gradlew :app:assembleDebug` | | |
| `./gradlew :app:lint` | | |
| Manual feature check | | |
| English UI | | |
| Chinese UI | | |

## Screenshots or recordings

<!-- Provide before-and-after screenshots or recordings for visible UI changes when practical. Use N/A for non-UI changes. Do not include real accounts, tokens, or other sensitive information. -->

## Risks and compatibility

<!-- Describe migration, data, network, storage, signing, release, or compatibility impact. Write None if there is no identified risk. -->

## Checklist

### Conventions and scope

- [ ] The branch was created from current `main`, and the branch name, commit messages, and PR title follow repository conventions.
- [ ] This PR contains one coherent change with no unrelated cleanup or build-environment workarounds.
- [ ] Changes affecting architecture, persistence, authentication, network providers, release versioning, or public behavior were discussed in an issue or discussion first.
- [ ] By submitting this PR, I agree to provide this contribution under the repository's MIT License.

### Verification

- [ ] `./gradlew :app:assembleDebug` was actually run and passed.
- [ ] `./gradlew :app:lint` was actually run, or known unrelated warnings were identified and explained.
- [ ] Behavior changes were manually verified on a device or emulator, including loading, empty, error, cancellation, and rapid-input states where relevant.
- [ ] Visible UI changes were checked in both English and Chinese, with screenshots or recordings provided when practical.
- [ ] CI passes, and all review findings are addressed or explicitly accepted by the maintainer.

### Security, compatibility, and documentation

- [ ] No tokens, passwords, real account credentials, AI API keys, keystores, signed distribution artifacts, or local configuration are included.
- [ ] Authentication credentials remain encrypted and are never logged; request headers and cookies are sent only to intended hosts.
- [ ] Debug and Release application identity and side-by-side installation remain intact, and Release signing configuration was not added to PR CI.
- [ ] New UI strings were added to the localization map and cover both English and Chinese.
- [ ] CONTRIBUTING.md, AGENTS.md, and both READMEs were updated where applicable when public behavior or release rules changed.
- [ ] Version metadata was changed only when release preparation was explicitly assigned, and release commits follow SemVer and repository conventions.
