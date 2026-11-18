<template>
  <div class="workflow-trail" v-loading="loading">
    <PageHeader title="审批轨迹" subtitle="实例全流程节点与处理记录" />

    <el-card shadow="never" class="business-card" v-if="instance">
      <template #header>
        <div class="card-header">
          <span>实例信息</span>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="业务类型">
          <el-tag size="small">{{ instance.bizType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="实例状态">
          <StatusTag :status="instance.instanceStatus" domain="workflow" />
        </el-descriptions-item>
        <el-descriptions-item label="发起时间">
          {{ formatDateTime(instance.startedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="结束时间">
          {{ instance.completedAt ? formatDateTime(instance.completedAt) : '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <ApprovalTimeline
      v-if="instanceId"
      :instance-id="instanceId"
      class="timeline-card"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import ApprovalTimeline from '@/components/ApprovalTimeline/ApprovalTimeline.vue'
import { workflowApi, type WorkflowInstanceResponse } from '@/api/workflow'
import { formatDateTime } from '@/utils/date'

const route = useRoute()

const instanceId = computed(() => route.params.instanceId as string)

const loading = ref(false)
const instance = ref<WorkflowInstanceResponse | null>(null)

onMounted(() => {
  fetchInstance()
})

const fetchInstance = async () => {
  loading.value = true
  try {
    instance.value = await workflowApi.trail(instanceId.value)
  } catch (error: any) {
    ElMessage.error(error.message || '加载审批轨迹失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.workflow-trail {
  .business-card,
  .timeline-card {
    margin-bottom: 16px;
  }

  .card-header {
    font-weight: 600;
    font-size: 16px;
  }
}
</style>
