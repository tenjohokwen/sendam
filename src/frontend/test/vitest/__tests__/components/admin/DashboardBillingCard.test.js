import { installQuasarPlugin } from '@quasar/quasar-app-extension-testing-unit-vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, it, expect } from 'vitest'
import messages from 'src/i18n'
import DashboardBillingCard from 'src/components/admin/DashboardBillingCard.vue'

installQuasarPlugin()

const i18n = createI18n({ locale: 'en-US', legacy: false, globalInjection: true, messages })

const baseCreditSummary = {
  sms_debit: 500,
  sms_refund: 20,
  topup_approved: 2000,
  net_credits_consumed: 480,
}

function mountComponent(overrides = {}) {
  return mount(DashboardBillingCard, {
    global: { plugins: [i18n] },
    props: {
      creditSummary: baseCreditSummary,
      activeClientCount: 7,
      totalCredits: 15000,
      ...overrides,
    },
  })
}

describe('DashboardBillingCard', () => {
  it('renders activeClientCount from props', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('7')
  })

  it('renders totalCredits from props', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('15000')
  })

  it('renders sms_debit from creditSummary', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('500')
  })

  it('renders net_credits_consumed from creditSummary', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('480')
  })
})
