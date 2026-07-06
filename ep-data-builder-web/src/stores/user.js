import { defineStore } from 'pinia'

const TOKEN_KEY = 'ep_builder_token'
const USERNAME_KEY = 'ep_builder_username'
const DISPLAY_NAME_KEY = 'ep_builder_display_name'
const ROLE_KEY = 'ep_builder_role'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    username: localStorage.getItem(USERNAME_KEY) || '',
    displayName: localStorage.getItem(DISPLAY_NAME_KEY) || '',
    role: localStorage.getItem(ROLE_KEY) || ''
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.role === 'ADMIN',
    // VIEWER 只读；ADMIN/EDITOR 可写
    canWrite: (state) => state.role === 'ADMIN' || state.role === 'EDITOR'
  },
  actions: {
    setLogin({ token, username, displayName, role }) {
      this.token = token || ''
      this.username = username || ''
      this.displayName = displayName || ''
      this.role = role || ''
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USERNAME_KEY, this.username)
      localStorage.setItem(DISPLAY_NAME_KEY, this.displayName)
      localStorage.setItem(ROLE_KEY, this.role)
    },
    logout() {
      this.token = ''
      this.username = ''
      this.displayName = ''
      this.role = ''
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USERNAME_KEY)
      localStorage.removeItem(DISPLAY_NAME_KEY)
      localStorage.removeItem(ROLE_KEY)
    }
  }
})
