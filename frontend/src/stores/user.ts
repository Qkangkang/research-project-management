import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginCmd, UserInfo } from '@/api/types'
import { iamApi } from '@/api/iam'
import { getToken, setToken, removeToken } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref<string>(getToken() || '')
  const refreshToken = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)

  // 计算属性
  const isLoggedIn = computed(() => !!token.value)
  const userId = computed(() => userInfo.value?.userId || '')
  const username = computed(() => userInfo.value?.username || '')
  const realName = computed(() => userInfo.value?.realName || '')
  const departmentId = computed(() => userInfo.value?.departmentId || '')
  const roles = computed(() => userInfo.value?.roles || [])
  const permissions = computed(() => userInfo.value?.permissions || [])

  // 权限判断
  const hasRole = (role: string): boolean => roles.value.includes(role)

  const hasPermission = (code: string): boolean => {
    // 后端返回权限码字符串数组；`*:*:*` 为超级管理员通配权限。
    return permissions.value.includes('*:*:*') || permissions.value.includes(code)
  }

  // 登录（后端直接返回 userInfo，无需再次请求）
  const login = async (loginData: LoginCmd) => {
    const res = await iamApi.login(loginData)
    token.value = res.accessToken
    refreshToken.value = res.refreshToken
    setToken(res.accessToken, res.expiresIn)
    userInfo.value = res.userInfo
  }

  // 获取用户信息（Token 刷新后或页面刷新后调用）
  const fetchUserInfo = async () => {
    const info = await iamApi.getUserInfo()
    userInfo.value = info
  }

  // 登出
  const logout = async () => {
    try {
      await iamApi.logout()
    } finally {
      token.value = ''
      refreshToken.value = ''
      userInfo.value = null
      removeToken()
    }
  }

  // 重置状态（仅本地清理，不调用登出接口；用于会话已失效时的兜底清理）
  const reset = () => {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null
    removeToken()
  }

  return {
    token,
    refreshToken,
    userInfo,
    isLoggedIn,
    userId,
    username,
    realName,
    departmentId,
    roles,
    permissions,
    hasRole,
    hasPermission,
    login,
    fetchUserInfo,
    logout,
    reset
  }
})
