<template>
  <q-dialog v-model="dialogVisible" full-width @hide="onDialogClosed">
    <q-card>
      <q-card-section class="row items-center q-pb-none">
        <div class="text-h6">{{ t('admin.apiKeys.title', { name: clientName }) }}</div>
        <q-space />
        <q-btn icon="close" flat round dense @click="close" />
      </q-card-section>

      <q-card-section>
        <!-- Error banner -->
        <q-banner v-if="hasError" class="bg-negative text-white q-mb-md" rounded>
          {{ errorMessage }}
        </q-banner>

        <!-- Generate key row -->
        <div class="row items-center q-gutter-sm q-mb-md">
          <q-input
            v-model="newKeyLabel"
            :placeholder="t('admin.apiKeys.keyLabelOptional')"
            outlined
            dense
            style="min-width: 200px"
            :disable="isGenerating"
          />
          <q-btn
            color="primary"
            icon="add"
            :label="t('admin.apiKeys.generateKey')"
            :loading="isGenerating"
            :disable="isGenerating"
            @click="generateKey"
          />
        </div>

        <!-- Keys table -->
        <div class="relative-position">
          <q-markup-table flat bordered>
            <thead>
              <tr>
                <th class="text-left">{{ t('admin.apiKeys.id') }}</th>
                <th class="text-left">{{ t('admin.apiKeys.label') }}</th>
                <th class="text-left">{{ t('admin.apiKeys.status') }}</th>
                <th class="text-left">{{ t('admin.apiKeys.createdDate') }}</th>
                <th class="text-center">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="keys.length === 0 && !isLoading">
                <td colspan="5" class="text-center text-grey">
                  {{ t('admin.apiKeys.noKeys') }}
                </td>
              </tr>
              <tr v-for="key in keys" :key="key.id">
                <td class="text-mono text-caption">{{ key.id }}</td>
                <td>{{ key.label ?? '—' }}</td>
                <td>
                  <q-badge
                    :color="key.status === 'ACTIVE' ? 'positive' : 'grey'"
                    :label="key.status === 'ACTIVE' ? t('admin.apiKeys.statusActive') : t('admin.apiKeys.statusRevoked')"
                  />
                </td>
                <td>{{ new Date(key.createdDate).toLocaleDateString() }}</td>
                <td class="text-center">
                  <q-btn
                    flat
                    dense
                    color="negative"
                    icon="block"
                    :label="t('admin.apiKeys.revoke')"
                    :loading="!!isRevoking[key.id]"
                    :disable="key.status !== 'ACTIVE' || !!isRevoking[key.id]"
                    @click="revokeKey(key.id)"
                  />
                </td>
              </tr>
            </tbody>
          </q-markup-table>

          <q-inner-loading :showing="isLoading">
            <q-spinner-dots size="40px" color="primary" />
          </q-inner-loading>
        </div>
      </q-card-section>
    </q-card>

    <!-- Raw key dialog: v-if so it unmounts on close (AKEY-04) -->
    <RawKeyDialog
      v-if="showRawKeyDialog"
      v-model="showRawKeyDialog"
      :raw-key="rawKeyResult?.rawKey ?? ''"
      :key-id="rawKeyResult?.apiKeyId ?? ''"
      @update:model-value="onRawKeyDialogClose"
    />
  </q-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useQuasar } from 'quasar'
import { adminApi } from 'src/api/admin'
import { useErrorHandler } from 'src/composables/useErrorHandler'
import { normalizeLongIds, longToString } from 'src/utils/longToString'
import RawKeyDialog from './RawKeyDialog.vue'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  clientId: { type: String, required: true },
  clientName: { type: String, required: true },
})
const emit = defineEmits(['update:modelValue'])

const { t } = useI18n()
const $q = useQuasar()
const { setError, clearError, hasError, errorMessage } = useErrorHandler()

const keys = ref([])
const isLoading = ref(false)
const isGenerating = ref(false)
const isRevoking = ref({})         // map: keyId (string) -> boolean
const newKeyLabel = ref('')         // optional label for generate
const rawKeyResult = ref(null)      // ApiKeyCreationResult after generation
const showRawKeyDialog = ref(false)

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

// Load keys when dialog opens
watch(
  () => props.modelValue,
  (open) => {
    if (open) loadKeys()
    else onDialogClosed()
  },
)

async function loadKeys() {
  isLoading.value = true
  clearError()
  try {
    const list = await adminApi.getApiKeys(props.clientId)
    keys.value = list.map((k) => normalizeLongIds(k, ['id']))
  } catch (err) {
    setError(err)
  } finally {
    isLoading.value = false
  }
}

async function generateKey() {
  isGenerating.value = true
  clearError()
  try {
    const result = await adminApi.createApiKey(
      props.clientId,
      newKeyLabel.value.trim() || undefined,
    )
    // Normalize Long id fields
    result.apiKeyId = longToString(result.apiKeyId)
    rawKeyResult.value = result
    showRawKeyDialog.value = true
    newKeyLabel.value = ''
    $q.notify({ type: 'positive', message: t('admin.apiKeys.generated') })
    await loadKeys()
  } catch (err) {
    setError(err)
  } finally {
    isGenerating.value = false
  }
}

async function revokeKey(keyId) {
  isRevoking.value = { ...isRevoking.value, [keyId]: true }
  clearError()
  try {
    await adminApi.revokeApiKey(props.clientId, keyId)
    $q.notify({ type: 'positive', message: t('admin.apiKeys.revoked') })
    await loadKeys()
  } catch (err) {
    setError(err)
  } finally {
    const updated = { ...isRevoking.value }
    delete updated[keyId]
    isRevoking.value = updated
  }
}

function onRawKeyDialogClose() {
  showRawKeyDialog.value = false
  rawKeyResult.value = null  // Drop the raw key — garbage collectable
}

function onDialogClosed() {
  keys.value = []
  newKeyLabel.value = ''
  rawKeyResult.value = null
  showRawKeyDialog.value = false
  clearError()
}

function close() {
  dialogVisible.value = false
}
</script>
