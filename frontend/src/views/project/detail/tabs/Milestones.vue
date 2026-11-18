<template>
  <div class="project-milestones">
    <el-table v-loading="loading" :data="milestones" border stripe>
      <el-table-column prop="code" label="里程碑编码" width="150" />
      <el-table-column prop="title" label="里程碑名称" min-width="200" />
      <el-table-column prop="planDate" label="原计划日期" width="120">
        <template #default="{ row }">
          {{ formatDate(row.planDate) }}
        </template>
      </el-table-column>
      <el-table-column prop="currentPlanDate" label="当前计划日期" width="140">
        <template #default="{ row }">
          {{ row.currentPlanDate ? formatDate(row.currentPlanDate) : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getMilestoneTagType(row.status)" size="small">
            {{ getMilestoneStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== 'COMPLETED'"
            type="success"
            text
            size="small"
            v-permission="'project:milestone:manage'"
            @click="handleComplete(row)"
          >
            标记完成
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 完成里程碑弹窗 -->
    <el-dialog v-model="showCompleteDialog" title="完成里程碑" width="400px">
      <el-form label-width="120px">
        <el-form-item label="实际完成日期">
          <el-date-picker
            v-model="actualDate"
            type="date"
            placeholder="选择日期（可选）"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCompleteDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmComplete">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { formatDate } from '@/utils/date'
import { projectApi, MilestoneView, MilestoneStatus } from '@/api/project'

interface Props {
  projectId: string
}

const props = defineProps<Props>()

const loading = ref(false)
const milestones = ref<MilestoneView[]>([])
const showCompleteDialog = ref(false)
const actualDate = ref('')
const currentMilestone = ref<MilestoneView | null>(null)
const submitting = ref(false)

onMounted(() => {
  fetchMilestones()
})

const fetchMilestones = async () => {
  loading.value = true
  try {
    milestones.value = await projectApi.milestones(props.projectId)
  } catch (error) {
    ElMessage.error('加载里程碑失败')
  } finally {
    loading.value = false
  }
}

const getMilestoneStatusText = (status: MilestoneStatus): string => {
  const map: Record<MilestoneStatus, string> = {
    PLANNED: '计划中',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    DELAYED: '已延期'
  }
  return map[status] || status
}

const getMilestoneTagType = (status: MilestoneStatus) => {
  const map: Record<MilestoneStatus, 'info' | 'success' | 'warning' | 'danger'> = {
    PLANNED: 'info',
    IN_PROGRESS: 'warning',
    COMPLETED: 'success',
    DELAYED: 'danger'
  }
  return map[status] || 'info'
}

const handleComplete = (milestone: any) => {
  currentMilestone.value = milestone
  actualDate.value = ''
  showCompleteDialog.value = true
}

const confirmComplete = async () => {
  if (!currentMilestone.value) return

  submitting.value = true
  try {
    await projectApi.completeMilestone(
      props.projectId,
      currentMilestone.value.id,
      actualDate.value || undefined
    )
    ElMessage.success('里程碑已标记完成')
    showCompleteDialog.value = false
    fetchMilestones()
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}
</script>
