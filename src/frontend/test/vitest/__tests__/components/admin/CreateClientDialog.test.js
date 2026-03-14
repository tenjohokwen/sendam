import { installQuasarPlugin } from '@quasar/quasar-app-extension-testing-unit-vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { vi, describe, it, expect, afterEach } from 'vitest'
import messages from 'src/i18n'
import { adminApi } from 'src/api/admin'
import CreateClientDialog from 'src/components/admin/CreateClientDialog.vue'

vi.mock('src/api/admin', () => ({
  adminApi: {
    createClient: vi.fn(),
  },
}))

installQuasarPlugin()

const i18n = createI18n({ locale: 'en-US', legacy: false, globalInjection: true, messages })

/**
 * q-dialog teleports its content to document.body, so wrapper.find() cannot
 * reach dialog internals. We attach to a div appended to body, set reactive
 * state directly on wrapper.vm, and call exposed methods (onSubmit / onClose).
 */
function mountComponent(modelValue = true) {
  const div = document.createElement('div')
  document.body.appendChild(div)
  const wrapper = mount(CreateClientDialog, {
    global: { plugins: [i18n] },
    props: { modelValue },
    attachTo: div,
  })
  wrapper.vm.$q.notify = vi.fn()
  return wrapper
}

describe('CreateClientDialog', () => {
  afterEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  it('calls adminApi.createClient with name on submit', async () => {
    adminApi.createClient.mockResolvedValue({})
    const wrapper = mountComponent()
    await flushPromises()

    wrapper.vm.name = 'Test Client'
    await wrapper.vm.onSubmit()
    await flushPromises()

    expect(adminApi.createClient).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Test Client' })
    )
  })

  it('emits created event on successful submit', async () => {
    adminApi.createClient.mockResolvedValue({})
    const wrapper = mountComponent()
    await flushPromises()

    wrapper.vm.name = 'Test Client'
    await wrapper.vm.onSubmit()
    await flushPromises()

    expect(wrapper.emitted('created')).toBeTruthy()
  })

  it('emits update:modelValue=false on successful submit (dialog closes)', async () => {
    adminApi.createClient.mockResolvedValue({})
    const wrapper = mountComponent()
    await flushPromises()

    wrapper.vm.name = 'My Client'
    await wrapper.vm.onSubmit()
    await flushPromises()

    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    const lastEmit = emitted[emitted.length - 1]
    expect(lastEmit).toEqual([false])
  })

  it('does not call createClient and emits update:modelValue=false on cancel', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.vm.onClose()

    expect(adminApi.createClient).not.toHaveBeenCalled()
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    const lastEmit = emitted[emitted.length - 1]
    expect(lastEmit).toEqual([false])
  })

  it('sets hasError=true on API failure (non-validation error)', async () => {
    adminApi.createClient.mockRejectedValue(new Error('Network Error'))
    const wrapper = mountComponent()
    await flushPromises()

    wrapper.vm.name = 'Client Name'
    await wrapper.vm.onSubmit()
    await flushPromises()

    // hasError drives the q-banner v-if — confirm reactive state set
    expect(wrapper.vm.hasError).toBe(true)
    // Banner is teleported to body; document.querySelector can reach it
    expect(document.querySelector('.q-banner')).not.toBeNull()
  })
})
