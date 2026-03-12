<template>
  <q-page padding>
    <div class="row items-center justify-between q-mb-md">
      <div class="text-h5">{{ t('admin.clients.title') }}</div>
      <q-btn
        color="primary"
        icon="add"
        :label="t('admin.clients.registerClient')"
        @click="showCreateDialog = true"
      />
    </div>

    <!-- Search filter -->
    <q-input
      v-model="filterText"
      outlined
      dense
      clearable
      :placeholder="t('admin.clients.filterPlaceholder')"
      class="q-mb-md"
      style="max-width: 400px"
    >
      <template #prepend><q-icon name="search" /></template>
    </q-input>

    <!-- Error banner -->
    <q-banner v-if="hasError" class="bg-negative text-white q-mb-md" rounded>
      {{ errorMessage }}
    </q-banner>

    <!-- Client table -->
    <div class="relative-position">
      <q-markup-table flat bordered>
        <thead>
          <tr>
            <th class="text-left">{{ t('admin.clients.id') }}</th>
            <th class="text-left">{{ t('admin.clients.name') }}</th>
            <th class="text-left">{{ t('admin.clients.status') }}</th>
            <th class="text-right">{{ t('admin.clients.balance') }}</th>
            <th class="text-center">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="filteredClients.length === 0 && !isLoading">
            <td colspan="5" class="text-center text-grey">
              {{ t('admin.clients.noClients') }}
            </td>
          </tr>
          <tr v-for="client in filteredClients" :key="client.id">
            <td class="text-left text-mono text-caption">{{ client.id }}</td>
            <td class="text-left">{{ client.name }}</td>
            <td class="text-left">
              <q-badge
                :color="client.status === 'ACTIVE' ? 'positive' : 'grey'"
                :label="statusLabelMap[client.status]?.() ?? client.status"
              />
            </td>
            <td class="text-right">{{ client.balance }}</td>
            <td class="text-center">
              <q-btn
                flat
                dense
                color="primary"
                icon="key"
                :label="t('admin.clients.manageKeys')"
                @click="openManageKeys(client)"
              />
            </td>
          </tr>
        </tbody>
      </q-markup-table>

      <!-- Loading overlay -->
      <q-inner-loading :showing="isLoading">
        <q-spinner-dots size="40px" color="primary" />
      </q-inner-loading>
    </div>

    <!-- Dialogs -->
    <CreateClientDialog
      v-model="showCreateDialog"
      @created="onClientCreated"
    />
    <ApiKeysDialog
      v-if="showApiKeysDialog && selectedClient"
      v-model="showApiKeysDialog"
      :client-id="selectedClient.id"
      :client-name="selectedClient.name"
      @update:model-value="(val) => { if (!val) selectedClient = null }"
    />
  </q-page>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { adminApi } from 'src/api/admin'
import { useErrorHandler } from 'src/composables/useErrorHandler'
import { normalizeLongIds } from 'src/utils/longToString'
import CreateClientDialog from 'src/components/admin/CreateClientDialog.vue'
import ApiKeysDialog from 'src/components/admin/ApiKeysDialog.vue'

const { t } = useI18n()
const { setError, clearError, hasError, errorMessage } = useErrorHandler()

const clients = ref([])
const isLoading = ref(false)
const filterText = ref('')
const showCreateDialog = ref(false)
const selectedClient = ref(null)
const showApiKeysDialog = ref(false)

const statusLabelMap = {
  ACTIVE: () => t('admin.clients.statusActive'),
  INACTIVE: () => t('admin.clients.statusInactive'),
  DELETED: () => t('admin.clients.statusDeleted'),
}

const filteredClients = computed(() => {
  const q = filterText.value.trim().toLowerCase()
  if (!q) return clients.value
  return clients.value.filter(
    (c) => c.id.toLowerCase().includes(q) || c.name.toLowerCase().includes(q),
  )
})

onMounted(loadClients)

async function loadClients() {
  isLoading.value = true
  clearError()
  try {
    const list = await adminApi.getClients()
    clients.value = list.map((c) => normalizeLongIds(c, ['id']))
  } catch (err) {
    setError(err)
  } finally {
    isLoading.value = false
  }
}

function openManageKeys(client) {
  selectedClient.value = client
  showApiKeysDialog.value = true
}

function onClientCreated() {
  loadClients()
}
</script>
