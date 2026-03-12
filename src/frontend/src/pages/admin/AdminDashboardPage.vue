<template>
  <q-page class="q-pa-md">
    <div class="text-h5 q-mb-md">{{ t('admin.nav.dashboard') }}</div>
    <p class="text-grey-7">Loading...</p>
  </q-page>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>
