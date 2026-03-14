import { installQuasarPlugin } from '@quasar/quasar-app-extension-testing-unit-vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import messages from 'src/i18n'
import { adminApi } from 'src/api/admin'
import WebhooksPage from 'src/pages/admin/WebhooksPage.vue'
import ServerPagination from 'src/components/common/ServerPagination.vue'

vi.mock('src/api/admin', () => ({
  adminApi: {
    getWebhookEndpoints:  vi.fn(),
    getWebhookDeliveries: vi.fn(),
  },
}))

installQuasarPlugin()

const i18n = createI18n({ locale: 'en-US', legacy: false, globalInjection: true, messages })

const emptyPage = { content: [], totalElements: 0, totalPages: 0 }

function mountComponent() {
  return mount(WebhooksPage, { global: { plugins: [i18n] } })
}

describe('WebhooksPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads endpoints tab on mount', async () => {
    adminApi.getWebhookEndpoints.mockResolvedValue(emptyPage)
    mountComponent()
    await flushPromises()
    expect(adminApi.getWebhookEndpoints).toHaveBeenCalledWith({ page: 0, size: 20 })
    expect(adminApi.getWebhookDeliveries).not.toHaveBeenCalled()
  })

  it('loads deliveries when tab switches to deliveries', async () => {
    adminApi.getWebhookEndpoints.mockResolvedValue(emptyPage)
    adminApi.getWebhookDeliveries.mockResolvedValue(emptyPage)
    const wrapper = mountComponent()
    await flushPromises()

    // Switch tab via reactive state — watcher fires loadForTab
    wrapper.vm.activeTab = 'deliveries'
    await flushPromises()

    expect(adminApi.getWebhookDeliveries).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 20 })
    )
  })

  it('passes attemptStatus param to getWebhookDeliveries when filter is set', async () => {
    adminApi.getWebhookEndpoints.mockResolvedValue(emptyPage)
    adminApi.getWebhookDeliveries.mockResolvedValue(emptyPage)
    const wrapper = mountComponent()
    await flushPromises()

    // Switch to deliveries tab
    wrapper.vm.activeTab = 'deliveries'
    await flushPromises()

    // Set filter value and call onFilterChange (called by q-select @update:model-value)
    wrapper.vm.attemptStatusFilter = 'FAILED'
    await wrapper.vm.onFilterChange()
    await flushPromises()

    expect(adminApi.getWebhookDeliveries).toHaveBeenLastCalledWith(
      expect.objectContaining({ attemptStatus: 'FAILED' })
    )
  })

  it('shows error banner on API failure', async () => {
    adminApi.getWebhookEndpoints.mockRejectedValue(new Error('Network Error'))
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.find('.q-banner').exists()).toBe(true)
  })

  it('shows ServerPagination when endpoints totalPages > 1', async () => {
    adminApi.getWebhookEndpoints.mockResolvedValue({
      content: [], totalElements: 50, totalPages: 3,
    })
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.findComponent(ServerPagination).exists()).toBe(true)
  })
})
