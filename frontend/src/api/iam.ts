import request from './base'
import type { LoginCmd, LoginResponse, UserInfo } from './types'

export const iamApi = {
  // 登录
  login(data: LoginCmd): Promise<LoginResponse> {
    return request.post('/auth/login', data)
  },

  // 登出
  logout(): Promise<void> {
    return request.post('/auth/logout')
  },

  // 获取用户信息
  getUserInfo(): Promise<UserInfo> {
    return request.get('/auth/user-info')
  },

  // 刷新 Token
  refreshToken(): Promise<LoginResponse> {
    return request.post('/auth/refresh-token')
  }
}
