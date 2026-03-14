import { installQuasarPlugin } from '@quasar/quasar-app-extension-testing-unit-vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, it, expect } from 'vitest'
import messages from 'src/i18n'
import ServerPagination from 'src/components/common/ServerPagination.vue'

installQuasarPlugin()

const i18n = createI18n({ locale: 'en-US', legacy: false, globalInjection: true, messages })

function mountComponent(props = {}) {
  return mount(ServerPagination, {
    global: { plugins: [i18n] },
    props,
  })
}

describe('ServerPagination', () => {
  describe('visibility', () => {
    it('renders when totalPages > 1', () => {
      const wrapper = mountComponent({ totalElements: 50, totalPages: 3, pageSize: 20 })
      expect(wrapper.find('.row').exists()).toBe(true)
    })

    it('is hidden when totalPages is 1', () => {
      const wrapper = mountComponent({ totalElements: 5, totalPages: 1, pageSize: 20 })
      expect(wrapper.find('.row').exists()).toBe(false)
    })

    it('is hidden when totalPages is 0', () => {
      const wrapper = mountComponent({ totalElements: 0, totalPages: 0, pageSize: 20 })
      expect(wrapper.find('.row').exists()).toBe(false)
    })
  })

  describe('summary text', () => {
    it('shows correct range for first page', () => {
      const wrapper = mountComponent({ totalElements: 50, totalPages: 3, pageSize: 20, modelValue: 1 })
      // Summary: "1–20 of 50"
      expect(wrapper.text()).toContain('50')
    })
  })

  describe('page-change event', () => {
    it('emits page-change with 0-based index when QPagination changes value', async () => {
      const wrapper = mountComponent({ totalElements: 50, totalPages: 3, pageSize: 20, modelValue: 1 })
      // Simulate QPagination emitting update:model-value with page 2 (1-based)
      const pagination = wrapper.findComponent({ name: 'QPagination' })
      await pagination.vm.$emit('update:model-value', 2)
      const emitted = wrapper.emitted('page-change')
      expect(emitted).toBeTruthy()
      expect(emitted[0]).toEqual([1])  // page 2 - 1 = 1 (0-based)
    })
  })
})
