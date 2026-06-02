import { defineStore } from 'pinia'
import { type AiApiConfig, isAiConfigComplete, loadAiConfig, saveAiConfig } from '../lib/aiConfig'

export const useAiConfigStore = defineStore('aiConfig', {
  state: () => ({
    endpoint: '',
    model: '',
    apiKey: '',
    loadedForUserId: null as number | null,
  }),
  getters: {
    isComplete(state): boolean {
      return isAiConfigComplete({
        endpoint: state.endpoint,
        model: state.model,
        apiKey: state.apiKey,
      })
    },
  },
  actions: {
    loadForUser(userId: number) {
      const config = loadAiConfig(userId)
      this.endpoint = config.endpoint
      this.model = config.model
      this.apiKey = config.apiKey
      this.loadedForUserId = userId
    },
    ensureLoaded(userId: number | undefined | null) {
      if (!userId) {
        this.resetSession()
        return
      }
      if (this.loadedForUserId !== userId) {
        this.loadForUser(userId)
      }
    },
    persistForUser(userId: number) {
      const payload: AiApiConfig = {
        endpoint: this.endpoint,
        model: this.model,
        apiKey: this.apiKey,
      }
      saveAiConfig(userId, payload)
      this.loadedForUserId = userId
    },
    resetSession() {
      this.endpoint = ''
      this.model = ''
      this.apiKey = ''
      this.loadedForUserId = null
    },
  },
})
