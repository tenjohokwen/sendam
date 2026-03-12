<template>
  <q-page padding>
    <div class="text-h5 q-mb-md">{{ t('admin.topups.title') }}</div>

    <!-- Error banner -->
    <q-banner v-if="hasError" class="bg-negative text-white q-mb-md" rounded>
      {{ errorMessage }}
    </q-banner>

    <!-- Tabs -->
    <q-tabs v-model="activeTab" align="left" class="q-mb-md">
      <q-tab name="pending" :label="t('admin.topups.tabPending')" />
      <q-tab name="history" :label="t('admin.topups.tabHistory')" />
    </q-tabs>

    <q-tab-panels v-model="activeTab" animated>
      <!-- Pending tab -->
      <q-tab-panel name="pending" class="q-pa-none">
        <div class="relative-position">
          <q-markup-table flat bordered>
            <thead>
              <tr>
                <th class="text-left">{{ t('admin.topups.id') }}</th>
                <th class="text-left">{{ t('admin.topups.clientId') }}</th>
                <th class="text-right">{{ t('admin.topups.amount') }}</th>
                <th class="text-left">{{ t('admin.topups.transactionId') }}</th>
                <th class="text-left">{{ t('admin.topups.paymentType') }}</th>
                <th class="text-left">{{ t('admin.topups.accountNumber') }}</th>
                <th class="text-left">{{ t('admin.topups.requestDate') }}</th>
                <th class="text-center">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="pendingTopups.length === 0 && !isPendingLoading">
                <td colspan="8" class="text-center text-grey">
                  {{ t('admin.topups.noPending') }}
                </td>
              </tr>
              <tr v-for="item in pendingTopups" :key="item.topupId">
                <td class="text-mono text-caption">{{ item.topupId }}</td>
                <td class="text-mono text-caption">{{ item.client_id }}</td>
                <td class="text-right">{{ item.amount }}</td>
                <td>{{ item.transaction_id }}</td>
                <td>{{ item.payment_type }}</td>
                <td>{{ item.account_number }}</td>
                <td>{{ item.created_at }}</td>
                <td class="text-center q-gutter-xs">
                  <q-btn
                    flat
                    dense
                    color="positive"
                    icon="check"
                    :label="t('admin.topups.approve')"
                    :loading="!!isApproving[item.topupId]"
                    :disable="!!isApproving[item.topupId] || !!isRejecting[item.topupId]"
                    @click="approveTopup(item.topupId)"
                  />
                  <q-btn
                    flat
                    dense
                    color="negative"
                    icon="close"
                    :label="t('admin.topups.reject')"
                    :loading="!!isRejecting[item.topupId]"
                    :disable="!!isApproving[item.topupId] || !!isRejecting[item.topupId]"
                    @click="rejectTopup(item.topupId)"
                  />
                </td>
              </tr>
            </tbody>
          </q-markup-table>

          <q-inner-loading :showing="isPendingLoading">
            <q-spinner-dots size="40px" color="primary" />
          </q-inner-loading>
        </div>
      </q-tab-panel>

      <!-- History tab -->
      <q-tab-panel name="history" class="q-pa-none">
        <div class="relative-position">
          <q-markup-table flat bordered>
            <thead>
              <tr>
                <th class="text-left">{{ t('admin.topups.id') }}</th>
                <th class="text-left">{{ t('admin.topups.clientId') }}</th>
                <th class="text-right">{{ t('admin.topups.amount') }}</th>
                <th class="text-left">{{ t('admin.topups.transactionId') }}</th>
                <th class="text-left">{{ t('admin.topups.paymentType') }}</th>
                <th class="text-left">{{ t('admin.topups.accountNumber') }}</th>
                <th class="text-left">{{ t('admin.topups.requestDate') }}</th>
                <th class="text-left">{{ t('admin.topups.processedDate') }}</th>
                <th class="text-left">{{ t('admin.topups.status') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="historyTopups.length === 0 && !isHistoryLoading">
                <td colspan="9" class="text-center text-grey">
                  {{ t('admin.topups.noHistory') }}
                </td>
              </tr>
              <tr v-for="item in historyTopups" :key="item.id">
                <td class="text-mono text-caption">{{ item.id }}</td>
                <td class="text-mono text-caption">{{ item.client_id }}</td>
                <td class="text-right">{{ item.amount }}</td>
                <td>{{ item.transaction_id }}</td>
                <td>{{ item.payment_type }}</td>
                <td>{{ item.account_number }}</td>
                <td>{{ item.created_at }}</td>
                <td>{{ item.approved_at ?? '—' }}</td>
                <td>
                  <q-badge
                    :color="statusColorMap[item.topup_status] ?? 'grey'"
                    :label="statusLabelMap[item.topup_status]?.() ?? item.topup_status"
                  />
                </td>
              </tr>
            </tbody>
          </q-markup-table>

          <q-inner-loading :showing="isHistoryLoading">
            <q-spinner-dots size="40px" color="primary" />
          </q-inner-loading>
        </div>
      </q-tab-panel>
    </q-tab-panels>
  </q-page>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useQuasar } from 'quasar'
import { adminApi } from 'src/api/admin'
import { useErrorHandler } from 'src/composables/useErrorHandler'
import { normalizeLongIds } from 'src/utils/longToString'

const { t } = useI18n()
const $q = useQuasar()
const { setError, clearError, hasError, errorMessage } = useErrorHandler()

const activeTab = ref('pending')
const pendingTopups = ref([])
const historyTopups = ref([])
const isPendingLoading = ref(false)
const isHistoryLoading = ref(false)
const isApproving = ref({})   // keyed by topupId string
const isRejecting = ref({})   // keyed by topupId string

const statusLabelMap = {
  PENDING_APPROVAL: () => t('admin.topups.statusPending'),
  APPROVED:         () => t('admin.topups.statusApproved'),
  REJECTED:         () => t('admin.topups.statusRejected'),
}

const statusColorMap = {
  PENDING_APPROVAL: 'warning',
  APPROVED:         'positive',
  REJECTED:         'negative',
}

async function loadPending() {
  isPendingLoading.value = true
  clearError()
  try {
    const data = await adminApi.getTopupHistory({ topupStatus: 'PENDING_APPROVAL' })
    pendingTopups.value = data.topups.map((item) => ({
      topupId: 'top_' + item.id,  // pre-build BEFORE normalizeLongIds converts id to string
      ...normalizeLongIds(item, ['id', 'client_id']),
    }))
  } catch (err) {
    setError(err)
  } finally {
    isPendingLoading.value = false
  }
}

async function loadHistory() {
  isHistoryLoading.value = true
  clearError()
  try {
    const data = await adminApi.getTopupHistory()
    historyTopups.value = data.topups.map((item) => ({
      ...normalizeLongIds(item, ['id', 'client_id']),
    }))
  } catch (err) {
    setError(err)
  } finally {
    isHistoryLoading.value = false
  }
}

function loadForTab() {
  if (activeTab.value === 'pending') loadPending()
  else loadHistory()
}

async function approveTopup(topupId) {
  isApproving.value = { ...isApproving.value, [topupId]: true }
  clearError()
  try {
    await adminApi.approveTopup(topupId)
    $q.notify({ type: 'positive', message: t('admin.topups.approved') })
    await loadPending()
  } catch (err) {
    setError(err)
  } finally {
    const updated = { ...isApproving.value }
    delete updated[topupId]
    isApproving.value = updated
  }
}

async function rejectTopup(topupId) {
  isRejecting.value = { ...isRejecting.value, [topupId]: true }
  clearError()
  try {
    await adminApi.rejectTopup(topupId)
    $q.notify({ type: 'positive', message: t('admin.topups.rejected') })
    await loadPending()
  } catch (err) {
    setError(err)
  } finally {
    const updated = { ...isRejecting.value }
    delete updated[topupId]
    isRejecting.value = updated
  }
}

watch(activeTab, loadForTab)
onMounted(loadForTab)
</script>
