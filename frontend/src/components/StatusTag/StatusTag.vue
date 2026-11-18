<template>
  <el-tag :type="tagType" :size="size" :effect="effect">
    {{ displayText }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type Domain = 'project' | 'application' | 'expense' | 'workflow' | 'output' | 'report' | 'task'
type TagType = 'success' | 'info' | 'warning' | 'danger' | ''

interface Props {
  status: string
  domain: Domain
  size?: 'default' | 'small' | 'large'
  effect?: 'dark' | 'light' | 'plain'
}

const props = withDefaults(defineProps<Props>(), {
  size: 'default',
  effect: 'light'
})

// 状态映射配置
const statusConfig: Record<Domain, Record<string, { text: string; type: TagType }>> = {
  project: {
    PENDING_START: { text: '待启动', type: 'info' },
    EXECUTING: { text: '执行中', type: 'success' },
    SUSPENDED: { text: '暂停', type: 'warning' },
    ACCEPTING: { text: '验收中', type: '' },
    COMPLETED: { text: '已完成', type: 'success' },
    TERMINATED: { text: '已终止', type: 'danger' }
  },
  application: {
    DRAFT: { text: '草稿', type: 'info' },
    IN_REVIEW: { text: '审批中', type: '' },
    SUPPLEMENT: { text: '待补充', type: 'warning' },
    APPROVED: { text: '已批准', type: 'success' },
    REJECTED: { text: '已拒绝', type: 'danger' },
    NOT_ACCEPTED: { text: '未受理', type: 'info' },
    CLOSED: { text: '已关闭', type: 'info' }
  },
  expense: {
    DRAFT: { text: '草稿', type: 'info' },
    IN_REVIEW: { text: '审批中', type: '' },
    SUPPLEMENT: { text: '待补充', type: 'warning' },
    APPROVED_UNPAID: { text: '待支付', type: 'warning' },
    PARTIALLY_PAID: { text: '部分支付', type: '' },
    PAID: { text: '已支付', type: 'success' },
    CANCELLED: { text: '已取消', type: 'info' },
    REJECTED: { text: '已拒绝', type: 'danger' },
    REVERSED: { text: '已冲销', type: 'danger' }
  },
  workflow: {
    PENDING: { text: '待处理', type: 'warning' },
    PROCESSING: { text: '处理中', type: '' },
    APPROVED: { text: '已通过', type: 'success' },
    RETURNED: { text: '已退回', type: 'warning' },
    REJECTED: { text: '已拒绝', type: 'danger' },
    CANCELLED: { text: '已取消', type: 'info' },
    TIMEOUT: { text: '已超时', type: 'danger' },
    EXCEPTION: { text: '异常', type: 'danger' },
    RUNNING: { text: '进行中', type: '' }
  },
  output: {
    DRAFT: { text: '草稿', type: 'info' },
    SUBMITTED: { text: '待审核', type: 'warning' },
    APPROVED: { text: '已审核', type: 'success' },
    REJECTED: { text: '已拒绝', type: 'danger' },
    REVOKED: { text: '已撤回', type: 'info' }
  },
  report: {
    DRAFT: { text: '草稿', type: 'info' },
    SUBMITTED: { text: '已提交', type: '' },
    APPROVED: { text: '已通过', type: 'success' },
    RETURNED: { text: '已退回', type: 'warning' }
  },
  task: {
    TODO: { text: '待开始', type: 'info' },
    OPEN: { text: '待开始', type: 'info' },
    IN_PROGRESS: { text: '进行中', type: '' },
    DONE: { text: '已完成', type: 'success' },
    COMPLETED: { text: '已完成', type: 'success' },
    CANCELLED: { text: '已取消', type: 'info' }
  }
}

const displayText = computed(() => {
  const config = statusConfig[props.domain]?.[props.status]
  return config?.text || props.status
})

const tagType = computed(() => {
  const config = statusConfig[props.domain]?.[props.status]
  return config?.type || 'info'
})
</script>
