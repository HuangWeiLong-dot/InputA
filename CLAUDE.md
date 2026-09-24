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

- `API_PORT=9000 npm run server` moves the backend; Vite reads the same env var for its proxy target.
- Single test file: `npx vitest run src/__tests__/reader.test.ts`
- Single test by name: `npx vitest run -t "falls back to Wiktionary"`
- Tests run in the plain `node` environment (no jsdom) with a `localStorage` stub from `vitest.setup.ts`, so test the stores/utils/services directly — there is no component-rendering setup.

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
- Old `'learning'`/`'known'` values are migrated on load by `normalizeWordMap`; the same function sanitizes imported JSON backups, dropping unrecognized values.

**Pagination and text.** `src/utils/tokenizer.ts` owns both: `tokenizeText` splits into word/non-word tokens (keeping contractions and hyphenated words; word clicks resolve through `cleanWord`), and `paginateText` chunks chapters into ~`WORDS_PER_PAGE` (220) pages while keeping paragraphs — and, for oversized paragraphs, sentences — intact. Chapter splitting on `CHAPTER I.`/`Letter 2`-style headings lives in `src/services/gutendexApi.ts`. `src/utils/wordExtraction.ts` (the word-explosion panel) deliberately reuses `tokenizeText` rather than `Intl.Segmenter`: the segmenter splits `well-known` in two, so the panel would disagree with the vocabulary keys the reader actually files. The tokenizer matches Unicode letters, not `[a-zA-Z]`, so accented Latin words are clickable whole; CJK is excluded on purpose (no honest word boundary without a real segmenter, and the page-turn rule would file whole sentences).

**Backups.** `src/utils/backup.ts` reads and writes the export file. v2 carries `{version, exportedAt, words, notes, sentences}`; the importer also accepts the two shapes v1 left behind (a bare `{word: status}` map, and the `{words: {...}}` wrapper that only ever existed in localStorage). Import replaces rather than merges, and an empty backup is refused so a mis-picked file cannot clear the library.

**Styling.** Tailwind v4 via the Vite plugin; themes are CSS custom properties in `src/index.css` keyed on `[data-theme="light|sepia|dark"]` on `<html>`, applied by an effect in `App.tsx`. The 5-level highlight scale is not hardcoded colours: each level is `color-mix()` of the theme's `--level-ink` into `--bg-main`, so the three themes stay monotonic on their own. Reusable class strings live in `src/components/ui.ts`.

## Conventions

- Comments explain *why*, often at length — e.g. the required dot in `ECDICT_DEFINITION_LINE` (`src/services/dictionaryApi.ts`), without which the article "A" opening a definition line is eaten as the WordNet code `a.` at the cost of the definition's first word. Match that style rather than describing what the code does.
- Where this project diverges from LingKuma it says so and why, at the point of divergence. Keep that habit: `NOTICE.md` says what was taken, the code says what was changed.
- The README is in Chinese and is genuinely detailed (endpoint tables, `/api/dict` response schema, ECDICT field meanings, FAQ). Check it before re-deriving behaviour from code; user-facing strings are Chinese too.
- `tsconfig.app.json` (src) and `tsconfig.node.json` (vite.config.ts + server/**/*.ts) both set `verbatimModuleSyntax` and `erasableSyntaxOnly`: use `import type` for type-only imports, and no enums, namespaces, or parameter properties anywhere — the backend's type stripping would fail on them.
- Types in `server/` are checked even though the files run unbundled: a `.ts` module cannot import values *or* types from a `.js` one. That is why shared backend code (`upstreamError.ts`, `edgeTts.ts`, `dictLookup.ts`) is TypeScript, and why Node imports it with an explicit `.ts` extension.
- `index.html` carries an inline pre-paint script that reads the saved theme to avoid a flash of the wrong background. It hardcodes the `language_reader_settings` key and the `sepia` default — keep it in sync if either changes.
- `oxlint` flags `set-state-in-effect`. The house answer is the codebase's existing idiom: key the component so it remounts (`<SentenceAnalysisModal key={selectedSentence} />`, likewise the reader and the explosion panel), or derive the value during render instead of storing it.
