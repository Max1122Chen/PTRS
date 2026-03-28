const INTEREST_LABEL_ZH: Record<string, string> = {
  nature: '自然',
  lake: '湖泊',
  history: '历史',
  culture: '文化',
  campus: '校园',
  photo: '摄影',
  museum: '博物馆',
  art: '艺术',
  science: '科学',
  architecture: '建筑',
  night: '夜景',
  hiking: '徒步',
  walk: '漫步',
  food: '美食',
}

const INTEREST_ALIASES: Record<string, string> = {
  自然: 'nature',
  山岳: 'nature',
  湖泊: 'lake',
  湖: 'lake',
  历史: 'history',
  文化: 'culture',
  校园: 'campus',
  摄影: 'photo',
  拍照: 'photo',
  博物馆: 'museum',
  艺术: 'art',
  科学: 'science',
  建筑: 'architecture',
  夜景: 'night',
  夜游: 'night',
  徒步: 'hiking',
  漫步: 'walk',
  美食: 'food',
}

export function normalizeInterestKey(raw: string): string {
  const trimmed = (raw || '').trim()
  if (!trimmed) return ''
  const lowered = trimmed.toLowerCase()
  return INTEREST_ALIASES[trimmed] ?? INTEREST_ALIASES[lowered] ?? lowered
}

export function interestLabelZh(raw: string): string {
  const key = normalizeInterestKey(raw)
  return INTEREST_LABEL_ZH[key] ?? (raw || '').trim()
}

export function roundTwo(value: number): number {
  if (!Number.isFinite(value)) return 0
  return Math.round(value * 100) / 100
}

export const COMMON_INTEREST_KEYS = [
  'nature',
  'history',
  'museum',
  'culture',
  'photo',
  'food',
  'science',
  'art',
  'night',
  'campus',
  'lake',
  'architecture',
  'hiking',
  'walk',
] as const
