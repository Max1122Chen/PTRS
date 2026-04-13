import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

export type ApiResponse<T> = {
  code: number
  data: T
  message: string
}

export const http = axios.create({
  baseURL: '/',
  // 管理端会执行较长耗时任务（Python/构建），前端若超时会误报“无法连接后端/请求超时”。
  // 这里禁用 axios timeout，让后端超时控制（900s）来决定最终返回。
  timeout: 0,
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${auth.token}`
  }

  // 管理端开发工具会执行脚本/构建，可能持续数十秒以上；
  // 全局 axios timeout 20s 会导致“无法连接后端”（超时）误报。
  const url = config.url ?? ''
  if (url.includes('/api/admin/dev/generate-from-osm') || url.includes('/api/admin/dev/import-place')) {
    // 后端 generateFromSelectedOsm 内部 exec 等待最长 timeoutSec=900（15分钟）
    // 前端 timeout 必须更大，否则还没等后端返回就会触发 axios timeout。
    // 对这些“长任务”禁用 axios timeout，避免客户端在后端 exec 尚未返回时提前 abort。
    // 后端 exec(timeoutSec=900) 理论上保证 15 分钟内返回（成功/失败都返回）。
    config.timeout = 0
  }
  return config
})

http.interceptors.response.use(
  (resp) => {
    const body = resp.data as ApiResponse<unknown>
    if (body && typeof body.code === 'number') {
      if (body.code === 200) return body
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return resp.data
  },
  (err: AxiosError<any>) => {
    const status = err.response?.status
    const msg =
      err.response?.data?.message ||
      (err.code === 'ECONNABORTED'
        ? '请求超时（请稍后重试）'
        : status
          ? `网络错误（${status}）`
          : '网络错误（无法连接后端）')
    ElMessage.error(msg)
    if (status === 401) {
      const auth = useAuthStore()
      auth.clear()
    }
    return Promise.reject(err)
  },
)

