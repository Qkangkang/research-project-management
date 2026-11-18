<template>
  <el-card v-loading="loading" class="approval-timeline">
    <template #header>
      <div class="card-header">
        <span>审批轨迹</span>
      </div>
    </template>

    <el-empty v-if="!loading && !timeline" description="暂无审批记录" />

    <el-timeline v-else-if="timeline">
      <el-timeline-item
        v-for="task in timeline.trail"
        :key="task.taskId"
        :type="getTimelineType(task.action)"
        :icon="getTimelineIcon(task.action)"
        :timestamp="formatDateTime(task.completedAt)"
        placement="top"
      >
        <el-card>
          <div class="timeline-content">
            <div class="timeline-content__header">
              <strong>{{ task.taskName }}</strong>
              <el-tag
                v-if="task.status"
                :type="getActionTagType(task.action)"
                size="small"
              >
                {{ getActionText(task.action, task.status) }}
              </el-tag>
            </div>
            <div class="timeline-content__body">
              <p><span class="label">处理人：</span>{{ task.assigneeName || '未知' }}</p>
              <p v-if="task.comment">
                <span class="label">审批意见：</span>{{ task.comment }}
              </p>
            </div>
          </div>
        </el-card>
      </el-timeline-item>
    </el-timeline>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Close, Back, Clock } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/date'
import { workflowApi, type WorkflowInstanceResponse } from '@/api/workflow'

interface Props {
  instanceId: string
}

const props = defineProps<Props>()

const loading = ref(false)
const timeline = ref<WorkflowInstanceResponse | null>(null)

onMounted(() => {
  fetchTimeline()
})

const fetchTimeline = async () => {
  if (!props.instanceId) {
    return
  }

  loading.value = true
  try {
    timeline.value = await workflowApi.trail(props.instanceId)
  } catch (error) {
    ElMessage.error('加载审批轨迹失败')
  } finally {
    loading.value = false
  }
}

const getActionText = (action?: string, status?: string): string => {
  if (!action && status === 'PENDING') return '待处理'
  if (!action) return status || '未知'

  const actionMap: Record<string, string> = {
    APPROVED: '通过',
    RETURNED: '退回',
    REJECTED: '拒绝',
    REASSIGN: '转派',
    DELEGATE: '委托'
  }
  return actionMap[action] || action
}

const getActionTagType = (action?: string): 'success' | 'warning' | 'danger' | 'info' => {
  if (action === 'APPROVED') return 'success'
  if (action === 'RETURNED') return 'warning'
  if (action === 'REJECTED') return 'danger'
  return 'info'
}

const getTimelineType = (action?: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' => {
  if (action === 'APPROVED') return 'success'
  if (action === 'RETURNED') return 'warning'
  if (action === 'REJECTED') return 'danger'
  return 'primary'
}

const getTimelineIcon = (action?: string) => {
  if (action === 'APPROVED') return Check
  if (action === 'RETURNED') return Back
  if (action === 'REJECTED') return Close
  return Clock
}
</script>

<style scoped lang="scss">
.approval-timeline {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .timeline-content {
    &__header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
    }

    &__body {
      font-size: 14px;
      color: var(--el-text-color-regular);

      p {
        margin: 4px 0;
      }

      .label {
        color: var(--el-text-color-secondary);
        margin-right: 8px;
      }
    }
  }
}

:deep(.el-timeline-item__timestamp) {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
