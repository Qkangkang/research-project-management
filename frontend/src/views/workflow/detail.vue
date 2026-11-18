<template>
  <div class="workflow-detail" v-loading="loading">
    <PageHeader :title="actionable ? '审批处理' : '审批详情'" />

    <!-- 业务信息卡片 -->
    <el-card shadow="never" class="business-card">
      <template #header>
        <div class="card-header">
          <span>业务信息</span>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="业务标题">
          {{ taskDetail?.bizTitle || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="业务类型">
          <el-tag size="small">{{ getBizTypeText(taskDetail?.bizType || '') }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审批节点">
          {{ taskDetail?.nodeName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="任务状态">
          <StatusTag :status="taskDetail?.status || ''" domain="workflow" />
        </el-descriptions-item>
        <el-descriptions-item label="截止时间">
          <span :class="{ 'text-danger': isOverdue(taskDetail?.dueDate) }">
            {{ taskDetail?.dueDate ? formatDateTime(taskDetail.dueDate) : '-' }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="业务 ID">
          {{ taskDetail?.bizId || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 审批轨迹 -->
    <ApprovalTimeline
      v-if="taskDetail?.instanceId"
      :instance-id="taskDetail.instanceId"
      class="timeline-card"
    />

    <!-- 审批操作卡片：仅待处理/处理中任务可操作，已办为只读详情 -->
    <el-card v-if="actionable" shadow="never" class="action-card">
      <template #header>
        <div class="card-header">
          <span>审批处理</span>
        </div>
      </template>

      <el-form :model="formData" label-width="100px">
        <el-form-item label="审批意见">
          <el-input
            v-model="formData.comment"
            type="textarea"
            :rows="4"
            placeholder="请输入审批意见（可选）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item>
          <el-space :size="12">
            <el-button
              type="success"
              :loading="submitting"
              @click="handleAction('APPROVED')"
            >
              通过
            </el-button>
            <el-button
              type="warning"
              :loading="submitting"
              @click="handleAction('RETURNED')"
            >
              退回
            </el-button>
            <el-button
              type="danger"
              :loading="submitting"
              @click="handleAction('REJECTED')"
            >
              拒绝
            </el-button>
            <el-button @click="handleCancel">取消</el-button>
          </el-space>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import ApprovalTimeline from '@/components/ApprovalTimeline/ApprovalTimeline.vue'
import { workflowApi, type WorkflowTaskItem, type ApprovalAction } from '@/api/workflow'
import { applicationApi } from '@/api/application'
import { fundingApi } from '@/api/funding'
import { outputApi } from '@/api/output'
import { formatDateTime } from '@/utils/date'

const route = useRoute()
const router = useRouter()

const taskId = route.params.taskId as string

const loading = ref(false)
const submitting = ref(false)
const taskDetail = ref<WorkflowTaskItem | null>(null)

const formData = reactive({
  comment: ''
})

// 仅待处理/处理中的任务展示审批操作；已办/取消/超时为只读详情
const actionable = computed(() =>
  taskDetail.value?.status === 'PENDING' || taskDetail.value?.status === 'PROCESSING'
)

onMounted(() => {
  fetchTaskDetail()
})

const fetchTaskDetail = async () => {
  loading.value = true
  try {
    const task = await workflowApi.task(taskId)
    task.bizTitle = await resolveBizTitle(task)
    taskDetail.value = task
  } catch (error: any) {
    ElMessage.error(error.message || '加载任务详情失败')
    router.back()
  } finally {
    loading.value = false
  }
}

const resolveBizTitle = async (task: WorkflowTaskItem): Promise<string | undefined> => {
  try {
    if (task.bizType === 'APPLICATION') {
      return (await applicationApi.detail(task.bizId)).title
    }
    if (task.bizType === 'EXPENSE') {
      const expense = await fundingApi.expenseDetail(task.bizId)
      return `${expense.requestNo}｜${expense.purpose}`
    }
    if (task.bizType === 'OUTPUT_REVIEW') {
      return (await outputApi.detail(task.bizId)).title
    }
    if (task.bizType === 'PROJECT_CHANGE') {
      return `项目变更 ${task.bizId.slice(0, 8)}`
    }
  } catch {
    return undefined
  }
  return undefined
}

const handleAction = async (action: ApprovalAction) => {
  const actionText = action === 'APPROVED' ? '通过' : action === 'RETURNED' ? '退回' : '拒绝'

  try {
    await ElMessageBox.confirm(
      `确认要${actionText}该审批吗？`,
      '确认操作',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    submitting.value = true

    await workflowApi.act(taskId, {
      requestId: crypto.randomUUID(),
      expectedVersion: taskDetail.value?.version ?? 0,
      action,
      comment: formData.comment || undefined
    })

    ElMessage.success(`${actionText}成功`)
    router.push('/workflow/todo')
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || `${actionText}失败`)
    }
  } finally {
    submitting.value = false
  }
}

const handleCancel = () => {
  router.back()
}

const getBizTypeText = (bizType: string): string => {
  const typeMap: Record<string, string> = {
    APPLICATION: '申报审批',
    EXPENSE: '支出审批',
    PROJECT_CHANGE: '项目变更',
    OUTPUT_REVIEW: '成果审核'
  }
  return typeMap[bizType] || bizType
}

const isOverdue = (dueDate?: string): boolean => {
  if (!dueDate) return false
  return new Date(dueDate) < new Date()
}
</script>

<style scoped lang="scss">
.workflow-detail {
  .business-card,
  .timeline-card,
  .action-card {
    margin-bottom: 16px;
  }

  .card-header {
    font-weight: 600;
    font-size: 16px;
  }

  .text-danger {
    color: var(--el-color-danger);
  }
}
</style>
