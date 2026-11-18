import type { Router } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

export function setupRouterGuards(router: Router) {
  // 全局前置守卫
  router.beforeEach(async (to, _from, next) => {
    const userStore = useUserStore()

    // 设置页面标题
    document.title = to.meta.title
      ? `${to.meta.title} - 科研项目管理系统`
      : '科研项目管理系统'

    // 匿名路由直接放行
    if (to.meta.anonymous) {
      next()
      return
    }

    // 未登录跳转登录页
    if (!userStore.isLoggedIn) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }

    // 首次加载时获取用户信息
    if (!userStore.userInfo) {
      try {
        await userStore.fetchUserInfo()
      } catch (error) {
        ElMessage.error('获取用户信息失败')
        await userStore.logout()
        next({ path: '/login', query: { redirect: to.fullPath } })
        return
      }
    }

    // 权限校验
    const requiredPermission = to.meta.permission as string | undefined
    if (requiredPermission && !userStore.hasPermission(requiredPermission)) {
      ElMessage.warning('权限不足')
      next({ path: '/403' })
      return
    }

    next()
  })

  // 全局后置守卫
  router.afterEach(() => {
    // 可以在此记录访问日志
  })
}
