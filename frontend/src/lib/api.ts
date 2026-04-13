import { http, type ApiResponse } from './http'
import type { UserVO } from '../stores/auth'

export type PageData<T> = { list: T[]; total: number }

export type ScenicArea = {
  id: number
  name: string
  tags?: string[]
  description?: string
  location?: string
  longitude?: number
  latitude?: number
  type?: string
  rating?: number
  heat?: number
  openTime?: string
  ticketPrice?: string
  createTime?: string
  updateTime?: string
}

export type ScenicAreaRecommendVO = ScenicArea & {
  score?: number
  reason?: string
}

export type Facility = {
  id: number
  name: string
  type?: string
  description?: string
  location?: string
  longitude?: number
  latitude?: number
  areaId?: number
}

export type FacilityNearbyVO = Facility & { distance?: number }

export type Food = {
  id: number
  name: string
  cuisine?: string
  description?: string
  price?: number
  rating?: number
  heat?: number
  restaurantId?: number
  areaId?: number
}

export type FoodRecommendVO = Food & {
  distance?: number
  score?: number
  restaurantName?: string
  restaurantId?: number
}

export type FoodDetailVO = Food & {
  restaurantName?: string
  areaName?: string
}

export type InterestItemVO = {
  type: string
  weight: number
}

/** 与后端 tags 表一致（经预加载） */
export type TagVO = {
  id: number
  name: string
  type?: string
}

export type Diary = {
  id: number
  userId: number
  title: string
  content: string
  images?: string
  videos?: string
  heat?: number
  rating?: number
  createTime?: string
  updateTime?: string
}

export type DiaryDetailVO = Diary & {
  destinations?: number[]
  creatorNickname?: string
  comments?: any[]
}

export type RoutePlanVO = { path: number[]; distance: number; time: number }

export type RoutePoiCandidate = {
  nodeId: number
  name: string
  type?: string
  location?: string
  longitude?: number
  latitude?: number
  areaId?: number
}

export async function apiRegister(payload: { username: string; password: string; email: string; nickname: string }) {
  const res = (await http.post('/api/auth/register', payload)) as ApiResponse<{
    user_id: number
    username: string
  }>
  return res.data
}

export async function apiLogin(payload: { username: string; password: string }) {
  const res = (await http.post('/api/auth/login', payload)) as ApiResponse<{
    token: string
    user: UserVO
  }>
  return res.data
}

export async function apiRefresh(token: string) {
  const res = (await http.post(
    '/api/auth/refresh',
    {},
    { headers: { Authorization: `Bearer ${token}` } },
  )) as ApiResponse<{ token: string }>
  return res.data
}

export async function apiUpdateInterest(payload: { interests: { type: string; weight?: number }[] }) {
  const res = (await http.put('/api/auth/interest', payload)) as ApiResponse<void>
  return res.data
}

export async function apiGetInterest() {
  const res = (await http.get('/api/auth/interest')) as ApiResponse<InterestItemVO[]>
  return res.data
}

/** 标签字典（tags 表），用于筛选下拉等 */
export async function apiTagsList() {
  const res = (await http.get('/api/tags')) as ApiResponse<TagVO[]>
  return res.data ?? []
}

export async function apiTrackEngagement(payload: {
  targetType: 'SCENIC' | 'FOOD'
  targetId: number
  actionType: 'LIKE' | 'FAVORITE' | 'VIEW'
}) {
  const res = (await http.post('/api/behavior/engage', payload)) as ApiResponse<void>
  return res.data
}

export async function apiRecommendationList(params: {
  page?: number
  size?: number
  sortBy?: string
  type?: string
}) {
  const res = (await http.get('/api/recommendation', { params })) as ApiResponse<PageData<ScenicArea>>
  return res.data
}

export async function apiRecommendationPersonalized(params: {
  page?: number
  size?: number
  type?: string
  tagKeyword?: string
}) {
  const res = (await http.get('/api/recommendation/personalized', { params })) as ApiResponse<
    PageData<ScenicAreaRecommendVO>
  >
  return res.data
}

export async function apiRecommendationHot(params: { page?: number; size?: number; type?: string }) {
  const res = (await http.get('/api/recommendation/hot', { params })) as ApiResponse<PageData<ScenicArea>>
  return res.data
}

export async function apiScenicDetail(id: number) {
  const res = (await http.get(`/api/recommendation/detail/${id}`)) as ApiResponse<ScenicArea>
  return res.data
}

/** 按名称关键字筛选景区（内存匹配），供目的地下拉 */
export async function apiScenicSearchByKeyword(params: { keyword: string; limit?: number }) {
  const res = (await http.get('/api/recommendation/scenic-search', { params })) as ApiResponse<ScenicArea[]>
  return res.data
}

export async function apiMapData(params: { areaId?: number }) {
  const res = (await http.get('/api/route/map-data', { params })) as ApiResponse<{
    nodes: number[]
    nodeDetails?: {
      nodeId: number
      name: string
      type?: string
      location?: string
      longitude?: number
      latitude?: number
      areaId?: number
    }[]
    nodeGeo?: {
      nodeId: number
      type?: string
      longitude?: number
      latitude?: number
    }[]
    edges: {
      startId: number
      endId: number
      distance: number
      speed: number
      congestion: number
      vehicleType?: string
    }[]
  }>
  return res.data
}

export async function apiRoutePoiCandidates(params: { areaId?: number }) {
  const res = (await http.get('/api/route/poi-candidates', { params })) as ApiResponse<RoutePoiCandidate[]>
  return res.data ?? []
}

export async function apiPlanRoute(payload: {
  areaId?: number
  startId: number
  endId: number
  strategy?: 'distance' | 'time'
  vehicle?: 'walk' | 'bike' | 'shuttle' | string
}) {
  const res = (await http.post('/api/route', payload)) as ApiResponse<RoutePlanVO>
  return res.data
}

export async function apiPlanRouteMulti(payload: {
  areaId?: number
  points: number[]
  returnToStart?: boolean
  strategy?: 'distance' | 'time'
  vehicle?: 'walk' | 'bike' | 'shuttle' | string
}) {
  const res = (await http.post('/api/route/multi-point', payload)) as ApiResponse<RoutePlanVO>
  return res.data
}

export async function apiFacilityNearby(params: {
  lat: number
  lng: number
  radius?: number
  type?: string
  areaId?: number
}) {
  const res = (await http.get('/api/facility/nearby', { params })) as ApiResponse<FacilityNearbyVO[]>
  return res.data
}

export async function apiFacilitySearch(params: { keyword?: string; type?: string; areaId?: number; limit?: number }) {
  const res = (await http.get('/api/facility/search', { params })) as ApiResponse<Facility[]>
  return res.data
}

export async function apiFacilityDetail(id: number) {
  const res = (await http.get(`/api/facility/detail/${id}`)) as ApiResponse<Facility>
  return res.data
}

export async function apiFoodRecommendation(params: {
  areaId: number
  lat?: number
  lng?: number
  radius?: number
  wHeat?: number
  wRating?: number
  wDistance?: number
  page?: number
  size?: number
}) {
  const res = (await http.get('/api/food/recommendation', { params })) as ApiResponse<any[]>
  const list = Array.isArray(res.data) ? res.data : []
  return list.map((item) => {
    if (item && typeof item === 'object' && item.food) {
      const food = item.food as Food
      const restaurant = item.restaurant as { id?: number; name?: string } | undefined
      return {
        ...food,
        distance: item.distance,
        score: item.score,
        restaurantName: restaurant?.name,
        restaurantId: restaurant?.id,
      } as FoodRecommendVO
    }
    return item as FoodRecommendVO
  })
}

export async function apiFoodSearch(params: {
  keyword?: string
  cuisine?: string
  areaId?: number
  page?: number
  size?: number
}) {
  const res = (await http.get('/api/food/search', { params })) as ApiResponse<Food[]>
  return res.data
}

export async function apiFoodDetail(id: number) {
  const res = (await http.get(`/api/food/detail/${id}`)) as ApiResponse<Food>
  return res.data
}

export async function apiFoodDetailView(id: number) {
  const res = (await http.get(`/api/food/detail-view/${id}`)) as ApiResponse<unknown>
  const raw = res.data as FoodDetailVO & { food?: Food }
  if (raw && typeof raw === 'object' && raw.food) {
    return {
      ...raw.food,
      restaurantName: raw.restaurantName,
      areaName: raw.areaName,
    } as FoodDetailVO
  }
  return raw as FoodDetailVO
}

export async function apiFoodRate(payload: { foodId: number; rating: number; comment?: string }) {
  const res = (await http.post('/api/food/rate', payload)) as ApiResponse<void>
  return res.data
}

export async function apiDiaryCreate(payload: {
  title: string
  content: string
  images?: string[]
  videos?: string[]
  destinations?: number[]
}) {
  const res = (await http.post('/api/diary', payload)) as ApiResponse<{ diary_id: number }>
  return res.data
}

export async function apiDiaryList(params: { page?: number; size?: number; sortBy?: string }) {
  const res = (await http.get('/api/diary', { params })) as ApiResponse<Diary[]>
  return res.data
}

export async function apiDiaryDetail(id: number) {
  const res = (await http.get(`/api/diary/${id}`)) as ApiResponse<unknown>
  const raw = res.data as { diary?: Diary; destinations?: number[] } & Diary
  // 后端 DiaryDetailVO 为 { diary, destinations }，前端统一摊平为 Diary + destinations
  if (raw && typeof raw === 'object' && raw.diary) {
    return {
      ...raw.diary,
      destinations: raw.destinations ?? [],
    } as DiaryDetailVO
  }
  return raw as DiaryDetailVO
}

export async function apiDiaryUpdate(
  id: number,
  payload: {
    title: string
    content: string
    images?: string[]
    videos?: string[]
    destinations?: number[]
  },
) {
  const res = (await http.put(`/api/diary/${id}`, payload)) as ApiResponse<void>
  return res.data
}

export async function apiDiaryDelete(id: number) {
  const res = (await http.delete(`/api/diary/${id}`)) as ApiResponse<void>
  return res.data
}

export async function apiDiarySearch(params: { keyword?: string; destination?: number; page?: number; size?: number }) {
  const res = (await http.get('/api/diary/search', { params })) as ApiResponse<Diary[]>
  return res.data
}

export async function apiDiaryRate(payload: { diaryId: number; rating: number }) {
  const res = (await http.post('/api/diary/rate', payload)) as ApiResponse<void>
  return res.data
}

export async function apiAdminAddScenicArea(payload: Partial<ScenicArea>) {
  const res = (await http.post('/api/admin/scenic-area', payload)) as ApiResponse<ScenicArea>
  return res.data
}

export async function apiAdminListScenicAreas(params: { page?: number; size?: number; type?: string }) {
  const res = (await http.get('/api/admin/scenic-area', { params })) as ApiResponse<PageData<ScenicArea>>
  return res.data
}

export async function apiAdminAddPoi(payload: any) {
  const res = (await http.post('/api/admin/poi', payload)) as ApiResponse<any>
  return res.data
}

export async function apiAdminAddBuilding(payload: any) {
  // Backward-compatible wrapper for existing callers.
  const res = (await http.post('/api/admin/building', payload)) as ApiResponse<any>
  return res.data
}

export async function apiAdminAddRoad(payload: any) {
  const res = (await http.post('/api/admin/road', payload)) as ApiResponse<any>
  return res.data
}

export async function apiAdminAddFood(payload: any) {
  const res = (await http.post('/api/admin/food', payload)) as ApiResponse<any>
  return res.data
}

export async function apiAdminImportPlace(payload: { placeName: string; force?: boolean }) {
  const res = (await http.post('/api/admin/dev/import-place', payload)) as ApiResponse<{
    placeName: string
    exists: boolean
    force: boolean
    status: string
    message: string
    seedExitCode?: number
    seedOutput?: string
    buildExitCode?: number
    buildOutput?: string
  }>
  return res.data
}

export async function apiAdminSearchLocalPlace(keyword: string) {
  const res = (await http.get('/api/admin/dev/local-place-search', { params: { keyword } })) as ApiResponse<any[]>
  return res.data ?? []
}

export async function apiAdminSearchOsm(keyword: string) {
  const res = (await http.get('/api/admin/dev/osm-search', { params: { keyword } })) as ApiResponse<any[]>
  return res.data ?? []
}

export async function apiAdminGenerateFromOsm(payload: {
  placeName: string
  query: string
  selectedOsm?: {
    placeId?: string | number
    osmType?: string
    osmId?: string | number
    displayName?: string
    name?: string
  }
  force?: boolean
  buildFrontend?: boolean
}) {
  const res = (await http.post('/api/admin/dev/generate-from-osm', payload)) as ApiResponse<any>
  return res.data
}

