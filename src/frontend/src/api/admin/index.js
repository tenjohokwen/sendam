import { api } from 'src/boot/axios'

export const adminApi = {
  getClients() {
    return api.get('/api/admin/clients')
  },
  createClient(data) {
    return api.post('/api/admin/clients', data)
  },
  getApiKeys(clientId) {
    return api.get(`/api/admin/clients/${clientId}/keys`)
  },
  createApiKey(clientId, label) {
    return api.post(
      `/api/admin/clients/${clientId}/keys`,
      label ? { label } : undefined
    )
  },
  revokeApiKey(clientId, keyId) {
    return api.delete(`/api/admin/clients/${clientId}/keys/${keyId}`)
  },
  getTopupHistory(params = {}) {
    // params may include: topupStatus, clientId, from, to — all optional
    return api.get('/api/admin/topups/history', { params })
  },
  approveTopup(topupId) {
    // topupId MUST be in "top_XXX" format — raw numeric id will return 404
    return api.put(`/api/admin/topups/${topupId}/approve`)
  },
  rejectTopup(topupId) {
    return api.put(`/api/admin/topups/${topupId}/reject`)
  }
}
