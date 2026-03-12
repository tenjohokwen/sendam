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
  }
}
