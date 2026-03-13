<template>
  <q-card>
    <q-card-section class="text-subtitle1 text-weight-medium">
      {{ t('admin.dashboard.systemCard') }}
    </q-card-section>

    <q-card-section class="q-pt-none">
      <div class="text-subtitle2 q-mb-xs">{{ t('admin.dashboard.circuitBreaker') }}</div>
      <div class="row items-center q-py-xs">
        <q-badge
          :color="cbColorMap[circuitBreaker.state] ?? 'grey'"
          :label="cbStateLabel(circuitBreaker.state)"
          class="text-subtitle2"
        />
      </div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.failureRate') }}</span>
        <span class="text-weight-medium">
          {{ circuitBreaker.failure_rate_pct != null ? circuitBreaker.failure_rate_pct.toFixed(1) + '%' : '—' }}
        </span>
      </div>
    </q-card-section>

    <q-separator />

    <q-card-section>
      <div class="text-subtitle2 q-mb-xs">{{ t('admin.dashboard.providerStats') }}</div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.totalSubmitted') }}</span>
        <span class="text-weight-medium">{{ providerStats.total_submitted ?? '—' }}</span>
      </div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.drReceived') }}</span>
        <span class="text-weight-medium">{{ providerStats.dr_received ?? '—' }}</span>
      </div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.failedCount') }}</span>
        <span class="text-weight-medium">{{ providerStats.failed_count ?? '—' }}</span>
      </div>
    </q-card-section>

    <q-separator />

    <q-card-section>
      <div class="text-subtitle2 q-mb-xs">{{ t('admin.dashboard.webhookStats') }}</div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.totalAttempts') }}</span>
        <span class="text-weight-medium">{{ webhookStats.total_attempts ?? '—' }}</span>
      </div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.failureCount') }}</span>
        <span class="text-weight-medium">{{ webhookStats.failure_count ?? '—' }}</span>
      </div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.exhaustedCount') }}</span>
        <span class="text-weight-medium">{{ webhookStats.exhausted_count ?? '—' }}</span>
      </div>
    </q-card-section>
  </q-card>
</template>

<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps({
  circuitBreaker: { type: Object, required: true },
  providerStats:  { type: Object, required: true },
  webhookStats:   { type: Object, required: true },
})

const cbColorMap = { CLOSED: 'positive', HALF_OPEN: 'warning', OPEN: 'negative' }

function cbStateLabel(state) {
  const map = {
    CLOSED:    t('admin.dashboard.cbStateClosed'),
    OPEN:      t('admin.dashboard.cbStateOpen'),
    HALF_OPEN: t('admin.dashboard.cbStateHalfOpen'),
  }
  return map[state] ?? state
}
</script>
