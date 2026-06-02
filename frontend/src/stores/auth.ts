import { defineStore } from 'pinia'

export type UserVO = {
  id: number
  username: string
  role?: string
  interests?: string[]
  avatar?: string
}

const LS_TOKEN = 'travel_token'
const LS_USER = 'travel_user'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(LS_TOKEN) ?? '',
    user: (localStorage.getItem(LS_USER) ? (JSON.parse(localStorage.getItem(LS_USER)!) as UserVO) : null) as
      | UserVO
      | null,
  }),
  getters: {
    isAuthed: (s) => Boolean(s.token),
  },
  actions: {
    setAuth(token: string, user: UserVO) {
      this.token = token
      this.user = user
      localStorage.setItem(LS_TOKEN, token)
      localStorage.setItem(LS_USER, JSON.stringify(user))
    },
    patchUser(partial: Partial<UserVO>) {
      if (!this.user) return
      this.user = { ...this.user, ...partial }
      localStorage.setItem(LS_USER, JSON.stringify(this.user))
    },
    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem(LS_TOKEN)
      localStorage.removeItem(LS_USER)
    },
  },
})

