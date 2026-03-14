import { installQuasarPlugin } from '@quasar/quasar-app-extension-testing-unit-vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { vi, describe, it, expect, afterEach } from 'vitest'
import messages from 'src/i18n'
import { adminApi } from 'src/api/admin'
import DlrDialog from 'src/components/admin/DlrDialog.vue'
import ServerPagination from 'src/components/common/ServerPagination.vue'

vi.mock('src/api/admin', () => ({
  adminApi: {
    getDlrForRequest: vi.fn(),
  },
}))

installQuasarPlugin()

const i18n = createI18n({ locale: 'en-US', legacy: false, globalInjection: true, messages })

const emptyPage = { content: [], totalElements: 0, totalPages: 0 }

/**
 * DlrDialog watch is on sendRequestId with { immediate: true }, so getDlrForRequest
 * is called synchronously during mount when sendRequestId is non-null. The mock MUST
 * be configured before mount.
 *
 * q-dialog teleports physical DOM to document.body; wrapper.findComponent() still
 * traverses the virtual component tree and finds nested components (e.g. ServerPagination).
 */
function mountComponent(props = {}) {
  const div = document.createElement('div')
  document.body.appendChild(div)
  return mount(DlrDialog, {
    global: { plugins: [i18n] },
    props: {
      show: true,
      sendRequestId: 'req-001',
      ...props,
    },
    attachTo: div,
  })
}

describe('DlrDialog', () => {
  afterEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  it('calls getDlrForRequest immediately on mount when sendRequestId is set (watch immediate)', async () => {
    // MUST mock BEFORE mount — watch is { immediate: true }
    adminApi.getDlrForRequest.mockResolvedValue(emptyPage)
    mountComponent()
    await flushPromises()
    expect(adminApi.getDlrForRequest).toHaveBeenCalledWith('req-001', { page: 0, size: 20 })
  })

  it('does not call getDlrForRequest when sendRequestId is null', async () => {
    adminApi.getDlrForRequest.mockResolvedValue(emptyPage)
    mountComponent({ sendRequestId: null })
    await flushPromises()
    expect(adminApi.getDlrForRequest).not.toHaveBeenCalled()
  })

  it('populates dlrRows from API response content', async () => {
    adminApi.getDlrForRequest.mockResolvedValue({
      content: [
        { id: '1', recipient: '237600000001', sendStatus: 'COMPLETED',
          gatewayMessageId: 'gw-1', providerMessageId: 'prov-1', segmentsConsumed: 1 },
      ],
      totalElements: 1,
      totalPages: 1,
    })
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.vm.dlrRows.length).toBe(1)
    expect(wrapper.vm.dlrRows[0].recipient).toBe('237600000001')
  })

  it('sets hasError=true when getDlrForRequest rejects', async () => {
    adminApi.getDlrForRequest.mockRejectedValue(new Error('Network Error'))
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.vm.hasError).toBe(true)
    expect(document.querySelector('.q-banner')).not.toBeNull()
  })

  it('does not render ServerPagination when totalPages <= 1', async () => {
    adminApi.getDlrForRequest.mockResolvedValue(emptyPage)
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.vm.totalPages).toBe(0)
    expect(wrapper.findComponent(ServerPagination).exists()).toBe(false)
  })

  it('renders ServerPagination when totalPages > 1', async () => {
    adminApi.getDlrForRequest.mockResolvedValue({
      content: [], totalElements: 50, totalPages: 3,
    })
    const wrapper = mountComponent()
    await flushPromises()
    expect(wrapper.vm.totalPages).toBe(3)
    expect(wrapper.findComponent(ServerPagination).exists()).toBe(true)
  })

  it('reloads with new sendRequestId when prop changes', async () => {
    adminApi.getDlrForRequest.mockResolvedValue(emptyPage)
    const wrapper = mountComponent({ sendRequestId: 'req-001' })
    await flushPromises()
    expect(adminApi.getDlrForRequest).toHaveBeenCalledWith('req-001', { page: 0, size: 20 })

    adminApi.getDlrForRequest.mockResolvedValue(emptyPage)
    await wrapper.setProps({ sendRequestId: 'req-002' })
    await flushPromises()

    expect(adminApi.getDlrForRequest).toHaveBeenCalledWith('req-002', { page: 0, size: 20 })
  })

  it('calls getDlrForRequest with 0-based page when onPageChange receives page 2 (currentPage=2 → API page=1)', async () => {
    adminApi.getDlrForRequest.mockResolvedValue({
      content: [], totalElements: 50, totalPages: 3,
    })
    const wrapper = mountComponent()
    await flushPromises()

    // onPageChange(page) sets currentPage=page, then loadDlr uses currentPage-1
    // ServerPagination emits page-change with 0-based value; passing 2 → currentPage=2 → API page=1
    adminApi.getDlrForRequest.mockClear()
    adminApi.getDlrForRequest.mockResolvedValue(emptyPage)
    await wrapper.vm.onPageChange(2)
    await flushPromises()

    expect(adminApi.getDlrForRequest).toHaveBeenCalledWith('req-001', { page: 1, size: 20 })
  })
})
