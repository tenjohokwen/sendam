import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { quasar, transformAssetUrls } from '@quasar/vite-plugin'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: ['test/vitest/setup-file.js'],
    globals: true,
    passWithNoTests: true,
  },
  plugins: [
    vue({
      template: { transformAssetUrls },
    }),
    quasar({ sassVariables: false }),
  ],
  resolve: {
    alias: {
      src: fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
