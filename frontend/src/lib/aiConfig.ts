export type AiApiConfig = {
  endpoint: string
  model: string
  apiKey: string
}

const EMPTY: AiApiConfig = {
  endpoint: '',
  model: '',
  apiKey: '',
}

function storageKey(userId: number): string {
  return `travel_ai_settings_${userId}`
}

export function isAiConfigComplete(config: AiApiConfig): boolean {
  return Boolean(config.endpoint.trim() && config.model.trim() && config.apiKey.trim())
}

export function loadAiConfig(userId: number): AiApiConfig {
  try {
    const raw = localStorage.getItem(storageKey(userId))
    if (!raw) return { ...EMPTY }
    const parsed = JSON.parse(raw) as Partial<AiApiConfig>
    return {
      endpoint: typeof parsed.endpoint === 'string' ? parsed.endpoint : '',
      model: typeof parsed.model === 'string' ? parsed.model : '',
      apiKey: typeof parsed.apiKey === 'string' ? parsed.apiKey : '',
    }
  } catch {
    return { ...EMPTY }
  }
}

export function saveAiConfig(userId: number, config: AiApiConfig): void {
  localStorage.setItem(
    storageKey(userId),
    JSON.stringify({
      endpoint: config.endpoint.trim(),
      model: config.model.trim(),
      apiKey: config.apiKey.trim(),
    }),
  )
}
