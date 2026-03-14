import { vi } from 'vitest'

/**
 * Global mock for src/boot/axios.
 *
 * src/boot/axios imports '#q-app/wrappers' which is a Quasar CLI virtual module
 * that does not exist in the Vitest/jsdom environment. Mocking the entire boot
 * file here prevents the import chain from crashing before any test runs.
 *
 * Each test file mocks src/api/admin directly via vi.mock('src/api/admin', ...)
 * so the `api` stub here is never actually called — it just satisfies the import.
 */
vi.mock('src/boot/axios', () => ({
  default: vi.fn(),
  api: {
    get:    vi.fn(),
    post:   vi.fn(),
    put:    vi.fn(),
    delete: vi.fn(),
  },
  onLoadingChange: vi.fn(() => () => {}),
}))
