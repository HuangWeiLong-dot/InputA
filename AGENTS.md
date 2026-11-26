# InputA Project Guide

This file is the shared repository guide for human contributors and coding agents. It records architecture, commands, workflow conventions, and implementation constraints.

## Repository status

This repository contains an Android app under `android/` and a React web client under `web/`. The previous unrelated web client and its Node backend were removed in 0.4.0; the current `web/` app is the focused InputA learning client.

`android/PLAN.md` is the original design brief, not documentation of the current code. It proposes a multi-module layout (`core/design`, `core/data`, `feature/*`) and Room/DataStore/Coil; the shipped app is a single `:app` module that uses SharedPreferences and a hand-written image loader. Trust the code and this file over PLAN.md.

## Commands

Run from `android/`. Requires JDK 17 and an Android SDK with API 37 (min SDK is 26). On Windows use `gradlew.bat`, or `./gradlew` from Git Bash.

```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:installDebug        # device or emulator must be attached
./gradlew :app:lint
./gradlew :app:testDebugUnitTest   # no test sources exist yet
./gradlew :app:assembleRelease     # needs android/keystore.properties
```

`keystore.properties` is gitignored and expects `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Never commit it, the keystore, or real LingQ credentials.

Run Web commands from `web/`. Requires Node.js 24 and npm.

```bash
cd web
npm ci
npm run dev
npm run typecheck
npm run lint
npm test
npm run test:server
npm run test:e2e        # first run: npx playwright install chromium
npm run build
npm start               # serves web/dist and the proxy
```

Web Playwright uses Chromium by default. Set `PLAYWRIGHT_CHANNEL` to use a locally installed channel such as `msedge` or `chrome`.

## Commit and PR conventions

History follows Conventional Commits. Nothing enforces this automatically — there is no commit hook or CI check — so it relies on review.

- Format: `<type>(<scope>): <summary>`, for example `feat(reader): translate long-pressed sentences`.
- Types: `feat`, `fix`, `refactor`, `docs`, `test`, `perf`, `build`, `ci`, `chore`.
- Branch names use `<type>/<short-kebab-summary>` with the same types and a lowercase ASCII kebab-case summary, for example `feat/sentence-translation`, `fix/explore-level-filter`, or `ci/android-debug-build`. Do not put versions, usernames, or issue numbers in branch names. Branch from current `origin/main`, keep active development short-lived, and retain merged local branches by default; delete local branches only when the maintainer explicitly requests cleanup. Release versions belong in release commits, tags, and milestones, not feature branch names.
- Scope is a code area, not a version: `android`, `web`, `data`, `reader`, `explore`, `review`, `vocabulary`, `settings`, `design`. Omit it for repo-wide changes. Never write `feat(0.4.1): …`; release bumps are `chore(release): bump to 0.4.1 (versionCode 5)`, and the version belongs in the body or the tag.
- Summary: lowercase, imperative, no trailing period, roughly 72 characters or less. Describe one user-visible change, not a list of unrelated items.
- One logical change per commit. Keep features, fixes, docs and build-environment tweaks separate; a `gradle.properties` workaround for Chinese paths gets its own commit instead of shipping with a feature.
- Body: one blank line after the summary, explain why (not just what), wrap near 72 columns; bullet lists are fine.
- Breaking changes: `feat(api)!: …` plus a `BREAKING CHANGE:` footer.
- PR titles use the same format. The repository allows squash merges, so the PR title (or the single commit headline) becomes the headline on `main`; treat it as the final commit message.
- Reference issues in the footer (`Refs #12`), not in the summary.
- After opening a PR, complete the required follow-up in [PR follow-up checklist](#pr-follow-up-checklist). Creating the PR alone does not finish triage.
- For every code or documentation commit, follow [Mandatory change workflow](#mandatory-change-workflow). Do not commit directly to `main`.
- When starting local development, immediately create the matching Git worktree required by [Local development worktrees](#local-development-worktrees). Do not perform feature work directly in the main repository checkout while other agents or parallel tasks may need it.

## Local development worktrees

Start work by creating the short-lived branch, then immediately create a dedicated worktree for that branch. The main repository checkout is reserved for synchronization, integration review, and administrative work; development happens inside the branch-specific worktree.

Use a deterministic directory name derived from the repository name and branch name:

```text
<repository-name>-worktree-<branch-type>-<short-kebab-summary>
```

For this repository, create sibling worktrees under `A:\GITHUB_MY_REP`:

```text
../InputA-worktree-feat-sentence-translation
../InputA-worktree-fix-explore-level-filter
../InputA-worktree-docs-pr-follow-up-compliance
../InputA-worktree-ci-android-debug-build
```

Rules:

- The branch remains `<type>/<short-kebab-summary>`; in the worktree path, replace the branch slash with one hyphen.
- Use the same type and summary in both the branch and worktree name. Do not add versions, usernames, issue numbers, agent names, or release names.
- Keep one branch and one worktree paired. Before creating a worktree, run `git worktree list` and reuse the matching existing worktree if the branch is already checked out.
- Fetch the latest `main` before creating the branch/worktree pair and base it on `origin/main`.
- Run Gradle, tests, documentation checks, commits, and branch-local review fixes inside the worktree. Check `git status` there before staging.
- Do not rename or delete a PR head branch while its worktree is active without coordinating with the maintainer.
- After merge, keep the local worktree and branch by default. Remove them only when the maintainer explicitly requests local cleanup, and verify the worktree is clean before removal.
- Existing nonstandard worktrees should be migrated with `git worktree move <old-path> <new-path>` when their branch is idle and has no uncommitted changes.

Example setup:

```bash
cd /absolute/path/to/InputA
git fetch origin
git worktree add ../InputA-worktree-feat-example -b feat/example origin/main
# Work and commit inside ../InputA-worktree-feat-example.
```

Optional local cleanup after an explicit maintainer request:

```bash
git worktree remove ../InputA-worktree-feat-example
git worktree prune
# Verify the squashed content before deleting the local branch.
git branch -D feat/example
```

## Mandatory change workflow

Every repository change must move through the same branch-to-merge lifecycle. Do not commit directly to `main`, and do not treat a local commit as delivered until its PR has passed the required checks and the remote branch has been cleaned up. Local worktrees and branches are retained by default.

1. Synchronize with the remote:
   - Fetch the latest `origin/main` from the main repository checkout.
   - Check `git status` before creating the branch. Preserve unrelated user changes and stage only files owned by the current task.
2. Create a short-lived branch from `origin/main` using `<type>/<short-kebab-summary>`.
3. Immediately create the matching worktree using the naming convention in [Local development worktrees](#local-development-worktrees). Work, test, and commit inside that worktree; never commit task changes directly in the main checkout.
4. Push the branch to `origin` and record the commit SHA.
5. Create a PR targeting `main`, complete the English PR template, and perform the [PR follow-up checklist](#pr-follow-up-checklist) for labels, milestone, and Projects.
6. Wait for CI to pass. If follow-up commits are made, push them and wait for the new check result before merging.
7. Squash-merge the PR after CI and all merge gates are satisfied.
8. After merge, verify the PR state is `MERGED` and the merged commit is visible on `origin/main`. Clean up remote artifacts:
   - Delete the remote branch with `git push origin --delete <branch>`.
   - Fetch with `git fetch origin --prune` and confirm the remote branch and stale remote-tracking refs are gone.
   - Keep the local worktree and branch by default. Only when the maintainer explicitly requests local cleanup, verify the worktree is clean, remove it with `git worktree remove <path>`, run `git worktree prune`, and delete the local branch. Because the repository uses squash merges, verify the merged content before using `git branch -D <branch>` when `-d` cannot recognize the squashed ancestry.
9. Return to the main checkout only to fetch or synchronize `origin/main`. Do not leave stale remote-tracking branches behind; retained local worktrees and branches must stay clean and identifiable.

Canonical lifecycle:

```bash
cd /absolute/path/to/InputA
git fetch origin
git worktree add ../InputA-worktree-feat-example -b feat/example origin/main
git -C ../InputA-worktree-feat-example add <owned-files>
git -C ../InputA-worktree-feat-example commit -m "feat(scope): summary"
git -C ../InputA-worktree-feat-example push -u origin feat/example
gh pr create --base main --head feat/example --fill
gh pr checks <number> --watch
gh pr merge <number> --squash --match-head-commit <sha>
gh pr view <number> --json state,mergedAt,mergeCommit
git -C ../InputA-worktree-feat-example status --short
git push origin --delete feat/example
git fetch origin --prune
# Keep the local worktree and branch unless cleanup is explicitly requested.
```

## PR follow-up checklist

Creating a PR is not complete until its GitHub description and planning metadata are filled in. Perform this checklist after opening the PR, after scope-changing pushes, and before requesting merge.

- Use the English PR template fields: `Summary`, `Changes`, `Test plan`, `Screenshots or recordings`, `Risks and compatibility`, and `Checklist`. Replace placeholder comments with English content, remove template comments, and leave only checklist items that were actually verified.
- When selecting an Issue template for a linked Issue or for follow-up triage, use the English Issue forms unless the contributor intentionally submitted the Simplified Chinese form. Keep code identifiers, commands, paths, and commit examples verbatim.
- Apply semantic labels from the repository taxonomy:
  - `bug` for `fix` PRs and reproducible defects.
  - `enhancement` for user-visible `feat` PRs and feature requests.
  - `documentation` for `docs`-only changes.
  - `accessibility` for accessibility-only fixes or audits.
  - `help wanted` or `good first issue` only when the maintainer explicitly marks the work for outside contributors.
  - `duplicate`, `invalid`, or `question` only after triage confirms that classification.
  Do not encode versions, release names, usernames, issue numbers, or P0/P1/P2 priority in labels; priority belongs in the GitHub Project field.
- Assign the PR and its linked Issue to the release milestone `vX.Y.Z` that matches the planned release. If the milestone does not exist and the release scope is already approved, create it before merging; move unfinished work to the next milestone instead of leaving it unassigned.
- Add the PR and linked Issue to the account-level `Languages Learning` project. Set `Priority`, `Size`, `Estimate`, `Iteration`, `Start date`, and `Target date` according to the [GitHub Projects conventions](#github-projects-conventions), and keep the fields consistent when scope changes.
- Recheck the PR title, linked milestone, labels, project status, and `Pre-merge checklist` after every history rewrite or scope-changing push. Update stale checklist items instead of leaving the original entries behind.
- Verify CI and branch-protection status before requesting merge. Do not claim a green build, manual check, screenshot, or localization pass unless its command or evidence is recorded in the PR.
- If the PR is duplicated, superseded, or depends on another branch, preserve its PR number until consolidation is agreed. Do not rename or delete the head branch without coordinating with the maintainer.

## Parallel agent development

Multiple agents may develop features in parallel only when each agent has its own branch and Git worktree.

- Never let two agents edit the same working directory at the same time.
- Create every feature branch from the latest `main` and keep it short-lived. Immediately pair it with a branch-specific worktree following [Local development worktrees](#local-development-worktrees).
- Assign non-overlapping modules or files to each agent. Shared interfaces, design tokens, build configuration, version metadata, CI, and release files must have one owner or be changed serially.
- Put cross-cutting changes in a separate, focused PR instead of mixing them into a feature branch.
- Each agent must verify its own branch and record the commands and manual checks in its PR. One agent must not claim another branch's results.
- Before merge, fetch and synchronize with current `main`; resolve conflicts in the feature worktree rather than rewriting shared history silently.
- Do not force-push a reviewed branch without notifying the reviewer. When a rewrite is agreed, use `git push --force-with-lease`.
- Do not commit or push unrelated work from another agent. Check `git status` before staging.
- Retain local worktrees and branches after merge by default; remove them only after an explicit maintainer request.

Example worktree setup:

```bash
cd /absolute/path/to/InputA
git fetch origin
git worktree add ../InputA-worktree-feat-feature-name -b feat/feature-name origin/main
# Work and commit inside ../InputA-worktree-feat-feature-name.
```

## Review comment conventions

Review bodies and PR comments use an English skeleton with Chinese explanations, so the structure scans the same way across reviews and can be referenced directly.

- Section headings, checklist group labels, and lead-in labels are English: `Review verdict`, `Blocking issues`, `Medium priority`, `Low priority / cleanup`, `What works well`, `Pre-merge checklist`, group labels such as `Conventions and versioning`, and labels such as `Conclusion`, `Rationale`, `Changes needed`, and suggestion lead-ins.
- Explanations, evidence, and inline review comments stay in Chinese; keep code, paths, and commit examples verbatim.
- Put merge gates in a `Pre-merge checklist` task list and update it as findings are fixed instead of leaving a stale checklist.

## Versioning, milestones and releases

The shipped Android version is `versionName` + `versionCode` in `android/app/build.gradle.kts`; bump both in the same commit and update the version badges in both READMEs. Web currently has no independent release version.

- Treat the project as SemVer `0.x`: new features or user-visible behavior changes take a minor bump (`0.4.0` → `0.5.0`), and a patch bump (`0.4.0` → `0.4.1`) is only for backwards-compatible fixes. Do not use a patch release to ship features.
- `versionCode` increases by one on every release and is never reused.
- Release commits are `chore(release): bump to X.Y.Z (versionCode N)`; never put a version in a `feat(...)` or `fix(...)` scope.
- Tags and releases use `vX.Y.Z` (`v0.4.0`, release title `InputA 0.4.0`). Milestones use the same `vX.Y.Z` title so `milestone:v0.5.0` matches the tag.
- Create a milestone when the release scope is planned, assign every PR and issue in that release, and close it after tagging; move unfinished items to the next milestone instead of leaving them behind.

## GitHub Projects conventions

Release planning also lives in the account-level `Languages Learning` project on GitHub. Milestones remain the release source of truth; project fields are for planning only.

- **Priority** — `P0`: blocking (broken login, crash, blocked release, credential or data loss), handle immediately and keep it out of the backlog; `P1`: must ship in the current milestone, a workaround exists; `P2`: planned but deferrable to a later version.
- **Size** — coarse effort: `XS` up to about 2 hours, `S` about half a day, `M` one to two days, `L` three to five days, and `XL` over a week, which should be split before starting.
- **Estimate** — numeric hours in this project (`1` means one hour). Keep it roughly consistent with `Size` instead of mixing in story points.
- **Iteration** — 14-day cycles starting on Sunday. Assign an item to the iteration it is planned for, and move unfinished work to the next iteration instead of leaving it in a past one. The schedule does not roll forward by itself; add an iteration when none is current, and keep completed iterations for history.
- **Start date / Target date** — per-item planning dates; the target should fall inside the assigned iteration.

## Architecture

**Single Activity, manual navigation.** `MainActivity` owns a `Screen` sealed interface plus `NextTab` selection; there is no Navigation-Compose. `CurrentLesson` and `CurrentCourse` are singleton `StateFlow` holders that carry the user's selection into `LessonViewModel`/`ReaderViewModel`. `Shell` records page content into a `GraphicsLayer` so the frosted bottom bar can blur the live content (real `BlurEffect` only on API 31+).

**MVVM convention.** Each feature has a `@HiltViewModel` with a nested `State` data class and a `MutableStateFlow<State>`; screens collect it with `collectAsStateWithLifecycle` and get the ViewModel via `hiltViewModel()` (imported from `androidx.hilt.lifecycle.viewmodel.compose`, not the older navigation-compose package). Hilt bindings are minimal: `core/data/AppModule.kt` provides `Json` and `OkHttpClient`; everything else is constructor-injected singletons.

**All remote data comes from LingQ.** `LingqApi` is a hand-rolled OkHttp client against `https://www.lingq.com/` — there is no backend in this repo. LingQ's responses have no public schema and mix camelCase with snake_case, so parsing is intentionally lenient: the `JsonObject.string/int/double/bool/obj/array()` helpers in `Models.kt` try several key aliases and return null instead of throwing, and `unwrapSingle`/`collectObjects` unwrap DRF wrappers such as `{results:[...]}`. Preserve that tolerance when adding endpoints. Failures surface as `ApiException(code, message)`.

**Local-first word state.** `LocalWordStore` (SharedPreferences, one map per language) and the Web `words` IndexedDB store are the source of truth for word status: `-1` ignored, `0` known, `1..4` LingQ proficiency. This state is deliberately never written back to LingQ. Vocabulary, review, stats, home counters, and lesson Total/Unique/New are computed locally — `computeLessonWordStats` combines the local record map with cached lesson text (`LessonTextCache`); server-provided counts are only fallbacks when no text is cached. Do not "fix" this by syncing LingQ cards/known-words endpoints. Other local stores: `BookmarkStore`, `PlaylistStore`, `LastLessonStore`, `AiWordCacheStore`, `SettingsStore`; session/token and AI credentials live in `EncryptedSharedPreferences` via `SessionStore`.

**Web client and proxy.** `web/` is a Vite/React SPA with React Query, React Router, IndexedDB, and Zod. `AuthContext` keeps the LingQ Token only in memory; reloading requires signing in again. `server/proxy.mjs` is shared by Vite and the production Node static server. It forwards a strict route/method/query allowlist to the fixed `https://www.lingq.com` origin, accepts only password-login POST bodies, strips cookies, disables redirects, and never exposes arbitrary upstream URLs. The Web proxy is an approved stateless transport exception, not permission to add a business backend or database.

**Reader text pipeline.** Android uses `LingqApi.sentences()` and aligns server tokens before local tokenization. Web uses `parseSentences` for primitive/keyed/nested/token-only responses, then `domain/tokenize.ts` with `Intl.Segmenter` for dictionary languages (`zh`, `ja`, `th`, `ko`, `lo`, `km`, `my`, `yue`) and a Unicode word-boundary regex for spaced scripts. `ReaderPage` renumbers sentence indices, guards stale meaning requests, and measures real DOM layout for page breaks.

**Audio.** `MediaPlayer` fails on LingQ's remote URLs (`error -38`), so `LessonPlayer` and `WordPronouncer` always download through `AudioDownloader` (OkHttp → `cacheDir`) before playing. `AudioDownloader` sends `Authorization` only to LingQ API hosts; S3 rejects it as a malformed AWS signature. Word pronunciation tries LingQ TTS first and falls back to system `TextToSpeech`. Daily reminders use `AlarmManager` + `ReadingReminderReceiver` (rescheduled on boot).

**AI.** `AiClient` talks to a user-configured OpenAI-compatible `{baseUrl}/chat/completions` endpoint (base URL, key, model encrypted in `SessionStore`); requests are non-streaming. Word explanations return fixed `[MEANING]`/`[GRAMMAR]`/`[USAGE]`/`[EXAMPLES]` sections that `AiWordSheet` renders, and are cached by learning language + output language + word. LingQ's own Lynx API is not used.

**Design and localization.** All visual tokens are in `core/design`: `LqColors`, `LqSpace`, `LqRadius`, `LqType`, custom `LqIcons`, and shared components. The theme is dark-only and Material ripple is disabled globally. UI text uses the custom `Text` wrapper in `Localization.kt`, which translates through the English→Chinese map in `LocalAppLanguage` (app language setting is `en`/`zh`); use `RawText` for content that must not be translated (lesson titles, words, meanings, AI output). There is no `strings.xml` besides `app_name`. Images use the small OkHttp/BitmapFactory loader in `RemoteImage.kt`, not Coil.

## Conventions and pitfalls

- Code comments and `PLAN.md` are written in Chinese; user-visible strings are English keys translated by the dictionary. Add a translation entry whenever you add UI text, otherwise Chinese mode shows the English key.
- Coroutines: `runCatching` swallows `CancellationException`. Rethrow cancellation (see `ExploreViewModel.fetch`) and guard stale writes with `collectLatest` or generation counters; otherwise an old request can overwrite newer state.
- Keep the status encoding consistent: local storage uses `0` for known, while the vocabulary UI uses `5` for known and maps it back (`VocabularyViewModel.updateStatus`).
- Android has no unit/instrumentation tests; CI builds the debug APK. Web has Vitest domain/parser tests, Node proxy allowlist tests, Playwright browser tests, lint/typecheck, and a production build; run the applicable Web commands after changes.
- Do not introduce a business backend, a configurable LingQ origin, or plaintext credential storage. The approved Web exception is a minimal stateless same-origin proxy with a fixed allowlist. Android encrypts its session token; Web keeps the Token only in memory and never logs it.
