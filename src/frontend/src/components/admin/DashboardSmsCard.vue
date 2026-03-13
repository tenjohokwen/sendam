<template>
  <q-card>
    <q-card-section class="text-subtitle1 text-weight-medium">
      {{ t('admin.dashboard.smsCard') }}
    </q-card-section>

    <q-card-section class="q-pt-none">
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.totalSent') }}</span>
        <span class="text-weight-medium">{{ smsStats.total_sent ?? '—' }}</span>
      </div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.delivered') }}</span>
        <span class="text-weight-medium">{{ smsStats.delivered ?? '—' }}</span>
      </div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.failed') }}</span>
        <span class="text-weight-medium">{{ smsStats.failed ?? '—' }}</span>
      </div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.deliveryRate') }}</span>
        <span class="text-weight-medium">{{ formatPct(smsStats.delivery_rate) }}</span>
      </div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.totalSegments') }}</span>
        <span class="text-weight-medium">{{ smsStats.total_segments ?? '—' }}</span>
      </div>
    </q-card-section>

    <template v-if="smsStats.daily_breakdown && smsStats.daily_breakdown.length">
      <q-separator />
      <q-card-section>
        <div class="text-subtitle2 q-mb-sm">{{ t('admin.dashboard.dailyBreakdown') }}</div>
        <q-markup-table flat bordered dense>
          <thead>
            <tr>
              <th class="text-left">{{ t('admin.dashboard.date') }}</th>
              <th class="text-right">{{ t('admin.dashboard.totalSent') }}</th>
              <th class="text-right">{{ t('admin.dashboard.delivered') }}</th>
              <th class="text-right">{{ t('admin.dashboard.failed') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in smsStats.daily_breakdown" :key="row.date">
              <td>{{ row.date }}</td>
              <td class="text-right">{{ row.total_sent ?? '—' }}</td>
              <td class="text-right">{{ row.delivered ?? '—' }}</td>
              <td class="text-right">{{ row.failed ?? '—' }}</td>
            </tr>
          </tbody>
        </q-markup-table>
      </q-card-section>
    </template>
  </q-card>
</template>

<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps({
  smsStats: { type: Object, required: true },
})

function formatPct(rate) {
  return rate != null ? (rate * 100).toFixed(1) + '%' : '—'
}
</script>
