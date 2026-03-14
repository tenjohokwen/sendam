import { installQuasarPlugin } from '@quasar/quasar-app-extension-testing-unit-vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import messages from 'src/i18n'
import { adminApi } from 'src/api/admin'
import TopupsPage from 'src/pages/admin/TopupsPage.vue'

vi.mock('src/api/admin', () => ({
  adminApi: {
    getTopupHistory: vi.fn(),
    approveTopup:    vi.fn(),
    rejectTopup:     vi.fn(),
  },
}))

installQuasarPlugin()

const i18n = createI18n({ locale: 'en-US', legacy: false, globalInjection: true, messages })

const pendingTopup = {
  id: 12345,
  client_id: 99,
  amount: 1000,
  topup_status: 'PENDING_APPROVAL',
  transaction_id: 'TXN1',
  payment_type: 'MTN',
  account_number: '237600000001',
  created_at: '2026-01-01',
}

function mountComponent() {
  const wrapper = mount(TopupsPage, { global: { plugins: [i18n] } })
  wrapper.vm.$q.notify = vi.fn()
  return wrapper
}

describe('TopupsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('calls getTopupHistory with PENDING_APPROVAL filter on mount', async () => {
    adminApi.getTopupHistory.mockResolvedValue({ topups: [pendingTopup] })
    mountComponent()
    await flushPromises()
    expect(adminApi.getTopupHistory).toHaveBeenCalledWith({ topupStatus: 'PENDING_APPROVAL' })
  })

  it('renders pending topup row with transaction id', async () => {
    adminApi.getTopupHistory.mockResolvedValue({ topups: [pendingTopup] })
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.text()).toContain('TXN1')
  })

  it('calls approveTopup with top_ prefixed id on Approve button click', async () => {
    adminApi.getTopupHistory.mockResolvedValue({ topups: [pendingTopup] })
    adminApi.approveTopup.mockResolvedValue({})
    // loadPending is called again after approve — mock for the reload
    adminApi.getTopupHistory.mockResolvedValue({ topups: [] })
    adminApi.getTopupHistory
      .mockResolvedValueOnce({ topups: [pendingTopup] })
      .mockResolvedValueOnce({ topups: [] })
    const wrapper = mountComponent()
    await flushPromises()

    const approveBtn = wrapper.findAll('.q-btn').find(
      b => b.text().toLowerCase().includes('approve')
    )
    await approveBtn.trigger('click')
    await flushPromises()

    expect(adminApi.approveTopup).toHaveBeenCalledWith('top_12345')
  })

  it('calls rejectTopup with top_ prefixed id on Reject button click', async () => {
    adminApi.getTopupHistory
      .mockResolvedValueOnce({ topups: [pendingTopup] })
      .mockResolvedValueOnce({ topups: [] })
    adminApi.rejectTopup.mockResolvedValue({})
    const wrapper = mountComponent()
    await flushPromises()

    const rejectBtn = wrapper.findAll('.q-btn').find(
      b => b.text().toLowerCase().includes('reject')
    )
    await rejectBtn.trigger('click')
    await flushPromises()

    expect(adminApi.rejectTopup).toHaveBeenCalledWith('top_12345')
  })

  it('calls getTopupHistory without status filter when tab switches to history', async () => {
    adminApi.getTopupHistory
      .mockResolvedValueOnce({ topups: [pendingTopup] }) // pending tab on mount
      .mockResolvedValueOnce({ topups: [] })             // history tab after switch

    const wrapper = mountComponent()
    await flushPromises()

    // Switch tab by directly setting the reactive state (watcher fires loadForTab)
    wrapper.vm.activeTab = 'history'
    await flushPromises()

    // Called twice: once on mount (pending), once after tab switch (history)
    expect(adminApi.getTopupHistory).toHaveBeenCalledTimes(2)
    // The second call (history) passes no arguments
    expect(adminApi.getTopupHistory).toHaveBeenLastCalledWith()
  })

  it('shows error banner on API failure', async () => {
    adminApi.getTopupHistory.mockRejectedValue(new Error('Network Error'))
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.find('.q-banner').exists()).toBe(true)
  })
})
