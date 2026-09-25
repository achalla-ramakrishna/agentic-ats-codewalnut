import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// The dev server proxies the API and the Google sign-in round trip to Spring
// Boot, so the browser sees one origin and session/CSRF cookies just work.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/oauth2': 'http://localhost:8080',
      '/login/oauth2': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
