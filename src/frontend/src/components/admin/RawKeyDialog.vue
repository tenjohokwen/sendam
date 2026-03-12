<template>
  <q-dialog v-model="dialogVisible" persistent>
    <q-card style="min-width: 480px">
      <q-card-section class="row items-center q-pb-none">
        <div class="text-h6">{{ t('admin.apiKeys.rawKeyTitle') }}</div>
        <q-space />
      </q-card-section>

      <q-card-section>
        <q-banner class="bg-negative text-white q-mb-md" rounded>
          {{ t('admin.apiKeys.rawKeyWarning') }}
        </q-banner>

        <div class="text-caption text-grey q-mb-xs">{{ t('admin.apiKeys.id') }}: {{ keyId }}</div>

        <div
          class="q-pa-md bg-grey-2 rounded-borders q-mb-md"
          style="word-break: break-all; font-family: monospace; font-size: 0.9em;"
        >
          <code>{{ rawKey }}</code>
        </div>

        <div class="row justify-end q-gutter-sm">
          <q-btn
            color="primary"
            icon="content_copy"
            :label="t('admin.apiKeys.copyKey')"
            @click="copyKey"
          />
          <q-btn
            flat
            :label="t('common.cancel')"
            @click="dialogVisible = false"
          />
        </div>
      </q-card-section>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useQuasar } from 'quasar'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  rawKey: { type: String, required: true },
  keyId: { type: String, required: true },
})
const emit = defineEmits(['update:modelValue'])

const { t } = useI18n()
const $q = useQuasar()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

async function copyKey() {
  try {
    await navigator.clipboard.writeText(props.rawKey)
    $q.notify({ type: 'positive', message: t('admin.apiKeys.keyCopied') })
  } catch {
    $q.notify({ type: 'warning', message: t('admin.apiKeys.keyCopied') })
  }
}
</script>
