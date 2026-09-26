# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Requires **Node ≥ 22.18** (the backend loads a `.ts` file through Node's built-in type stripping, so the backend has no build step; dev uses 24.x).

```bash
npm run server   # Node backend on :8787 (CORS bridge + static host)
npm run dev      # Vite on :5173, proxies /api → :8787
npm start        # npm run build, then the backend serves dist/ on one origin
npm run build    # tsc -b && vite build
npm test         # vitest run (all suites, incl. server/dictLookup.test.ts)
npm run lint     # oxlint (not eslint)
```

The native Android port lives in `android/` (an independent Gradle project, see the Android section below):

```bash
cd android
./gradlew :domain:test          # pure-JVM tests for the ported algorithms (fast, no emulator)
./gradlew :app:testDebugUnitTest # data-layer tests (Robolectric + in-memory Room)
./gradlew :app:assembleDebug
./gradlew :app:installDebug     # needs a running emulator or a USB device
```

- `API_PORT=9000 npm run server` moves the backend; Vite reads the same env var for its proxy target.
- Single test file: `npx vitest run src/__tests__/reader.test.ts`
- Single test by name: `npx vitest run -t "falls back to Wiktionary"`
- Tests run in the plain `node` environment (no jsdom) with a `localStorage` stub from `vitest.setup.ts`, so test the stores/utils/services directly — there is no component-rendering setup.

Deployment is automated by `.github/workflows/` (frontend → GitHub Pages, backend → a
Tencent Cloud box over SSH); there is no deploy command to run by hand. The one-time server
provisioning lives in `deploy/` and is run by hand from there. Its runbook, `DEPLOY.md`, is
deliberately untracked — it names the live host — so read it on the machine that has it. See
[Deployment](#deployment) below for the constraints that shape the code.

## Architecture

An English-learning reader: click an unknown word → it is filed in a vocabulary book and its definition is shown; turning the page auto-files that page's unclicked words as "mastered". All state lives in the browser's localStorage — there is no account and no server database.

**Two processes, one origin.** The browser cannot reach Project Gutenberg text (no CORS headers; 302s to plain HTTP), `api.dictionaryapi.dev` (522 error pages without CORS), or `api.deepseek.com` (no browser access at all), so every cross-origin call goes through the Node backend in `server/`. In dev, Vite proxies `/api`; in production the same server also serves `dist/`, so nothing is cross-origin. Upstream status codes are passed through untouched (a 404 for an unknown word stays a 404) to keep client degradation logic meaningful.

- `server/index.js` — routing, static hosting, SPA fallback. The `API_ROUTES` array it prints at boot is hand-maintained; add new routes to it too.
- `server/upstreams.js` — every outbound request, with per-upstream timeouts deliberately *shorter* than the client's so the browser gets a real error rather than aborting. `assertBookTextUrl` is the SSRF guard: only Gutenberg hosts may be downloaded.
- `server/dictLookup.ts` — the one TypeScript file on the backend, loaded via Node type stripping. It opens `data/stardict.db` (an ~850MB ECDICT SQLite file, gitignored, **not** in this repo) with `readonly: true` + `fileMustExist: true`: it reads, never writes, never creates the DB. Missing file ⇒ `/api/dict` returns 503, everything else still works, and `/api/health` reports `dictionaryAvailable: false` so the client skips it without wasting a request.

**Frontend data flow.** `src/services/apiBase.ts` probes `/api/health` once per page load (memoized) and everything else asks `hasBackend()` first. `src/services/dictionaryApi.ts` holds an ordered `PROVIDERS` chain — `local-ecdict` (offline, ~1ms, Chinese definitions + exam tags) → `dictionaryapi.dev` → `wiktionary` → `datamuse` — and each provider tries its same-origin backend route before its direct upstream URL, since server and browser can differ in reachability. Two rules govern the chain:

- `optional: true` (only `local-ecdict`) means a transport failure is logged but must not turn "unknown word" into a reported outage.
- Only transport/5xx failures set `unavailable`; a clean 404 is a definitive "not in the dictionary" and gets cached, while outages stay retryable.

Adding a provider means editing `PROVIDERS` **and** the matching handler in `server/index.js` + `server/upstreams.js`.

**AI.** `src/services/aiService.ts` is the single entry point for every model call (`requestChat`, `askOnce`); `src/services/aiPrompts.ts` holds the prompt text. Prompts and several algorithms here were ported from **LingKuma** (MIT) — see `NOTICE.md` for the file-by-file provenance before changing them. The mock-output rule is easy to get wrong: the built-in sample appears only when there is *neither* a browser key *nor* a running backend, because a backend that is up but keyless is a genuine 401 the reader needs to see.

**Text to speech.** Three engines, in `src/services/ttsService.ts`: `edge` (the backend's `/api/tts`, which proxies Edge TTS), `custom` (a user's own endpoint, contract = a GET returning playable audio bytes) and `browser` (dictionary audio, then `SpeechSynthesis`). `edge`/`custom` failures fall back to `browser` silently — reading aloud is an incidental gesture, and an error dialog is worse than a different voice. `server/edgeTts.ts` is the only WebSocket client in the project and it has **zero dependencies** (Node's global `WebSocket` + `crypto.subtle`); it needs a browser `User-Agent` on the handshake or Edge answers 403, and `SEC_MS_GEC_VERSION` there is a pinned Edge build number that Microsoft rotates — a stale value makes every synthesis fail.

**Reading aids and language.** `src/utils/bionic.ts` (bold the first 40% of each word), the paragraph-focus ruler in `src/components/ReaderArea.tsx`, and `src/services/languageDetect.ts` (a few-KB heuristic, not the ~1MB model LingKuma uses). `Book.language` prefers source metadata (Gutendex's `languages`, which the code used to discard) and falls back to detection on paste. `src/utils/tokenizer.ts` owns the shared `CJK_PATTERN` — the tokenizer, the bionic skip rule and the language detector all key off that one definition, so change it in one place.

**State.** `useReaderStore` (book/chapter/page/settings/modal flags), `useVocabularyStore` (word → status) and `useAnnotationStore` (word → notes + saved sentences) all hand-roll localStorage persistence rather than using zustand's middleware. Storage keys: `language_reader_vocabulary`, `language_reader_annotations`, `language_reader_progress`, `language_reader_settings`, `deepseek_api_key`. `App.tsx` owns the definition-lookup state and drawer; `handleSelectWord` is the single entry point, reused by the panel's retry button, by following an inflection link to its lemma, and by the word-explosion panel.

**Two invariants that will silently destroy data if broken:**

- Vocabulary values must stay **scalars** (`1`-`5` or `'mastered'`). `normalizeWordMap` (`src/utils/wordLevel.ts`) is the only gate on both load and import, and it drops any value it does not recognise — so storing a note inside a word's value wipes every existing reader's vocabulary on their next load. Notes and sentences therefore live under their own key; `src/utils/annotations.ts` explains this at length. There is a test pinning it.
- `loadSettings` merges over `DEFAULT_SETTINGS` field by field. It used to return `JSON.parse` output unvalidated, so every new setting arrived as `undefined` for existing users and flowed straight into rendering (a font size of `undefinedpx`). New settings need a default there.

**The vocabulary rule** (documented in `src/utils/wordLevel.ts`, and the thing most likely to be broken by a careless change): statuses are richer than the old two-state model — levels `1`–`5` (1 熟知 … 5 生词) plus a separate `'mastered'`.

- Clicking a word in the text files it as level 5 **only if it has no status yet**; an already-filed word (any level, or mastered) is still looked up but its status is left alone. User judgement always wins — clicking never overwrites.
- Turning a page files that page's unclicked words as `'mastered'` (`markPageWordsAsMastered`, called from `PaginationBar.tsx`), also never overwriting an existing status. Single letters are skipped as noise.
- An uncollected word is **painted** as 生词 (`highlightLevel` in `src/utils/wordLevel.ts`) but is **not filed** — display only, so the page shows what is still unprocessed. The `'unknown'` sentinel drives the tint, so the page-turn rule above still sees those words as uncollected and files them, and their highlight then disappears. Do not "improve" this into a real write: pre-filing every page's words as 5 makes clicking unable to register as user judgement at all (`markAsNewWord` returns early *before* any write), so the page-turn rule would silently master the words the reader just clicked, and the vocabulary book would grow by thousands of entries nothing ever processed.
- Old `'learning'`/`'known'` values are migrated on load by `normalizeWordMap`; the same function sanitizes imported JSON backups, dropping unrecognized values.

**Pagination and text.** `src/utils/tokenizer.ts` owns both: `tokenizeText` splits into word/non-word tokens (keeping contractions and hyphenated words; word clicks resolve through `cleanWord`), and `paginateText` chunks chapters into ~`WORDS_PER_PAGE` (220) pages while keeping paragraphs — and, for oversized paragraphs, sentences — intact. Chapter splitting on `CHAPTER I.`/`Letter 2`-style headings lives in `src/services/gutendexApi.ts`. `src/utils/wordExtraction.ts` (the word-explosion panel) deliberately reuses `tokenizeText` rather than `Intl.Segmenter`: the segmenter splits `well-known` in two, so the panel would disagree with the vocabulary keys the reader actually files. The tokenizer matches Unicode letters, not `[a-zA-Z]`, so accented Latin words are clickable whole; CJK is excluded on purpose (no honest word boundary without a real segmenter, and the page-turn rule would file whole sentences).

**Backups.** `src/utils/backup.ts` reads and writes the export file. v2 carries `{version, exportedAt, words, notes, sentences}`; the importer also accepts the two shapes v1 left behind (a bare `{word: status}` map, and the `{words: {...}}` wrapper that only ever existed in localStorage). Import replaces rather than merges, and an empty backup is refused so a mis-picked file cannot clear the library.

**Styling.** Tailwind v4 via the Vite plugin; themes are CSS custom properties in `src/index.css` keyed on `[data-theme="light|sepia|dark"]` on `<html>`, applied by an effect in `App.tsx`. The 5-level highlight scale is not hardcoded colours: each level is `color-mix()` of the theme's `--level-ink` into `--bg-main`, so the three themes stay monotonic on their own. Reusable class strings live in `src/components/ui.ts`.

## Android (`android/`)

A native Kotlin/Compose port. It talks to the **same** `server/` backend — the ECDICT dictionary is not bundled into the APK, and vocabulary/progress live on the device in Room.

**Two Gradle modules, and the boundary is the point.** `:domain` is pure Kotlin with no `android`/`androidx` dependency at all; `:app` holds Compose, Hilt, Room and Retrofit. That boundary is what makes `./gradlew :domain:test` a plain JVM test run — the ported algorithms (tokenizer, pagination, word levels, dictionary provider chain, text normalisation) are tested in seconds without an emulator. Every algorithm was ported from `src/` file-by-file, so the same rule now exists twice; when you change one, change the other.

**Toolchain couplings that bite.** AGP 9 has built-in Kotlin, so applying `org.jetbrains.kotlin.android` fails the build outright, and **kapt is incompatible with it** — Room and Hilt must use KSP. The Kotlin version is whatever AGP bundles (read it from AGP's POM, not from the release notes), and the Compose compiler plugin version must equal it. `compileSdk` is 37 because the current stable `core-ktx`/`okhttp`/`Compose 1.12` all require it. Robolectric's emulated SDK is pinned in `app/src/test/resources/robolectric.properties` — without it Robolectric reads `targetSdk 37` and needs Java 21.

**The vocabulary rule has one entry point on this side:** `WordLevels` + `WordLevelCodec` (decode/encode) and `VocabularyRepository`. The invariants are the same as the web's, and so is the reason to be careful with them.

**Deliberate divergences from the web version** — each is commented at the point of divergence:

- The page-turn notice carries an **undo** and lives 6s instead of 2.6s (the web's action-less toast cannot carry an undo).
- A **multi-page fling masters nothing** (the web has no such gesture); it reports the skipped page count instead. Note that Compose's default `pagerSnapDistance` makes this hard to reach — it is a guard, not a daily path.
- The reading ruler is **not shipped**: its web implementation is hover-driven, and a setting that does nothing is worse than no setting.
- `ReaderUiState.statuses` is the **whole library, resident in memory**, matching the web. This was once a deliberate divergence (page-scoped, for phone memory) and was reversed on purpose — see the flash it caused, below.
- **Books are persisted** (the web keeps only progress + settings, so a refresh returns to the built-in first book).
- Chapter content is capped at `MAX_CHAPTER_CHARS` (200k) because Room reads rows through `CursorWindow`'s 2MB limit, and the web's chapter splitter can emit a whole book as one chapter.
- `wordsPerPage` is a setting (default 220, matching the web).
- **The default backend address depends on the build type** — debug points at `http://10.0.2.2:8787/` (the emulator's alias for the developer's machine), release at the deployed backend. The split lives in `app/build.gradle.kts` (`DEBUG_SERVER_BASE_URL`, empty for release) plus `SettingsRepositoryImpl.defaultServerBaseUrl`, because `:domain` cannot see `BuildConfig` — and that independence is exactly what lets the module run on a plain JVM. The production value stays a single constant (`DEFAULT_SERVER_BASE_URL` in `ReaderSettings.kt`); do not copy the address into Gradle, where it would be a second copy that drifts. The app-module unit tests therefore assert against `BuildConfig.DEBUG_SERVER_BASE_URL`, not that constant — they run the debug variant.
- **Tapping a word in the vocabulary panel opens its definition.** The web's rows only carry 朗读 / 标为生词 / 已掌握 / 移除 — there is no way to see a meaning without finding the word in the text. On a phone that's the long way round, so the row's text area is tappable and routes through the reader's own `onWordSelected` (`MainActivity`'s `Vocabulary` composable), which means the definition panel, the level picker and 移出词库 are all the same path as tapping a word in the text. The panel stays open behind the sheet so dismissing it returns to the list.
- **The interface is card-styled** — 14dp radius, a 1px border, a 2dp shadow — where the web is flat by declaration (`index.css` sets `border-radius: 0` on `*` and has no shadow or elevation token anywhere). The reading body stays full-bleed; cards are for controls, lists and panels. See `ui/theme/Design.kt` for the reasoning, including why a 2dp shadow is nearly invisible and is *invisible* in the dark theme (the 1px border is what carries the card — do not "fix" dark cards by raising the elevation).

**New UI work starts at the token and component layers, not at a literal.** `ui/theme/Design.kt` (`Space`, `Radius`, `Elevation`, `TypeScale`, `Motion`) plus `ui/components/` (`Card`, `PanelButton`, `IconButton`, `Segmented`, `ListRow`, `Chip`, `NoticeBar`, `AlertBox`, `PanelTextField`, `StepperRow`, `CheckboxRow`, `ModalPanel`, `AppIcon`). Two rules that are easy to break:

- **A tone is one complete style set, and there is no second style axis.** `Tone` (a sealed interface, including `Tone.Level(WordStatus?)`) resolves to container/content/border in one place (`ThemeTokens.colorsOf`). Do not add a `color` + `filled` pair to a component — that is the combination that has 2×N legal states and no way to say which wins.
- **Never pass a raw `shape =`.** Corners come from `MaterialTheme.shapes`, which *is* overridable (`Shapes().copy(...)` — the old comment claiming otherwise was wrong, and the explicit `shape = RectangleShape` on the bottom sheet was itself what made the override look broken). A raw `shape` argument silently defeats it at one call site.
- The reading measure is **not** part of `Space`: `PageContent`'s horizontal padding and its paragraph spacer are typography, not chrome, and are deliberately left as literals.

**Icons are hand-authored** (`ui/icons/InputaIcons.kt`) because `material-icons` is not on the classpath and is no longer transitive at material3 1.4.0 — `Icons.Default.*` would not compile. Geometry is ported from the web's lucide set (ISC; provenance in `NOTICE.md`), with the spec (24×24 viewport, 2 stroke, round caps, 20dp) enforced by one private builder and asserted by `InputaIconsTest`. That test deliberately works on the geometry rather than pixels — Compose's vector rasteriser is `internal` to compose-ui, so unit-testing ink is impossible; it catches coordinate escapes, degenerate paths, and reversed shapes (chevrons, plus/minus, check), and the pixels are checked in `android/build-screenshots/`.

**The proficiency tint is drawn, not styled** (`wordRect`/`wordBoxesOf` in `ui/reader/WordParagraph.kt`), because Compose's `SpanStyle(background=)` fills the whole line box. The replacement box reproduces what a browser paints for an inline `background`: the font's content box (`ascent + descent`) hung off the line's **baseline**, one box per line for a word that wraps, with the active word's 3dp outline grown from the same rect — so the web and the port share one rule, not two. The font's ascent/descent come from a probe measured with `lineHeight` stripped (`measureFontBox`); deriving them from the line box instead would make the block drift with the line height, which is the bug this replaced.

**Its geometry and its colour travel separately, and that is load-bearing.** `wordBoxesOf` returns a box for *every* word and depends only on the layout, so it is computed once per layout pass in `onTextLayout` and cached (it is the expensive part — one `getBoundingBox` per character). The colour is resolved per frame in `drawWithContent` from the current statuses. Folding the colour into the cached boxes reintroduces a real bug: vocabulary state does **not** re-layout the text (`buildParagraph` ignores it), so `onTextLayout` never fires for it and every tint would sit frozen at the state of the last layout — which is what made proficiency colours lag a page turn. `buildParagraph` taking no vocabulary state at all is part of the same guarantee, not an oversight.

**Why the statuses are resident — the flash, and why nothing smaller fixed it.** The page-turn rule masters the outgoing page, so turning back onto a page can flash 生词 colour and then clear. Two independent causes, both fixed:

1. The tint was coloured during the layout pass, and vocabulary state never re-lays-out the text — so the colour could only ever be as fresh as the last layout. Fixed by the geometry/colour split above.
2. `pageIndex` changes before that page's statuses have been queried, and the flow keeps its previous value meanwhile. With a page-scoped map that value holds *none* of the new page's words, so every one of them is painted as uncollected. Widening the window to the page's neighbours covers the common cases but not a page never observed before; only a map that always has every word removes the state entirely. Hence `ReaderViewModel.statusesFlow` = `observeAll()`.

The cost is real and accepted: Room invalidates per table, so **every write re-reads the whole vocabulary** (one page turn writes ~100 rows, then one full `SELECT`), tens of milliseconds on IO for a 20k-word library plus a transient allocation of similar size. It is off the main thread and only happens on page turns and word taps. The badge counts keep their own `GROUP BY`.

**Testing the geometry:** `TextLayoutResult` can be built for real in unit tests — Robolectric lays out text with actual fonts under `@GraphicsMode(NATIVE)` (the default legacy mode reports zeroed font metrics, so assertions against them verify nothing). `WordTintGeometryTest` uses this to pin where the tint lands, taking its reference from `Paint.fontMetrics`/`getTextBounds` rather than restating the implementation's own formula. `WordParagraphTest`'s claim that geometry is device-only predates this.

**Working on a device:** `adb` is not on `PATH` (`$ANDROID_HOME/platform-tools/adb.exe`). For tapping UI in scripts, take coordinates from `adb shell uiautomator dump` rather than eyeballing a screenshot — a scaled screenshot estimate was off by 86px and cost an hour of chasing a button that worked fine.

## Deployment

Two units, deployed independently: the SPA to **GitHub Pages** (at the repository subpath,
`/InputA/`) and `server/` to a Tencent Cloud box (Tokyo, so no ICP filing), behind the nginx
that was already serving other sites on that host, run by systemd with `node server/index.js`.
TLS is certbot's, matching the convention already on that machine — `deploy/setup-tls.sh` only
adds one `server_name`-scoped site file and never touches the existing vhosts. The walkthrough
is `DEPLOY.md`,
which is deliberately untracked because it names the live host; this section is the part that
constrains future code changes.

**The single-origin build must keep working byte for byte.** `npm start` serves `dist/` and
`/api` from one process and `npm run dev` proxies `/api`; both leave the deployment variables
unset, and that is the configuration the README documents. So:

- `src/services/apiBase.ts` is the only place the backend origin is decided. `API_BASE` is
  `''` unless `VITE_API_BASE_ENC` was set at build time, and with it empty `apiUrl(path)`
  returns `path` unchanged while `isBackendUrl(url)` *is* `url.startsWith('/api/')`. Every
  module-level URL in the app goes through `apiUrl` — do not "simplify" that indirection
  away, and do not re-introduce a literal `/api/...` request path, or the Pages build
  silently stops reaching the backend rather than failing.
- The origin travels **base64-encoded** (`VITE_API_BASE_ENC`, decoded by `decodeApiBase`),
  and the CI job encodes it at build time so the plain value never reaches `vite build`; the
  Android port does the same to `DEFAULT_SERVER_BASE_URL`. This is **obfuscation, not
  security** — `atob` reverses it and DevTools shows the real address on every request. It
  exists so a public repo and a public artifact are not greppable for the host, and nothing
  should be built on top of it that assumes the address is secret. Never pass a plaintext
  `VITE_API_BASE` to the build: Vite inlines whatever the code references, so adding one
  would put the address straight back into the bundle.
- `vite.config.ts` takes `base` from `BASE_PATH` and defaults to `'/'`, which is what the SPA
  fallback in `server/index.js` assumes. Hardcoding `/InputA/` breaks `npm start` in a way
  that yields a blank page and no error: the fallback answers `index.html` as `text/html` for
  a module script.
- Two old `startsWith('/api/')` checks are now `isBackendUrl` (`dictionaryApi.ts`,
  `gutendexApi.ts`). With a remote base they would otherwise label every backend request as a
  direct one in the logs and in the failure text the reader sees.
- `src/__tests__/apiBase.test.ts` pins the empty-base identity, and `dictionary.test.ts` /
  `gutendex.test.ts` already assert byte-exact root-relative URLs. If those go red, look for a
  `VITE_API_BASE` in an `.env*` file first — Vite loads those for every command, `npm test`
  included.

**CORS is opt-in and isolated in `server/cors.ts`.** `CORS_ALLOWED_ORIGINS`
(comma-separated, exact match, deliberately never a prefix match) is additive to the
always-allowed localhost origins. It is a `.ts` module because a `.ts` test cannot import
from a `.js` one. `Vary: Origin` is set on every response that carried an `Origin`, refused
ones included — `/api/tts` is cached `private, max-age=86400`, so a cache keyed without it
could replay one origin's response to another.

**Two behaviours changed *because* of the split,** both in `apiBase.ts`: a failed health probe
is remembered for 30s rather than for the page load (a success is still cached for the page
load). A cross-origin probe is a real network round trip, and previously one dropped packet
disabled the local dictionary, the book download, Edge TTS and the AI path until a reload,
with `resetBackendProbe()` — up to then dead code — as the only escape hatch. The probe
timeout likewise became 5s when a remote base is configured.

**`engines: { node: ">=22.18" }`** in `package.json` is load-bearing rather than advisory:
`server/index.js` imports `.ts` through Node's built-in type stripping, so the version the
server runs on cannot be older.

## Conventions

- Comments explain *why*, often at length — e.g. the required dot in `ECDICT_DEFINITION_LINE` (`src/services/dictionaryApi.ts`), without which the article "A" opening a definition line is eaten as the WordNet code `a.` at the cost of the definition's first word. Match that style rather than describing what the code does.
- Where this project diverges from LingKuma it says so and why, at the point of divergence. Keep that habit: `NOTICE.md` says what was taken, the code says what was changed.
- The README is in Chinese and is genuinely detailed (endpoint tables, `/api/dict` response schema, ECDICT field meanings, FAQ). Check it before re-deriving behaviour from code; user-facing strings are Chinese too.
- `tsconfig.app.json` (src) and `tsconfig.node.json` (vite.config.ts + server/**/*.ts) both set `verbatimModuleSyntax` and `erasableSyntaxOnly`: use `import type` for type-only imports, and no enums, namespaces, or parameter properties anywhere — the backend's type stripping would fail on them.
- Types in `server/` are checked even though the files run unbundled: a `.ts` module cannot import values *or* types from a `.js` one. That is why shared backend code (`upstreamError.ts`, `edgeTts.ts`, `dictLookup.ts`) is TypeScript, and why Node imports it with an explicit `.ts` extension.
- `index.html` carries an inline pre-paint script that reads the saved theme to avoid a flash of the wrong background. It hardcodes the `language_reader_settings` key and the `sepia` default — keep it in sync if either changes.
- Tailwind v4 scans the **whole repository** for class-shaped strings, not just `src/` — so a Tailwind class name written inside a comment in an unrelated file still emits real CSS. Not hypothetical: `android/app/src/main/kotlin/com/inputa/reader/ui/reader/DefinitionSheet.kt` documents the web drawer's height cap as `` `max-h-[86dvh]` ``, and that one string produces a `max-height:86dvh` rule on its own. Harmless while the class is genuinely used somewhere; dead CSS (and a confusing hunt) when it is not. If a built declaration has no visible source, grep the *whole* repo for the class name, comments included.
- `oxlint` flags `set-state-in-effect`. The house answer is the codebase's existing idiom: key the component so it remounts (`<SentenceAnalysisModal key={selectedSentence} />`, likewise the reader and the explosion panel), or derive the value during render instead of storing it.
