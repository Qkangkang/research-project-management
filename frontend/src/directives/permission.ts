import { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * v-permission 指令
 * 用法：v-permission="'project:view'" 或 v-permission="['project:view', 'project:edit']"
 * 无权限时移除 DOM 元素
 */
const permissionDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const { value } = binding

    if (!value) {
      return
    }

    const userStore = useUserStore()
    const permissions = value instanceof Array ? value : [value]

    // 检查是否有任一权限（统一复用 store 的字符串权限码判断）
    const hasPermission = permissions.some((permission: string) =>
      userStore.hasPermission(permission)
    )

    if (!hasPermission) {
      // 无权限则移除元素
      el.parentNode?.removeChild(el)
    }
  }
}

export default permissionDirective
