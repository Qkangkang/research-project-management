<template>
  <div class="output-detail-page">
    <PageHeader title="成果详情" :subtitle="detail?.title || ''">
      <template #actions>
        <el-button @click="handleBack">返回</el-button>
        <el-button
          v-if="detail?.status === 'DRAFT'"
          type="warning"
          v-permission="'output:edit'"
          @click="handleEdit"
        >
          编辑
        </el-button>
        <el-button
          v-if="detail?.status === 'APPROVED'"
          type="primary"
          v-permission="'output:edit'"
          @click="handleRevise"
        >
          修订
        </el-button>
        <el-button
          v-if="detail?.status === 'APPROVED'"
          type="danger"
          v-permission="'output:edit'"
          @click="handleWithdraw"
        >
          撤回
        </el-button>
      </template>
    </PageHeader>

    <el-card v-loading="loading" shadow="never" class="info-card">
      <template #header>
        <div class="card-header">
          <span>基本信息</span>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="成果类型">
          {{ detail?.outputType ? (outputTypeLabels[detail.outputType] || detail.outputType) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag v-if="detail" :status="detail.status" domain="output" />
        </el-descriptions-item>
        <el-descriptions-item label="成果标题" :span="2">
          {{ detail?.title }}
        </el-descriptions-item>
        <el-descriptions-item label="DOI">
          {{ detail?.doi || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="专利号">
          {{ detail?.patentNo || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="完成日期">
          {{ formatDate(detail?.completedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="登记人">
          {{ ownerDisplay }}
        </el-descriptions-item>
        <el-descriptions-item label="所属部门" :span="2">
          {{ deptDisplay }}
        </el-descriptions-item>
        <el-descriptions-item v-if="detail?.metadata" label="元数据" :span="2">
          <pre class="metadata-pre">{{ JSON.stringify(detail.metadata, null, 2) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail?.reviewComment" label="审核意见" :span="2">
          {{ detail.reviewComment }}
        </el-descriptions-item>
        <el-descriptions-item v-if="detail?.reviewedAt" label="审核时间">
          {{ formatDateTime(detail.reviewedAt) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 证据附件 -->
    <el-card v-if="detail?.evidenceFileIds && detail.evidenceFileIds.length > 0" shadow="never" class="evidence-card">
      <template #header>
        <div class="card-header">
          <span>证据附件</span>
        </div>
      </template>

      <el-table :data="fileList" border stripe>
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              type="primary"
              text
              size="small"
              @click="handleDownload(row.fileId)"
            >
              下载
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 关联项目 -->
    <el-card shadow="never" class="relation-card">
      <template #header>
        <div class="card-header">
          <span>关联项目</span>
          <el-button
            type="primary"
            size="small"
            v-permission="'output:relate'"
            @click="relationDialogVisible = true"
          >
            新增关联
          </el-button>
        </div>
      </template>

      <el-table :data="detail?.relatedProjects || []" border stripe>
        <el-table-column label="项目编号" width="180">
          <template #default="{ row }">
            {{ projectNo(row.projectId) }}
          </template>
        </el-table-column>
        <el-table-column label="项目名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ projectTitle(row.projectId) }}
          </template>
        </el-table-column>
        <el-table-column prop="contributionDesc" label="贡献说明" min-width="150" show-overflow-tooltip />
        <el-table-column label="归属比例" width="120">
          <template #default="{ row }">
            {{ row.attributionRatio ? `${(row.attributionRatio * 100).toFixed(0)}%` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              type="danger"
              text
              size="small"
              v-permission="'output:relate'"
              @click="handleRevokeRelation(row.projectId)"
            >
              解除关联
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增关联对话框 -->
    <el-dialog
      v-model="relationDialogVisible"
      title="关联成果到项目"
      width="600px"
    >
      <el-form
        ref="relationFormRef"
        :model="relationForm"
        :rules="relationRules"
        label-width="100px"
      >
        <el-form-item label="项目" prop="projectId" required>
          <el-select
            v-model="relationForm.projectId"
            placeholder="请选择项目"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="project in projects"
              :key="project.id"
              :label="`${project.projectNo}｜${project.title}`"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="贡献说明" prop="contributionDesc">
          <el-input
            v-model="relationForm.contributionDesc"
            type="textarea"
            :rows="3"
            placeholder="描述该成果对项目的贡献"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="归属比例" prop="attributionRatio">
          <el-input-number
            v-model="relationForm.attributionRatio"
            :min="0"
            :max="1"
            :step="0.1"
            :precision="2"
            placeholder="0.0 - 1.0"
          />
          <span style="margin-left: 8px; color: var(--el-text-color-secondary)">
            范围：0.0 - 1.0（如 0.5 表示 50%）
          </span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="relationDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="relating" @click="handleRelate">确认关联</el-button>
      </template>
    </el-dialog>

    <!-- 撤回对话框 -->
    <el-dialog
      v-model="withdrawDialogVisible"
      title="撤回成果"
      width="500px"
    >
      <el-form
        ref="withdrawFormRef"
        :model="withdrawForm"
        :rules="withdrawRules"
        label-width="80px"
      >
        <el-form-item label="撤回原因" prop="reason" required>
          <el-input
            v-model="withdrawForm.reason"
            type="textarea"
            :rows="4"
            placeholder="请输入撤回原因"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="withdrawDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="withdrawing" @click="confirmWithdraw">确认撤回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import PageHeader from '@/components/PageHeader/PageHeader.vue'
import StatusTag from '@/components/StatusTag/StatusTag.vue'
import { formatDate, formatDateTime } from '@/utils/date'
import { outputApi, OutputDetailVO, OutputType, RelateOutputRequest } from '@/api/output'
import { projectApi, type ProjectListItem } from '@/api/project'
import { organizationApi, type UserOption, type OrgUnitOption } from '@/api/organization'
import { fileApi } from '@/api/file'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const detail = ref<OutputDetailVO>()
const projects = ref<ProjectListItem[]>([])
const userNames = ref<Record<string, string>>({})
const deptNames = ref<Record<string, string>>({})
const relationDialogVisible = ref(false)
const withdrawDialogVisible = ref(false)
const relating = ref(false)
const withdrawing = ref(false)

const relationFormRef = ref<FormInstance>()
const withdrawFormRef = ref<FormInstance>()

const relationForm = reactive<RelateOutputRequest>({
  projectId: '',
  contributionDesc: '',
  attributionRatio: undefined
})

const withdrawForm = reactive({
  reason: ''
})

const relationRules: FormRules = {
  projectId: [
    { required: true, message: '请选择项目', trigger: 'change' }
  ]
}

const withdrawRules: FormRules = {
  reason: [
    { required: true, message: '请输入撤回原因', trigger: 'blur' },
    { min: 10, message: '撤回原因至少 10 个字符', trigger: 'blur' }
  ]
}

const outputTypeLabels: Record<OutputType, string> = {
  PAPER: '论文',
  PATENT: '专利',
  SOFTWARE: '软件',
  AWARD: '获奖',
  STANDARD: '标准',
  REPORT: '报告',
  OTHER: '其他'
}

const fileList = computed(() => {
  if (!detail.value?.evidenceFileIds) return []
  return detail.value.evidenceFileIds.map((fileId, index) => ({
    fileId,
    fileName: `证据文件 ${index + 1}`
  }))
})

onMounted(() => {
  loadDetail()
  loadProjects()
  loadLookups()
})

const loadLookups = async () => {
  try {
    const [users, units] = await Promise.all([
      organizationApi.users(),
      organizationApi.orgUnits()
    ])
    userNames.value = Object.fromEntries(users.map((item: UserOption) => [item.id, item.display_name]))
    deptNames.value = Object.fromEntries(units.map((item: OrgUnitOption) => [item.id, item.name]))
  } catch {
    userNames.value = {}
    deptNames.value = {}
  }
}

const ownerDisplay = computed(() => {
  const id = detail.value?.ownerId
  return id ? (userNames.value[id] || detail.value?.ownerName || id) : '-'
})

const deptDisplay = computed(() => {
  const id = detail.value?.deptId
  return id ? (deptNames.value[id] || detail.value?.deptName || id) : '-'
})

const projectNo = (projectId: string) => {
  return projects.value.find(item => item.id === projectId)?.projectNo || '-'
}

const projectTitle = (projectId: string) => {
  return projects.value.find(item => item.id === projectId)?.title || projectId
}

const loadProjects = async () => {
  try {
    const result = await projectApi.list({ page: 1, size: 200 })
    projects.value = result.items
  } catch {
    projects.value = []
  }
}

const loadDetail = async () => {
  const id = route.params.id as string
  if (!id) {
    ElMessage.error('缺少成果 ID')
    router.back()
    return
  }

  loading.value = true
  try {
    detail.value = await outputApi.detail(id)
  } catch (error: any) {
    ElMessage.error(error.message || '加载成果详情失败')
    router.back()
  } finally {
    loading.value = false
  }
}

const handleBack = () => {
  router.back()
}

const handleEdit = () => {
  router.push({
    path: '/outputs/create',
    query: { id: detail.value?.id }
  })
}

const handleRevise = () => {
  router.push({
    path: '/outputs/create',
    query: { id: detail.value?.id, mode: 'revise' }
  })
}

const handleWithdraw = () => {
  withdrawForm.reason = ''
  withdrawDialogVisible.value = true
}

const confirmWithdraw = async () => {
  if (!withdrawFormRef.value || !detail.value) return

  try {
    await withdrawFormRef.value.validate()
  } catch {
    return
  }

  withdrawing.value = true
  try {
    await outputApi.withdraw(detail.value.id, withdrawForm)
    ElMessage.success('撤回申请已提交')
    withdrawDialogVisible.value = false
    loadDetail()
  } catch (error: any) {
    ElMessage.error(error.message || '撤回失败')
  } finally {
    withdrawing.value = false
  }
}

const handleDownload = (fileId: string) => {
  fileApi.download(fileId).catch(() => ElMessage.error('获取下载地址失败'))
}

const handleRelate = async () => {
  if (!relationFormRef.value || !detail.value) return

  try {
    await relationFormRef.value.validate()
  } catch {
    return
  }

  relating.value = true
  try {
    await outputApi.relate(detail.value.id, relationForm)
    ElMessage.success('关联成功')
    relationDialogVisible.value = false
    relationForm.projectId = ''
    relationForm.contributionDesc = ''
    relationForm.attributionRatio = undefined
    loadDetail()
  } catch (error: any) {
    ElMessage.error(error.message || '关联失败')
  } finally {
    relating.value = false
  }
}

const handleRevokeRelation = async (projectId: string) => {
  if (!detail.value) return

  try {
    await ElMessageBox.confirm('确认解除与该项目的关联吗？', '解除关联', {
      type: 'warning'
    })

    const relation = detail.value.relatedProjects?.find(r => r.projectId === projectId)
    if (!relation) {
      ElMessage.error('未找到关联记录')
      return
    }
    if (!relation.relationId) {
      ElMessage.error('关联记录缺少 relationId')
      return
    }
    await outputApi.revokeRelation(detail.value.id, relation.relationId)
    ElMessage.success('已解除关联')
    loadDetail()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '解除关联失败')
    }
  }
}
</script>

<style scoped lang="scss">
.output-detail-page {
  .info-card,
  .evidence-card,
  .relation-card {
    margin-bottom: 16px;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .metadata-pre {
    margin: 0;
    padding: 8px;
    background-color: var(--el-fill-color-light);
    border-radius: 4px;
    font-size: 12px;
    line-height: 1.5;
    max-height: 300px;
    overflow: auto;
  }

  .form-tip {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-top: 4px;
  }
}
</style>
