<template>
  <q-page padding>
    <div class="text-h5 q-mb-md">{{ t('admin.sms.title') }}</div>

    <!-- Error banner -->
    <q-banner v-if="hasError" class="bg-negative text-white q-mb-md" rounded>
      {{ errorMessage }}
    </q-banner>

    <div class="relative-position">
      <q-markup-table flat bordered>
        <thead>
          <tr>
            <th class="text-left">{{ t('admin.sms.id') }}</th>
            <th class="text-left">{{ t('admin.sms.clientId') }}</th>
            <th class="text-left">{{ t('admin.sms.sendRequestId') }}</th>
            <th class="text-left">{{ t('admin.sms.sender') }}</th>
            <th class="text-right">{{ t('admin.sms.messageCount') }}</th>
            <th class="text-left">{{ t('admin.sms.scheduleTime') }}</th>
            <th class="text-right">{{ t('admin.sms.reservedCredits') }}</th>
            <th class="text-center">{{ t('common.menu') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="rows.length === 0 && !isLoading">
            <td colspan="8" class="text-center text-grey">{{ t('admin.sms.noScheduled') }}</td>
          </tr>
          <tr v-for="row in rows" :key="row.id">
            <td class="text-mono text-caption">{{ row.id }}</td>
            <td class="text-mono text-caption">{{ row.clientId }}</td>
            <td class="text-mono text-caption">{{ row.sendRequestId }}</td>
            <td>{{ row.sender }}</td>
            <td class="text-right">{{ row.messageCount }}</td>
            <td>{{ row.scheduleTime }}</td>
            <td class="text-right">{{ row.reservedCredits }}</td>
            <td class="text-center">
              <q-btn
                flat
                dense
                color="primary"
                icon="list"
                @click="openDlr(row.sendRequestId)"
              />
            </td>
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

    <!-- DLR drill-down dialog -->
    <DlrDialog
      v-model:show="showDlrDialog"
      :send-request-id="selectedSendRequestId"
    />
  </q-page>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { adminApi } from 'src/api/admin'
import { useErrorHandler } from 'src/composables/useErrorHandler'
import { normalizeLongIds } from 'src/utils/longToString'
import ServerPagination from 'src/components/common/ServerPagination.vue'
import DlrDialog from 'src/components/admin/DlrDialog.vue'

const { t } = useI18n()
const { setError, clearError, hasError, errorMessage } = useErrorHandler()

const rows = ref([])
const isLoading = ref(false)
const currentPage = ref(1)
const totalElements = ref(0)
const totalPages = ref(0)
const pageSize = 20

const showDlrDialog = ref(false)
const selectedSendRequestId = ref(null)

async function loadScheduled() {
  isLoading.value = true
  clearError()
  try {
    const data = await adminApi.getScheduledSms({ page: currentPage.value - 1, size: pageSize })
    rows.value = data.content.map((row) => normalizeLongIds(row, ['id', 'clientId']))
    totalElements.value = data.totalElements
    totalPages.value = data.totalPages
  } catch (err) {
    setError(err)
  } finally {
    isLoading.value = false
  }
}

function onPageChange(page) {
  currentPage.value = page
  selectedSendRequestId.value = null  // clear DLR selection on page change
  loadScheduled()
}

function openDlr(sendRequestId) {
  selectedSendRequestId.value = sendRequestId
  showDlrDialog.value = true
}

onMounted(loadScheduled)
</script>
