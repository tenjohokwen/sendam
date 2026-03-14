import { installQuasarPlugin } from '@quasar/quasar-app-extension-testing-unit-vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import messages from 'src/i18n'
import { adminApi } from 'src/api/admin'
import SmsMonitorPage from 'src/pages/admin/SmsMonitorPage.vue'
import DlrDialog from 'src/components/admin/DlrDialog.vue'
import ServerPagination from 'src/components/common/ServerPagination.vue'

vi.mock('src/api/admin', () => ({
  adminApi: {
    getScheduledSms:  vi.fn(),
    getDlrForRequest: vi.fn(),
  },
}))

installQuasarPlugin()

const i18n = createI18n({ locale: 'en-US', legacy: false, globalInjection: true, messages })

const sampleRow = {
  id: '1000000000000001',
  clientId: '2000000000000001',
  sendRequestId: 'sr-abc-001',
  sender: 'SENDER1',
  messageCount: 5,
  scheduleTime: '2026-03-15T10:00:00Z',
  reservedCredits: 50,
}

function mountComponent() {
  return mount(SmsMonitorPage, { global: { plugins: [i18n] } })
}

describe('SmsMonitorPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // DlrDialog watch fires immediately on mount (sendRequestId=null — early-returns without API call)
    adminApi.getDlrForRequest.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0 })
  })

  it('calls getScheduledSms with page 0 on mount', async () => {
    adminApi.getScheduledSms.mockResolvedValue({ content: [sampleRow], totalElements: 1, totalPages: 1 })
    mountComponent()
    await flushPromises()
    expect(adminApi.getScheduledSms).toHaveBeenCalledWith({ page: 0, size: 20 })
  })

  it('renders SMS rows from API response', async () => {
    adminApi.getScheduledSms.mockResolvedValue({ content: [sampleRow], totalElements: 1, totalPages: 1 })
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.text()).toContain('SENDER1')
    expect(wrapper.text()).toContain('sr-abc-001')
  })

  it('shows error banner on getScheduledSms failure', async () => {
    adminApi.getScheduledSms.mockRejectedValue(new Error('Network Error'))
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.find('.q-banner').exists()).toBe(true)
  })

  it('hides ServerPagination when totalPages is 1', async () => {
    adminApi.getScheduledSms.mockResolvedValue({ content: [sampleRow], totalElements: 5, totalPages: 1 })
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.findComponent(ServerPagination).exists()).toBe(false)
  })

  it('opens DlrDialog with correct sendRequestId when DLR button is clicked', async () => {
    adminApi.getScheduledSms.mockResolvedValue({ content: [sampleRow], totalElements: 1, totalPages: 1 })
    const wrapper = mountComponent()
    await flushPromises()

    // The icon button at the end of each row calls openDlr(row.sendRequestId)
    // It's the only button rendered in the table row (icon="list", no label)
    const allBtns = wrapper.findAll('.q-btn')
    // Find the last q-btn (action button in the row)
    const dlrBtn = allBtns[allBtns.length - 1]
    await dlrBtn.trigger('click')
    await wrapper.vm.$nextTick()

    const dlrDialog = wrapper.findComponent(DlrDialog)
    expect(dlrDialog.props('sendRequestId')).toBe('sr-abc-001')
  })

  it('resets selectedSendRequestId to null on page change', async () => {
    adminApi.getScheduledSms.mockResolvedValue({ content: [sampleRow], totalElements: 50, totalPages: 3 })
    const wrapper = mountComponent()
    await flushPromises()

    // Manually set selectedSendRequestId to simulate an open DLR
    wrapper.vm.selectedSendRequestId = 'sr-abc-001'
    await wrapper.vm.$nextTick()

    // Simulate ServerPagination page-change event
    const pagination = wrapper.findComponent(ServerPagination)
    await pagination.vm.$emit('page-change', 2)
    await flushPromises()

    expect(wrapper.vm.selectedSendRequestId).toBeNull()
  })
})
