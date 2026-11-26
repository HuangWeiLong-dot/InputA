<div align="center">

# InputA

[English](README.md) | [简体中文](README.zh-CN.md)

![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Web-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)
![version](https://img.shields.io/badge/version-0.4.0-blue)
![License](https://img.shields.io/badge/license-MIT-3DA639)

An unofficial, local-first [LingQ](https://www.lingq.com/) reader. The Android app provides the full feature set; the React web app focuses on the core learning loop: login, home, explore, lesson/course previews, reading, local vocabulary, and flashcard review.

</div>

> This is a personal-use client that talks directly to LingQ's internal API. It is not affiliated with LingQ, and the API may change without notice. You need your own LingQ account.

## Features

- **Sign in** with a LingQ username/email and password, or paste an existing API token.
- **Home** — known/learning/ignored word counts, daily goal, Continue Studying, Picked For You grouped by level, saved lessons, language and profile switching.
- **Explore** — shelf browsing, search, level filtering, and provider filters (LingQ, Netflix, TED, …), all from LingQ's library.
- **Lesson preview and course view** — cover, description, word totals, audio duration, lesson list, bookmark, playlist, and review entry points.
- **Reader** — Page, Sentence, and Scroll modes; tap any word for dictionary meanings, pronunciation, status changes, and an AI explanation; real layout-based page breaks; adjustable font size, line height, word spacing, and paragraph spacing; lesson audio with seek and auto-TTS.
- **Vocabulary and review** — a local word list with search and All / Inputa / Known / Ignored filters, status editing, meanings on demand, and self-graded flashcard review.
- **Stats** — known words, learning words, words read, daily goal progress, and words learned today, computed from your local records.
- **Playlist** — a device-local audio queue with the built-in player.
- **AI tools** — word explanations and a tutor chat using your own OpenAI-compatible endpoint.
- **Import** — create lessons from an article URL or pasted text through LingQ's import API.
- **Daily reminder** — a local notification at a time you choose.
- **English / Chinese interface**, plus AI explanations in English, Chinese, Japanese, or Korean.

## Tech stack

| Layer | Choice |
| --- | --- |
| Language | Kotlin 2.2 |
| UI | Jetpack Compose, Material 3, custom dark design system |
| Architecture | Single Activity, MVVM, StateFlow |
| DI | Hilt |
| Networking | OkHttp, hand-written LingQ API client |
| JSON | kotlinx.serialization |
| Local storage | SharedPreferences + EncryptedSharedPreferences |
| Audio | MediaPlayer with an OkHttp download cache, system TextToSpeech fallback |
| Build | Gradle 9.6, AGP 9.4, JDK 17, compile/target SDK 37, min SDK 26 |

There is intentionally no Room, Navigation-Compose, or Coil dependency; the app keeps its own small stores and loaders.

## Data and privacy

- There is **no business backend or database**. Android talks directly to `https://www.lingq.com/`; Web uses a minimal same-origin forwarding proxy because LingQ does not allow browser CORS access.
- Android keeps the LingQ token in `EncryptedSharedPreferences`. Web keeps it only in memory and requires signing in again after a reload. Neither client logs it.
- Vocabulary status, bookmarks, playlist, last lesson, reading settings, and the AI explanation cache stay on the device.
- Passwords are only used to request a token and are not persisted.
- AI requests go to the OpenAI-compatible endpoint **you** configure.

Word statuses are local-first on purpose: the app does not write them back to LingQ, so the vocabulary and counts shown here can differ from your LingQ account stats.

## Build and run

Requirements: JDK 17, Android SDK with API 37, and a device or emulator running Android 8.0 (API 26) or newer.

```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

On Windows, use `gradlew.bat`, or `./gradlew` from Git Bash.

Release builds read signing values from `android/keystore.properties` (gitignored):

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```bash
cd android
./gradlew :app:assembleRelease
```

### Web

Requires Node.js 24. Full Web setup, deployment, and test instructions are in [web/README.md](web/README.md).

```bash
cd web
npm ci
npm run dev
```

```bash
cd web
npm run typecheck
npm run lint
npm test
npm run test:server
npm run test:e2e
npm run build
npm start
```

## Project layout

```text
android/
  app/src/main/kotlin/com/inputa/next/
    MainActivity.kt        # single Activity, manual screen navigation
    core/data/             # LingQ API client, models, session and local stores
    core/design/           # dark theme tokens, components, icons, localization, image loader
    core/media/            # audio download/playback and pronunciation
    core/notify/           # daily reading reminder
    feature/               # auth, home, explore, lesson, course, reader, vocabulary,
                           # review, stats, playlist, saved, imports, chat, settings
  app/src/main/res/        # manifest, launcher assets, theme
web/
  src/                     # React features, API parsers, domain and local storage
  server/                  # allowlisted LingQ proxy and Node static server
  e2e/                     # Playwright learning-loop tests
```

## Limitations

- LingQ's internal API is undocumented; fields and quotas can change and break features.
- Android has no automated tests; CI builds the debug APK for pull requests and pushes to `main`. Web has parser/domain tests, proxy allowlist tests, browser tests, and a production build in CI.
- The Web first release excludes AI, playlists, imports, reminders, the full stats screen, and Android/Web synchronization.
- The UI is dark-theme only.
- AI features require your own OpenAI-compatible base URL, API key, and model.

## Contributing

Commit, review, development, and release rules are enforced by review only. See [CONTRIBUTING.md](CONTRIBUTING.md) for contributor requirements and [AGENTS.md](AGENTS.md) for the canonical branch/worktree, PR follow-up, and architecture rules.

- Commits and PR titles follow [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): summary`, with a lowercase imperative summary and no trailing period.
- Types: `feat`, `fix`, `refactor`, `docs`, `test`, `perf`, `build`, `ci`, `chore`.
- Branch names use `type/short-kebab-summary`, for example `feat/sentence-translation` or `ci/android-debug-build`. Do not put versions, usernames, or issue numbers in branch names. Branch from the latest `origin/main`, keep branches short-lived, and delete them after merge.
- Every code or documentation change uses a matching branch-specific worktree named `<repository>-worktree-<type>-<short-kebab-summary>`. Develop and commit inside that worktree; reserve the main checkout for synchronization, integration review, and administrative work. See [Local development worktrees](AGENTS.md#local-development-worktrees).
- Scopes are code areas (`android`, `web`, `data`, `reader`, `explore`, `review`, `vocabulary`, `settings`, `design`), never versions. Releases use `chore(release): bump to X.Y.Z (versionCode N)`.
- Keep one logical change per commit; do not mix features, fixes, docs, and build-environment tweaks.
- Complete the required change lifecycle: synchronize, branch, create the worktree, verify, commit, push, open a PR, wait for CI, squash-merge, and clean up the merged worktree and branches. Never commit directly to `main`. See [Mandatory change workflow](AGENTS.md#mandatory-change-workflow).
- The repository allows squash merges, so the PR title becomes the headline on `main` and must follow the same format.
- After opening a PR, complete the English template and set semantic labels, the `vX.Y.Z` release milestone, and the `Languages Learning` project fields. Recheck this metadata after scope-changing pushes. See [PR follow-up checklist](AGENTS.md#pr-follow-up-checklist).
- Parallel agents must use separate branches and worktrees with non-overlapping files; each agent verifies and records only their own results.
- Versions are SemVer while the app is `0.x`: minor bumps for features or behavior changes, patch bumps only for fixes. Bump `versionName` and `versionCode` together and update the README badges in the same commit.
- Tags and releases use `vX.Y.Z` (release title `InputA X.Y.Z`); milestones use the same `vX.Y.Z` title and are closed after tagging.

## License

MIT — see [LICENSE](LICENSE).
