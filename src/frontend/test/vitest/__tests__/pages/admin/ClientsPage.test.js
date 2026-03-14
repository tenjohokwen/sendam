import { installQuasarPlugin } from '@quasar/quasar-app-extension-testing-unit-vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import messages from 'src/i18n'
import { adminApi } from 'src/api/admin'
import ClientsPage from 'src/pages/admin/ClientsPage.vue'
import CreateClientDialog from 'src/components/admin/CreateClientDialog.vue'
import ApiKeysDialog from 'src/components/admin/ApiKeysDialog.vue'

vi.mock('src/api/admin', () => ({
  adminApi: {
    getClients:   vi.fn(),
    getApiKeys:   vi.fn(),
    createClient: vi.fn(),
    createApiKey: vi.fn(),
    revokeApiKey: vi.fn(),
  },
}))

installQuasarPlugin()

const i18n = createI18n({ locale: 'en-US', legacy: false, globalInjection: true, messages })

const sampleClients = [
  { id: '1000000000000001', name: 'Alpha Corp', status: 'ACTIVE',   balance: 5000 },
  { id: '1000000000000002', name: 'Beta Ltd',   status: 'INACTIVE', balance: 0    },
]

function mountComponent() {
  const wrapper = mount(ClientsPage, { global: { plugins: [i18n] } })
  wrapper.vm.$q.notify = vi.fn()
  return wrapper
}

describe('ClientsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads and displays clients on mount', async () => {
    adminApi.getClients.mockResolvedValue(sampleClients)
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.text()).toContain('Alpha Corp')
    expect(wrapper.text()).toContain('Beta Ltd')
  })

  it('shows error banner when getClients rejects', async () => {
    adminApi.getClients.mockRejectedValue(new Error('Network Error'))
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.find('.q-banner').exists()).toBe(true)
  })

  it('filters clients by name text', async () => {
    adminApi.getClients.mockResolvedValue(sampleClients)
    const wrapper = mountComponent()
    await flushPromises()

    // q-input renders a native <input>; setValue triggers input + change events
    const input = wrapper.find('input')
    await input.setValue('alpha')
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('Alpha Corp')
    expect(wrapper.text()).not.toContain('Beta Ltd')
  })

  it('opens CreateClientDialog when Register Client button is clicked', async () => {
    adminApi.getClients.mockResolvedValue([])
    const wrapper = mountComponent()
    await flushPromises()

    // The register button is the one with icon="add"
    const registerBtn = wrapper.findAll('.q-btn').find(
      b => b.text().toLowerCase().includes('register') || b.text().toLowerCase().includes('client')
    )
    await registerBtn.trigger('click')

    // CreateClientDialog is always rendered — modelValue is now true
    const dialog = wrapper.findComponent(CreateClientDialog)
    expect(dialog.props('modelValue')).toBe(true)
  })

  it('opens ApiKeysDialog with correct clientId when Manage Keys is clicked', async () => {
    adminApi.getClients.mockResolvedValue(sampleClients)
    adminApi.getApiKeys.mockResolvedValue([])
    const wrapper = mountComponent()
    await flushPromises()

    // Find the first Manage Keys button (for Alpha Corp, first row)
    const manageBtn = wrapper.findAll('.q-btn').find(
      b => b.text().toLowerCase().includes('manage') || b.text().toLowerCase().includes('key')
    )
    await manageBtn.trigger('click')
    await wrapper.vm.$nextTick()

    // v-if="showApiKeysDialog && selectedClient" — dialog mounts after click
    const dialog = wrapper.findComponent(ApiKeysDialog)
    expect(dialog.exists()).toBe(true)
    expect(dialog.props('clientId')).toBe('1000000000000001')
  })
})
