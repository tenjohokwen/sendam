<template>
  <q-page class="q-pa-md" style="position: relative">
    <!-- Page header -->
    <div class="row items-center justify-between q-mb-md">
      <div class="text-h5">{{ t('admin.dashboard.title') }}</div>
      <q-btn
        flat
        color="primary"
        icon="refresh"
        :label="t('admin.dashboard.refresh')"
        :loading="isLoading"
        @click="loadAll"
      />
    </div>

    <!-- Error banner -->
    <q-banner v-if="hasError" class="bg-negative text-white q-mb-md" rounded>
      {{ errorMessage }}
    </q-banner>

    <!-- Cards grid -->
    <div class="row q-col-gutter-md">
      <div class="col-12 col-md-4">
        <DashboardSmsCard :smsStats="smsStats" />
      </div>
      <div class="col-12 col-md-4">
        <DashboardBillingCard
          :creditSummary="creditSummary"
          :activeClientCount="activeClientCount"
          :totalCredits="totalCredits"
        />
      </div>
      <div class="col-12 col-md-4">
        <DashboardSystemCard
          :circuitBreaker="circuitBreaker"
          :providerStats="providerStats"
          :webhookStats="webhookStats"
        />
      </div>
    </div>

    <QInnerLoading :showing="isLoading" />
  </q-page>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { adminApi } from 'src/api/admin'
import { useErrorHandler } from 'src/composables/useErrorHandler'
import { normalizeLongIds } from 'src/utils/longToString'
import DashboardSmsCard     from 'src/components/admin/DashboardSmsCard.vue'
import DashboardBillingCard from 'src/components/admin/DashboardBillingCard.vue'
import DashboardSystemCard  from 'src/components/admin/DashboardSystemCard.vue'

const { t } = useI18n()
const { hasError, errorMessage, setError, clearError } = useErrorHandler()

const isLoading      = ref(false)
const clients        = ref([])
const smsStats       = ref({})
const creditSummary  = ref({})
const circuitBreaker = ref({})
const providerStats  = ref({})
const webhookStats   = ref({})

const activeClientCount = computed(() =>
  clients.value.filter(c => c.status === 'ACTIVE').length
)
const totalCredits = computed(() =>
  clients.value.reduce((sum, c) => sum + (c.balance ?? 0), 0)
)

async function loadAll() {
  isLoading.value = true
  clearError()
  try {
    const [clientsData, smsData, creditsData, cbData, providerData, webhookData] =
      await Promise.all([
        adminApi.getClients(),
        adminApi.getDeliveryStats(),
        adminApi.getCreditSummary(),
        adminApi.getCircuitBreakerHealth(),
        adminApi.getProviderStats(),
        adminApi.getWebhookHealth(),
      ])
    clients.value        = clientsData.map(c => normalizeLongIds(c, ['id']))
    smsStats.value       = smsData
    creditSummary.value  = creditsData
    circuitBreaker.value = cbData
    providerStats.value  = providerData
    webhookStats.value   = webhookData
  } catch (err) {
    setError(err)
  } finally {
    isLoading.value = false
  }
}

onMounted(loadAll)
</script>
