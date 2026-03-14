import { installQuasarPlugin } from '@quasar/quasar-app-extension-testing-unit-vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { vi, describe, it, expect, afterEach } from 'vitest'
import messages from 'src/i18n'
import { adminApi } from 'src/api/admin'
import ApiKeysDialog from 'src/components/admin/ApiKeysDialog.vue'

vi.mock('src/api/admin', () => ({
  adminApi: {
    getApiKeys: vi.fn(),
    createApiKey: vi.fn(),
    revokeApiKey: vi.fn(),
  },
}))

installQuasarPlugin()

const i18n = createI18n({ locale: 'en-US', legacy: false, globalInjection: true, messages })

/**
 * q-dialog teleports its content to document.body; RawKeyDialog (nested inside)
 * is also not reachable via wrapper.findComponent. Use wrapper.vm reactive state
 * (showRawKeyDialog, rawKeyResult, keys, hasError) and direct method calls.
 */
function mountComponent(modelValue = true) {
  const div = document.createElement('div')
  document.body.appendChild(div)
  const wrapper = mount(ApiKeysDialog, {
    global: { plugins: [i18n] },
    props: { modelValue, clientId: 'client-abc', clientName: 'Test Corp' },
    attachTo: div,
  })
  wrapper.vm.$q.notify = vi.fn()
  return wrapper
}

describe('ApiKeysDialog', () => {
  afterEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  it('calls getApiKeys when modelValue transitions from false to true (watch fires)', async () => {
    adminApi.getApiKeys.mockResolvedValue([])
    // Mount closed, then open — this triggers the watcher
    const wrapper = mountComponent(false)
    await flushPromises()
    expect(adminApi.getApiKeys).not.toHaveBeenCalled()

    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    expect(adminApi.getApiKeys).toHaveBeenCalledWith('client-abc')
  })

  it('does not call getApiKeys when modelValue stays false', async () => {
    adminApi.getApiKeys.mockResolvedValue([])
    mountComponent(false)
    await flushPromises()
    expect(adminApi.getApiKeys).not.toHaveBeenCalled()
  })

  it('renders loaded keys — vm.keys is populated', async () => {
    adminApi.getApiKeys.mockResolvedValue([
      { id: '9999999999999999', label: 'Default', status: 'ACTIVE', createdDate: '2026-01-01T00:00:00Z' },
    ])
    const wrapper = mountComponent(false)
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    expect(wrapper.vm.keys.length).toBe(1)
    expect(wrapper.vm.keys[0].label).toBe('Default')
  })

  it('sets hasError=true when getApiKeys rejects', async () => {
    adminApi.getApiKeys.mockRejectedValue(new Error('Network Error'))
    const wrapper = mountComponent(false)
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    expect(wrapper.vm.hasError).toBe(true)
    expect(document.querySelector('.q-banner')).not.toBeNull()
  })

  it('sets showRawKeyDialog=true after generateKey succeeds', async () => {
    adminApi.getApiKeys.mockResolvedValue([])
    adminApi.createApiKey.mockResolvedValue({ apiKeyId: '12345', rawKey: 'raw_key_value' })
    const wrapper = mountComponent(false)
    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    await wrapper.vm.generateKey()
    await flushPromises()

    expect(wrapper.vm.showRawKeyDialog).toBe(true)
    expect(wrapper.vm.rawKeyResult).not.toBeNull()
    expect(wrapper.vm.rawKeyResult.rawKey).toBe('raw_key_value')
  })

  it('clears rawKeyResult (memory guard) when onRawKeyDialogClose is called', async () => {
    adminApi.getApiKeys.mockResolvedValue([])
    adminApi.createApiKey.mockResolvedValue({ apiKeyId: '12345', rawKey: 'raw_key_value' })
    const wrapper = mountComponent(false)
    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    await wrapper.vm.generateKey()
    await flushPromises()
    expect(wrapper.vm.rawKeyResult).not.toBeNull()

    // Simulate RawKeyDialog close — onRawKeyDialogClose is the handler
    wrapper.vm.onRawKeyDialogClose()
    await flushPromises()

    expect(wrapper.vm.rawKeyResult).toBeNull()
    expect(wrapper.vm.showRawKeyDialog).toBe(false)
  })

  it('calls revokeApiKey with correct arguments on revokeKey call', async () => {
    adminApi.getApiKeys.mockResolvedValue([
      { id: 'key-001', label: 'Main', status: 'ACTIVE', createdDate: '2026-01-01T00:00:00Z' },
    ])
    adminApi.revokeApiKey.mockResolvedValue({})
    const wrapper = mountComponent(false)
    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    await wrapper.vm.revokeKey('key-001')
    await flushPromises()

    expect(adminApi.revokeApiKey).toHaveBeenCalledWith('client-abc', 'key-001')
  })
})
