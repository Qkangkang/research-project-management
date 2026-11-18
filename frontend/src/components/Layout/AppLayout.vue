<template>
  <el-container class="app-layout">
    <el-header class="app-header">
      <div class="header-left">
        <el-icon class="menu-icon" @click="toggleSidebar">
          <Expand v-if="appStore.sidebarCollapsed" />
          <Fold v-else />
        </el-icon>
        <h1 class="title">科研项目管理系统</h1>
      </div>
      <div class="header-right">
        <el-dropdown>
          <span class="user-info">
            <el-avatar :size="32">{{ userStore.realName?.[0] || 'U' }}</el-avatar>
            <span class="username">{{ userStore.realName }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item>个人中心</el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container>
      <el-aside :width="appStore.sidebarCollapsed ? '64px' : '200px'" class="app-aside">
        <el-menu
          :default-active="activeMenu"
          :collapse="appStore.sidebarCollapsed"
          :router="false"
          class="app-menu"
        >
          <template v-for="route in menuRoutes" :key="route.path">
            <!-- 有子菜单 -->
            <el-sub-menu v-if="route.children && route.children.length > 0" :index="String(route.name || route.path)">
              <template #title>
                <el-icon v-if="route.meta?.icon">
                  <component :is="route.meta.icon" />
                </el-icon>
                <span>{{ route.meta?.title }}</span>
              </template>
              <template v-for="child in route.children" :key="child.path">
                <el-menu-item
                  v-if="!child.path.includes(':')"
                  :index="String(child.name || child.path)"
                  @click="handleMenuSelect(child)"
                >
                  {{ child.meta?.title }}
                </el-menu-item>
              </template>
            </el-sub-menu>

            <!-- 无子菜单 -->
            <el-menu-item v-else :index="String(route.name || route.path)" @click="handleMenuSelect(route)">
              <el-icon v-if="route.meta?.icon">
                <component :is="route.meta.icon" />
              </el-icon>
              <template #title>{{ route.meta?.title }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>

      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Expand, Fold } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

// 当前激活的菜单
const activeMenu = computed(() => String(route.name || route.path))

const handleMenuSelect = (target: RouteRecordRaw) => {
  const targetName = target.name
  if (!targetName || typeof targetName !== 'string') return

  void router.push({ name: targetName })
}

// 菜单路由（从路由配置中筛选，并按 meta.permission 过滤无权限入口）
const menuRoutes = computed(() => {
  const layout = router.options.routes.find((r) => r.path === '/')?.children || []
  return layout
    .map((parent) => {
      if (!parent.children?.length) return parent
      const visible = parent.children.filter(
        (child) => !child.path.includes(':') && canAccess(child.meta?.permission)
      )
      return visible.length ? { ...parent, children: visible } : null
    })
    .filter((parent): parent is RouteRecordRaw => !!parent && canAccess(parent.meta?.permission))
})

// 权限判断与路由守卫同口径：`*:*:*` 为超管通配；未声明 permission 的入口（如工作台）所有人可见
const canAccess = (permission?: unknown): boolean => {
  if (typeof permission !== 'string') return true
  return userStore.hasPermission(permission)
}

// 切换侧边栏
const toggleSidebar = () => {
  appStore.toggleSidebar()
}

// 退出登录
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await userStore.logout()
    router.push('/login')
  } catch {
    // 用户取消
  }
}
</script>

<style scoped lang="scss">
.app-layout {
  height: 100%;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background-color: #fff;
  border-bottom: 1px solid var(--color-border-base);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .menu-icon {
      font-size: 20px;
      cursor: pointer;
      transition: color 0.3s;

      &:hover {
        color: var(--color-primary);
      }
    }

    .title {
      font-size: 18px;
      font-weight: 500;
      color: var(--color-text-primary);
    }
  }

  .header-right {
    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;

      .username {
        font-size: 14px;
        color: var(--color-text-regular);
      }
    }
  }
}

.app-aside {
  background-color: #fff;
  border-right: 1px solid var(--color-border-base);
  transition: width 0.3s;
  overflow-x: hidden;

  .app-menu {
    border-right: none;
  }
}

.app-main {
  background-color: var(--color-bg-base);
  padding: 20px;
  overflow-y: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
