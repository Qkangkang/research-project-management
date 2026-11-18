// 通用类型定义

// 用户信息
export interface UserInfo {
  userId: string
  username: string      // 工号
  realName: string
  email?: string
  roles: string[]
  permissions: string[]
  departmentId?: string
  departmentName?: string
}

// 权限
export interface Permission {
  code: string
  name: string
  type: 'MENU' | 'BUTTON' | 'API'
}

// 登录请求（与后端 LoginCmd 对齐：employeeNo + password）
export interface LoginCmd {
  employeeNo: string
  password: string
}

// 登录响应（与后端 LoginVO 对齐）
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  userInfo: UserInfo
}

// 字典项
export interface DictItem {
  value: string
  label: string
  sort?: number
  extra?: Record<string, any>
}

// 选项
export interface Option {
  label: string
  value: string | number
  disabled?: boolean
  children?: Option[]
}
