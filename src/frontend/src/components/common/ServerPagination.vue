<template>
  <div v-if="totalPages > 1" class="row justify-center q-mt-md">
    <q-pagination
      v-model="currentPage"
      :max="totalPages"
      :max-pages="7"
      boundary-numbers
      direction-links
      @update:model-value="onPageChange"
    />
    <div class="text-caption text-grey-7 q-ml-md self-center">
      {{ t('pagination.summary', { from: fromItem, to: toItem, total: totalElements }) }}
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps({
  totalElements: {
    type: Number,
    required: true
  },
  totalPages: {
    type: Number,
    required: true
  },
  pageSize: {
    type: Number,
    default: 20
  },
  modelValue: {
    type: Number,
    default: 1
  }
})

const emit = defineEmits(['update:modelValue', 'page-change'])

const currentPage = ref(props.modelValue)

// Keep currentPage in sync when parent resets modelValue (e.g., after filter change)
watch(() => props.modelValue, val => { currentPage.value = val })

// Compute display range for summary
const fromItem = computed(() => (currentPage.value - 1) * props.pageSize + 1)
const toItem = computed(() => Math.min(currentPage.value * props.pageSize, props.totalElements))

function onPageChange(page) {
  emit('update:modelValue', page)
  emit('page-change', page - 1) // Convert 1-based UI to 0-based Spring Data
}
</script>
