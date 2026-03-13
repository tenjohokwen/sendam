<template>
  <q-card>
    <q-card-section class="text-subtitle1 text-weight-medium">
      {{ t('admin.dashboard.billingCard') }}
    </q-card-section>

    <q-card-section class="q-pt-none">
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.activeClients') }}</span>
        <span class="text-weight-medium">{{ activeClientCount ?? '—' }}</span>
      </div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.totalCredits') }}</span>
        <span class="text-weight-medium">{{ totalCredits ?? '—' }}</span>
      </div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.smsDebit') }}</span>
        <span class="text-weight-medium">{{ creditSummary.sms_debit ?? '—' }}</span>
      </div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.smsRefund') }}</span>
        <span class="text-weight-medium">{{ creditSummary.sms_refund ?? '—' }}</span>
      </div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.topupApproved') }}</span>
        <span class="text-weight-medium">{{ creditSummary.topup_approved ?? '—' }}</span>
      </div>
      <div class="row justify-between q-py-xs">
        <span class="text-grey-7">{{ t('admin.dashboard.netConsumed') }}</span>
        <span class="text-weight-medium">{{ creditSummary.net_credits_consumed ?? '—' }}</span>
      </div>
    </q-card-section>
  </q-card>
</template>

<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps({
  creditSummary:     { type: Object, required: true },
  activeClientCount: { type: Number, required: true },
  totalCredits:      { type: Number, required: true },
})
</script>
