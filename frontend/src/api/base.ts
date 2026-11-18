import axios, { AxiosInstance, AxiosError, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/stores/user'

// 响应拦截器已把响应体解包为 ApiResponse['data']，这里对实例方法做类型增强，
// 让 api.get<Row>(...) 直接得到业务数据类型，调用方无需 as unknown as 双重断言
declare module 'axios' {
  export interface AxiosInstance {
    get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
    delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
    post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
    put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
    patch<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  }
}

// 响应统一格式（与后端 Result.java 对齐）
export interface ApiResponse<T = any> {
  code: string           // 'OK' 表示成功，其他为错误码
  message?: string       // 错误或警告信息
  data: T
  trace_id: string       // 链路追踪 ID
  timestamp?: string     // ISO 8601 时间戳
}

// 分页响应（与后端 PageVO.java 对齐）
export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number          // 后端字段名为 size，不是 pageSize
}

// 创建 axios 实例
const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
instance.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    const token = userStore.token

    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // 添加 TraceID（前端生成）
    if (!config.headers['X-Trace-Id']) {
      config.headers['X-Trace-Id'] = generateTraceId()
    }

    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data

    // 业务成功（code === 'OK'）
    if (res.code === 'OK') {
      return res.data
    }

    // RPM-OUT-001 是 HTTP 200 的疑似重复警告，不阻断流程
    if (res.code === 'RPM-OUT-001') {
      ElMessage.warning(res.message || '检测到疑似重复成果')
      return { ...res.data, _warning: res.code }  // 附加警告标记
    }

    // 其他业务错误
    handleBusinessError(res.code, res.message)
    return Promise.reject(new Error(res.message || 'Business Error'))
  },
  (error: AxiosError<ApiResponse>) => {
    // HTTP 错误
    if (error.response) {
      const { status, data } = error.response
      const backendMessage = data?.message

      switch (status) {
        case 401:
          handleUnauthorized()
          break
        case 403:
          ElMessage.error(backendMessage || '权限不足')
          break
        case 404:
          ElMessage.error(backendMessage || '请求的资源不存在')
          break
        case 500:
          ElMessage.error(backendMessage || '服务器错误')
          break
        default:
          ElMessage.error(backendMessage || `请求失败 (${status})`)
      }
      if (backendMessage) {
        error.message = backendMessage
      }
    } else if (error.request) {
      ElMessage.error('网络错误，请检查网络连接')
    } else {
      ElMessage.error('请求配置错误')
    }

    return Promise.reject(error)
  }
)

// 业务错误处理
function handleBusinessError(errorCode?: string, errorMessage?: string) {
  // 按错误码特殊处理
  const specialCodes = ['RPM-FUND-001', 'RPM-IAM-403']

  if (errorCode && specialCodes.includes(errorCode)) {
    ElMessage.warning(errorMessage || '操作失败')
  } else {
    ElMessage.error(errorMessage || '操作失败')
  }
}

// 未授权处理（重入保护：并发多个 401 只处理一次；会话已失效时不再调用登出接口，避免二次 401 递归）
let handlingUnauthorized = false
function handleUnauthorized() {
  if (handlingUnauthorized) return
  handlingUnauthorized = true
  try {
    useUserStore().reset()
    router.push('/login')
    ElMessage.warning('登录已过期，请重新登录')
  } finally {
    setTimeout(() => { handlingUnauthorized = false }, 1000)
  }
}

// 生成 TraceID
function generateTraceId(): string {
  return `web-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`
}

// 生成幂等键（用于关键写操作）
export function generateIdempotencyKey(): string {
  return crypto.randomUUID()
}

// 分页查询参数
export interface PageQuery {
  page: number
  size: number          // 字段名为 size，不是 pageSize
  [key: string]: any
}

export default instance
