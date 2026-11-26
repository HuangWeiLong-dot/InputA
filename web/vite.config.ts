import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'
import { createLingqProxy } from './server/proxy.mjs'

function lingqProxy(): Plugin {
  return {
    name: 'inputa-lingq-proxy',
    configureServer(server) {
      server.middlewares.use(createLingqProxy())
    },
    configurePreviewServer(server) {
      server.middlewares.use(createLingqProxy())
    },
  }
}

export default defineConfig({
  plugins: [react(), lingqProxy()],
  server: {
    host: '127.0.0.1',
  },
})
