import { defineStore } from 'pinia'
import { profileApi } from 'src/api/profile.api'

export const useUserStore = defineStore('user', {
  state: () => ({
    authorities: [],
    username: ''
  }),
  getters: {
    isAdmin: (state) => state.authorities.includes('ROLE_ADMIN'),
    isLoaded: (state) => state.authorities.length > 0 || state.username !== ''
  },
  actions: {
    async fetchUser() {
      const user = await profileApi.getProfile()
      this.authorities = user.authorities ?? []
      this.username = user.login ?? user.email ?? ''
    },
    reset() {
      this.authorities = []
      this.username = ''
    }
  }
})
