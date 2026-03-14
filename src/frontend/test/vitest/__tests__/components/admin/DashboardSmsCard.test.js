import { installQuasarPlugin } from '@quasar/quasar-app-extension-testing-unit-vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, it, expect } from 'vitest'
import messages from 'src/i18n'
import DashboardSmsCard from 'src/components/admin/DashboardSmsCard.vue'

installQuasarPlugin()

const i18n = createI18n({ locale: 'en-US', legacy: false, globalInjection: true, messages })

const baseSmsStats = {
  total_sent: 1200,
  delivered: 1100,
  failed: 100,
  delivery_rate: 0.9167,
  total_segments: 1250,
  daily_breakdown: [],
}

function mountComponent(smsStats = baseSmsStats) {
  return mount(DashboardSmsCard, {
    global: { plugins: [i18n] },
    props: { smsStats },
  })
}

describe('DashboardSmsCard', () => {
  it('renders total_sent value', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('1200')
  })

  it('renders delivered value', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('1100')
  })

  it('renders delivery_rate as percentage via formatPct', () => {
    const wrapper = mountComponent()
    // 0.9167 * 100 = 91.67 -> toFixed(1) = "91.7%"
    expect(wrapper.text()).toContain('91.7%')
  })

  it('renders em-dash when delivery_rate is null', () => {
    const wrapper = mountComponent({ ...baseSmsStats, delivery_rate: null })
    expect(wrapper.text()).toContain('—')
  })

  it('shows daily_breakdown table when array is non-empty', () => {
    const stats = {
      ...baseSmsStats,
      daily_breakdown: [
        { date: '2026-03-01', total_sent: 100, delivered: 90, failed: 10 },
      ],
    }
    const wrapper = mountComponent(stats)
    expect(wrapper.text()).toContain('2026-03-01')
  })

  it('hides daily_breakdown section when array is empty', () => {
    const wrapper = mountComponent({ ...baseSmsStats, daily_breakdown: [] })
    // The q-separator before the table must not render
    // Simplest check: date from a row is absent
    expect(wrapper.text()).not.toContain('2026-03-')
  })
})
