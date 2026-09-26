import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

/**
 * GitHub Pages serves a project site from a subpath (`/<repo>/`), so the build
 * has to know its own public path; actions/configure-pages supplies this value
 * (without a trailing slash).
 *
 * Unset means "served from the domain root", which is the local dev server and
 * `npm start` (where server/index.js hosts ./dist itself) — so the default must
 * stay '/'. A hardcoded '/InputA/' breaks the single-origin mode in a way that
 * is hard to trace: dist requests `/InputA/assets/*.js`, serveStatic misses the
 * file and falls through to the SPA fallback, which answers index.html as
 * text/html for a module script — a blank page with no error to go on.
 */
const basePath = process.env.BASE_PATH || '/'

// https://vite.dev/config/
export default defineConfig({
  base: basePath.endsWith('/') ? basePath : `${basePath}/`,
  plugins: [
    react(),
    tailwindcss(),
  ],
  test: {
    // Plain node environment (no jsdom): vitest.setup.ts supplies a
    // localStorage stub so the zustand persistence paths still run.
    environment: 'node',
    setupFiles: ['./vitest.setup.ts'],
  },
  server: {
    proxy: {
      /**
       * All cross-origin traffic (Gutendex search, the Gutenberg plain-text
       * download, the free dictionaries, DeepSeek) is handled by the local
       * backend in server/. Proxying /api here keeps the browser on a single
       * origin during development, exactly like production where `npm start`
       * serves ./dist from that same server.
       *
       * Start the backend first: npm run server
       */
      '/api': {
        target: `http://localhost:${process.env.API_PORT ?? '8787'}`,
        changeOrigin: true,
      },
    },
  },
})

