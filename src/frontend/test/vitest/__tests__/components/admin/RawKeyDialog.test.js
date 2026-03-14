import { installQuasarPlugin } from '@quasar/quasar-app-extension-testing-unit-vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest'
import messages from 'src/i18n'
import RawKeyDialog from 'src/components/admin/RawKeyDialog.vue'

installQuasarPlugin()

const i18n = createI18n({ locale: 'en-US', legacy: false, globalInjection: true, messages })

/**
 * q-dialog teleports its content to document.body. Attach to a div in body
 * so the teleport shares the same document. Use document.body.textContent for
 * text assertions and wrapper.vm methods for interactions.
 */
function mountComponent() {
  const div = document.createElement('div')
  document.body.appendChild(div)
  const wrapper = mount(RawKeyDialog, {
    global: { plugins: [i18n] },
    props: {
      modelValue: true,
      rawKey: 'sk_test_abc123xyz',
      keyId: '9876543210',
    },
    attachTo: div,
  })
  wrapper.vm.$q.notify = vi.fn()
  return wrapper
}

describe('RawKeyDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Mock clipboard API (not available in jsdom)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      writable: true,
      configurable: true,
    })
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('displays the rawKey value', async () => {
    mountComponent()
    await flushPromises()
    expect(document.body.textContent).toContain('sk_test_abc123xyz')
  })

  it('displays the keyId', async () => {
    mountComponent()
    await flushPromises()
    expect(document.body.textContent).toContain('9876543210')
  })

  it('calls clipboard.writeText with rawKey on copy button click', async () => {
    const wrapper = mountComponent()
    await flushPromises()
    await wrapper.vm.copyKey()
    await flushPromises()
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('sk_test_abc123xyz')
  })

  it('emits update:modelValue=false on cancel (dialogVisible set to false)', async () => {
    const wrapper = mountComponent()
    await flushPromises()
    // Setting dialogVisible to false triggers the computed setter which emits update:modelValue
    wrapper.vm.dialogVisible = false
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })
})
