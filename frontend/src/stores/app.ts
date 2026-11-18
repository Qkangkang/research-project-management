import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface MenuItem {
  path: string
  name: string
  icon?: string
  children?: MenuItem[]
  meta?: {
    title: string
    permission?: string
    badge?: string
  }
}

export const useAppStore = defineStore('app', () => {
  // 侧边栏是否折叠
  const sidebarCollapsed = ref(false)

  // 设备类型
  const device = ref<'desktop' | 'tablet' | 'mobile'>('desktop')

  // 菜单列表
  const menuList = ref<MenuItem[]>([])

  // 标签页列表
  const tabs = ref<Array<{ path: string; title: string }>>([])

  // 当前激活的标签页
  const activeTab = ref('')

  // 切换侧边栏
  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  // 设置设备类型
  const setDevice = (type: 'desktop' | 'tablet' | 'mobile') => {
    device.value = type
  }

  // 设置菜单列表
  const setMenuList = (list: MenuItem[]) => {
    menuList.value = list
  }

  // 添加标签页
  const addTab = (path: string, title: string) => {
    if (!tabs.value.some((tab) => tab.path === path)) {
      tabs.value.push({ path, title })
    }
    activeTab.value = path
  }

  // 移除标签页
  const removeTab = (path: string) => {
    const index = tabs.value.findIndex((tab) => tab.path === path)
    if (index !== -1) {
      tabs.value.splice(index, 1)
    }
  }

  // 移除其他标签页
  const removeOtherTabs = (path: string) => {
    tabs.value = tabs.value.filter((tab) => tab.path === path)
    activeTab.value = path
  }

  // 移除所有标签页
  const removeAllTabs = () => {
    tabs.value = []
    activeTab.value = ''
  }

  return {
    sidebarCollapsed,
    device,
    menuList,
    tabs,
    activeTab,
    toggleSidebar,
    setDevice,
    setMenuList,
    addTab,
    removeTab,
    removeOtherTabs,
    removeAllTabs
  }
})
