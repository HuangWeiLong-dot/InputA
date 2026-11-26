# InputA Web

React + TypeScript web client for the InputA core learning loop. It mirrors the Android app's local-first behavior while keeping the first release focused on login, home, library search, lesson/course previews, reading, vocabulary, and flashcard review.

## Requirements

- Node.js 24
- npm

## Development

```bash
npm ci
npm run dev
```

Open the local URL printed by Vite. The development server includes the same allowlisted LingQ proxy used by production.

## Verification and build

```bash
npm run typecheck
npm run lint
npm test
npm run test:server
npm run test:e2e
npm run build
```

Playwright downloads its Chromium build separately:

```bash
npx playwright install chromium
```

To use an already installed browser locally, set `PLAYWRIGHT_CHANNEL` (for example, `msedge` or `chrome`).

## Production

```bash
npm ci
npm run build
npm start
```

The Node server reads `web/dist`, provides SPA routing, and exposes the proxy at `/api/lingq/`. It defaults to `127.0.0.1:4173`; configure `HOST` and `PORT` when deploying.

The server is a stateless static host plus a narrow forwarding proxy, not a business backend. It has no database, cookies, arbitrary upstream URLs, or LingQ write routes. Deploy behind HTTPS and add rate limiting at the reverse proxy before exposing the login endpoint publicly.

## Architecture

```text
web/
  src/
    api/          LingQ client and lenient response parsers
    app/          auth, preferences, shell, local word and audio hooks
    domain/       tokenization, lesson statistics, review transitions
    features/     login, home, explore, lesson, course, reader, vocabulary, review
    i18n/         English keys and Chinese translations
    storage/      IndexedDB records and localStorage preferences
  server/         allowlisted LingQ proxy and production static server
  e2e/            Playwright learning-loop tests
```

React Query owns remote/server cache state. Word records, bookmarks, cached lesson text, and the last lesson live in IndexedDB. App language, reader preferences, and the selected learning language live in localStorage.

## Authentication and privacy

- Password login exchanges credentials for a LingQ Token; pasted Tokens are also accepted.
- The Token is held only in memory. Reloading the page requires signing in again.
- Passwords and Tokens are never written to localStorage, sessionStorage, IndexedDB, URLs, or logs.
- Browser requests use the same-origin proxy. Images and media load directly from their remote hosts without the Token.
- Word status is local-first (`-1` ignored, `0` known, `1..4` proficiency) and is never written back to LingQ.
- Clearing browser site data removes local vocabulary, bookmarks, cached lessons, and preferences.

The first release does not synchronize local state with Android or across browsers.

## Current scope

Included:

- Username/password and Token login
- Home counters, continue learning, saved lessons, and shelves
- Explore search and provider filters
- Lesson and course previews
- Page, sentence, and scroll reading modes
- Dictionary meanings, pronunciation, word status, and lesson audio
- Local vocabulary and self-graded flashcard review
- English/Chinese interface and basic reader preferences

Not included yet:

- AI explanations or chat
- Playlists, imports, reminders, and the full stats screen
- Android/Web synchronization
- Managed hosting or release automation

LingQ's API is undocumented and may change. This is an unofficial personal-use client and is not affiliated with LingQ.
