import { installQuasarPlugin } from '@quasar/quasar-app-extension-testing-unit-vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, it, expect } from 'vitest'
import messages from 'src/i18n'
import DashboardSystemCard from 'src/components/admin/DashboardSystemCard.vue'

installQuasarPlugin()

const i18n = createI18n({ locale: 'en-US', legacy: false, globalInjection: true, messages })

const baseProviderStats = { total_submitted: 1200, dr_received: 1100, failed_count: 100 }
const baseWebhookStats  = { total_attempts: 500, failure_count: 10, exhausted_count: 2 }

function mountComponent(circuitBreakerState) {
  return mount(DashboardSystemCard, {
    global: { plugins: [i18n] },
    props: {
      circuitBreaker: { state: circuitBreakerState, failure_rate_pct: 1.5 },
      providerStats: baseProviderStats,
      webhookStats: baseWebhookStats,
    },
  })
}

describe('DashboardSystemCard', () => {
  describe('circuit breaker cbStateLabel', () => {
    it('CLOSED state renders Closed (Healthy) text', () => {
      const wrapper = mountComponent('CLOSED')
      expect(wrapper.text()).toContain('Closed')
    })

    it('OPEN state renders Open (Tripped) text', () => {
      const wrapper = mountComponent('OPEN')
      expect(wrapper.text()).toContain('Open')
    })

    it('HALF_OPEN state renders Half-Open text', () => {
      const wrapper = mountComponent('HALF_OPEN')
      expect(wrapper.text()).toContain('Half-Open')
    })
  })

  it('renders provider total_submitted', () => {
    const wrapper = mountComponent('CLOSED')
    expect(wrapper.text()).toContain('1200')
  })

  it('renders webhook total_attempts', () => {
    const wrapper = mountComponent('CLOSED')
    expect(wrapper.text()).toContain('500')
  })
})
