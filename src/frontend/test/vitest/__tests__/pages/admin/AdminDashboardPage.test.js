import { installQuasarPlugin } from '@quasar/quasar-app-extension-testing-unit-vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import messages from 'src/i18n'
import { adminApi } from 'src/api/admin'
import AdminDashboardPage from 'src/pages/admin/AdminDashboardPage.vue'
import DashboardSmsCard     from 'src/components/admin/DashboardSmsCard.vue'
import DashboardBillingCard from 'src/components/admin/DashboardBillingCard.vue'
import DashboardSystemCard  from 'src/components/admin/DashboardSystemCard.vue'

vi.mock('src/api/admin', () => ({
  adminApi: {
    getClients:              vi.fn(),
    getDeliveryStats:        vi.fn(),
    getCreditSummary:        vi.fn(),
    getCircuitBreakerHealth: vi.fn(),
    getProviderStats:        vi.fn(),
    getWebhookHealth:        vi.fn(),
  },
}))

installQuasarPlugin()

const i18n = createI18n({ locale: 'en-US', legacy: false, globalInjection: true, messages })

// Minimal valid data for each API — prevents component from crashing when spreading results
const mockClients  = [
  { id: '1', name: 'Alpha', status: 'ACTIVE',   balance: 5000 },
  { id: '2', name: 'Beta',  status: 'INACTIVE', balance: 0    },
]
const mockSmsStats  = { total_sent: 100, delivered: 90, failed: 10, delivery_rate: 0.9, total_segments: 110, daily_breakdown: [] }
const mockCredits   = { sms_debit: 200, sms_refund: 10, topup_approved: 1000, net_credits_consumed: 190 }
const mockCb        = { state: 'CLOSED', failure_rate_pct: 0.5 }
const mockProvider  = { total_submitted: 100, dr_received: 90, failed_count: 10 }
const mockWebhook   = { total_attempts: 50, failure_count: 2, exhausted_count: 0 }

function mockAllApis() {
  adminApi.getClients.mockResolvedValue(mockClients)
  adminApi.getDeliveryStats.mockResolvedValue(mockSmsStats)
  adminApi.getCreditSummary.mockResolvedValue(mockCredits)
  adminApi.getCircuitBreakerHealth.mockResolvedValue(mockCb)
  adminApi.getProviderStats.mockResolvedValue(mockProvider)
  adminApi.getWebhookHealth.mockResolvedValue(mockWebhook)
}

function mountComponent() {
  return mount(AdminDashboardPage, { global: { plugins: [i18n] } })
}

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('calls all 6 API methods on mount via Promise.all', async () => {
    mockAllApis()
    mountComponent()
    await flushPromises()
    expect(adminApi.getClients).toHaveBeenCalledTimes(1)
    expect(adminApi.getDeliveryStats).toHaveBeenCalledTimes(1)
    expect(adminApi.getCreditSummary).toHaveBeenCalledTimes(1)
    expect(adminApi.getCircuitBreakerHealth).toHaveBeenCalledTimes(1)
    expect(adminApi.getProviderStats).toHaveBeenCalledTimes(1)
    expect(adminApi.getWebhookHealth).toHaveBeenCalledTimes(1)
  })

  it('renders all three dashboard cards after successful load', async () => {
    mockAllApis()
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.findComponent(DashboardSmsCard).exists()).toBe(true)
    expect(wrapper.findComponent(DashboardBillingCard).exists()).toBe(true)
    expect(wrapper.findComponent(DashboardSystemCard).exists()).toBe(true)
  })

  it('shows error banner when one API rejects (Promise.all fails)', async () => {
    adminApi.getClients.mockRejectedValue(new Error('Network Error'))
    adminApi.getDeliveryStats.mockResolvedValue(mockSmsStats)
    adminApi.getCreditSummary.mockResolvedValue(mockCredits)
    adminApi.getCircuitBreakerHealth.mockResolvedValue(mockCb)
    adminApi.getProviderStats.mockResolvedValue(mockProvider)
    adminApi.getWebhookHealth.mockResolvedValue(mockWebhook)

    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.find('.q-banner').exists()).toBe(true)
  })

  it('re-calls all 6 APIs when Refresh button is clicked', async () => {
    mockAllApis()
    const wrapper = mountComponent()
    await flushPromises()

    mockAllApis()
    const refreshBtn = wrapper.findAll('.q-btn').find(
      b => b.text().toLowerCase().includes('refresh')
    )
    await refreshBtn.trigger('click')
    await flushPromises()

    // Each method called once on mount + once on refresh = 2 total
    expect(adminApi.getClients).toHaveBeenCalledTimes(2)
    expect(adminApi.getDeliveryStats).toHaveBeenCalledTimes(2)
  })

  it('computes activeClientCount as count of ACTIVE clients only', async () => {
    mockAllApis()
    const wrapper = mountComponent()
    await flushPromises()

    // mockClients has 1 ACTIVE and 1 INACTIVE — activeClientCount must be 1
    const billingCard = wrapper.findComponent(DashboardBillingCard)
    expect(billingCard.props('activeClientCount')).toBe(1)
  })

  it('computes totalCredits as sum of all client balances', async () => {
    mockAllApis()
    const wrapper = mountComponent()
    await flushPromises()

    // mockClients: 5000 + 0 = 5000
    const billingCard = wrapper.findComponent(DashboardBillingCard)
    expect(billingCard.props('totalCredits')).toBe(5000)
  })
})
