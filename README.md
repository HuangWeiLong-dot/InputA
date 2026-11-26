<div align="center">

# InputA

[English](README.md) | [简体中文](README.zh-CN.md)

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Hilt](https://img.shields.io/badge/DI-Hilt-2C4F7C)
![OkHttp](https://img.shields.io/badge/HTTP-OkHttp-3E4348)
![kotlinx.serialization](https://img.shields.io/badge/JSON-kotlinx.serialization-7F52FF)
![MVVM](https://img.shields.io/badge/architecture-MVVM-6E4AFF)
![Gradle](https://img.shields.io/badge/Gradle-9.6-02303A?logo=gradle&logoColor=white)
![JDK](https://img.shields.io/badge/JDK-17-007396?logo=openjdk&logoColor=white)
![SDK](https://img.shields.io/badge/compile%2Ftarget%20SDK-37-3DDC84)
![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84)
![version](https://img.shields.io/badge/version-0.4.0-blue)
![License](https://img.shields.io/badge/license-MIT-3DA639)
![Storage](https://img.shields.io/badge/storage-SharedPreferences-15803D)
![AI](https://img.shields.io/badge/AI-OpenAI%20compatible-412991)
![Theme](https://img.shields.io/badge/theme-dark-111113)
![UI](https://img.shields.io/badge/UI-English%20%7C%20%E4%B8%AD%E6%96%87-blue)

An unofficial, local-first Android reader for [LingQ](https://www.lingq.com/). Read lessons, tap unknown words, keep your vocabulary on your device, and listen to lesson audio — wrapped in a dark, custom Compose UI.

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

- There is **no project-owned backend**. The app talks directly to `https://www.lingq.com/`.
- Your LingQ token is stored in `EncryptedSharedPreferences` and is never logged.
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
```

## Limitations

- LingQ's internal API is undocumented; fields and quotas can change and break features.
- There are no automated tests; CI builds the debug APK for pull requests and pushes to `main`.
- The UI is dark-theme only.
- AI features require your own OpenAI-compatible base URL, API key, and model.

## Contributing

Commit, review, development, and release rules are enforced by review only; the full version lives in [CONTRIBUTING.md](CONTRIBUTING.md).

- Commits and PR titles follow [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): summary`, with a lowercase imperative summary and no trailing period.
- Types: `feat`, `fix`, `refactor`, `docs`, `test`, `perf`, `build`, `ci`, `chore`.
- Branch names use `type/short-kebab-summary`, for example `feat/sentence-translation` or `ci/android-debug-build`. Do not put versions, usernames, or issue numbers in branch names. Branch from current `main` and delete the branch after merge.
- Scopes are code areas (`android`, `data`, `reader`, `explore`, `review`, `vocabulary`, `settings`, `design`), never versions. Releases use `chore(release): bump to X.Y.Z (versionCode N)`.
- Keep one logical change per commit; do not mix features, fixes, docs, and build-environment tweaks.
- The repository allows squash merges, so the PR title becomes the headline on `main` and must follow the same format.
- Versions are SemVer while the app is `0.x`: minor bumps for features or behavior changes, patch bumps only for fixes. Bump `versionName` and `versionCode` together and update the README badges in the same commit.
- Tags and releases use `vX.Y.Z` (release title `InputA X.Y.Z`); milestones use the same `vX.Y.Z` title and are closed after tagging.

## License

MIT — see [LICENSE](LICENSE).
