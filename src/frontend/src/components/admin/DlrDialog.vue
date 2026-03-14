<template>
  <q-dialog :model-value="show" @update:model-value="$emit('update:show', $event)" maximized>
    <q-card>
      <q-card-section class="row items-center q-pb-none">
        <div class="text-h6">{{ t('admin.sms.dlrTitle', { sendRequestId: sendRequestId ?? '' }) }}</div>
        <q-space />
        <q-btn icon="close" flat round dense @click="$emit('update:show', false)" />
      </q-card-section>

      <q-card-section>
        <!-- Error banner -->
        <q-banner v-if="hasError" class="bg-negative text-white q-mb-md" rounded>
          {{ errorMessage }}
        </q-banner>

        <div class="relative-position">
          <q-markup-table flat bordered>
            <thead>
              <tr>
                <th class="text-left">{{ t('admin.sms.dlrRecipient') }}</th>
                <th class="text-left">{{ t('admin.sms.dlrStatus') }}</th>
                <th class="text-left">{{ t('admin.sms.dlrGatewayMsgId') }}</th>
                <th class="text-left">{{ t('admin.sms.dlrProviderMsgId') }}</th>
                <th class="text-right">{{ t('admin.sms.dlrSegments') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="dlrRows.length === 0 && !isLoading">
                <td colspan="5" class="text-center text-grey">{{ t('admin.sms.noDlr') }}</td>
              </tr>
              <tr v-for="row in dlrRows" :key="row.id">
                <td>{{ row.recipient }}</td>
                <td>
                  <q-badge
                    :color="recipientStatusColorMap[row.sendStatus] ?? 'grey'"
                    :label="recipientStatusLabelMap[row.sendStatus]?.() ?? row.sendStatus"
                  />
                </td>
                <td class="text-mono text-caption">{{ row.gatewayMessageId ?? '—' }}</td>
                <td class="text-mono text-caption">{{ row.providerMessageId ?? '—' }}</td>
                <td class="text-right">{{ row.segmentsConsumed ?? '—' }}</td>
              </tr>
            </tbody>
          </q-markup-table>

          <q-inner-loading :showing="isLoading">
            <q-spinner-dots size="40px" color="primary" />
          </q-inner-loading>
        </div>

        <ServerPagination
          v-if="totalPages > 1"
          :total-elements="totalElements"
          :total-pages="totalPages"
          :page-size="pageSize"
          v-model="currentPage"
          @page-change="onPageChange"
          class="q-mt-md"
        />
      </q-card-section>
    </q-card>
  </q-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { adminApi } from 'src/api/admin'
import { useErrorHandler } from 'src/composables/useErrorHandler'
import { normalizeLongIds } from 'src/utils/longToString'
import ServerPagination from 'src/components/common/ServerPagination.vue'

const props = defineProps({
  show: { type: Boolean, required: true },
  sendRequestId: { type: String, default: null },
})
defineEmits(['update:show'])

const { t } = useI18n()
const { setError, clearError, hasError, errorMessage } = useErrorHandler()

const dlrRows = ref([])
const isLoading = ref(false)
const currentPage = ref(1)
const totalElements = ref(0)
const totalPages = ref(0)
const pageSize = 20

const recipientStatusColorMap = {
  ACCEPTED:       'info',
  SUBMITTED:      'warning',
  COMPLETED:      'positive',
  FAILED:         'negative',
  FINALIZED:      'positive',
  FAIL_FINALIZED: 'negative',
  CANCELLED:      'grey',
}

const recipientStatusLabelMap = {
  ACCEPTED:       () => t('admin.sms.statusAccepted'),
  SUBMITTED:      () => t('admin.sms.statusSubmitted'),
  COMPLETED:      () => t('admin.sms.statusCompleted'),
  FAILED:         () => t('admin.sms.statusFailed'),
  FINALIZED:      () => t('admin.sms.statusFinalized'),
  FAIL_FINALIZED: () => t('admin.sms.statusFailFinalized'),
  CANCELLED:      () => t('admin.sms.statusCancelled'),
}

async function loadDlr() {
  if (!props.sendRequestId) return
  isLoading.value = true
  clearError()
  try {
    const data = await adminApi.getDlrForRequest(props.sendRequestId, {
      page: currentPage.value - 1,
      size: pageSize,
    })
    dlrRows.value = data.content.map((row) => normalizeLongIds(row, ['id']))
    totalElements.value = data.totalElements
    totalPages.value = data.totalPages
  } catch (err) {
    setError(err)
  } finally {
    isLoading.value = false
  }
}

function onPageChange(page) {
  currentPage.value = page + 1
  loadDlr()
}

// Reload when sendRequestId changes; reset pagination first
watch(
  () => props.sendRequestId,
  (newId) => {
    if (newId) {
      currentPage.value = 1
      dlrRows.value = []
      loadDlr()
    } else {
      dlrRows.value = []
      totalElements.value = 0
      totalPages.value = 0
    }
  },
  { immediate: true }
)
</script>
