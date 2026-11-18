<template>
  <div class="project-risks">
    <div class="actions">
      <el-button
        type="primary"
        v-permission="'project:risk:create'"
        @click="showAddDialog = true"
      >
        <el-icon><Plus /></el-icon>
        登记风险
      </el-button>
    </div>

    <el-table v-loading="loading" :data="risks" border stripe>
      <el-table-column prop="title" label="风险标题" min-width="200" />
      <el-table-column prop="riskType" label="风险类型" width="120" />
      <el-table-column label="风险等级" width="100">
        <template #default="{ row }">
          <el-tag :type="getRiskLevelTagType(row.riskLevel)" size="small">
            {{ getRiskLevelText(row.riskLevel) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getRiskStatusTagType(row.status)" size="small">
            {{ getRiskStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="责任人" width="120">
        <template #default="{ row }">
          {{ ownerName(row.ownerId) }}
        </template>
      </el-table-column>
      <el-table-column prop="dueDate" label="截止日期" width="120">
        <template #default="{ row }">
          {{ row.dueDate ? formatDate(row.dueDate) : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== 'CLOSED'"
            type="success"
            text
            size="small"
            v-permission="'project:risk:close'"
            @click="handleClose(row)"
          >
            关闭
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 登记风险弹窗 -->
    <el-dialog v-model="showAddDialog" title="登记项目风险" width="600px">
      <el-form :model="addForm" label-width="100px">
        <el-form-item label="风险标题" required>
          <el-input v-model="addForm.title" placeholder="请输入风险标题" />
        </el-form-item>
        <el-form-item label="风险类型" required>
          <el-select v-model="addForm.riskType" placeholder="请选择">
            <el-option label="技术风险" value="TECHNICAL" />
            <el-option label="管理风险" value="MANAGEMENT" />
            <el-option label="资源风险" value="RESOURCE" />
            <el-option label="外部风险" value="EXTERNAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级" required>
          <el-select v-model="addForm.riskLevel" placeholder="请选择">
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险描述">
          <el-input
            v-model="addForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入风险描述"
          />
        </el-form-item>
        <el-form-item label="责任人" required>
          <el-select v-model="addForm.ownerId" placeholder="请选择项目成员" filterable style="width: 100%">
            <el-option
              v-for="member in members"
              :key="member.userId"
              :label="`${member.userName}（${member.projectRole}）`"
              :value="member.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker
            v-model="addForm.dueDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAddRisk">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 关闭风险弹窗 -->
    <el-dialog v-model="showCloseDialog" title="关闭风险" width="500px">
      <el-form label-width="100px">
        <el-form-item label="解决方案" required>
          <el-input
            v-model="closeForm.resolution"
            type="textarea"
            :rows="4"
            placeholder="请输入风险解决方案"
          />
        </el-form-item>
        <el-form-item label="证据文件">
          <FileUpload v-model="closeForm.evidenceFileIds" :max-count="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCloseDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmClose">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { formatDate } from '@/utils/date'
import { projectApi, ProjectRiskView, ProjectMemberVO, RiskLevel, RiskStatus } from '@/api/project'
import FileUpload from '@/components/FileUpload/FileUpload.vue'

interface Props {
  projectId: string
}

const props = defineProps<Props>()

const loading = ref(false)
const risks = ref<ProjectRiskView[]>([])
const members = ref<ProjectMemberVO[]>([])
const showAddDialog = ref(false)
const showCloseDialog = ref(false)
const currentRisk = ref<ProjectRiskView | null>(null)
const submitting = ref(false)

const addForm = reactive({
  title: '',
  riskType: '',
  riskLevel: '' as RiskLevel,
  description: '',
  ownerId: '',
  dueDate: ''
})

const closeForm = reactive({
  resolution: '',
  evidenceFileIds: [] as string[]
})

onMounted(() => {
  fetchRisks()
  loadMembers()
})

const loadMembers = async () => {
  try {
    members.value = await projectApi.members(props.projectId)
  } catch {
    members.value = []
  }
}

const ownerName = (ownerId: string): string => {
  return members.value.find(item => item.userId === ownerId)?.userName || ownerId
}

const fetchRisks = async () => {
  loading.value = true
  try {
    risks.value = await projectApi.risks(props.projectId)
  } catch (error) {
    ElMessage.error('加载风险列表失败')
  } finally {
    loading.value = false
  }
}

const getRiskLevelText = (level: RiskLevel): string => {
  const map: Record<RiskLevel, string> = {
    LOW: '低',
    MEDIUM: '中',
    HIGH: '高',
    CRITICAL: '严重'
  }
  return map[level] || level
}

const getRiskLevelTagType = (level: RiskLevel) => {
  const map: Record<RiskLevel, 'success' | 'warning' | 'danger'> = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger',
    CRITICAL: 'danger'
  }
  return map[level] || 'warning'
}

const getRiskStatusText = (status: RiskStatus): string => {
  const map: Record<RiskStatus, string> = {
    OPEN: '开放',
    PROCESSING: '处理中',
    ESCALATED: '已升级',
    CLOSED: '已关闭',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

const getRiskStatusTagType = (status: RiskStatus) => {
  const map: Record<RiskStatus, 'danger' | 'warning' | 'success' | 'info'> = {
    OPEN: 'danger',
    PROCESSING: 'warning',
    ESCALATED: 'danger',
    CLOSED: 'success',
    CANCELLED: 'info'
  }
  return map[status] || 'warning'
}

const handleAddRisk = async () => {
  submitting.value = true
  try {
    await projectApi.createRisk(props.projectId, addForm)
    ElMessage.success('风险登记成功')
    showAddDialog.value = false
    fetchRisks()
  } catch (error) {
    ElMessage.error('登记失败')
  } finally {
    submitting.value = false
  }
}

const handleClose = (risk: any) => {
  currentRisk.value = risk
  closeForm.resolution = ''
  closeForm.evidenceFileIds = []
  showCloseDialog.value = true
}

const confirmClose = async () => {
  if (!currentRisk.value) return
  if (!closeForm.resolution.trim()) {
    ElMessage.warning('请输入风险解决方案')
    return
  }
  if (!closeForm.evidenceFileIds[0]) {
    ElMessage.warning('请上传处置证据文件')
    return
  }

  submitting.value = true
  try {
    await projectApi.closeRisk(props.projectId, currentRisk.value.id, {
      resolution: closeForm.resolution,
      evidenceFileId: closeForm.evidenceFileIds[0]
    })
    ElMessage.success('风险已关闭')
    showCloseDialog.value = false
    fetchRisks()
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.project-risks {
  .actions {
    margin-bottom: 16px;
  }
}
</style>
