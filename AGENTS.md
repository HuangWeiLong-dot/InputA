# InputA Project Guide

This file is the shared repository guide for human contributors and coding agents. It records architecture, commands, workflow conventions, and implementation constraints.

## Repository status

This is an Android-only repository. The previous web client and its Node backend were removed in the 0.4.0 commit; everything that ships lives under `android/`, which is an independent Gradle project.

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

## Commit and PR conventions

History follows Conventional Commits. Nothing enforces this automatically — there is no commit hook or CI check — so it relies on review.

- Format: `<type>(<scope>): <summary>`, for example `feat(reader): translate long-pressed sentences`.
- Types: `feat`, `fix`, `refactor`, `docs`, `test`, `perf`, `build`, `ci`, `chore`.
- Branch names use `<type>/<short-kebab-summary>` with the same types and a lowercase ASCII kebab-case summary, for example `feat/sentence-translation`, `fix/explore-level-filter`, or `ci/android-debug-build`. Do not put versions, usernames, or issue numbers in branch names. Branch from current `main`, keep branches short-lived, and delete them after merge; release versions belong in release commits, tags, and milestones, not feature branch names.
- Scope is a code area, not a version: `android`, `data`, `reader`, `explore`, `review`, `vocabulary`, `settings`, `design`. Omit it for repo-wide changes. Never write `feat(0.4.1): …`; release bumps are `chore(release): bump to 0.4.1 (versionCode 5)`, and the version belongs in the body or the tag.
- Summary: lowercase, imperative, no trailing period, roughly 72 characters or less. Describe one user-visible change, not a list of unrelated items.
- One logical change per commit. Keep features, fixes, docs and build-environment tweaks separate; a `gradle.properties` workaround for Chinese paths gets its own commit instead of shipping with a feature.
- Body: one blank line after the summary, explain why (not just what), wrap near 72 columns; bullet lists are fine.
- Breaking changes: `feat(api)!: …` plus a `BREAKING CHANGE:` footer.
- PR titles use the same format. The repository allows squash merges, so the PR title (or the single commit headline) becomes the headline on `main`; treat it as the final commit message.
- Reference issues in the footer (`Refs #12`), not in the summary.

## Parallel agent development

Multiple agents may develop features in parallel only when each agent has its own branch and Git worktree.

- Never let two agents edit the same working directory at the same time.
- Create every feature branch from the latest `main` and keep it short-lived.
- Assign non-overlapping modules or files to each agent. Shared interfaces, design tokens, build configuration, version metadata, CI, and release files must have one owner or be changed serially.
- Put cross-cutting changes in a separate, focused PR instead of mixing them into a feature branch.
- Each agent must verify its own branch and record the commands and manual checks in its PR. One agent must not claim another branch's results.
- Before merge, fetch and synchronize with current `main`; resolve conflicts in the feature worktree rather than rewriting shared history silently.
- Do not force-push a reviewed branch without notifying the reviewer. When a rewrite is agreed, use `git push --force-with-lease`.
- Do not commit or push unrelated work from another agent. Check `git status` before staging.
- Remove temporary worktrees after their branches are merged or abandoned.

Example worktree setup:

```bash
git fetch origin
git worktree add ../inputa-feature-name -b feat/feature-name origin/main
# Work and commit inside ../inputa-feature-name.
git worktree remove ../inputa-feature-name
```

## Review comment conventions

Review bodies and PR comments use an English skeleton with Chinese explanations, so the structure scans the same way across reviews and can be referenced directly.

- Section headings, checklist group labels, and lead-in labels are English: `Review verdict`, `Blocking issues`, `Medium priority`, `Low priority / cleanup`, `What works well`, `Pre-merge checklist`, group labels such as `Conventions and versioning`, and labels such as `Conclusion`, `Rationale`, `Changes needed`, and suggestion lead-ins.
- Explanations, evidence, and inline review comments stay in Chinese; keep code, paths, and commit examples verbatim.
- Put merge gates in a `Pre-merge checklist` task list and update it as findings are fixed instead of leaving a stale checklist.

## Versioning, milestones and releases

The shipped version is `versionName` + `versionCode` in `android/app/build.gradle.kts`; bump both in the same commit and update the version badges in both READMEs.

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

**Local-first word state.** `LocalWordStore` (SharedPreferences, one map per language) is the source of truth for word status: `-1` ignored, `0` known, `1..4` LingQ proficiency. This state is deliberately never written back to LingQ. Vocabulary, review, stats, home counters, and lesson Total/Unique/New are computed locally — `computeLessonWordStats` combines the local record map with cached lesson text (`LessonTextCache`); server-provided counts are only fallbacks when no text is cached. Do not "fix" this by syncing LingQ cards/known-words endpoints. Other local stores: `BookmarkStore`, `PlaylistStore`, `LastLessonStore`, `AiWordCacheStore`, `SettingsStore`; session/token and AI credentials live in `EncryptedSharedPreferences` via `SessionStore`.

**Reader text pipeline.** `LingqApi.sentences()` prefers the sentences endpoint, aligns each server token to the sentence text by substring position (`alignServerTokens`), and falls back to local tokenization. `tokenizeWords()` uses ICU `BreakIterator` for dictionary languages (`zh`, `ja`, `th`, `ko`, `lo`, `km`, `my`, `yue`) and a word-boundary regex for spaced scripts. `ReaderViewModel` renumbers sentence indices to list positions and keeps `wordStates` in memory keyed by lowercase term. Page mode measures real text layout (`measurePageBreaks` in `ReaderScreen.kt`) instead of using a fixed sentences-per-page count.

**Audio.** `MediaPlayer` fails on LingQ's remote URLs (`error -38`), so `LessonPlayer` and `WordPronouncer` always download through `AudioDownloader` (OkHttp → `cacheDir`) before playing. `AudioDownloader` sends `Authorization` only to LingQ API hosts; S3 rejects it as a malformed AWS signature. Word pronunciation tries LingQ TTS first and falls back to system `TextToSpeech`. Daily reminders use `AlarmManager` + `ReadingReminderReceiver` (rescheduled on boot).

**AI.** `AiClient` talks to a user-configured OpenAI-compatible `{baseUrl}/chat/completions` endpoint (base URL, key, model encrypted in `SessionStore`); requests are non-streaming. Word explanations return fixed `[MEANING]`/`[GRAMMAR]`/`[USAGE]`/`[EXAMPLES]` sections that `AiWordSheet` renders, and are cached by learning language + output language + word. LingQ's own Lynx API is not used.

**Design and localization.** All visual tokens are in `core/design`: `LqColors`, `LqSpace`, `LqRadius`, `LqType`, custom `LqIcons`, and shared components. The theme is dark-only and Material ripple is disabled globally. UI text uses the custom `Text` wrapper in `Localization.kt`, which translates through the English→Chinese map in `LocalAppLanguage` (app language setting is `en`/`zh`); use `RawText` for content that must not be translated (lesson titles, words, meanings, AI output). There is no `strings.xml` besides `app_name`. Images use the small OkHttp/BitmapFactory loader in `RemoteImage.kt`, not Coil.

## Conventions and pitfalls

- Code comments and `PLAN.md` are written in Chinese; user-visible strings are English keys translated by the dictionary. Add a translation entry whenever you add UI text, otherwise Chinese mode shows the English key.
- Coroutines: `runCatching` swallows `CancellationException`. Rethrow cancellation (see `ExploreViewModel.fetch`) and guard stale writes with `collectLatest` or generation counters; otherwise an old request can overwrite newer state.
- Keep the status encoding consistent: local storage uses `0` for known, while the vocabulary UI uses `5` for known and maps it back (`VocabularyViewModel.updateStatus`).
- No unit/instrumentation tests exist. CI builds the debug APK for pull requests and pushes to `main`; verify local changes with `./gradlew :app:compileDebugKotlin` or `:app:assembleDebug`; the current build emits only Kotlin annotation-target warnings.
- Do not introduce a self-hosted backend, a configurable LingQ origin, or plaintext credential storage. Login supports both username/password and a pasted LingQ token; the token stays encrypted and is never logged.
