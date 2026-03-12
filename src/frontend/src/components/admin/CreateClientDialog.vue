<template>
  <q-dialog v-model="dialogVisible" persistent @hide="onClose">
    <q-card style="min-width: 400px">
      <q-card-section class="row items-center q-pb-none">
        <div class="text-h6">{{ t('admin.clients.createTitle') }}</div>
        <q-space />
        <q-btn icon="close" flat round dense @click="onClose" />
      </q-card-section>

      <q-card-section>
        <!-- Error banner (non-field errors only) -->
        <q-banner
          v-if="hasError && !isValidationError"
          class="bg-negative text-white q-mb-md"
          rounded
        >
          {{ errorMessage }}
        </q-banner>

        <q-form @submit.prevent="onSubmit">
          <q-input
            v-model="name"
            :label="t('admin.clients.clientName')"
            outlined
            :lazy-rules="true"
            :rules="[
              (val) => !!val || t('validation.required'),
              (val) => val.length >= 2 || t('validation.minLength', { min: 2 }),
              (val) => val.length <= 100 || t('validation.maxLength', { max: 100 }),
            ]"
            :error="hasFieldError('name')"
            :error-message="getFieldError('name')"
            class="q-mb-md"
          />
          <q-input
            v-model="keyLabel"
            :label="t('admin.clients.keyLabel')"
            outlined
            :lazy-rules="true"
            :rules="[
              (val) =>
                !val || val.length <= 100 || t('validation.maxLength', { max: 100 }),
            ]"
            :error="hasFieldError('keyLabel')"
            :error-message="getFieldError('keyLabel')"
            class="q-mb-md"
          />

          <div class="row justify-end q-gutter-sm">
            <q-btn
              flat
              :label="t('common.cancel')"
              :disable="isSubmitting"
              @click="onClose"
            />
            <q-btn
              type="submit"
              color="primary"
              :label="t('admin.clients.registerClient')"
              :loading="isSubmitting"
              :disable="isSubmitting"
            />
          </div>
        </q-form>
      </q-card-section>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useQuasar } from 'quasar'
import { adminApi } from 'src/api/admin'
import { useErrorHandler } from 'src/composables/useErrorHandler'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
})
const emit = defineEmits(['update:modelValue', 'created'])

const { t } = useI18n()
const $q = useQuasar()
const { setError, clearError, hasError, errorMessage, isValidationError, hasFieldError, getFieldError } =
  useErrorHandler()

const isSubmitting = ref(false)
const name = ref('')
const keyLabel = ref('')

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

function onClose() {
  name.value = ''
  keyLabel.value = ''
  clearError()
  dialogVisible.value = false
}

async function onSubmit() {
  isSubmitting.value = true
  clearError()
  try {
    const payload = { name: name.value }
    if (keyLabel.value.trim()) payload.keyLabel = keyLabel.value.trim()
    await adminApi.createClient(payload)
    $q.notify({ type: 'positive', message: t('admin.clients.created') })
    emit('created')
    onClose()
  } catch (err) {
    setError(err)
  } finally {
    isSubmitting.value = false
  }
}
</script>
