import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
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

