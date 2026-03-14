<template>
  <q-page padding>
    <div class="text-h5 q-mb-md">{{ t('admin.webhooks.title') }}</div>

    <!-- Error banner -->
    <q-banner v-if="hasError" class="bg-negative text-white q-mb-md" rounded>
      {{ errorMessage }}
    </q-banner>

    <!-- Tabs -->
    <q-tabs v-model="activeTab" align="left" class="q-mb-md">
      <q-tab name="endpoints" :label="t('admin.webhooks.tabEndpoints')" />
      <q-tab name="deliveries" :label="t('admin.webhooks.tabDeliveries')" />
    </q-tabs>

    <q-tab-panels v-model="activeTab" animated>

      <!-- Registrations tab -->
      <q-tab-panel name="endpoints" class="q-pa-none">
        <div class="relative-position">
          <q-markup-table flat bordered>
            <thead>
              <tr>
                <th class="text-left">{{ t('admin.webhooks.endpointId') }}</th>
                <th class="text-left">{{ t('admin.webhooks.clientId') }}</th>
                <th class="text-left">{{ t('admin.webhooks.publicId') }}</th>
                <th class="text-left">{{ t('admin.webhooks.url') }}</th>
                <th class="text-left">{{ t('admin.webhooks.status') }}</th>
                <th class="text-left">{{ t('admin.webhooks.createdDate') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="endpoints.length === 0 && !isEndpointsLoading">
                <td colspan="6" class="text-center text-grey">{{ t('admin.webhooks.noEndpoints') }}</td>
              </tr>
              <tr v-for="ep in endpoints" :key="ep.id">
                <td class="text-mono text-caption">{{ ep.id }}</td>
                <td class="text-mono text-caption">{{ ep.clientId }}</td>
                <td class="text-mono text-caption">{{ ep.publicId }}</td>
                <td>{{ ep.url }}</td>
                <td>
                  <q-badge
                    :color="endpointStatusColorMap[ep.status] ?? 'grey'"
                    :label="endpointStatusLabelMap[ep.status]?.() ?? ep.status"
                  />
                </td>
                <td>{{ ep.createdDate }}</td>
              </tr>
            </tbody>
          </q-markup-table>
          <q-inner-loading :showing="isEndpointsLoading">
            <q-spinner-dots size="40px" color="primary" />
          </q-inner-loading>
        </div>
        <ServerPagination
          v-if="endpointsTotalPages > 1"
          :total-elements="endpointsTotalElements"
          :total-pages="endpointsTotalPages"
          :page-size="pageSize"
          v-model="endpointsPage"
          @page-change="onEndpointsPageChange"
          class="q-mt-md"
        />
      </q-tab-panel>

      <!-- Deliveries tab -->
      <q-tab-panel name="deliveries" class="q-pa-none">
        <q-select
          v-model="attemptStatusFilter"
          :options="attemptStatusOptions"
          :label="t('admin.webhooks.filterAttemptStatus')"
          emit-value
          map-options
          clearable
          class="q-mb-md"
          style="max-width: 260px"
          @update:model-value="onFilterChange"
        />
        <div class="relative-position">
          <q-markup-table flat bordered>
            <thead>
              <tr>
                <th class="text-left">{{ t('admin.webhooks.deliveryId') }}</th>
                <th class="text-left">{{ t('admin.webhooks.clientId') }}</th>
                <th class="text-left">{{ t('admin.webhooks.sendRequestId') }}</th>
                <th class="text-left">{{ t('admin.webhooks.recipient') }}</th>
                <th class="text-left">{{ t('admin.webhooks.deliveryStatus') }}</th>
                <th class="text-left">{{ t('admin.webhooks.attemptStatus') }}</th>
                <th class="text-right">{{ t('admin.webhooks.attemptCount') }}</th>
                <th class="text-left">{{ t('admin.webhooks.lastAttemptAt') }}</th>
                <th class="text-right">{{ t('admin.webhooks.httpStatus') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="deliveries.length === 0 && !isDeliveriesLoading">
                <td colspan="9" class="text-center text-grey">{{ t('admin.webhooks.noDeliveries') }}</td>
              </tr>
              <tr v-for="d in deliveries" :key="d.id">
                <td class="text-mono text-caption">{{ d.id }}</td>
                <td class="text-mono text-caption">{{ d.clientId }}</td>
                <td class="text-mono text-caption">{{ d.sendRequestId }}</td>
                <td>{{ d.recipient }}</td>
                <td>
                  <q-badge
                    :color="d.deliveryStatus === 'DELIVERED' ? 'positive' : 'negative'"
                    :label="d.deliveryStatus"
                  />
                </td>
                <td>
                  <q-badge
                    :color="attemptStatusColorMap[d.attemptStatus] ?? 'grey'"
                    :label="attemptStatusLabelMap[d.attemptStatus]?.() ?? d.attemptStatus"
                  />
                </td>
                <td class="text-right">{{ d.attemptCount }}</td>
                <td>{{ d.lastAttemptAt ?? '—' }}</td>
                <td class="text-right">{{ d.httpStatus ?? '—' }}</td>
              </tr>
            </tbody>
          </q-markup-table>
          <q-inner-loading :showing="isDeliveriesLoading">
            <q-spinner-dots size="40px" color="primary" />
          </q-inner-loading>
        </div>
        <ServerPagination
          v-if="deliveriesTotalPages > 1"
          :total-elements="deliveriesTotalElements"
          :total-pages="deliveriesTotalPages"
          :page-size="pageSize"
          v-model="deliveriesPage"
          @page-change="onDeliveriesPageChange"
          class="q-mt-md"
        />
      </q-tab-panel>

    </q-tab-panels>
  </q-page>
</template>

<script setup>
import { ref, watch, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { adminApi } from 'src/api/admin'
import { useErrorHandler } from 'src/composables/useErrorHandler'
import { normalizeLongIds } from 'src/utils/longToString'
import ServerPagination from 'src/components/common/ServerPagination.vue'

const { t } = useI18n()
const { setError, clearError, hasError, errorMessage } = useErrorHandler()

const activeTab = ref('endpoints')
const pageSize = 20

// Endpoints tab state
const endpoints = ref([])
const isEndpointsLoading = ref(false)
const endpointsPage = ref(1)
const endpointsTotalElements = ref(0)
const endpointsTotalPages = ref(0)

// Deliveries tab state
const deliveries = ref([])
const isDeliveriesLoading = ref(false)
const deliveriesPage = ref(1)
const deliveriesTotalElements = ref(0)
const deliveriesTotalPages = ref(0)
const attemptStatusFilter = ref(null)

const endpointStatusColorMap = { ACTIVE: 'positive', INACTIVE: 'grey', DELETED: 'negative' }
const endpointStatusLabelMap = {
  ACTIVE:   () => t('admin.webhooks.endpointStatusActive'),
  INACTIVE: () => t('admin.webhooks.endpointStatusInactive'),
  DELETED:  () => t('admin.webhooks.endpointStatusDeleted'),
}
const attemptStatusColorMap = { PENDING: 'warning', DELIVERED: 'positive', FAILED: 'negative', EXHAUSTED: 'grey' }
const attemptStatusLabelMap = {
  PENDING:   () => t('admin.webhooks.attemptPending'),
  DELIVERED: () => t('admin.webhooks.attemptDelivered'),
  FAILED:    () => t('admin.webhooks.attemptFailed'),
  EXHAUSTED: () => t('admin.webhooks.attemptExhausted'),
}

const attemptStatusOptions = computed(() => [
  { label: t('admin.webhooks.filterAll'),          value: null },
  { label: t('admin.webhooks.attemptPending'),      value: 'PENDING' },
  { label: t('admin.webhooks.attemptDelivered'),    value: 'DELIVERED' },
  { label: t('admin.webhooks.attemptFailed'),       value: 'FAILED' },
  { label: t('admin.webhooks.attemptExhausted'),    value: 'EXHAUSTED' },
])

async function loadEndpoints() {
  isEndpointsLoading.value = true
  clearError()
  try {
    const data = await adminApi.getWebhookEndpoints({ page: endpointsPage.value - 1, size: pageSize })
    endpoints.value = data.content.map((ep) => normalizeLongIds(ep, ['id', 'clientId']))
    endpointsTotalElements.value = data.totalElements
    endpointsTotalPages.value = data.totalPages
  } catch (err) {
    setError(err)
  } finally {
    isEndpointsLoading.value = false
  }
}

async function loadDeliveries() {
  isDeliveriesLoading.value = true
  clearError()
  try {
    const params = { page: deliveriesPage.value - 1, size: pageSize }
    if (attemptStatusFilter.value) params.attemptStatus = attemptStatusFilter.value
    const data = await adminApi.getWebhookDeliveries(params)
    deliveries.value = data.content.map((d) => normalizeLongIds(d, ['id', 'clientId']))
    deliveriesTotalElements.value = data.totalElements
    deliveriesTotalPages.value = data.totalPages
  } catch (err) {
    setError(err)
  } finally {
    isDeliveriesLoading.value = false
  }
}

function loadForTab() {
  if (activeTab.value === 'endpoints') loadEndpoints()
  else loadDeliveries()
}

function onEndpointsPageChange(page) { endpointsPage.value = page + 1; loadEndpoints() }
function onDeliveriesPageChange(page) { deliveriesPage.value = page + 1; loadDeliveries() }
function onFilterChange() { deliveriesPage.value = 1; loadDeliveries() }

watch(activeTab, loadForTab)
onMounted(loadForTab)
</script>
