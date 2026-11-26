# Contributing to InputA

InputA is an Android-only project. All shipped application code lives under `android/`, which is an independent Gradle project. This document defines contribution and development requirements; architecture details and implementation constraints are recorded in [CLAUDE.md](CLAUDE.md).

By submitting a pull request, you agree that your contribution is provided under the repository's [MIT License](LICENSE).

## 1. Contribution workflow

### Before starting

- Check existing issues and pull requests before starting a large feature or refactor.
- Open an issue or discussion first when the change affects architecture, persistence, authentication, network providers, release versioning, or public behavior.
- Keep the change focused. One pull request should solve one coherent problem.

### Fork and branch

1. Fork the repository and configure the maintainer's repository as `upstream`.
2. Fetch the current upstream `main` before creating a branch.
3. Create a short-lived branch from the current `main`.

```bash
git fetch upstream
git switch -c feat/short-summary upstream/main
```

Branch names must use:

```text
<type>/<short-kebab-summary>
```

Examples:

```text
feat/sentence-translation
fix/explore-level-filter
docs/contributing-guide
ci/android-debug-build
```

Rules:

- Use the same types allowed for commits.
- Use a lowercase ASCII kebab-case summary.
- Do not include version numbers, usernames, or issue numbers.
- Do not use release names such as `v0.5.0` as feature branches.
- Delete the branch after merge unless the maintainer asks to keep it.

### While the pull request is open

- Keep the branch based on current `main`; rebase or merge `main` before the first review when practical.
- Push focused follow-up commits for review feedback.
- Do not silently force-push a branch that is already under review. If a history rewrite is necessary, tell the reviewer first and use `git push --force-with-lease`, never an unrestricted force push.
- Do not rename or delete a pull request's head branch without coordinating with the maintainer. A closed pull request can remain tied to the old branch name and become orphaned.
- If the pull request is superseded or duplicated, ask the maintainer how to consolidate it. Do not create a replacement pull request without agreement.

### Pull request requirements

The pull request title follows the same Conventional Commits format as a commit because the repository uses squash merges.

The description should include:

- **Summary** — the problem and the intended result.
- **Changes** — the important implementation or documentation changes.
- **Test plan** — exact commands run and manual checks performed.
- **Screenshots or recordings** — required for visible UI changes when practical.
- **Risks and compatibility** — migration, network, storage, signing, or release impact.
- **Checklist** — confirm build, localization, secrets, and documentation requirements.

Do not mark an item complete unless it was actually verified.

## 2. Commit and pull request conventions

History follows [Conventional Commits](https://www.conventionalcommits.org/). These rules are reviewed manually; there is currently no commit hook or CI rule that enforces them.

### Format

```text
<type>(<scope>): <summary>
```

Example:

```text
feat(reader): translate long-pressed sentences
```

### Types

Allowed types are:

- `feat` — a new feature or user-visible behavior.
- `fix` — a backwards-compatible bug fix.
- `refactor` — code restructuring without intended behavior change.
- `docs` — documentation only.
- `test` — tests only.
- `perf` — performance improvement.
- `build` — build system or dependency change.
- `ci` — continuous integration change.
- `chore` — maintenance work that does not fit another type.

### Scopes

Scopes identify code areas, never versions:

- `android`
- `data`
- `reader`
- `explore`
- `review`
- `vocabulary`
- `settings`
- `design`

Omit the scope for repository-wide changes. Never write a version as a scope:

```text
feat(0.4.1): add sentence translation
```

Use a separate release commit instead:

```text
chore(release): bump to X.Y.Z (versionCode N)
```

### Commit content

- Keep one logical change per commit.
- Do not mix features, fixes, documentation, and build-environment workarounds.
- Use a lowercase imperative summary with no trailing period, roughly 72 characters or fewer.
- Explain why in the commit body when the reason is not obvious.
- Reference issues in the footer, for example `Refs #12`, not in the summary.
- Mark breaking changes with `!` and a `BREAKING CHANGE:` footer.

## 3. Review conventions

Review bodies and pull request comments use an English structure with Chinese explanations.

Use English for:

- Section headings.
- Checklist group labels.
- Lead-in labels such as `Conclusion`, `Rationale`, and `Changes needed`.

Use Chinese for:

- Explanations.
- Evidence.
- Inline review comments.

Keep code, paths, commands, and commit examples verbatim. Merge gates belong in a living `Pre-merge checklist`; update it as findings are fixed instead of leaving stale items.

## 4. Versioning and releases

The application version is defined by `versionName` and `versionCode` in `android/app/build.gradle.kts`.

- During `0.x`, follow SemVer.
- New features or user-visible behavior changes require a minor bump.
- Patch releases are only for backwards-compatible fixes.
- Increase `versionCode` by one for every release and never reuse it.
- Update `versionName`, `versionCode`, and the version badges in both READMEs in the same release commit.
- Use `chore(release): bump to X.Y.Z (versionCode N)` for the release commit.
- Use `vX.Y.Z` for tags and releases; release titles use `InputA X.Y.Z`.
- Milestones use the same `vX.Y.Z` title.
- Assign release work to its milestone and close the milestone only after tagging.

Contributors should not bump the version, create a tag, or close a milestone unless the maintainer explicitly assigns release preparation.

## 5. Development environment

### Requirements

- JDK 17.
- Android SDK with API 37.
- Minimum supported device or emulator: Android 8.0 / API 26.
- The Gradle wrapper checked into the repository.

Run Gradle commands from `android/`. On Windows, use `gradlew.bat` from Command Prompt or PowerShell, or `./gradlew` from Git Bash.

### Common commands

```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew :app:lint
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

Notes:

- `installDebug` requires an attached device or emulator.
- There are currently no unit or instrumentation test sources; a successful test task does not prove feature correctness.
- `assembleRelease` requires a local release signing configuration and is not expected to work in pull request CI.
- Do not commit `android/local.properties`.

### Debug and release identity

Debug and release builds are intentionally installable side by side:

- Release application ID: `com.inputa.next`.
- Debug application ID: `com.inputa.next.debug`.
- Debug label: `InputA Debug`.

Do not remove the debug suffix or merge release signing configuration into pull request CI.

### Continuous integration

The `Android debug build` workflow runs for:

- Pull requests.
- Pushes to `main`.
- Manual dispatches.

CI uses JDK 17, installs Android SDK 37, and runs `:app:assembleDebug`. It uploads an artifact named `app-debug`, retained for 14 days. The artifact belongs to the workflow run that produced it; it does not update automatically after another run, rebase, or merge.

## 6. Code and architecture requirements

### Project structure

- The shipped app is a single `:app` module.
- `android/PLAN.md` is an original design brief, not the current architecture specification.
- Trust current source code and [CLAUDE.md](CLAUDE.md) over `PLAN.md`.

### Kotlin and Compose

- Follow the official Kotlin code style.
- Use the existing MVVM pattern: `@HiltViewModel`, nested `State`, and `MutableStateFlow`.
- Collect state with `collectAsStateWithLifecycle`.
- Reuse design tokens and shared components under `core/design` instead of adding one-off colors, spacing, or typography.
- Preserve the existing manual navigation and single-Activity architecture unless an architectural change is explicitly approved.

### Localization

- User-visible text uses the custom `Text` wrapper and the English-to-Chinese localization map.
- Add a translation entry whenever new UI text is introduced.
- Use `RawText` only for content that must not be translated, such as lesson titles, words, meanings, or AI output.
- Do not assume adding an English string is sufficient; Chinese mode must also be checked.

### Data and networking

- Local word state is the source of truth and is deliberately not written back to LingQ.
- Do not add LingQ card or known-word synchronization as a shortcut.
- Preserve lenient parsing for undocumented LingQ responses.
- Do not introduce a self-hosted backend or a configurable LingQ origin.
- Keep authentication tokens and AI credentials encrypted and never log them.
- Scope authorization headers and cookies to the intended hosts.

### Coroutines and state safety

- Rethrow `CancellationException` instead of swallowing it with `runCatching`.
- Guard against stale writes with `collectLatest`, cancellation, or generation counters.
- Do not allow an older request to overwrite newer state after a language, lesson, or filter change.

### Comments and scope discipline

- Keep code comments in Chinese when a comment is necessary.
- Do not add comments that merely restate obvious code.
- Do not combine unrelated cleanup with a bug fix or feature.

## 7. Security and secrets

Never commit or attach:

- `keystore.properties`.
- Keystore files such as `.jks` or `.keystore`.
- `local.properties`.
- LingQ tokens, passwords, or real account credentials.
- AI API keys or provider secrets.
- Signed release APKs or AABs intended for distribution.

Release signing properties are local-only. Pull request CI must use only debug builds and read-only repository permissions.

If a secret is exposed, stop using it, rotate it, and notify the maintainer. Removing it from a later commit is not sufficient.

## 8. Verification and definition of done

Before requesting review, verify the applicable items:

- [ ] The branch was created from current `main`.
- [ ] The branch name, commit messages, and pull request title follow the conventions.
- [ ] The pull request contains one coherent change.
- [ ] No secrets, keystores, generated APKs, or local configuration are included.
- [ ] `./gradlew :app:assembleDebug` succeeds.
- [ ] `./gradlew :app:lint` succeeds or known unrelated warnings are identified.
- [ ] The debug APK was installed or exercised on a device/emulator when behavior changed.
- [ ] Visible UI changes were checked in both English and Chinese.
- [ ] Loading, empty, error, cancellation, and rapid-input states were checked where relevant.
- [ ] Debug and release package identity was not broken.
- [ ] Documentation and both READMEs were updated when public behavior or release rules changed.
- [ ] Version metadata was changed only when release preparation was requested.
- [ ] CI passes and all review findings are addressed or explicitly accepted by the maintainer.

A green build is necessary but not sufficient. UI and behavior changes also require a manual feature check.
